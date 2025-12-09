# 手表实体按键拦截与自定义事件说明

> 适用场景：Android Wear OS 定制固件，允许应用通过设置 window flag 拦截物理按键并屏蔽系统默认行为（参考《手表按键定义及接入说明》）。下文基于项目 `watch-view` 的实现，路径 `app/src/main/java/com/example/watchview/presentation/RivePreviewActivity.kt`。

## 1. 核心封装

- `WatchKeyController`（`app/src/main/java/com/example/watchview/utils/WatchKeyController.kt`）负责将厂商约定的 flag 写入 window title（`miwear_<flags>`）。
- Activity 中通过 `watchKeyController.applyFlags(flags)` / `clearFlags()` 控制当前窗口的按键行为。
- 表冠/电源键事件通过 `onKeyDown`/`onKeyUp` 拦截后，分别在 `StemKeyDispatcher` 与 `PowerKeyDispatcher` 中转换成 app 内部的 Flow 事件（`MutableSharedFlow`），进而驱动 Databinding Trigger。

## 2. 表冠（KEYCODE_STEM_PRIMARY）

### 2.1 屏蔽系统交互
- 在 Activity 设置 window flag：
  ```kotlin
  private const val STEM_KEY_FLAGS =
      WatchKeyController.FLAG_CONVERT_STEM_TO_FX or
      WatchKeyController.FLAG_CONVERT_STEM_TO_F1_ONLY
  ```
- 在 `onResume()` 调用 `watchKeyController.applyFlags(STEM_KEY_FLAGS or POWER_KEY_FLAGS)`，`onPause()` 调用 `clearFlags()`。
- 在 `onKeyDown/onKeyUp` 中判断 `keyCode == KEYCODE_STEM_PRIMARY`（以及系统转换的 F1/F2）并 `return true`，系统默认（返回表盘 / 多任务）的操作就会被屏蔽。

### 2.2 自定义事件
- `StemKeyDispatcher` 在 `onKeyUp` 回调 `emitCrownTrigger()`，后者向 `crownTriggerFlow` 发事件。
- `RivePlayerUI` 中 `LaunchedEffect(crownTriggerFlow)` 调用 `runtimeSession.fireTrigger("keyCrown")`。若 Rive 文件存在 ViewModel trigger，则使用数据绑定；否则回退到 state machine trigger。
- 日志可通过 `adb logcat | grep RiveBinding` 观察 `Emitting keyCrown trigger...`、`Trigger keyCrown fired via ViewModel` 等信息。

## 3. 电源键（KEYCODE_POWER）

### 3.1 屏蔽系统交互
- 按厂商文档仅设置 `FLAG_USE_POWER_KEY`：
  ```kotlin
  private const val POWER_KEY_FLAGS = WatchKeyController.FLAG_USE_POWER_KEY
  ```
- 同样在 `onResume()` 调用 `applyFlags(STEM_KEY_FLAGS or POWER_KEY_FLAGS)`。
- 在 `onKeyDown/keyCode == KEYCODE_POWER` 时 `return true` 并调用 `PowerKeyDispatcher.onKeyDown()`；系统默认的控制中心/紧急呼叫将不会触发。

### 3.2 自定义事件
- `PowerKeyDispatcher` 在 `onKeyUp` 调用 `emitPowerTrigger()`，向 `powerTriggerFlow` 发事件。
- `RivePlayerUI` 的 `LaunchedEffect(powerTriggerFlow)` 调用 `runtimeSession.fireTrigger("keyPower")`，触发 Databinding。若 ViewModel trigger 不存在，则回退到 state machine 的同名 trigger。

## 4. Replay/重新播放的特别处理
- “重新播放”按钮在 `RivePreviewActivity` 中调用 `riveView.reset()/play()` 后，会向 `replayTriggerFlow` 发信号。
- `RivePlayerUI` 监听该 Flow，通过 `reloadToken` + `key(reloadToken)` 重建 `AndroidView`，完全复刻初次加载的流程，确保 Databinding 重建并继续响应实体键事件。

## 5. 调试建议
1. 运行 `adb logcat | grep RiveBinding`，表冠/电源键分别会打印 `Stem key ...`、`Power key ...`、`Emitting keyCrown/keyPower ...` 的日志。
2. 如果按键仍触发系统默认行为或没有日志，检查：
   - APK 是否来自带有以上逻辑的构建（当前代码在 main）。
   - window flag 是否写入成功（`adb shell dumpsys window | grep miwear_`).
   - 系统是否已将应用加入 “可拦截实体键” 白名单。
3. Rive 文件需包含 `keyCrown`、`keyPower` trigger（ViewModel 或 state machine）。否则触发会在日志中提示 `Pending trigger ... not found`。

## 6. 关键文件
- `app/src/main/java/com/example/watchview/presentation/RivePreviewActivity.kt`
- `app/src/main/java/com/example/watchview/utils/WatchKeyController.kt`
- `docs/手表按键定义及接入说明.{pdf,docx}`（厂商文档）

通过上述流程，可以在手表上屏蔽系统对表冠/电源键的默认交互，并在应用层注入任意自定义事件（如触发 Rive Databinding、执行逻辑等）。
