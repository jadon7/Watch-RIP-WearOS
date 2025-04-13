package com.example.watchview.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import com.example.watchview.presentation.model.ServerAddress

/**
 * 网络扫描屏幕组件
 */
@Composable
fun NetworkScanScreen(
    servers: List<ServerAddress>,
    onServerSelected: (ServerAddress) -> Unit,
    isScanning: Boolean,
    onCancelScan: () -> Unit = {},  // 添加取消回调
    hasLocalFile: Boolean = false,  // 添加本地文件标志
    onOpenLocalFile: () -> Unit = {},  // 添加处理本地文件回调
    isOpeningLocalFile: Boolean = false // 添加状态以控制按钮可用性
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
                
                // 添加按钮行
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 手动连接按钮
                    Button(
                        onClick = onCancelScan,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .padding(end = if (hasLocalFile) 4.dp else 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.DarkGray.copy(alpha = 0.6f)
                        ),
                        shape = CircleShape
                    ) {
                        Text(
                            "手动连接",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                    
                    // 有线传输按钮 (如果有本地文件)
                    if (hasLocalFile) {
                        Button(
                            onClick = onOpenLocalFile,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .padding(start = 4.dp)
                                .then(Modifier.alpha(if (isOpeningLocalFile) 0.2f else 1.0f)),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50)
                            ),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "有线传输",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
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
                                backgroundColor = Color(0xFF2196F3).copy(alpha = 0.3f)
                            ),
                            shape = CircleShape
                        ) {
                            Text(
                                text = server.ip,
                                color = Color(0xFF2196F3),
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
                                    backgroundColor = Color.DarkGray
                                ),
                                shape = CircleShape
                            ) {
                                Text(
                                    "手动输入",
                                    color = Color.White,
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
                                    backgroundColor = Color.DarkGray
                                ),
                                shape = CircleShape
                            ) {
                                Text(
                                    "重新扫描",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    
                    // 如果有本地文件，添加有线传输按钮
                    if (hasLocalFile) {
                        item {
                            Button(
                                onClick = onOpenLocalFile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(top = 6.dp)
                                    .then(Modifier.alpha(if (isOpeningLocalFile) 0.2f else 1.0f)),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF4CAF50)
                                ),
                                shape = CircleShape
                            ) {
                                Text("有线传输", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
} 