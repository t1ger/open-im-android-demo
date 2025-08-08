package io.openim.android.ouicalling.state;

import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.models.SignalingInvitationInfo;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 通话状态管理器 - 基于信令数据的统一状态管理
 * 
 * 设计原则：
 * 1. 状态判断基于SignalingInfo，不依赖成员变量
 * 2. 单一数据源：SignalingInfo.getInvitation()
 * 3. 线程安全：所有状态计算都是纯函数
 * 4. 易于测试：状态逻辑与UI分离
 */
public class CallStateManager {
    private static final String TAG = "CallStateManager";
    
    private volatile SignalingInfo currentSignalingInfo;
    private final Object stateLock = new Object();
    
    /**
     * 更新当前信令信息
     * @param signalingInfo 信令信息
     */
    public void updateSignalingInfo(SignalingInfo signalingInfo) {
        synchronized (stateLock) {
            this.currentSignalingInfo = signalingInfo;
            Log.d(TAG, "信令信息已更新: " + getCallTypeDescription());
        }
    }
    
    /**
     * 是否为群组通话
     * 基于信令数据判断，不依赖成员变量
     */
    public boolean isGroupCall() {
        return isGroupCall(currentSignalingInfo);
    }
    
    /**
     * 静态方法：判断是否为群组通话
     * @param signalingInfo 信令信息
     * @return true if 群组通话
     */
    public static boolean isGroupCall(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return false;
        }
        
        SignalingInvitationInfo invitation = signalingInfo.getInvitation();
        
        // 方法1：基于SessionType判断（主要判断依据）
        if (invitation.getSessionType() == ConversationType.GROUP_CHAT) {
            return true;
        }
        
        // 方法2：基于GroupID判断（辅助判断）
        if (!TextUtils.isEmpty(invitation.getGroupID())) {
            return true;
        }
        
        // 方法3：基于参与者数量判断（兜底判断）
        List<String> invitees = invitation.getInviteeUserIDList();
        if (invitees != null && invitees.size() > 1) {
            Log.d(TAG, "基于参与者数量判断为群组通话: " + invitees.size() + "人");
            return true;
        }
        
        return false;
    }
    
    /**
     * 是否为视频通话
     */
    public boolean isVideoCall() {
        return isVideoCall(currentSignalingInfo);
    }
    
    /**
     * 静态方法：判断是否为视频通话
     * @param signalingInfo 信令信息
     * @return true if 视频通话
     */
    public static boolean isVideoCall(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return false;
        }
        
        // 基于MediaType判断
        String mediaType = signalingInfo.getInvitation().getMediaType();
        return "video".equalsIgnoreCase(mediaType);
    }
    
    /**
     * 获取通话类型描述
     */
    public String getCallTypeDescription() {
        return getCallTypeDescription(currentSignalingInfo);
    }
    
    /**
     * 静态方法：获取通话类型描述
     * @param signalingInfo 信令信息
     * @return 通话类型描述
     */
    public static String getCallTypeDescription(SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            return "未知通话";
        }
        
        boolean isGroup = isGroupCall(signalingInfo);
        boolean isVideo = isVideoCall(signalingInfo);
        
        if (isGroup) {
            return isVideo ? "群组视频通话" : "群组音频通话";
        } else {
            return isVideo ? "单人视频通话" : "单人音频通话";
        }
    }
    
    /**
     * 获取参与者数量
     */
    public int getParticipantCount() {
        return getParticipantCount(currentSignalingInfo);
    }
    
    /**
     * 静态方法：获取参与者数量
     * @param signalingInfo 信令信息
     * @return 参与者数量（包括发起者）
     */
    public static int getParticipantCount(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return 0;
        }
        
        List<String> invitees = signalingInfo.getInvitation().getInviteeUserIDList();
        int inviteeCount = (invitees != null) ? invitees.size() : 0;
        
        // 参与者数量 = 被邀请者数量 + 发起者(1)
        return inviteeCount + 1;
    }
    
    /**
     * 获取群组ID（如果是群组通话）
     */
    public String getGroupId() {
        return getGroupId(currentSignalingInfo);
    }
    
    /**
     * 静态方法：获取群组ID
     * @param signalingInfo 信令信息
     * @return 群组ID，单人通话返回null
     */
    public static String getGroupId(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return null;
        }
        
        return signalingInfo.getInvitation().getGroupID();
    }
    
    /**
     * 获取房间ID
     */
    public String getRoomId() {
        return getRoomId(currentSignalingInfo);
    }
    
    /**
     * 静态方法：获取房间ID
     * @param signalingInfo 信令信息
     * @return 房间ID
     */
    public static String getRoomId(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return null;
        }
        
        return signalingInfo.getInvitation().getRoomID();
    }
    
    /**
     * 验证信令信息完整性
     * @param signalingInfo 信令信息
     * @return 验证结果
     */
    public static ValidationResult validateSignalingInfo(SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            return ValidationResult.error("SignalingInfo为null");
        }
        
        SignalingInvitationInfo invitation = signalingInfo.getInvitation();
        if (invitation == null) {
            return ValidationResult.error("Invitation为null");
        }
        
        if (TextUtils.isEmpty(invitation.getRoomID())) {
            return ValidationResult.error("RoomID为空");
        }
        
        if (TextUtils.isEmpty(invitation.getInviterUserID())) {
            return ValidationResult.error("InviterUserID为空");
        }
        
        List<String> invitees = invitation.getInviteeUserIDList();
        if (invitees == null || invitees.isEmpty()) {
            return ValidationResult.error("被邀请用户列表为空");
        }
        
        // 检查群组通话特有字段
        if (isGroupCall(signalingInfo)) {
            if (TextUtils.isEmpty(invitation.getGroupID())) {
                return ValidationResult.warning("群组通话但GroupID为空");
            }
            
            if (invitees.size() > 8) {
                return ValidationResult.error("群组通话参与者超过限制: " + invitees.size());
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String message;
        public final Level level;
        
        public enum Level {
            SUCCESS, WARNING, ERROR
        }
        
        private ValidationResult(boolean isValid, String message, Level level) {
            this.isValid = isValid;
            this.message = message;
            this.level = level;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, "验证通过", Level.SUCCESS);
        }
        
        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, Level.WARNING);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, Level.ERROR);
        }
        
        @Override
        public String toString() {
            return level + ": " + message;
        }
    }
    
    /**
     * 获取当前信令信息的调试字符串
     */
    public String getDebugInfo() {
        return getDebugInfo(currentSignalingInfo);
    }
    
    /**
     * 静态方法：获取信令信息的调试字符串
     * @param signalingInfo 信令信息
     * @return 调试信息
     */
    public static String getDebugInfo(SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            return "SignalingInfo=null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("CallState{");
        sb.append("type=").append(getCallTypeDescription(signalingInfo));
        sb.append(", participants=").append(getParticipantCount(signalingInfo));
        sb.append(", roomId=").append(getRoomId(signalingInfo));
        
        if (isGroupCall(signalingInfo)) {
            sb.append(", groupId=").append(getGroupId(signalingInfo));
        }
        
        sb.append("}");
        return sb.toString();
    }
}