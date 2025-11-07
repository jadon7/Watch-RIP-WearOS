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

    // 2. 记录自动绑定得到的 ViewModelInstance
    var boundViewModelInstance by remember { mutableStateOf<ViewModelInstance?>(null) }
    val missingViewModelProperties = remember { mutableSetOf<String>() }

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
            delay(1000L) // 每秒刷新一次
        }
    }

    LaunchedEffect(batteryLevel) {
        currentBattery = batteryLevel
    }

    AndroidView(
        factory = { context ->
            RiveAnimationView(context).apply {
                val riveFile = RiveCoreFile(file.readBytes())
                setRiveFile(riveFile)
                autoplay = true
                addEventListener(eventListener)

                // 4. 绑定 ViewModelInstance（若 Rive 已自动绑定则直接复用）
                boundViewModelInstance = ensureViewModelBinding()
                if (boundViewModelInstance != null) {
                    Log.i("RivePlayerUI", "ViewModel instance ready for data binding")
                } else {
                    Log.w("RivePlayerUI", "No ViewModel instance bound; falling back to state-machine inputs")
                }

                onRiveViewCreated(this)
            }
        },
        update = { riveView ->
            val artboard = riveView.file?.firstArtboard
            val viewModelInstance = boundViewModelInstance

            // 5a. 若仍未绑定到 ViewModel，则兼容传统 State Machine 输入
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

            // 5b. 若绑定成功，则直接通过 ViewModelInstance 写入属性
            if (viewModelInstance != null) {
                viewModelInstance.setNumberProperty("timeHour", currentHour, missingViewModelProperties)
                viewModelInstance.setNumberProperty("timeMinute", currentMinute, missingViewModelProperties)
                viewModelInstance.setNumberProperty("timeSecond", currentSecond, missingViewModelProperties)
                viewModelInstance.setNumberProperty("systemStatusBattery", currentBattery, missingViewModelProperties)
                viewModelInstance.setNumberProperty("dateMonth", currentMonth, missingViewModelProperties)
                viewModelInstance.setNumberProperty("dateDay", currentDay, missingViewModelProperties)
                viewModelInstance.setNumberProperty("dateWeek", currentWeek, missingViewModelProperties)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

> `round()` 为同文件内的扩展函数，负责控制小数位。

---

## 3. 核心扩展函数

### `RiveAnimationView.ensureViewModelBinding()`
封装在 `RivePreviewActivity.kt` 末尾，用于：

1. 若 `stateMachines.firstOrNull()?.viewModelInstance` 已存在，则直接复用；
2. 否则获取当前 artboard 的默认 ViewModel，创建实例；
3. 优先绑定到第一个 State Machine，若不存在则退回绑定到 artboard；
4. 返回绑定后的 `ViewModelInstance`，供上层状态持有。

### `ViewModelInstance.setNumberProperty(...)`
同样位于文件底部，负责安全写入数值属性并记录缺失字段。避免了重复 try/catch，也能在属性名拼写错误时只打印一次日志。

---

## 4. 最小可复用模板
若在新模块中接入 Rive 时间数据，可按下列“最小模板”编写：

```kotlin
val riveView = RiveAnimationView(context).apply {
    setRiveFile(RiveCoreFile(riveFileBytes))
}

val viewModelInstance = riveView.ensureViewModelBinding()
if (viewModelInstance == null) {
    // 若 Rive 文件没有暴露 ViewModel，则继续使用传统 state machine 输入
    riveView.setNumberState("State Machine 1", "timeHour", hour)
} else {
    viewModelInstance.setNumberProperty("timeHour", hour, missingProperties)
    viewModelInstance.setNumberProperty("timeMinute", minute, missingProperties)
    viewModelInstance.setNumberProperty("timeSecond", second, missingProperties)
}
```

> `missingProperties` 为一个 `mutableSetOf<String>()`，用于避免重复打印缺失字段的日志。

---

## 5. 触发器与其他属性示例
`ViewModelInstance` 同样提供字符串、布尔、枚举、触发器等访问器：

```kotlin
val vmInstance = riveView.ensureViewModelBinding() ?: return
vmInstance.getBooleanProperty("watchAlarm")?.value = true
vmInstance.getStringProperty("city")?.value = "Shenzhen"
vmInstance.getTriggerProperty("updateDisplay")?.trigger()
```

如需访问嵌套属性，可直接使用 `getNumberProperty("settings/display/brightness")`。

---

## 6. 调试与排查
- `adb logcat | grep "RivePlayerUI"`：查看绑定状态、缺失属性或降级信息。
- **属性缺失**：日志若提示 `ViewModel property not found`，请在 `.riv` 文件中补充对应字段。
- **绑定失败**：若 `ensureViewModelBinding()` 返回 `null`，说明 Rive 文件没有默认 ViewModel，可联系设计师添加，或继续依赖 state machine 输入。
- **资源释放**：`DisposableEffect` 已统一调用 `view.stop()`，不再需要手动 `cleanup()`。

按照该模板即可在任意页面快速复用“时间 + 数据绑定”的写法，同时自动享受官方推荐的降级与性能优化策略。
