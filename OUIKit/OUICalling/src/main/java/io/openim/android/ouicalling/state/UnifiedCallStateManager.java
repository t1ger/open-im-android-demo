package io.openim.android.ouicalling.state;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 统一通话状态管理器
 * 
 * 解决问题：
 * 1. 多状态源冲突 (CallingVM, GroupCallStateManager, CallViewModel)
 * 2. 异步状态更新竞争条件
 * 3. 状态不同步导致的UI错乱
 * 
 * 架构原则：
 * - Single Source of Truth: 所有状态都在这里统一管理
 * - Thread Safety: 支持多线程安全操作
 * - Observer Pattern: 使用LiveData提供响应式更新
 * - State Consistency: 确保状态变更的原子性和一致性
 * 
 * 参考微信群组通话架构：
 * - 统一状态存储
 * - 事件驱动更新
 * - 状态变更验证
 * - 生命周期管理
 */
public class UnifiedCallStateManager {
    private static final String TAG = "UnifiedCallStateManager";
    
    // === 单例模式 ===
    private static volatile UnifiedCallStateManager instance;
    
    // === 通话基本状态 ===
    private final AtomicReference<CallState> currentCallState = new AtomicReference<>(CallState.IDLE);
    private final AtomicReference<String> groupId = new AtomicReference<>();
    private final AtomicReference<String> roomId = new AtomicReference<>();
    private final AtomicReference<Boolean> isVideoCall = new AtomicReference<>(false);
    
    // === 成员状态管理 ===
    // 使用ConcurrentHashMap确保线程安全的成员状态管理
    private final ConcurrentHashMap<String, GroupCallMember> memberStateMap = new ConcurrentHashMap<>();
    private final AtomicReference<String> currentSpeaker = new AtomicReference<>();
    
    // === LiveData for UI观察 ===
    private final MutableLiveData<CallState> callStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<GroupCallMember>> membersLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentSpeakerLiveData = new MutableLiveData<>();
    private final MutableLiveData<CallError> errorLiveData = new MutableLiveData<>();
    
    // === 观察者管理 ===
    private final CopyOnWriteArrayList<StateChangeObserver> observers = new CopyOnWriteArrayList<>();
    
    private UnifiedCallStateManager() {
        L.d(TAG, "统一通话状态管理器初始化");
        initializeLiveData();
    }
    
    public static UnifiedCallStateManager getInstance() {
        if (instance == null) {
            synchronized (UnifiedCallStateManager.class) {
                if (instance == null) {
                    instance = new UnifiedCallStateManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化LiveData
     */
    private void initializeLiveData() {
        callStateLiveData.setValue(CallState.IDLE);
        membersLiveData.setValue(new CopyOnWriteArrayList<>());
        currentSpeakerLiveData.setValue(null);
        errorLiveData.setValue(null);
    }
    
    // === 核心状态管理方法 ===
    
    /**
     * 初始化群组通话状态
     * 单一入口，确保状态一致性
     */
    public synchronized void initializeGroupCall(@NonNull SignalingInfo signalingInfo) {
        try {
            L.businessFlow(TAG, "初始化群组通话", "开始状态初始化");
            
            // 1. 验证输入参数
            if (signalingInfo.getInvitation() == null) {
                reportError("SignalingInfo.invitation 为空", CallError.Type.INVALID_PARAMETER);
                return;
            }
            
            // 2. 设置基本通话信息
            String newGroupId = signalingInfo.getInvitation().getGroupID();
            String newRoomId = signalingInfo.getInvitation().getRoomID();
            boolean newIsVideoCall = determineVideoCall(signalingInfo);
            
            groupId.set(newGroupId);
            roomId.set(newRoomId);
            isVideoCall.set(newIsVideoCall);
            
            // 3. 初始化成员列表
            initializeMembersFromSignaling(signalingInfo);
            
            // 4. 更新通话状态
            updateCallState(CallState.INITIALIZING);
            
            L.businessFlow(TAG, "群组通话初始化完成", 
                String.format("GroupID: %s, RoomID: %s, Video: %s, Members: %d", 
                    newGroupId, newRoomId, newIsVideoCall, memberStateMap.size()));
            
        } catch (Exception e) {
            handleException("初始化群组通话", e);
            reportError("初始化群组通话失败: " + e.getMessage(), CallError.Type.INITIALIZATION_ERROR);
        }
    }
    
    /**
     * 从SignalingInfo初始化成员列表
     */
    private void initializeMembersFromSignaling(@NonNull SignalingInfo signalingInfo) {
        // 清空现有成员
        memberStateMap.clear();
        
        List<String> inviteeIds = signalingInfo.getInvitation().getInviteeUserIDList();
        if (inviteeIds != null) {
            for (String memberId : inviteeIds) {
                if (memberId != null && !memberId.isEmpty()) {
                    GroupCallMember member = new GroupCallMember(memberId, memberId, null);
                    member.setState(CallMemberState.INVITING);
                    memberStateMap.put(memberId, member);
                    
                    L.v(TAG, "添加成员到统一状态管理: " + memberId);
                }
            }
        }
        
        // 更新LiveData
        updateMembersLiveData();
    }
    
    /**
     * 更新成员状态（原子操作）
     * 解决多线程状态竞争问题
     */
    public boolean updateMemberState(@NonNull String userId, @NonNull CallMemberState newState) {
        try {
            if (userId == null || userId.isEmpty()) {
                L.w(TAG, "[状态更新] 用户ID为空");
                return false;
            }
            
            GroupCallMember member = memberStateMap.get(userId);
            if (member == null) {
                L.w(TAG, "[状态更新] 成员不存在: " + userId);
                return false;
            }
            
            CallMemberState oldState = member.getState();
            
            // 验证状态转换是否合法
            if (!oldState.canTransitionTo(newState)) {
                L.w(TAG, String.format("[状态更新] 非法状态转换: %s %s -> %s", 
                    userId, oldState.getDescription(), newState.getDescription()));
                return false;
            }
            
            // 原子性状态更新
            synchronized (memberStateMap) {
                member.setState(newState);
                memberStateMap.put(userId, member); // 确保更新到Map中
                
                L.d(TAG, String.format("[状态更新] %s: %s -> %s", 
                    userId, oldState.getDescription(), newState.getDescription()));
                
                // 更新LiveData（在同步块内，确保一致性）
                updateMembersLiveData();
                
                // 通知观察者
                notifyMemberStateChanged(userId, oldState, newState);
                
                return true;
            }
            
        } catch (Exception e) {
            handleException("更新成员状态", e);
            return false;
        }
    }
    
    /**
     * 设置当前发言人（原子操作）
     */
    public void setCurrentSpeaker(@NonNull String userId) {
        try {
            String oldSpeaker = currentSpeaker.getAndSet(userId);
            
            if (!userId.equals(oldSpeaker)) {
                L.d(TAG, String.format("[发言人变更] %s -> %s", oldSpeaker, userId));
                
                // 更新LiveData
                currentSpeakerLiveData.postValue(userId);
                
                // 通知观察者
                notifySpeakerChanged(oldSpeaker, userId);
            }
            
        } catch (Exception e) {
            handleException("设置发言人", e);
        }
    }
    
    /**
     * 更新通话状态
     */
    public void updateCallState(@NonNull CallState newState) {
        try {
            CallState oldState = currentCallState.getAndSet(newState);
            
            if (newState != oldState) {
                L.d(TAG, String.format("[通话状态] %s -> %s", oldState, newState));
                
                // 更新LiveData
                callStateLiveData.postValue(newState);
                
                // 通知观察者
                notifyCallStateChanged(oldState, newState);
            }
            
        } catch (Exception e) {
            handleException("更新通话状态", e);
        }
    }
    
    // === 数据获取方法 ===
    
    /**
     * 获取当前所有成员（线程安全）
     */
    @NonNull
    public List<GroupCallMember> getCurrentMembers() {
        return new CopyOnWriteArrayList<>(memberStateMap.values());
    }
    
    /**
     * 获取活跃成员列表
     */
    @NonNull
    public List<GroupCallMember> getActiveMembers() {
        CopyOnWriteArrayList<GroupCallMember> activeMembers = new CopyOnWriteArrayList<>();
        for (GroupCallMember member : memberStateMap.values()) {
            if (member.getState().isActiveState() || member.getState().isWaitingState()) {
                activeMembers.add(member);
            }
        }
        return activeMembers;
    }
    
    /**
     * 获取当前通话状态
     */
    @NonNull
    public CallState getCurrentCallState() {
        return currentCallState.get();
    }
    
    /**
     * 获取当前发言人
     */
    public String getCurrentSpeaker() {
        return currentSpeaker.get();
    }
    
    // === LiveData访问器（供UI观察） ===
    
    public LiveData<CallState> getCallStateLiveData() {
        return callStateLiveData;
    }
    
    public LiveData<List<GroupCallMember>> getMembersLiveData() {
        return membersLiveData;
    }
    
    public LiveData<String> getCurrentSpeakerLiveData() {
        return currentSpeakerLiveData;
    }
    
    public LiveData<CallError> getErrorLiveData() {
        return errorLiveData;
    }
    
    // === 观察者模式 ===
    
    public void addObserver(@NonNull StateChangeObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            L.d(TAG, "添加状态观察者: " + observer.getClass().getSimpleName());
        }
    }
    
    public void removeObserver(@NonNull StateChangeObserver observer) {
        if (observers.remove(observer)) {
            L.d(TAG, "移除状态观察者: " + observer.getClass().getSimpleName());
        }
    }
    
    // === 私有辅助方法 ===
    
    /**
     * 更新成员LiveData
     */
    private void updateMembersLiveData() {
        List<GroupCallMember> currentMembers = new CopyOnWriteArrayList<>(memberStateMap.values());
        membersLiveData.postValue(currentMembers);
    }
    
    /**
     * 确定是否为视频通话
     */
    private boolean determineVideoCall(@NonNull SignalingInfo signalingInfo) {
        // 这里可以根据SignalingInfo判断是否为视频通话
        // 暂时返回false，具体逻辑需要根据信令结构调整
        return false;
    }
    
    /**
     * 通知成员状态变更
     */
    private void notifyMemberStateChanged(@NonNull String userId, 
                                          @NonNull CallMemberState oldState, 
                                          @NonNull CallMemberState newState) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onMemberStateChanged(userId, oldState, newState);
            } catch (Exception e) {
                L.e(TAG, "通知观察者成员状态变更失败: " + observer.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 通知发言人变更
     */
    private void notifySpeakerChanged(String oldSpeaker, String newSpeaker) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onSpeakerChanged(oldSpeaker, newSpeaker);
            } catch (Exception e) {
                L.e(TAG, "通知观察者发言人变更失败: " + observer.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 通知通话状态变更
     */
    private void notifyCallStateChanged(@NonNull CallState oldState, @NonNull CallState newState) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onCallStateChanged(oldState, newState);
            } catch (Exception e) {
                L.e(TAG, "通知观察者通话状态变更失败: " + observer.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 报告错误
     */
    private void reportError(@NonNull String message, @NonNull CallError.Type type) {
        CallError error = new CallError(type, message, System.currentTimeMillis());
        errorLiveData.postValue(error);
        L.e(TAG, "[错误报告] " + message);
    }
    
    /**
     * 异常处理
     */
    private void handleException(@NonNull String operation, @NonNull Exception e) {
        LogExceptionHandler.handleException(TAG, operation, 
            LogExceptionHandler.ExceptionType.STATE_ERROR, e);
    }
    
    /**
     * 重置状态（用于通话结束）
     */
    public synchronized void resetState() {
        try {
            L.d(TAG, "重置统一状态管理器");
            
            // 重置所有状态
            currentCallState.set(CallState.IDLE);
            groupId.set(null);
            roomId.set(null);
            isVideoCall.set(false);
            currentSpeaker.set(null);
            
            // 清空成员
            memberStateMap.clear();
            
            // 重置LiveData
            initializeLiveData();
            
            // 通知观察者
            for (StateChangeObserver observer : observers) {
                try {
                    observer.onStateReset();
                } catch (Exception e) {
                    L.e(TAG, "通知观察者状态重置失败: " + observer.getClass().getSimpleName(), e);
                }
            }
            
        } catch (Exception e) {
            handleException("重置状态", e);
        }
    }
    
    // === 内部类定义 ===
    
    /**
     * 通话状态枚举
     */
    public enum CallState {
        IDLE("空闲"),
        INITIALIZING("初始化中"),
        CALLING("通话中"),
        CONNECTING("连接中"),
        CONNECTED("已连接"),
        ENDING("结束中"),
        ENDED("已结束"),
        ERROR("错误");
        
        private final String description;
        
        CallState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 通话错误类
     */
    public static class CallError {
        public enum Type {
            INVALID_PARAMETER("参数错误"),
            INITIALIZATION_ERROR("初始化错误"),
            NETWORK_ERROR("网络错误"),
            STATE_ERROR("状态错误"),
            UNKNOWN_ERROR("未知错误");
            
            private final String description;
            
            Type(String description) {
                this.description = description;
            }
            
            public String getDescription() {
                return description;
            }
        }
        
        private final Type type;
        private final String message;
        private final long timestamp;
        
        public CallError(@NonNull Type type, @NonNull String message, long timestamp) {
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
        }
        
        public Type getType() { return type; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (时间: %d)", type.getDescription(), message, timestamp);
        }
    }
    
    /**
     * 状态变更观察者接口
     */
    public interface StateChangeObserver {
        default void onMemberStateChanged(@NonNull String userId, 
                                          @NonNull CallMemberState oldState, 
                                          @NonNull CallMemberState newState) {}
        
        default void onSpeakerChanged(String oldSpeaker, String newSpeaker) {}
        
        default void onCallStateChanged(@NonNull CallState oldState, @NonNull CallState newState) {}
        
        default void onStateReset() {}
    }
}