# Rive 时间数据绑定开发指南

本文档提供项目内 **Rive 数据绑定** 的完整实现示例，帮助开发者在其它页面快速复用“初始化 + 时间数据写入”的流程。示例代码均来自 `app/src/main/java/com/example/watchview`。

## 1. 前置条件与依赖
- Rive 文件：默认 ViewModel 需定义下列数值属性，以便代码直接写入：
  `timeHour`、`timeMinute`、`timeSecond`、`dateMonth`、`dateDay`、`dateWeek`、`systemStatusBattery`
- State Machine：动画至少要有一个 State Machine，或保证 Artboard 支持 ViewModelInstance 绑定。
- 依赖：`app/build.gradle.kts` 已引入 `app.rive:rive-android:10.4.4` 与 `ReLinker`，无需额外配置。

---

## 2. Compose 层完整示例（`RivePlayerUI`）
下方代码直接摘自 `presentation/RivePreviewActivity.kt:500` 起，演示如何：
1. 定义时间、电量等状态。
2. 使用协程每秒刷新数据。
3. 创建 `RiveAnimationView` 并自动绑定 ViewModel。
4. 在 `update` 回调里通过数据绑定写入属性。

```kotlin
@Composable
fun RivePlayerUI(
    file: File,
    batteryLevel: Float,
    onRiveViewCreated: (RiveAnimationView) -> Unit,
    eventListener: RiveFileController.RiveEventListener
) {
    // 1. 维护最新时间/电量状态
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toFloat()) }
    var currentMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE).toFloat()) }
    var currentSecond by remember { mutableStateOf(0f) }
    var currentBattery by remember { mutableStateOf(batteryLevel) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1f) }
    var currentDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toFloat()) }
    var currentWeek by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1f) }

    // 2. 保存数据绑定 Helper 状态
    var dataBindingHelper by remember { mutableStateOf<RiveDataBindingHelper?>(null) }
    var isDataBindingInitialized by remember { mutableStateOf(false) }

    // 3. 协程每秒刷新时间、电量
    LaunchedEffect(Unit) {
        while (isActive) {
            val calendar = Calendar.getInstance()
            currentHour = (calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f).round(1)
            currentMinute = (calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60f).round(1)
            currentSecond = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f).round(2)
            currentMonth = (calendar.get(Calendar.MONTH) + 1).toFloat()
            currentDay = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
            currentWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 1).toFloat()
            currentBattery = batteryLevel
            delay(1000L) // 每秒刷新一次
        }
    }

    AndroidView(
        factory = { context ->
            RiveAnimationView(context).apply {
                val riveFile = RiveCoreFile(file.readBytes())
                setRiveFile(riveFile)
                autoplay = true
                addEventListener(eventListener)

                // 4. 初始化数据绑定（自动绑定 + 兜底）
                val helper = RiveDataBindingHelper(this)
                val autoBindSuccess = helper.initializeWithAutoBind()
                if (autoBindSuccess) {
                    dataBindingHelper = helper
                    isDataBindingInitialized = true
                    Log.i("RivePlayerUI", "Auto-binding completed successfully")
                } else {
                    Log.w("RivePlayerUI", "Auto-binding failed, falling back to manual initialization")
                    helper.initialize()
                    helper.getDefaultViewModel()?.let {
                        helper.createDefaultInstance(it, "default")
                    }
                    dataBindingHelper = helper
                    isDataBindingInitialized = false
                }

                onRiveViewCreated(this)
            }
        },
        update = { riveView ->
            val artboard = riveView.file?.firstArtboard
            val helper = dataBindingHelper

            // 5a. 兼容传统 State Machine 输入
            artboard?.stateMachineNames?.firstOrNull()?.let { machine ->
                fun setNumber(inputName: String, value: Float) {
                    artboard.stateMachine(machine)?.inputs?.firstOrNull { it.name == inputName } ?: return
                    riveView.setNumberState(machine, inputName, value)
                }
                setNumber("timeHour", currentHour)
                setNumber("timeMinute", currentMinute)
                setNumber("timeSecond", currentSecond)
                setNumber("systemStatusBattery", currentBattery)
                setNumber("dateMonth", currentMonth)
                setNumber("dateDay", currentDay)
                setNumber("dateWeek", currentWeek)
            }

            // 5b. 使用数据绑定写入
            if (helper != null) {
                val instanceKey = if (isDataBindingInitialized) {
                    helper.getAutoBoundInstanceKey() // "auto_default"
                } else {
                    "default" // 手动兜底实例
                }
                helper.setNumberProperty(instanceKey, "timeHour", currentHour)
                helper.setNumberProperty(instanceKey, "timeMinute", currentMinute)
                helper.setNumberProperty(instanceKey, "timeSecond", currentSecond)
                helper.setNumberProperty(instanceKey, "systemStatusBattery", currentBattery)
                helper.setNumberProperty(instanceKey, "dateMonth", currentMonth)
                helper.setNumberProperty(instanceKey, "dateDay", currentDay)
                helper.setNumberProperty(instanceKey, "dateWeek", currentWeek)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

> `round()` 为同文件内的扩展函数，负责控制小数位。

---

## 3. `RiveDataBindingHelper` 关键接口示例
`utils/RiveDataBindingHelper.kt` 封装了 ViewModel/Instance 操作。以下片段展示最常用的 API：

```kotlin
class RiveDataBindingHelper(private val riveView: RiveAnimationView) {
    private val viewModelInstances: MutableMap<String, ViewModelInstance> = mutableMapOf()

    fun initializeWithAutoBind(): Boolean {
        initialize()
        val defaultViewModel = getDefaultViewModel() ?: return false
        val instance = createDefaultInstance(defaultViewModel, "auto_default") ?: return false
        if (!bindInstanceToStateMachine("auto_default")) return false
        logAvailableProperties("auto_default")
        return true
    }

    fun setNumberProperty(instanceKey: String, propertyName: String, value: Float): Boolean {
        val instance = viewModelInstances[instanceKey] ?: return false
        val property = instance.getNumberProperty(propertyName) ?: return false
        property.value = value
        Log.d(TAG, "Set number property: $instanceKey.$propertyName = $value")
        return true
    }

    fun getAutoBoundInstanceKey(): String = "auto_default"
}
```

常见的异常都会在内部捕获并输出到 `Logcat`，因此调用端只需关注返回值即可。

---

## 4. 最小可复用模板
若在新模块中接入 Rive 时间数据，可按下列“最小模板”编写：

```kotlin
val riveView = RiveAnimationView(context).apply {
    setRiveFile(RiveCoreFile(riveFileBytes))
}

val bindingHelper = RiveDataBindingHelper(riveView)
val instanceKey = if (bindingHelper.initializeWithAutoBind()) {
    bindingHelper.getAutoBoundInstanceKey() // "auto_default"
} else {
    bindingHelper.initialize()
    bindingHelper.getDefaultViewModel()?.let {
        bindingHelper.createDefaultInstance(it, "default")
    }
    "default"
}

// 定时任务 / 协程内写入属性
timerScope.launch {
    while (isActive) {
        val now = Calendar.getInstance()
        val hour = (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f)
        val minute = (now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f)
        val second = (now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f)

        bindingHelper.setNumberProperty(instanceKey, "timeHour", hour)
        bindingHelper.setNumberProperty(instanceKey, "timeMinute", minute)
        bindingHelper.setNumberProperty(instanceKey, "timeSecond", second)
        delay(1000)
    }
}
```

将以上模板嵌入任意 Activity/Fragment/Compose 组件即可完成时间数据注入。

---

## 5. 触发器与其他属性示例
项目还提供了更多类型的属性操作，可在 `RiveDataBindingUsageExample.kt` 中查看：

```kotlin
val viewModel = helper.getViewModelByName("ClockViewModel")
if (viewModel != null) {
    helper.createDefaultInstance(viewModel, "clock")
    helper.setNumberProperty("clock", "hours", 12.5f)
    helper.setNumberProperty("clock", "minutes", 30f)
    helper.fireTrigger("clock", "updateTime") // 触发 Rive trigger
}
```

利用这些 API，可在同一实例上写入字符串、布尔、枚举或嵌套属性（`setStringProperty`、`setBooleanProperty`、`setEnumProperty`、`setNestedNumberProperty`）。

---

## 6. 调试与排查
- `adb logcat | grep "RiveDataBinding\|RivePlayerUI"` 可查看自动绑定、属性扫描、写入失败等信息。
- 若看到 `Number property not found`，请检查 Rive 文件内是否定义了对应属性名。
- 若出现 `No State Machine or Artboard available for binding`，需在 Rive 编辑器中补充 State Machine。
- 生命周期结束时别忘记调用 `bindingHelper.cleanup()` 清理缓存实例。

通过以上代码示例，开发者可以快速在新的 Rive 动画场景中接入时间数据绑定，同时保留自动绑定与降级策略。EOF
