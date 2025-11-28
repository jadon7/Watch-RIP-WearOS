# Rive 数据绑定优化需求

## 背景
当前 Rive 预览的数据绑定实现只覆盖最基础的数据写入流程：加载 `.riv`、绑定默认 ViewModel、循环写入时间/电量、失败时回退到 State Machine 输入。对照 Rive 官方数据绑定生命周期（选择 ViewModel → 创建实例 → 绑定 → 多类型属性读写/监听 → 支持多状态机、多实例），仍有明显差距。为支撑更复杂的设计稿与调试场景，需要扩展现有能力并消除状态共享问题。

## 目标总览
1. **多实例/多状态机同步**：允许针对所有活跃 State Machine/实例推送时间、电量等数据，并支持手动选择 ViewModel 或实例。
2. **拓展属性类型与监听**：除数值外，提供布尔、字符串、触发器、枚举等属性的便捷访问器，并允许订阅属性值变化。
3. **基于文件的绑定重置**：在切换不同 `.riv` 时，能够重新绑定 ViewModel/状态缓存，避免复用上一份文件的数据上下文。
4. **属性级混合回退**：当部分字段在 ViewModel 中缺失时，仅对缺失字段保持对 State Machine 输入的写入，兼容旧稿件。

---

## 1. 多实例/多状态机同步
- **背景**：`RivePlayerUI` 仅把数据写入第一个包含该输入的状态机，且 `ensureViewModelBinding()` 只能绑定默认实例。
- **需求**：
  - 允许通过 Intent/设置选择 ViewModel/实例，至少支持“默认/按名称”两种策略。
    - 建议新增 Intent key：`EXTRA_RIVE_VIEWMODEL_NAME`、`EXTRA_RIVE_INSTANCE_NAME`，或在设置页提供同名开关；若未传入则回退至默认实例。
  - 缓存 `StateMachine.inputs` 或使用 `playingStateMachines`，确保所有包含指定输入的实例都会同步数值。
  - 当用户切换目标 state machine 或实例时，可实时生效且无需重启 Activity。
- **验收标准**：
  - 多个 state machine 含 `timeHour` 时，日志显示全部实例收到更新，无遗漏。
  - 在 UI 或配置中切换 ViewModel 名称后，`ensureViewModelBinding()` 会重新绑定并写入新实例，旧实例释放。

## 2. 拓展属性类型与监听
- **背景**：目前只实现 `setNumberProperty()`，无法触发布尔/字符串/触发器属性，也无法响应事件。
- **需求**：
  - 提供 `setBooleanProperty`、`setStringProperty`、`triggerProperty` 等 helper，并允许访问嵌套路径。
  - 支持对关键属性注册 `BindToValueChange`（或 Android 等价 API）以调试/展示实时值。
    - 监听接口需支持生命周期感知（如在 `DisposableEffect` 内注册/注销），避免 Activity 销毁后仍触发回调。
  - 在日志中区分写入/读取/监听事件，便于排查。
- **验收标准**：
  - 新增单元或手动测试：调用布尔/字符串 setter 后，Rive 动画在设计稿中做出响应。
  - 可以订阅某个属性的变更并在日志看到回调。

## 3. 基于文件的绑定重置
- **背景**：`remember { ... }` 缓存的 `ViewModelInstance`、`missingProperties` 在文件切换时不会清除，导致复用旧实例。
- **需求**：
  - 以 `remember(file.absolutePath)` 或 `DisposableEffect(file)` 为键，在新文件加载时强制清空状态并重新执行 `ensureViewModelBinding()`。
  - `missingViewModelProperties`、`lastSnapshot`、`riveViewRef` 等也应按文件隔离。
    - 建议封装成 `RiveRuntimeSession` 数据结构，统一管理：实例引用、输入缓存、日志计数、监听者等，生命周期与 `file.absolutePath` 绑定。
- **验收标准**：
  - 连续预览不同 `.riv` 时，不会出现“ViewModel property not found”延续到下一份的日志。
  - 切换文件后可立即写入新文件定义的属性，旧 ViewModel 不再接收更新。

## 4. 属性级混合回退
- **背景**：一旦 `boundViewModelInstance` 存在，现逻辑完全跳过 `setNumberState()`，即便部分字段在 ViewModel 中缺失。
- **需求**：
  - 根据 `missingViewModelProperties` 或 `ViewModelInstance` 响应情况，针对缺失字段继续调用 state machine 输入更新。
  - 提供可选策略：全部走 ViewModel、全部走 state machine、按字段混合，以适应不同 Rive 文件。
    - 新增配置入口（Intent key：`EXTRA_RIVE_BINDING_MODE`，可选值 `viewmodel_only`、`state_machine_only`、`hybrid`；设置页保持同样枚举）。
- **验收标准**：
  - 当某个属性在 ViewModel 中不存在，但在 state machine 输入中存在时，动画仍能收到更新且日志明确写入路径。
  - 策略切换可通过设置或 Intent 控制，并在日志中说明当前模式。

---

## 数据结构与缓存策略
- 引入 `RiveRuntimeSession`（或等价对象）记录：
  - 当前文件路径、所选 ViewModel 名称/实例名称、绑定模式；
  - 缓存的 `StateMachineInput` 映射（按 state machine 名称 + input 名称）；
  - 活跃的 ViewModel 实例引用、监听回调列表、`missingViewModelProperties` 集合。
- Session 生命周期与 `file.absolutePath` 一致：文件切换 → 销毁旧 session（释放监听、清空缓存）→ 创建新 session。
- 需确保 session 清理在 UI 线程执行，避免 JNI 对象在后台线程被误用。

## 测试与验证清单
1. **多实例同步**：准备含两个 state machine 的 `.riv`，确认时间/电量同时驱动两者。
2. **ViewModel 切换**：通过 Intent 传不同的 ViewModel 名称，验证日志显示重新绑定、旧实例释放。
3. **属性类型**：对布尔、字符串、触发器、枚举分别调用 setter，并观测动画响应。
4. **监听回调**：注册某个属性的 `BindToValueChange`，在 Rive 内部修改该属性后，确认回调触发。
5. **文件重置**：连续打开两份 `.riv`，确保第二份不再打印第一份的缺失属性日志。
6. **混合回退**：在 `hybrid` 模式下，删除某个 ViewModel 属性但保留 state machine 输入，验证仍能收到更新；切换到 `viewmodel_only`/`state_machine_only` 检查行为符合预期。

测试记录需包含：输入文件、Intent/配置、关键日志片段或截图。

## 兼容性与回退策略
- 默认行为保持“自动绑定默认 ViewModel + hybrid 模式”，以兼容旧版。
- 若 Rive 文件缺少 ViewModel，`ensureViewModelBinding()` 返回 null，自动降级为 `state_machine_only`，并在 UI 上提示（Toast/日志）。
- Intent/设置未指定 ViewModel 名称时，继续使用默认；若指定名称不存在，记录警告并回退到默认。

## 风险与依赖
- **Rive 文件支持**：需要设计团队提供包含 ViewModel 名称、实例的 `.riv`，否则部分功能无法验证。
- **性能风险**：多实例同步和监听可能增加 JNI 频率，应监控 `RivePlayerUI` 的 update 耗时，必要时增加节流。
- **JNI 兼容**：项目使用的 AAR 版本可能缺少最新 API（如 property listeners），如遇缺失需评估升级 Rive runtime。
- **用户配置迁移**：新增 Intent/设置项需在文档与 UI 中同步说明，避免旧脚本/自动化因参数变动失败。

## 日志与监控要求
- 统一使用 tag：`RiveBinding`, `RiveBindingSession`, `RiveBindingListener`。
- 写入事件格式：`[write] target=<viewmodel|stateMachine> field=timeHour value=12.5 mode=hybrid`.
- 监听事件格式：`[listen] field=battery newValue=80 source=viewmodel`.
- Session 生命周期日志：创建/销毁时打印文件、ViewModel 名称、模式，便于诊断。
- 当发生降级（如找不到 ViewModel 或属性）时必须有 `WARN` 级别日志，并记录触发原因。

## 里程碑与交付
1. **M1：多实例/文件重置** — 实现目标 1 & 3，确保多个 state machine 同步与文件隔离；
2. **M2：拓展属性 & 混合回退** — 实现目标 2 & 4，并补充调试日志/文档。

每个里程碑需更新现有 README/指南，并附带最少一次端到端测试记录（含截图或日志）。
