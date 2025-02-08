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
    val coroutineScope = rememberCoroutineScope()

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
                // 拼接 URL，默认端口设置为 8080，并指定下载路径为 /download（可根据服务端实际情况修改）
                val url = "http://$ipAddress:8080"
                downloadStatus = "正在下载..."
                coroutineScope.launch {
                    try {
                        downloadFile(context, url)
                        downloadStatus = "下载成功！"
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

// 实现下载文件的功能，每次下载都会覆盖掉上次保存的文件
suspend fun downloadFile(context: Context, urlString: String) {
    withContext(Dispatchers.IO) {
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

        // 通过内部存储保存文件（每次下载都会覆盖旧的文件）
        val inputStream = connection.inputStream
        val file = File(context.filesDir, "downloaded_file")
        file.outputStream().use { fileOut ->
            inputStream.copyTo(fileOut)
        }
        inputStream.close()
        connection.disconnect()
    }
}