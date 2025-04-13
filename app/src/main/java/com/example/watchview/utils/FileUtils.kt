package com.example.watchview.utils

import android.content.Context
import android.util.Log
import app.rive.runtime.kotlin.core.File as RiveCoreFile
import com.example.watchview.presentation.model.DownloadResult
import com.example.watchview.presentation.model.DownloadType
import com.example.watchview.presentation.model.MediaFile
import com.example.watchview.presentation.model.MediaType
import java.io.*
import java.net.*
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载文件工具类
 */
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
            
/**
 * 判断文件是否为 ZIP 文件
 */
fun isZipFile(file: File): Boolean {
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

/**
 * 判断文件是否为 Rive 文件
 */
fun isRiveFile(file: File): Boolean {
    return try {
        // 尝试加载文件作为 Rive 文件
        RiveCoreFile(file.readBytes())
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * 解压媒体文件
 */
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
                                                    "mp4", "3gp", "mkv", "webm", "mov" -> MediaType.VIDEO
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
                                "mp4", "3gp", "mkv", "webm", "mov" -> MediaType.VIDEO
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

/**
 * 解压已保存的 ZIP 文件进行预览
 */
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
                                                      "mp4", "3gp", "mkv", "webm", "mov" -> MediaType.VIDEO
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
                                   "mp4", "3gp", "mkv", "webm", "mov" -> MediaType.VIDEO
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