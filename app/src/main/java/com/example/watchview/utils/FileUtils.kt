import java.io.File
import java.util.zip.ZipFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream

fun unzipFile(zipFilePath: String, destinationPath: String): List<String> {
    val mediaFiles = mutableListOf<String>()
    
    try {
        println("Attempting to unzip file: $zipFilePath")
        println("Destination path: $destinationPath")
        
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) {
            println("Error: Zip file does not exist at path: $zipFilePath")
            return mediaFiles
        }
        
        println("Zip file size: ${zipFile.length()} bytes")
        
        val buffer = ByteArray(1024)
        val zip = ZipFile(zipFile)
        
        println("Starting unzip process")
        println("Total entries in zip: ${zip.size()}")
        
        // 创建临时目录用于解压
        val tempDir = File(destinationPath, "temp_${System.currentTimeMillis()}")
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            println("Error: Failed to create temp directory: ${tempDir.absolutePath}")
            return mediaFiles
        }
        
        zip.entries().asSequence().forEach { entry ->
            println("Processing zip entry: ${entry.name} (size: ${entry.size} bytes)")
            
            // 跳过 MacOS 系统生成的隐藏文件夹
            if (entry.name.startsWith("__MACOSX") || entry.name.startsWith(".")) {
                println("Skipping MacOS system file: ${entry.name}")
                return@forEach
            }
            
            val newFile = File(tempDir, entry.name)
            println("Extracting to: ${newFile.absolutePath}")
            
            // 创建目录
            if (entry.isDirectory) {
                if (!newFile.exists() && !newFile.mkdirs()) {
                    println("Failed to create directory: ${newFile.absolutePath}")
                }
                return@forEach
            }
            
            // 确保父目录存在
            newFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    println("Failed to create parent directory: ${parent.absolutePath}")
                }
            }
            
            try {
                // 解压文件
                BufferedInputStream(zip.getInputStream(entry)).use { input ->
                    BufferedOutputStream(FileOutputStream(newFile)).use { output ->
                        var len = 0
                        var totalBytes = 0L
                        while (input.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                            totalBytes += len
                        }
                        println("Extracted ${entry.name}: $totalBytes bytes")
                    }
                }
                
                // 如果是zip文件，继续解压
                if (newFile.name.lowercase().endsWith(".zip")) {
                    println("Found nested zip file: ${newFile.name}")
                    val nestedMediaFiles = unzipFile(newFile.absolutePath, destinationPath)
                    println("Found ${nestedMediaFiles.size} media files in nested zip")
                    mediaFiles.addAll(nestedMediaFiles)
                    if (!newFile.delete()) {
                        println("Failed to delete temporary zip file: ${newFile.absolutePath}")
                    }
                } else if (isMediaFile(newFile.name)) {
                    // 将媒体文件移动到目标目录
                    val targetFile = File(destinationPath, newFile.name)
                    println("Moving media file to: ${targetFile.absolutePath}")
                    if (newFile.renameTo(targetFile)) {
                        mediaFiles.add(targetFile.absolutePath)
                        println("Successfully moved media file")
                    } else {
                        println("Failed to move media file")
                    }
                } else {
                    println("Not a media file: ${newFile.name}")
                }
            } catch (e: Exception) {
                println("Error processing entry ${entry.name}: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("Found ${mediaFiles.size} media files")
        zip.close()
        
        // 清理临时目录
        if (!tempDir.deleteRecursively()) {
            println("Failed to delete temp directory: ${tempDir.absolutePath}")
        }
        
    } catch (e: Exception) {
        e.printStackTrace()
        println("Error during unzip: ${e.message}")
    }
    
    return mediaFiles
}

private fun isMediaFile(fileName: String): Boolean {
    val mediaExtensions = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp",  // 图片格式
        ".mp4", ".avi", ".mov", ".wmv", ".flv"    // 视频格式
    )
    val isMedia = mediaExtensions.any { fileName.lowercase().endsWith(it) }
    println("Checking if $fileName is media file: $isMedia")
    return isMedia
} 