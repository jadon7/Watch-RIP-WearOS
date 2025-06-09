# Rive 数据绑定功能说明

本项目已集成 Rive 数据绑定功能，基于 [Rive 官方文档](https://rive.app/docs/runtimes/data-binding#android) 实现。

## 功能概述

数据绑定功能允许你在运行时动态地与 Rive 文件中的元素进行交互，包括：

- 设置和获取数字、字符串、布尔值属性
- 触发触发器事件
- 处理枚举属性
- 支持嵌套属性路径
- 管理多个 ViewModel 实例

## 核心组件

### 1. RiveDataBindingHelper

位置：`app/src/main/java/com/example/watchview/utils/RiveDataBindingHelper.kt`

这是主要的数据绑定辅助类，提供了完整的数据绑定 API。

### 2. RiveDataBindingUsageExample

位置：`app/src/main/java/com/example/watchview/utils/RiveDataBindingUsageExample.kt`

包含各种使用示例，展示如何使用数据绑定功能。

## 基本使用方法

### 1. 初始化数据绑定

```kotlin
val helper = RiveDataBindingHelper(riveView)
helper.initialize()
```

### 2. 获取 ViewModel

```kotlin
// 根据名称获取
val viewModel = helper.getViewModelByName("WatchFaceViewModel")

// 根据索引获取
val viewModel = helper.getViewModelByIndex(0)

// 获取默认 ViewModel
val viewModel = helper.getDefaultViewModel()
```

### 3. 创建 ViewModel 实例

```kotlin
// 创建默认实例
helper.createDefaultInstance(viewModel, "watchface")

// 根据名称创建实例
helper.createInstanceByName(viewModel, "Instance1", "instance1")

// 创建空白实例
helper.createBlankInstance(viewModel, "blank")
```

### 4. 设置和获取属性

```kotlin
// 数字属性
helper.setNumberProperty("watchface", "hour", 14.5f)
val hour = helper.getNumberProperty("watchface", "hour")

// 字符串属性
helper.setStringProperty("watchface", "title", "My Watch")
val title = helper.getStringProperty("watchface", "title")

// 布尔属性
helper.setBooleanProperty("watchface", "isVisible", true)
val isVisible = helper.getBooleanProperty("watchface", "isVisible")

// 枚举属性
helper.setEnumProperty("theme", "colorScheme", "dark")
val colorScheme = helper.getEnumProperty("theme", "colorScheme")
```

### 5. 触发器

```kotlin
helper.fireTrigger("watchface", "updateDisplay")
```

### 6. 嵌套属性

```kotlin
// 使用路径访问嵌套属性
helper.setNestedNumberProperty("settings", "display/brightness", 0.8f)
val brightness = helper.getNestedNumberProperty("settings", "display/brightness")
```

### 7. 清理资源

```kotlin
helper.cleanup()
```

## 在项目中的集成

### RivePreviewActivity 集成

在 `RivePreviewActivity.kt` 中，数据绑定功能已经集成到 `RivePlayerUI` 组件中：

1. **初始化**：当 `RiveAnimationView` 创建时，自动初始化数据绑定
2. **默认实例**：自动创建默认 ViewModel 实例
3. **属性更新**：同时使用传统状态机输入和数据绑定方式更新属性
4. **资源清理**：在 Activity 销毁时自动清理数据绑定资源

### 日志输出

数据绑定操作会产生详细的日志输出，标签为 `RiveDataBinding`，包括：

- ViewModel 发现和创建
- 实例创建状态
- 属性设置和获取操作
- 错误信息和警告
- 可用枚举列表

## 错误处理

数据绑定功能包含完善的错误处理机制：

1. **空值检查**：所有操作都进行空值检查
2. **异常捕获**：捕获并记录所有异常
3. **优雅降级**：当数据绑定操作失败时，不影响传统状态机输入
4. **详细日志**：提供详细的错误信息用于调试
5. **ViewModelException 处理**：优雅地处理属性不存在的情况，避免应用崩溃

### 属性不存在的处理

当尝试访问不存在的属性时（如 `dateWeek`），系统会：

- 捕获 `ViewModelException` 异常
- 记录调试级别的日志（不是错误）
- 返回 `false`（设置操作）或 `null`（获取操作）
- 继续正常运行，不影响其他功能

### 属性发现功能

新增了 `logAvailableProperties()` 方法，可以自动发现 ViewModel 实例中的可用属性：

```kotlin
helper.logAvailableProperties("default")
```

该方法会检查常见的属性名称，包括：

- **时间属性**：`timeHour`, `hour`, `hours`, `timeMinute`, `minute`, `timeSecond`, `second` 等
- **日期属性**：`dateMonth`, `month`, `dateDay`, `day`, `dateWeek`, `week`, `dayOfWeek` 等  
- **系统属性**：`systemStatusBattery`, `battery`, `batteryLevel`, `brightness`, `volume` 等
- **其他属性**：`x`, `y`, `rotation`, `scale`, `visible`, `enabled`, `color` 等

发现的属性会在日志中显示，格式如：
```
Found 5 properties in instance 'default':
  - timeHour (Number)
  - battery (Number)
  - visible (Boolean)
  - title (String)
  - updateDisplay (Trigger)
```

## 兼容性说明

### 与传统状态机输入的兼容性

当前实现同时支持：
- 传统的 `setNumberState()` 方式
- 新的数据绑定方式

这确保了向后兼容性，即使数据绑定功能不可用，应用仍能正常工作。

### 本地 AAR 兼容性

由于项目使用本地 `kotlin-release.aar`，某些 API 可能与官方版本有差异：

1. **方法名称**：某些方法名可能不同（如 `trigger()` vs `fire()`）
2. **API 可用性**：某些功能可能不可用
3. **错误处理**：代码包含多种备选方案以处理 API 差异

## 调试建议

### 查看日志

使用以下过滤器查看数据绑定相关日志：

```bash
adb logcat | grep "RiveDataBinding\|RivePlayerUI"
```

### 发现可用属性

如果不确定 Rive 文件中有哪些属性可用，可以查看应用启动时的日志输出。数据绑定会自动检查常见属性并记录发现的属性列表。

### 常见问题

1. **ViewModel 未找到**
   - 检查 Rive 文件是否包含 ViewModel
   - 确认 ViewModel 名称拼写正确

2. **属性设置失败**
   - 检查属性名称是否正确
   - 确认 ViewModel 实例已正确创建
   - 查看日志中的详细错误信息
   - 使用属性发现功能确认可用的属性名称

3. **触发器不工作**
   - 确认触发器名称正确
   - 检查本地 AAR 是否支持触发器功能

4. **属性不存在错误**
   - 这是正常情况，不会影响应用运行
   - 系统会自动跳过不存在的属性
   - 查看日志中的属性发现结果，使用正确的属性名称

### 日志级别说明

- **INFO**：重要的初始化和发现信息
- **DEBUG**：详细的操作记录和属性访问
- **WARN**：非致命问题，如属性不存在
- **ERROR**：严重错误，可能影响功能

## 示例代码

详细的使用示例请参考 `RiveDataBindingUsageExample.kt` 文件，包含：

- 基础使用示例
- 嵌套属性示例
- 枚举使用示例
- 多实例管理示例
- 实时数据更新示例
- 错误处理示例

## 未来扩展

可以考虑的功能扩展：

1. **自动绑定**：实现类似官方文档中的 `autoBind` 功能
2. **观察者模式**：监听属性变化
3. **批量操作**：批量设置多个属性
4. **配置文件**：通过配置文件定义绑定关系
5. **性能优化**：缓存和优化频繁访问的属性

## 参考资料

- [Rive 官方数据绑定文档](https://rive.app/docs/runtimes/data-binding#android)
- [Rive Android Runtime 文档](https://rive.app/docs/runtimes/android)
- [项目中的实现示例](app/src/main/java/com/example/watchview/utils/RiveDataBindingUsageExample.kt) 