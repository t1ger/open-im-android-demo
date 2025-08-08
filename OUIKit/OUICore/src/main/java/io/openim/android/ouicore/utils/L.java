package io.openim.android.ouicore.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Log统一管理类
 * 
 * 增强功能：
 * 1. 统一异常处理支持
 * 2. 关键流程日志记录
 * 3. 完全向后兼容旧的API
 */
public class L {
    public static boolean isDebug = true;// 是否需要打印bug，可以在application的onCreate函数里面初始化
    private static final String TAG = "openIM";

    public static void setDebug(boolean isDebug) {
        L.isDebug = isDebug;
    }

    // 下面四个是默认tag的函数
    public static void i(String msg) {
        if (isDebug)
            Log.i(TAG, msg);
    }

    public static void d(String msg) {
        if (isDebug)
            Log.d(TAG, msg);
    }

    public static void e(String msg) {
        if (isDebug)
            Log.e(TAG, msg);
    }

    public static void v(String msg) {
        if (isDebug)
            Log.v(TAG, msg);
    }

    public static void w(String msg) {
        if (isDebug)
            Log.w(TAG, msg);
    }

    // 下面是传入自定义tag的函数
    public static void i(String tag, String msg) {
        if (isDebug)
            Log.i(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (isDebug)
            Log.d(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (isDebug)
            Log.e(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (isDebug)
            Log.v(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (isDebug)
            Log.w(tag, msg);
    }
    
    // ======== 增强功能：异常处理支持 ========
    
    /**
     * 错误日志带异常信息
     */
    public static void e(String msg, Throwable throwable) {
        if (isDebug)
            Log.e(TAG, msg, throwable);
    }
    
    public static void e(String tag, String msg, Throwable throwable) {
        if (isDebug)
            Log.e(tag, msg, throwable);
    }
    
    /**
     * 警告日志带异常信息
     */
    public static void w(String msg, Throwable throwable) {
        if (isDebug)
            Log.w(TAG, msg, throwable);
    }
    
    public static void w(String tag, String msg, Throwable throwable) {
        if (isDebug)
            Log.w(tag, msg, throwable);
    }
    
    // ======== 增强功能：关键流程日志 ========
    
    /**
     * 关键流程日志（生产环境也会输出）
     */
    public static void critical(String msg) {
        Log.i(TAG, "[CRITICAL] " + msg);
    }
    
    public static void critical(String tag, String msg) {
        Log.i(tag, "[CRITICAL] " + msg);
    }
    
    /**
     * 状态变化日志
     */
    public static void stateChange(String tag, String fromState, String toState) {
        critical(tag, "状态切换: " + fromState + " → " + toState);
    }
    
    /**
     * 业务流程日志
     */
    public static void businessFlow(String tag, String operation, String details) {
        critical(tag, "业务流程: " + operation + " - " + details);
    }
    
    // ======== 增强功能：简化的异常处理 ========
    
    /**
     * 快速异常处理
     */
    public static void handleException(String tag, String operation, Throwable throwable) {
        String msg = "操作失败: " + operation;
        if (throwable != null) {
            e(tag, msg, throwable);
        } else {
            e(tag, msg);
        }
    }
    
    /**
     * 带重试建议的异常处理
     */
    public static void handleRetryableException(String tag, String operation, Throwable throwable) {
        handleException(tag, operation + " (建议重试)", throwable);
    }
    
    /**
     * 安全对象转字符串
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
