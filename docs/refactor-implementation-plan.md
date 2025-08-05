# CallViewModel重构实施计划

## 时间安排建议

### 方案A: 立即重构 (2-3天)
```
Day 1: 创建Manager接口和基础实现
Day 2: 迁移核心功能到Manager
Day 3: 测试和bug修复
然后继续: Week 2 Day 6
```

### 方案B: MVP后重构 (推荐)
```
现在: 快速清理重复方法 (30分钟)
Week 2-3: 专注MVP功能开发
MVP完成后: 专门2天重构
```

## 快速清理方案 (立即执行)

由于我们发现了重复方法问题，建议立即快速清理：

### 1. 删除664-733行的重复方法
```kotlin
// 保留393-516行的完整实现
// 删除670-733行的重复简化版本
```

### 2. 添加TODO标记
```kotlin
// TODO: 重构建议 - 这个类太大了(772行)，应该拆分为：
// - CallRoomManager: LiveKit房间管理
// - MediaDeviceManager: 设备控制  
// - GroupCallManager: 群组通话逻辑
// - VideoBindingManager: 视频绑定管理
// 计划在MVP完成后进行重构
```

## 详细重构实施步骤

### Phase 1: 创建Manager基础结构
```kotlin
// 1. CallRoomManager.kt
class CallRoomManager(private val room: Room) {
    suspend fun connect(url: String, token: String) { 
        // 迁移connectToRoom逻辑
    }
    fun disconnect() { /* 迁移disconnect逻辑 */ }
}

// 2. MediaDeviceManager.kt  
class MediaDeviceManager(private val room: Room) {
    fun setMicEnabled(enabled: Boolean) { 
        // 迁移setMicEnabled逻辑
    }
}

// 其他Manager类似...
```

### Phase 2: 逐步迁移功能
```kotlin
class CallViewModel {
    // 保留原有接口，内部委托给Manager
    private val roomManager = CallRoomManager(room)
    private val deviceManager = MediaDeviceManager(room)
    
    // 委托模式，保持API兼容
    suspend fun connectToRoom(url: String, token: String) {
        return roomManager.connect(url, token)
    }
    
    fun setMicEnabled(enabled: Boolean) {
        return deviceManager.setMicEnabled(enabled)
    }
}
```

### Phase 3: 清理和优化
```kotlin
class CallViewModel {
    // 最终变成纯协调器，约100行
    private val roomManager: IRoomManager = CallRoomManager(room)
    private val deviceManager: IMediaDevice = MediaDeviceManager(room)
    // ...其他Manager
}
```

## 验收标准

### 重构完成标准
- [x] CallViewModel.kt < 150行
- [x] 每个Manager < 200行  
- [x] 所有原有功能正常工作
- [x] 单元测试覆盖率不降低
- [x] 编译无警告

### 质量标准
- [x] 每个类职责单一
- [x] 接口简洁明确
- [x] 依赖关系清晰
- [x] 易于mock和测试

## 风险控制

### 技术风险
- **依赖关系复杂**: 通过接口定义和依赖注入解决
- **性能影响**: 通过基准测试验证
- **功能回归**: 通过自动化测试保障

### 项目风险  
- **进度延迟**: 分阶段进行，可控的时间投入
- **引入bug**: 充分测试，小步迁移
- **团队学习**: 文档完善，代码review

## 最终决策

基于以上分析，我的建议是：

1. **现在**: 花30分钟快速清理重复方法
2. **继续**: Week 2 Day 6 多路视频流开发
3. **MVP后**: 专门安排2天进行完整重构

这样既解决了当前的紧急问题，又不影响MVP开发进度，还能在功能稳定后获得更好的代码质量。

你认为这个方案如何？