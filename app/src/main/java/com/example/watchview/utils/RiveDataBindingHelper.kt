package com.example.watchview.utils

import android.util.Log
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File as RiveFile
import app.rive.runtime.kotlin.core.ViewModel
import app.rive.runtime.kotlin.core.ViewModelInstance

/**
 * Rive 数据绑定辅助类
 * 根据 Rive 官方文档实现基础的数据绑定功能
 * 参考: https://rive.app/docs/runtimes/data-binding#android
 */
class RiveDataBindingHelper(private val riveView: RiveAnimationView) {
    
    companion object {
        private const val TAG = "RiveDataBinding"
    }
    
    private var riveFile: RiveFile? = null
    private var viewModels: MutableMap<String, ViewModel> = mutableMapOf()
    private var viewModelInstances: MutableMap<String, ViewModelInstance> = mutableMapOf()
    
    /**
     * 初始化数据绑定
     */
    fun initialize() {
        try {
            riveFile = riveView.file
            if (riveFile == null) {
                Log.w(TAG, "Rive file is null, cannot initialize data binding")
                return
            }
            
            Log.i(TAG, "Rive data binding initialized successfully")
            logAvailableViewModels()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing data binding", e)
        }
    }
    
    /**
     * 根据名称获取 ViewModel
     */
    fun getViewModelByName(name: String): ViewModel? {
        return try {
            if (viewModels.containsKey(name)) {
                viewModels[name]
            } else {
                val viewModel = riveFile?.getViewModelByName(name)
                if (viewModel != null) {
                    viewModels[name] = viewModel
                    Log.d(TAG, "Retrieved ViewModel by name: $name")
                } else {
                    Log.w(TAG, "ViewModel not found: $name")
                }
                viewModel
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ViewModel by name: $name", e)
            null
        }
    }
    
    /**
     * 根据索引获取 ViewModel
     */
    fun getViewModelByIndex(index: Int): ViewModel? {
        return try {
            val viewModel = riveFile?.getViewModelByIndex(index)
            if (viewModel != null) {
                Log.d(TAG, "Retrieved ViewModel by index: $index")
            } else {
                Log.w(TAG, "ViewModel not found at index: $index")
            }
            viewModel
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ViewModel by index: $index", e)
            null
        }
    }
    
    /**
     * 获取默认的 ViewModel
     */
    fun getDefaultViewModel(): ViewModel? {
        return try {
            val artboard = riveView.controller?.activeArtboard
            if (artboard != null) {
                val viewModel = riveFile?.defaultViewModelForArtboard(artboard)
                if (viewModel != null) {
                    Log.d(TAG, "Retrieved default ViewModel for artboard")
                } else {
                    Log.w(TAG, "No default ViewModel found for artboard")
                }
                viewModel
            } else {
                Log.w(TAG, "No active artboard found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default ViewModel", e)
            null
        }
    }
    
    /**
     * 创建 ViewModel 实例（空白实例）
     */
    fun createBlankInstance(viewModel: ViewModel, instanceKey: String): ViewModelInstance? {
        return try {
            val instance = viewModel.createDefaultInstance()
            if (instance != null) {
                viewModelInstances[instanceKey] = instance
                Log.d(TAG, "Created blank instance: $instanceKey")
            } else {
                Log.w(TAG, "Failed to create blank instance: $instanceKey")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error creating blank instance: $instanceKey", e)
            null
        }
    }
    
    /**
     * 创建默认 ViewModel 实例
     */
    fun createDefaultInstance(viewModel: ViewModel, instanceKey: String): ViewModelInstance? {
        return try {
            val instance = viewModel.createDefaultInstance()
            if (instance != null) {
                viewModelInstances[instanceKey] = instance
                Log.d(TAG, "Created default instance: $instanceKey")
            } else {
                Log.w(TAG, "Failed to create default instance: $instanceKey")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default instance: $instanceKey", e)
            null
        }
    }
    
    /**
     * 根据名称创建 ViewModel 实例
     */
    fun createInstanceByName(viewModel: ViewModel, instanceName: String, instanceKey: String): ViewModelInstance? {
        return try {
            val instance = viewModel.createInstanceFromName(instanceName)
            if (instance != null) {
                viewModelInstances[instanceKey] = instance
                Log.d(TAG, "Created instance by name: $instanceName -> $instanceKey")
            } else {
                Log.w(TAG, "Failed to create instance by name: $instanceName")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error creating instance by name: $instanceName", e)
            null
        }
    }
    
    /**
     * 根据索引创建 ViewModel 实例
     */
    fun createInstanceByIndex(viewModel: ViewModel, index: Int, instanceKey: String): ViewModelInstance? {
        return try {
            val instance = viewModel.createInstanceFromIndex(index)
            if (instance != null) {
                viewModelInstances[instanceKey] = instance
                Log.d(TAG, "Created instance by index: $index -> $instanceKey")
            } else {
                Log.w(TAG, "Failed to create instance by index: $index")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error creating instance by index: $index", e)
            null
        }
    }
    
    /**
     * 获取已创建的 ViewModel 实例
     */
    fun getViewModelInstance(instanceKey: String): ViewModelInstance? {
        return viewModelInstances[instanceKey]
    }
    
    /**
     * 设置数字属性
     */
    fun setNumberProperty(instanceKey: String, propertyName: String, value: Float): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getNumberProperty(propertyName)
                    if (property != null) {
                        property.value = value
                        Log.d(TAG, "Set number property: $instanceKey.$propertyName = $value")
                        true
                    } else {
                        Log.w(TAG, "Number property not found: $instanceKey.$propertyName")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    // 属性不存在时的异常，这是正常情况，不需要记录为错误
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting number property: $instanceKey.$propertyName = $value", e)
            false
        }
    }
    
    /**
     * 获取数字属性
     */
    fun getNumberProperty(instanceKey: String, propertyName: String): Float? {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getNumberProperty(propertyName)
                    if (property != null) {
                        val value = property.value
                        Log.d(TAG, "Get number property: $instanceKey.$propertyName = $value")
                        value
                    } else {
                        Log.w(TAG, "Number property not found: $instanceKey.$propertyName")
                        null
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    null
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting number property: $instanceKey.$propertyName", e)
            null
        }
    }
    
    /**
     * 设置字符串属性
     */
    fun setStringProperty(instanceKey: String, propertyName: String, value: String): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getStringProperty(propertyName)
                    if (property != null) {
                        property.value = value
                        Log.d(TAG, "Set string property: $instanceKey.$propertyName = $value")
                        true
                    } else {
                        Log.w(TAG, "String property not found: $instanceKey.$propertyName")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting string property: $instanceKey.$propertyName = $value", e)
            false
        }
    }
    
    /**
     * 获取字符串属性
     */
    fun getStringProperty(instanceKey: String, propertyName: String): String? {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getStringProperty(propertyName)
                    if (property != null) {
                        val value = property.value
                        Log.d(TAG, "Get string property: $instanceKey.$propertyName = $value")
                        value
                    } else {
                        Log.w(TAG, "String property not found: $instanceKey.$propertyName")
                        null
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    null
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting string property: $instanceKey.$propertyName", e)
            null
        }
    }
    
    /**
     * 设置布尔属性
     */
    fun setBooleanProperty(instanceKey: String, propertyName: String, value: Boolean): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getBooleanProperty(propertyName)
                    if (property != null) {
                        property.value = value
                        Log.d(TAG, "Set boolean property: $instanceKey.$propertyName = $value")
                        true
                    } else {
                        Log.w(TAG, "Boolean property not found: $instanceKey.$propertyName")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting boolean property: $instanceKey.$propertyName = $value", e)
            false
        }
    }
    
    /**
     * 获取布尔属性
     */
    fun getBooleanProperty(instanceKey: String, propertyName: String): Boolean? {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getBooleanProperty(propertyName)
                    if (property != null) {
                        val value = property.value
                        Log.d(TAG, "Get boolean property: $instanceKey.$propertyName = $value")
                        value
                    } else {
                        Log.w(TAG, "Boolean property not found: $instanceKey.$propertyName")
                        null
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    null
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting boolean property: $instanceKey.$propertyName", e)
            null
        }
    }
    
    /**
     * 触发触发器属性
     */
    fun fireTrigger(instanceKey: String, triggerName: String): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val trigger = instance.getTriggerProperty(triggerName)
                    if (trigger != null) {
                        try {
                            trigger.trigger()
                        } catch (e: NoSuchMethodError) {
                            Log.w(TAG, "trigger() method not found, trying alternative approach")
                            return false
                        }
                        Log.d(TAG, "Fired trigger: $instanceKey.$triggerName")
                        true
                    } else {
                        Log.w(TAG, "Trigger not found: $instanceKey.$triggerName")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Trigger not found (expected): $instanceKey.$triggerName")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing trigger: $instanceKey.$triggerName", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error firing trigger: $instanceKey.$triggerName", e)
            false
        }
    }
    
    /**
     * 设置枚举属性
     */
    fun setEnumProperty(instanceKey: String, propertyName: String, value: String): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getEnumProperty(propertyName)
                    if (property != null) {
                        property.value = value
                        Log.d(TAG, "Set enum property: $instanceKey.$propertyName = $value")
                        true
                    } else {
                        Log.w(TAG, "Enum property not found: $instanceKey.$propertyName")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting enum property: $instanceKey.$propertyName = $value", e)
            false
        }
    }
    
    /**
     * 获取枚举属性
     */
    fun getEnumProperty(instanceKey: String, propertyName: String): String? {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getEnumProperty(propertyName)
                    if (property != null) {
                        val value = property.value
                        Log.d(TAG, "Get enum property: $instanceKey.$propertyName = $value")
                        value
                    } else {
                        Log.w(TAG, "Enum property not found: $instanceKey.$propertyName")
                        null
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyName")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyName", e)
                    null
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enum property: $instanceKey.$propertyName", e)
            null
        }
    }
    
    /**
     * 获取嵌套属性（使用路径）
     * 例如: "settings/theme/name"
     */
    fun getNestedNumberProperty(instanceKey: String, propertyPath: String): Float? {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getNumberProperty(propertyPath)
                    if (property != null) {
                        val value = property.value
                        Log.d(TAG, "Get nested number property: $instanceKey.$propertyPath = $value")
                        value
                    } else {
                        Log.w(TAG, "Nested number property not found: $instanceKey.$propertyPath")
                        null
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyPath")
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyPath", e)
                    null
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nested number property: $instanceKey.$propertyPath", e)
            null
        }
    }
    
    /**
     * 设置嵌套属性（使用路径）
     */
    fun setNestedNumberProperty(instanceKey: String, propertyPath: String, value: Float): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance != null) {
                try {
                    val property = instance.getNumberProperty(propertyPath)
                    if (property != null) {
                        property.value = value
                        Log.d(TAG, "Set nested number property: $instanceKey.$propertyPath = $value")
                        true
                    } else {
                        Log.w(TAG, "Nested number property not found: $instanceKey.$propertyPath")
                        false
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    Log.d(TAG, "Property not found (expected): $instanceKey.$propertyPath")
                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Unexpected error accessing property: $instanceKey.$propertyPath", e)
                    false
                }
            } else {
                Log.w(TAG, "ViewModel instance not found: $instanceKey")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting nested number property: $instanceKey.$propertyPath = $value", e)
            false
        }
    }
    
    /**
     * 获取可用的枚举值
     */
    fun getAvailableEnums(): List<Pair<String, List<String>>> {
        return try {
            val enums = riveFile?.enums ?: emptyList()
            enums.map { enum ->
                enum.name to enum.values
            }.also {
                Log.d(TAG, "Available enums: ${it.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available enums", e)
            emptyList()
        }
    }
    
    /**
     * 记录可用的 ViewModel 信息
     */
    private fun logAvailableViewModels() {
        try {
            val file = riveFile ?: return
            val viewModelCount = file.viewModelCount
            Log.i(TAG, "Available ViewModels count: $viewModelCount")
            
            for (i in 0 until viewModelCount) {
                val viewModel = file.getViewModelByIndex(i)
                if (viewModel != null) {
                    Log.i(TAG, "ViewModel[$i]: ${viewModel.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging available ViewModels", e)
        }
    }
    
    /**
     * 列出 ViewModel 实例中的所有可用属性
     * 这个方法通过尝试访问常见的属性名称来发现可用的属性
     */
    fun logAvailableProperties(instanceKey: String) {
        try {
            val instance = viewModelInstances[instanceKey] ?: return
            Log.i(TAG, "Checking available properties for instance: $instanceKey")
            
            // 常见的时间相关属性名称
            val commonTimeProperties = listOf(
                "timeHour", "hour", "hours", "h",
                "timeMinute", "minute", "minutes", "m", 
                "timeSecond", "second", "seconds", "s",
                "time", "currentTime"
            )
            
            // 常见的日期相关属性名称
            val commonDateProperties = listOf(
                "dateMonth", "month", "months", "mon",
                "dateDay", "day", "days", "d",
                "dateWeek", "week", "weeks", "weekday", "dayOfWeek", "w",
                "date", "currentDate", "year", "years"
            )
            
            // 常见的系统状态属性名称
            val commonSystemProperties = listOf(
                "systemStatusBattery", "battery", "batteryLevel", "power",
                "brightness", "volume", "temperature", "temp"
            )
            
            // 其他常见属性
            val commonOtherProperties = listOf(
                "x", "y", "z", "rotation", "scale", "alpha", "opacity",
                "visible", "enabled", "active", "selected",
                "color", "backgroundColor", "textColor",
                "width", "height", "size",
                "deviceKnob", "deviceKnobPage", "deviceKnobProgress", "deviceKnobPercent",
                "deviceKnobPageCount", "crownValue", "dial"
            )
            
            val allCommonProperties = commonTimeProperties + commonDateProperties + 
                                    commonSystemProperties + commonOtherProperties
            
            val foundProperties = mutableListOf<String>()
            
            // 检查数字属性
            for (propertyName in allCommonProperties) {
                try {
                    val property = instance.getNumberProperty(propertyName)
                    if (property != null) {
                        foundProperties.add("$propertyName (Number)")
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    // 属性不存在，继续检查下一个
                } catch (e: Exception) {
                    // 其他错误，记录但继续
                    Log.d(TAG, "Error checking property $propertyName: ${e.message}")
                }
            }
            
            // 检查字符串属性
            val commonStringProperties = listOf(
                "title", "text", "label", "name", "description",
                "status", "message", "content"
            )
            
            for (propertyName in commonStringProperties) {
                try {
                    val property = instance.getStringProperty(propertyName)
                    if (property != null) {
                        foundProperties.add("$propertyName (String)")
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    // 属性不存在，继续检查下一个
                } catch (e: Exception) {
                    Log.d(TAG, "Error checking string property $propertyName: ${e.message}")
                }
            }
            
            // 检查布尔属性
            val commonBooleanProperties = listOf(
                "visible", "enabled", "active", "selected", "checked",
                "isVisible", "isEnabled", "isActive", "isSelected"
            )
            
            for (propertyName in commonBooleanProperties) {
                try {
                    val property = instance.getBooleanProperty(propertyName)
                    if (property != null) {
                        foundProperties.add("$propertyName (Boolean)")
                    }
                } catch (e: app.rive.runtime.kotlin.core.errors.ViewModelException) {
                    // 属性不存在，继续检查下一个
                } catch (e: Exception) {
                    Log.d(TAG, "Error checking boolean property $propertyName: ${e.message}")
                }
            }
            
            if (foundProperties.isNotEmpty()) {
                Log.i(TAG, "Found ${foundProperties.size} properties in instance '$instanceKey':")
                foundProperties.forEach { property ->
                    Log.i(TAG, "  - $property")
                }
            } else {
                Log.w(TAG, "No common properties found in instance '$instanceKey'")
                Log.i(TAG, "This might mean the ViewModel uses custom property names")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging available properties for instance: $instanceKey", e)
        }
    }
    
    /**
     * 将ViewModel实例绑定到State Machine
     * 这是数据绑定生效的关键步骤
     */
    fun bindInstanceToStateMachine(instanceKey: String): Boolean {
        return try {
            val instance = viewModelInstances[instanceKey]
            if (instance == null) {
                Log.w(TAG, "ViewModel instance not found for binding: $instanceKey")
                return false
            }
            
            val controller = riveView.controller
            if (controller == null) {
                Log.w(TAG, "RiveView controller is null, cannot bind instance")
                return false
            }
            
            // 获取第一个State Machine进行绑定
            val stateMachine = controller.stateMachines.firstOrNull()
            if (stateMachine != null) {
                stateMachine.viewModelInstance = instance
                Log.i(TAG, "ViewModel instance bound to State Machine: $instanceKey")
                return true
            } else {
                // 如果没有State Machine，尝试绑定到Artboard
                val artboard = controller.activeArtboard
                if (artboard != null) {
                    artboard.viewModelInstance = instance
                    Log.i(TAG, "ViewModel instance bound to Artboard: $instanceKey")
                    return true
                } else {
                    Log.w(TAG, "No State Machine or Artboard available for binding")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding ViewModel instance to State Machine: $instanceKey", e)
            false
        }
    }
    
    /**
     * 自动绑定功能：获取默认ViewModel，创建默认实例，并绑定到State Machine
     * 这是官方推荐的自动绑定流程的实现
     */
    fun initializeWithAutoBind(): Boolean {
        return try {
            // 1. 初始化数据绑定
            initialize()
            
            // 2. 获取默认ViewModel
            val defaultViewModel = getDefaultViewModel()
            if (defaultViewModel == null) {
                Log.w(TAG, "No default ViewModel found for auto-binding")
                return false
            }
            
            // 3. 创建默认实例
            val instance = createDefaultInstance(defaultViewModel, "auto_default")
            if (instance == null) {
                Log.w(TAG, "Failed to create default instance for auto-binding")
                return false
            }
            
            // 4. 绑定实例到State Machine
            val bindingSuccess = bindInstanceToStateMachine("auto_default")
            if (bindingSuccess) {
                Log.i(TAG, "Auto-binding completed successfully")
                
                // 5. 发现可用属性
                logAvailableProperties("auto_default")
                
                // 6. 记录可用枚举
                val enums = getAvailableEnums()
                if (enums.isNotEmpty()) {
                    Log.i(TAG, "Available enums after auto-binding:")
                    enums.forEach { (name, values) ->
                        Log.i(TAG, "  $name: ${values.joinToString(", ")}")
                    }
                }
                
                return true
            } else {
                Log.w(TAG, "Auto-binding failed at binding step")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-binding", e)
            false
        }
    }
    
    /**
     * 获取自动绑定的实例key
     * 用于在自动绑定后访问属性
     */
    fun getAutoBoundInstanceKey(): String = "auto_default"
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            viewModelInstances.clear()
            viewModels.clear()
            riveFile = null
            Log.i(TAG, "Data binding resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up data binding resources", e)
        }
    }
} 
