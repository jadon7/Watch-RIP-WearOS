package com.example.watchview.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.watchview.presentation.theme.WatchViewTheme
import java.io.File

class RivePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用向上导航
        actionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 从 Intent 中获取 Rive 文件路径
        val filePath = intent.getStringExtra("file_path") ?: return
        
        setContent {
            WatchViewTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    RivePlayer(file = File(filePath))
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
} 