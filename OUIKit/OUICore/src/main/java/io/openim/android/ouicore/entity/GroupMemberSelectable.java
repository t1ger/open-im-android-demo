package io.openim.android.ouicore.entity;

import android.text.TextUtils;
import com.github.promeg.pinyinhelper.Pinyin;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.sdk.models.GroupMembersInfo;

/**
 * 群成员信息的适配器实现
 * 将GroupMembersInfo适配为SelectableUser接口
 * 
 * 业务场景：
 * 1. 群组通话成员选择
 * 2. 群组管理（移除成员等）
 * 
 * @author AI Assistant
 * @version 1.0
 */
public class GroupMemberSelectable implements SelectableUser {
    
    private final GroupMembersInfo memberInfo;
    private final String currentUserId;
    private final String groupOwnerId;
    private final boolean isForGroupCall; // 是否用于群组通话
    
    /**
     * 构造函数
     * @param memberInfo 群成员信息
     * @param currentUserId 当前登录用户ID
     * @param groupOwnerId 群主ID
     * @param isForGroupCall 是否用于群组通话
     */
    public GroupMemberSelectable(GroupMembersInfo memberInfo, String currentUserId, 
                               String groupOwnerId, boolean isForGroupCall) {
        this.memberInfo = memberInfo;
        this.currentUserId = currentUserId;
        this.groupOwnerId = groupOwnerId;
        this.isForGroupCall = isForGroupCall;
    }
    
    @Override
    public String getUserId() {
        return memberInfo != null ? memberInfo.getUserID() : "";
    }
    
    @Override
    public String getDisplayName() {
        if (memberInfo == null) return "";
        
        // 优先使用群昵称，其次用户昵称，最后用户ID
        if (!TextUtils.isEmpty(memberInfo.getNickname())) {
            return memberInfo.getNickname();
        }
        
        return !TextUtils.isEmpty(memberInfo.getUserID()) ? memberInfo.getUserID() : "Unknown";
    }
    
    @Override
    public String getAvatarUrl() {
        return memberInfo != null ? memberInfo.getFaceURL() : "";
    }
    
    @Override
    public boolean isEnabled() {
        if (memberInfo == null) return false;
        
        String userId = memberInfo.getUserID();
        
        if (isForGroupCall) {
            // 群组通话场景：自己不能选择自己
            return !userId.equals(currentUserId);
        } else {
            // 群组管理场景：群主不能被移除，自己不能选择自己
            return !userId.equals(currentUserId) && !userId.equals(groupOwnerId);
        }
    }
    
    @Override
    public String getSourceType() {
        return "group_member";
    }
    
    @Override
    public String getSortLetter() {
        String displayName = getDisplayName();
        if (TextUtils.isEmpty(displayName)) return "#";
        
        try {
            String letter = String.valueOf(Pinyin.toPinyin(displayName.charAt(0)).charAt(0));
            letter = letter.toUpperCase();
            
            // 检查是否为字母
            if (Common.isAlpha(letter)) {
                return letter;
            }
        } catch (Exception e) {
            // 拼音转换失败，返回#
        }
        
        return "#";
    }
    
    @Override
    public Object getRawData() {
        return memberInfo;
    }
    
    /**
     * 获取群成员信息（类型安全的getter）
     * @return GroupMembersInfo对象
     */
    public GroupMembersInfo getGroupMemberInfo() {
        return memberInfo;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GroupMemberSelectable that = (GroupMemberSelectable) obj;
        return getUserId().equals(that.getUserId());
    }
    
    @Override
    public int hashCode() {
        return getUserId().hashCode();
    }
    
    @Override
    public String toString() {
        return "GroupMemberSelectable{" +
                "userId='" + getUserId() + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                ", sourceType='" + getSourceType() + '\'' +
                ", enabled=" + isEnabled() +
                '}';
    }
}