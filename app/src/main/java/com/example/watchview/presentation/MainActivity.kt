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
import app.rive.runtime.kotlin.core.File as RiveFile

// 新增枚举用于区分下载文件类型
enum class DownloadType {
    VIDEO,
    ZIP,
    RIVE,  // 新增 RIVE 类型，用于 .riv 文件
    OTHER
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WatchViewTheme {
                // 使用 DownloadScreen 提供输入 IP 并下载文件的功能
                DownloadScreen()
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

// 新增用于手表端下载功能的界面：
@Composable
fun DownloadScreen() {
    val context = LocalContext.current
    var ipAddress by remember { mutableStateOf("") }
    var downloadStatus by remember { mutableStateOf("") }
    var playVideo by remember { mutableStateOf(false) }
    var playRive by remember { mutableStateOf(false) }
    var zipImages by remember { mutableStateOf<List<File>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    if (playVideo) {
        // 播放下载的视频文件
        VideoPlayer(file = File(context.filesDir, "downloaded_file"))
    } else if (playRive) {
        // 播放下载的 Rive 文件
        RivePlayer(file = File(context.filesDir, "downloaded_file"))
    } else if (zipImages.isNotEmpty()) {
        // 使用 VerticalPager 显示解压后的图片，达到一屏一张的翻页效果
        ZipViewer(images = zipImages)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("请输入服务器 IP", color = MaterialTheme.colors.onBackground, fontSize = 12.sp) },
                placeholder = { Text("例如: 192.168.1.100", color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    cursorColor = MaterialTheme.colors.primary,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                    placeholderColor = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    // 拼接 URL，默认端口设置为 8080
                    val url = "http://$ipAddress:8080"
                    downloadStatus = "正在下载..."
                    coroutineScope.launch {
                        try {
                            val fileType = downloadFile(context, url)
                            when (fileType) {
                                DownloadType.VIDEO -> playVideo = true
                                DownloadType.ZIP -> {
                                    zipImages = unzipImages(context)
                                    if (zipImages.isEmpty())
                                        downloadStatus = "解压失败或压缩包中无图片"
                                }
                                DownloadType.RIVE -> playRive = true
                                else -> downloadStatus = "下载成功，但文件类型不支持（仅支持视频、压缩包和 Rive 文件）"
                            }
                        } catch (e: Exception) {
                            downloadStatus = "下载失败: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.height(36.dp)
            ) {
                Text("下载文件", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = downloadStatus,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onBackground,
                fontSize = 12.sp
            )
        }
    }
}

// 实现下载文件的功能，每次下载都会覆盖掉上次保存的文件，返回是否为视频文件
suspend fun downloadFile(context: Context, urlString: String): DownloadType {
    return withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        // 可选：设置超时参数
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("服务器返回错误代码: $responseCode")
        }

        // 判断文件类型，根据响应头的 Content-Type 来判定
        val contentType = connection.contentType ?: ""
        val fileType = when {
            contentType.startsWith("video/") -> DownloadType.VIDEO
            contentType.contains("zip", ignoreCase = true) -> DownloadType.ZIP
            urlString.lowercase().endsWith(".riv") -> DownloadType.RIVE
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

@Composable
fun VideoPlayer(file: File) {
    AndroidView(
        factory = { context: Context ->
            // 使用 VideoView 播放视频
            val videoView = VideoView(context)
            videoView.setVideoPath(file.absolutePath)
            videoView.setOnPreparedListener { mediaPlayer: MediaPlayer ->
                mediaPlayer.isLooping = true
                videoView.start()
            }
            videoView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun RivePlayer(file: File) {
    AndroidView(
        factory = { context ->
            // 创建 RiveAnimationView 并加载下载的 .riv 文件
            app.rive.runtime.kotlin.RiveAnimationView(context).apply {
                // 读取文件字节数组
                val fileBytes = file.readBytes()
                // 直接传入 ByteArray 给 RiveFile 的构造函数
                val riveFile = RiveFile(fileBytes)
                setRiveFile(riveFile)
            }
        }
    )
}

// 新增解压函数：解压下载的压缩包并返回所有图片文件
fun unzipImages(context: Context): List<File> {
    val zipFile = File(context.filesDir, "downloaded_file")
    val outputDir = File(context.filesDir, "unzipped_images")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val imageFiles = mutableListOf<File>()
    ZipInputStream(FileInputStream(zipFile)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val fileName = entry.name
            if (!entry.isDirectory &&
                (fileName.endsWith(".jpg", ignoreCase = true) ||
                 fileName.endsWith(".jpeg", ignoreCase = true) ||
                 fileName.endsWith(".png", ignoreCase = true))
            ) {
                val outFile = File(outputDir, fileName)
                FileOutputStream(outFile).use { fos ->
                    zis.copyTo(fos)
                }
                imageFiles.add(outFile)
            }
            entry = zis.nextEntry
        }
    }
    return imageFiles
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ZipViewer(images: List<File>) {
    val pagerState = rememberPagerState()
    VerticalPager(
        count = images.size,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        AndroidView(
            factory = { context: Context ->
                android.widget.ImageView(context).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_XY
                }
            },
            update = { view ->
                val bitmap = android.graphics.BitmapFactory.decodeFile(images[page].absolutePath)
                view.setImageBitmap(bitmap)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}