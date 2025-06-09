package com.example.watchview.utils

import android.util.Log
import app.rive.runtime.kotlin.RiveAnimationView

/**
 * Rive 数据绑定使用示例
 * 
 * 这个文件展示了如何使用 RiveDataBindingHelper 进行数据绑定操作
 * 根据 Rive 官方文档: https://rive.app/docs/runtimes/data-binding#android
 */
class RiveDataBindingUsageExample {
    
    companion object {
        private const val TAG = "RiveDataBindingExample"
        
        /**
         * 基础使用示例
         */
        fun basicUsageExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            
            // 1. 初始化数据绑定
            helper.initialize()
            
            // 2. 获取 ViewModel
            val viewModel = helper.getViewModelByName("WatchFaceViewModel")
            if (viewModel != null) {
                // 3. 创建 ViewModel 实例
                val instance = helper.createDefaultInstance(viewModel, "watchface")
                
                if (instance != null) {
                    // 4. 设置属性
                    helper.setNumberProperty("watchface", "hour", 14.5f)
                    helper.setNumberProperty("watchface", "minute", 30.0f)
                    helper.setStringProperty("watchface", "title", "My Watch")
                    helper.setBooleanProperty("watchface", "isVisible", true)
                    
                    // 5. 获取属性
                    val currentHour = helper.getNumberProperty("watchface", "hour")
                    val title = helper.getStringProperty("watchface", "title")
                    val isVisible = helper.getBooleanProperty("watchface", "isVisible")
                    
                    Log.d(TAG, "Current hour: $currentHour")
                    Log.d(TAG, "Title: $title")
                    Log.d(TAG, "Is visible: $isVisible")
                    
                    // 6. 触发触发器
                    helper.fireTrigger("watchface", "updateDisplay")
                }
            }
            
            // 7. 清理资源（通常在 Activity/Fragment 销毁时调用）
            // helper.cleanup()
        }
        
        /**
         * 高级使用示例 - 嵌套属性
         */
        fun nestedPropertiesExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            helper.initialize()
            
            val viewModel = helper.getViewModelByName("SettingsViewModel")
            if (viewModel != null) {
                helper.createDefaultInstance(viewModel, "settings")
                
                // 使用嵌套属性路径
                helper.setNestedNumberProperty("settings", "display/brightness", 0.8f)
                helper.setNestedNumberProperty("settings", "sound/volume", 0.6f)
                
                val brightness = helper.getNestedNumberProperty("settings", "display/brightness")
                Log.d(TAG, "Display brightness: $brightness")
            }
        }
        
        /**
         * 枚举使用示例
         */
        fun enumUsageExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            helper.initialize()
            
            // 获取可用的枚举
            val enums = helper.getAvailableEnums()
            enums.forEach { (enumName, values) ->
                Log.d(TAG, "Enum $enumName has values: ${values.joinToString(", ")}")
            }
            
            val viewModel = helper.getViewModelByName("ThemeViewModel")
            if (viewModel != null) {
                helper.createDefaultInstance(viewModel, "theme")
                
                // 设置枚举属性
                helper.setEnumProperty("theme", "colorScheme", "dark")
                helper.setEnumProperty("theme", "layout", "compact")
                
                // 获取枚举属性
                val colorScheme = helper.getEnumProperty("theme", "colorScheme")
                Log.d(TAG, "Current color scheme: $colorScheme")
            }
        }
        
        /**
         * 多个 ViewModel 实例示例
         */
        fun multipleInstancesExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            helper.initialize()
            
            val viewModel = helper.getViewModelByName("WidgetViewModel")
            if (viewModel != null) {
                // 创建多个实例
                helper.createInstanceByName(viewModel, "Widget1", "widget1")
                helper.createInstanceByName(viewModel, "Widget2", "widget2")
                helper.createBlankInstance(viewModel, "widget3")
                
                // 为不同实例设置不同的属性
                helper.setNumberProperty("widget1", "x", 100.0f)
                helper.setNumberProperty("widget1", "y", 200.0f)
                
                helper.setNumberProperty("widget2", "x", 300.0f)
                helper.setNumberProperty("widget2", "y", 400.0f)
                
                helper.setNumberProperty("widget3", "x", 500.0f)
                helper.setNumberProperty("widget3", "y", 600.0f)
            }
        }
        
        /**
         * 实时数据更新示例
         */
        fun realTimeUpdateExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            helper.initialize()
            
            val viewModel = helper.getViewModelByName("ClockViewModel")
            if (viewModel != null) {
                helper.createDefaultInstance(viewModel, "clock")
                
                // 模拟实时更新（实际使用中应该在协程或定时器中进行）
                val currentTime = System.currentTimeMillis()
                val hours = ((currentTime / (1000 * 60 * 60)) % 24).toFloat()
                val minutes = ((currentTime / (1000 * 60)) % 60).toFloat()
                val seconds = ((currentTime / 1000) % 60).toFloat()
                
                helper.setNumberProperty("clock", "hours", hours)
                helper.setNumberProperty("clock", "minutes", minutes)
                helper.setNumberProperty("clock", "seconds", seconds)
                
                // 触发更新动画
                helper.fireTrigger("clock", "updateTime")
            }
        }
        
        /**
         * 错误处理示例
         */
        fun errorHandlingExample(riveView: RiveAnimationView) {
            val helper = RiveDataBindingHelper(riveView)
            
            try {
                helper.initialize()
                
                // 尝试获取不存在的 ViewModel
                val nonExistentViewModel = helper.getViewModelByName("NonExistentViewModel")
                if (nonExistentViewModel == null) {
                    Log.w(TAG, "ViewModel not found, using fallback")
                    // 使用默认 ViewModel 或其他备选方案
                    val defaultViewModel = helper.getDefaultViewModel()
                    if (defaultViewModel != null) {
                        helper.createDefaultInstance(defaultViewModel, "fallback")
                    }
                }
                
                // 尝试设置不存在的属性
                val success = helper.setNumberProperty("fallback", "nonExistentProperty", 1.0f)
                if (!success) {
                    Log.w(TAG, "Failed to set property, property might not exist")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in data binding operations", e)
                // 实现适当的错误恢复逻辑
            } finally {
                // 确保资源得到清理
                helper.cleanup()
            }
        }
    }
} 