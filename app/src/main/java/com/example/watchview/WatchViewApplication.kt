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
            // 按官方 AAR 中的实际库名加载 JNI（librive-android.so => "rive-android"）
            ReLinker.loadLibrary(applicationContext, "rive-android")
            Log.i("WatchViewApplication", "Rive native library loaded successfully.")
            // 继续初始化 Rive，确保默认渲染器就绪
            Rive.init(applicationContext, defaultRenderer = RendererType.Rive)
            Log.i("WatchViewApplication", "Rive initialized successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("WatchViewApplication", "Failed to load/initialize Rive native library", e)
        } catch (t: Throwable) {
            Log.e("WatchViewApplication", "An unexpected error occurred during Rive native library loading", t)
        }
    }
}
