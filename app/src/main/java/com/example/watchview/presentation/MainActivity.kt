/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.watchview.presentation

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.watchview.R
import com.example.watchview.presentation.theme.WatchViewTheme
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.pager.*
import app.rive.runtime.kotlin.core.File as RiveCoreFile
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.wear.compose.material.CircularProgressIndicator
import java.util.zip.ZipFile
import com.example.watchview.utils.PreferencesManager
import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import android.text.InputFilter
import android.text.Spanned
import androidx.compose.ui.text.input.*
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import java.io.FileInputStream
import android.util.Log
import android.widget.Toast
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import java.util.zip.ZipInputStream
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.pager.VerticalPager
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState

// 文件类型枚举，用于区分下载的文件类型
enum class DownloadType {
    ZIP,        // 压缩包文件
    RIVE,       // Rive 动画文件
}

// 在文件开头的枚举类型后面添加
data class MediaFile(
    val file: File,
    val type: MediaType
)

// 在 MediaFile 类后面添加数据类
data class ServerAddress(
    val ip: String,
    val isManualInput: Boolean = false
)

// 添加 PreviewType 密封类定义
sealed class PreviewType {
    data class MediaPreview(val mediaFiles: List<MediaFile>) : PreviewType()
    data class RivePreview(val riveFile: File) : PreviewType()
}

// 在 SavedRiveFilesScreen 之前添加新的数据类和函数
data class SavedRiveFile(
    val name: String,
    val file: File,
    val lastModified: Long
)

// 在 PreviewType 之后添加新的枚举和数据类
enum class SavedFileType {
    RIVE,
    MEDIA_LIST // 代表 ZIP 文件
}

data class SavedFileItem(
    val name: String,
    val file: File,
    val type: SavedFileType,
    val lastModified: Long
)

// 主 Activity
class MainActivity : ComponentActivity() {
    private var isWifiConnected by mutableStateOf(false)
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()  // 显示启动画面
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)  // 设置 WearOS 默认主题

        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

        // ------------------------- 新增代码：监控 Wi-Fi 状态 -------------------------
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 监听网络变化的回调
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    isWifiConnected = true
                }
            }
            override fun onLost(network: Network) {
                isWifiConnected = false
            }
        }
        // 注册回调，实时监听网络状态变化
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        // ---------------------------------------------------------------

        setContent {
            WatchViewTheme {
                DownloadScreen(
                    isWifiConnected = isWifiConnected,
                    initialIpAddress = preferencesManager.lastIpAddress,
                    onIpAddressChange = { newIp ->
                        preferencesManager.lastIpAddress = newIp
                    }
                )
            }
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    WatchViewTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}

// 修改扫描函数
suspend fun scanLocalNetwork(context: Context): List<ServerAddress> {
    return withContext(Dispatchers.IO) {
        val servers = mutableListOf<ServerAddress>()
        
        try {
            // 获取 WiFi 管理器
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            
            // 获取本机 IP 地址并转换为字符串
            val ipInt = dhcpInfo.ipAddress
            val ipByteArray = ByteArray(4)
            ipByteArray[0] = (ipInt and 0xff).toByte()
            ipByteArray[1] = (ipInt shr 8 and 0xff).toByte()
            ipByteArray[2] = (ipInt shr 16 and 0xff).toByte()
            ipByteArray[3] = (ipInt shr 24 and 0xff).toByte()
            
            val ipAddress = InetAddress.getByAddress(ipByteArray)
            val subnet = ipAddress.hostAddress.substring(0, ipAddress.hostAddress.lastIndexOf("."))
            
            println("Device IP: ${ipAddress.hostAddress}")
            println("Scanning subnet: $subnet")
            
            // 修改扫描逻辑
            val scanJobs = (1..254).map { i ->
                async(Dispatchers.IO) {
                    val testIp = "$subnet.$i"
                    try {
                        val socket = Socket()
                        socket.soTimeout = 300
                        val address = InetSocketAddress(testIp, 8080)
                        
                        try {
                            socket.connect(address, 300)
                            // 如果能连接成功，说明端口开放
                            println("Found open port at: $testIp")
                            testIp
                        } catch (e: Exception) {
                            null
                        } finally {
                            try {
                                socket.close()
                            } catch (e: Exception) {}
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            val results = scanJobs.awaitAll()
            servers.addAll(results.filterNotNull().map { ServerAddress(it) })
            
        } catch (e: Exception) {
            println("Scan error: ${e.message}")
            e.printStackTrace()
        }
        
        servers.add(ServerAddress("手动输入", true))
        servers
    }
}

// 修改 NetworkScanScreen 组件
@Composable
fun NetworkScanScreen(
    servers: List<ServerAddress>,
    onServerSelected: (ServerAddress) -> Unit,
    isScanning: Boolean,
    onCancelScan: () -> Unit = {}  // 添加取消回调
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isScanning) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.wear.compose.material.CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(8.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    "正在扫描局域网...",
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // 添加取消按钮
                Button(
                    onClick = onCancelScan,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = androidx.compose.ui.graphics.Color.DarkGray.copy(alpha = 0.6f)
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Text(
                        "手动连接",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "可用设备",
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(servers.filterNot { it.isManualInput }) { server ->
                        Button(
                            onClick = { onServerSelected(server) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = androidx.compose.ui.graphics.Color(0xFF2196F3).copy(alpha = 0.3f)
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text(
                                text = server.ip,
                                color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                fontSize = 16.sp
                            )
                        }
                    }
                    
                    // 修改手动输入和重新扫描按钮的布局
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()  // 添加 fillMaxWidth 让 Row 占满宽度
                        ) {
                            // 手动输入按钮
                            Button(
                                onClick = { onServerSelected(ServerAddress("手动输入", true)) },
                                modifier = Modifier
                                    .weight(1f)  // 使用 weight 替代固定宽度
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = androidx.compose.ui.graphics.Color.DarkGray
                                ),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Text(
                                    "手动输入",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 10.sp
                                )
                            }
                            
                            // 重新扫描按钮
                            Button(
                                onClick = onCancelScan,
                                modifier = Modifier
                                    .weight(1f)  // 使用 weight 替代固定宽度
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = androidx.compose.ui.graphics.Color.DarkGray
                                ),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Text(
                                    "重新扫描",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 在 DownloadScreen 函数之前添加新的组件
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
    activeColor: Color = MaterialTheme.colors.primary,
    inactiveColor: Color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f)
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(pageCount) { index ->
            val color = if (index == currentPage) activeColor else inactiveColor
            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun SavedFilesScreen(context: Context) {
    var savedFiles by remember { mutableStateOf<List<SavedFileItem>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
        val filesDir = File(context.filesDir, "saved_rive")
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
            val loadedFiles = filesDir.listFiles()
                ?.filter { it.name.endsWith(".riv") || it.name.endsWith(".zip") }
                ?.mapNotNull { file ->
                    val type = when {
                        file.name.endsWith(".riv") -> SavedFileType.RIVE
                        file.name.endsWith(".zip") -> SavedFileType.MEDIA_LIST
                        else -> null 
                    }
                    type?.let {
                        SavedFileItem(
                    name = file.name,
                    file = file,
                            type = it,
                    lastModified = file.lastModified()
                )
                    }
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
            withContext(Dispatchers.Main) {
                 savedFiles = loadedFiles
            }
        }
    }

            LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 80.dp)
            ) {
                items(savedFiles, key = { it.file.absolutePath }) { savedFile ->
                    var offsetX by remember { mutableStateOf(0f) }
            val draggableState = rememberDraggableState { delta ->
                offsetX = (offsetX + delta).coerceAtMost(0f) 
            }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    .height(48.dp) 
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = {
                            val deleteThreshold = -300f
                            if (offsetX < deleteThreshold) {
                                            coroutineScope.launch {
                                    try {
                                        val deleted = withContext(Dispatchers.IO) {
                                                savedFile.file.delete()
                                        }
                                        if (deleted) {
                                            withContext(Dispatchers.Main) {
                                                savedFiles = savedFiles.filterNot { it.file.absolutePath == savedFile.file.absolutePath }
                                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                                            offsetX = 0f
                                        }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DirectDeleteError", "Failed to delete file: ${savedFile.name}", e)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "删除出错", Toast.LENGTH_SHORT).show()
                                        offsetX = 0f
                                    }
                                    }
                                }
                            } else {
                                offsetX = 0f
                            }
                        }
                    )
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
            ) {
                        // 文件项按钮
                        Button(
                            onClick = {
                        when (savedFile.type) {
                            SavedFileType.RIVE -> {
                                val intent = Intent(context, RivePreviewActivity::class.java).apply {
                                    putExtra("file_path", savedFile.file.absolutePath)
                                    putExtra("is_temp_file", false)
                                }
                                context.startActivity(intent)
                            }
                            SavedFileType.MEDIA_LIST -> {
                                coroutineScope.launch {
                                    try {
                                        val mediaFiles = withContext(Dispatchers.IO) {
                                            unzipSavedZipForPreview(context, savedFile.file)
                                        }
                                        withContext(Dispatchers.Main) {
                                             if (mediaFiles.isNotEmpty()) {
                                                 val intent = Intent(context, MediaPreviewActivity::class.java).apply {
                                                     putStringArrayListExtra("media_files", ArrayList(mediaFiles.map { it.file.absolutePath }))
                                                     putStringArrayListExtra("media_types", ArrayList(mediaFiles.map { it.type.name }))
                                                     putExtra("zip_file_path", savedFile.file.absolutePath)
                                                     putExtra("is_saved_list", true)
                                                 }
                                                 context.startActivity(intent)
                                             } else {
                                                 Toast.makeText(context, "无法预览：压缩包为空或解压失败", Toast.LENGTH_SHORT).show()
                                             }
                                        }
                                    } catch (e: Exception) {
                                         Log.e("UnzipPreviewError", "Failed to unzip for preview: ${savedFile.name}", e)
                                         withContext(Dispatchers.Main) {
                                             Toast.makeText(context, "预览失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                         }
                                    }
                                }
                            }
                        }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                         Icon(
                             imageVector = if (savedFile.type == SavedFileType.RIVE) Icons.Default.PlayArrow else Icons.Default.List,
                             contentDescription = if (savedFile.type == SavedFileType.RIVE) "Rive 文件" else "媒体列表",
                             tint = MaterialTheme.colors.primary
                         )
                         Spacer(Modifier.width(8.dp))
                            Text(
                                text = savedFile.name,
                             color = MaterialTheme.colors.onSurface,
                                fontSize = 12.sp,
                                maxLines = 1,
                             overflow = TextOverflow.Ellipsis,
                             modifier = Modifier.weight(1f)
                            )
                    }
                }
            }
        }
    }
}

// 修改 DownloadScreen 函数
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
    
    // 添加 BottomSheet 相关状态
    val bottomSheetState = rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val sheetPeekHeight = 32.dp // 改为更小的高度，像一个指示条

    // 添加下载进度状态
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadSpeed by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    // 添加一个协程作用域来控制扫描任务
    val scanJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 监听 ipAddress 的变化
    LaunchedEffect(ipAddress) {
        if (ipAddress.isNotEmpty()) {
            onIpAddressChange(ipAddress)
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
                            val mediaFiles = unzipMedia(context, tempZipFile) 
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
                SavedFilesScreen(context = context)
            }
        },
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = MaterialTheme.shapes.large, 
        sheetBackgroundColor = MaterialTheme.colors.surface
    ) { paddingValues -> 
        // --- 修改为基于状态的动画 --- 
        // val progress = bottomSheetState.progress.fraction // Remove this line
        // val scale = (1.0f - (progress * 0.15f)).coerceIn(0.85f, 1.0f) // Remove this line
        // val alpha = (1.0f - (progress * 0.5f)).coerceIn(0.5f, 1.0f)   // Remove this line

        // 根据目标状态计算目标值
        val targetScale = if (bottomSheetState.targetValue == BottomSheetValue.Expanded) 0.85f else 1.0f
        val targetAlpha = if (bottomSheetState.targetValue == BottomSheetValue.Expanded) 0.5f else 1.0f
        
        // 使用 animateFloatAsState 创建动画状态
        val scale by animateFloatAsState(targetValue = targetScale)
        val alpha by animateFloatAsState(targetValue = targetAlpha)
        // --- 结束修改 ---
        
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
                     Text("请连接 Wi-Fi 后继续", color = Color.White, fontSize = 14.sp)
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
                            }
                        )
                    } else {
                // 手动输入页面 Column
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        // .background(MaterialTheme.colors.background) // 背景由外部 Box 处理
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
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
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
                                    backgroundColor = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                    disabledBackgroundColor = Color.Gray,
                                    disabledContentColor = Color.White
                                ),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Text(
                                    text = if (isDownloading) "下载中..." else "预览",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
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
        }
    }
}

// 添加一个辅助函数来检查文件是否为 Rive 文件
private fun isRiveFile(file: File): Boolean {
    return try {
        // 尝试加载文件作为 Rive 文件
        RiveCoreFile(file.readBytes())
        true
    } catch (e: Exception) {
        false
    }
}

// 将 DownloadResult 定义移到 downloadFile 函数之前
data class DownloadResult(val filePath: String?, val type: DownloadType)

suspend fun downloadFile(
    context: Context, 
    urlString: String,
    onProgress: (Float) -> Unit
): DownloadResult { 
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var tempFile: File? = null 
        try {
            println("Downloading from: $urlString")
            
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000  // 增加连接超时时间到15秒
                readTimeout = 5000     // 增加读取超时时间到45秒
                
                // 优化连接配置
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "keep-alive") // 使用持久连接
                setRequestProperty("Accept-Encoding", "gzip, deflate") // 支持压缩
                setRequestProperty("User-Agent", "WatchView/1.0")
                
                // 启用缓存
                useCaches = true
                defaultUseCaches = true
                
                // 允许自动重定向
                instanceFollowRedirects = true
            }
            
            val responseCode = connection.responseCode
            println("Response code: $responseCode")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                 // 增加重试机制或更详细错误信息
                 var errorDetails = "列表返回错误代码: $responseCode"
                 try {
                     connection.errorStream?.bufferedReader()?.use { errorDetails += "\\n${it.readText()}" }
                 } catch (_: Exception) {}
                 throw IOException(errorDetails)
            }
            
            val contentType = connection.contentType ?: "application/octet-stream"
            val fileSize = connection.contentLengthLong
            println("Content type: $contentType, File size: $fileSize bytes")
            
            // 获取原始文件名，处理 Content-Disposition
            var originalFileName = "downloaded_file"
            val contentDisposition = connection.getHeaderField("Content-Disposition")
            if (contentDisposition != null) {
                val parts = contentDisposition.split("filename=")
                if (parts.size > 1) {
                    originalFileName = parts[1].trim(' ', '"')
                     // 简单的 URL 解码处理（可能不完全充分）
                     try {
                         originalFileName = URLDecoder.decode(originalFileName, "UTF-8")
                     } catch (e: UnsupportedEncodingException) {
                         println("Warning: Could not URL decode filename: $originalFileName")
                     } catch (e: IllegalArgumentException) {
                          println("Warning: Invalid encoding in filename: $originalFileName")
                     }
                }
            } else {
                 // 尝试从 URL 中提取文件名
                 try {
                      val path = URL(urlString).path
                      if (path.isNotEmpty() && path != "/") {
                           originalFileName = File(path).name
                      }
                 } catch (e: MalformedURLException) {
                      println("Warning: Could not parse URL for filename: $urlString")
                 }
            }

            // 确保文件名有扩展名，并确定临时文件路径
            val fileExtension = originalFileName.substringAfterLast('.', "")
            val baseName = originalFileName.substringBeforeLast('.', originalFileName)
            val finalFileName = if (fileExtension.isNotEmpty()) originalFileName else "$baseName.tmp"
            tempFile = File(context.filesDir, finalFileName)
            
            // 使用更大的缓冲区(1MB)进行读写
            val buffer = ByteArray(8192) // 增大缓冲区
            var bytesRead: Int
            var totalBytesRead = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileSize > 0) {
                            val progress = totalBytesRead.toFloat() / fileSize
                            withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                        } else {
                            // 如果文件大小未知，可以提供一个不确定的进度或仅显示下载速度
                            withContext(Dispatchers.Main) {
                                onProgress(-1f) // 表示进度未知
                    }
                }
            }
                }
            }
            println("File downloaded successfully to ${tempFile.absolutePath}")
            
            // --- 简化文件类型判断逻辑 --- 
            val fileType: DownloadType = when {
                 contentType.contains("zip", ignoreCase = true) -> DownloadType.ZIP
                 contentType.contains("application/rive", ignoreCase = true) -> DownloadType.RIVE
                 fileExtension.equals("zip", ignoreCase = true) || isZipFile(tempFile) -> DownloadType.ZIP
                 fileExtension.equals("riv", ignoreCase = true) || isRiveFile(tempFile) -> DownloadType.RIVE
                 // 如果都不是，则直接抛出异常
                 else -> throw IOException("不支持的文件类型: $contentType / $originalFileName") 
            }
            println("Determined file type: $fileType")
            // --- 结束简化 --- 

             // 如果类型是 ZIP 或 RIVE，重命名文件以包含正确的扩展名
             var finalFilePath = tempFile.absolutePath
             if (fileType == DownloadType.ZIP && !tempFile.name.endsWith(".zip", ignoreCase = true)) {
                 val newFile = File(tempFile.parent, "$baseName.zip")
                 if (tempFile.renameTo(newFile)) {
                     finalFilePath = newFile.absolutePath
                     println("Renamed temp file to: ${newFile.name}")
                 } else {
                     println("Warning: Failed to rename temp file to .zip")
                 }
             } else if (fileType == DownloadType.RIVE && !tempFile.name.endsWith(".riv", ignoreCase = true)) {
                 val newFile = File(tempFile.parent, "$baseName.riv")
                 if (tempFile.renameTo(newFile)) {
                      finalFilePath = newFile.absolutePath
                      println("Renamed temp file to: ${newFile.name}")
                 } else {
                     println("Warning: Failed to rename temp file to .riv")
                 }
             } 

            DownloadResult(finalFilePath, fileType) 
        } catch (e: Exception) {
            println("Download error: ${e.message}")
            e.printStackTrace()
            tempFile?.delete() 
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}

// 添加一个辅助函数来检查文件是否为 ZIP
private fun isZipFile(file: File): Boolean {
    return try {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            val read = fis.read(magic)
            read == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
        }
    } catch (e: Exception) {
        false
    }
}

// 视频播放组件
@Composable
fun VideoPlayer(file: File) {
    AndroidView(
        factory = { context: Context ->
            VideoView(context).apply {
                setVideoPath(file.absolutePath)
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = true  // 循环播放
                    start()
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Rive 动画播放组件
@Composable
fun RivePlayer(file: java.io.File) {
    AndroidView(
        factory = { context ->
            app.rive.runtime.kotlin.RiveAnimationView(context).apply {
                // 读取本地 Rive 文件并播放
                val riveFile = RiveCoreFile(file.readBytes())
                setRiveFile(riveFile)
                autoplay = true  // 自动播放动画
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// 修改解压函数，恢复处理嵌套 ZIP 的逻辑
fun unzipMedia(context: Context, zipFile: File): List<MediaFile> {
    println("Starting unzip process for ${zipFile.name}")
    val outputDirName = "unzipped_${zipFile.nameWithoutExtension}_${System.currentTimeMillis()}"
    val outputDir = File(context.cacheDir, outputDirName).apply {
        deleteRecursively()
        mkdirs()
    }
    println("Unzipping to: ${outputDir.absolutePath}")
    
    val mediaFiles = mutableListOf<MediaFile>()

    try {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                // 忽略 Mac 的 __MACOSX 目录和 .DS_Store 等隐藏文件
                if (!entry.isDirectory && !entryName.startsWith("__MACOSX/") && !entryName.startsWith(".")) {
                    val outputFile = File(outputDir, entryName)
                    // 确保父目录存在
                    outputFile.parentFile?.mkdirs()

                    println("Extracting: ${entry.name} to ${outputFile.absolutePath}")
                    try {
                        FileOutputStream(outputFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }

                        // 检查解压出的文件是否是嵌套的 ZIP 文件
                        if (outputFile.extension.equals("zip", ignoreCase = true)) {
                            println("Found nested zip: ${outputFile.name}, processing...")
                            // 解压嵌套的 ZIP 文件到同一个输出目录
                            try {
                                ZipInputStream(BufferedInputStream(FileInputStream(outputFile))).use { nestedZis ->
                                    var nestedEntry = nestedZis.nextEntry
                                    while (nestedEntry != null) {
                                        val nestedEntryName = nestedEntry.name
                                        if (!nestedEntry.isDirectory && !nestedEntryName.startsWith("__MACOSX/") && !nestedEntryName.startsWith(".")) {
                                            // 注意：使用原始 entry 名称，防止路径重复
                                            val nestedOutputFile = File(outputDir, nestedEntryName)
                                            nestedOutputFile.parentFile?.mkdirs()
                                            println("Extracting nested: ${nestedEntry.name} to ${nestedOutputFile.absolutePath}")
                                            try {
                                                FileOutputStream(nestedOutputFile).use { nestedFos ->
                                                    val nestedBuffer = ByteArray(8192)
                                                    var nestedLen: Int
                                                    while (nestedZis.read(nestedBuffer).also { nestedLen = it } > 0) {
                                                        nestedFos.write(nestedBuffer, 0, nestedLen)
                                                    }
                                                }
                                                // 检查解压出的嵌套文件是否是媒体文件
                                                val nestedFileType = when (nestedOutputFile.extension.lowercase()) {
                                                    "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
                                                    "mp4", "3gp", "mkv", "webm" -> MediaType.VIDEO
                                                    else -> null
                                                }
                                                if (nestedFileType != null) {
                                                    mediaFiles.add(MediaFile(nestedOutputFile, nestedFileType))
                                                    println("Added nested media file: ${nestedOutputFile.name}, Type: $nestedFileType")
                                                } else {
                                                    println("Skipping non-media nested file: ${nestedOutputFile.name}")
                                                    nestedOutputFile.delete()
                                                }
                                            } catch (e: Exception) {
                                                println("Error extracting nested file ${nestedEntry.name}: ${e.message}")
                                                nestedOutputFile.delete()
                                            }
                                        }
                                        nestedZis.closeEntry()
                                        nestedEntry = nestedZis.nextEntry
                                    }
                                }
                            } catch (e: Exception) {
                                println("Error processing nested zip ${outputFile.name}: ${e.message}")
                            }
                            // 删除临时的嵌套 ZIP 文件
                            outputFile.delete()
                            println("Deleted nested zip: ${outputFile.name}")

                        } else {
                            // 如果不是嵌套 ZIP 文件，按原逻辑判断是否为媒体文件
                            val fileType = when (outputFile.extension.lowercase()) {
                                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
                                "mp4", "3gp", "mkv", "webm" -> MediaType.VIDEO
                                else -> null
                            }

                            if (fileType != null) {
                                mediaFiles.add(MediaFile(outputFile, fileType))
                                println("Added media file: ${outputFile.name}, Type: $fileType")
                            } else {
                                println("Skipping non-media file: ${outputFile.name}")
                                outputFile.delete()
                            }
                        }
                    } catch (e: Exception) {
                        println("Error extracting file ${entry.name}: ${e.message}")
                        // 尝试删除不完整的文件
                        outputFile.delete()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        println("Unzip completed. Found ${mediaFiles.size} media files.")
    } catch (e: Exception) {
        println("Unzip error: ${e.message}")
        e.printStackTrace()
        outputDir.deleteRecursively()
        return emptyList()
    }

    return mediaFiles
}

// 修改 unzipSavedZipForPreview 函数，使用正确的 Kotlin 字符串语法
fun unzipSavedZipForPreview(context: Context, savedZipFile: File): List<MediaFile> {
    println("Unzipping saved file for preview: ${savedZipFile.name}")
    val previewDir = File(context.cacheDir, "unzipped_saved_preview").apply {
         deleteRecursively()
         mkdirs()
    }
    println("Preview unzip target directory: ${previewDir.absolutePath}")

    val mediaFiles = mutableListOf<MediaFile>()
    try {
        ZipInputStream(BufferedInputStream(FileInputStream(savedZipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                // 使用正确的 Kotlin 字符串语法
                if (!entry.isDirectory && !entryName.startsWith("__MACOSX/") && !entryName.startsWith(".")) {
                     val outputFile = File(previewDir, entryName)
                     outputFile.parentFile?.mkdirs()
                     println("Extracting for preview: ${entry.name} to ${outputFile.absolutePath}")
                     try {
                          FileOutputStream(outputFile).use { fos ->
                              val buffer = ByteArray(8192)
                              var len: Int
                              while (zis.read(buffer).also { len = it } > 0) {
                                  fos.write(buffer, 0, len)
                              }
                          }

                          // --- 开始：添加处理嵌套 ZIP 的逻辑 (同 unzipMedia) ---
                          if (outputFile.extension.equals("zip", ignoreCase = true)) {
                              println("Found nested zip in saved file: ${outputFile.name}, processing...")
                              try {
                                  ZipInputStream(BufferedInputStream(FileInputStream(outputFile))).use { nestedZis ->
                                      var nestedEntry = nestedZis.nextEntry
                                      while (nestedEntry != null) {
                                          val nestedEntryName = nestedEntry.name
                                          // 使用正确的 Kotlin 字符串语法
                                          if (!nestedEntry.isDirectory && !nestedEntryName.startsWith("__MACOSX/") && !nestedEntryName.startsWith(".")) {
                                              val nestedOutputFile = File(previewDir, nestedEntryName)
                                              nestedOutputFile.parentFile?.mkdirs()
                                              println("Extracting nested for preview: ${nestedEntry.name} to ${nestedOutputFile.absolutePath}")
                                              try {
                                                  FileOutputStream(nestedOutputFile).use { nestedFos ->
                                                      val nestedBuffer = ByteArray(8192)
                                                      var nestedLen: Int
                                                      while (nestedZis.read(nestedBuffer).also { nestedLen = it } > 0) {
                                                          nestedFos.write(nestedBuffer, 0, nestedLen)
                                                      }
                                                  }
                                                  val nestedFileType = when (nestedOutputFile.extension.lowercase()) {
                                                      "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
                                                      "mp4", "3gp", "mkv", "webm" -> MediaType.VIDEO
                                                      else -> null
                                                  }
                                                  if (nestedFileType != null) {
                                                      mediaFiles.add(MediaFile(nestedOutputFile, nestedFileType))
                                                      println("Added nested media file for preview: ${nestedOutputFile.name}, Type: $nestedFileType")
                                                  } else {
                                                      println("Skipping non-media nested file for preview: ${nestedOutputFile.name}")
                                                      nestedOutputFile.delete()
                                                  }
                } catch (e: Exception) {
                                                  println("Error extracting nested file for preview ${nestedEntry.name}: ${e.message}")
                                                  nestedOutputFile.delete()
                }
            }
                                          nestedZis.closeEntry()
                                          nestedEntry = nestedZis.nextEntry
        }
                                  }
    } catch (e: Exception) {
                                  println("Error processing nested zip for preview ${outputFile.name}: ${e.message}")
                              }
                              outputFile.delete() // 删除临时的嵌套 ZIP
                              println("Deleted nested zip for preview: ${outputFile.name}")
                          } else {
                              // 如果不是嵌套 ZIP，按原逻辑判断媒体类型
                              val fileType = when (outputFile.extension.lowercase()) {
                                   "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
                                   "mp4", "3gp", "mkv", "webm" -> MediaType.VIDEO
                                   else -> null
                              }

                              if (fileType != null) {
                                   mediaFiles.add(MediaFile(outputFile, fileType))
                              } else {
                                   println("Skipping non-media file for preview: ${outputFile.name}")
                                   outputFile.delete()
                              }
                          }
                          // --- 结束：添加处理嵌套 ZIP 的逻辑 ---

                     } catch (e: Exception) {
                          println("Error extracting file for preview ${entry.name}: ${e.message}")
                          outputFile.delete()
                     }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        println("Preview unzip completed. Found ${mediaFiles.size} media files.")
    return mediaFiles
    } catch (e: Exception) {
        println("Error during preview unzip: ${e.message}")
        previewDir.deleteRecursively()
        throw e
    }
}

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
                        VideoView(context).apply {
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