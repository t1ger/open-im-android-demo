package io.openim.android.ouigroup.debug;

import android.util.Log;

/**
 * 群组通话调试日志工具类
 * 
 * 使用统一的TAG方便adb日志过滤：
 * adb logcat | grep "GroupCallFlow"
 * 
 * 关键路径标识：
 * 🚀 启动流程
 * 👥 成员选择
 * ✅ 成功操作
 * ❌ 错误处理
 * ⚠️ 警告信息
 * 🔍 调试信息
 */
public class GroupCallDebugLogger {
    
    private static final String TAG = "GroupCallFlow";
    
    // ==================== 成员选择流程 ====================
    
    /**
     * 成员选择Activity启动
     */
    public static void logMemberSelectionStart(String groupId, boolean isVideo) {
        Log.d(TAG, "🚀 [MemberSelection] 启动成员选择 - groupId: " + groupId + ", isVideo: " + isVideo);
    }
    
    /**
     * 群信息初始化
     */
    public static void logGroupInfoInit(String groupId, boolean isSelectMemberMode) {
        Log.d(TAG, "🔍 [Init] 群信息初始化 - groupId: " + groupId + ", isSelectMember: " + isSelectMemberMode);
    }
    
    /**
     * 群信息获取结果
     */
    public static void logGroupInfoResult(boolean success, String groupOwnerId) {
        if (success) {
            Log.d(TAG, "✅ [Init] 群信息获取成功 - ownerId: " + groupOwnerId);
        } else {
            Log.w(TAG, "⚠️ [Init] 群信息获取失败，groupsInfo为null");
        }
    }
    
    /**
     * 群成员数据加载
     */
    public static void logMemberDataLoaded(int memberCount) {
        Log.d(TAG, "👥 [Data] 群成员数据加载完成 - 数量: " + memberCount);
    }
    
    /**
     * 用户选择确认
     */
    public static void logMemberSelectionConfirm(int selectedCount) {
        Log.d(TAG, "🚀 [Selection] 用户点击确定 - 已选择" + selectedCount + "个成员");
    }
    
    /**
     * 选择结果返回
     */
    public static void logMemberSelectionResult(java.util.List<String> selectedIds) {
        Log.d(TAG, "✅ [Selection] 成员选择完成 - 返回" + selectedIds.size() + "个成员: " + selectedIds);
        Log.d(TAG, "📤 [Selection] 设置Activity结果并关闭页面");
    }
    
    // ==================== 通话发起流程 ====================
    
    /**
     * ChatActivity接收选择结果
     */
    public static void logSelectionReceived(int resultCode, int memberCount) {
        Log.d(TAG, "📥 [ChatActivity] 接收选择结果 - resultCode: " + resultCode + ", 成员数: " + memberCount);
    }
    
    /**
     * 群组信令构建
     */
    public static void logSignalingBuild(boolean isVideo, String groupId, int memberCount) {
        Log.d(TAG, "🔧 [Signaling] 开始构建群组信令 - isVideo: " + isVideo + ", groupId: " + groupId + ", 成员数: " + memberCount);
    }
    
    /**
     * 群组信令构建结果
     */
    public static void logSignalingResult(boolean success, String sessionType) {
        if (success) {
            Log.d(TAG, "✅ [Signaling] 群组信令构建成功 - SessionType: " + sessionType);
        } else {
            Log.e(TAG, "❌ [Signaling] 群组信令构建失败");
        }
    }
    
    /**
     * 通话服务调用
     */
    public static void logCallingServiceCall() {
        Log.d(TAG, "📞 [CallingService] 发起群组通话");
    }
    
    // ==================== 错误处理 ====================
    
    /**
     * 空指针异常处理
     */
    public static void logNullPointerHandled(String location, String details) {
        Log.w(TAG, "⚠️ [NullPointer] " + location + " - " + details);
    }
    
    /**
     * 异常处理
     */
    public static void logException(String phase, Exception e) {
        Log.e(TAG, "❌ [Exception] " + phase + " - " + e.getMessage(), e);
    }
    
    // ==================== ADB使用指南 ====================
    
    /**
     * 打印ADB使用指南到日志
     */
    public static void printAdbGuide() {
        Log.d(TAG, "==================== ADB调试指南 ====================");
        Log.d(TAG, "完整流程日志: adb logcat | grep \"GroupCallFlow\"");
        Log.d(TAG, "成员选择流程: adb logcat | grep \"GroupCallFlow.*Selection\"");
        Log.d(TAG, "信令构建流程: adb logcat | grep \"GroupCallFlow.*Signaling\"");
        Log.d(TAG, "错误信息过滤: adb logcat | grep \"GroupCallFlow.*❌\"");
        Log.d(TAG, "警告信息过滤: adb logcat | grep \"GroupCallFlow.*⚠️\"");
        Log.d(TAG, "=================================================");
    }
}