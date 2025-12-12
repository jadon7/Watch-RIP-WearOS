# Rive 时间数据绑定开发指南

本文档提供项目内 **Rive 数据绑定** 的完整实现示例，帮助开发者在其它页面快速复用“初始化 + 时间数据写入”的流程。示例代码均来自 `app/src/main/java/com/example/watchview`。

## 1. 前置条件与依赖
- Rive 文件：默认 ViewModel 需定义下列数值属性，以便代码直接写入：
  `timeHour`、`timeMinute`、`timeSecond`、`dateMonth`、`dateDay`、`dateWeek`、`systemStatusBattery`
- State Machine：动画至少要有一个 State Machine，或保证 Artboard 支持 ViewModelInstance 绑定。
- 依赖：`app/build.gradle.kts` 已引入 `app.rive:rive-android:10.5.2` 与 `ReLinker`，无需额外配置。
- 配置入口：`RivePreviewActivity` 可通过 Intent 传入 `EXTRA_RIVE_VIEWMODEL_NAME`、`EXTRA_RIVE_INSTANCE_NAME`、`EXTRA_RIVE_BINDING_MODE`（`viewmodel_only` / `state_machine_only` / `hybrid`），Compose 层会将其封装成 `RiveBindingConfig`。

---

## 2. Compose 层完整示例（`RivePlayerUI`）
`RiveRuntimeSession` 负责 ViewModel 绑定、输入缓存、降级策略与监听器。简化示例如下：

```kotlin
@Composable
fun RivePlayerUI(
    file: File,
    batteryLevel: Float,
    bindingConfig: RiveBindingConfig,
    onRiveViewCreated: (RiveAnimationView) -> Unit,
    eventListener: RiveFileController.RiveEventListener
) {
    // 1. 维护时间、电量等状态
    var currentHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toFloat()) }
    var currentMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE).toFloat()) }
    var currentSecond by remember { mutableStateOf(0f) }
    var currentBattery by remember { mutableStateOf(batteryLevel) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1f) }
    var currentDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toFloat()) }
    var currentWeek by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1f) }

    // 2. 为当前文件创建 session，并注册监听器
    val session = remember(file.absolutePath, bindingConfig) {
        RiveRuntimeSession(file.absolutePath, bindingConfig).apply {
            registerObserver("systemStatusBattery") { value ->
                Log.d("RiveBindingListener", "battery -> $value")
            }
        }
    }
    DisposableEffect(session) {
        onDispose { session.dispose() }
    }

    // 3. 协程刷新时间、电量
    LaunchedEffect(Unit) {
        while (isActive) {
            val calendar = Calendar.getInstance()
            currentHour = (calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f).round(1)
            currentMinute = (calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60f).round(1)
            currentSecond = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f).round(2)
            currentMonth = (calendar.get(Calendar.MONTH) + 1).toFloat()
            currentDay = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
            currentWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 1).toFloat()
            delay(1000L)
        }
    }
    LaunchedEffect(batteryLevel) {
        currentBattery = batteryLevel
    }

    // 4. 挂载 Rive 视图并让 session 处理绑定/写入
    AndroidView(
        factory = { context ->
            RiveAnimationView(context).apply {
                val riveFile = RiveCoreFile(file.readBytes())
                setRiveFile(riveFile)
                autoplay = true
                addEventListener(eventListener)
                session.attachView(this)
                session.bindViewModelIfNeeded(this)
                onRiveViewCreated(this)
            }
        },
        update = { riveView ->
            val snapshot = RiveSnapshot(
                hour = currentHour,
                minute = currentMinute,
                second = currentSecond,
                battery = currentBattery,
                month = currentMonth,
                day = currentDay,
                week = currentWeek
            )
            session.applySnapshot(riveView, snapshot)
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

> `round()` 为同文件内的扩展函数，负责控制小数位。

---

## 3. 核心扩展与 Session API

### `RiveRuntimeSession.bindViewModelIfNeeded()`
- 根据 `RiveBindingConfig` 选择指定 ViewModel/实例；
- 如配置为 `state_machine_only`，直接跳过绑定；
- 如果当前 controller 已绑定实例且配置未指定名称，则复用；
- 创建实例后会广播到所有 state machine，并记录日志标签 `RiveBindingSession`。

### `RiveRuntimeSession.applySnapshot()`
- 接收最新 `RiveSnapshot`（时间、电量、日期）；
- 优先写入 ViewModel（数字、布尔、字符串、触发器），并通过 `markPropertyAvailable/markPropertyMissing` 管控降级；
- 根据绑定模式，决定是否把指定字段同步到所有播放中的 state machine。

### `ViewModelInstance` 扩展函数
- `setNumberProperty` / `setBooleanProperty` / `setStringProperty`：安全写入属性并自动记录日志、监听事件；
- `triggerProperty`：封装触发器调用；
- 所有 helper 均由 session 捕获异常、打印缺失字段，只在值真的变化时触发写入。

---

## 4. 最小可复用模板
若在新模块中接入 Rive 时间数据，可按下列“最小模板”编写：

```kotlin
val session = remember(file.absolutePath, bindingConfig) {
    RiveRuntimeSession(file.absolutePath, bindingConfig)
}
val riveView = RiveAnimationView(context).apply {
    setRiveFile(RiveCoreFile(riveFileBytes))
    session.attachView(this)
    session.bindViewModelIfNeeded(this)
}

val viewModelInstance = session.viewModelInstance
if (viewModelInstance == null) {
    // 若 Rive 文件没有 ViewModel，可继续依赖 state machine 输入
    riveView.setNumberState("State Machine 1", "timeHour", hour)
} else {
    viewModelInstance.setNumberProperty("timeHour", hour, session)
    viewModelInstance.setNumberProperty("timeMinute", minute, session)
    viewModelInstance.setNumberProperty("timeSecond", second, session)
}
```

> `missingProperties` 为一个 `mutableSetOf<String>()`，用于避免重复打印缺失字段的日志。

---

## 5. 触发器与其他属性示例
`ViewModelInstance` 同样提供字符串、布尔、枚举、触发器等访问器：

```kotlin
val vmInstance = session.viewModelInstance ?: return
vmInstance.getBooleanProperty("watchAlarm")?.value = true
vmInstance.getStringProperty("city")?.value = "Shenzhen"
vmInstance.getTriggerProperty("updateDisplay")?.trigger()
```

如需访问嵌套属性，可直接使用 `getNumberProperty("settings/display/brightness")`。

---

## 6. 调试与排查
- `adb logcat | grep "RivePlayerUI"`：查看绑定状态、缺失属性或降级信息。
- **属性缺失**：日志若提示 `ViewModel property not found`，请在 `.riv` 文件中补充对应字段。
- **绑定失败**：若 `session.viewModelInstance` 始终为 `null`，说明 `.riv` 没有暴露默认 ViewModel，可联系设计师添加，或继续依赖 state machine 输入。
- **资源释放**：`DisposableEffect` 已统一调用 `view.stop()`，不再需要手动 `cleanup()`。

按照该模板即可在任意页面快速复用“时间 + 数据绑定”的写法，同时自动享受官方推荐的降级与性能优化策略。
