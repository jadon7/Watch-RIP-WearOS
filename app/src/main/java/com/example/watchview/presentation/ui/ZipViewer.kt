package com.example.watchview.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.MaterialTheme
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.rememberPagerState

/**
 * ZIP 文件内容查看器组件
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun ZipViewer(media: List<MediaFile>) {
    println("ZipViewer called with ${media.size} files") // 调试日志

    if (media.isEmpty()) {
        Text(
            "没有可显示的媒体文件",
            color = MaterialTheme.colors.onBackground,
            fontSize = 14.sp
        )
        return
    }

    val pagerState = rememberPagerState()
    var currentPlayingPage by remember { mutableStateOf<Int?>(null) }
    
    // 添加页面变化监听
    LaunchedEffect(pagerState.currentPage) {
        println("Page changed to: ${pagerState.currentPage}")
        println("Current file: ${media[pagerState.currentPage].file.absolutePath}")
        println("File type: ${media[pagerState.currentPage].type}")
        
        currentPlayingPage = if (media[pagerState.currentPage].type == MediaType.VIDEO) {
            pagerState.currentPage
        } else {
            null
        }
    }

    VerticalPager(
        count = media.size,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        println("Rendering page $page") // 调试日志
        when (media[page].type) {
            MediaType.IMAGE -> {
                println("Rendering image: ${media[page].file.absolutePath}") // 调试日志
                AndroidView(
                    factory = { context ->
                        android.widget.ImageView(context).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { view ->
                        try {
                            val bitmap = android.graphics.BitmapFactory.decodeFile(media[page].file.absolutePath)
                            view.setImageBitmap(bitmap)
                            println("Image loaded successfully") // 调试日志
                        } catch (e: Exception) {
                            println("Error loading image: ${e.message}")
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaType.VIDEO -> {
                println("Rendering video: ${media[page].file.absolutePath}") // 调试日志
                AndroidView(
                    factory = { context ->
                        android.widget.VideoView(context).apply {
                            setVideoPath(media[page].file.absolutePath)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                println("Video prepared, page: $page, currentPlaying: $currentPlayingPage") // 调试日志
                                if (page == currentPlayingPage) {
                                    start()
                                }
                            }
                        }
                    },
                    update = { view ->
                        if (page == currentPlayingPage) {
                            if (!view.isPlaying) {
                                println("Starting video on page $page") // 调试日志
                                view.start()
                            }
                        } else {
                            if (view.isPlaying) {
                                println("Pausing video on page $page") // 调试日志
                                view.pause()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
} 