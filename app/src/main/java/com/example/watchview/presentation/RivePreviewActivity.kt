package com.example.watchview.presentation

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.watchview.presentation.theme.WatchViewTheme
import java.io.File
import kotlinx.coroutines.*
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File as RiveCoreFile
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.core.errors.ViewModelException
import java.util.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import android.util.Log
import app.rive.runtime.kotlin.controllers.RiveFileController
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.widget.Toast
import com.example.watchview.presentation.model.DownloadType
import com.example.watchview.presentation.ui.ACTION_NEW_ADB_FILE
import com.example.watchview.presentation.ui.EXTRA_FILE_PATH
import com.example.watchview.presentation.ui.EXTRA_FILE_TYPE
import com.example.watchview.utils.unzipMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.watchview.presentation.ui.ACTION_TRIGGER_WIRED_PREVIEW
import com.example.watchview.presentation.ui.ACTION_CLOSE_PREVIOUS_PREVIEW
import com.example.watchview.utils.WatchKeyController

private data class RiveSnapshot(
    val hour: Float,
    val minute: Float,
    val second: Float,
    val battery: Float,
    val month: Float,
    val day: Float,
    val week: Float,
    val deviceKnob: Float = 0f
)

private const val EXTRA_RIVE_VIEWMODEL_NAME = "extra_rive_viewmodel_name"
private const val EXTRA_RIVE_INSTANCE_NAME = "extra_rive_instance_name"
private const val EXTRA_RIVE_BINDING_MODE = "extra_rive_binding_mode"

private const val TAG_BINDING = "RiveBinding"
private const val TAG_BINDING_SESSION = "RiveBindingSession"
private const val TAG_BINDING_LISTENER = "RiveBindingListener"
// 每度旋转映射到的 deviceKnob 增量（调节敏感度时调整此值）
private const val KNOB_DELTA_PER_DEGREE = 0.1f
// 平滑参数：单帧趋近比例、最大单帧步长、平滑帧间隔
private const val DEVICE_KNOB_SMOOTHING_FACTOR = 0.18f
private const val DEVICE_KNOB_MAX_STEP_PER_TICK = 0.3f
private const val DEVICE_KNOB_SMOOTHING_INTERVAL_MS = 16L
// 连续循环驱动 deviceKnob 开关与参数
private const val ENABLE_DEVICE_KNOB_LOOP = false
private const val DEVICE_KNOB_LOOP_MAX = 13f
private const val DEVICE_KNOB_LOOP_DURATION_MS = 4000L
private const val DEVICE_KNOB_LOOP_INTERVAL_MS = 1000L
private const val KNOB_ACTIVE_TIMEOUT_MS = 400L
private const val STEM_KEY_FLAGS =
    WatchKeyController.FLAG_CONVERT_STEM_TO_FX or
    WatchKeyController.FLAG_CONVERT_STEM_TO_F1_ONLY
// Power key events are delivered only when FLAG_USE_POWER_KEY is set.
// According to the vendor doc, returning true from onKeyDown() is enough
// to suppress the default control-center behavior, so we don't set IGNORE flags.
private const val POWER_KEY_FLAGS = WatchKeyController.FLAG_USE_POWER_KEY

class RivePreviewActivity : ComponentActivity() {
    // 使用强引用持有RiveView
    private var riveView: RiveAnimationView? = null
    private lateinit var vibrator: Vibrator
    private lateinit var watchKeyController: WatchKeyController
    private val activityScope = MainScope()
    private val crownTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val powerTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val replayTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // 旋钮值增量流，用于旋钮旋转时传递增量
    private val rotaryKnobDeltaFlow = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    private val stemKeyDispatcher = StemKeyDispatcher { emitCrownTrigger() }
    private val powerKeyDispatcher = PowerKeyDispatcher { emitPowerTrigger() }
    
    // 用于跟踪当前文件路径，支持动态更新
    private val _currentFilePath = MutableStateFlow<String?>("")
    private val _currentIsTempFile = MutableStateFlow(false)
    private val _currentBindingConfig = MutableStateFlow(RiveBindingConfig())

    // 添加错误计数器
    private var errorCount = 0
    private val maxErrorCount = 3
    
    // 添加电池电量状态
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()
    
    // 添加协程作用域

    // 添加 Rive 事件监听器
    private val eventListener = object : RiveFileController.RiveEventListener {
        override fun notifyEvent(event: app.rive.runtime.kotlin.core.RiveEvent) {
            try {
                Log.i("RiveEvent", "Event received: ${event.name}")
                
                // 检查是否是振动事件
                if (event.name == "vibratory") {
                    val state = event.properties?.get("state") as? Number
                    Log.d("RiveEvent", "Vibration state: $state")
                    if (state?.toDouble() == 1.0) {
                        runOnUiThread {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val amplitude = 100
                                    val duration = 40L
                                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(50)
                                }
                            } catch (e: Exception) {
                                Log.e("RiveEvent", "Error during vibration", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RiveEvent", "Error in event listener", e)
                handleError(e)
            }
        }
    }

    private fun handleError(e: Exception) {
        errorCount++
        Log.e("RivePreviewActivity", "Error occurred: ${e.message}, count: $errorCount")
        
        if (errorCount >= maxErrorCount) {
            Log.e("RivePreviewActivity", "Too many errors, finishing activity")
            runOnUiThread {
                Toast.makeText(this, "预览出现异常，即将关闭", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = level * 100f / scale
                _batteryLevel.value = batteryPct
            }
        }
    }

    // 添加新的文件接收广播
    private val newFileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("RivePreviewActivity", "收到 newFileReceiver 广播: ${intent.action}")
            // 只处理新文件广播
            if (intent.action == ACTION_NEW_ADB_FILE) {
                // 立即发送触发检查的广播
                Log.d("RivePreviewActivity", "收到新文件广播，立即发送 ACTION_TRIGGER_WIRED_PREVIEW")
                val triggerIntent = Intent(ACTION_TRIGGER_WIRED_PREVIEW).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(triggerIntent)
                // 不再 finish 或延迟发送
            }
            // 忽略 CLOSE_PREVIEW Action
        }
    }

    // 添加新的关闭接收广播
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLOSE_PREVIOUS_PREVIEW) {
                Log.i("RivePreviewActivity", "收到 ACTION_CLOSE_PREVIOUS_PREVIEW 广播，关闭预览")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchKeyController = WatchKeyController { window }
        
        try {
            // 初始化振动器
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            
            // 禁用滑动手势
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // 注册电池状态广播接收器
            registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            // 注册旧的文件/关闭接收器
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_NEW_ADB_FILE)
                // 不再监听旧的 CLOSE_PREVIEW Action
                // addAction("com.example.watchview.CLOSE_PREVIEW")
            }
            registerReceiver(
                newFileReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED 
            )
            // 注册新的关闭接收器
            val closeFilter = IntentFilter(ACTION_CLOSE_PREVIOUS_PREVIEW)
            registerReceiver(closeReceiver, closeFilter, Context.RECEIVER_NOT_EXPORTED)
            
            Log.d("RivePreviewActivity", "已注册广播接收器: ACTION_NEW_ADB_FILE, ACTION_CLOSE_PREVIOUS_PREVIEW")
            
            // 启用向上导航
            actionBar?.setDisplayHomeAsUpEnabled(true)
            
            // 从 Intent 中获取文件路径和临时文件标记
            val initialFilePath = intent.getStringExtra("file_path") ?: return
            val initialIsTempFile = intent.getBooleanExtra("is_temp_file", false)
            val initialBindingMode = intent.getStringExtra(EXTRA_RIVE_BINDING_MODE)?.toRiveBindingMode()
                ?: RiveBindingMode.HYBRID
            val initialBindingConfig = RiveBindingConfig(
                viewModelName = intent.getStringExtra(EXTRA_RIVE_VIEWMODEL_NAME),
                instanceName = intent.getStringExtra(EXTRA_RIVE_INSTANCE_NAME),
                mode = initialBindingMode
            )
            
            // 初始化 StateFlow
            _currentFilePath.value = initialFilePath
            _currentIsTempFile.value = initialIsTempFile
            _currentBindingConfig.value = initialBindingConfig
            
            Log.d(
                "RivePreviewActivity",
                "onCreate: File path: $initialFilePath, isTempFile: $initialIsTempFile, bindingConfig=$initialBindingConfig"
            )
            
            setContent {
                WatchViewTheme {
                    // 使用 StateFlow 来响应文件变化
                    val filePath by _currentFilePath.collectAsState()
                    val isTempFile by _currentIsTempFile.collectAsState()
                    val bindingConfig by _currentBindingConfig.collectAsState()
                    
                    // 使用 remember 来保持状态
                    val showDialog = remember { mutableStateOf(false) }
                    var isSaved by remember(filePath) { mutableStateOf(false) } // 当文件变化时重置保存状态
                    
                    // 添加检查以确定文件是否为 ADB 传输的文件
                    val isExternalFile = remember(filePath) { 
                        filePath?.let { isExternalStorageFile(it) } ?: false 
                    }
                    
                    // 添加错误状态监听
                    LaunchedEffect(errorCount) {
                        if (errorCount >= maxErrorCount) {
                            finish()
                        }
                    }
                    
                    // 当文件路径为空时显示加载状态
                    if (filePath.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(text = "Loading...", style = androidx.compose.ui.text.TextStyle(color = Color.White))
                        }
                        return@WatchViewTheme
                    }
                    
                    if (showDialog.value) {
                        Dialog(
                            onDismissRequest = { 
                                Log.d("DialogDebug", "Dialog dismissed by outside click")
                                showDialog.value = false 
                            },
                            properties = DialogProperties(
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                                securePolicy = SecureFlagPolicy.Inherit,
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.9f))
                                    .clickable { 
                                        Log.d("DialogDebug", "Dialog background clicked")
                                        showDialog.value = false 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 48.dp)
                                        .clickable(enabled = false) {}, // 防止点击传递到背景
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // 重新播放按钮
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2196F3))
                                            .clickable {
                                                Log.d("DialogDebug", "Replay button clicked")
                                                try {
                                                    showDialog.value = false
                                                    // 重新播放当前 Rive 文件
                                                    riveView?.let {
                                                        it.reset()
                                                        it.play()
                                                    }
                                                    activityScope.launch {
                                                        replayTriggerFlow.emit(Unit)
                                                    }
                                                    Log.d("DialogDebug", "Replay successful")
                                                } catch (e: Exception) {
                                                    Log.e("DialogDebug", "Error during replay", e)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "重新播放",
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // 保存文件按钮（显示条件更改为：临时文件或外部存储文件，且未保存）
                                    if ((isTempFile || isExternalFile) && !isSaved) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                                .clickable {
                                                    Log.d("DialogDebug", "Save button clicked")
                                                    try {
                                                        showDialog.value = false
                                                        // 保存文件
                                                        val tempFile = File(filePath!!)
                                                        Log.d("SaveDebug", "Saving file from: ${tempFile.absolutePath}")
                                                        
                                                        val riveDir = File(this@RivePreviewActivity.filesDir, "saved_rive")
                                                        if (!riveDir.exists()) {
                                                            riveDir.mkdirs()
                                                            Log.d("SaveDebug", "Created save directory: ${riveDir.absolutePath}")
                                                        }
                                                        
                                                        // 获取原始文件名
                                                        val originalName = tempFile.name
                                                        val baseName = originalName.substringBeforeLast(".")
                                                        val extension = originalName.substringAfterLast(".", "riv")
                                                        
                                                        // 生成不重复的文件名
                                                        var index = 1
                                                        var fileName = originalName
                                                        var savedFile = File(riveDir.absolutePath, fileName)
                                                        
                                                        while (savedFile.exists()) {
                                                            fileName = "${baseName}_${index}.${extension}"
                                                            savedFile = File(riveDir.absolutePath, fileName)
                                                            index++
                                                        }
                                                        
                                                        // 复制文件
                                                        tempFile.copyTo(savedFile, overwrite = true)
                                                        Log.d("DialogDebug", "File saved successfully: ${savedFile.absolutePath}")
                                                        
                                                        // 显示保存成功提示
                                                        android.widget.Toast.makeText(
                                                            this@RivePreviewActivity,
                                                            "已成功保存到本地",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                        
                                                        // 更新保存状态
                                                        isSaved = true
                                                    } catch (e: Exception) {
                                                        Log.e("DialogDebug", "Error during save", e)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "保存文件",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                    } else if ((isTempFile || isExternalFile) && isSaved) {
                                        // 显示已保存状态的按钮
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "已保存",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    
                                    // 退出预览按钮
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935))
                                            .clickable {
                                                Log.d("DialogDebug", "Exit button clicked")
                                                try {
                                                    showDialog.value = false
                                                    finish() // 退出预览
                                                    Log.d("DialogDebug", "Activity finished successfully")
                                                } catch (e: Exception) {
                                                    Log.e("DialogDebug", "Error during exit", e)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "退出预览",
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            style = androidx.compose.ui.text.TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        // 检测双指按下事件 (与 MediaPreviewActivity 保持一致)
                                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press && event.changes.size >= 2) {
                                            Log.d("GestureDebug", "Double finger press detected in RivePreviewActivity")
                                            showDialog.value = true
                                            // 消费掉事件，防止其他处理
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    ) {
                        // 将电池电量传递给 RivePlayerUI
                        val batteryState by batteryLevel.collectAsState()

                        RivePlayerUI(
                            file = File(filePath!!),
                            batteryLevel = batteryState,
                            bindingConfig = bindingConfig,
                            crownTriggerFlow = crownTriggerFlow,
                            powerTriggerFlow = powerTriggerFlow,
                            replayTriggerFlow = replayTriggerFlow,
                            rotaryKnobDeltaFlow = rotaryKnobDeltaFlow,
                            onRiveViewCreated = { view -> riveView = view },
                            eventListener = eventListener,
                            onRotaryKnobDelta = { delta ->
                                // 旋钮旋转时发送增量值
                                activityScope.launch {
                                    rotaryKnobDeltaFlow.emit(delta)
                                }
                            },
                            onKnobIntegerCross = { vibrateMinimal() }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RivePreviewActivity", "Error in onCreate", e)
            handleError(e)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            // 取消所有协程
            activityScope.cancel()
            // 取消注册广播接收器
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(newFileReceiver)
            unregisterReceiver(closeReceiver) // <-- 注销新的接收器
            // 移除 Rive 事件监听器和停止动画，并释放资源
            riveView?.let { view ->
                view.removeEventListener(eventListener)
                view.stop()
            }
            riveView = null
        } catch (e: Exception) {
            Log.e("RivePreviewActivity", "Error in onDestroy", e)
        }
    }

    override fun onResume() {
        super.onResume()
        watchKeyController.applyFlags(STEM_KEY_FLAGS or POWER_KEY_FLAGS)
    }

    override fun onPause() {
        watchKeyController.clearFlags()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // 获取新的文件路径
        val newFilePath = intent.getStringExtra("file_path")
        val newIsTempFile = intent.getBooleanExtra("is_temp_file", false)
        val newBindingMode = intent.getStringExtra(EXTRA_RIVE_BINDING_MODE)?.toRiveBindingMode()
            ?: RiveBindingMode.HYBRID
        val newBindingConfig = RiveBindingConfig(
            viewModelName = intent.getStringExtra(EXTRA_RIVE_VIEWMODEL_NAME),
            instanceName = intent.getStringExtra(EXTRA_RIVE_INSTANCE_NAME),
            mode = newBindingMode
        )
        
        Log.d("RivePreviewActivity", "onNewIntent: 收到新文件请求 path=$newFilePath, isTempFile=$newIsTempFile")
        
        if (newFilePath != null && newFilePath != _currentFilePath.value) {
            // 重置错误计数
            errorCount = 0

            // 更新文件路径，触发 UI 重新加载（让新的加载流程与首次打开一致）
            _currentFilePath.value = newFilePath
            _currentIsTempFile.value = newIsTempFile
            _currentBindingConfig.value = newBindingConfig

            Log.d("RivePreviewActivity", "onNewIntent: 已更新文件路径，触发重新加载")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            Log.d(TAG_BINDING, "Power key onKeyDown intercepted")
            powerKeyDispatcher.onKeyDown(event)
            return true
        }
        return if (isStemKeyCode(keyCode)) {
            stemKeyDispatcher.onKeyDown(event)
        } else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            Log.d(TAG_BINDING, "Power key onKeyUp intercepted")
            powerKeyDispatcher.onKeyUp(event)
            return true
        }
        return if (isStemKeyCode(keyCode)) {
            stemKeyDispatcher.onKeyUp(event)
        } else super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun emitCrownTrigger() {
        activityScope.launch {
            Log.i(TAG_BINDING, "Emitting keyCrown trigger from stem key")
            crownTriggerFlow.emit(Unit)
        }
    }

    private fun emitPowerTrigger() {
        activityScope.launch {
            Log.i(TAG_BINDING, "Emitting keyPower trigger from power key")
            powerTriggerFlow.emit(Unit)
        }
    }

    private fun vibrateMinimal() {
        try {
            if (!::vibrator.isInitialized || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12L, 1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12L)
            }
        } catch (e: Exception) {
            Log.w("RivePreviewActivity", "Failed to vibrate on knob step", e)
        }
    }
}

@Composable
private fun RivePlayerUI(
    file: java.io.File,
    batteryLevel: Float,
    bindingConfig: RiveBindingConfig,
    crownTriggerFlow: SharedFlow<Unit>,
    powerTriggerFlow: SharedFlow<Unit>,
    replayTriggerFlow: SharedFlow<Unit>,
    rotaryKnobDeltaFlow: SharedFlow<Float>,
    onRiveViewCreated: (RiveAnimationView) -> Unit,
    eventListener: RiveFileController.RiveEventListener,
    onRotaryKnobDelta: (Float) -> Unit,
    onKnobIntegerCross: () -> Unit
) {
    // 用于接收旋钮旋转的焦点请求器
    val focusRequester = remember { FocusRequester() }
    
    var reloadToken by remember { mutableStateOf(0) }

    // 当前旋钮值，精度为小数点后两位
    var currentKnobValue by remember(file.absolutePath, reloadToken) { mutableStateOf(0f) }
    var targetKnobValue by remember(file.absolutePath, reloadToken) { mutableStateOf(0f) }
    var deviceKnobDisplay by remember(file.absolutePath, reloadToken) { mutableStateOf(0f) }
    // 使用 remember 保持引用，但不作为 Compose 状态，避免触发 recomposition
    var riveViewRef by remember { mutableStateOf<RiveAnimationView?>(null) }
    
    // 使用 rememberUpdatedState 来保持最新的电池电量，而不触发 recomposition
    val currentBatteryLevel by rememberUpdatedState(batteryLevel)
    
    val runtimeSession = remember(file.absolutePath, bindingConfig, reloadToken) {
        RiveRuntimeSession(file.absolutePath, bindingConfig).apply {
            registerObserver("systemStatusBattery") { value ->
                Log.d(TAG_BINDING_LISTENER, "systemStatusBattery -> $value")
            }
        }
    }

    // 控制 knobIsActive 延时归位
    val knobActiveScope = rememberCoroutineScope()
    var knobActiveResetJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(runtimeSession) {
        onDispose { runtimeSession.dispose() }
    }

    val riveBytes by produceState<ByteArray?>(initialValue = null, key1 = file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching { file.readBytes() }
                .onFailure { Log.e("RivePlayerUI", "Failed to read Rive file: ${file.absolutePath}", it) }
                .getOrNull()
        }
    }

    LaunchedEffect(riveViewRef, runtimeSession) {
        val view = riveViewRef ?: return@LaunchedEffect
        val knob = runtimeSession.readDeviceKnob(view)
        val rounded = knob.round(2)
        currentKnobValue = rounded
        targetKnobValue = rounded
        deviceKnobDisplay = rounded
    }

    // 使用 LaunchedEffect 直接更新 RiveView，不通过 Compose 状态
    // 这样可以避免频繁的 recomposition，大幅提升性能
    LaunchedEffect(riveViewRef, runtimeSession) {
        val view = riveViewRef ?: return@LaunchedEffect
        while (isActive) {
            try {
                val calendar = Calendar.getInstance()
                val hour = (calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f).round(1)
                val minute = (calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60f).round(1)
                val second = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f).round(2)
                val month = (calendar.get(Calendar.MONTH) + 1).toFloat()
                val day = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
                val week = (calendar.get(Calendar.DAY_OF_WEEK) - 1).toFloat()
                
                val snapshot = RiveSnapshot(
                    hour = hour,
                    minute = minute,
                    second = second,
                    battery = currentBatteryLevel,
                    month = month,
                    day = day,
                    week = week,
                    deviceKnob = currentKnobValue
                )
                runtimeSession.applySnapshot(view, snapshot)
                
                delay(1000L)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("RivePlayerUI", "Error updating time", e)
            }
        }
    }
    
    // 监听旋钮增量流，实时更新 deviceKnob 值（仅在循环模式关闭时启用）
    if (!ENABLE_DEVICE_KNOB_LOOP) {
        LaunchedEffect(rotaryKnobDeltaFlow, riveViewRef, runtimeSession) {
            rotaryKnobDeltaFlow.collect { delta ->
                val view = riveViewRef ?: return@collect
                val base = runtimeSession.readDeviceKnob(view).coerceAtLeast(0f)
                val nextTarget = (base + delta).coerceAtLeast(0f)
                targetKnobValue = nextTarget
                currentKnobValue = base
                deviceKnobDisplay = base.round(2)
                runtimeSession.updateDeviceKnob(view, base)
                Log.d("RotaryKnob", "deviceKnob target updated: $nextTarget (delta: $delta)")
            }
        }
    }

    val riveData = riveBytes
    if (riveData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BasicText(text = "Loading Rive...")
        }
        return
    }

    // 请求焦点以接收旋钮事件
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 定期从 Rive Databinding 读取当前值用于显示，避免本地与 Rive 内部脱节
    LaunchedEffect(riveViewRef, runtimeSession) {
        val view = riveViewRef ?: return@LaunchedEffect
        while (isActive) {
            val live = runtimeSession.readDeviceKnob(view).round(2)
            deviceKnobDisplay = live
            // 若 Rive 内部/手势改变值，实时对齐 current/target，避免旋钮基线偏差
            if (kotlin.math.abs(live - currentKnobValue) > 0.005f) {
                currentKnobValue = live
                targetKnobValue = live
            }
            delay(300L)
        }
    }

    // 平滑逼近目标值，降低单帧跳变感
    if (!ENABLE_DEVICE_KNOB_LOOP) {
        LaunchedEffect(riveViewRef, runtimeSession) {
            val view = riveViewRef ?: return@LaunchedEffect
            while (isActive) {
                val current = currentKnobValue
                val target = targetKnobValue
                val diff = target - current
                if (kotlin.math.abs(diff) < 0.005f) {
                    delay(DEVICE_KNOB_SMOOTHING_INTERVAL_MS)
                    continue
                }
                // 插值：先按比例限速，再用 ease-out 映射步长，避免大跨度抖动
                val step = (diff * DEVICE_KNOB_SMOOTHING_FACTOR)
                    .coerceIn(-DEVICE_KNOB_MAX_STEP_PER_TICK, DEVICE_KNOB_MAX_STEP_PER_TICK)
                val t = (abs(step) / DEVICE_KNOB_MAX_STEP_PER_TICK).coerceIn(0f, 1f)
                val easedStep = easeOutQuad(t) * DEVICE_KNOB_MAX_STEP_PER_TICK * if (step >= 0) 1f else -1f
                val first = (current + easedStep * 0.4f).coerceAtLeast(0f)
                val second = (first + easedStep * 0.6f).coerceAtLeast(0f)
                val next = second.round(3)
                val crossings = countIntegerCrossings(current, next)
                if (crossings > 0) {
                    repeat(crossings) { onKnobIntegerCross() }
                }
                currentKnobValue = next
                deviceKnobDisplay = next.round(2)
                runtimeSession.updateDeviceKnob(view, (first.round(3)))
                runtimeSession.updateDeviceKnob(view, next)
                delay(DEVICE_KNOB_SMOOTHING_INTERVAL_MS)
            }
        }
    } else {
        // 循环驱动 deviceKnob：周期内从 0 到 13 循环
        LaunchedEffect(riveViewRef, runtimeSession) {
            val view = riveViewRef ?: return@LaunchedEffect
            while (isActive) {
                val phase = (SystemClock.uptimeMillis() % DEVICE_KNOB_LOOP_DURATION_MS).toFloat() / DEVICE_KNOB_LOOP_DURATION_MS
                val value = (phase * DEVICE_KNOB_LOOP_MAX).round(2)
                currentKnobValue = value
                targetKnobValue = value
                deviceKnobDisplay = value
                runtimeSession.updateDeviceKnob(view, value)
                delay(DEVICE_KNOB_LOOP_INTERVAL_MS)
            }
        }
    }
    
    key(reloadToken) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    if (!ENABLE_DEVICE_KNOB_LOOP) {
                        // 将旋转角度（系统提供的 scroll 像素）映射为按度数的增量
                        val delta = event.verticalScrollPixels * KNOB_DELTA_PER_DEGREE
                        // 标记旋钮激活，并在一段时间无输入后自动复位为 false
                        val view = riveViewRef
                        if (view != null) {
                            runtimeSession.setKnobActive(view, true)
                            knobActiveResetJob?.cancel()
                            knobActiveResetJob = knobActiveScope.launch {
                                delay(KNOB_ACTIVE_TIMEOUT_MS)
                                val stillView = riveViewRef
                                if (stillView != null) {
                                    runtimeSession.setKnobActive(stillView, false)
                                }
                            }
                        }
                        onRotaryKnobDelta(delta)
                        Log.d("RotaryKnob", "Rotary scroll event: pixels=${event.verticalScrollPixels}, delta=$delta")
                    }
                    true // 消费事件
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            AndroidView(
                factory = { context ->
                    try {
                        RiveAnimationView(context).apply {
                            autoplay = true
                            riveViewRef = this
                            runtimeSession.attachView(this)
                            val riveFile = RiveCoreFile(riveData)
                            setRiveFile(riveFile)
                            addEventListener(eventListener)
                            runtimeSession.log("View created; autoplay=$autoplay mode=${bindingConfig.mode}")
                            controller?.stateMachines?.forEach { sm ->
                                runCatching { play(sm.name) }.onFailure {
                                    Log.w(TAG_BINDING, "Failed to start state machine ${sm.name}", it)
                                }
                            }
                            runtimeSession.bindViewModelIfNeeded(this)
                            onRiveViewCreated(this)
                        }
                    } catch (e: Exception) {
                        Log.e("RivePlayerUI", "Error creating RiveAnimationView", e)
                        throw e
                    }
                },
                // update 回调只用于必要的视图重新绑定，不再用于数据更新
                update = { riveView ->
                    try {
                        if (riveViewRef !== riveView) {
                            riveViewRef = riveView
                            runtimeSession.attachView(riveView)
                        }
                    } catch (e: Exception) {
                        Log.e("RivePlayerUI", "Error updating RiveAnimationView", e)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LaunchedEffect(crownTriggerFlow, runtimeSession) {
        crownTriggerFlow.collect {
            Log.i(TAG_BINDING, "crownTriggerFlow -> fire keyCrown")
            runtimeSession.fireTrigger("keyCrown")
        }
    }

    LaunchedEffect(powerTriggerFlow, runtimeSession) {
        powerTriggerFlow.collect {
            Log.i(TAG_BINDING, "powerTriggerFlow -> fire keyPower")
            runtimeSession.fireTrigger("keyPower")
        }
    }

    LaunchedEffect(replayTriggerFlow) {
        replayTriggerFlow.collect {
            reloadToken++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            riveViewRef?.let { view ->
                runtimeSession.setKnobActive(view, false)
                // 仅停止并移除监听，具体释放交由 session 统一处理，避免重复释放导致崩溃
                view.removeEventListener(eventListener)
                view.stop()
            }
            riveViewRef = null
            knobActiveResetJob?.cancel()
            knobActiveResetJob = null
        }
    }
}

private fun countIntegerCrossings(prev: Float, next: Float): Int {
    val lower = floor(min(prev, next))
    val upper = floor(max(prev, next))
    return abs(upper.toInt() - lower.toInt())
}

// 扩展函数：对 Float 进行四舍五入并保留指定位数的小数
private fun Float.round(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

private fun easeOutQuad(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped)
}

// 添加函数检查文件是否在外部存储中
private fun isExternalStorageFile(filePath: String): Boolean {
    return try {
        // 检查路径是否包含外部存储目录的特征
        val file = File(filePath)
        val canonicalPath = file.canonicalPath
        
        // 检查路径是否包含外部存储的特征目录名
        canonicalPath.contains("/storage/emulated/0/Android/data") ||
        canonicalPath.contains("/sdcard/Android/data") ||
        canonicalPath.contains("/mnt/sdcard/Android/data") ||
        // 适配 Android 设备的外部存储路径模式
        file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)
    } catch (e: Exception) {
        Log.e("RivePreviewActivity", "Error checking external storage path", e)
        false
    }
}

private data class RiveBindingConfig(
    val viewModelName: String? = null,
    val instanceName: String? = null,
    val mode: RiveBindingMode = RiveBindingMode.HYBRID
)

private enum class RiveBindingMode(val wireValue: String) {
    VIEWMODEL_ONLY("viewmodel_only"),
    STATE_MACHINE_ONLY("state_machine_only"),
    HYBRID("hybrid");

    override fun toString(): String = wireValue
}

private fun String?.toRiveBindingMode(): RiveBindingMode? {
    if (this.isNullOrBlank()) return null
    return RiveBindingMode.values().firstOrNull { it.wireValue.equals(this, ignoreCase = true) }
}

private class RiveRuntimeSession(
    private val filePath: String,
    val config: RiveBindingConfig
) {
    private var riveView: RiveAnimationView? = null
    private var lastSnapshot: RiveSnapshot? = null
    private var lastKnownDeviceKnob = 0f
    private var lastKnobIsActive = false
    private val propertyObservers = mutableMapOf<String, MutableList<(Any?) -> Unit>>()
    val missingViewModelProperties = mutableSetOf<String>()
    var viewModelInstance: ViewModelInstance? = null
        private set
    private val pendingTriggers = ArrayDeque<String>()
    
    // 标记文件是否支持 ViewModel 绑定
    private var viewModelAvailable: Boolean = true

    fun attachView(view: RiveAnimationView) {
        riveView = view
    }

    fun dispose() {
        // 清理 ViewModel 实例引用
        viewModelInstance = null
        
        // 释放 RiveAnimationView 资源（集中释放，避免多处重复 release 引发 JNI 异常）
        riveView?.let { view ->
            try {
                view.stop()
            } catch (e: Exception) {
                Log.w(TAG_BINDING_SESSION, "Error stopping Rive view in session dispose", e)
            }
        }
        riveView = null
        
        lastSnapshot = null
        propertyObservers.clear()
        missingViewModelProperties.clear()
        pendingTriggers.clear()
        lastKnobIsActive = false
        
        Log.d(TAG_BINDING_SESSION, "[$filePath] Session disposed")
    }

    fun registerObserver(propertyName: String, observer: (Any?) -> Unit) {
        propertyObservers.getOrPut(propertyName) { mutableListOf() }.add(observer)
    }

    fun notifyObservers(propertyName: String, value: Any?) {
        propertyObservers[propertyName]?.forEach { listener ->
            runCatching { listener(value) }.onFailure {
                Log.w(TAG_BINDING_LISTENER, "Listener for $propertyName failed", it)
            }
        }
    }

    fun log(message: String) {
        Log.i(TAG_BINDING_SESSION, "[$filePath] $message")
    }

    fun bindViewModelIfNeeded(riveAnimationView: RiveAnimationView) {
        if (config.mode == RiveBindingMode.STATE_MACHINE_ONLY) {
            log("ViewModel binding skipped due to mode=${config.mode}")
            return
        }

        try {
            val controller = riveAnimationView.controller
            if (controller == null) {
                log("Controller not available, will use state machine fallback")
                return
            }
            
            val artboard = controller.activeArtboard ?: riveAnimationView.file?.firstArtboard
            if (artboard == null) {
                log("Artboard not available, will use state machine fallback")
                return
            }
            
            val file = riveAnimationView.file
            if (file == null) {
                log("Rive file not available, will use state machine fallback")
                return
            }

            // 尝试复用已存在的 ViewModel 实例
            val existing = controller.stateMachines.firstOrNull { it.viewModelInstance != null }?.viewModelInstance
            if (existing != null && config.viewModelName.isNullOrBlank() && config.instanceName.isNullOrBlank()) {
                viewModelInstance = existing
                log("Reusing existing ViewModel instance ${existing.name}")
                dispatchPendingTriggers()
                return
            }

            // 尝试获取 ViewModel，如果不存在则优雅降级到 state machine 模式
            val viewModel = when {
                !config.viewModelName.isNullOrBlank() -> runCatching {
                    file.getViewModelByName(config.viewModelName!!)
                }.onFailure {
                    Log.w(TAG_BINDING_SESSION, "[$filePath] ViewModel ${config.viewModelName} not found", it)
                }.getOrNull()
                else -> runCatching {
                    file.defaultViewModelForArtboard(artboard)
                }.onFailure {
                    // 这个 Rive 文件没有 ViewModel，这是正常的情况
                    Log.i(TAG_BINDING_SESSION, "[$filePath] No default ViewModel for artboard (this is normal for files without Databinding)")
                }.getOrNull()
            }
            
            if (viewModel == null) {
                // 文件没有 ViewModel，这不是错误，将使用纯 state machine 模式
                log("No ViewModel available, animation will play using state machine inputs only")
                return
            }

            // 尝试创建 ViewModel 实例
            val instance = when {
                !config.instanceName.isNullOrBlank() -> runCatching {
                    viewModel.createInstanceFromName(config.instanceName!!)
                }.onFailure {
                    Log.w(TAG_BINDING_SESSION, "[$filePath] Instance ${config.instanceName} not found, fallback to default")
                }.getOrNull() ?: runCatching { viewModel.createDefaultInstance() }.getOrNull()
                else -> runCatching { viewModel.createDefaultInstance() }.getOrNull()
            }
            
            if (instance == null) {
                Log.w(TAG_BINDING_SESSION, "[$filePath] Failed to create ViewModel instance, will use state machine fallback")
                return
            }

            controller.stateMachines.forEach { it.viewModelInstance = instance }
            artboard.viewModelInstance = instance
            viewModelInstance = instance
            log("Bound ViewModel=${viewModel.name} instance=${instance.name ?: "default"}")
            dispatchPendingTriggers()
        } catch (e: Exception) {
            // 捕获所有异常，确保即使 ViewModel 绑定失败，动画也能播放
            Log.w(TAG_BINDING_SESSION, "[$filePath] ViewModel binding failed, will use state machine fallback", e)
            viewModelInstance = null
        }
    }

    fun applySnapshot(riveAnimationView: RiveAnimationView, snapshot: RiveSnapshot) {
        val previous = lastSnapshot
        if (snapshot == previous) return
        lastSnapshot = snapshot

        // deviceKnob 仅在旋钮事件时写入，避免非用户操作的周期性写入
    }

    fun readDeviceKnob(riveAnimationView: RiveAnimationView): Float {
        val viewModel = viewModelInstance
        if (viewModel != null && config.mode != RiveBindingMode.STATE_MACHINE_ONLY) {
            try {
                val property = viewModel.getNumberProperty("deviceKnob")
                if (property != null) {
                    val value = property.value
                    lastKnownDeviceKnob = value
                    markPropertyAvailable("deviceKnob")
                    return value
                }
                markPropertyMissing("deviceKnob")
            } catch (e: Exception) {
                markPropertyMissing("deviceKnob", e)
            }
        }
        return lastKnownDeviceKnob
    }

    fun adjustDeviceKnob(riveAnimationView: RiveAnimationView, delta: Float): Float {
        val base = readDeviceKnob(riveAnimationView)
        val next = (base + delta).coerceAtLeast(0f).round(2)
        if (next == base) return base
        updateDeviceKnob(riveAnimationView, next)
        return next
    }

    fun setKnobActive(riveAnimationView: RiveAnimationView, active: Boolean) {
        if (active == lastKnobIsActive) return
        lastKnobIsActive = active
        val viewModel = viewModelInstance
        val hasViewModel = viewModel != null && config.mode != RiveBindingMode.STATE_MACHINE_ONLY
        if (hasViewModel && viewModel != null) {
            viewModel.setBooleanProperty("knobIsActive", active, this)
        } else {
            markPropertyMissing("knobIsActive")
        }
    }

    /**
     * 实时更新 deviceKnob 值到 Rive ViewModel
     * 这个方法专门用于旋钮旋转时的增量更新，不走 snapshot 机制
     * @param riveAnimationView Rive 动画视图
     * @param value 当前的旋钮值（已累加增量后的值）
     */
    fun updateDeviceKnob(riveAnimationView: RiveAnimationView, value: Float) {
        val viewModel = viewModelInstance
        val hasViewModel = viewModel != null && config.mode != RiveBindingMode.STATE_MACHINE_ONLY
        lastKnownDeviceKnob = value.coerceAtLeast(0f)
        
        if (hasViewModel && viewModel != null) {
            viewModel.setNumberProperty("deviceKnob", lastKnownDeviceKnob, this)
        }
        
        // Hybrid 模式下也尝试更新 state machine
        // 如果只要 ViewModel 写入，则跳过状态机
        val shouldTouchStateMachines = config.mode != RiveBindingMode.VIEWMODEL_ONLY
        if (shouldTouchStateMachines && config.mode == RiveBindingMode.STATE_MACHINE_ONLY) {
            riveAnimationView.pushNumberInputAcrossStateMachines("deviceKnob", value, this)
        }
    }

    fun markPropertyAvailable(propertyName: String) {
        if (missingViewModelProperties.remove(propertyName)) {
            Log.i(TAG_BINDING, "[$filePath] ViewModel property restored: $propertyName")
        }
    }

    fun markPropertyMissing(propertyName: String, throwable: Throwable? = null) {
        if (missingViewModelProperties.add(propertyName)) {
            if (throwable != null) {
                Log.w(TAG_BINDING, "[$filePath] ViewModel property missing: $propertyName", throwable)
            } else {
                Log.w(TAG_BINDING, "[$filePath] ViewModel property missing: $propertyName")
            }
        }
    }

    fun logWrite(target: String, propertyName: String, value: Any?, extra: String? = null) {
        val suffix = extra?.let { " $it" } ?: ""
        Log.d(
            TAG_BINDING,
            "[write] target=$target field=$propertyName value=$value mode=${config.mode.wireValue}$suffix"
        )
    }

    fun fireTrigger(triggerName: String) {
        if (config.mode == RiveBindingMode.STATE_MACHINE_ONLY) {
            Log.i(TAG_BINDING, "[$filePath] Trigger $triggerName -> state machine (mode STATE_MACHINE_ONLY)")
            riveView?.pushTriggerAcrossStateMachines(triggerName, this)
            return
        }

        val viewModelReady = viewModelInstance != null
        if (!viewModelReady) {
            pendingTriggers.add(triggerName)
            Log.i(TAG_BINDING, "[$filePath] Queue trigger=$triggerName (ViewModel not bound yet)")
            return
        }

        val handled = dispatchTriggerToViewModel(triggerName)
        if (handled) {
            Log.i(TAG_BINDING, "[$filePath] Trigger $triggerName fired via ViewModel")
            return
        }

        val canFallback = config.mode != RiveBindingMode.VIEWMODEL_ONLY
        if (canFallback) {
            val view = riveView
            if (view != null) {
                Log.i(TAG_BINDING, "[$filePath] Trigger $triggerName fallback to state machine input")
                view.pushTriggerAcrossStateMachines(triggerName, this)
            } else {
                Log.w(TAG_BINDING, "[$filePath] Trigger $triggerName dropped; view not attached")
            }
        } else {
            Log.w(TAG_BINDING, "[$filePath] Trigger $triggerName not found in ViewModel and fallback disabled")
        }
    }

    private fun dispatchTriggerToViewModel(triggerName: String): Boolean {
        val viewModel = viewModelInstance ?: return false
        return viewModel.triggerProperty(triggerName, this)
    }

    private fun dispatchPendingTriggers() {
        if (pendingTriggers.isEmpty()) return
        val queued = ArrayList(pendingTriggers)
        pendingTriggers.clear()
        queued.forEach { trigger ->
            if (!dispatchTriggerToViewModel(trigger)) {
                Log.w(TAG_BINDING, "[$filePath] Pending trigger $trigger not found in ViewModel")
            } else {
                Log.i(TAG_BINDING, "[$filePath] Drained pending trigger $trigger")
            }
        }
    }

}

private fun RiveAnimationView.pushNumberInputAcrossStateMachines(
    inputName: String,
    value: Float,
    session: RiveRuntimeSession
) {
    val artboard = controller?.activeArtboard ?: file?.firstArtboard ?: return
    val playing = playingStateMachines
    val targetMachines = if (playing.isNotEmpty()) playing.map { it.name } else artboard.stateMachineNames ?: emptyList()
    var applied = false
    targetMachines.forEach { machineName ->
        val stateMachine = artboard.stateMachine(machineName)
        val hasInput = stateMachine?.inputs?.any { it.name == inputName } == true
        if (hasInput) {
            try {
                setNumberState(machineName, inputName, value)
                session.logWrite("stateMachine", inputName, value, "machine=$machineName")
                session.notifyObservers(inputName, value)
                applied = true
            } catch (e: Exception) {
                Log.e(TAG_BINDING, "Failed to set $inputName on $machineName", e)
            }
        }
    }
    if (!applied) {
        Log.d(TAG_BINDING, "No state machine exposes input: $inputName")
    }
}

private fun RiveAnimationView.pushTriggerAcrossStateMachines(
    triggerName: String,
    session: RiveRuntimeSession
) {
    val artboard = file?.firstArtboard ?: return
    val playing = playingStateMachines
    val targetMachines = if (playing.isNotEmpty()) {
        playing.map { it.name }
    } else {
        artboard.stateMachineNames ?: emptyList()
    }
    var fired = false
    targetMachines.forEach { machineName ->
        val stateMachine = artboard.stateMachine(machineName)
        val hasTrigger = stateMachine?.inputs?.any { it.name == triggerName } == true
        if (hasTrigger) {
            try {
                fireState(machineName, triggerName)
                session.logWrite("stateMachine", triggerName, "trigger", "machine=$machineName")
                fired = true
            } catch (e: Exception) {
                Log.e(TAG_BINDING, "Failed to fire trigger=$triggerName on $machineName", e)
            }
        }
    }
    if (!fired) {
        Log.w(TAG_BINDING, "No state machine trigger named $triggerName")
    }
}

private fun ViewModelInstance.setNumberProperty(
    propertyName: String,
    value: Float,
    session: RiveRuntimeSession
) {
    try {
        val property = getNumberProperty(propertyName)
        if (property != null) {
            if (property.value != value) {
                property.value = value
                session.logWrite("viewModel", propertyName, value)
                session.notifyObservers(propertyName, value)
            }
            session.markPropertyAvailable(propertyName)
        } else {
            session.markPropertyMissing(propertyName)
        }
    } catch (e: Exception) {
        session.markPropertyMissing(propertyName, e)
    }
}

private fun ViewModelInstance.setBooleanProperty(
    propertyName: String,
    value: Boolean,
    session: RiveRuntimeSession
) {
    try {
        val property = getBooleanProperty(propertyName)
        if (property != null && property.value != value) {
            property.value = value
            session.logWrite("viewModel", propertyName, value)
            session.notifyObservers(propertyName, value)
        }
        session.markPropertyAvailable(propertyName)
    } catch (e: ViewModelException) {
        session.markPropertyMissing(propertyName, e)
    } catch (e: Exception) {
        Log.w(TAG_BINDING, "Unexpected boolean write failure: $propertyName", e)
    }
}

private fun ViewModelInstance.setStringProperty(
    propertyName: String,
    value: String,
    session: RiveRuntimeSession
) {
    try {
        val property = getStringProperty(propertyName)
        if (property != null && property.value != value) {
            property.value = value
            session.logWrite("viewModel", propertyName, value)
            session.notifyObservers(propertyName, value)
        }
        session.markPropertyAvailable(propertyName)
    } catch (e: ViewModelException) {
        session.markPropertyMissing(propertyName, e)
    } catch (e: Exception) {
        Log.w(TAG_BINDING, "Unexpected string write failure: $propertyName", e)
    }
}

private fun ViewModelInstance.triggerProperty(
    propertyName: String,
    session: RiveRuntimeSession
) : Boolean {
    try {
        val property = getTriggerProperty(propertyName)
        if (property != null) {
            property.trigger()
            session.logWrite("viewModel", propertyName, "trigger")
            session.notifyObservers(propertyName, "trigger")
            session.markPropertyAvailable(propertyName)
            return true
        }
        session.markPropertyMissing(propertyName)
    } catch (e: ViewModelException) {
        session.markPropertyMissing(propertyName, e)
    } catch (e: Exception) {
        Log.w(TAG_BINDING, "Unexpected trigger failure: $propertyName", e)
    }
    return false
}

private class StemKeyDispatcher(
    private val onTrigger: () -> Unit
) {
    private var lastDownTimestamp = 0L

    fun onKeyDown(event: KeyEvent): Boolean {
        lastDownTimestamp = event.eventTime
        Log.i(TAG_BINDING, "Stem key down intercepted")
        return true
    }

    fun onKeyUp(event: KeyEvent): Boolean {
        val pressDuration = event.eventTime - lastDownTimestamp
        Log.i(TAG_BINDING, "Stem key up after ${pressDuration}ms")
        onTrigger()
        return true
    }
}

private class PowerKeyDispatcher(
    private val onTrigger: () -> Unit
) {
    private var lastDownTimestamp = 0L

    fun onKeyDown(event: KeyEvent): Boolean {
        lastDownTimestamp = event.eventTime
        Log.i(TAG_BINDING, "Power key down intercepted")
        return true
    }

    fun onKeyUp(event: KeyEvent): Boolean {
        val pressDuration = event.eventTime - lastDownTimestamp
        Log.i(TAG_BINDING, "Power key up after ${pressDuration}ms")
        onTrigger()
        return true
    }
}

private fun isStemKeyCode(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
        keyCode == KeyEvent.KEYCODE_F1 ||
        keyCode == KeyEvent.KEYCODE_F2
}
