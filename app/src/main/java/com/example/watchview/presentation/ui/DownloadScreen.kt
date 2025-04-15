package com.example.watchview.presentation.ui

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import com.example.watchview.presentation.MediaPreviewActivity
import com.example.watchview.presentation.RivePreviewActivity
import com.example.watchview.presentation.model.DownloadType
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import com.example.watchview.presentation.model.ServerAddress
import com.example.watchview.utils.downloadFile
import com.example.watchview.utils.scanLocalNetwork
import com.example.watchview.utils.unzipMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import app.rive.runtime.kotlin.core.File as RiveCoreFile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

// 添加广播常量
const val ACTION_NEW_ADB_FILE = "com.example.watchview.NEW_ADB_FILE"
const val EXTRA_FILE_PATH = "file_path"
const val EXTRA_FILE_TYPE = "file_type"

// 添加新的广播常量
const val ACTION_TRIGGER_WIRED_PREVIEW = "com.example.watchview.TRIGGER_WIRED_PREVIEW"

/**
 * 下载屏幕组件
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DownloadScreen(
    isWifiConnected: Boolean,
    initialIpAddress: String = "",
    onIpAddressChange: (String) -> Unit = {}
) {
    var showScanScreen by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(true) }
    var servers by remember { mutableStateOf<List<ServerAddress>>(emptyList()) }
    var ipAddress by remember { mutableStateOf(initialIpAddress) }
    var downloadStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 添加检测本地文件的状态
    var hasLocalFile by remember { mutableStateOf(false) }
    var localFilePath by remember { mutableStateOf("") }
    var localFileType by remember { mutableStateOf<DownloadType?>(null) }
    
    // 添加定期刷新检查的状态
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 添加 BottomSheet 相关状态
    val bottomSheetState = rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val sheetPeekHeight = 32.dp // 改为更小的高度，像一个指示条

    // 添加下载进度状态
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadSpeed by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    // 添加状态来控制"有线传输"按钮的可用性
    var isOpeningLocalFile by remember { mutableStateOf(false) }
    // 添加状态控制顶部通知栏
    var showTopNotification by remember { mutableStateOf(false) }
    var topNotificationMessage by remember { mutableStateOf("") }

    // 添加一个协程作用域来控制扫描任务
    val scanJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // 添加状态以触发自动打开文件
    var shouldAutoOpenFileAfterRefresh by remember { mutableStateOf(false) }

    // 监听 ipAddress 的变化
    LaunchedEffect(ipAddress) {
        if (ipAddress.isNotEmpty()) {
            onIpAddressChange(ipAddress)
        }
    }
    
    // 注册广播接收器以监听预览关闭后的触发信号
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_TRIGGER_WIRED_PREVIEW) {
                    Log.d("WiredTrigger", "收到 ACTION_TRIGGER_WIRED_PREVIEW 广播，触发刷新")
                    // 设置标记以在刷新后自动打开文件
                    shouldAutoOpenFileAfterRefresh = true
                }
            }
        }
        val filter = IntentFilter(ACTION_TRIGGER_WIRED_PREVIEW)
        // 使用 context.registerReceiver 注册
        context.registerReceiver(receiver, filter)
        
        // 在 DisposableEffect 销毁时注销接收器
        onDispose {
            Log.d("WiredTrigger", "注销 ACTION_TRIGGER_WIRED_PREVIEW 广播接收器")
            context.unregisterReceiver(receiver)
        }
    }
    
    // 处理打开本地文件的逻辑
    fun handleOpenLocalFile() {
        // 防止重复点击
        if (isOpeningLocalFile) return
        
        isOpeningLocalFile = true
        downloadStatus = "准备打开文件..." // 可选：提供状态反馈
        
        if (localFilePath.isNotEmpty() && localFileType != null) {
            when (localFileType) {
                DownloadType.RIVE -> {
                    try {
                        val intent = Intent(context, RivePreviewActivity::class.java).apply {
                            putExtra("file_path", localFilePath)
                            putExtra("is_temp_file", false)
                        }
                        context.startActivity(intent)
                        downloadStatus = "" // 清除状态
                    } catch (e: Exception) {
                         Log.e("LocalFileOpenError", "Error opening Rive file", e)
                         downloadStatus = "打开Rive文件失败"
                         Toast.makeText(context, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isOpeningLocalFile = false
                    }
                }
                DownloadType.ZIP -> {
                    coroutineScope.launch {
                        try {
                            downloadStatus = "解压文件中..."
                            val tempZipFile = File(localFilePath)
                            val mediaFiles = withContext(Dispatchers.IO) {
                                unzipMedia(context, tempZipFile)
                            }
                            downloadStatus = ""
                            
                            if (mediaFiles.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "解压失败或压缩包中无可用媒体文件",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val intent = Intent(context, MediaPreviewActivity::class.java).apply {
                                    putStringArrayListExtra("media_files", ArrayList(mediaFiles.map { it.file.absolutePath }))
                                    putStringArrayListExtra("media_types", ArrayList(mediaFiles.map { it.type.name }))
                                    putExtra("zip_file_path", tempZipFile.absolutePath)
                                    putExtra("is_saved_list", false)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            Log.e("LocalFileError", "Error processing local file", e)
                             downloadStatus = "处理ZIP文件失败"
                            Toast.makeText(
                                context,
                                "处理文件失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isOpeningLocalFile = false
                        }
                    }
                }
                else -> {
                    Toast.makeText(
                        context,
                        "不支持的文件类型",
                        Toast.LENGTH_SHORT
                    ).show()
                    downloadStatus = "不支持的文件类型"
                    isOpeningLocalFile = false
                }
            }
        } else {
            Toast.makeText(context, "未找到有效本地文件", Toast.LENGTH_SHORT).show()
            downloadStatus = "未找到有效本地文件"
            isOpeningLocalFile = false
        }
    }
    
    // 检查本地文件，并设置定期刷新
    LaunchedEffect(refreshTrigger) {
        try {
            val (file, type, isNewFile) = checkLocalFile(context, localFilePath)
            
            val hadLocalFile = hasLocalFile
            val previousPath = localFilePath
            
            hasLocalFile = file != null
            localFilePath = file?.absolutePath ?: ""
            localFileType = type
            
            // --- 修改后的逻辑：检查是否需要自动打开文件 (忽略 isNewFile)，并添加重试 --- 
            if (shouldAutoOpenFileAfterRefresh) {
                if (hasLocalFile) {
                    // 初始检查成功找到文件
                    Log.d("AutoOpenFile", "初始检查找到文件 (isNewFile=$isNewFile)，调用 handleOpenLocalFile")
                    handleOpenLocalFile()
                    shouldAutoOpenFileAfterRefresh = false // 重置标记
                } else {
                    // 初始检查未找到文件，开始重试
                    Log.d("AutoOpenFile", "初始检查未找到文件，开始重试 (最多3秒, 0.2s/次)")
                    var retrySuccess = false
                    for (retryAttempt in 1..15) {
                        delay(200)
                        Log.d("AutoOpenFile", "重试检查 #${retryAttempt}")
                        // 在重试时，我们仍然需要比较 isNewFile，以避免重复打开完全相同的文件
                        // 但初始触发不再需要检查 isNewFile
                        val (retryFile, retryType, retryIsNewFile) = checkLocalFile(context, localFilePath)
                        
                        if (retryFile != null) {
                            Log.d("AutoOpenFile", "重试成功找到文件: ${retryFile.absolutePath} (isNewFile=$retryIsNewFile)")
                            // 更新状态以反映新找到的文件
                            hasLocalFile = true
                            localFilePath = retryFile.absolutePath
                            localFileType = retryType
                            // 打开文件
                            handleOpenLocalFile()
                            shouldAutoOpenFileAfterRefresh = false // 重置标记
                            retrySuccess = true
                            break // 成功，跳出重试循环
                        }
                    }
                    if (!retrySuccess) {
                        Log.w("AutoOpenFile", "重试3秒后仍未找到新文件，放弃自动打开")
                        shouldAutoOpenFileAfterRefresh = false // 重试超时，重置标记
                    }
                }
            }
            // --- 结束修改后的逻辑 --- 
            
            // 如果是新文件，显示提示并发送广播 (这部分逻辑保持不变，仅在非自动打开流程或需要时执行)
            // Ensure this only runs if it wasn't handled by the auto-open logic above OR if auto-open wasn't requested
            if (!shouldAutoOpenFileAfterRefresh && hasLocalFile && isNewFile) {
                Log.d("LocalFileNotify", "显示新文件通知并发送广播 (isNewFile=$isNewFile, shouldAutoOpen=$shouldAutoOpenFileAfterRefresh)")
                // 不再使用 Toast，而是更新状态显示顶部通知栏
                topNotificationMessage = "收到新文件"
                showTopNotification = true
                // 启动一个协程，在短暂延迟后隐藏通知栏
                coroutineScope.launch {
                    delay(2500) // 显示 2.5 秒
                    showTopNotification = false
                }
                
                // 发送广播通知当前可能正在运行的预览活动
                if (file != null && type != null) {
                    val broadcastIntent = Intent(ACTION_NEW_ADB_FILE).apply {
                        putExtra(EXTRA_FILE_PATH, file.absolutePath)
                        putExtra(EXTRA_FILE_TYPE, type.name)
                        // 添加包名，使用显式 Intent
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcastIntent)
                    Log.d("LocalFile", "发送新文件广播: ${file.absolutePath}, 类型: $type")
                }
            }
            
            // 5秒后再次检查
            kotlinx.coroutines.delay(5000)
            refreshTrigger = (refreshTrigger + 1) % 1000 // 避免数值过大
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w("LocalFile", "LaunchedEffect 在 checkLocalFile 期间被取消 (可能是 Composition 变化)。将在下次触发时重试。", e)
            // 不重置 shouldAutoOpenFileAfterRefresh，让下一次成功的运行来处理
        } catch (e: Exception) {
            Log.e("LocalFile", "检查本地文件出错: ${e.message}", e)
            // 出错时也考虑重置标记，防止意外触发
            shouldAutoOpenFileAfterRefresh = false
            // 5秒后重试
            kotlinx.coroutines.delay(5000)
            refreshTrigger = (refreshTrigger + 1) % 1000
        }
    }
    
    // 修改下载处理逻辑
    val handleDownload = { url: String ->
        coroutineScope.launch {
            var downloadedFilePath: String? = null 
            var downloadedFileType: DownloadType? = null 

            try {
                isDownloading = true
                downloadStatus = "开始下载..."
                downloadProgress = 0f
                
                // 清理旧的临时下载文件 (保留 saved_rive 目录)
                context.filesDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name == "saved_rive") {
                        // 保留 saved_rive 目录
                    } else if (file.isDirectory && file.name.startsWith("unzipped_")) {
                        // 清理旧的解压目录
                        file.deleteRecursively()
                    } else if (file.isFile && (file.name.endsWith(".riv") || file.name.endsWith(".zip"))) {
                         // 删除根目录下的临时 riv/zip 文件
                        file.delete()
                    }
                }
                
                val downloadResult = downloadFile(context, url) { progress ->
                    downloadProgress = progress
                    downloadStatus = "下载中... ${(progress * 100).toInt()}%"
                }
                downloadedFilePath = downloadResult.filePath
                downloadedFileType = downloadResult.type
                
                when (downloadedFileType) {
                    DownloadType.ZIP -> {
                        if (downloadedFilePath != null) {
                        downloadStatus = "解压文件中..."
                            val tempZipFile = File(downloadedFilePath)
                            val mediaFiles = withContext(Dispatchers.IO) {
                                unzipMedia(context, tempZipFile)
                            }
                        if (mediaFiles.isEmpty()) {
                            downloadStatus = "解压失败或压缩包中无可用媒体文件"
                                tempZipFile.delete() 
                        } else {
                            downloadStatus = ""
                                val intent = Intent(context, MediaPreviewActivity::class.java).apply {
                                putStringArrayListExtra("media_files", ArrayList(mediaFiles.map { it.file.absolutePath }))
                                putStringArrayListExtra("media_types", ArrayList(mediaFiles.map { it.type.name }))
                                    putExtra("zip_file_path", tempZipFile.absolutePath)
                                    putExtra("is_saved_list", false)
                            }
                            context.startActivity(intent)
                            }
                        } else {
                             downloadStatus = "下载 ZIP 文件失败，未找到文件"
                        }
                    }
                    DownloadType.RIVE -> {
                        if (downloadedFilePath != null) {
                            val tempFile = File(downloadedFilePath)
                            downloadStatus = ""
                            val intent = Intent(context, RivePreviewActivity::class.java).apply {
                                putExtra("file_path", tempFile.absolutePath)
                                putExtra("is_temp_file", true)  
                            }
                            context.startActivity(intent)
                        } else {
                             downloadStatus = "下载 Rive 文件失败，未找到文件"
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e("DownloadHandleError", "Error during download/processing", e)
                val errorMsg = when (e) {
                    is java.net.ConnectException -> "无法连接到服务器"
                    is java.net.SocketTimeoutException -> "连接超时"
                    is java.io.IOException -> "网络错误: ${e.message}" 
                    else -> "处理失败: ${e.message}" 
                }
                downloadStatus = errorMsg 
                downloadedFilePath?.let { File(it).delete() }
            } finally {
                isDownloading = false
            }
        }
    }

    // 修改初始扫描逻辑
    LaunchedEffect(Unit) {
        if (isWifiConnected) {
            isScanning = true
            scanJob.value = coroutineScope.launch {
                servers = scanLocalNetwork(context)
                isScanning = false
            }
        }
    }

    // 使用 BottomSheetScaffold 替换 Box 和 VerticalPager
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            Column {
                // 1. 自定义的拖动指示条 (Handle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetPeekHeight)
                        .background(Color.Black)
                        .clickable( 
                             interactionSource = remember { MutableInteractionSource() },
                             indication = null,
                             onClick = { 
                                 coroutineScope.launch {
                                      if (bottomSheetState.isCollapsed) {
                                           bottomSheetState.expand()
                                      } else {
                                           bottomSheetState.collapse()
                                      }
                                 }
                             }
                         ),
                    contentAlignment = Alignment.Center
                ) {
                    // 指示条中间的小横线
                        Box(
                            modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)) 
                            .background(Color.Gray.copy(alpha = 0.8f))
                    )
                }
                
                // 2. 放置实际的 Sheet 内容
                SavedFilesScreen(context = context, bottomSheetState = bottomSheetState)
            }
        },
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = MaterialTheme.shapes.large, 
        sheetBackgroundColor = MaterialTheme.colors.surface,
        backgroundColor = MaterialTheme.colors.background,
        content = { paddingValues -> 
            // 根据目标状态计算目标值
            val targetScale = if (bottomSheetState.targetValue == BottomSheetValue.Expanded) 0.85f else 1.0f
            val targetAlpha = if (bottomSheetState.targetValue == BottomSheetValue.Expanded) 0.5f else 1.0f
            
            // 使用 animateFloatAsState 创建动画状态
            val scale by animateFloatAsState(targetValue = targetScale)
            val alpha by animateFloatAsState(targetValue = targetAlpha)
            
            // 主内容区域 Box
            Box(
                 modifier = Modifier
                     .padding(paddingValues) 
                     .fillMaxSize()
                     .background(MaterialTheme.colors.background) 
                     // --- 应用动画状态 --- 
                     .graphicsLayer {
                         scaleX = scale
                         scaleY = scale
                         this.alpha = alpha
                     }
                     // --- 结束应用 --- 
             ) {
                 // 主内容的逻辑不变
                if (!isWifiConnected) {
                     Box( 
                         modifier = Modifier.fillMaxSize(), // 背景由外部 Box 处理
                         contentAlignment = Alignment.Center
                     ) {
                         Column(
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.Center
                         ) {
                             Text("请连接Wi-Fi或选择有线传输", color = Color.White, fontSize = 14.sp)
                             
                             Spacer(modifier = Modifier.height(16.dp))
                             
                             // 即使未连接 WiFi，也显示有线传输按钮 (如果有本地文件)
                             if (hasLocalFile) {
                                 Button(
                                     onClick = { handleOpenLocalFile() },
                                     modifier = Modifier
                                         .width(160.dp)
                                         .height(48.dp)
                                         .then(Modifier.alpha(if (isOpeningLocalFile) 0.2f else 1.0f)), // 控制透明度
                                     colors = ButtonDefaults.buttonColors(
                                         backgroundColor = Color(0xFF4CAF50)
                                     ),
                                     shape = CircleShape
                                 ) {
                                     Text("有线传输", color = Color.White, fontSize = 14.sp)
                                 }
                             }
                         }
                     }
                } else if (showScanScreen) {
                    // NetworkScanScreen 内部可能也需要检查背景设置
                    NetworkScanScreen(
                        servers = servers,
                        isScanning = isScanning,
                        onServerSelected = { server ->
                            if (server.isManualInput) {
                                showScanScreen = false
                            } else {
                                ipAddress = server.ip
                                showScanScreen = false
                                handleDownload("http://$ipAddress:8080")
                            }
                            // 点击扫描结果或手动输入后，可以考虑折叠底部面板 (如果它是展开的)
                            coroutineScope.launch {
                                bottomSheetState.collapse()
                            }
                        },
                        onCancelScan = {
                            scanJob.value?.cancel()
                            if (isScanning) {
                                showScanScreen = false
                            } else {
                                isScanning = true
                                scanJob.value = coroutineScope.launch {
                                    servers = scanLocalNetwork(context)
                                    isScanning = false
                                }
                            }
                            coroutineScope.launch {
                                bottomSheetState.collapse() // 取消扫描或重扫也折叠
                            }
                        },
                        hasLocalFile = hasLocalFile,  // 传递本地文件状态
                        onOpenLocalFile = {  // 传递处理本地文件的回调
                            handleOpenLocalFile()
                            coroutineScope.launch {
                                bottomSheetState.collapse() // 打开本地文件后折叠
                            }
                        },
                        isOpeningLocalFile = isOpeningLocalFile // 传递状态
                    )
                } else {
                    // 手动输入页面 Column
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        var focusState by remember { mutableStateOf(false) }
                        val focusManager = LocalFocusManager.current
                        
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { newValue -> 
                                // 只允许输入数字和点号，且长度不超过15
                                if (newValue.length <= 15 && newValue.all { it.isDigit() || it == '.' }) {
                                    ipAddress = newValue
                                }
                            },
                            label = { 
                                Text(
                                    "设备地址",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f)
                                )
                            },
                            placeholder = { Text("", color = MaterialTheme.colors.onBackground.copy(alpha = 1f), fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    val url = "http://$ipAddress:8080"
                                    downloadStatus = "下载中..."
                                    handleDownload(url)
                                }
                            ),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = MaterialTheme.colors.onBackground,
                                cursorColor = MaterialTheme.colors.primary,
                                focusedBorderColor = MaterialTheme.colors.primary,
                                unfocusedBorderColor = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
                                placeholderColor = MaterialTheme.colors.onBackground.copy(alpha = 1f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))  // 输入框与按钮之间的间距
                        // 修改为水平并排的两个按钮
                        if (hasLocalFile) {
                            // 使用 Row 来左右并排放置按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 预览按钮
                                Button(
                                    onClick = {
                                        val url = "http://$ipAddress:8080"
                                        downloadStatus = "下载中..."
                                        handleDownload(url)
                                        coroutineScope.launch { bottomSheetState.collapse() } // 开始下载后折叠
                                    },
                                    enabled = !isDownloading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .padding(end = 1.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF2196F3),
                                        disabledBackgroundColor = Color.Gray,
                                        disabledContentColor = Color.White
                                    ),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (isDownloading) "下载中..." else "WIFI下载",
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                                
                                // 有线传输按钮
                                Button(
                                    onClick = { handleOpenLocalFile() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .padding(start = 1.dp)
                                        .then(Modifier.alpha(if (isOpeningLocalFile) 0.2f else 1.0f)), // 控制透明度
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF4CAF50)
                                    ),
                                    shape = CircleShape
                                ) {
                                    Text("有线传输", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        } else {
                            // 只有预览按钮
                            Button(
                                onClick = {
                                    val url = "http://$ipAddress:8080"
                                    downloadStatus = "下载中..."
                                    handleDownload(url)
                                    coroutineScope.launch { bottomSheetState.collapse() } // 开始下载后折叠
                                },
                                enabled = !isDownloading,
                                modifier = Modifier
                                    .width(300.dp)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF2196F3),
                                    disabledBackgroundColor = Color.Gray,
                                    disabledContentColor = Color.White
                                ),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (isDownloading) "下载中..." else "WIFI下载",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))  // 按钮与状态文本之间的间距
                        if (isDownloading) {
                            if (downloadProgress > 0) {
                                Text(
                                    text = "${downloadProgress}%",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                            if (downloadSpeed.isNotEmpty()) {
                                Text(
                                    text = downloadSpeed,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            Text(
                                text = downloadStatus,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 8.sp                  // 状态文本大小
                            )
                        }
                    }
                }

                // 顶部通知栏 (覆盖在其他内容之上)
                AnimatedVisibility(
                    visible = showTopNotification,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = topNotificationMessage,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

/**
 * 检查是否在外部存储的应用专属目录中有新文件
 * @return Triple<文件, 文件类型, 是否是新文件>
 */
suspend fun checkLocalFile(
    context: Context,
    previousPath: String
): Triple<File?, DownloadType?, Boolean> {
    return withContext(Dispatchers.IO) {
        try {
            val externalFilesDir = File(context.getExternalFilesDir(null)?.absolutePath ?: "")
            // 检查目录是否存在
            if (!externalFilesDir.exists()) {
                Log.d("LocalFile", "外部存储目录不存在: ${externalFilesDir.absolutePath}")
                return@withContext Triple(null, null, false)
            }

            // 查找目录下的文件
            val files = externalFilesDir.listFiles() ?: emptyArray()
            Log.d("LocalFile", "外部存储目录: ${externalFilesDir.absolutePath}, 文件数量: ${files.size}")
            
            if (files.isNotEmpty()) {
                // 按照修改时间降序排序，取最新的一个文件
                val latestFile = files.maxByOrNull { it.lastModified() }
                
                if (latestFile != null) {
                    val fileType = when {
                        latestFile.name.endsWith(".zip", ignoreCase = true) || isZipFile(latestFile) -> DownloadType.ZIP
                        latestFile.name.endsWith(".riv", ignoreCase = true) || isRiveFile(latestFile) -> DownloadType.RIVE
                        else -> null
                    }
                    
                    val isNewFile = latestFile.absolutePath != previousPath
                    
                    Log.d("LocalFile", "找到文件: ${latestFile.name}, 类型: $fileType, 是新文件: $isNewFile")
                    return@withContext Triple(if (fileType != null) latestFile else null, fileType, isNewFile)
                }
            }
            
            // 如果没有找到支持的文件
            return@withContext Triple(null, null, false)
        } catch (e: Exception) {
            Log.e("LocalFile", "检查本地文件出错", e)
            return@withContext Triple(null, null, false)
        }
    }
}

// 检查文件是否为 Rive 文件
private fun isRiveFile(file: File): Boolean {
    return try {
        RiveCoreFile(file.readBytes())
        true
    } catch (e: Exception) {
        false
    }
}

// 检查文件是否为 ZIP 文件
private fun isZipFile(file: File): Boolean {
    return try {
        java.io.FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            val read = fis.read(magic)
            read == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
        }
    } catch (e: Exception) {
        false
    }
} 