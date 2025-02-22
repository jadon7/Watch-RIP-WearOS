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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.watchview.presentation.theme.WatchViewTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberScrollState
import android.util.Log
import app.rive.runtime.kotlin.controllers.RiveFileController
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build

class RivePreviewActivity : ComponentActivity() {
    // 修改变量名和注释以反映新的交互方式
    private var riveView: RiveAnimationView? = null
    private lateinit var vibrator: Vibrator
    
    // 添加电池电量状态
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

    // 添加 Rive 事件监听器
    private val eventListener = object : RiveFileController.RiveEventListener {
        override fun notifyEvent(event: app.rive.runtime.kotlin.core.RiveEvent) {
            Log.i("RiveEvent", "Event received: ${event.name}")
            Log.i("RiveEvent", "Event type: ${event.type}")
            Log.i("RiveEvent", "Event properties: ${event.properties}")
            Log.i("RiveEvent", "Event data: ${event.data}")
            
            // 检查是否是振动事件
            if (event.name == "vibratory") {
                try {
                    val state = event.properties?.get("state") as? Number
                    Log.d("RiveEvent", "Vibration state: $state")
                    if (state?.toDouble() == 1.0) {
                        Log.d("RiveEvent", "Triggering vibration")
                        // 在主线程中触发振动
                        runOnUiThread {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // 降低振动强度（1-255），设置为较低的值
                                val amplitude = 100 // 降低振动强度到 50（约20%的强度）
                                val duration = 40L // 缩短振动时间到 50ms
                                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(50) // 老版本 Android 只调整时长
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RiveEvent", "Error triggering vibration", e)
                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        
        // 启用向上导航
        actionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 从 Intent 中获取 Rive 文件路径
        val filePath = intent.getStringExtra("file_path") ?: return
        
        setContent {
            WatchViewTheme {
                // 添加对话框状态
                val showDialog = remember { mutableStateOf(false) }
                
                if (showDialog.value) {
                    Dialog(
                        onDismissRequest = { showDialog.value = false },
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
                                .clickable { showDialog.value = false }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2196F3))
                                        .clickable {
                                            showDialog.value = false
                                            // 重新播放当前 Rive 文件
                                            riveView?.let {
                                                it.reset()
                                                it.play()
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
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2196F3))
                                        .clickable {
                                            showDialog.value = false
                                            finish() // 退出预览
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
                                    // 只检测双指点击
                                    if (event.changes.size == 2) {
                                        showDialog.value = true
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
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(batteryReceiver)
        // 移除 Rive 事件监听器
        riveView?.removeEventListener(eventListener)
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
    // 现有的时间状态
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toFloat()) }
    var currentMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE).toFloat()) }
    var currentSecond by remember { mutableStateOf(0f) }
    var currentBattery by remember { mutableStateOf(batteryLevel) }
    
    // 添加日期相关状态
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1f) } // 月份从0开始，需要+1
    var currentDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toFloat()) }
    var currentWeek by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1f) } // 转换为0-6

    // 每小时更新日期相关状态
    LaunchedEffect(Unit) {
        while (true) {
            val calendar = Calendar.getInstance()
            currentMonth = (calendar.get(Calendar.MONTH) + 1).toFloat()
            currentDay = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
            currentWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 1).toFloat() // 周日为1，转换为0
            delay(60 * 60 * 1000L) // 1小时
        }
    }

    // 每5分钟更新小时，保留1位小数
    LaunchedEffect(Unit) {
        while (true) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            currentHour = (hour + minute / 60f).round(1)
            delay(5 * 60 * 1000L) // 5分钟
        }
    }
    
    // 每5秒更新分钟，保留1位小数
    LaunchedEffect(Unit) {
        while (true) {
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            val second = Calendar.getInstance().get(Calendar.SECOND)
            currentMinute = (minute + second / 60f).round(1)
            delay(5000L) // 5秒
        }
    }
    
    // 每0.1秒更新秒钟，保留2位小数
    LaunchedEffect(Unit) {
        while (true) {
            val second = Calendar.getInstance().get(Calendar.SECOND)
            val millis = Calendar.getInstance().get(Calendar.MILLISECOND)
            currentSecond = (second + millis / 1000f).round(2)
            delay(100L) // 0.1秒，每秒10次
        }
    }

    // 添加电池更新的 LaunchedEffect
    LaunchedEffect(batteryLevel) {
        while (true) {
            currentBattery = batteryLevel
            delay(5 * 60 * 1000L) // 5分钟
        }
    }

    AndroidView(
        factory = { context ->
            RiveAnimationView(context).apply {
                val riveFile = RiveCoreFile(file.readBytes())
                setRiveFile(riveFile)
                autoplay = true
                // 添加事件监听器
                addEventListener(eventListener)
                onRiveViewCreated(this)

                val artboard = riveFile.firstArtboard
                val smNames = artboard?.stateMachineNames ?: emptyList()
                
                if (smNames.isNotEmpty()) {
                    val firstMachineName = smNames[0]
                    val firstMachine = artboard?.stateMachine(firstMachineName)
                    
                    // 安全地设置输入值
                    fun safeSetNumberState(inputName: String, value: Float) {
                        try {
                            if (firstMachine?.inputs?.any { it.name == inputName } == true) {
                                this@apply.setNumberState(firstMachineName, inputName, value)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 初始化所有可能的输入值
                    safeSetNumberState("timeHour", currentHour)
                    safeSetNumberState("timeMinute", currentMinute)
                    safeSetNumberState("timeSecond", currentSecond)
                    safeSetNumberState("systemStatusBattery", currentBattery)
                    // 添加日期相关输入
                    safeSetNumberState("dateMonth", currentMonth)
                    safeSetNumberState("dateDay", currentDay)
                    safeSetNumberState("dateWeek", currentWeek)
                }
            }
        },
        update = { riveView ->
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
                        e.printStackTrace()
                    }
                }

                // 更新所有可能的输入值
                safeSetNumberState("timeHour", currentHour)
                safeSetNumberState("timeMinute", currentMinute)
                safeSetNumberState("timeSecond", currentSecond)
                safeSetNumberState("systemStatusBattery", currentBattery)
                // 添加日期相关输入的更新
                safeSetNumberState("dateMonth", currentMonth)
                safeSetNumberState("dateDay", currentDay)
                safeSetNumberState("dateWeek", currentWeek)
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