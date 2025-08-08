package io.openim.android.ouicore.factory;

import android.text.TextUtils;
import java.util.List;
import java.util.UUID;

import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.config.CallingConfig;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.sdk.models.SignalingInvitationInfo;

/**
 * SignalingInfo构建工厂类
 * 
 * 解决问题：
 * 1. buildSignalingInfo和buildGroupSignalingInfo代码重复度高
 * 2. 参数校验逻辑分散，容易遗漏
 * 3. 信令构建逻辑不统一，维护困难
 * 
 * 设计原则：
 * 1. 单一职责：只负责SignalingInfo的构建
 * 2. 参数校验前置：严格的参数检查机制
 * 3. 配置驱动：使用CallingConfig统一管理常量
 * 4. 工厂模式：提供统一的构建接口
 */
public class SignalingInfoFactory {
    
    private static final String TAG = "SignalingInfoFactory";
    
    /**
     * 构建单人通话信令
     * 
     * @param isVideo 是否视频通话
     * @param inviteeUserIDs 被邀请用户ID列表
     * @return SignalingInfo对象
     * @throws IllegalArgumentException 参数校验失败
     * @throws IllegalStateException 用户未登录
     */
    public static SignalingInfo buildSingleCallSignaling(boolean isVideo, List<String> inviteeUserIDs) {
        return buildSignalingInfo(SignalingType.SINGLE_CALL, isVideo, null, inviteeUserIDs);
    }
    
    /**
     * 构建群组通话信令
     * 
     * @param isVideo 是否视频通话
     * @param groupId 群组ID
     * @param inviteeUserIDs 被邀请用户ID列表
     * @return SignalingInfo对象
     * @throws IllegalArgumentException 参数校验失败
     * @throws IllegalStateException 用户未登录
     */
    public static SignalingInfo buildGroupCallSignaling(boolean isVideo, String groupId, List<String> inviteeUserIDs) {
        return buildSignalingInfo(SignalingType.GROUP_CALL, isVideo, groupId, inviteeUserIDs);
    }
    
    /**
     * 统一的SignalingInfo构建方法
     */
    private static SignalingInfo buildSignalingInfo(SignalingType type, boolean isVideo, String groupId, List<String> inviteeUserIDs) {
        // 1. 参数校验
        validateBuildParameters(type, groupId, inviteeUserIDs);
        
        // 2. 获取当前用户信息
        String inviterUserId = getCurrentUserId();
        if (TextUtils.isEmpty(inviterUserId)) {
            throw new IllegalStateException("用户未登录，无法发起通话");
        }
        
        try {
            // 3. 创建SignalingInfo
            SignalingInfo signalingInfo = new SignalingInfo();
            
            // 4. 创建SignalingInvitationInfo
            SignalingInvitationInfo invitation = new SignalingInvitationInfo();
            invitation.setInviterUserID(inviterUserId);
            invitation.setInviteeUserIDList(inviteeUserIDs);
            invitation.setRoomID(generateRoomId());
            invitation.setTimeout(CallingConfig.CALL_CONNECTION_TIMEOUT_SECONDS); // 使用配置常量
            invitation.setInitiateTime(System.currentTimeMillis());
            invitation.setMediaType(isVideo ? Constants.MediaType.VIDEO : Constants.MediaType.AUDIO);
            invitation.setPlatformID(IMUtil.PLATFORM_ID);
            
            // 5. 根据类型设置特定字段
            configureByType(invitation, type, groupId);
            
            signalingInfo.setInvitation(invitation);
            
            android.util.Log.d(TAG, "构建信令成功: " + getSignalingDescription(type, isVideo, inviteeUserIDs.size()));
            return signalingInfo;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "构建信令失败: " + e.getMessage(), e);
            throw new RuntimeException("构建SignalingInfo失败", e);
        }
    }
    
    /**
     * 根据信令类型配置特定字段
     */
    private static void configureByType(SignalingInvitationInfo invitation, SignalingType type, String groupId) {
        switch (type) {
            case SINGLE_CALL:
                invitation.setSessionType(ConversationType.SINGLE_CHAT);
                // 单人通话不设置GroupID
                break;
                
            case GROUP_CALL:
                invitation.setSessionType(ConversationType.GROUP_CHAT);
                invitation.setGroupID(groupId);
                break;
                
            default:
                throw new IllegalArgumentException("不支持的信令类型: " + type);
        }
    }
    
    /**
     * 参数校验
     */
    private static void validateBuildParameters(SignalingType type, String groupId, List<String> inviteeUserIDs) {
        // 基础参数校验
        if (type == null) {
            throw new IllegalArgumentException("信令类型不能为null");
        }
        
        if (inviteeUserIDs == null || inviteeUserIDs.isEmpty()) {
            throw new IllegalArgumentException("被邀请用户列表不能为空");
        }
        
        // 检查用户ID有效性
        for (String userId : inviteeUserIDs) {
            if (TextUtils.isEmpty(userId)) {
                throw new IllegalArgumentException("被邀请用户ID不能为空");
            }
        }
        
        // 类型特定校验
        switch (type) {
            case SINGLE_CALL:
                if (inviteeUserIDs.size() != 1) {
                    throw new IllegalArgumentException("单人通话只能邀请1个用户，当前: " + inviteeUserIDs.size());
                }
                break;
                
            case GROUP_CALL:
                if (TextUtils.isEmpty(groupId)) {
                    throw new IllegalArgumentException("群组通话必须提供群组ID");
                }
                
                if (inviteeUserIDs.size() > CallingConfig.MAX_GROUP_CALL_MEMBERS) {
                    throw new IllegalArgumentException(
                        "群组通话最多支持 " + CallingConfig.MAX_GROUP_CALL_MEMBERS + " 个成员，当前: " + inviteeUserIDs.size());
                }
                
                if (inviteeUserIDs.size() < 1) {
                    throw new IllegalArgumentException("群组通话至少需要邀请1个用户");
                }
                break;
        }
        
        // 登录状态校验
        String currentUserId = getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            throw new IllegalStateException("用户未登录，无法发起通话");
        }
        
        // 检查是否邀请自己
        if (inviteeUserIDs.contains(currentUserId)) {
            throw new IllegalArgumentException("不能邀请自己参与通话");
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private static String getCurrentUserId() {
        try {
            return BaseApp.inst().loginCertificate.userID;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 生成房间ID
     */
    private static String generateRoomId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
    
    /**
     * 获取信令描述（用于日志）
     */
    private static String getSignalingDescription(SignalingType type, boolean isVideo, int participantCount) {
        String callType = isVideo ? "视频" : "音频";
        String groupType = (type == SignalingType.GROUP_CALL) ? "群组" : "单人";
        return groupType + callType + "通话(" + participantCount + "人)";
    }
    
    /**
     * 信令类型枚举
     */
    public enum SignalingType {
        SINGLE_CALL("单人通话"),
        GROUP_CALL("群组通话");
        
        private final String description;
        
        SignalingType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 构建器模式支持（可选扩展）
     */
    public static class Builder {
        private boolean isVideo = false;
        private String groupId;
        private List<String> inviteeUserIDs;
        private SignalingType type;
        
        public Builder video(boolean isVideo) {
            this.isVideo = isVideo;
            return this;
        }
        
        public Builder groupId(String groupId) {
            this.groupId = groupId;
            this.type = SignalingType.GROUP_CALL;
            return this;
        }
        
        public Builder invitees(List<String> inviteeUserIDs) {
            this.inviteeUserIDs = inviteeUserIDs;
            return this;
        }
        
        public Builder singleCall() {
            this.type = SignalingType.SINGLE_CALL;
            return this;
        }
        
        public SignalingInfo build() {
            if (type == null) {
                // 根据参数自动判断类型
                type = TextUtils.isEmpty(groupId) ? SignalingType.SINGLE_CALL : SignalingType.GROUP_CALL;
            }
            
            return buildSignalingInfo(type, isVideo, groupId, inviteeUserIDs);
        }
    }
}