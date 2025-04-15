/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.watchview.presentation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.watchview.presentation.theme.WatchViewTheme
import com.example.watchview.presentation.ui.DownloadScreen
import com.example.watchview.utils.PreferencesManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import com.example.watchview.presentation.ui.ACTION_TRIGGER_WIRED_PREVIEW

/**
 * 主 Activity
 */
class MainActivity : ComponentActivity() {
    private var isWifiConnected by mutableStateOf(false)
    private lateinit var preferencesManager: PreferencesManager

    // 定义广播 Action 常量
    private val OPEN_FILE_ACTION = "com.example.watchview.OPEN_FILE"
    private val CLOSE_PREVIEW_ACTION = "com.example.watchview.CLOSE_PREVIEW"

    // 创建广播接收器实例
    private val openFileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivityReceiver", "Received broadcast: ${intent?.action}")
            if (intent?.action == OPEN_FILE_ACTION && context != null) {
                Log.i("MainActivityReceiver", "Received $OPEN_FILE_ACTION broadcast.")
                val message = intent.getStringExtra("message") // 获取包含文件名的 message
                Log.d("MainActivityReceiver", "Broadcast message (filename): $message")
                
                // 1. 移除 Toast 显示
                // val toastMessage = message ?: "收到广播 (无文件名)"
                // Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                // Log.i("MainActivityReceiver", "Displayed Toast: '$toastMessage'")

                // 2. 发送关闭预览的广播命令
                Log.d("MainActivityReceiver", "Sending $CLOSE_PREVIEW_ACTION broadcast to close previews.")
                val closeIntent = Intent(CLOSE_PREVIEW_ACTION).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(closeIntent)
                
                // 3. 延迟后发送触发有线预览检查的广播
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d("MainActivityReceiver", "Sending ACTION_TRIGGER_WIRED_PREVIEW broadcast after delay.")
                    val triggerIntent = Intent(ACTION_TRIGGER_WIRED_PREVIEW).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(triggerIntent)
                }, 500) // 延迟 500ms
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()  // 显示启动画面
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)  // 设置 WearOS 默认主题

        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

        // ------------------------- 监控 Wi-Fi 状态 -------------------------
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

        // 注册广播接收器
        val intentFilter = IntentFilter(OPEN_FILE_ACTION)
        registerReceiver(openFileReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        Log.i("MainActivity", "Registered openFileReceiver for action $OPEN_FILE_ACTION (exported)")

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

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(openFileReceiver)
        Log.i("MainActivity", "Unregistered openFileReceiver")
    }
}