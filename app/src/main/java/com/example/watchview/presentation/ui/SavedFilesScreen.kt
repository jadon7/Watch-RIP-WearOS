package com.example.watchview.presentation.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import com.example.watchview.presentation.MediaPreviewActivity
import com.example.watchview.presentation.RivePreviewActivity
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.SavedFileItem
import com.example.watchview.presentation.model.SavedFileType
import com.example.watchview.utils.unzipSavedZipForPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 保存的文件列表屏幕组件
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SavedFilesScreen(context: Context, bottomSheetState: BottomSheetState) {
    var savedFiles by remember { mutableStateOf<List<SavedFileItem>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(bottomSheetState.isExpanded) {
        if (bottomSheetState.isExpanded) {
            Log.d("SavedFilesScreen", "Sheet expanded, reloading files...")
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        if (savedFiles.isEmpty()) {
            // 空列表状态显示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无已保存的文件",
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 有文件时显示列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 80.dp)
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
                                    text = savedFile.name.substringBeforeLast('.'),
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
    }
} 