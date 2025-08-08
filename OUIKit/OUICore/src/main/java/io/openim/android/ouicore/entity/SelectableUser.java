package io.openim.android.ouicore.entity;

/**
 * 统一的用户选择接口
 * 用于抽象不同数据源（群成员、朋友等）的用户选择逻辑
 * 
 * 设计模式：适配器模式
 * 目的：解决InitiateGroupActivity中数据类型混淆的问题
 * 
 * @author AI Assistant
 * @version 1.0
 * @since 2024-12
 */
public interface SelectableUser {
    
    /**
     * 获取用户ID
     * @return 用户唯一标识
     */
    String getUserId();
    
    /**
     * 获取显示名称
     * 优先级：备注名 > 昵称 > 用户ID
     * @return 用于UI显示的名称
     */
    String getDisplayName();
    
    /**
     * 获取头像URL
     * @return 头像地址，可能为null或空字符串
     */
    String getAvatarUrl();
    
    /**
     * 是否可以被选择
     * 业务规则：
     * - 群主不能被移除
     * - 自己不能选择自己参与通话
     * - 已离线用户可能不能被邀请等
     * @return true表示可选择，false表示禁用
     */
    boolean isEnabled();
    
    /**
     * 获取数据源类型
     * 用于调试和日志记录
     * @return "group_member", "friend", "contact" 等
     */
    String getSourceType();
    
    /**
     * 获取排序字母
     * 用于字母导航
     * @return 首字母，如 "A", "B", "#" 等
     */
    String getSortLetter();
    
    /**
     * 获取原始数据对象
     * 用于需要访问特定字段的场景
     * @return 原始的数据对象（GroupMembersInfo, FriendInfo等）
     */
    Object getRawData();
}