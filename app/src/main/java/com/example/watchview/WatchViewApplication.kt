package com.example.watchview

import android.app.Application
import android.util.Log
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.RendererType
import com.getkeepsafe.relinker.ReLinker

class WatchViewApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            // 假设 Rive JNI 库的名称是 "rive_android"
            // 你可能需要根据 kotlin-release.aar 的实际内容调整这个名称
            ReLinker.loadLibrary(applicationContext, "rive_android")
            Log.i("WatchViewApplication", "Rive native library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("WatchViewApplication", "Failed to load Rive native library", e)
            // 根据应用需求决定是否因为加载失败而抛出异常或采取其他措施
        } catch (t: Throwable) {
            Log.e("WatchViewApplication", "An unexpected error occurred during Rive native library loading", t)
        }

        // 重新启用 Rive 初始化，它可能也负责一些 JNI 设置或进一步的初始化
        // 如果 kotlin-release.aar 有不同的 Rive API，需要相应调整
        try {
            Rive.init(applicationContext, defaultRenderer = RendererType.Rive) // 或者 RendererType.Skia
            Log.i("WatchViewApplication", "Rive initialized successfully.")
        } catch (e: Exception) {
            Log.e("WatchViewApplication", "Failed to initialize Rive", e)
        }
    }
}