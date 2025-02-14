package com.example.watchview.utils

import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class DownloadUtils {
    companion object {
        private const val CHUNK_SIZE = 1024 * 1024 * 2 // 2MB per chunk
        
        suspend fun downloadWithChunks(
            urlString: String,
            outputFile: File,
            chunks: Int = 4
        ) = withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            
            val fileSize = connection.contentLength.toLong()
            connection.disconnect()
            
            if (fileSize <= 0) {
                // 如果无法获取文件大小，回退到普通下载
                downloadNormally(urlString, outputFile)
                return@withContext
            }
            
            // 创建空文件
            RandomAccessFile(outputFile, "rw").use { it.setLength(fileSize) }
            
            // 计算每个分片的大小
            val chunkSize = fileSize / chunks
            
            // 创建并发下载任务
            val jobs = (0 until chunks).map { index ->
                async(Dispatchers.IO) {
                    val start = index * chunkSize
                    val end = if (index == chunks - 1) fileSize - 1 else start + chunkSize - 1
                    downloadChunk(urlString, outputFile, start, end)
                }
            }
            
            // 等待所有分片下载完成
            jobs.awaitAll()
        }
        
        private suspend fun downloadChunk(
            urlString: String,
            outputFile: File,
            startByte: Long,
            endByte: Long
        ) = withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=$startByte-$endByte")
                    connectTimeout = 15000
                    readTimeout = 45000
                }
                
                val input = connection.inputStream.buffered()
                val output = RandomAccessFile(outputFile, "rw")
                
                output.use { file ->
                    input.use { stream ->
                        file.seek(startByte)
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            file.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        
        private suspend fun downloadNormally(
            urlString: String,
            outputFile: File
        ) = withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 45000
                }
                
                connection.inputStream.buffered().use { input ->
                    outputFile.outputStream().buffered().use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }
} 