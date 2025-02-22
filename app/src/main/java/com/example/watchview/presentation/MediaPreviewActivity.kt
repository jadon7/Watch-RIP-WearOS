package com.example.watchview.presentation

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.example.watchview.presentation.theme.WatchViewTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import android.util.Log

class MediaPreviewActivity : ComponentActivity() {
    private lateinit var wearableRecyclerView: WearableRecyclerView
    private var currentPosition = 0
    
    // 自定义的滚动回调，用于实现渐变缩放效果
    private inner class CustomScrollingLayoutCallback : WearableLinearLayoutManager.LayoutCallback() {
        private var progressToCenter: Float = 0f
        
        override fun onLayoutFinished(child: View, parent: RecyclerView) {
            child.apply {
                // 计算子视图到中心的距离
                val centerOffset = height.toFloat() / 2.0f
                val childCenter = (top + bottom) / 2.0f
                val parentCenter = parent.height / 2.0f
                progressToCenter = abs(1f - abs(parentCenter - childCenter) / centerOffset)
                
                // 应用缩放效果
                val scale = 0.85f + (0.15f * progressToCenter)
                scaleX = scale
                scaleY = scale
                
                // 应用透明度效果
                alpha = 0.6f + (0.4f * progressToCenter)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用向上导航
        actionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 从 Intent 中获取媒体文件路径列表
        val mediaFiles = intent.getStringArrayListExtra("media_files") ?: arrayListOf()
        val mediaTypes = intent.getStringArrayListExtra("media_types") ?: arrayListOf()
        
        // 将路径和类型转换为 MediaFile 列表
        val mediaList = mediaFiles.zip(mediaTypes).map { (path, type) ->
            MediaFile(
                file = java.io.File(path),
                type = MediaType.valueOf(type)
            )
        }

        // 创建并配置 WearableRecyclerView
        wearableRecyclerView = WearableRecyclerView(this).apply {
            // 设置视图为可聚焦
            isFocusable = true
            isFocusableInTouchMode = true

            // 使用自定义的 LayoutManager，设置较小的滚动阻尼
            val layoutManager = WearableLinearLayoutManager(context, CustomScrollingLayoutCallback())
            layoutManager.apply {
                // 设置较小的滚动阻尼，使滚动更流畅
                setScrollDegreesPerScreen(90f)
            }
            this.layoutManager = layoutManager
            
            isVerticalScrollBarEnabled = false
            isEdgeItemsCenteringEnabled = true
            
            // 添加 PagerSnapHelper 实现翻页效果，但设置较大的阈值
            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)
            
            // 请求焦点以接收旋钮事件
            requestFocus()
            
            adapter = object : RecyclerView.Adapter<MediaViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
                    val layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        parent.height  // 设置高度为父容器高度，确保每个item填充整个屏幕
                    )
                    
                    return when (viewType) {
                        MediaType.IMAGE.ordinal -> {
                            val imageView = ImageView(context).apply {
                                this.layoutParams = layoutParams
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                            MediaViewHolder.ImageViewHolder(imageView)
                        }
                        else -> { // VIDEO
                            val videoView = VideoView(context).apply {
                                this.layoutParams = layoutParams
                            }
                            MediaViewHolder.VideoViewHolder(videoView)
                        }
                    }
                }

                override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
                    val media = mediaList[position]
                    when (holder) {
                        is MediaViewHolder.ImageViewHolder -> {
                            val bitmap = android.graphics.BitmapFactory.decodeFile(media.file.absolutePath)
                            holder.imageView.setImageBitmap(bitmap)
                        }
                        is MediaViewHolder.VideoViewHolder -> {
                            holder.videoView.apply {
                                setVideoPath(media.file.absolutePath)
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true
                                    start()
                                }
                            }
                        }
                    }
                }

                override fun getItemCount() = mediaList.size

                override fun getItemViewType(position: Int): Int {
                    return mediaList[position].type.ordinal
                }
            }
            
            // 添加滚动监听器来处理视频播放和更新当前位置，并记录滚动状态
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var currentPlayingPosition = 0
                private var lastScrollPosition = 0f
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    // 记录滚动位置和速度
                    val firstVisibleItem = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                    val firstVisibleItemView = recyclerView.layoutManager?.findViewByPosition(firstVisibleItem)
                    val offset = firstVisibleItemView?.top ?: 0
                    
                    Log.d("ScrollDebug", "Scrolled: dx=$dx, dy=$dy, position=$firstVisibleItem, offset=$offset")
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            Log.d("ScrollDebug", "Scroll State: IDLE")
                            // 获取当前中心位置的item
                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            val centerPosition = layoutManager.findFirstVisibleItemPosition()
                            currentPosition = centerPosition
                            
                            if (centerPosition != currentPlayingPosition) {
                                Log.d("ScrollDebug", "Switching video: from $currentPlayingPosition to $centerPosition")
                                // 暂停之前的视频
                                val previousHolder = recyclerView.findViewHolderForAdapterPosition(currentPlayingPosition)
                                if (previousHolder is MediaViewHolder.VideoViewHolder) {
                                    previousHolder.videoView.pause()
                                }
                                
                                // 播放当前位置的视频
                                val currentHolder = recyclerView.findViewHolderForAdapterPosition(centerPosition)
                                if (currentHolder is MediaViewHolder.VideoViewHolder) {
                                    currentHolder.videoView.start()
                                }
                                
                                currentPlayingPosition = centerPosition
                            }
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            Log.d("ScrollDebug", "Scroll State: DRAGGING")
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            Log.d("ScrollDebug", "Scroll State: SETTLING")
                        }
                    }
                }
            })
        }

        // 使用 ComposeView 作为容器
        setContentView(ComposeView(this).apply {
            setContent {
                WatchViewTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { 
                                wearableRecyclerView.apply {
                                    // 确保在 UI 线程中请求焦点
                                    post { requestFocus() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 在 Activity 恢复时也请求焦点
        wearableRecyclerView.requestFocus()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d("RotaryScroll", "Activity received motion event: ${event.action}")
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
        ) {
            Log.d("RotaryScroll", "Processing rotary event in Activity")
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            Log.d("RotaryScroll", "Raw delta: $delta")
            
            // 将旋转角度转换为滚动距离
            val scrollAmount = (delta * 3000).toInt() // 调整这个乘数可以改变滚动灵敏度
            Log.d("RotaryScroll", "Scroll amount: $scrollAmount")
            
            // 使用 smoothScrollBy 进行平滑滚动
            wearableRecyclerView.smoothScrollBy(0, scrollAmount)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}

sealed class MediaViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
    class ImageViewHolder(val imageView: ImageView) : MediaViewHolder(imageView)
    class VideoViewHolder(val videoView: VideoView) : MediaViewHolder(videoView)
} 