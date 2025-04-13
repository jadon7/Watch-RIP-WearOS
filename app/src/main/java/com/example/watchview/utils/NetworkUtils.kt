package com.example.watchview.utils

import android.content.Context
import com.example.watchview.presentation.model.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 扫描本地网络，查找运行服务的设备
 */
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