package io.openim.android.ouicore.config;

/**
 * 音视频通话相关配置常量
 * 统一管理魔法数字，避免硬编码
 */
public class CallingConfig {
    
    // === 群组通话成员限制 ===
    /**
     * 群组通话最大成员数（不包括发起者）
     * 最多8人 + 发起者 = 9人同时通话
     */
    public static final int MAX_GROUP_CALL_MEMBERS = 8;
    
    /**
     * 群组通话总人数限制（包括发起者）
     */
    public static final int MAX_GROUP_CALL_TOTAL = MAX_GROUP_CALL_MEMBERS + 1; // 9人
    
    // === 超时配置 ===
    /**
     * 通话连接超时时间（秒）
     */
    public static final int CALL_CONNECTION_TIMEOUT_SECONDS = 30;
    
    /**
     * 信令等待超时时间（秒）
     */
    public static final int SIGNALING_TIMEOUT_SECONDS = 60;
    
    /**
     * 成员选择Activity超时时间（秒）
     */
    public static final int MEMBER_SELECTION_TIMEOUT_SECONDS = 300; // 5分钟
    
    // === 性能配置 ===
    /**
     * 视频渲染器最大数量
     */
    public static final int MAX_VIDEO_RENDERERS = 9;
    
    /**
     * 音频缓冲区大小
     */
    public static final int AUDIO_BUFFER_SIZE = 1024;
    
    /**
     * 视频帧率限制
     */
    public static final int MAX_VIDEO_FPS = 30;
    
    // === 日志配置 ===
    /**
     * 日志输出最大行数
     */
    public static final int MAX_LOG_LINES = 500;
    
    /**
     * 终端输出最小行数
     */
    public static final int MIN_TERMINAL_LINES = 10;
    
    // === UI配置 ===
    /**
     * Toast显示时长（毫秒）
     */
    public static final int TOAST_DURATION_MS = 3000;
    
    /**
     * 进度条更新间隔（毫秒）
     */
    public static final int PROGRESS_UPDATE_INTERVAL_MS = 100;
    
    // === 网络配置 ===
    /**
     * HTTP请求超时时间（毫秒）
     */
    public static final int HTTP_TIMEOUT_MS = 30000; // 30秒
    
    /**
     * WebSocket心跳间隔（毫秒）
     */
    public static final int WEBSOCKET_HEARTBEAT_INTERVAL_MS = 30000; // 30秒
    
    // === 存储配置 ===
    /**
     * 通话记录保存天数
     */
    public static final int CALL_HISTORY_RETENTION_DAYS = 30;
    
    /**
     * 缓存文件最大大小（MB）
     */
    public static final int MAX_CACHE_SIZE_MB = 100;
    
    // === 权限相关 ===
    /**
     * 权限请求超时时间（秒）
     */
    public static final int PERMISSION_REQUEST_TIMEOUT_SECONDS = 30;
    
    // === 调试模式 ===
    /**
     * 是否启用调试日志
     */
    public static final boolean DEBUG_LOGGING_ENABLED = true;
    
    /**
     * 是否启用性能监控
     */
    public static final boolean PERFORMANCE_MONITORING_ENABLED = false;
    
    private CallingConfig() {
        // 私有构造函数，防止实例化
    }
}