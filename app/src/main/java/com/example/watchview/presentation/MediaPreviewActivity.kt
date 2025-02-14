package com.example.watchview.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.watchview.presentation.theme.WatchViewTheme

class MediaPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用向上导航
        actionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 从 Intent 中获取媒体文件路径列表
        val mediaFiles = intent.getStringArrayListExtra("media_files") ?: arrayListOf()
        val mediaTypes = intent.getStringArrayListExtra("media_types") ?: arrayListOf()
        
        setContent {
            WatchViewTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 使用现有的 ZipViewer 组件
                    ZipViewer(
                        media = mediaFiles.zip(mediaTypes).map { (path, type) ->
                            MediaFile(
                                file = java.io.File(path),
                                type = MediaType.valueOf(type)
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
} 