# OpenIM Android 群组音视频通话 API 使用指南

## 📋 概述

本文档描述如何使用OpenIM Android项目中的群组音视频通话功能API。所有API都遵循信令驱动架构原则，不直接操作LiveKit API。

**当前版本**: MVP v1.0 + Week 2 Day 6 多路视频流功能  
**架构状态**: ✅ 编译成功，严格遵循信令驱动模式  
**支持功能**: 1v1通话 + 群组通话(最多9人) + 多路视频流管理

## 🚀 快速开始

### 1. 初始化CallingVM

```java
// 创建CallingVM实例
CallingService callingService = new CallingServiceImpl();
CallingVM callingVM = new CallingVM(callingService, true); // true表示支持群组通话

// 设置群组信令监听器
callingVM.setGroupSignalingListener(new CallingVM.GroupSignalingListener() {
    @Override
    public void onMemberStateChanged(String userId, CallMemberState newState) {
        // 处理成员状态变化
        Log.d(TAG, \"成员 \" + userId + \" 状态变更为: \" + newState.name());
    }
    
    @Override
    public void onCallEnded() {
        // 处理通话结束
        Log.d(TAG, \"群组通话已结束\");
    }
    
    @Override
    public void onMemberJoined(String userId) {
        // 处理成员加入
        Log.d(TAG, \"成员 \" + userId + \" 加入通话\");
    }
    
    @Override
    public void onMemberLeft(String userId) {
        // 处理成员离开
        Log.d(TAG, \"成员 \" + userId + \" 离开通话\");
    }
});
```

### 2. 发起群组通话

```java
// 准备成员列表
List<String> memberIds = Arrays.asList(\"user_001\", \"user_002\", \"user_003\");

// 发起群组音频通话
callingVM.initiateGroupCall(\"group_123\", memberIds, false); // false表示音频通话

// 发起群组视频通话
callingVM.initiateGroupCall(\"group_123\", memberIds, true); // true表示视频通话
```

### 3. 处理群组信令

```java
// 在收到群组信令时调用
public void onReceiveGroupSignaling(MultiPartySignaling signaling) {
    callingVM.handleGroupSignaling(signaling);
}

// 示例：创建信令对象
MultiPartySignaling signaling = new MultiPartySignaling();
signaling.setType(Constants.MsgType.MULTI_PARTY_INVITE.name());
signaling.setRoomID(\"room_123\");
signaling.setInviterID(\"user_001\");
signaling.setInviteeList(Arrays.asList(\"user_002\", \"user_003\"));
signaling.setVideoCall(true);
signaling.setTimestamp(System.currentTimeMillis());
```

## 🎯 核心API详解

### CallingVM 主要方法

#### 群组通话管理
```java
// 发起群组通话
public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideoCall)

// 接受群组通话邀请
public void acceptGroupCall()

// 拒绝群组通话邀请  
public void rejectGroupCall()

// 挂断群组通话
public void hangupGroupCall()

// 处理群组信令
public void handleGroupSignaling(MultiPartySignaling signaling)
```

#### 设备控制
```java
// 麦克风控制
public void setMicEnabled(boolean enabled)

// 摄像头控制
public void setCameraEnabled(boolean enabled)

// 切换前后摄像头
public void flipCamera()

// 扬声器控制
public void setSpeakerEnabled(boolean enabled)
```

#### 成员管理
```java
// 获取群组成员列表
public List<GroupCallMember> getGroupMembers()

// 获取特定成员信息
public GroupCallMember getMember(String userId)

// 更新成员状态
public void updateMemberState(String userId, CallMemberState newState)

// 踢出成员（仅主持人）
public void removeMember(String userId)
```

### CallViewModel (Kotlin) 核心API

#### LiveKit集成
```kotlin
// 连接到群组房间
suspend fun connectToGroupRoom(
    roomUrl: String, 
    token: String, 
    memberIds: List<String>,
    callback: (Result<Boolean>) -> Unit
)

// 断开房间连接
fun disconnect()

// 绑定成员视频渲染器
suspend fun bindGroupMemberVideoRenderer(
    renderer: TextureViewRenderer, 
    participantId: String
)

// 解绑视频渲染器
fun unbindGroupMemberVideoRenderer(renderer: TextureViewRenderer)
```

#### Week 2 Day 6: 多路视频流管理
```kotlin
// 注册视频流到MultiStreamManager
fun registerVideoStream(
    participantId: String, 
    renderer: TextureViewRenderer, 
    priority: StreamPriority
)

// 更新流优先级
fun updateStreamPriority(participantId: String, priority: StreamPriority)

// 获取流统计信息
fun getStreamStatistics(): StreamStatistics

// 开始性能监控
fun startPerformanceMonitoring()

// 获取性能报告
fun getPerformanceReport(): PerformanceReport
```

#### 状态监听
```kotlin
// 监听群组成员状态
val groupMembers: StateFlow<List<GroupCallMember>>

// 监听当前说话者 (Week 2 Day 6)
val activeSpeakersMulti: StateFlow<List<String>>
val primarySpeakerMulti: StateFlow<String?>

// 监听连接状态
val connectionState: StateFlow<ConnectionState>

// 监听通话状态
val callState: StateFlow<CallState>
```

### MultiStreamManager API (Week 2 Day 6)

#### 流注册管理
```kotlin
// 注册视频流
fun registerStream(
    participantId: String,
    renderer: TextureViewRenderer, 
    priority: StreamPriority = StreamPriority.NORMAL
)

// 注销视频流
fun unregisterStream(participantId: String)

// 更新流优先级
fun updateStreamPriority(participantId: String, priority: StreamPriority)
```

#### 优先级管理
```kotlin
enum class StreamPriority {
    HIGH,    // 主要说话者，优先渲染
    NORMAL,  // 普通参与者
    LOW      // 网络较差的参与者
}

// 自动设置说话者为高优先级
fun handleSpeakerChanged(speakerId: String)

// 手动调整优先级
fun adjustPriorityBasedOnNetworkQuality(
    participantId: String, 
    networkQuality: NetworkQuality
)
```

#### 自适应质量控制
```kotlin
// 启用/禁用自适应质量
fun setAdaptiveQualityEnabled(enabled: Boolean)

// 根据网络质量调整视频质量
fun adjustVideoQuality(
    participantId: String, 
    connectionQuality: ConnectionQuality
)

// 获取当前质量设置
fun getCurrentVideoQuality(participantId: String): VideoQuality
```

### VideoStreamMonitor API (Week 2 Day 6)

#### 性能监控
```kotlin
// 开始监控
fun startMonitoring()

// 停止监控
fun stopMonitoring()

// 获取流统计
fun getStreamStatistics(): StreamStatistics

// 获取性能报告
fun getPerformanceReport(): PerformanceReport
```

#### 监控数据结构
```kotlin
data class StreamStatistics(
    val totalStreams: Int,
    val activeStreams: Int,
    val highPriorityStreams: Int,
    val normalPriorityStreams: Int,
    val lowPriorityStreams: Int
)

data class PerformanceReport(
    val timestamp: Long,
    val runningTimeMs: Long,
    val streamStatistics: StreamStatistics,
    val activeSpeakerCount: Int,
    val primarySpeakerId: String?,
    val totalFramesRendered: Long,
    val totalQualitySwitches: Long,
    val memoryUsage: Long,
    val cpuUsage: Double
)
```

## 📱 UI集成示例

### 群组通话界面初始化

```java
public class CallDialog {
    private CallingVM callingVM;
    private CallViewModel callViewModel;
    private GroupMemberAdapter memberAdapter;
    
    private void initGroupCall() {
        // 1. 初始化适配器
        memberAdapter = new GroupMemberAdapter(this, callViewModel);
        recyclerView.setAdapter(memberAdapter);
        
        // 2. 设置网格布局管理器
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int memberCount = memberAdapter.getItemCount();
                if (memberCount == 1) return 3; // 1人占满屏
                if (memberCount == 2) return 2; // 2人各占1/2
                return 1; // 3人以上使用网格
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        
        // 3. 监听成员状态变化
        callingVM.setGroupSignalingListener(new GroupSignalingListener() {
            @Override
            public void onMemberStateChanged(String userId, CallMemberState newState) {
                runOnUiThread(() -> memberAdapter.notifyDataSetChanged());
            }
        });
        
        // 4. Week 2 Day 6: 开始性能监控
        callViewModel.startPerformanceMonitoring();
    }
}
```

### 成员适配器实现

```java
public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder> {
    private CallViewModel callViewModel;
    private List<GroupCallMember> members;
    
    @Override
    public void onBindViewHolder(MemberViewHolder holder, int position) {
        GroupCallMember member = members.get(position);
        
        // 绑定基础信息
        holder.tvNickname.setText(member.getNickname());
        holder.ivMicState.setVisibility(member.isMicEnabled() ? View.GONE : View.VISIBLE);
        holder.ivCameraState.setVisibility(member.isCameraEnabled() ? View.GONE : View.VISIBLE);
        
        // Week 2 Day 6: 注册视频流到MultiStreamManager
        if (member.isCameraEnabled()) {
            StreamPriority priority = determineStreamPriority(member);
            callViewModel.registerVideoStream(member.getUserID(), holder.videoRenderer, priority);
        }
        
        // 设置状态指示器
        updateMemberStateIndicator(holder, member);
    }
    
    private StreamPriority determineStreamPriority(GroupCallMember member) {
        if (member.isSpeaking()) {
            return StreamPriority.HIGH; // 说话者优先级最高
        } else if (member.getNetworkQuality() <= 2) {
            return StreamPriority.LOW; // 网络差的优先级低
        } else {
            return StreamPriority.NORMAL; // 默认正常优先级
        }
    }
}
```

## 🔧 信令系统使用

### 信令类型定义

```java
// 在Constants.java中定义的群组通话信令
public enum MsgType {
    MULTI_PARTY_INVITE(210, \"multiPartyInvite\"),
    MULTI_PARTY_ACCEPT(211, \"multiPartyAccept\"),
    MULTI_PARTY_REJECT(212, \"multiPartyReject\"),
    MULTI_PARTY_CANCEL(213, \"multiPartyCancel\"),
    MULTI_PARTY_HANGUP(214, \"multiPartyHangup\"),
    MULTI_PARTY_MEMBER_JOIN(215, \"multiPartyMemberJoin\"),
    MULTI_PARTY_MEMBER_LEAVE(216, \"multiPartyMemberLeave\"),
    MULTI_PARTY_MEMBER_STATE_CHANGE(217, \"multiPartyMemberStateChange\"),
    MULTI_PARTY_SPEAKING_STATE(218, \"multiPartySpeakingState\"),
    MULTI_PARTY_QUALITY_REPORT(219, \"multiPartyQualityReport\");
}
```

### 创建和发送信令

```java
public void sendGroupInviteSignaling(String groupId, List<String> memberIds, boolean isVideoCall) {
    MultiPartySignaling signaling = new MultiPartySignaling();
    signaling.setType(Constants.MsgType.MULTI_PARTY_INVITE.name());
    signaling.setRoomID(generateRoomId(groupId));
    signaling.setInviterID(getCurrentUserId());
    signaling.setInviteeList(memberIds);
    signaling.setVideoCall(isVideoCall);
    signaling.setTimestamp(System.currentTimeMillis());
    signaling.setMessageId(UUID.randomUUID().toString()); // 用于去重
    
    // 发送信令（通过IM系统）
    sendSignalingToGroup(groupId, signaling);
}

public void sendMemberStateChangeSignaling(String memberId, CallMemberState newState) {
    MultiPartySignaling signaling = new MultiPartySignaling();
    signaling.setType(Constants.MsgType.MULTI_PARTY_MEMBER_STATE_CHANGE.name());
    signaling.setMemberID(memberId);
    signaling.setMemberState(newState);
    signaling.setTimestamp(System.currentTimeMillis());
    signaling.setMessageId(UUID.randomUUID().toString());
    
    sendSignalingToGroup(currentGroupId, signaling);
}
```

### 处理接收到的信令

```java
// 在IM消息接收处调用
public void onReceiveIMMessage(IMMessage message) {
    if (message.getType() >= 210 && message.getType() <= 219) {
        // 解析群组通话信令
        MultiPartySignaling signaling = parseMultiPartySignaling(message);
        
        // 交给CallingVM处理
        callingVM.handleGroupSignaling(signaling);
    }
}
```

## ⚠️ 重要注意事项

### 架构合规性
1. **不直接操作LiveKit API**: 所有LiveKit交互必须通过Manager层
2. **信令驱动**: 状态变更通过信令系统触发
3. **分层调用**: UI → 业务层 → 数据层 → Manager层 → SDK

### 错误处理
```java
// 正确的错误处理方式
try {
    callingVM.initiateGroupCall(groupId, memberIds, true);
} catch (GroupCallException e) {
    Log.e(TAG, \"群组通话发起失败\", e);
    showUserFriendlyError(\"无法发起群组通话，请检查网络连接\");
}
```

### 资源管理
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    
    // 清理资源
    if (callViewModel != null) {
        callViewModel.disconnect();
        callViewModel.stopPerformanceMonitoring(); // Week 2 Day 6
    }
    
    if (callingVM != null) {
        callingVM.cleanup();
    }
}
```

### 线程安全
```kotlin
// 在正确的线程中更新UI
callingVM.setGroupSignalingListener(object : GroupSignalingListener {
    override fun onMemberStateChanged(userId: String, newState: CallMemberState) {
        runOnUiThread {
            memberAdapter.notifyDataSetChanged()
        }
    }
})
```

## 🧪 测试建议

### 基础功能测试
```java
@Test
public void testGroupCallInitiation() {
    List<String> members = Arrays.asList(\"user1\", \"user2\");
    
    callingVM.initiateGroupCall(\"group123\", members, true);
    
    // 验证信令是否正确发送
    verify(mockSignalingService).sendMultiPartyInvite(any());
    
    // 验证成员状态是否正确初始化
    assertEquals(CallMemberState.INVITING, callingVM.getMember(\"user1\").getState());
}

@Test
public void testMultiStreamManagerRegistration() {
    TextureViewRenderer renderer = new TextureViewRenderer(context);
    
    callViewModel.registerVideoStream(\"user1\", renderer, StreamPriority.HIGH);
    
    StreamStatistics stats = callViewModel.getStreamStatistics();
    assertEquals(1, stats.getTotalStreams());
    assertEquals(1, stats.getHighPriorityStreams());
}
```

## 📚 更多资源

- [项目当前状态](./current-project-status.md)
- [架构原则和设计规范](./architecture-principles.md)
- [Week 2 Day 6 多路视频流实现详解](./week2-day6-multistream-implementation.md)
- [MVP发布说明](./mvp-release-notes.md)

---

**版本信息**: MVP v1.0 + Week 2 Day 6  
**最后更新**: 2024年12月  
**架构状态**: ✅ 编译成功，信令驱动架构合规