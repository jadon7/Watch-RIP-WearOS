package com.example.watchview

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.RendererType

class WatchViewApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 设置Rive渲染器为Canvas渲染器
//        Rive.init(applicationContext, defaultRenderer = RendererType.Rive)
    }
}