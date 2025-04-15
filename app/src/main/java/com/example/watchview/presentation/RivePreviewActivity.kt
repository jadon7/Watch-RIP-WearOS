package com.example.watchview.presentation

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
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
import app.rive.runtime.kotlin.core.SMINumber
import java.util.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import app.rive.runtime.kotlin.controllers.RiveFileController
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.Environment
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

class RivePreviewActivity : ComponentActivity() {
    // 使用强引用持有RiveView
    private var riveView: RiveAnimationView? = null
    private lateinit var vibrator: Vibrator
    
    // 添加错误计数器
    private var errorCount = 0
    private val maxErrorCount = 3
    
    // 添加电池电量状态
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()
    
    // 添加协程作用域
    private val activityScope = MainScope()

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
            Log.d("RivePreviewActivity", "收到广播: ${intent.action}")
            val actionToTake = intent.action // 保存 action

            // 先完成关闭 Activity 的操作
            Log.i("RivePreviewActivity", "收到广播 ($actionToTake)，准备关闭当前预览...")
            finish()

            // 使用 CoroutineScope 在后台执行延迟和发送新广播
            CoroutineScope(Dispatchers.Main).launch {
                 // 等待 Activity 真正关闭可能需要一点时间，延迟 1 秒确保 finish() 生效
                 // 并满足用户1秒后触发的需求
                delay(1000L)
                Log.d("RivePreviewActivity", "延迟结束，发送 ACTION_TRIGGER_WIRED_PREVIEW 广播")
                val triggerIntent = Intent(ACTION_TRIGGER_WIRED_PREVIEW)
                context.sendBroadcast(triggerIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            
            // 注册广播接收器，监听两个 Action
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_NEW_ADB_FILE)
                addAction("com.example.watchview.CLOSE_PREVIEW")
            }
            registerReceiver(
                newFileReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED // 这个 Receiver 只接收来自应用内部的广播，不需要导出
            )
            
            // 增加广播接收标记，便于调试
            Log.d("RivePreviewActivity", "已注册广播接收器: ACTION_NEW_ADB_FILE, com.example.watchview.CLOSE_PREVIEW")
            
            // 启用向上导航
            actionBar?.setDisplayHomeAsUpEnabled(true)
            
            // 从 Intent 中获取文件路径和临时文件标记
            val filePath = intent.getStringExtra("file_path") ?: return
            val isTempFile = intent.getBooleanExtra("is_temp_file", false)
            // 添加检查以确定文件是否为 ADB 传输的文件
            val isExternalFile = isExternalStorageFile(filePath)
            
            Log.d("RivePreviewActivity", "File path: $filePath, isTempFile: $isTempFile, isExternalFile: $isExternalFile")
            
            setContent {
                WatchViewTheme {
                    // 使用 remember 来保持状态
                    val showDialog = remember { mutableStateOf(false) }
                    var isSaved by remember { mutableStateOf(false) }
                    
                    // 添加错误状态监听
                    LaunchedEffect(errorCount) {
                        if (errorCount >= maxErrorCount) {
                            finish()
                        }
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
                                                        val tempFile = File(filePath)
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
                        RivePlayerUI(
                            file = File(filePath),
                            batteryLevel = batteryLevel.collectAsState().value,
                            onRiveViewCreated = { view -> riveView = view },
                            eventListener = eventListener
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
            // 移除 Rive 事件监听器和停止动画
            riveView?.let {
                it.removeEventListener(eventListener)
                it.stop()
            }
            riveView = null
        } catch (e: Exception) {
            Log.e("RivePreviewActivity", "Error in onDestroy", e)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}

@Composable
fun RivePlayerUI(
    file: java.io.File,
    batteryLevel: Float,
    onRiveViewCreated: (RiveAnimationView) -> Unit,
    eventListener: RiveFileController.RiveEventListener
) {
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toFloat()) }
    var currentMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE).toFloat()) }
    var currentSecond by remember { mutableStateOf(0f) }
    var currentBattery by remember { mutableStateOf(batteryLevel) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1f) }
    var currentDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toFloat()) }
    var currentWeek by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1f) }

    // 减少更新频率，使用单个协程管理所有更新
    LaunchedEffect(Unit) {
        while (isActive) { // 检查协程是否仍然活跃
            try {
                val calendar = Calendar.getInstance()
                
                // 更新所有时间相关状态
                currentHour = (calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f).round(1)
                currentMinute = (calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60f).round(1)
                currentSecond = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f).round(2)
                currentMonth = (calendar.get(Calendar.MONTH) + 1).toFloat()
                currentDay = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
                currentWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 1).toFloat()
                currentBattery = batteryLevel
                
                // 使用较长的延迟时间
                delay(1000L) // 每秒更新一次
            } catch (e: Exception) {
                // 如果是取消异常，应该重新抛出以停止协程
                if (e is CancellationException) throw e
                Log.e("RivePlayerUI", "Error updating time", e)
            }
        }
    }

    AndroidView(
        factory = { context ->
            try {
                RiveAnimationView(context).apply {
                    val riveFile = RiveCoreFile(file.readBytes())
                    setRiveFile(riveFile)
                    autoplay = true
                    addEventListener(eventListener)
                    onRiveViewCreated(this)
                }
            } catch (e: Exception) {
                Log.e("RivePlayerUI", "Error creating RiveAnimationView", e)
                throw e
            }
        },
        update = { riveView ->
            try {
                val artboard = riveView.file?.firstArtboard
                val smNames = artboard?.stateMachineNames ?: emptyList()
                
                if (smNames.isNotEmpty()) {
                    val firstMachineName = smNames[0]
                    
                    fun safeSetNumberState(inputName: String, value: Float) {
                        try {
                            val firstMachine = artboard?.stateMachine(firstMachineName)
                            if (firstMachine?.inputs?.any { it.name == inputName } == true) {
                                riveView.setNumberState(firstMachineName, inputName, value)
                            }
                        } catch (e: Exception) {
                            Log.e("RivePlayerUI", "Error setting state: $inputName = $value", e)
                        }
                    }

                    safeSetNumberState("timeHour", currentHour)
                    safeSetNumberState("timeMinute", currentMinute)
                    safeSetNumberState("timeSecond", currentSecond)
                    safeSetNumberState("systemStatusBattery", currentBattery)
                    safeSetNumberState("dateMonth", currentMonth)
                    safeSetNumberState("dateDay", currentDay)
                    safeSetNumberState("dateWeek", currentWeek)
                }
            } catch (e: Exception) {
                Log.e("RivePlayerUI", "Error updating RiveAnimationView", e)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// 扩展函数：对 Float 进行四舍五入并保留指定位数的小数
private fun Float.round(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
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