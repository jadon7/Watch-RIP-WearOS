# deviceKnob 惯性物理系统

## 🎯 核心理念

**用户不是"设置目标"，而是"推动轮子"！**

想象你在推一个真实的轮子：
- 推得快 → 轮子转得快
- 推得慢 → 轮子转得慢
- 停止推 → 轮子因摩擦力逐渐减速停止（惯性）

这就是本系统的设计思想！

---

## ⚙️ 物理模型

### 核心公式（每帧执行）

```
// 1. 应用用户推力
velocity += acceleration

// 2. 应用摩擦力（自然减速）
velocity *= friction

// 3. 更新位置
value += velocity
```

### 变量说明

| 变量 | 类型 | 含义 | 示例值 |
|------|------|------|--------|
| **deviceKnobValue** | Float | 当前位置 | 2.35 |
| **deviceKnobVelocity** | Float | 当前速度（单位/帧） | 0.15 |
| **userAcceleration** | Float | 用户施加的加速度 | 0.008 |
| **userRotationSpeed** | Float | 用户旋转速度（单位/秒） | 1.2 |

---

## 🔧 算法详解

### 步骤 1：监听旋钮输入，计算用户速度

```kotlin
rotaryKnobDeltaFlow.collect { delta ->
    val currentTime = System.currentTimeMillis()
    val timeDelta = currentTime - lastRotationTime
    lastRotationTime = currentTime

    // 计算用户旋转速度（单位/秒）
    val userRotationSpeed = delta / (timeDelta / 1000f)

    // 转换为加速度
    userAcceleration = userRotationSpeed × ACCELERATION_GAIN
}
```

**示例计算**：
- 用户旋转增量：delta = 0.2
- 时间间隔：timeDelta = 50ms
- 用户速度：0.2 / 0.05 = 4.0 单位/秒
- 加速度：4.0 × 0.08 = 0.32

---

### 步骤 2：检测用户停止，清零加速度

```kotlin
LaunchedEffect(Unit) {
    while (isActive) {
        delay(ROTATION_TIMEOUT_MS)  // 80ms
        if ((currentTime - lastRotationTime) >= 80ms) {
            userAcceleration = 0f  // 用户停止，清零推力
        }
    }
}
```

**作用**：80ms 内没有新输入 → 判定用户停止旋转 → 清零加速度

---

### 步骤 3：物理更新循环（每 16ms）

```kotlin
while (isActive) {
    // 1. 应用加速度（用户推力）
    deviceKnobVelocity += userAcceleration

    // 2. 应用摩擦力（自然减速）
    deviceKnobVelocity *= FRICTION  // 0.92

    // 3. 限制最大速度
    deviceKnobVelocity = clamp(deviceKnobVelocity, -0.6, 0.6)

    // 4. 检测静止
    if (abs(deviceKnobVelocity) < 0.001) {
        deviceKnobVelocity = 0f
        continue
    }

    // 5. 更新位置
    deviceKnobValue += deviceKnobVelocity

    // 6. 写入 Rive
    runtimeSession.updateDeviceKnob(view, deviceKnobValue)

    delay(16ms)
}
```

---

## 📊 行为分析

### 场景 1：单次快速旋转

```
t=0ms:   用户旋转 delta=0.5, speed=10 单位/秒
         acceleration = 10 × 0.08 = 0.8

t=16ms:  velocity = 0 + 0.8 = 0.8 → 限制到 0.6
         value = 0 + 0.6 = 0.6

t=32ms:  velocity = 0.6 × 0.92 = 0.552
         value = 0.6 + 0.552 = 1.152

t=48ms:  velocity = 0.552 × 0.92 = 0.508
         value = 1.152 + 0.508 = 1.66

t=80ms:  用户停止检测到，acceleration = 0

t=96ms:  velocity = 0.508 × 0.92 = 0.467
         value = 1.66 + 0.467 = 2.127

t=112ms: velocity = 0.467 × 0.92 = 0.43
         ...继续惯性滑行

t=~500ms: velocity < 0.001，停止 ✅
```

**特点**：
- 快速达到最大速度 0.6
- 停止推动后，摩擦力逐帧减速
- 产生明显的惯性滑行效果

---

### 场景 2：慢速连续旋转

```
t=0ms:   delta=0.05, speed=1 单位/秒, accel=0.08
         velocity = 0 + 0.08 = 0.08

t=16ms:  velocity = 0.08 × 0.92 = 0.0736
         value = 0 + 0.0736 = 0.074

t=30ms:  再次旋转 delta=0.05, accel=0.08
         velocity = 0.0736 + 0.08 = 0.1536
         velocity = 0.1536 × 0.92 = 0.141

t=45ms:  再次旋转...
         velocity 逐渐增大到平衡点
```

**特点**：
- 速度缓慢增长
- 推力和摩擦力达到平衡
- 平滑跟随用户输入

---

### 场景 3：快速反向旋转

```
t=0ms:   正向旋转，velocity = 0.4

t=50ms:  反向旋转 delta=-0.3, accel=-0.24
         velocity = 0.4 + (-0.24) = 0.16
         velocity = 0.16 × 0.92 = 0.147

t=66ms:  再次反向旋转 accel=-0.24
         velocity = 0.147 + (-0.24) = -0.093
         velocity = -0.093 × 0.92 = -0.086
         ...现在反向移动
```

**特点**：
- 先减速、然后反向加速
- 自然的速度过渡
- 符合物理直觉

---

## 🎛️ 参数调优

### 参数说明

| 参数 | 默认值 | 范围 | 作用 |
|------|--------|------|------|
| **ACCELERATION_GAIN** | 0.08 | 0.05-0.15 | 推力强度，越大响应越快 |
| **FRICTION** | 0.92 | 0.85-0.98 | 摩擦系数，越小减速越快 |
| **MAX_VELOCITY** | 0.6 | 0.3-1.0 | 最大速度限制 |
| **MIN_VELOCITY** | 0.001 | 0.0005-0.005 | 静止判定阈值 |
| **ROTATION_TIMEOUT_MS** | 80 | 50-150 | 停止检测时间 |

---

### 调优场景

#### 问题 1：惯性太强，停不下来

**症状**：停止旋转后，还要滑行很久

**原因**：摩擦力太小

**解决**：降低摩擦系数
```kotlin
private const val KNOB_FRICTION = 0.88f  // 从 0.92 改为 0.88
```

**效果**：每帧多损失 4% 速度，减速更快

---

#### 问题 2：惯性太弱，感觉粘滞

**症状**：停止旋转后立即停止，没有滑行感

**原因**：摩擦力太大

**解决**：提高摩擦系数
```kotlin
private const val KNOB_FRICTION = 0.95f  // 从 0.92 改为 0.95
```

**效果**：每帧仅损失 5% 速度，滑行更远

---

#### 问题 3：响应太慢，推不动

**症状**：用力旋转，速度增长很慢

**原因**：加速度增益太小

**解决**：提高加速度增益
```kotlin
private const val KNOB_ACCELERATION_GAIN = 0.12f  // 从 0.08 改为 0.12
```

**效果**：相同旋转速度产生 1.5 倍加速度

---

#### 问题 4：太灵敏，一碰就飞

**症状**：轻轻旋转就速度很快

**原因**：加速度增益太大

**解决**：降低加速度增益
```kotlin
private const val KNOB_ACCELERATION_GAIN = 0.05f  // 从 0.08 改为 0.05
```

**效果**：需要更大的旋转力才能加速

---

#### 问题 5：快速旋转时跳变

**症状**：快速旋转时，画面有跳跃感

**原因**：最大速度太高

**解决**：降低最大速度
```kotlin
private const val KNOB_MAX_VELOCITY = 0.4f  // 从 0.6 改为 0.4
```

**效果**：即使快速旋转，速度也不会超过 0.4

---

## 🧪 调试技巧

### 查看实时日志

```bash
adb logcat | grep KnobInterpolation

# 输出示例
User input: delta=0.120, speed=2.40/s, accel=0.0192
value: 0.156, velocity: 0.0789, accel: 0.0192
value: 0.235, velocity: 0.1234, accel: 0.0192
User stopped rotating, acceleration cleared
value: 0.349, velocity: 0.1135, accel: 0.0000
value: 0.453, velocity: 0.1044, accel: 0.0000
...速度逐渐衰减
value: 0.998, velocity: 0.0009, accel: 0.0000
```

### 关键指标

| 指标 | 说明 | 正常值 |
|------|------|--------|
| **speed** | 用户旋转速度 | 慢速: 0.5-2/s<br>快速: 5-15/s |
| **accel** | 加速度 | 与 speed 成正比 |
| **velocity** | 当前速度 | 逐渐增长到 MAX 或摩擦平衡 |
| **加速阶段** | 有 accel，velocity 增长 | 3-10 帧 |
| **惯性阶段** | accel=0，velocity 衰减 | 10-30 帧 |

### 可视化测试

**惯性测试**：
1. 快速旋转 3 圈
2. 立即放手
3. 观察参数是否继续滑行
4. 记录停止时间

**摩擦测试**：
1. 单次旋转后放手
2. 计算从停止旋转到完全静止的时间
3. 理想值：0.5-1.5 秒

---

## 🆚 对比：三代方案

### 第一代：插值队列

```
用户旋转 → 生成插值点队列 → 逐帧消费
```

**问题**：
- ❌ 队列堆积
- ❌ 停止延迟（7-8 秒）
- ❌ 管理复杂

---

### 第二代：速度跟随目标

```
用户旋转 → 设置目标值 → 速度追赶目标
```

**问题**：
- ❌ 没有惯性感
- ❌ 停止后立即停止
- ✅ 无队列

---

### 第三代：惯性物理系统（当前）

```
用户旋转 → 计算速度 → 转换为推力 → 自然滑行
```

**优势**：
- ✅ 真实惯性
- ✅ 速度感应
- ✅ 自然减速
- ✅ 简单高效

---

## 📐 物理原理

### 经典力学模型

本系统基于**牛顿第二定律** + **滑动摩擦力**：

```
F = ma           (牛顿第二定律)
F_friction = -μv (摩擦力与速度成正比)

合力：
F_total = F_user + F_friction
        = ma + (-μv)

加速度：
a = F_user/m - μv/m
```

我们的实现（简化版）：

```
velocity += acceleration      （用户推力）
velocity *= friction         （摩擦阻力）
```

这相当于：
- `acceleration` = 用户施加的力
- `friction` = 1 - 摩擦系数

---

### 阻尼振动类比

如果把 deviceKnob 看作弹簧质量系统：

| 物理量 | 对应 |
|--------|------|
| 位移 x | deviceKnobValue |
| 速度 v | deviceKnobVelocity |
| 外力 F | userAcceleration |
| 阻尼 b | 1 - FRICTION |

运动方程：
```
v(t+Δt) = v(t) × (1-b) + F×Δt
x(t+Δt) = x(t) + v(t+Δt)×Δt
```

**FRICTION = 0.92**，意味着阻尼比 **b = 0.08**，每帧损失 8% 速度。

---

## 🎨 高级优化

### 1. 非线性摩擦

模拟真实摩擦：低速时摩擦更大

```kotlin
val dynamicFriction = when {
    abs(velocity) < 0.1f -> 0.85f  // 低速：高摩擦
    abs(velocity) < 0.3f -> 0.90f  // 中速：中等
    else -> 0.95f                  // 高速：低摩擦
}
velocity *= dynamicFriction
```

---

### 2. 速度预测

根据当前速度预测用户意图：

```kotlin
if (abs(userAcceleration) < 0.01 && abs(velocity) > 0.2) {
    // 用户停止推但速度还很快 → 可能是想快速滑动
    // 可以暂时增大摩擦系数，加快响应
}
```

---

### 3. 边界弹性

达到边界时反弹：

```kotlin
if (deviceKnobValue < 0) {
    deviceKnobValue = 0
    deviceKnobVelocity = -deviceKnobVelocity × 0.5  // 反弹，损失 50% 能量
}
```

---

### 4. 音效反馈

根据速度播放不同音效：

```kotlin
if (abs(velocity) > 0.3) {
    playSound(SOUND_FAST_SCROLL)
} else if (abs(velocity) > 0.1) {
    playSound(SOUND_NORMAL_SCROLL)
}
```

---

## ✅ 总结

| 方面 | 说明 |
|------|------|
| **核心理念** | 用户推动轮子，而非设置目标 |
| **物理模型** | 加速度 + 速度 + 摩擦力 |
| **代码量** | ~90 行核心逻辑 |
| **参数数量** | 6 个，物理意义明确 |
| **惯性效果** | 真实自然，停止后滑行 |
| **响应速度** | 零延迟，立即响应 |
| **调优性** | 高，参数效果明显 |
| **复杂度** | 低，符合物理直觉 |

**推荐的第一次调整**：

如果你觉得默认参数不合适：

1. 惯性太强？→ `FRICTION = 0.88`（更多摩擦）
2. 惯性太弱？→ `FRICTION = 0.95`（更少摩擦）
3. 响应太慢？→ `ACCELERATION_GAIN = 0.12`（更强推力）
4. 响应太快？→ `ACCELERATION_GAIN = 0.05`（更弱推力）

现在去真机测试，感受真实的物理惯性！🎉
