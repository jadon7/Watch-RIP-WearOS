package com.example.watchview.presentation.model

import java.io.File

// 媒体类型枚举，用于区分解压后的文件类型
enum class MediaType {
    IMAGE,
    VIDEO
}

// 数据类，用于表示解压后的单个媒体文件
data class MediaFile(
    val file: File,
    val type: MediaType
) 