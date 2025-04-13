package com.example.watchview.presentation.model

import java.io.File

// 文件类型枚举，用于区分下载的文件类型
enum class DownloadType {
    ZIP,        // 压缩包文件
    RIVE,       // Rive 动画文件
}

// 媒体文件数据类
data class MediaFile(
    val file: File,
    val type: MediaType
)

// 服务器地址数据类
data class ServerAddress(
    val ip: String,
    val isManualInput: Boolean = false
)

// 预览类型密封类定义
sealed class PreviewType {
    data class MediaPreview(val mediaFiles: List<MediaFile>) : PreviewType()
    data class RivePreview(val riveFile: File) : PreviewType()
}

// 保存的 Rive 文件数据类
data class SavedRiveFile(
    val name: String,
    val file: File,
    val lastModified: Long
)

// 保存的文件类型枚举
enum class SavedFileType {
    RIVE,
    MEDIA_LIST // 代表 ZIP 文件
}

// 保存的文件项数据类
data class SavedFileItem(
    val name: String,
    val file: File,
    val type: SavedFileType,
    val lastModified: Long
)

// 下载结果数据类
data class DownloadResult(val filePath: String?, val type: DownloadType) 