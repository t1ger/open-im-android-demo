package io.openim.android.ouicalling;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.openim.android.ouicalling.state.CallStateManager;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 通话对话框工厂类
 * 根据信令类型创建对应的通话对话框实例
 * 解决原有CallDialog双重职责问题的核心类
 */
public class CallDialogFactory {
    
    /**
     * 创建通话对话框
     * 这是解决"两次群组选择"和"无法跳转九宫格"问题的关键方法
     * 
     * @param context 上下文
     * @param callingService 通话服务
     * @param signalingInfo 信令信息
     * @param isCallOut 是否为呼出
     * @param dismissListener 关闭监听器（可选）
     * @return 对应类型的通话对话框
     */
    @NonNull
    public static BaseCallDialog create(@NonNull Context context, 
                                      @NonNull CallingService callingService, 
                                      @NonNull SignalingInfo signalingInfo,
                                      boolean isCallOut,
                                      @Nullable DialogInterface.OnDismissListener dismissListener) {
        
        try {
            // 关键修复点1：通过统一的状态管理器判断通话类型
            boolean isGroupCall = CallStateManager.isGroupCall(signalingInfo);
            
            L.businessFlow("CallDialogFactory", "创建通话对话框", 
                "类型: " + (isGroupCall ? "群组" : "单人") + 
                ", SessionType: " + L.safeToString(signalingInfo.getInvitation().getSessionType()) +
                ", GroupID: " + L.safeToString(signalingInfo.getInvitation().getGroupID()));
            
            BaseCallDialog dialog;
            
            if (isGroupCall) {
                // 关键修复点2：群组通话直接创建GroupCallDialog，显示九宫格界面
                dialog = new GroupCallDialog(context, callingService, isCallOut);
                L.critical("CallDialogFactory", "创建群组通话对话框 - 直接九宫格界面");
                
            } else {
                // 单人通话创建SingleCallDialog
                dialog = new SingleCallDialog(context, callingService, isCallOut);
                L.d("CallDialogFactory", "创建单人通话对话框");
            }
            
            // 设置关闭监听器
            if (dismissListener != null) {
                dialog.setOnDismissListener(dismissListener);
            }
            
            // 绑定信令数据
            dialog.bindData(signalingInfo);
            
            L.businessFlow("CallDialogFactory", "对话框创建完成", 
                "类型: " + dialog.getClass().getSimpleName());
            
            return dialog;
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallDialogFactory", "创建通话对话框失败", 
                LogExceptionHandler.ExceptionType.FACTORY_ERROR, e);
            
            // 发生异常时的降级策略：创建单人通话对话框
            L.w("CallDialogFactory", "创建对话框异常，降级为单人通话模式");
            SingleCallDialog fallbackDialog = new SingleCallDialog(context, callingService, isCallOut);
            
            if (dismissListener != null) {
                fallbackDialog.setOnDismissListener(dismissListener);
            }
            
            // 仍然尝试绑定数据
            try {
                fallbackDialog.bindData(signalingInfo);
            } catch (Exception bindException) {
                LogExceptionHandler.handleException("CallDialogFactory", "降级对话框数据绑定失败", 
                    LogExceptionHandler.ExceptionType.DATA_ERROR, bindException);
            }
            
            return fallbackDialog;
        }
    }
    
    /**
     * 创建通话对话框 - 简化版本
     * 不需要关闭监听器的场景
     */
    @NonNull
    public static BaseCallDialog create(@NonNull Context context, 
                                      @NonNull CallingService callingService, 
                                      @NonNull SignalingInfo signalingInfo,
                                      boolean isCallOut) {
        return create(context, callingService, signalingInfo, isCallOut, null);
    }
    
    /**
     * 预检查通话类型
     * 用于在创建对话框前预先了解通话类型，便于日志和调试
     * 
     * @param signalingInfo 信令信息
     * @return 通话类型描述
     */
    @NonNull
    public static String getCallTypeDescription(@NonNull SignalingInfo signalingInfo) {
        try {
            boolean isGroupCall = CallStateManager.isGroupCall(signalingInfo);
            boolean isVideoCall = CallStateManager.isVideoCall(signalingInfo);
            
            String callType = isGroupCall ? "群组" : "单人";
            String mediaType = isVideoCall ? "视频" : "音频";
            
            return callType + mediaType + "通话";
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallDialogFactory", "获取通话类型描述", 
                LogExceptionHandler.ExceptionType.VALIDATION_ERROR, e);
            return "未知类型通话";
        }
    }
    
    /**
     * 验证信令信息是否有效
     * 用于在创建对话框前验证数据完整性
     * 
     * @param signalingInfo 信令信息
     * @return 验证结果
     */
    @NonNull
    public static ValidationResult validateSignalingInfo(@Nullable SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            return ValidationResult.error("信令信息为null");
        }
        
        if (signalingInfo.getInvitation() == null) {
            return ValidationResult.error("信令邀请信息为null");
        }
        
        // 检查基本字段
        if (signalingInfo.getInvitation().getSessionType() == null) {
            return ValidationResult.error("会话类型为null");
        }
        
        if (signalingInfo.getInvitation().getMediaType() == null) {
            return ValidationResult.error("媒体类型为null");
        }
        
        // 根据通话类型检查特定字段
        boolean isGroupCall = CallStateManager.isGroupCall(signalingInfo);
        
        if (isGroupCall) {
            // 群组通话需要群组ID
            if (signalingInfo.getInvitation().getGroupID() == null || 
                signalingInfo.getInvitation().getGroupID().isEmpty()) {
                return ValidationResult.error("群组通话缺少群组ID");
            }
            
            // 群组通话需要邀请人列表
            if (signalingInfo.getInvitation().getInviteeUserIDList() == null || 
                signalingInfo.getInvitation().getInviteeUserIDList().isEmpty()) {
                return ValidationResult.error("群组通话缺少被邀请人列表");
            }
        } else {
            // 单人通话需要单个被邀请人
            if (signalingInfo.getInvitation().getInviteeUserIDList() == null || 
                signalingInfo.getInvitation().getInviteeUserIDList().size() != 1) {
                return ValidationResult.error("单人通话被邀请人列表无效");
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
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
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return valid ? "验证通过" : "验证失败: " + errorMessage;
        }
    }
}