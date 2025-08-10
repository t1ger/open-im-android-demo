package io.openim.android.ouicore.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 群组音视频专用日志系统
 * 
 * 设计原则：
 * 1. 基于现有L.java和LogExceptionHandler，不重复造轮子
 * 2. 提供专用的标签系统，便于adb过滤和问题定位  
 * 3. 关键节点强制输出，支持生产环境问题追踪
 * 4. 与现有日志系统完全兼容
 * 
 * 使用示例：
 * - 关键流程：GroupCallLogger.logCriticalFlow("成员加入", "userId=123", "memberCount=5")
 * - 信令追踪：GroupCallLogger.logSignaling("INVITE", "发送群组邀请", signalingData)
 * - 状态变化：GroupCallLogger.logStateChange("IDLE", "CALLING", "成员响应")
 * - 性能监控：GroupCallLogger.logPerformance("九宫格渲染", 234)
 * - 错误处理：GroupCallLogger.logError("视频渲染失败", exception)
 * 
 * adb过滤命令：
 * - 所有群组通话日志：adb logcat | grep "GC_"
 * - 关键流程：adb logcat | grep "GC_CRITICAL"  
 * - 信令相关：adb logcat | grep "GC_SIGNALING"
 * - 状态变化：adb logcat | grep "GC_STATE"
 * - 性能问题：adb logcat | grep "GC_PERF"
 * - 错误日志：adb logcat | grep "GC_ERROR"
 */
public class GroupCallLogger {
    
    // 群组通话专用标签前缀
    private static final String TAG_PREFIX = "GC_";  // Group Call
    
    // 专用标签分类（便于adb过滤）
    public static final String TAG_CRITICAL = TAG_PREFIX + "CRITICAL";     // 关键流程 
    public static final String TAG_SIGNALING = TAG_PREFIX + "SIGNALING";   // 信令处理
    public static final String TAG_STATE = TAG_PREFIX + "STATE";           // 状态变化
    public static final String TAG_MEMBER = TAG_PREFIX + "MEMBER";         // 成员管理
    public static final String TAG_UI = TAG_PREFIX + "UI";                 // UI相关
    public static final String TAG_VIDEO = TAG_PREFIX + "VIDEO";           // 视频渲染
    public static final String TAG_AUDIO = TAG_PREFIX + "AUDIO";           // 音频处理
    public static final String TAG_PERF = TAG_PREFIX + "PERF";             // 性能监控
    public static final String TAG_ERROR = TAG_PREFIX + "ERROR";           // 错误日志
    public static final String TAG_DEBUG = TAG_PREFIX + "DEBUG";           // 调试信息
    
    // ========== 关键流程日志（生产环境也输出）==========
    
    /**
     * 关键业务流程日志
     * 用于追踪群组通话的关键节点，生产环境也会输出
     */
    public static void logCriticalFlow(@NonNull String operation, @NonNull String context, @NonNull String details) {
        String message = String.format("🎯 [%s] %s - %s", operation, context, details);
        L.critical(TAG_CRITICAL, message);
    }
    
    /**
     * 关键流程的简化版本
     */
    public static void logCriticalFlow(@NonNull String operation, @NonNull String details) {
        logCriticalFlow(operation, "", details);
    }
    
    // ========== 信令处理日志 ==========
    
    /**
     * 信令处理日志
     */
    public static void logSignaling(@NonNull String signalingType, @NonNull String operation, @Nullable String data) {
        String message = String.format("📡 [%s] %s", signalingType, operation);
        if (data != null && !data.isEmpty()) {
            message += " - " + data;
        }
        L.critical(TAG_SIGNALING, message);
    }
    
    /**
     * 信令错误日志
     */
    public static void logSignalingError(@NonNull String signalingType, @NonNull String error, @Nullable Throwable throwable) {
        String message = String.format("📡❌ [%s] %s", signalingType, error);
        if (throwable != null) {
            L.e(TAG_SIGNALING, message, throwable);
        } else {
            L.e(TAG_SIGNALING, message);
        }
    }
    
    // ========== 状态变化日志 ==========
    
    /**
     * 状态变化日志
     */
    public static void logStateChange(@NonNull String fromState, @NonNull String toState, @NonNull String trigger) {
        String message = String.format("🔄 状态切换: %s → %s (触发: %s)", fromState, toState, trigger);
        L.critical(TAG_STATE, message);
    }
    
    /**
     * 成员状态变化
     */
    public static void logMemberStateChange(@NonNull String memberId, @NonNull String fromState, @NonNull String toState) {
        String message = String.format("👤 成员状态: %s - %s → %s", memberId, fromState, toState);
        L.critical(TAG_MEMBER, message);
    }
    
    // ========== 成员管理日志 ==========
    
    /**
     * 成员加入日志
     */
    public static void logMemberJoin(@NonNull String memberId, int totalCount) {
        String message = String.format("👤➕ 成员加入: %s (总数: %d)", memberId, totalCount);
        L.critical(TAG_MEMBER, message);
    }
    
    /**
     * 成员离开日志
     */
    public static void logMemberLeave(@NonNull String memberId, @NonNull String reason, int remainingCount) {
        String message = String.format("👤➖ 成员离开: %s - 原因: %s (剩余: %d)", memberId, reason, remainingCount);
        L.critical(TAG_MEMBER, message);
    }
    
    // ========== UI相关日志 ==========
    
    /**
     * UI操作日志
     */
    public static void logUIOperation(@NonNull String operation, @NonNull String details) {
        String message = String.format("🖥️ UI操作: %s - %s", operation, details);
        L.businessFlow(TAG_UI, operation, details);
    }
    
    /**
     * 九宫格布局日志
     */
    public static void logGridLayout(@NonNull String operation, int memberCount, @NonNull String layoutType) {
        String message = String.format("🔳 九宫格: %s - 成员数: %d, 布局: %s", operation, memberCount, layoutType);
        L.critical(TAG_UI, message);
    }
    
    // ========== 视频渲染日志 ==========
    
    /**
     * 视频渲染日志
     */
    public static void logVideoRendering(@NonNull String memberId, @NonNull String operation, @NonNull String status) {
        String message = String.format("📹 视频渲染: %s - %s (%s)", memberId, operation, status);
        L.d(TAG_VIDEO, message);
    }
    
    /**
     * 视频质量日志
     */
    public static void logVideoQuality(@NonNull String memberId, int width, int height, int fps) {
        String message = String.format("📹📊 视频质量: %s - %dx%d@%dfps", memberId, width, height, fps);
        L.d(TAG_VIDEO, message);
    }
    
    // ========== 音频处理日志 ==========
    
    /**
     * 音频设备日志
     */
    public static void logAudioDevice(@NonNull String operation, @NonNull String deviceType, @NonNull String status) {
        String message = String.format("🔊 音频设备: %s - %s (%s)", operation, deviceType, status);
        L.critical(TAG_AUDIO, message);
    }
    
    /**
     * 音频状态日志
     */
    public static void logAudioState(@NonNull String memberId, boolean micEnabled, boolean speakerEnabled) {
        String message = String.format("🎙️ 音频状态: %s - 麦克风: %s, 扬声器: %s", 
                                     memberId, micEnabled ? "开" : "关", speakerEnabled ? "开" : "关");
        L.d(TAG_AUDIO, message);
    }
    
    // ========== 性能监控日志 ==========
    
    /**
     * 性能监控日志
     */
    public static void logPerformance(@NonNull String operation, long durationMs) {
        String message = String.format("⏱️ 性能: %s - 耗时: %dms", operation, durationMs);
        
        if (durationMs > 1000) {
            L.w(TAG_PERF, message + " ⚠️ 耗时过长");
        } else if (durationMs > 500) {
            L.i(TAG_PERF, message + " ⚠️ 需关注");
        } else {
            L.d(TAG_PERF, message);
        }
    }
    
    /**
     * 内存使用日志
     */
    public static void logMemoryUsage(@NonNull String operation, long memoryMB) {
        String message = String.format("💾 内存: %s - 使用: %dMB", operation, memoryMB);
        
        if (memoryMB > 100) {
            L.w(TAG_PERF, message + " ⚠️ 内存占用较高");
        } else {
            L.d(TAG_PERF, message);
        }
    }
    
    // ========== 错误处理日志 ==========
    
    /**
     * 通用错误日志
     */
    public static void logError(@NonNull String operation, @NonNull String error, @Nullable Throwable throwable) {
        String message = String.format("❌ 错误: %s - %s", operation, error);
        
        if (throwable != null) {
            L.e(TAG_ERROR, message, throwable);
        } else {
            L.e(TAG_ERROR, message);
        }
    }
    
    /**
     * 恢复性错误日志
     */
    public static void logRecoverableError(@NonNull String operation, @NonNull String error, @NonNull String recovery) {
        String message = String.format("⚠️ 可恢复错误: %s - %s (恢复策略: %s)", operation, error, recovery);
        L.w(TAG_ERROR, message);
    }
    
    // ========== 调试日志 ==========
    
    /**
     * 调试信息日志
     */
    public static void logDebug(@NonNull String operation, @NonNull String details) {
        String message = String.format("🔍 调试: %s - %s", operation, details);
        L.d(TAG_DEBUG, message);
    }
    
    /**
     * 数据结构日志
     */
    public static void logDataStructure(@NonNull String name, @NonNull String content) {
        String message = String.format("📋 数据: %s - %s", name, content);
        L.d(TAG_DEBUG, message);
    }
    
    // ========== 实用工具方法 ==========
    
    /**
     * 格式化成员列表
     */
    public static String formatMemberList(java.util.List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(", ", memberIds) + "]";
    }
    
    /**
     * 格式化信令数据
     */
    public static String formatSignalingData(@NonNull Object signaling) {
        // 简化信令数据输出，避免日志过长
        return signaling.getClass().getSimpleName() + "@" + Integer.toHexString(signaling.hashCode());
    }
    
    /**
     * 创建业务流程追踪器
     */
    public static LogExceptionHandler.BusinessFlow startBusinessFlow(@NonNull String flowName) {
        return LogExceptionHandler.BusinessFlow.start(TAG_PREFIX + "FLOW", flowName);
    }
}