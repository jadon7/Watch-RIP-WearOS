package com.example.watchview.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.material.Text
import androidx.core.view.InputDeviceCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import com.example.watchview.presentation.model.DownloadType
import com.example.watchview.presentation.theme.WatchViewTheme
import com.example.watchview.presentation.ui.ACTION_NEW_ADB_FILE
import com.example.watchview.presentation.ui.EXTRA_FILE_PATH
import com.example.watchview.presentation.ui.EXTRA_FILE_TYPE
import com.example.watchview.utils.unzipSavedZipForPreview
import com.example.watchview.utils.unzipMedia
import java.io.File
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.watchview.presentation.ui.ACTION_TRIGGER_WIRED_PREVIEW

class MediaPreviewActivity : ComponentActivity() {
    private lateinit var wearableRecyclerView: WearableRecyclerView
    private var currentPosition = 0
    private var originalZipPath: String? = null
    private var isSavedList: Boolean = false
    @Volatile private var hasBeenSavedInThisSession: Boolean = false
    
    // 创建协程作用域
    private val coroutineScope = MainScope()
    
    // 用于存储所有 ExoPlayer 实例，方便管理生命周期
    private val players = mutableMapOf<Int, ExoPlayer>()
    
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
    
    // 添加新的文件接收广播
    private val newFileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MediaPreviewActivity", "收到广播: ${intent.action}")
            val actionToTake = intent.action // 保存 action

            // 先完成关闭 Activity 的操作
            Log.i("MediaPreviewActivity", "收到广播 ($actionToTake)，准备关闭当前预览...")
            releaseAllPlayers() // 释放播放器资源
            finish()

            // 使用 CoroutineScope 在后台执行延迟和发送新广播
            CoroutineScope(Dispatchers.Main).launch {
                // 等待 Activity 真正关闭可能需要一点时间，延迟 1 秒确保 finish() 生效
                // 并满足用户1秒后触发的需求
                delay(1000L)
                Log.d("MediaPreviewActivity", "延迟结束，发送 ACTION_TRIGGER_WIRED_PREVIEW 广播")
                val triggerIntent = Intent(ACTION_TRIGGER_WIRED_PREVIEW)
                context.sendBroadcast(triggerIntent)
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
        
        // 将路径和类型转换为 MediaFile 列表，并按文件名排序
        val mediaList = mediaFiles.zip(mediaTypes)
            .map { (path, type) ->
            MediaFile(
                    file = File(path),
                type = MediaType.valueOf(type)
            )
        }
            .sortedBy { mediaFile -> 
                // 提取文件名中的数字部分进行排序
                mediaFile.file.nameWithoutExtension.let { fileName ->
                    // 尝试提取文件名中的数字部分
                    val numberMatch = Regex("\\d+").find(fileName)
                    numberMatch?.value?.toIntOrNull() ?: Int.MAX_VALUE
                }
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
                        else -> { // VIDEO using ExoPlayer
                            val playerView = PlayerView(context).apply {
                                this.layoutParams = layoutParams
                                useController = false // 隐藏播放控制器
                            }
                            MediaViewHolder.PlayerViewHolder(playerView)
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
                        is MediaViewHolder.PlayerViewHolder -> {
                            // 获取或创建 ExoPlayer 实例
                            val player = players.getOrPut(position) {
                                ExoPlayer.Builder(context).build().apply {
                                    repeatMode = Player.REPEAT_MODE_ONE // 循环播放
                                }
                            }
                            holder.player = player
                            holder.playerView.player = player

                            // 设置媒体项并准备播放
                            val mediaItem = MediaItem.fromUri(media.file.toURI().toString())
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            // 只有当前项才自动播放
                            player.playWhenReady = (position == currentPosition)
                        }
                    }
                }

                override fun getItemCount() = mediaList.size

                override fun getItemViewType(position: Int): Int {
                    return mediaList[position].type.ordinal
                }
                
                // 在 ViewHolder 回收时释放视频资源
                override fun onViewRecycled(holder: MediaViewHolder) {
                    super.onViewRecycled(holder)
                    if (holder is MediaViewHolder.PlayerViewHolder) {
                        Log.d("ExoPlayerRecycle", "Recycling player view for holder at position ${holder.bindingAdapterPosition}")
                        holder.player?.let { player ->
                            player.stop()
                            player.release()
                            players.remove(holder.bindingAdapterPosition) // 从 map 中移除
                            Log.d("ExoPlayerRecycle", "Released player for position ${holder.bindingAdapterPosition}")
                        }
                        holder.playerView.player = null // 解除绑定
                        holder.player = null
                    }
                }
            }
            
            // 添加滚动监听器来处理视频播放和更新当前位置，并记录滚动状态
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var settlingPosition = RecyclerView.NO_POSITION
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    Log.d("ScrollDebug", "Scrolled: dx=$dx, dy=$dy")
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            Log.d("ScrollDebug", "Scroll State: IDLE")
                            // 使用 PagerSnapHelper 找到居中的视图
                            val snapView = snapHelper.findSnapView(layoutManager)
                            val centerPosition = snapView?.let { layoutManager.getPosition(it) } ?: RecyclerView.NO_POSITION

                            if (centerPosition != RecyclerView.NO_POSITION) {
                                Log.d("ScrollDebug", "New center position: $centerPosition")
                                if (currentPosition != centerPosition) {
                                    // 停止之前的播放器 (如果存在且不同)
                                    players[currentPosition]?.playWhenReady = false
                                    Log.d("ExoPlayerControl", "Pausing player at old position: $currentPosition")

                            currentPosition = centerPosition
                            
                                    // 启动新的播放器
                                    players[currentPosition]?.let {
                                        it.playWhenReady = true
                                        Log.d("ExoPlayerControl", "Playing player at new position: $currentPosition")
                                    }
                                } else {
                                    // 如果位置没变，确保它在播放
                                     players[currentPosition]?.let {
                                         if (!it.isPlaying) {
                                             it.playWhenReady = true
                                             Log.d("ExoPlayerControl", "Resuming player at position: $currentPosition")
                                         }
                                     }
                                }
                            } else {
                                Log.w("ScrollDebug", "Could not find snap view in IDLE state")
                                // 尝试暂停所有播放器作为后备
                                pauseAllPlayers()
                            }
                            settlingPosition = RecyclerView.NO_POSITION // Reset settling state
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            Log.d("ScrollDebug", "Scroll State: DRAGGING")
                            // 拖动时暂停所有播放器
                            pauseAllPlayers()
                            settlingPosition = RecyclerView.NO_POSITION // Reset settling state
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            Log.d("ScrollDebug", "Scroll State: SETTLING")
                             // 在滚动动画期间也暂停所有播放器
                             pauseAllPlayers()
                             // Optional: Try to predict target position if needed, but pausing all is safer
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
                                            // 消费掉事件，防止其他处理
                                            event.changes.forEach { it.consume() }
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
                                                        Toast.makeText(this@MediaPreviewActivity, "已成功保存到本地", Toast.LENGTH_SHORT).show()

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
                                    } else if (isSavedList || hasBeenSavedInThisSession) { // Show saved state if it's a saved list OR saved in this session
                                        // 显示已保存状态
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .background(Color(0xFF388E3C), shape = CircleShape), // Slightly darker green for saved
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("已保存", color = Color.White, fontSize = 14.sp)
                                        }
                                    }

                                    // 退出预览按钮
                                    Button(
                                        onClick = {
                                            Log.d("DialogDebug", "Exit button clicked")
                                            showDialog.value = false
                                            releaseAllPlayers() // Exit -> Release players
                                            finish()
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

        // 关闭系统 UI，实现全屏效果
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 注册新文件广播接收器，监听两个 Action
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
        Log.d("MediaPreviewActivity", "已注册广播接收器: ACTION_NEW_ADB_FILE, com.example.watchview.CLOSE_PREVIEW")
    }

    override fun onResume() {
        super.onResume()
        wearableRecyclerView.post { wearableRecyclerView.requestFocus() }
        // Resume playback for the current player if it exists
        players[currentPosition]?.playWhenReady = true
        Log.d("ExoPlayerControl", "onResume: Resuming player at position $currentPosition")
    }

    override fun onPause() {
        super.onPause()
        // Pause all players when the activity is paused
        pauseAllPlayers()
        Log.d("ExoPlayerControl", "onPause: Paused all players")

        // Cleanup temporary files if needed (logic remains the same)
        if (!isChangingConfigurations && !isSavedList && !hasBeenSavedInThisSession) {
             cleanupTempFiles()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        releaseAllPlayers() // Ensure players are released on back press too
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d("RotaryScroll", "Activity received motion event: ${event.action}")
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
        ) {
            Log.d("RotaryScroll", "Processing rotary event in Activity")
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            Log.d("RotaryScroll", "Raw delta: $delta")
            
            // Adjust scroll multiplier as needed for ExoPlayer view
            val scrollAmount = (delta * 2000).toInt() // Might need tuning
            Log.d("RotaryScroll", "Scroll amount: $scrollAmount")
            
            wearableRecyclerView.smoothScrollBy(0, scrollAmount)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(newFileReceiver)
        // 取消协程
        coroutineScope.cancel()
        releaseAllPlayers() // Ensure players are released on destroy
        Log.d("ExoPlayerControl", "onDestroy: Released all players and cancelled scope")
    }
    
    // Helper function to pause all currently active players
    private fun pauseAllPlayers() {
        players.values.forEach { it.playWhenReady = false }
        Log.d("ExoPlayerControl", "Paused all players (${players.size} players)")
    }

    // Helper function to release all players
    private fun releaseAllPlayers() {
        Log.d("ExoPlayerControl", "Releasing all players (${players.size} players)...")
        players.values.forEach {
            it.stop()
            it.release()
        }
        players.clear()
        Log.d("ExoPlayerControl", "All players released.")
    }
    
    // Helper function to clean up temp unzipped files
    private fun cleanupTempFiles() {
        val mediaListFiles = intent.getStringArrayListExtra("media_files")
        val firstFilePath = mediaListFiles?.firstOrNull()
        firstFilePath?.let { path ->
            val parentDir = File(path).parentFile
            parentDir?.let { dir ->
                // Ensure we only delete expected temporary directories
                if (dir.exists() && (dir.name.startsWith("unzipped_"))) {
                    Log.d("Cleanup", "Deleting preview directory on pause/exit: ${dir.absolutePath}")
                    coroutineScope.launch(Dispatchers.IO) { // Perform deletion off the main thread
                        try {
                           dir.deleteRecursively()
                           Log.d("Cleanup", "Successfully deleted ${dir.absolutePath}")
                        } catch (e: Exception) {
                            Log.e("Cleanup", "Failed to delete ${dir.absolutePath}", e)
                        }
                    }
                } else {
                     Log.d("Cleanup", "Directory not eligible for deletion: ${dir?.absolutePath}")
                }
            } ?: Log.d("Cleanup", "Parent directory is null for path: $path")
        } ?: Log.d("Cleanup", "No media files found to determine parent directory.")
    }
}

sealed class MediaViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
    class ImageViewHolder(val imageView: ImageView) : MediaViewHolder(imageView)
    class PlayerViewHolder(val playerView: PlayerView) : MediaViewHolder(playerView) {
        var player: ExoPlayer? = null // Hold a reference to the player for recycling
    }
} 