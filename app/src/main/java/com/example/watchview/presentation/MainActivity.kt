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

// 主 Activity
class MainActivity : ComponentActivity() {
    private var isWifiConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()  // 显示启动画面
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)  // 设置 WearOS 默认主题

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
                // 将 Wi-Fi 状态传递到 DownloadScreen
                DownloadScreen(isWifiConnected = isWifiConnected)
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

// 下载界面组件
@Composable
fun DownloadScreen(isWifiConnected: Boolean) {
    // 状态管理
    val context = LocalContext.current
    var ipAddress by remember { mutableStateOf("") }        // IP 地址输入
    var downloadStatus by remember { mutableStateOf("") }   // 下载状态显示
    var playVideo by remember { mutableStateOf(false) }     // 是否播放视频
    var zipImages by remember { mutableStateOf<List<MediaFile>>(emptyList()) }  // 解压的图片列表
    var playRive by remember { mutableStateOf(false) }      // 是否播放 Rive 动画
    val coroutineScope = rememberCoroutineScope()

    // 如果 Wi-Fi 未连接，且不在播放视频 / 不在图片预览 / 不在 Rive 动画预览，则拦截显示全屏提醒
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

    if (playVideo) {
        // 播放下载的视频文件
        VideoPlayer(file = File(context.filesDir, "downloaded_file"))
    } else if (zipImages.isNotEmpty()) {
        // 使用 VerticalPager 显示解压后的图片，达到一屏一张的翻页效果
        ZipViewer(media = zipImages)
    } else if (playRive) {
        // 显示 Rive 动画
        RivePlayer(file = File(context.filesDir, "downloaded_file"))
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
                onValueChange = { ipAddress = it },
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
            Spacer(modifier = Modifier.height(4.dp))  // 输入框与按钮之间的间距
            Button(
                onClick = {
                    // 拼接 URL，默认端口设置为 8080
                    val url = "http://$ipAddress:8080"
                    downloadStatus = "下载中..."
                    coroutineScope.launch {
                        try {
                            when (val downloadType = downloadFile(context, url)) {
                                DownloadType.VIDEO -> playVideo = true
                                DownloadType.ZIP -> {
                                    // 解压压缩包中的媒体文件
                                    val mediaFiles = unzipMedia(context)
                                    if (mediaFiles.isEmpty()) {
                                        downloadStatus = "解压失败或压缩包中无可用媒体文件"
                                    } else {
                                        zipImages = mediaFiles  // 注意：需要修改 zipImages 的类型为 List<MediaFile>
                                    }
                                }
                                DownloadType.RIVE -> {
                                    playRive = true
                                }
                                else -> downloadStatus = "仅支持视频、图片和 Rive 文件"
                            }
                        } catch (e: Exception) {
                            downloadStatus = "下载失败: ${e.message}"
                        }
                    }
                },
                modifier = Modifier
                    .width(300.dp)
                    .height(36.dp),
                colors = androidx.compose.material.ButtonDefaults.buttonColors(
                    backgroundColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(
                    "下载文件",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.Black
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

// 文件下载功能实现
suspend fun downloadFile(context: Context, urlString: String): DownloadType {
    return withContext(Dispatchers.IO) {
        // 设置连接参数
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        // 检查响应状态
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("服务器返回错误代码: $responseCode")
        }

        // 根据 Content-Type 判断文件类型
        val contentType = connection.contentType ?: ""
        val fileType = when {
            contentType.startsWith("video/", ignoreCase = true) -> DownloadType.VIDEO
            contentType.contains("zip", ignoreCase = true) -> DownloadType.ZIP
            contentType.contains("application/rive", ignoreCase = true)
                || contentType.contains("octet-stream", ignoreCase = true) -> DownloadType.RIVE
            else -> DownloadType.OTHER
        }

        // 保存文件到内部存储
        val inputStream = connection.inputStream
        val file = File(context.filesDir, "downloaded_file")
        file.outputStream().use { fileOut ->
            inputStream.copyTo(fileOut)
        }
        inputStream.close()
        connection.disconnect()

        fileType
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

// ZIP 文件处理：解压并返回图片文件列表
fun unzipMedia(context: Context): List<MediaFile> {
    val zipFile = File(context.filesDir, "downloaded_file")
    val outputDir = File(context.filesDir, "unzipped_media")
    outputDir.mkdirs()
    
    val mediaFiles = mutableListOf<MediaFile>()
    ZipInputStream(FileInputStream(zipFile)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val fileName = entry.name.lowercase()
            if (!entry.isDirectory) {
                val isImage = fileName.endsWith(".jpg") || 
                             fileName.endsWith(".jpeg") || 
                             fileName.endsWith(".png")
                val isVideo = fileName.endsWith(".mp4") || 
                             fileName.endsWith(".3gp") || 
                             fileName.endsWith(".mkv")
                
                if (isImage || isVideo) {
                    val outFile = File(outputDir, fileName)
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    mediaFiles.add(
                        MediaFile(
                            file = outFile,
                            type = if (isImage) MediaType.IMAGE else MediaType.VIDEO
                        )
                    )
                }
            }
            entry = zis.nextEntry
        }
    }
    return mediaFiles
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ZipViewer(media: List<MediaFile>) {
    val pagerState = rememberPagerState()
    
    // 记录当前正在播放的视频页面
    var currentPlayingPage by remember { mutableStateOf<Int?>(null) }
    
    // 当页面改变时更新播放状态
    LaunchedEffect(pagerState.currentPage) {
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
        when (media[page].type) {
            MediaType.IMAGE -> {
                // 显示图片
                AndroidView(
                    factory = { context ->
                        android.widget.ImageView(context).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_XY
                        }
                    },
                    update = { view ->
                        val bitmap = android.graphics.BitmapFactory.decodeFile(media[page].file.absolutePath)
                        view.setImageBitmap(bitmap)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaType.VIDEO -> {
                // 显示视频
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoPath(media[page].file.absolutePath)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                // 只有当前页面是视频时才自动播放
                                if (page == currentPlayingPage) {
                                    start()
                                }
                            }
                        }
                    },
                    update = { view ->
                        // 根据是否是当前页面来控制播放状态
                        if (page == currentPlayingPage) {
                            if (!view.isPlaying) {
                                view.start()
                            }
                        } else {
                            if (view.isPlaying) {
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