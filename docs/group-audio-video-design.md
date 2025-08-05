# 群组音视频通话功能设计概要

## 1. 概述

### 1.1 项目背景
基于现有OpenIM Android项目的1v1音视频通话功能，扩展实现多人群组音视频通话。保持与现有实现风格一致，使用LiveKit作为音视频引擎，通过IM信令系统进行呼叫管理。

### 1.2 设计目标
- **功能目标**: 支持多人群组音视频通话（最多9人）
- **技术目标**: 复用现有架构，最小化改动，保持性能稳定
- **体验目标**: 与现有1v1通话保持一致的交互体验
- **质量目标**: 通过TDD确保代码质量，达到业界最佳实践标准

### 1.3 设计原则
- **复用优先**: 最大化复用现有CallingVM、CallDialog、CallViewModel组件
- **渐进式**: 先实现MVP，后续迭代增加高级功能
- **测试驱动**: 采用TDD开发模式，先写测试再写功能
- **业界标准**: 融入状态管理、资源管理、信令去重等最佳实践

## 2. 现有架构分析

### 2.1 核心组件架构
```
CallDialog.java          -> UI层：通话界面管理
CallingVM.java          -> 业务层：通话逻辑和状态管理  
CallViewModel.kt        -> 数据层：LiveKit集成和房间管理
CallingServiceImp.java  -> 服务层：系统服务集成
```

### 2.2 现有信令协议
```java
// 现有1v1通话信令（Constants.MsgType）
CALLING_INVITE(200),     // 呼叫邀请
CALLING_ACCEPT(201),     // 接受呼叫  
CALLING_REJECT(202),     // 拒绝呼叫
CALLING_CANCEL(203),     // 取消呼叫
CALLING_HANGUP(204);     // 挂断呼叫
```

### 2.3 现有UI组件
- **dialog_call.xml**: 1v1通话界面
- **dialog_group_call.xml**: 群组通话界面（已存在，需扩展）
- **item_member_renderer.xml**: 成员视频渲染项（已存在）

## 3. 群组音视频扩展设计

### 3.1 信令协议扩展

#### 3.1.1 新增信令定义
```java
// 扩展群组通话信令（Constants.MsgType）
MULTI_PARTY_INVITE(210),      // 群组呼叫邀请
MULTI_PARTY_ACCEPT(211),      // 接受群组呼叫
MULTI_PARTY_REJECT(212),      // 拒绝群组呼叫  
MULTI_PARTY_CANCEL(213),      // 取消群组呼叫
MULTI_PARTY_HANGUP(214),      // 挂断群组呼叫
MULTI_PARTY_MEMBER_JOIN(215), // 成员加入通话
MULTI_PARTY_MEMBER_LEAVE(216), // 成员离开通话
MULTI_PARTY_MEMBER_STATE_CHANGE(217), // 成员状态变更
MULTI_PARTY_SPEAKING_STATE(218), // 发言状态变更
MULTI_PARTY_QUALITY_REPORT(219); // 网络质量报告
```

#### 3.1.2 信令数据结构
```java
public class MultiPartySignaling {
    public String type;           // 信令类型
    public String roomID;         // 通话房间ID
    public String inviterID;      // 发起人ID
    public List<String> inviteeList; // 被邀请人列表
    public String memberID;       // 成员ID（用于成员相关信令）
    public CallMemberState memberState; // 成员状态
    public boolean isVideoCall;  // 是否为视频通话
    public long timestamp;       // 时间戳
    public String messageId;     // 消息唯一ID（去重用）
    public Map<String, Object> extraData; // 扩展数据
}
```

### 3.2 核心状态管理优化

#### 3.2.1 成员状态枚举
```java
public enum CallMemberState {
    IDLE("idle"),                 // 空闲状态
    INVITING("inviting"),        // 邀请中
    RINGING("ringing"),          // 响铃中
    CONNECTING("connecting"),     // 连接中
    CONNECTED("connected"),       // 已连接
    DISCONNECTED("disconnected"), // 已断开
    REJECTED("rejected"),        // 已拒绝
    TIMEOUT("timeout"),          // 超时
    AUDIO_ONLY("audio_only");    // 仅音频模式

    private final String value;
    
    // 状态转换验证
    public boolean canTransitionTo(CallMemberState target) {
        switch (this) {
            case IDLE: return target == INVITING;
            case INVITING: return target == RINGING || target == TIMEOUT || target == REJECTED;
            case RINGING: return target == CONNECTING || target == REJECTED || target == TIMEOUT;
            case CONNECTING: return target == CONNECTED || target == DISCONNECTED;
            case CONNECTED: return target == DISCONNECTED || target == AUDIO_ONLY;
            default: return false;
        }
    }
}
```

#### 3.2.2 群组通话成员管理
```java
public class GroupCallMember {
    private String userId;
    private String nickname;
    private String avatar;
    private CallMemberState state = CallMemberState.IDLE;
    private boolean isCameraEnabled = true;
    private boolean isMicEnabled = true;
    private boolean isSpeaking = false;
    private int networkQuality = 5; // 1-5星网络质量
    private long stateTimestamp;
    private TextureViewRenderer videoRenderer; // 视频渲染器
    
    public boolean setState(CallMemberState newState) {
        if (state.canTransitionTo(newState)) {
            this.state = newState;
            this.stateTimestamp = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}
```

### 3.3 核心业务逻辑扩展

#### 3.3.1 CallingVM扩展
```java
public class CallingVM extends BaseViewModel {
    // 现有字段保持不变...
    
    // 新增群组通话相关字段
    public List<GroupCallMember> groupMembers = new ArrayList<>();
    public String currentSpeaker = "";  // 当前发言人ID
    public boolean isGroupCall = false; // 是否群组通话
    
    // 业界最佳实践组件
    private final SignalingDeduplicator deduplicator = new SignalingDeduplicator();
    private final CallErrorRecovery errorRecovery = new CallErrorRecovery();
    private final CallQualityMonitor qualityMonitor = new CallQualityMonitor();
    private final VideoResourcePool resourcePool = new VideoResourcePool();
    
    // 群组通话核心方法
    public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
        // 1. 创建LiveKit房间
        // 2. 发送群组邀请信令
        // 3. 初始化成员状态
        // 4. 显示群组通话界面
    }
    
    public void handleGroupSignaling(MultiPartySignaling signaling) {
        // 使用信令去重
        deduplicator.handleSignalingWithDeduplication(signaling);
    }
    
    public void updateMemberState(String userId, CallMemberState newState) {
        GroupCallMember member = findMember(userId);
        if (member != null && member.setState(newState)) {
            notifyUI();
            // 状态变更日志
            L.d("成员状态变更: " + userId + " -> " + newState);
        }
    }
}
```

#### 3.3.2 CallDialog扩展
```java
public class CallDialog extends BaseDialog {
    // 现有字段保持不变...
    
    // 新增群组通话UI组件
    private RecyclerView memberGridView;
    private GridLayoutManager gridLayoutManager;
    private GroupMemberAdapter memberAdapter;
    private TextView speakerIndicator;
    
    private void setupGroupCallUI() {
        if (callingVM.isGroupCall) {
            // 切换到群组通话布局
            setContentView(R.layout.dialog_group_call);
            initGroupViewComponents();
            setupMemberGrid();
        } else {
            // 使用现有1v1布局
            setContentView(R.layout.dialog_call);
        }
    }
    
    private void setupMemberGrid() {
        // 九宫格布局：1人1x1，2-4人2x2，5-9人3x3
        int memberCount = callingVM.groupMembers.size();
        int spanCount = memberCount <= 1 ? 1 : (memberCount <= 4 ? 2 : 3);
        
        gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        memberGridView.setLayoutManager(gridLayoutManager);
        
        memberAdapter = new GroupMemberAdapter(callingVM.groupMembers);
        memberGridView.setAdapter(memberAdapter);
    }
}
```

### 3.4 LiveKit集成优化

#### 3.4.1 CallViewModel扩展
```java
class CallViewModel {
    // 现有字段保持不变...
    
    // 群组通话房间管理
    fun connectToGroupRoom(roomUrl: String, token: String, memberIds: List<String>) {
        viewModelScope.launch {
            try {
                room.connect(roomUrl, token)
                
                // 监听参与者变化
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.ParticipantConnected -> {
                            handleParticipantJoined(event.participant)
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            handleParticipantLeft(event.participant)
                        }
                        is RoomEvent.DataPacketReceived -> {
                            handleDataPacket(event)
                        }
                    }
                }
            } catch (e: Exception) {
                L.e("群组房间连接失败", e)
                onConnectionResult(Result.failure(e))
            }
        }
    }
    
    private fun handleParticipantJoined(participant: RemoteParticipant) {
        // 更新成员状态为CONNECTED
        callingVM.updateMemberState(participant.identity, CallMemberState.CONNECTED)
        
        // 分配视频渲染器
        val renderer = callingVM.resourcePool.acquireRenderer(participant.identity, this)
        if (renderer != null) {
            participant.videoTracks.forEach { track ->
                track.track?.addRenderer(renderer)
            }
        }
    }
}
```

## 4. 业界最佳实践融入

### 4.1 资源管理优化
```java
public class VideoResourcePool {
    private static final int MAX_TEXTURE_RENDERERS = 12;
    private Queue<TextureViewRenderer> rendererPool = new LinkedList<>();
    private Map<String, TextureViewRenderer> activeRenderers = new HashMap<>();
    private WeakHashMap<Object, List<TextureViewRenderer>> ownerMap = new WeakHashMap<>();
    
    public TextureViewRenderer acquireRenderer(String participantId, Object owner) {
        TextureViewRenderer renderer = rendererPool.poll();
        if (renderer == null) {
            renderer = new TextureViewRenderer(BaseApp.inst());
            callViewModel.getRoom().initVideoRenderer(renderer);
        }
        
        activeRenderers.put(participantId, renderer);
        
        // 内存泄漏防护：记录owner，自动释放
        ownerMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(renderer);
        
        return renderer;
    }
    
    public void releaseRenderer(String participantId) {
        TextureViewRenderer renderer = activeRenderers.remove(participantId);
        if (renderer != null) {
            renderer.clearImage();
            rendererPool.offer(renderer);
        }
    }
}
```

### 4.2 信令去重机制
```java
public class SignalingDeduplicator {
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5分钟
    private ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();
    
    public boolean isDuplicate(String messageId) {
        Long timestamp = processedMessages.get(messageId);
        return timestamp != null && (System.currentTimeMillis() - timestamp) < CLEANUP_INTERVAL_MS;
    }
    
    public void handleSignalingWithDeduplication(MultiPartySignaling signaling) {
        String messageId = signaling.getMessageId();
        
        if (isDuplicate(messageId)) {
            L.w("重复信令，忽略处理: " + messageId);
            return;
        }
        
        markProcessed(messageId);
        
        try {
            handleSignaling(signaling);
        } catch (Exception e) {
            // 处理失败时移除去重标记，允许重试
            processedMessages.remove(messageId);
            throw e;
        }
    }
}
```

### 4.3 网络质量监控
```java
public class CallQualityMonitor {
    public static class QualityMetrics {
        public long rtt;              // 往返时延
        public double packetLoss;     // 丢包率  
        public double jitter;         // 抖动
        public int bitrate;           // 码率
        public int networkQuality;    // 网络质量（1-5星）
        public long timestamp;
    }
    
    private Timer qualityTimer;
    
    public void startMonitoring() {
        qualityTimer = new Timer();
        qualityTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                QualityMetrics metrics = collectMetrics();
                
                // 更新UI网络质量指示器
                for (GroupCallMember member : callingVM.groupMembers) {
                    member.setNetworkQuality(calculateQualityForMember(member.getUserId()));
                }
                
                // 自动降级策略
                if (metrics.packetLoss > 0.1 || metrics.rtt > 500) {
                    suggestQualityDowngrade();
                }
            }
        }, 0, 2000);
    }
}
```

## 5. UI设计方案

### 5.1 九宫格布局策略
```
1人: 1x1 全屏显示
2-4人: 2x2 网格
5-9人: 3x3 网格
```

### 5.2 成员状态指示
```xml
<!-- item_member_renderer.xml 增强版 -->
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- 视频渲染器 -->
    <org.webrtc.SurfaceViewRenderer
        android:id="@+id/videoRenderer"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    
    <!-- 成员信息叠加层 -->
    <LinearLayout
        android:id="@+id/memberInfoOverlay"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:orientation="horizontal"
        android:background="#80000000"
        android:padding="8dp">
        
        <!-- 网络质量指示器 -->
        <ImageView
            android:id="@+id/networkQuality"
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:src="@mipmap/ic_net_excellent" />
            
        <!-- 成员昵称 -->
        <TextView
            android:id="@+id/memberName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:layout_marginStart="4dp" />
            
        <!-- 麦克风状态 -->
        <ImageView
            android:id="@+id/micStatus"
            android:layout_width="16dp" 
            android:layout_height="16dp"
            android:src="@mipmap/ic_mic_on" />
    </LinearLayout>
    
    <!-- 发言状态指示环 -->
    <View
        android:id="@+id/speakingIndicator"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/speaking_ring"
        android:visibility="gone" />
        
</RelativeLayout>
```

## 6. 测试策略

### 6.1 TDD开发流程
```
1. 编写单元测试 -> 2. 运行测试（失败）-> 3. 编写最小代码使测试通过 -> 4. 重构代码 -> 5. 重复
```

### 6.2 测试覆盖定义
- **单元测试（70%）**: 状态机转换、信令处理、资源管理
- **集成测试（20%）**: LiveKit集成、IM信令集成  
- **端到端测试（10%）**: 完整通话流程

### 6.3 关键测试用例
```java
// 状态管理测试
@Test
public void testCallMemberStateTransition() {
    GroupCallMember member = new GroupCallMember();
    assertTrue(member.setState(CallMemberState.INVITING));
    assertTrue(member.setState(CallMemberState.RINGING));
    assertFalse(member.setState(CallMemberState.IDLE)); // 非法转换
}

// 信令去重测试
@Test
public void testSignalingDeduplication() {
    SignalingDeduplicator deduplicator = new SignalingDeduplicator();
    String messageId = "test_message_123";
    
    assertFalse(deduplicator.isDuplicate(messageId));
    deduplicator.markProcessed(messageId);
    assertTrue(deduplicator.isDuplicate(messageId));
}

// 资源池测试
@Test
public void testVideoResourcePool() {
    VideoResourcePool pool = new VideoResourcePool();
    TextureViewRenderer renderer1 = pool.acquireRenderer("user1", this);
    assertNotNull(renderer1);
    
    pool.releaseRenderer("user1");
    TextureViewRenderer renderer2 = pool.acquireRenderer("user2", this);
    assertEquals(renderer1, renderer2); // 应该复用同一个renderer
}
```

## 7. 性能和稳定性

### 7.1 内存管理
- TextureViewRenderer对象池复用
- 弱引用防止内存泄漏
- 及时释放音视频资源

### 7.2 网络优化
- 信令去重减少重复处理
- 断路器模式处理网络异常
- 指数退避重试策略

### 7.3 用户体验优化  
- 网络质量实时指示
- 智能音视频降级
- 发言状态可视化

## 8. 兼容性和扩展性

### 8.1 向后兼容
- 现有1v1通话功能完全不受影响
- 信令协议向前兼容
- UI组件渐进式增强

### 8.2 未来扩展
- 屏幕共享功能
- 举手发言模式
- 会议录制功能
- 美颜滤镜集成

## 9. 风险评估

### 9.1 技术风险
- **中等**: LiveKit多人房间网络稳定性
- **低**: 现有代码重构风险
- **低**: UI性能影响

### 9.2 缓解策略
- 充分的集成测试覆盖
- 渐进式发布策略
- 完整的回滚机制

---

**本设计基于现有OpenIM Android项目架构，在保持系统稳定性的前提下，最大化复用现有资源，融入业界最佳实践，确保群组音视频功能的高质量实现。**