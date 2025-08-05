# CallViewModel重构方案

## 问题分析
当前CallViewModel.kt有772行代码，承担了太多职责，违反单一职责原则：

1. LiveKit连接管理
2. 音视频设备控制  
3. 群组通话管理
4. 参与者管理
5. 视频渲染绑定
6. 协程生命周期管理
7. 屏幕共享管理
8. 扬声器管理

## 重构方案：按领域拆分

### 1. CallViewModel (核心协调器)
```kotlin
class CallViewModel(application: Application) : AndroidViewModel(application) {
    // 只保留核心协调逻辑
    private val roomManager = CallRoomManager(application)
    private val deviceManager = MediaDeviceManager()  
    private val groupManager = GroupCallManager()
    private val videoManager = VideoBindingManager()
    
    // 暴露统一接口给UI层
    fun connectToRoom(url: String, token: String) = roomManager.connect(url, token)
    fun setMicEnabled(enabled: Boolean) = deviceManager.setMicEnabled(enabled)
    // ... 其他委托方法
}
```

### 2. CallRoomManager (房间连接管理)
```kotlin
class CallRoomManager(application: Application) {
    val room = LiveKit.create(...)
    
    suspend fun connect(url: String, token: String) { }
    fun disconnect() { }
    fun reconnect() { }
    // 专注LiveKit房间连接逻辑
}
```

### 3. MediaDeviceManager (媒体设备管理)  
```kotlin
class MediaDeviceManager {
    fun setMicEnabled(enabled: Boolean) { }
    fun setCameraEnabled(enabled: Boolean) { }
    fun flipCamera() { }
    fun startScreenCapture(intent: Intent) { }
    fun stopScreenCapture() { }
    // 专注设备控制
}
```

### 4. GroupCallManager (群组通话管理)
```kotlin
class GroupCallManager {
    private val mutableGroupMembers = MutableStateFlow<Map<String, Participant>>(emptyMap())
    
    suspend fun connectToGroupRoom(url: String, token: String, memberIds: List<String>) { }
    fun getGroupParticipants(): StateFlow<List<Participant>> { }
    fun handleParticipantConnected(participant: RemoteParticipant) { }
    // 专注群组通话逻辑
}
```

### 5. VideoBindingManager (视频绑定管理)
```kotlin
class VideoBindingManager {
    suspend fun bindGroupMemberVideoRenderer(renderer: TextureViewRenderer, participantId: String) { }
    fun unbindGroupMemberVideoRenderer(renderer: TextureViewRenderer) { }
    fun bindVideoTrack(renderer: TextureViewRenderer, track: VideoTrack) { }
    // 专注视频绑定逻辑
}
```

### 6. SpeakerManager (扬声器管理)
```kotlin 
class SpeakerManager {
    private val mutablePrimarySpeaker = MutableStateFlow<Participant?>(null)
    
    fun handlePrimarySpeaker(participants: List<Participant>, speakers: List<Participant>) { }
    fun getActiveSpeakersFlow(): StateFlow<List<Participant>> { }
    // 专注扬声器逻辑
}
```

## 重构收益

### 1. 可维护性提升
- 每个类职责单一，易于理解和修改
- 新功能开发更专注
- bug修复影响范围更小

### 2. 可测试性提升  
- 每个Manager可以独立单元测试
- Mock依赖更简单
- 测试覆盖率更容易提升

### 3. 可扩展性提升
- 新增功能只需扩展对应Manager
- 功能模块可以独立演进
- 更容易支持插件化架构

### 4. 代码复用
- Manager类可以在其他地方复用
- 减少重复代码
- 更好的组件化

## 实施建议

### 阶段1: 创建Manager接口
- 定义各Manager的接口契约
- 保持现有CallViewModel不变

### 阶段2: 逐步迁移功能
- 一次迁移一个Manager
- 保持向后兼容
- 充分测试

### 阶段3: 清理CallViewModel
- 移除已迁移的代码
- CallViewModel变为纯协调器
- 最终目标控制在100-150行

## 是否立即开始重构？

鉴于当前正在开发MVP阶段，建议：
1. **先完成MVP功能**（继续Week 2 Day 6）
2. **在MVP完成后进行重构**
3. **重构时可以作为独立任务进行**

这样既不影响当前开发进度，又能在功能稳定后获得更好的代码质量。