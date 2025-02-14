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

class RivePreviewActivity : ComponentActivity() {
    // 保留类级别的变量用于 onTouchEvent
    private var touchStartTime = 0L
    private var isTwoFingerPressed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                    RivePlayer(file = File(filePath))
                }
            }
        }
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