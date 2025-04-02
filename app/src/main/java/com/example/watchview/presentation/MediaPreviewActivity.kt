package com.example.watchview.presentation

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
// import android.view.ViewConfiguration // Not used currently
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.width // Not directly used
import androidx.compose.foundation.shape.CircleShape // Correct import
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.clip // Not directly used
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material.Text // Using Material Text for Dialog
import androidx.wear.compose.material.Button // Using Wear Button for Dialog
import androidx.wear.compose.material.ButtonDefaults // Using Wear ButtonDefaults for Dialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import com.example.watchview.presentation.theme.WatchViewTheme
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.abs
// import kotlin.math.min // Not used currently
import kotlin.math.roundToInt
import android.util.Log
import androidx.core.view.InputDeviceCompat

class MediaPreviewActivity : ComponentActivity() {
    private lateinit var wearableRecyclerView: WearableRecyclerView
    private var currentPosition = 0
    private var originalZipPath: String? = null
    private var isSavedList: Boolean = false
    @Volatile private var hasBeenSavedInThisSession: Boolean = false
    
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
        
        // 从 Intent 中获取媒体文件路径列表、原始 ZIP 路径和保存标记
        val mediaFiles = intent.getStringArrayListExtra("media_files") ?: arrayListOf()
        val mediaTypes = intent.getStringArrayListExtra("media_types") ?: arrayListOf()
        originalZipPath = intent.getStringExtra("zip_file_path")
        isSavedList = intent.getBooleanExtra("is_saved_list", false)
        
        // 将路径和类型转换为 MediaFile 列表
        val mediaList = mediaFiles.zip(mediaTypes).map { (path, type) ->
            MediaFile(
                file = File(path),
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
                                    if (position == currentPosition) {
                                        start()
                                    } else {
                                        pause()
                                    }
                                }
                                setOnTouchListener { _, event ->
                                    true
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
                private var currentPlayingPosition = -1
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    Log.d("ScrollDebug", "Scrolled: dx=$dx, dy=$dy")
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            Log.d("ScrollDebug", "Scroll State: IDLE")
                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            val centerPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                            if (centerPosition == RecyclerView.NO_POSITION) {
                                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                if (firstVisible != RecyclerView.NO_POSITION) {
                                    currentPosition = firstVisible
                                } else {
                                    Log.w("ScrollDebug", "Could not find any visible item in IDLE state")
                                    return
                                }
                            } else {
                                currentPosition = centerPosition
                            }
                            Log.d("ScrollDebug", "Current center position: $currentPosition")

                            if (currentPosition != currentPlayingPosition) {
                                Log.d("ScrollDebug", "Switching video play state: new=$currentPosition, old=$currentPlayingPosition")
                                if (currentPlayingPosition != -1) {
                                    val previousHolder = recyclerView.findViewHolderForAdapterPosition(currentPlayingPosition)
                                    if (previousHolder is MediaViewHolder.VideoViewHolder) {
                                        Log.d("ScrollDebug", "Pausing video at position $currentPlayingPosition")
                                        previousHolder.videoView.pause()
                                    }
                                }

                                val currentHolder = recyclerView.findViewHolderForAdapterPosition(currentPosition)
                                if (currentHolder is MediaViewHolder.VideoViewHolder) {
                                    Log.d("ScrollDebug", "Starting video at position $currentPosition")
                                    currentHolder.videoView.start()
                                    currentPlayingPosition = currentPosition
                                } else {
                                    currentPlayingPosition = -1
                                }
                            } else {
                                val currentHolder = recyclerView.findViewHolderForAdapterPosition(currentPosition)
                                if (currentHolder is MediaViewHolder.VideoViewHolder && !currentHolder.videoView.isPlaying) {
                                    Log.d("ScrollDebug", "Ensuring video is playing at position $currentPosition")
                                    currentHolder.videoView.start()
                                    currentPlayingPosition = currentPosition
                                }
                            }
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            Log.d("ScrollDebug", "Scroll State: DRAGGING")
                            if (currentPlayingPosition != -1) {
                                val playingHolder = recyclerView.findViewHolderForAdapterPosition(currentPlayingPosition)
                                if (playingHolder is MediaViewHolder.VideoViewHolder) {
                                    Log.d("ScrollDebug", "Pausing video during DRAG at $currentPlayingPosition")
                                    playingHolder.videoView.pause()
                                }
                            }
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            Log.d("ScrollDebug", "Scroll State: SETTLING")
                            if (currentPlayingPosition != -1) {
                                val playingHolder = recyclerView.findViewHolderForAdapterPosition(currentPlayingPosition)
                                if (playingHolder is MediaViewHolder.VideoViewHolder) {
                                    Log.d("ScrollDebug", "Pausing video during SETTLE at $currentPlayingPosition")
                                    playingHolder.videoView.pause()
                                }
                            }
                        }
                    }
                }
            })
        }

        // 使用 ComposeView 作为容器
        setContentView(ComposeView(this).apply {
            setContent {
                WatchViewTheme {
                    val coroutineScope = rememberCoroutineScope()
                    // Dialog 显示状态
                    val showDialog = remember { mutableStateOf(false) }
                    // 用于更新 UI 的保存状态 (反映 isSavedList 和 hasBeenSavedInThisSession)
                    var uiSavedState by remember { mutableStateOf(isSavedList || hasBeenSavedInThisSession) }
                    
                    // 监听 hasBeenSavedInThisSession 的变化来更新 UI 状态
                    LaunchedEffect(hasBeenSavedInThisSession) {
                        uiSavedState = isSavedList || hasBeenSavedInThisSession
                    }

                    // 主内容 Box
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        // 检测双指按下事件 (PointerEventType.Press 更精确)
                                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press && event.changes.size >= 2) {
                                            Log.d("GestureDebug", "Double finger press detected.")
                                            showDialog.value = true // 显示对话框
                                            // 消费掉事件，防止其他处理 (可选，看是否干扰滚动)
                                            // event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    ) {
                        AndroidView(
                            factory = {
                                wearableRecyclerView.apply {
                                    post { requestFocus() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 对话框 Composable
                    if (showDialog.value) {
                        Dialog(
                            onDismissRequest = {
                                Log.d("DialogDebug", "Dialog dismissed by outside click or back press")
                                showDialog.value = false
                            },
                            properties = DialogProperties(
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                                securePolicy = SecureFlagPolicy.Inherit,
                                usePlatformDefaultWidth = false // 确保对话框可以填满屏幕
                            )
                        ) {
                            // 半透明背景，点击可关闭
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
                                // 按钮容器
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 48.dp)
                                        .clickable(enabled = false) {}, // 防止点击传递到背景
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp) // 按钮间距
                                ) {
                                    // 保存文件按钮 (或显示已保存状态)
                                    val canSave = originalZipPath != null && !isSavedList && !hasBeenSavedInThisSession

                                    if (canSave) {
                                        // 可点击的保存按钮
                                        Button(
                                            onClick = {
                                                Log.d("DialogDebug", "Save button clicked")
                                                coroutineScope.launch {
                                                    try {
                                                        val sourceFile = File(originalZipPath!!)
                                                        if (!sourceFile.exists()) {
                                                            Log.e("SaveDebug", "Source ZIP file not found: $originalZipPath")
                                                            // 可以显示错误提示
                                                            Toast.makeText(this@MediaPreviewActivity, "源文件丢失，无法保存", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }
                                                        
                                                        val saveDir = File(filesDir, "saved_rive")
                                                        if (!saveDir.exists()) {
                                                            saveDir.mkdirs()
                                                        }

                                                        val originalName = sourceFile.name
                                                        val baseName = originalName.substringBeforeLast(".")
                                                        val extension = originalName.substringAfterLast(".", "zip")
                                                        var index = 1
                                                        var fileName = originalName
                                                        var savedFile = File(saveDir, fileName)

                                                        while (savedFile.exists()) {
                                                            fileName = "${baseName}_${index}.${extension}"
                                                            savedFile = File(saveDir, fileName)
                                                            index++
                                                        }

                                                        sourceFile.copyTo(savedFile, overwrite = true)
                                                        Log.d("SaveDebug", "List (ZIP) saved successfully: ${savedFile.absolutePath}")

                                                        // 更新状态并关闭对话框
                                                        hasBeenSavedInThisSession = true
                                                        showDialog.value = false

                                                        // 显示 Toast
                                                        Toast.makeText(this@MediaPreviewActivity, "已保存", Toast.LENGTH_SHORT).show()

                                                    } catch (e: Exception) {
                                                        Log.e("SaveDebug", "Error saving list (ZIP)", e)
                                                        Toast.makeText(this@MediaPreviewActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        showDialog.value = false // 出错也关闭对话框
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp),
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)), // 绿色
                                            shape = CircleShape
                                        ) {
                                            Text("保存文件", color = Color.White, fontSize = 14.sp)
                                        }
                                    }

                                    // 退出预览按钮
                                    Button(
                                        onClick = {
                                            Log.d("DialogDebug", "Exit button clicked")
                                            showDialog.value = false
                                            finish() // 退出预览
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE53935)), // 红色
                                        shape = CircleShape
                                    ) {
                                        Text("退出预览", color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        wearableRecyclerView.post { wearableRecyclerView.requestFocus() }
        val holder = wearableRecyclerView.findViewHolderForAdapterPosition(currentPosition)
        if (holder is MediaViewHolder.VideoViewHolder) {
            holder.videoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        for (i in 0 until wearableRecyclerView.adapter!!.itemCount) {
            val holder = wearableRecyclerView.findViewHolderForAdapterPosition(i)
            if (holder is MediaViewHolder.VideoViewHolder) {
                holder.videoView.pause()
            }
        }
        if (!isChangingConfigurations && !isSavedList) {
            val mediaListFiles = intent.getStringArrayListExtra("media_files")
            val firstFilePath = mediaListFiles?.firstOrNull()
            firstFilePath?.let { path ->
                val parentDir = File(path).parentFile
                parentDir?.let { dir ->
                    if (dir.exists() && (dir.name.startsWith("unzipped_") || dir.name == "unzipped_saved_preview")) {
                        Log.d("Cleanup", "Deleting preview directory: ${dir.absolutePath}")
                        dir.deleteRecursively()
                    }
                }
            }
        }
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
            
            val scrollAmount = (delta * 3000).toInt()
            Log.d("RotaryScroll", "Scroll amount: $scrollAmount")
            
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