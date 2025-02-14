package com.example.watchview.presentation

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

class RivePreviewActivity : ComponentActivity() {
    // 保留类级别的变量用于 onTouchEvent
    private var touchStartTime = 0L
    private var isTwoFingerPressed = false
    
    // 添加电池电量状态
    private val _batteryLevel = MutableStateFlow(0f)
    val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when {
                                        // 检测到两个手指按下
                                        event.changes.size == 2 && event.changes.all { it.pressed } -> {
                                            if (!isTwoFingerPressed) {
                                                touchStartTime = System.currentTimeMillis()
                                                isTwoFingerPressed = true
                                            } else {
                                                // 检查长按时间
                                                val currentTime = System.currentTimeMillis()
                                                if (currentTime - touchStartTime >= 1000) {
                                                    finish()
                                                }
                                            }
                                        }
                                        // 任何手指抬起，重置状态
                                        event.changes.any { !it.pressed } -> {
                                            isTwoFingerPressed = false
                                            touchStartTime = 0
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // 将电池电量传递给 RivePlayerUI
                    RivePlayerUI(
                        file = File(filePath),
                        batteryLevel = batteryLevel.collectAsState().value
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(batteryReceiver)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 检测到第二个手指按下
                if (event.pointerCount == 2) {
                    touchStartTime = System.currentTimeMillis()
                    isTwoFingerPressed = true
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                // 手指抬起，重置状态
                isTwoFingerPressed = false
                touchStartTime = 0
            }
            MotionEvent.ACTION_MOVE -> {
                // 如果是双指按住状态，检查持续时间
                if (isTwoFingerPressed && event.pointerCount == 2) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - touchStartTime >= 1000) { // 1秒
                        // 触发返回操作
                        finish()
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}

@Composable
fun RivePlayerUI(
    file: java.io.File,
    batteryLevel: Float
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