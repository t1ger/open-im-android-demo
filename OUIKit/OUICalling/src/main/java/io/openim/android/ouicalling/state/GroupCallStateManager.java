package io.openim.android.ouicalling.state;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 群组通话状态管理器
 * 
 * 业务职责：
 * 1. 统一管理群组通话的所有状态
 * 2. 实现Observer模式，通知UI状态变化
 * 3. 处理群组成员的状态转换
 * 4. 确保状态的一致性和完整性
 * 
 * 架构原则：
 * - 单一数据源：所有群组状态都在这里管理
 * - 状态同步：确保UI和业务逻辑的状态一致
 * - 观察者模式：UI通过监听器获取状态变化
 */
public class GroupCallStateManager {
    private static final String TAG = "GroupCallStateManager";
    
    // === 群组通话状态 ===
    private boolean isGroupCall = false;
    private String groupId;
    private String groupRoomId;
    private String currentSpeaker;
    private boolean isVideoCall;
    
    // === 成员管理 ===
    // 使用线程安全的CopyOnWriteArrayList
    private final CopyOnWriteArrayList<GroupCallMember> groupMembers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<StateChangeObserver> observers = new CopyOnWriteArrayList<>();
    
    // === 单例模式 ===
    private static volatile GroupCallStateManager instance;
    
    private GroupCallStateManager() {
        L.d(TAG, "群组通话状态管理器初始化");
    }
    
    public static GroupCallStateManager getInstance() {
        if (instance == null) {
            synchronized (GroupCallStateManager.class) {
                if (instance == null) {
                    instance = new GroupCallStateManager();
                }
            }
        }
        return instance;
    }
    
    // === 状态管理方法 ===
    
    /**
     * 从SignalingInfo初始化群组通话状态
     */
    public void initializeFromSignaling(@NonNull SignalingInfo signalingInfo) {
        try {
            if (!CallStateManager.isGroupCall(signalingInfo)) {
                L.d(TAG, "非群组通话，跳过初始化");
                return;
            }
            
            this.isGroupCall = true;
            this.groupId = signalingInfo.getInvitation().getGroupID();
            this.groupRoomId = signalingInfo.getInvitation().getRoomID();
            this.isVideoCall = CallStateManager.isVideoCall(signalingInfo);
            
            // 初始化成员列表
            initializeMembersFromSignaling(signalingInfo);
            
            L.businessFlow(TAG, "群组通话状态初始化", 
                "GroupID: " + groupId + ", RoomID: " + groupRoomId + 
                ", Video: " + isVideoCall + ", Members: " + groupMembers.size());
                
            // 通知观察者
            notifyStateChanged(StateChangeType.CALL_INITIATED);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "初始化群组通话状态", 
                LogExceptionHandler.ExceptionType.STATE_ERROR, e);
        }
    }
    
    /**
     * 从SignalingInfo初始化成员列表
     */
    private void initializeMembersFromSignaling(@NonNull SignalingInfo signalingInfo) {
        groupMembers.clear();
        
        List<String> inviteeIds = signalingInfo.getInvitation().getInviteeUserIDList();
        if (inviteeIds != null) {
            for (String memberId : inviteeIds) {
                if (memberId != null && !memberId.isEmpty()) {
                    GroupCallMember member = new GroupCallMember(memberId, memberId, null);
                    member.setState(CallMemberState.INVITING);
                    groupMembers.add(member);
                    L.v(TAG, "添加群组成员: " + memberId);
                }
            }
        }
    }
    
    /**
     * 更新成员状态
     */
    public boolean updateMemberState(@NonNull String userId, @NonNull CallMemberState newState) {
        try {
            GroupCallMember member = findMember(userId);
            if (member == null) {
                L.w(TAG, "未找到成员: " + userId);
                return false;
            }
            
            CallMemberState oldState = member.getState();
            boolean success = member.setState(newState);
            
            if (success) {
                L.d(TAG, "成员状态更新: " + userId + " " + oldState.getDescription() + " -> " + newState.getDescription());
                
                // 成员状态变化日志（SPEAKING状态可能将来添加）
                // 目前通过其他方式管理发言人状态
                
                // 通知观察者
                notifyMemberStateChanged(member);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "更新成员状态", 
                LogExceptionHandler.ExceptionType.STATE_ERROR, e);
            return false;
        }
    }
    
    /**
     * 添加群组成员
     */
    public boolean addMember(@NonNull String userId, @NonNull String nickname) {
        try {
            if (findMember(userId) != null) {
                L.w(TAG, "成员已存在: " + userId);
                return false;
            }
            
            GroupCallMember member = new GroupCallMember(userId, nickname, null);
            member.setState(CallMemberState.INVITING);
            groupMembers.add(member);
            
            L.d(TAG, "添加群组成员: " + userId + " (" + nickname + ")");
            notifyMemberAdded(member);
            return true;
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "添加群组成员", 
                LogExceptionHandler.ExceptionType.STATE_ERROR, e);
            return false;
        }
    }
    
    /**
     * 移除群组成员
     */
    public boolean removeMember(@NonNull String userId) {
        try {
            GroupCallMember member = findMember(userId);
            if (member == null) {
                L.w(TAG, "成员不存在: " + userId);
                return false;
            }
            
            groupMembers.remove(member);
            
            // 如果移除的是当前发言人，清除发言人
            if (userId.equals(currentSpeaker)) {
                clearCurrentSpeaker();
            }
            
            L.d(TAG, "移除群组成员: " + userId);
            notifyMemberRemoved(member);
            return true;
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "移除群组成员", 
                LogExceptionHandler.ExceptionType.STATE_ERROR, e);
            return false;
        }
    }
    
    /**
     * 查找群组成员
     */
    public GroupCallMember findMember(@NonNull String userId) {
        for (GroupCallMember member : groupMembers) {
            if (userId.equals(member.getUserId())) {
                return member;
            }
        }
        return null;
    }
    
    /**
     * 设置当前发言人
     */
    public void setCurrentSpeaker(@NonNull String userId) {
        if (!userId.equals(currentSpeaker)) {
            String oldSpeaker = currentSpeaker;
            this.currentSpeaker = userId;
            
            L.d(TAG, "切换发言人: " + oldSpeaker + " -> " + userId);
            notifyCurrentSpeakerChanged(oldSpeaker, userId);
        }
    }
    
    /**
     * 清除当前发言人
     */
    public void clearCurrentSpeaker() {
        if (currentSpeaker != null) {
            String oldSpeaker = currentSpeaker;
            this.currentSpeaker = null;
            
            L.d(TAG, "清除发言人: " + oldSpeaker);
            notifyCurrentSpeakerChanged(oldSpeaker, null);
        }
    }
    
    /**
     * 结束群组通话
     */
    public void endGroupCall(@NonNull String reason) {
        try {
            L.d(TAG, "结束群组通话: " + reason);
            
            // 重置所有状态
            isGroupCall = false;
            groupId = null;
            groupRoomId = null;
            currentSpeaker = null;
            isVideoCall = false;
            groupMembers.clear();
            
            // 通知观察者
            notifyCallEnded(reason);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "结束群组通话", 
                LogExceptionHandler.ExceptionType.STATE_ERROR, e);
        }
    }
    
    // === 观察者模式 ===
    
    /**
     * 添加状态变化观察者
     */
    public void addObserver(@NonNull StateChangeObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            L.v(TAG, "添加状态观察者: " + observer.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除状态变化观察者
     */
    public void removeObserver(@NonNull StateChangeObserver observer) {
        observers.remove(observer);
        L.v(TAG, "移除状态观察者: " + observer.getClass().getSimpleName());
    }
    
    /**
     * 通知状态变化
     */
    private void notifyStateChanged(@NonNull StateChangeType changeType) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onStateChanged(changeType);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知状态变化", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    /**
     * 通知成员状态变化
     */
    private void notifyMemberStateChanged(@NonNull GroupCallMember member) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onMemberStateChanged(member);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知成员状态变化", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    /**
     * 通知成员添加
     */
    private void notifyMemberAdded(@NonNull GroupCallMember member) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onMemberAdded(member);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知成员添加", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    /**
     * 通知成员移除
     */
    private void notifyMemberRemoved(@NonNull GroupCallMember member) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onMemberRemoved(member);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知成员移除", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    /**
     * 通知发言人变化
     */
    private void notifyCurrentSpeakerChanged(String oldSpeaker, String newSpeaker) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onCurrentSpeakerChanged(oldSpeaker, newSpeaker);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知发言人变化", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    /**
     * 通知通话结束
     */
    private void notifyCallEnded(@NonNull String reason) {
        for (StateChangeObserver observer : observers) {
            try {
                observer.onCallEnded(reason);
            } catch (Exception e) {
                LogExceptionHandler.handleException(TAG, "通知通话结束", 
                    LogExceptionHandler.ExceptionType.OBSERVER_ERROR, e);
            }
        }
    }
    
    // === Getters ===
    
    public boolean isGroupCall() {
        return isGroupCall;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getGroupRoomId() {
        return groupRoomId;
    }
    
    public String getCurrentSpeaker() {
        return currentSpeaker;
    }
    
    public boolean isVideoCall() {
        return isVideoCall;
    }
    
    public List<GroupCallMember> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }
    
    public int getMemberCount() {
        return groupMembers.size();
    }
    
    /**
     * 获取指定状态的成员数量
     */
    public int getMemberCountByState(@NonNull CallMemberState state) {
        int count = 0;
        for (GroupCallMember member : groupMembers) {
            if (state.equals(member.getState())) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 通知成员信息已更新（用户名、头像等）
     */
    public void notifyMembersInfoUpdated() {
        try {
            // 通知所有监听器成员信息已更新
            for (StateChangeObserver observer : stateObservers) {
                observer.onMembersInfoUpdated();
            }
            
            android.util.Log.d("GroupCallStateManager", "成员信息已更新，通知UI刷新");
        } catch (Exception e) {
            android.util.Log.e("GroupCallStateManager", "通知成员信息更新失败", e);
        }
    }
    
    // === 状态变化观察者接口 ===
    
    public interface StateChangeObserver {
        /**
         * 状态变化通知
         */
        void onStateChanged(@NonNull StateChangeType changeType);
        
        /**
         * 成员状态变化
         */
        void onMemberStateChanged(@NonNull GroupCallMember member);
        
        /**
         * 成员添加
         */
        void onMemberAdded(@NonNull GroupCallMember member);
        
        /**
         * 成员移除
         */
        void onMemberRemoved(@NonNull GroupCallMember member);
        
        /**
         * 发言人变化
         */
        void onCurrentSpeakerChanged(String oldSpeaker, String newSpeaker);
        
        /**
         * 通话结束
         */
        void onCallEnded(@NonNull String reason);
        
        /**
         * 成员信息已更新（用户名、头像等）
         */
        void onMembersInfoUpdated();
    }
    
    // === 状态变化类型枚举 ===
    
    public enum StateChangeType {
        CALL_INITIATED("通话发起"),
        CALL_CONNECTED("通话连接"),
        CALL_ENDED("通话结束"),
        MEMBER_JOINED("成员加入"),
        MEMBER_LEFT("成员离开"),
        SPEAKER_CHANGED("发言人变化");
        
        private final String description;
        
        StateChangeType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}