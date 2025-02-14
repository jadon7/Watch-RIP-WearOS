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
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.watchview.R
import com.example.watchview.presentation.theme.WatchViewTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.TextFieldDefaults
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.compose.ui.viewinterop.AndroidView
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.rememberPagerState
import app.rive.runtime.kotlin.core.File as RiveCoreFile
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CircularProgressIndicator
import java.util.zip.ZipFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import com.example.watchview.utils.PreferencesManager

// 文件类型枚举，用于区分下载的文件类型
enum class DownloadType {
    VIDEO,      // 视频文件
    ZIP,        // 压缩包文件
    RIVE,       // Rive 动画文件
    OTHER       // 其他不支持的文件类型
}

// 媒体类型枚举，用于区分解压后的文件类型
enum class MediaType {
    IMAGE,
    VIDEO
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
                // 传入上次保存的 IP 地址
                DownloadScreen(
                    isWifiConnected = isWifiConnected,
                    initialIpAddress = preferencesManager.lastIpAddress,
                    onIpAddressChange = { newIp ->
                        // 当 IP 地址改变时保存
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
                        "取消",
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
                    "可用服务器",
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

// 修改 DownloadScreen 的参数和实现
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
    var downloadStatus by remember { mutableStateOf("") }   // 下载状态显示
    var playVideo by remember { mutableStateOf(false) }     // 是否播放视频
    var zipImages by remember { mutableStateOf<List<MediaFile>>(emptyList()) }  // 解压的图片列表
    var playRive by remember { mutableStateOf(false) }      // 是否播放 Rive 动画
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    // 添加下载进度状态
    var downloadProgress by remember { mutableStateOf(0f) }
    
    // 添加一个状态来控制是否显示预览
    var showPreview by remember { mutableStateOf(false) }

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
            try {
                downloadStatus = "开始下载..."
                downloadProgress = 0f
                
                val downloadType = downloadFile(context, url) { progress ->
                    downloadProgress = progress
                    downloadStatus = "下载中... ${(progress * 100).toInt()}%"
                }
                
                when (downloadType) {
                    DownloadType.ZIP -> {
                        downloadStatus = "解压文件中..."
                        val mediaFiles = unzipMedia(context)
                        if (mediaFiles.isEmpty()) {
                            downloadStatus = "解压失败或压缩包中无可用媒体文件"
                        } else {
                            println("Setting ${mediaFiles.size} media files to zipImages") // 调试日志
                            zipImages = mediaFiles
                            downloadStatus = ""
                            showPreview = true  // 设置显示预览
                            playRive = false    // 确保 Rive 播放状态为 false
                        }
                    }
                    DownloadType.RIVE -> {
                        downloadStatus = ""
                        playRive = true      // 设置播放 Rive
                        showPreview = false  // 关闭预览状态
                        zipImages = emptyList() // 清空媒体文件列表
                    }
                    else -> downloadStatus = "不支持的文件类型"
                }
            } catch (e: Exception) {
                downloadStatus = "下载失败: ${e.message}"
                println("Download error: ${e.message}") // 调试日志
                e.printStackTrace()
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

    // 添加状态变化监控
    LaunchedEffect(zipImages) {
        println("zipImages changed: size = ${zipImages.size}")
        zipImages.forEach { mediaFile ->
            println("Media file: ${mediaFile.file.absolutePath}, type: ${mediaFile.type}")
        }
    }

    if (!isWifiConnected && !playVideo && zipImages.isEmpty() && !playRive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "请连接 Wi-Fi 后继续",
                color = MaterialTheme.colors.primary,
                fontSize = 14.sp,
            )
        }
        return // 提前返回，不渲染下面内容
    }

    if (playRive) {
        // 显示 Rive 动画
        RivePlayer(file = File(context.filesDir, "downloaded_file"))
    } else if (showPreview && zipImages.isNotEmpty()) {
        ZipViewer(media = zipImages)
    } else if (showScanScreen && isWifiConnected) {
        NetworkScanScreen(
            servers = servers,
            isScanning = isScanning,
            onServerSelected = { server ->
                if (server.isManualInput) {
                    showScanScreen = false
                } else {
                    ipAddress = server.ip
                    showScanScreen = false
                    // 自动开始下载
                    handleDownload("http://$ipAddress:8080")
                }
            },
            onCancelScan = {
                // 取消当前扫描任务
                scanJob.value?.cancel()
                if (isScanning) {
                    // 如果是在扫描过程中取消，进入输入界面
                    showScanScreen = false
                } else {
                    // 如果是点击重新扫描，重新开始扫描
                    isScanning = true
                    scanJob.value = coroutineScope.launch {
                        servers = scanLocalNetwork(context)
                        isScanning = false
                    }
                }
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { 
                    ipAddress = it
                },
                label = { 
                    Text(
                        "服务器地址",
                        fontSize = 12.sp,          // 标签文字大小
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f)
                    )
                },
                placeholder = { Text("", color = MaterialTheme.colors.onBackground.copy(alpha = 1f), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth(),                // 宽度填充父容器
//                    .height(48.dp),               // 输入框高度
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp              // 输入文字大小
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
                },
                modifier = Modifier
                    .width(300.dp)
                    .height( 48.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
                ),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Text(
                    "下载文件",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))  // 按钮与状态文本之间的间距
            Text(
                text = downloadStatus,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                fontSize = 8.sp                  // 状态文本大小
            )
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

// 在 downloadFile 函数中修改文件类型判断逻辑
suspend fun downloadFile(
    context: Context, 
    urlString: String,
    onProgress: (Float) -> Unit
): DownloadType {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            println("Downloading from: $urlString")
            
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000  // 增加连接超时时间到15秒
                readTimeout = 45000     // 增加读取超时时间到45秒
                
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
                throw Exception("服务器返回错误代码: $responseCode")
            }
            
            val contentType = connection.contentType ?: "application/zip"
            val fileSize = connection.contentLength.toLong()
            println("Content type: $contentType, File size: $fileSize bytes")
            
            // 创建输出文件
            val file = File(context.filesDir, "downloaded_file")
            
            // 使用更大的缓冲区(1MB)进行读写
            val buffer = ByteArray(1024 * 1024)
            var lastProgressUpdate = 0L
            var totalBytesRead = 0L
            
            connection.inputStream.buffered().use { input ->
                FileOutputStream(file).buffered().use { output ->
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 降低进度回调频率，每读取1MB才更新一次
                        if (totalBytesRead - lastProgressUpdate >= 1024 * 1024) {
                            val progress = if (fileSize > 0) {
                                (totalBytesRead * 100 / fileSize).toInt()
                            } else 0
                            println("Download progress: $progress%")
                            lastProgressUpdate = totalBytesRead
                        }
                    }
                }
            }
            
            println("Download completed: $totalBytesRead bytes")
            
            // 修改文件类型判断逻辑
            val fileType = when {
                contentType.contains("zip", ignoreCase = true) || 
                isZipFile(file) -> DownloadType.ZIP
                
                contentType.contains("application/rive", ignoreCase = true) ||
                (contentType.contains("octet-stream", ignoreCase = true) && isRiveFile(file)) -> DownloadType.RIVE
                
                else -> throw Exception("不支持的文件类型: $contentType")
            }
            
            fileType
            
        } catch (e: Exception) {
            println("Download error: ${e.message}")
            e.printStackTrace()
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

// 修改解压函数
fun unzipMedia(context: Context): List<MediaFile> {
    println("Starting unzip process")
    val zipFile = File(context.filesDir, "downloaded_file")
    val outputDir = File(context.filesDir, "unzipped_media").apply {
        deleteRecursively() // 清除旧文件
        mkdirs()
    }
    
    val mediaFiles = mutableListOf<MediaFile>()
    try {
        println("Opening zip file: ${zipFile.absolutePath}")
        println("Zip file exists: ${zipFile.exists()}")
        println("Zip file size: ${zipFile.length()}")
        
        val zip = ZipFile(zipFile)
        println("Total entries in zip: ${zip.size()}")
        
        zip.entries().asSequence().forEach { entry ->
            println("Processing entry: ${entry.name}")
            
            // 跳过 MacOS 系统生成的隐藏文件夹
            if (entry.name.startsWith("__MACOSX") || entry.name.startsWith(".")) {
                println("Skipping MacOS system file: ${entry.name}")
                return@forEach
            }
            
            val tempFile = File(outputDir, entry.name)
            
            // 解压文件
            BufferedInputStream(zip.getInputStream(entry)).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    input.copyTo(output)
                }
            }
            
            println("Extracted file: ${tempFile.absolutePath}")
            
            // 如果是 zip 文件，继续解压
            if (tempFile.name.lowercase().endsWith(".zip")) {
                println("Found nested zip file: ${tempFile.name}")
                try {
                    val nestedZip = ZipFile(tempFile)
                    nestedZip.entries().asSequence().forEach { nestedEntry ->
                        println("Processing nested entry: ${nestedEntry.name}")
                        
                        if (nestedEntry.name.startsWith("__MACOSX") || nestedEntry.name.startsWith(".")) {
                            return@forEach
                        }
                        
                        val fileName = nestedEntry.name.substringAfterLast('/')
                        val outFile = File(outputDir, fileName)
                        
                        // 解压嵌套的文件
                        BufferedInputStream(nestedZip.getInputStream(nestedEntry)).use { input ->
                            BufferedOutputStream(FileOutputStream(outFile)).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        println("Extracted nested file: ${outFile.absolutePath}")
                        
                        // 检查是否为媒体文件
                        val isImage = fileName.lowercase().let { name ->
                            name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                            name.endsWith(".png") || name.endsWith(".gif") || 
                            name.endsWith(".bmp")
                        }
                        val isVideo = fileName.lowercase().let { name ->
                            name.endsWith(".mp4") || name.endsWith(".avi") || 
                            name.endsWith(".mov") || name.endsWith(".wmv") || 
                            name.endsWith(".flv")
                        }
                        
                        if (isImage || isVideo) {
                            println("Found media file: $fileName")
                            mediaFiles.add(
                                MediaFile(
                                    file = outFile,
                                    type = if (isImage) MediaType.IMAGE else MediaType.VIDEO
                                )
                            )
                        }
                    }
                    nestedZip.close()
                    tempFile.delete() // 删除临时的嵌套 zip 文件
                } catch (e: Exception) {
                    println("Error processing nested zip: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        zip.close()
        
    } catch (e: Exception) {
        println("Unzip error: ${e.message}")
        e.printStackTrace()
    }
    
    println("Found ${mediaFiles.size} media files")
    mediaFiles.forEach { 
        println("Media file: ${it.file.absolutePath}, type: ${it.type}")
    }
    
    return mediaFiles
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