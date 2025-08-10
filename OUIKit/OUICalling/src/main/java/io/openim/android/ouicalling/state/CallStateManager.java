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
     * 业务规则：只依据SessionType判断，这是最权威的业务依据
     * @param signalingInfo 信令信息
     * @return true if 群组通话
     */
    public static boolean isGroupCall(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            android.util.Log.d("RootCauseDebug", "❌ [CallStateManager] isGroupCall返回false - 信令为null");
            return false;
        }
        
        // 🔍 根本原因调试：详细分析SessionType判断失败的原因
        try {
            Object sessionTypeObj = signalingInfo.getInvitation().getSessionType();
            Object groupChatObj = ConversationType.GROUP_CHAT;
            
            android.util.Log.d("RootCauseDebug", "=== SessionType根因调试 ===");
            android.util.Log.d("RootCauseDebug", "SessionType对象: " + sessionTypeObj);
            android.util.Log.d("RootCauseDebug", "SessionType类型: " + (sessionTypeObj != null ? sessionTypeObj.getClass().getName() : "null"));
            android.util.Log.d("RootCauseDebug", "SessionType值: " + sessionTypeObj);
            android.util.Log.d("RootCauseDebug", "GROUP_CHAT对象: " + groupChatObj);
            android.util.Log.d("RootCauseDebug", "GROUP_CHAT类型: " + groupChatObj.getClass().getName());
            android.util.Log.d("RootCauseDebug", "GROUP_CHAT值: " + groupChatObj);
            
            // 尝试不同的比较方式
            boolean directEquals = (sessionTypeObj == groupChatObj);
            boolean objectEquals = (sessionTypeObj != null && sessionTypeObj.equals(groupChatObj));
            boolean valueEquals = false;
            
            if (sessionTypeObj != null && groupChatObj != null) {
                valueEquals = sessionTypeObj.toString().equals(groupChatObj.toString());
            }
            
            android.util.Log.d("RootCauseDebug", "== 比较结果 ==");
            android.util.Log.d("RootCauseDebug", "直接比较(==): " + directEquals);
            android.util.Log.d("RootCauseDebug", "对象比较(.equals()): " + objectEquals);
            android.util.Log.d("RootCauseDebug", "字符串比较(.toString()): " + valueEquals);
            
            // 如果是整数类型，尝试数值比较
            if (sessionTypeObj instanceof Number && groupChatObj instanceof Number) {
                int sessionInt = ((Number) sessionTypeObj).intValue();
                int groupChatInt = ((Number) groupChatObj).intValue();
                boolean intEquals = (sessionInt == groupChatInt);
                android.util.Log.d("RootCauseDebug", "整数比较: " + sessionInt + " == " + groupChatInt + " = " + intEquals);
            }
            
            // 原始判断逻辑
            boolean result = signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT;
            android.util.Log.d("RootCauseDebug", "🎯 最终判断结果: " + result);
            android.util.Log.d("RootCauseDebug", "=== 调试结束 ===");
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e("RootCauseDebug", "❌ [CallStateManager] SessionType判断异常: " + e.getMessage(), e);
            return false;
        }
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