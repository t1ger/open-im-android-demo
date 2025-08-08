package io.openim.android.ouicore.entity;

import io.openim.android.ouicore.ex.CommEx;
import io.openim.android.sdk.models.UserInfo;

public class ExUserInfo  extends CommEx {
    public UserInfo userInfo;
    public ExGroupMemberInfo exGroupMemberInfo;
    
    // 🔥 新增：统一的用户选择接口支持（适配器模式）
    public SelectableUser selectableUser;
    
    /**
     * 从 SelectableUser 获取显示名称
     * 优先使用 selectableUser，如果为null则回退到原有逻辑
     */
    public String getDisplayName() {
        if (selectableUser != null) {
            return selectableUser.getDisplayName();
        }
        
        // 回退到原有逻辑
        if (userInfo != null && userInfo.getNickname() != null) {
            return userInfo.getNickname();
        }
        
        if (exGroupMemberInfo != null && exGroupMemberInfo.groupMembersInfo != null) {
            return exGroupMemberInfo.groupMembersInfo.getNickname();
        }
        
        return "Unknown";
    }
    
    /**
     * 从 SelectableUser 获取用户ID
     */
    public String getUserId() {
        if (selectableUser != null) {
            return selectableUser.getUserId();
        }
        
        // 回退到原有逻辑
        if (userInfo != null) {
            return userInfo.getUserID();
        }
        
        if (exGroupMemberInfo != null && exGroupMemberInfo.groupMembersInfo != null) {
            return exGroupMemberInfo.groupMembersInfo.getUserID();
        }
        
        return "";
    }
    
    /**
     * 从 SelectableUser 获取头像URL
     */
    public String getAvatarUrl() {
        if (selectableUser != null) {
            return selectableUser.getAvatarUrl();
        }
        
        // 回退到原有逻辑
        if (userInfo != null) {
            return userInfo.getFaceURL();
        }
        
        if (exGroupMemberInfo != null && exGroupMemberInfo.groupMembersInfo != null) {
            return exGroupMemberInfo.groupMembersInfo.getFaceURL();
        }
        
        return "";
    }
}
