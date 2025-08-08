# OpenIM Android 架构原则和设计规范

## 🎯 核心架构原则

### 1. 统一异常处理架构 (Unified Exception Handling Architecture) 🆕

**核心原则**: **统一异常处理，规范错误管理**

这是本项目的新添加的重要架构原则。所有的异常处理必须通过LogExceptionHandler统一管理，确保：
- 异常处理的一致性和可预测性
- 业务流程的完整追踪和监控
- 用户友好的错误提示和恢复机制

#### 正确的异常处理流程
```java
// 正确：使用统一异常处理
public void someBusinessMethod() {
    try {
        // 业务逻辑
        performBusinessOperation();
    } catch (Exception e) {
        // 统一异常处理
        String errorCode = LogExceptionHandler.handleException(
            e, "CallingVM", "business_operation", "业务操作失败"
        );
        // 基于错误类型执行恢复策略
        handleErrorRecovery(errorCode, e);
    }
}
```

#### BusinessFlow业务流程追踪
```java
// 业务流程的完整生命周期管理
LogExceptionHandler.BusinessFlow flow = LogExceptionHandler.BusinessFlow.start(
    "CallingVM", "群组通话发起"
);
try {
    // 业务操作
    initiateGroupCall();
    flow.success("群组通话发起成功");
} catch (Exception e) {
    flow.error(e, "群组通话发起失败");
    throw e;
}
```

### 2. 信令驱动架构 (Signal-Driven Architecture)

**核心原则**: **不直接操作LiveKit API**

这是本项目的最重要架构原则。所有与LiveKit的交互必须通过信令系统和Manager层进行，确保：
- 业务逻辑与底层SDK解耦
- 状态管理统一和可控
- 更好的可测试性和可维护性

#### 正确的架构层次
```
UI层 (CallDialog.java)
  ↓ 业务调用
业务层 (CallingVM.java) 
  ↓ 管理器调用
数据层 (CallViewModel.kt)
  ↓ Manager封装
Manager层 (GroupCallManager, MultiStreamManager等)
  ↓ 最终API调用
LiveKit SDK
```

#### ❌ 错误示例 - 直接操作LiveKit API
```java
// 错误：在业务层直接调用LiveKit API
public void enableCamera() {
    room.localParticipant.setCameraEnabled(true); // ❌ 违反架构原则
}
```

#### ✅ 正确示例 - 通过信令驱动
```java
// 正确：通过信令系统和Manager层
public void enableCamera() {
    // 1. 发送信令
    sendDeviceControlSignaling(CAMERA_ENABLE);
    
    // 2. 通过Manager处理
    callViewModel.setCameraEnabled(true);
}
```

### 3. Manager模式封装

使用Manager模式封装对LiveKit SDK的直接访问：

#### GroupCallManager.kt - 群组通话管理
```kotlin
class GroupCallManager {
    // 封装LiveKit群组功能
    fun connectToGroupRoom(url: String, token: String) { /* 实现 */ }
    fun handleParticipantConnected(participant: RemoteParticipant) { /* 实现 */ }
    fun updateMemberState(userId: String, state: CallMemberState) { /* 实现 */ }
}
```

#### MultiStreamManager.kt - 多流管理 (Week 2 Day 6)
```kotlin
class MultiStreamManager {
    // 智能视频流管理
    fun registerVideoStream(participantId: String, renderer: TextureViewRenderer, priority: StreamPriority)
    fun updateStreamPriority(participantId: String, priority: StreamPriority)
    fun handleSpeakerChanged(speakerId: String)
}
```

#### VideoResourcePool.java - 资源池管理
```kotlin
class VideoResourcePool {
    // 统一管理视频渲染器资源
    fun acquireRenderer(): TextureViewRenderer
    fun releaseRenderer(renderer: TextureViewRenderer)
    fun bindVideoTrack(renderer: TextureViewRenderer, track: VideoTrack)
}
```

### 4. 状态管理统一性

#### 使用StateFlow进行响应式状态管理
```kotlin
class CallViewModel {
    // 统一的状态流
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()
    
    private val _groupMembers = MutableStateFlow<List<GroupCallMember>>(emptyList())
    val groupMembers = _groupMembers.asStateFlow()
}
```

#### 状态转换验证
```java
public enum CallMemberState {
    IDLE, INVITING, RINGING, CONNECTING, CONNECTED, DISCONNECTED;
    
    public boolean canTransitionTo(CallMemberState target) {
        // 严格的状态转换验证
        switch (this) {
            case IDLE: return target == INVITING;
            case INVITING: return target == RINGING || target == REJECTED;
            // ... 其他转换规则
        }
    }
}
```

## 🏗️ 分层架构详细说明

### UI层 (Presentation Layer)
**职责**: 用户界面展示和用户交互处理
- `CallDialog.java`: 通话界面主控制器
- `GroupMemberAdapter.java`: 群组成员列表适配器  
- `SmartVideoStreamAdapter.kt`: 智能视频流适配器

**原则**:
- 只处理UI逻辑，不包含业务逻辑
- 通过ViewModel观察状态变化
- 用户操作通过信令发送到业务层

### 业务层 (Business Layer)
**职责**: 业务逻辑处理和流程控制
- `CallingVM.java`: 核心业务逻辑，信令处理，状态管理

**原则**:
- 处理所有通话相关业务逻辑
- 响应信令事件，更新业务状态
- 协调各Manager完成复杂功能

### 数据层 (Data Layer) 
**职责**: 数据访问和LiveKit SDK集成
- `CallViewModel.kt`: LiveKit SDK封装，Manager协调层

**原则**:
- 作为Manager层的协调者
- 提供统一的数据访问接口
- 管理LiveKit连接和资源

### Manager层 (Manager Layer)
**职责**: 专域功能封装和SDK直接调用
- `GroupCallManager.kt`: 群组通话专用功能
- `MultiStreamManager.kt`: 多路视频流智能管理
- `VideoBindingManager.kt`: 视频绑定管理
- `CallRoomManager.kt`: 房间连接管理

### 异常处理层 (Exception Handling Layer) 🆕
**职责**: 统一异常处理和日志管理
- `LogExceptionHandler.java`: 统一异常处理工具类
  - 异常分类和自动推断
  - BusinessFlow业务流程追踪
  - 错误恢复策略建议
- `L.java`: 增强日志系统(向后兼容)
  - critical()、stateChange()、handleException()
  - 业务流程日志记录
  - 关键操作状态追踪

**原则**:
- 每个Manager专注单一职责领域
- 所有异常必须通过LogExceptionHandler处理
- 日志系统必须保持向后兼容性
- 是唯一可以直接调用LiveKit API的层
- 向上提供领域特定的业务接口

## 📊 信令系统设计

### 信令类型定义
```java
public enum MsgType {
    // 1v1通话信令
    CALLING_INVITE(200), CALLING_ACCEPT(201), CALLING_REJECT(202),
    CALLING_CANCEL(203), CALLING_HANGUP(204),
    
    // 群组通话信令 (MVP v1.0)
    MULTI_PARTY_INVITE(210), MULTI_PARTY_ACCEPT(211), 
    MULTI_PARTY_REJECT(212), MULTI_PARTY_CANCEL(213),
    MULTI_PARTY_HANGUP(214), MULTI_PARTY_MEMBER_JOIN(215),
    MULTI_PARTY_MEMBER_LEAVE(216), MULTI_PARTY_MEMBER_STATE_CHANGE(217),
    MULTI_PARTY_SPEAKING_STATE(218), MULTI_PARTY_QUALITY_REPORT(219);
}
```

### 信令处理流程
```java
// 标准信令处理模式
public void handleGroupSignaling(MultiPartySignaling signaling) {
    // 1. 信令去重
    if (signalingDeduplicator.isDuplicate(signaling.getMessageId())) {
        return;
    }
    
    // 2. 信令验证
    if (!validateSignaling(signaling)) {
        handleSignalingError(signaling);
        return;
    }
    
    // 3. 业务处理
    switch (signaling.getType()) {
        case MULTI_PARTY_INVITE:
            handleGroupInvite(signaling);
            break;
        case MULTI_PARTY_ACCEPT:
            handleGroupAccept(signaling);
            break;
        // ... 其他信令处理
    }
    
    // 4. 状态更新
    updateUIState();
}
```

### 信令去重机制
```java
public class SignalingDeduplicator {
    private final Map<String, Long> processedMessages = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 30000; // 30秒
    
    public boolean isDuplicate(String messageId) {
        Long timestamp = processedMessages.get(messageId);
        if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_TTL) {
            return true; // 重复信令
        }
        processedMessages.put(messageId, System.currentTimeMillis());
        return false;
    }
}
```

## 🔄 并发和线程安全

### Kotlin协程使用规范
```kotlin
class CallViewModel {
    fun connectToRoom(url: String, token: String) {
        viewModelScope.launch {
            try {
                // 在IO线程执行网络操作
                withContext(Dispatchers.IO) {
                    room.connect(url, token)
                }
                
                // 在Main线程更新UI状态
                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.CONNECTED
                }
            } catch (e: Exception) {
                handleConnectionError(e)
            }
        }
    }
}
```

### StateFlow状态管理
```kotlin
// 正确的StateFlow使用模式
class MultiStreamManager {
    private val _activeSpeakers = MutableStateFlow<List<String>>(emptyList())
    val activeSpeakers = _activeSpeakers.asStateFlow()
    
    private val _primarySpeaker = MutableStateFlow<String?>(null)
    val primarySpeaker = _primarySpeaker.asStateFlow()
    
    fun updateActiveSpeakers(speakers: List<String>) {
        _activeSpeakers.value = speakers
        _primarySpeaker.value = speakers.firstOrNull()
    }
}
```

## 🛡️ 错误处理策略

### 分层错误处理
```java
// Manager层：记录详细错误，向上抛出业务异常
public class GroupCallManager {
    public void connectToGroupRoom(String url, String token) throws GroupCallException {
        try {
            // LiveKit调用
            room.connect(url, token);
        } catch (Exception e) {
            Log.e(TAG, "Group room connection failed", e);
            throw new GroupCallException("无法连接到群组通话", e);
        }
    }
}

// 业务层：处理业务异常，更新用户状态
public class CallingVM {
    public void initiateGroupCall(String groupId, List<String> memberIds) {
        try {
            groupCallManager.connectToGroupRoom(roomUrl, token);
        } catch (GroupCallException e) {
            handleGroupCallError(e);
            showUserFriendlyError("群组通话连接失败，请检查网络连接");
        }
    }
}
```

### 用户友好错误提示
```java
public void handleGroupCallError(Exception error) {
    if (error instanceof NetworkException) {
        showError("网络连接异常，请检查网络设置");
    } else if (error instanceof TokenExpiredException) {
        showError("登录已过期，请重新登录");
    } else if (error instanceof RoomFullException) {
        showError("通话人数已满，无法加入");
    } else {
        showError("通话连接失败，请稍后重试");
    }
}
```

## 📏 代码质量规范

### 类职责单一性
- 每个类应该只有一个变更理由
- CallViewModel当前772行，建议重构拆分
- Manager类应保持在200行以内

### 接口设计原则
```kotlin
// 好的接口设计：简洁明确
interface IVideoStreamManager {
    fun registerStream(participantId: String, renderer: TextureViewRenderer, priority: StreamPriority)
    fun unregisterStream(participantId: String)
    fun updatePriority(participantId: String, priority: StreamPriority)
}

// 避免的接口设计：参数过多，职责不清
interface IBadManager {
    fun doEverything(param1: String, param2: Int, param3: Boolean, ...param10: Object) // ❌
}
```

### 资源管理规范
```kotlin
// 正确的资源管理
class VideoResourcePool {
    fun acquireRenderer(): TextureViewRenderer {
        return availableRenderers.poll() ?: createNewRenderer()
    }
    
    fun releaseRenderer(renderer: TextureViewRenderer) {
        renderer.clearVideo() // 清理资源
        availableRenderers.offer(renderer) // 回收到池中
    }
}
```

## 📋 架构合规检查清单

在开发过程中，请确保：

### ✅ 信令驱动原则
- [ ] 不在业务层直接调用LiveKit API
- [ ] 所有LiveKit交互通过Manager层封装
- [ ] 状态变更通过信令系统触发

### ✅ 分层架构原则  
- [ ] UI层只处理界面逻辑
- [ ] 业务层专注业务流程
- [ ] Manager层封装SDK调用
- [ ] 状态管理统一使用StateFlow

### ✅ 并发安全原则
- [ ] 使用Kotlin协程处理异步操作
- [ ] StateFlow更新在正确线程
- [ ] 避免直接操作UI线程

### ✅ 错误处理原则
- [ ] 分层错误处理策略
- [ ] 用户友好错误提示
- [ ] 详细错误日志记录

### ✅ 代码质量原则
- [ ] 类职责单一，代码行数合理
- [ ] 接口设计简洁明确
- [ ] 资源正确管理和释放

---

**重要提醒**: 违反\"不直接操作LiveKit API\"原则是项目中最严重的架构违规行为。所有开发都必须严格遵循信令驱动架构，确保系统的可维护性和稳定性。