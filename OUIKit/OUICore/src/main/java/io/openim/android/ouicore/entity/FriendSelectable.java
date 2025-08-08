package io.openim.android.ouicore.entity;

import android.text.TextUtils;
import com.github.promeg.pinyinhelper.Pinyin;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.sdk.models.FriendInfo;

/**
 * 朋友信息的适配器实现
 * 将FriendInfo适配为SelectableUser接口
 * 
 * 业务场景：
 * 1. 创建群组时选择朋友
 * 2. 邀请朋友加入群组
 * 
 * @author AI Assistant
 * @version 1.0
 */
public class FriendSelectable implements SelectableUser {
    
    private final FriendInfo friendInfo;
    private final String currentUserId;
    
    /**
     * 构造函数
     * @param friendInfo 朋友信息
     * @param currentUserId 当前登录用户ID
     */
    public FriendSelectable(FriendInfo friendInfo, String currentUserId) {
        this.friendInfo = friendInfo;
        this.currentUserId = currentUserId;
    }
    
    @Override
    public String getUserId() {
        return friendInfo != null ? friendInfo.getUserID() : "";
    }
    
    @Override
    public String getDisplayName() {
        if (friendInfo == null) return "";
        
        // 优先使用备注名，其次昵称，最后用户ID
        if (!TextUtils.isEmpty(friendInfo.getRemark())) {
            return friendInfo.getRemark();
        }
        
        if (!TextUtils.isEmpty(friendInfo.getNickname())) {
            return friendInfo.getNickname();
        }
        
        return !TextUtils.isEmpty(friendInfo.getUserID()) ? friendInfo.getUserID() : "Unknown";
    }
    
    @Override
    public String getAvatarUrl() {
        return friendInfo != null ? friendInfo.getFaceURL() : "";
    }
    
    @Override
    public boolean isEnabled() {
        if (friendInfo == null) return false;
        
        // 自己不能选择自己
        return !friendInfo.getUserID().equals(currentUserId);
    }
    
    @Override
    public String getSourceType() {
        return "friend";
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
        return friendInfo;
    }
    
    /**
     * 获取朋友信息（类型安全的getter）
     * @return FriendInfo对象
     */
    public FriendInfo getFriendInfo() {
        return friendInfo;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        FriendSelectable that = (FriendSelectable) obj;
        return getUserId().equals(that.getUserId());
    }
    
    @Override
    public int hashCode() {
        return getUserId().hashCode();
    }
    
    @Override
    public String toString() {
        return "FriendSelectable{" +
                "userId='" + getUserId() + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                ", sourceType='" + getSourceType() + '\'' +
                ", enabled=" + isEnabled() +
                '}';
    }
}