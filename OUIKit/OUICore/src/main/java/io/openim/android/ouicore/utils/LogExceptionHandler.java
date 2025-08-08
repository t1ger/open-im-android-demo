package io.openim.android.ouicore.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 统一异常处理模式和日志系统
 * 
 * 解决问题：
 * 1. 异常处理不统一，日志输出格式不一致
 * 2. 缺乏关键流程的错误追踪机制
 * 3. 调试信息散乱，影响问题定位效率
 * 
 * 使用示例：
 * - 基础日志：LogExceptionHandler.logDebug("CallDialog", "开始群组切换")
 * - 异常处理：LogExceptionHandler.handleException("CallDialog", "UI切换失败", e)
 * - 关键流程：LogExceptionHandler.logCriticalStep("CallDialog", "状态切换", "IDLE → CALLING")
 */
public class LogExceptionHandler {
    
    // 统一TAG前缀
    private static final String TAG_PREFIX = "OpenIM_";
    
    // 日志级别
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, CRITICAL
    }
    
    // 异常类型
    public enum ExceptionType {
        NETWORK_ERROR("网络异常"),
        UI_ERROR("界面异常"),
        DATA_ERROR("数据异常"),
        STATE_ERROR("状态异常"),
        CONFIG_ERROR("配置异常"),
        PERMISSION_ERROR("权限异常"),
        UNKNOWN_ERROR("未知异常");
        
        private final String description;
        
        ExceptionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 调试日志 - 开发阶段详细信息
     */
    public static void logDebug(@NonNull String module, @NonNull String message) {
        if (L.isDebug) {
            Log.d(TAG_PREFIX + module, "[DEBUG] " + message);
        }
    }
    
    /**
     * 信息日志 - 正常流程记录
     */
    public static void logInfo(@NonNull String module, @NonNull String message) {
        if (L.isDebug) {
            Log.i(TAG_PREFIX + module, "[INFO] " + message);
        }
    }
    
    /**
     * 警告日志 - 潜在问题
     */
    public static void logWarning(@NonNull String module, @NonNull String message) {
        if (L.isDebug) {
            Log.w(TAG_PREFIX + module, "[WARN] " + message);
        }
    }
    
    /**
     * 错误日志 - 明确的错误
     */
    public static void logError(@NonNull String module, @NonNull String message) {
        Log.e(TAG_PREFIX + module, "[ERROR] " + message);
    }
    
    /**
     * 关键步骤日志 - 重要流程节点（生产环境也会输出）
     */
    public static void logCriticalStep(@NonNull String module, @NonNull String operation, @NonNull String details) {
        String message = "[CRITICAL] " + operation + " - " + details;
        Log.i(TAG_PREFIX + module, message);
        
        // 可选：关键步骤也可以写入文件或发送到监控系统
        // 这里保持简单，只输出到控制台
    }
    
    /**
     * 状态变化日志 - 专门用于状态转换
     */
    public static void logStateChange(@NonNull String module, @NonNull String fromState, @NonNull String toState) {
        logCriticalStep(module, "状态切换", fromState + " → " + toState);
    }
    
    /**
     * 统一异常处理 - 带异常类型
     */
    public static void handleException(@NonNull String module, @NonNull String operation, 
                                     @NonNull ExceptionType type, @Nullable Throwable throwable) {
        String message = "[EXCEPTION] " + operation + " - " + type.getDescription();
        
        if (throwable != null) {
            Log.e(TAG_PREFIX + module, message, throwable);
            
            // 为特定异常类型提供额外处理
            handleSpecificException(module, type, throwable);
        } else {
            Log.e(TAG_PREFIX + module, message);
        }
    }
    
    /**
     * 简化异常处理 - 自动推断异常类型
     */
    public static void handleException(@NonNull String module, @NonNull String operation, @Nullable Throwable throwable) {
        ExceptionType type = inferExceptionType(throwable);
        handleException(module, operation, type, throwable);
    }
    
    /**
     * 性能日志 - 耗时操作记录
     */
    public static void logPerformance(@NonNull String module, @NonNull String operation, long durationMs) {
        String message = "[PERF] " + operation + " - 耗时: " + durationMs + "ms";
        
        if (durationMs > 1000) { // 超过1秒的操作记录为警告
            Log.w(TAG_PREFIX + module, message);
        } else {
            logDebug(module, message);
        }
    }
    
    /**
     * 业务流程日志 - 完整业务操作的开始和结束
     */
    public static class BusinessFlow {
        private final String module;
        private final String flowName;
        private final long startTime;
        
        private BusinessFlow(String module, String flowName) {
            this.module = module;
            this.flowName = flowName;
            this.startTime = System.currentTimeMillis();
            logCriticalStep(module, "业务流程开始", flowName);
        }
        
        public static BusinessFlow start(@NonNull String module, @NonNull String flowName) {
            return new BusinessFlow(module, flowName);
        }
        
        public void success() {
            long duration = System.currentTimeMillis() - startTime;
            logCriticalStep(module, "业务流程成功", flowName + " (耗时: " + duration + "ms)");
        }
        
        public void failure(@NonNull String reason) {
            long duration = System.currentTimeMillis() - startTime;
            logError(module, "业务流程失败: " + flowName + " - " + reason + " (耗时: " + duration + "ms)");
        }
        
        public void failure(@NonNull String reason, @Nullable Throwable throwable) {
            long duration = System.currentTimeMillis() - startTime;
            handleException(module, "业务流程失败: " + flowName + " - " + reason + " (耗时: " + duration + "ms)", throwable);
        }
    }
    
    /**
     * 根据异常推断类型
     */
    private static ExceptionType inferExceptionType(@Nullable Throwable throwable) {
        if (throwable == null) {
            return ExceptionType.UNKNOWN_ERROR;
        }
        
        String className = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        
        // 根据异常类型和消息推断
        if (className.contains("Network") || className.contains("Socket") || 
            className.contains("Http") || className.contains("Connect")) {
            return ExceptionType.NETWORK_ERROR;
        }
        
        if (className.contains("Permission") || className.contains("Security")) {
            return ExceptionType.PERMISSION_ERROR;
        }
        
        if (className.contains("IllegalState") || 
            (message != null && (message.contains("状态") || message.contains("state")))) {
            return ExceptionType.STATE_ERROR;
        }
        
        if (className.contains("NullPointer") || className.contains("IllegalArgument") ||
            (message != null && (message.contains("数据") || message.contains("参数")))) {
            return ExceptionType.DATA_ERROR;
        }
        
        if (className.contains("Inflate") || className.contains("View") ||
            (message != null && (message.contains("UI") || message.contains("视图")))) {
            return ExceptionType.UI_ERROR;
        }
        
        return ExceptionType.UNKNOWN_ERROR;
    }
    
    /**
     * 特定异常类型的额外处理
     */
    private static void handleSpecificException(@NonNull String module, @NonNull ExceptionType type, @NonNull Throwable throwable) {
        switch (type) {
            case NETWORK_ERROR:
                // 网络异常可能需要重试机制
                logWarning(module, "建议检查网络连接或实施重试机制");
                break;
                
            case STATE_ERROR:
                // 状态异常可能需要重置状态
                logWarning(module, "建议检查状态管理逻辑或重置相关状态");
                break;
                
            case UI_ERROR:
                // UI异常可能影响用户体验
                logWarning(module, "UI异常可能影响用户体验，请优先修复");
                break;
                
            case PERMISSION_ERROR:
                // 权限异常需要用户操作
                logWarning(module, "权限异常需要引导用户授权");
                break;
                
            default:
                // 其他异常的通用处理
                break;
        }
    }
    
    /**
     * 格式化对象为日志字符串
     */
    public static String formatObject(@Nullable Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return "\"" + obj + "\"";
        }
        
        return obj.toString();
    }
    
    /**
     * 安全地获取对象的字符串表示（处理可能的toString异常）
     */
    public static String safeToString(@Nullable Object obj) {
        if (obj == null) {
            return "null";
        }
        
        try {
            return obj.toString();
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode()) + "(toString异常)";
        }
    }
}