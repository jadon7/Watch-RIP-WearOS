# Data Binding 自动绑定测试指南

## 最新实现概要

`RivePreviewActivity` 已全面改为官方推荐的绑定流程：

1. `RiveAnimationView` 在加载 `.riv` 文件后调用 `ensureViewModelBinding()`。
2. 如果 Rive 已自动绑定实例则直接复用，否则自动创建默认实例并绑定到第一个 State Machine。
3. `RivePlayerUI` 通过 `ViewModelInstance.setNumberProperty()` 写入属性，仅在实例缺失时才退回 `setNumberState()`。
4. `DisposableEffect` 在 Compose 退出时统一停止视图并清理实例，避免旧引用导致的“自动绑定失效”问题。

## 如何验证自动绑定是否生效

### 方法1：查看日志输出

运行应用后，使用以下命令查看日志：

```bash
adb logcat | grep "RiveDataBinding\|RivePlayerUI"
```

**成功的日志应该包含：**
```
RiveDataBinding: Auto-binding completed successfully
RiveDataBinding: ViewModel instance bound to State Machine: auto_default
RiveDataBinding: Found X properties in instance 'auto_default'
```

**失败的日志会显示：**
```
RiveDataBinding: Auto-binding failed, falling back to manual initialization
```

### 方法2：临时禁用传统状态机输入验证

要确认data binding真正生效，可以临时注释掉传统的状态机输入：

1. 打开 `RivePreviewActivity.kt`
2. 找到第589-611行的 `safeSetNumberState` 调用
3. 临时注释掉这些调用：

```kotlin
// 临时注释掉传统方式，测试data binding
/*
safeSetNumberState("timeHour", currentHour)
safeSetNumberState("timeMinute", currentMinute)
safeSetNumberState("timeSecond", currentSecond)
safeSetNumberState("systemStatusBattery", currentBattery)
safeSetNumberState("dateMonth", currentMonth)
safeSetNumberState("dateDay", currentDay)
safeSetNumberState("dateWeek", currentWeek)
*/
```

4. 重新编译运行
5. 如果时间和电量数据仍然更新，说明data binding生效
6. 如果动画停止更新，说明data binding未生效，需要进一步调试

### 方法3：确认绑定实例

在 `RivePreviewActivity.kt` 中，`ensureViewModelBinding()` 会在成功时输出：
```
RivePlayerUI: ViewModel instance ready for data binding
```
若需要进一步确认，可在 `AndroidView` 的 `factory` 中临时加入：
```kotlin
Log.i(
    "RivePlayerUI",
    "StateMachine binding: ${controller.stateMachines.firstOrNull()?.viewModelInstance != null}"
)
```

## 预期效果

### 成功情况
- 日志显示 "Auto-binding completed successfully"
- 即使注释掉传统状态机输入，动画仍能正常更新
- 可以看到详细的属性发现日志

### 失败情况
- 日志显示 "Auto-binding failed"
- 注释掉传统输入后动画停止更新
- 需要检查本地AAR的API兼容性

## 故障排除

如果自动绑定失败，可能的原因：

1. **Rive 文件未定义 ViewModel**：`defaultViewModelForArtboard` 返回 `null`，请让设计稿补齐 ViewModel。
2. **State Machine 为空**：若 `.riv` 没有 State Machine，只能把实例绑定到 Artboard，这样属性生效但无法驱动状态切换。
3. **旧版 runtime**：请确认 `app.rive:rive-android:10.4.5` 及以上版本，并重新同步依赖。
4. **属性名不匹配**：日志中若出现 `ViewModel property not found`，说明 `.riv` 中未暴露该字段，需要与设计师核对命名。

## 回退机制

若 `ensureViewModelBinding()` 返回 `null`，`RivePlayerUI` 会自动退回到 `setNumberState()` 的传统写法。
因此即使 ViewModel 缺失，动画也能继续运行，只是无法享受数据绑定带来的属性映射能力。

这样即使data binding有问题，也不会影响应用的基本功能。
