# 群组音视频通话功能技术设计文档

## 1. 项目概述

### 1.1 功能定义
实现支持最多9人的群组音视频通话功能，基于现有OpenIM Android项目架构，使用LiveKit作为音视频引擎。

### 1.2 业务目标
- 支持群聊中发起音视频通话
- 支持成员中途加入/退出
- 提供与微信群通话相似的用户体验
- 保证通话质量和稳定性

## 2. 系统架构设计

### 2.1 整体架构图
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   群聊界面      │───▶│  群组通话界面    │───▶│   LiveKit房间   │
│  ChatActivity   │    │ GroupCallDialog │    │                │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       ▲
         ▼                       ▼                       │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   业务逻辑层     │    │   状态管理器     │    │   视频渲染层     │
│   CallingVM     │◀──▶│GroupCallState   │───▶│VideoResourcePool│
└─────────────────┘    │    Manager      │    └─────────────────┘
         │              └─────────────────┘
         ▼
┌─────────────────┐
│   信令服务层     │
│ CallingService  │
└─────────────────┘
```

### 2.2 核心组件职责

#### 前端UI层
- **ChatActivity**: 群聊界面，提供发起群组通话入口
- **GroupCallDialog**: 群组通话主界面，九宫格视频布局
- **GroupMemberAdapter**: 成员视频网格适配器

#### 业务逻辑层  
- **CallingVM**: 通话业务逻辑控制器
- **GroupCallStateManager**: 群组通话状态管理器
- **CallViewModel**: LiveKit房间管理协调器

#### 服务层
- **CallingService**: 信令发送和接收服务
- **VideoResourcePool**: 视频渲染资源池管理

## 3. 业务特点与设计细节

### 3.1 群组音视频的业务特点

#### 与单人通话的核心区别

**环境上下文：**
- 单人通话：在单聊会话中发起，明确的对方
- 群组通话：在群聊环境中发起，需要从多个成员中选择

**权限控制：**
- 单人通话：无权限限制，双方都可发起
- 群组通话：受群组设置影响（群主、管理员、普通成员权限）

**成员状态：**
- 单人通话：只需关注对方在线状态
- 群组通话：需关注多个成员的在线状态、群内状态

#### 群组环境的特殊考虑

**群组状态检查：**
```java
public class GroupCallValidator {
    public static boolean canInitiateGroupCall(String groupId, String userId) {
        // 1. 检查群组是否存在
        if (!isGroupExists(groupId)) {
            return false;
        }
        
        // 2. 检查用户是否在群内
        if (!isUserInGroup(groupId, userId)) {
            return false;
        }
        
        // 3. 检查群组通话权限设置
        GroupSettings settings = getGroupSettings(groupId);
        if (!settings.isCallAllowed(userId)) {
            return false;
        }
        
        // 4. 检查群组是否被解散
        if (isGroupDismissed(groupId)) {
            return false;
        }
        
        return true;
    }
}
```

**成员选择逻辑：**
```java
public class GroupMemberSelector {
    public static List<GroupMember> getAvailableMembers(String groupId, String inviterId) {
        List<GroupMember> availableMembers = new ArrayList<>();
        List<GroupMember> allMembers = getGroupMembers(groupId);
        
        for (GroupMember member : allMembers) {
            // 排除发起人自己
            if (member.getUserId().equals(inviterId)) {
                continue;
            }
            
            // 排除被禁言的成员（根据业务规则）
            if (member.isMuted() && !canInviteMutedMembers()) {
                continue;
            }
            
            // 排除不在线的成员（可选）
            if (isOnlineCheckEnabled() && !member.isOnline()) {
                continue;
            }
            
            availableMembers.add(member);
        }
        
        return availableMembers;
    }
}
```

### 3.2 群组通话发起流程（重新设计）

#### 前端流程

**发起方流程：**
```
用户点击群聊通话按钮
         ↓
ChatActivity.onGroupCallClick()
         ↓
弹出成员选择对话框
         ↓
用户选择通话成员（可选全选）
         ↓
用户点击"确认发起"按钮
         ↓
关闭成员选择对话框
         ↓
立即显示GroupCallDialog（九宫格界面，显示邀请中状态）
         ↓
同时调用CallingVM.initiateGroupCall()发送信令
         ↓
后台异步发送信令给所有选中成员
         ↓
实时更新成员状态：邀请中 → 响铃 → 已接听/已拒绝
```

**接收方流程：**
```
接收到群组通话信令
         ↓
显示来电界面（GroupCallDialog来电状态）
         ↓
用户选择接受/拒绝
         ↓
如果接受：显示GroupCallDialog（九宫格布局）
         ↓
连接LiveKit房间，显示所有参与者视频
```

#### 后端流程（参考单人音视频模式）

**发起方后端流程：**
```java
// 1. 发起群组通话 - 参考单人通话的 call() 方法模式
public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
    // 1.1 构建标准群组信令（复用OpenIM SDK标准接口）
    SignalingInfo groupSignalingInfo = buildGroupSignalingInfo(groupId, memberIds, isVideo);
    
    // 1.2 更新状态管理器
    updateSignalingInfo(groupSignalingInfo);
    
    // 1.3 发送邀请信令给所有成员 - 复用单人通话的sendSignaling模式
    sendSignaling(Constants.MsgType.callingInvite, groupSignalingInfo, new OnMsgSendCallback() {
        @Override
        public void onSuccess(Message message) {
            // 1.4 获取Token并连接房间 - 复用单人通话的getTokenAndConnectRoom方法
            getTokenAndConnectRoom(groupSignalingInfo, new OnBase<SignalingCertificate>() {
                @Override
                public void onSuccess(SignalingCertificate data) {
                    // 1.5 连接房间 - 复用connectToRoom方法
                    connectToRoom(data);
                }
            });
        }
    });
}

// 2. 连接LiveKit房间 - 复用单人通话的connectToRoom方法
// 无需修改现有connectToRoom方法，LiveKit会自动处理多人场景
private void connectToRoom(SignalingCertificate data) {
    // 使用现有的callViewModel.connectToRoom方法
    callViewModel.connectToRoom(data.getLiveURL(), data.getToken(), new Continuation<Unit>() {
        @Override
        public void resumeWith(@NonNull Object o) {
            // 复用现有逻辑：设置扬声器、本地视频等
            setSpeakerphoneOn(true);
            if (!isVideoCalls) callViewModel.setCameraEnabled(false);
            // ... 其他现有逻辑保持不变
        }
    });
}
```

**接收方后端流程：**
```java
// 1. 接收群组通话邀请 - 复用现有handleSignaling方法
// 在CallingService中接收信令后，调用现有的accept/reject流程

// 2. 接受群组通话 - 复用现有signalingAccept方法
// 现有的signalingAccept方法已支持群组通话，无需修改
public void signalingAccept(SignalingInfo signalingInfo, OnBase onBase) {
    // 使用现有实现，自动支持群组通话
    sendSignaling(Constants.MsgType.callingAccept, signalingInfo, new OnMsgSendCallback() {
        // ... 现有实现保持不变
    });
}
```

**业务逻辑分析：群组音视频 vs 单人音视频**

从业务角度看，群组音视频和单人音视频是两个不同的业务领域：

| 维度 | 单人音视频 | 群组音视频 | 复用适合性 |
|------|-----------|-----------|----------|
| **邀请机制** | 1对1直接邀请 | 1对多批量邀请，需成员选择 | ❌ 业务逻辑不同 |
| **状态管理** | 2个状态（自己+对方） | N个独立成员状态管理 | ❌ 复杂度指数增长 |
| **UI布局** | 2分屏固定布局 | 九宫格动态布局 | ❌ 交互模式不同 |
| **成员管理** | 无需管理 | 复杂的成员进出管理 | ❌ 功能需求不同 |
| **用户期望** | 简单快速连接 | 成员可见、状态控制 | ❌ 用户心智不同 |

**业界最佳实践对比：**
- **微信**: 单人通话和群通话完全独立的系统
- **钉钉**: 区分"通话"和"会议"两个不同产品功能
- **Zoom/Teams**: 直接区分为不同的业务模块

**MVP版本设计原则：**
1. **快速上线原则**: 优先实现核心功能，复杂功能后续迭代
2. **最小可行原则**: 4人群通话，基础音视频控制
3. **复用优先原则**: 最大化复用现有单人通话架构
4. **简化处理原则**: 避免复杂的权限控制和状态管理
5. **稳定第一原则**: 确保基础功能稳定可用

**共享架构设计：**
```
┌─────────────────┐    ┌─────────────────┐
│   单人通话业务   │    │   群组通话业务   │
│  SingleCallVM   │    │  GroupCallVM    │
└─────────────────┘    └─────────────────┘
         │                       │
         └───────┬───────────────┘
                 ▼
    ┌─────────────────────────┐
    │     共享技术组件        │
    │  CallViewModel(LiveKit) │
    │  AudioManager          │
    │  VideoRenderer         │
    └─────────────────────────┘
```

**SignalingInfo扩展设计（根据群组音视频需要）：**

现有SignalingInvitationInfo结构已支持群组通话必需字段：
```java
public class SignalingInvitationInfo {
    // 基础字段（已存在）
    private String inviterUserID;           // 发起人
    private List<String> inviteeUserIDList; // 被邀请用户列表
    private String groupID;                 // 群组ID（群组通话必需）
    private ConversationType sessionType;   // 会话类型（GROUP_CHAT/SINGLE_CHAT）
    private String mediaType;               // 音频/视频类型
    private String roomID;                  // LiveKit房间ID
    private long timeout;                   // 超时时间
    private long initiateTime;              // 发起时间
    
    // 群组音视频可选扩展字段（在customData中存储）
    private Map<String, Object> customData; // 自定义数据
}
```

如需扩展字段，可在customData中添加：
```java
// 扩展字段示例（可选）
private static final String CUSTOM_MAX_PARTICIPANTS = "maxParticipants";
private static final String CUSTOM_CALL_MODE = "callMode";           // 通话模式
private static final String CUSTOM_QUALITY_LEVEL = "qualityLevel";   // 画质级别

// 使用示例
customData.put(CUSTOM_MAX_PARTICIPANTS, 9);
customData.put(CUSTOM_CALL_MODE, "group_video_call");
customData.put(CUSTOM_QUALITY_LEVEL, "720p");
```

**扩展原则：**
1. **保持兼容性**: 不修改OpenIM SDK核心结构
2. **按需扩展**: 只在确实需要时才扩展customData
3. **向前兼容**: 新字段不影响现有功能

### 3.2 成员接收邀请流程



### 3.3 成员状态同步流程

#### 状态定义
```java
public enum CallMemberState {
    IDLE("空闲"),
    INVITING("邀请中"),
    RINGING("响铃"),
    CONNECTING("连接中"),
    CONNECTED("已连接"),
    SPEAKING("发言中"),
    DISCONNECTED("已断开"),
    REJECTED("已拒绝"),
    TIMEOUT("超时");
}
```

#### 状态同步机制
```java
// 1. IM信令状态更新
private void handleGroupCallSignaling(SignalingInfo signalingInfo) {
    GroupCallStateManager stateManager = GroupCallStateManager.getInstance();
    String userId = signalingInfo.getInviterUserID();
    
    switch (signalingInfo.getInvitation().getCmd()) {
        case Constants.CALLING_ACCEPT:
            stateManager.updateMemberState(userId, CallMemberState.CONNECTING);
            break;
        case Constants.CALLING_REJECT:
            stateManager.updateMemberState(userId, CallMemberState.REJECTED);
            break;
        case Constants.CALLING_HANGUP:
            stateManager.updateMemberState(userId, CallMemberState.DISCONNECTED);
            break;
    }
}

// 2. LiveKit参与者状态更新
private void handleGroupParticipantsChanged(List<Participant> participants) {
    GroupCallStateManager stateManager = GroupCallStateManager.getInstance();
    
    for (Participant participant : participants) {
        if (participant instanceof RemoteParticipant) {
            // 参与者加入LiveKit房间，更新为已连接
            stateManager.updateMemberState(participant.getIdentity(), CallMemberState.CONNECTED);
        }
    }
}
```

### 3.4 视频渲染绑定流程

#### 渲染绑定策略
```java
public class GroupMemberAdapter {
    @Override
    public void onBindViewHolder(MemberViewHolder holder, int position) {
        GroupCallMember member = members.get(position);
        
        // 1. 绑定成员基础信息
        holder.bindMemberInfo(member);
        
        // 2. 根据状态处理视频渲染
        switch (member.getState()) {
            case CONNECTED:
                // 延迟绑定视频流
                holder.itemView.post(() -> bindVideoRenderer(holder, member));
                break;
            case CONNECTING:
                holder.showConnectingState();
                break;
            case INVITING:
                holder.showInvitingState();
                break;
            default:
                holder.showPlaceholder(member);
                break;
        }
    }
    
    private void bindVideoRenderer(MemberViewHolder holder, GroupCallMember member) {
        // 1. 从资源池获取渲染器
        TextureViewRenderer renderer = resourcePool.acquireRenderer(member.getUserId(), holder);
        
        if (renderer != null) {
            // 2. 绑定到ViewHolder
            holder.bindVideoRenderer(renderer);
            
            // 3. 请求CallViewModel绑定视频流
            callViewModel.bindGroupMemberVideo(member.getUserId(), renderer);
        }
    }
}
```

### 3.5 成员中途加入/离开流程

#### 中途加入流程
```java
public void handleMemberJoinMidway(String userId) {
    GroupCallStateManager stateManager = GroupCallStateManager.getInstance();
    
    // 1. 添加新成员到状态管理器
    stateManager.addMember(userId, CallMemberState.CONNECTING);
    
    // 2. 发送当前通话信息给新成员
    sendCurrentCallStateToNewMember(userId);
    
    // 3. 通知其他成员有新人加入
    notifyOtherMembersAboutNewJoin(userId);
    
    // 4. UI会通过观察者自动更新网格布局
}
```

#### 成员离开流程
```java
public void handleMemberLeave(String userId, String reason) {
    GroupCallStateManager stateManager = GroupCallStateManager.getInstance();
    
    // 1. 更新成员状态
    stateManager.updateMemberState(userId, CallMemberState.DISCONNECTED);
    
    // 2. 清理视频资源
    resourcePool.releaseRenderer(userId);
    
    // 3. 检查通话是否应该结束
    if (stateManager.getConnectedMemberCount() <= 1) {
        endGroupCallDueToInsufficientMembers();
    }
    
    // 4. UI自动调整网格布局
}
```

## 4. 数据结构设计

### 4.1 群组通话成员
```java
public class GroupCallMember {
    private String userId;           // 用户ID
    private String nickname;         // 昵称
    private String avatar;           // 头像URL
    private CallMemberState state;   // 当前状态
    private boolean isCameraEnabled; // 摄像头开关
    private boolean isMicEnabled;    // 麦克风开关
    private boolean isSpeaking;      // 是否正在发言
    private long stateTimestamp;     // 状态更新时间
    private TextureViewRenderer videoRenderer; // 视频渲染器
    
    // 状态转换验证
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

### 4.2 群组通话状态管理器
```java
public class GroupCallStateManager {
    private static volatile GroupCallStateManager instance;
    
    // 群组通话基础信息
    private boolean isGroupCall = false;
    private String groupId;
    private String groupRoomId;
    private String currentSpeaker;
    
    // 成员管理（线程安全）
    private final CopyOnWriteArrayList<GroupCallMember> groupMembers = new CopyOnWriteArrayList<>();
    
    // 观察者列表（分业务层和UI层）
    private final CopyOnWriteArrayList<StateObserver> businessObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<StateObserver> uiObservers = new CopyOnWriteArrayList<>();
    
    // 状态观察者接口
    public interface StateObserver {
        void onMemberStateChanged(GroupCallMember member);
        void onMemberAdded(GroupCallMember member);
        void onMemberRemoved(GroupCallMember member);
        void onCurrentSpeakerChanged(String oldSpeaker, String newSpeaker);
        void onCallEnded(String reason);
    }
}
```

## 5. UI界面设计

### 5.1 成员选择对话框

#### 布局设计
```xml
<!-- dialog_group_member_selector.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">
    
    <!-- 标题栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">
        
        <TextView
            android:text="选择通话成员"
            android:textSize="18sp"
            android:textStyle="bold"/>
            
        <View android:layout_weight="1"/>
        
        <TextView
            android:id="@+id/selectAllTv"
            android:text="全选"
            android:textColor="@color/theme_color"
            android:clickable="true"/>
            
    </LinearLayout>
    
    <!-- 成员列表 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/memberRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="300dp"
        android:layout_marginTop="16dp"/>
    
    <!-- 底部按钮 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:orientation="horizontal">
        
        <Button
            android:id="@+id/cancelBtn"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="取消"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>
            
        <Space
            android:layout_width="16dp"
            android:layout_height="1dp"/>
            
        <Button
            android:id="@+id/confirmBtn"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="确认发起"
            android:enabled="false"/>
            
    </LinearLayout>
    
</LinearLayout>
```

#### 成员项布局
```xml
<!-- item_member_selector.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp">
    
    <CheckBox
        android:id="@+id/memberCheckbox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
        
    <ImageView
        android:id="@+id/memberAvatar"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="12dp"/>
        
    <TextView
        android:id="@+id/memberName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textSize="16sp"/>
        
</LinearLayout>
```

### 5.2 群组通话界面布局

#### 九宫格布局策略
```
1人: 1x1 全屏显示
2-4人: 2x2 网格布局  
5-9人: 3x3 网格布局
```

#### 界面组件设计
```xml
<!-- dialog_group_call.xml -->
<RelativeLayout>
    <!-- 顶部信息栏 -->
    <LinearLayout
        android:id="@+id/topInfoLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
        
        <TextView android:id="@+id/groupNameTv"/>
        <TextView android:id="@+id/memberCountTv"/>
        <TextView android:id="@+id/timeTv"/>
        
    </LinearLayout>
    
    <!-- 视频网格 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/viewRenderers"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_below="@id/topInfoLayout"
        android:layout_above="@id/controlPanel"/>
    
    <!-- 底部控制栏 -->
    <LinearLayout
        android:id="@+id/controlPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true">
        
        <ImageButton android:id="@+id/micToggle"/>
        <ImageButton android:id="@+id/cameraToggle"/>
        <ImageButton android:id="@+id/speakerToggle"/>
        <ImageButton android:id="@+id/hangupBtn"/>
        
    </LinearLayout>
    
</RelativeLayout>
```

### 5.2 成员视频项布局
```xml
<!-- item_group_member.xml -->
<RelativeLayout>
    <!-- 视频渲染器 -->
    <org.webrtc.SurfaceViewRenderer
        android:id="@+id/videoRenderer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
    
    <!-- 成员信息覆盖层 -->
    <LinearLayout
        android:id="@+id/memberInfoOverlay"
        android:layout_alignParentBottom="true"
        android:background="#80000000">
        
        <TextView android:id="@+id/memberName"/>
        <ImageView android:id="@+id/micStatus"/>
        <ImageView android:id="@+id/networkStatus"/>
        
    </LinearLayout>
    
    <!-- 发言状态指示框 -->
    <View
        android:id="@+id/speakingIndicator"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/speaking_border"
        android:visibility="gone"/>
        
</RelativeLayout>
```

## 6. LiveKit集成要求

### 6.1 房间配置要求
- **房间类型**: 多人会议房间
- **最大参与者**: 9人
- **音频编解码**: Opus
- **视频编解码**: VP8/H.264
- **分辨率**: 360p（默认），720p（高质量）

### 6.2 Token管理要求
- 房间Token有效期：2小时
- 支持动态刷新Token
- 权限控制：发布音视频流权限

### 6.3 网络适应性要求
- 自动码率调整
- 网络质量监控
- 弱网络环境降级策略

## 7. 异常处理设计

### 7.1 信令去重机制
```java
public class SignalingDeduplicator {
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5分钟
    private final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();
    
    public boolean shouldProcess(String messageId) {
        Long timestamp = processedMessages.get(messageId);
        long now = System.currentTimeMillis();
        
        // 清理过期记录
        if (now % 10000 == 0) {
            processedMessages.entrySet().removeIf(
                entry -> now - entry.getValue() > CLEANUP_INTERVAL_MS
            );
        }
        
        // 检查重复
        if (timestamp != null && now - timestamp < CLEANUP_INTERVAL_MS) {
            return false; // 重复，不处理
        }
        
        processedMessages.put(messageId, now);
        return true; // 首次，需处理
    }
}
```

### 7.2 错误恢复机制
```java
public class CallErrorRecovery {
    private static final int MAX_RETRY_COUNT = 3;
    
    // 指数退避重试
    public void retryWithBackoff(String operation, Runnable task, int attemptCount) {
        if (attemptCount >= MAX_RETRY_COUNT) {
            L.e(TAG, "操作失败，超过最大重试次数: " + operation);
            return;
        }
        
        long delay = 2000 * (long) Math.pow(2, attemptCount);
        Common.UIHandler.postDelayed(() -> {
            try {
                task.run();
            } catch (Exception e) {
                retryWithBackoff(operation, task, attemptCount + 1);
            }
        }, delay);
    }
}
```

### 7.3 资源管理
```java
public class VideoResourcePool {
    private static final int MAX_RENDERERS = 12; // 9人+3预留
    
    // 资源分配
    public TextureViewRenderer acquireRenderer(String participantId, Object owner) {
        try {
            TextureViewRenderer renderer = getOrCreateRenderer();
            if (renderer == null) {
                renderer = reclaimOldestRenderer(); // 强制回收
            }
            
            activeRenderers.put(participantId, renderer);
            ownerMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(renderer);
            
            return renderer;
        } catch (OutOfMemoryError e) {
            emergencyCleanup();
            throw new RuntimeException("视频渲染器分配失败", e);
        }
    }
}
```

## 8. 性能监控

### 8.1 关键性能指标
- 通话建立时间 < 3秒
- 视频渲染延迟 < 200ms
- 内存使用峰值 < 200MB
- CPU使用率 < 30%

### 8.2 监控实现
```java
// 简单性能监控
public class CallPerformanceMonitor {
    public void recordCallStartTime() {
        // 记录通话开始时间
    }
    
    public void recordFirstVideoFrame() {
        // 记录首帧渲染时间
    }
    
    public void monitorMemoryUsage() {
        // 监控内存使用
    }
}
```

## 9. 测试设计

### 9.1 单元测试
- **状态管理测试**: GroupCallStateManager状态转换
- **信令处理测试**: 信令去重、异常处理
- **资源管理测试**: VideoResourcePool分配回收

### 9.2 集成测试
- **LiveKit集成测试**: 房间连接、参与者管理
- **UI集成测试**: 界面响应、状态同步
- **端到端测试**: 完整通话流程

### 9.3 压力测试
- **多人并发测试**: 9人同时通话
- **网络异常测试**: 弱网络环境
- **长时间通话测试**: 2小时持续通话

## 10. 验收标准

### 10.1 功能验收标准

#### 基础功能
- [ ] **群组通话发起**: 从群聊界面能成功发起音视频通话
- [ ] **成员选择界面**: 点击通话按钮后能弹出成员选择对话框
- [ ] **成员选择功能**: 能勾选/取消勾选成员，支持全选功能
- [ ] **确认发起机制**: 只有选中成员后才能点击"确认发起"按钮
- [ ] **成员邀请**: 能选择指定成员或全员参与通话
- [ ] **来电接听**: 被邀请成员能收到来电通知并选择接受/拒绝
- [ ] **视频显示**: 九宫格布局正确显示所有参与者视频
- [ ] **音频通信**: 所有参与者音频清晰无杂音
- [ ] **媒体控制**: 麦克风、摄像头、扬声器开关正常工作

#### 高级功能  
- [ ] **成员中途加入**: 通话过程中新成员能正常加入
- [ ] **成员中途离开**: 成员离开后UI自动调整，资源正确释放
- [ ] **状态同步**: 所有成员状态变化实时同步到其他参与者
- [ ] **发言状态**: 能正确识别并显示当前发言人
- [ ] **网络适应**: 弱网络环境下自动降级，恢复后自动升级

### 10.2 性能验收标准

#### 响应性能
- [ ] **通话建立时间**: 从发起到连通 < 3秒
- [ ] **视频首帧时间**: 视频开始显示 < 2秒  
- [ ] **UI响应时间**: 按钮点击响应 < 100ms
- [ ] **状态同步延迟**: 成员状态变化同步 < 500ms

#### 资源使用
- [ ] **内存使用**: 9人通话内存峰值 < 200MB
- [ ] **CPU使用**: 通话过程CPU使用率 < 30%
- [ ] **电池消耗**: 1小时通话耗电 < 25%
- [ ] **网络流量**: 视频通话码率在合理范围内

### 10.3 稳定性验收标准

#### 异常处理
- [ ] **网络中断恢复**: 网络中断后能自动重连
- [ ] **应用崩溃恢复**: 应用意外退出后能恢复通话状态  
- [ ] **信令重复处理**: 重复信令不会造成异常
- [ ] **资源泄漏检测**: 长时间使用无内存泄漏

#### 兼容性
- [ ] **设备兼容**: 支持Android 7.0+主流设备
- [ ] **分辨率适配**: 支持各种屏幕分辨率正确显示
- [ ] **权限处理**: 音视频权限申请和处理正确
- [ ] **后台处理**: 切换到后台通话继续，返回前台恢复UI

### 10.4 用户体验验收标准

#### 界面体验
- [ ] **UI美观性**: 界面设计符合Material Design规范
- [ ] **操作便捷性**: 核心操作不超过2步完成
- [ ] **状态提示**: 各种状态有明确的视觉反馈
- [ ] **错误提示**: 异常情况有友好的错误提示

#### 交互体验
- [ ] **响应流畅**: 界面切换和动画流畅无卡顿
- [ ] **手势支持**: 支持双击全屏、滑动切换等手势
- [ ] **音频体验**: 音频质量清晰，无回音和杂音
- [ ] **视频体验**: 视频清晰度合适，帧率稳定

### 10.5 验收测试用例

#### 基础流程测试
```
测试用例1: 群组通话发起
前置条件: 用户在群聊界面
测试步骤:
1. 点击群聊右上角通话按钮
2. 选择视频通话
3. 弹出成员选择对话框
4. 勾选邀请成员（可选择1人或多人）
5. 点击"确认发起"按钮
预期结果: 成功进入群组通话界面（九宫格布局），显示邀请状态

测试用例2: 接受群组通话
前置条件: 收到群组通话邀请
测试步骤:
1. 收到来电通知
2. 点击接受按钮
3. 等待连接
预期结果: 成功加入群组通话，进入九宫格界面，能看到其他参与者
```

#### 异常场景测试
```
测试用例3: 网络中断恢复
前置条件: 正在进行群组通话
测试步骤:
1. 关闭网络连接
2. 等待5秒
3. 恢复网络连接
预期结果: 通话自动恢复，不需要手动重连

测试用例4: 成员全部离开
前置条件: 群组通话进行中
测试步骤:
1. 除自己外所有成员离开通话
2. 等待系统响应
预期结果: 通话自动结束，返回群聊界面
```

---

**验收完成标准**: 所有验收标准项目100%通过，无阻塞性问题，用户体验达到微信群通话水平。