package io.openim.android.ouicalling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.openim.android.ouicalling.state.CallStateManager;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 统一信令处理器
 * 解决信令处理分散、状态不同步等问题
 * 确保UI状态与信令状态完全一致
 */
public class SignalingProcessor {
    
    /**
     * 处理信令信息，返回标准化的处理结果
     * 这是解决信令与UI状态不同步问题的关键方法
     * 
     * @param originalSignaling 原始信令信息
     * @return 标准化的处理结果
     */
    @NonNull
    public static ProcessingResult process(@Nullable SignalingInfo originalSignaling) {
        L.businessFlow("SignalingProcessor", "开始处理信令", "输入验证");
        
        try {
            // 第1步：信令验证和预处理
            ValidationResult validation = validateSignaling(originalSignaling);
            if (!validation.isValid()) {
                L.w("SignalingProcessor", "信令验证失败: " + validation.getErrorMessage());
                return ProcessingResult.error(validation.getErrorMessage());
            }
            
            // 第2步：确定通话类型
            CallType callType = determineCallType(originalSignaling);
            L.d("SignalingProcessor", "识别通话类型: " + callType.getDescription());
            
            // 第3步：构建标准化的内部信令
            InternalSignaling internalSignaling = buildInternalSignaling(originalSignaling, callType);
            L.d("SignalingProcessor", "构建内部信令完成");
            
            // 第4步：执行额外的业务逻辑验证
            BusinessValidationResult businessValidation = validateBusinessLogic(internalSignaling, callType);
            if (!businessValidation.isValid()) {
                L.w("SignalingProcessor", "业务逻辑验证失败: " + businessValidation.getErrorMessage());
                return ProcessingResult.error(businessValidation.getErrorMessage());
            }
            
            L.businessFlow("SignalingProcessor", "信令处理完成", 
                "类型: " + callType.getDescription() + ", 结果: 成功");
            
            // 第5步：返回成功结果
            return ProcessingResult.success(internalSignaling, callType);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("SignalingProcessor", "信令处理异常", 
                LogExceptionHandler.ExceptionType.SIGNALING_ERROR, e);
            return ProcessingResult.error("信令处理异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证信令信息的完整性和有效性
     */
    @NonNull
    private static ValidationResult validateSignaling(@Nullable SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            return ValidationResult.error("信令信息为null");
        }
        
        if (signalingInfo.getInvitation() == null) {
            return ValidationResult.error("信令邀请信息为null");
        }
        
        // 检查必要字段
        // SessionType的具体验证交由CallStateManager处理，这里跳过
        // 理由：SDK中可能存在类型不一致问题
        
        if (signalingInfo.getInvitation().getMediaType() == null) {
            return ValidationResult.error("媒体类型为null");
        }
        
        if (signalingInfo.getInvitation().getInviterUserID() == null || 
            signalingInfo.getInvitation().getInviterUserID().isEmpty()) {
            return ValidationResult.error("发起人ID为空");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 确定通话类型
     */
    @NonNull
    private static CallType determineCallType(@NonNull SignalingInfo signalingInfo) {
        boolean isGroupCall = CallStateManager.isGroupCall(signalingInfo);
        boolean isVideoCall = CallStateManager.isVideoCall(signalingInfo);
        
        if (isGroupCall && isVideoCall) {
            return CallType.GROUP_VIDEO;
        } else if (isGroupCall) {
            return CallType.GROUP_AUDIO;
        } else if (isVideoCall) {
            return CallType.SINGLE_VIDEO;
        } else {
            return CallType.SINGLE_AUDIO;
        }
    }
    
    /**
     * 构建标准化的内部信令
     */
    @NonNull
    private static InternalSignaling buildInternalSignaling(@NonNull SignalingInfo originalSignaling, 
                                                           @NonNull CallType callType) {
        InternalSignaling internal = new InternalSignaling();
        
        // 复制原始信令信息
        internal.setOriginalSignaling(originalSignaling);
        internal.setCallType(callType);
        
        // 根据通话类型设置特定字段
        if (callType.isGroupCall()) {
            internal.setGroupID(originalSignaling.getInvitation().getGroupID());
            internal.setMemberIds(originalSignaling.getInvitation().getInviteeUserIDList());
        } else {
            // 单人通话
            if (originalSignaling.getInvitation().getInviteeUserIDList() != null && 
                !originalSignaling.getInvitation().getInviteeUserIDList().isEmpty()) {
                internal.setTargetUserId(originalSignaling.getInvitation().getInviteeUserIDList().get(0));
            }
        }
        
        internal.setInviterUserId(originalSignaling.getInvitation().getInviterUserID());
        internal.setVideoCall(callType.isVideoCall());
        internal.setTimestamp(System.currentTimeMillis());
        
        return internal;
    }
    
    /**
     * 执行业务逻辑验证
     */
    @NonNull
    private static BusinessValidationResult validateBusinessLogic(@NonNull InternalSignaling internalSignaling, 
                                                                 @NonNull CallType callType) {
        
        if (callType.isGroupCall()) {
            // 群组通话特定验证
            if (internalSignaling.getGroupID() == null || internalSignaling.getGroupID().isEmpty()) {
                return BusinessValidationResult.error("群组通话缺少群组ID");
            }
            
            if (internalSignaling.getMemberIds() == null || internalSignaling.getMemberIds().isEmpty()) {
                return BusinessValidationResult.error("群组通话缺少成员列表");
            }
            
            // 检查成员数量限制
            if (internalSignaling.getMemberIds().size() > Constants.MAX_CALL_NUM) {
                return BusinessValidationResult.error("群组通话成员数超过限制: " + 
                    internalSignaling.getMemberIds().size() + "/" + Constants.MAX_CALL_NUM);
            }
            
        } else {
            // 单人通话特定验证
            if (internalSignaling.getTargetUserId() == null || internalSignaling.getTargetUserId().isEmpty()) {
                return BusinessValidationResult.error("单人通话缺少目标用户ID");
            }
        }
        
        return BusinessValidationResult.success();
    }
    
    /**
     * 通话类型枚举
     */
    public enum CallType {
        SINGLE_AUDIO("单人音频通话", false, false),
        SINGLE_VIDEO("单人视频通话", false, true),
        GROUP_AUDIO("群组音频通话", true, false),
        GROUP_VIDEO("群组视频通话", true, true);
        
        private final String description;
        private final boolean isGroupCall;
        private final boolean isVideoCall;
        
        CallType(String description, boolean isGroupCall, boolean isVideoCall) {
            this.description = description;
            this.isGroupCall = isGroupCall;
            this.isVideoCall = isVideoCall;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isGroupCall() {
            return isGroupCall;
        }
        
        public boolean isVideoCall() {
            return isVideoCall;
        }
    }
    
    /**
     * 标准化内部信令
     */
    public static class InternalSignaling {
        private SignalingInfo originalSignaling;
        private CallType callType;
        private String groupID;
        private java.util.List<String> memberIds;
        private String targetUserId;
        private String inviterUserId;
        private boolean isVideoCall;
        private long timestamp;
        
        // Getters and Setters
        public SignalingInfo getOriginalSignaling() { return originalSignaling; }
        public void setOriginalSignaling(SignalingInfo originalSignaling) { this.originalSignaling = originalSignaling; }
        
        public CallType getCallType() { return callType; }
        public void setCallType(CallType callType) { this.callType = callType; }
        
        public String getGroupID() { return groupID; }
        public void setGroupID(String groupID) { this.groupID = groupID; }
        
        public java.util.List<String> getMemberIds() { return memberIds; }
        public void setMemberIds(java.util.List<String> memberIds) { this.memberIds = memberIds; }
        
        public String getTargetUserId() { return targetUserId; }
        public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
        
        public String getInviterUserId() { return inviterUserId; }
        public void setInviterUserId(String inviterUserId) { this.inviterUserId = inviterUserId; }
        
        public boolean isVideoCall() { return isVideoCall; }
        public void setVideoCall(boolean videoCall) { this.isVideoCall = videoCall; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * 处理结果
     */
    public static class ProcessingResult {
        private final boolean success;
        private final String errorMessage;
        private final InternalSignaling internalSignaling;
        private final CallType callType;
        
        private ProcessingResult(boolean success, String errorMessage, 
                               InternalSignaling internalSignaling, CallType callType) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.internalSignaling = internalSignaling;
            this.callType = callType;
        }
        
        public static ProcessingResult success(InternalSignaling internalSignaling, CallType callType) {
            return new ProcessingResult(true, null, internalSignaling, callType);
        }
        
        public static ProcessingResult error(String errorMessage) {
            return new ProcessingResult(false, errorMessage, null, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public InternalSignaling getInternalSignaling() { return internalSignaling; }
        public CallType getCallType() { return callType; }
    }
    
    /**
     * 验证结果
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 业务逻辑验证结果
     */
    private static class BusinessValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private BusinessValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static BusinessValidationResult success() {
            return new BusinessValidationResult(true, null);
        }
        
        public static BusinessValidationResult error(String message) {
            return new BusinessValidationResult(false, message);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}