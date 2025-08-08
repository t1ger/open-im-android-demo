package io.openim.android.ouicalling.monitor;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.openim.android.ouicore.config.CallingConfig;

/**
 * 轻量级性能监控器 - 专注核心指标
 * 
 * 用途说明：
 * 1. 实时监控群组通话性能
 * 2. 及时发现性能问题
 * 3. 为优化决策提供数据支撑
 * 4. 用户体验问题诊断
 */
public class LightweightPerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";
    
    // === 核心监控指标 ===
    private final AtomicInteger totalFramesRendered = new AtomicInteger(0);
    private final AtomicLong totalRenderTime = new AtomicLong(0);
    private final AtomicInteger droppedFrames = new AtomicInteger(0);
    private final AtomicLong networkLatency = new AtomicLong(0);
    private final AtomicInteger qualitySwitches = new AtomicInteger(0);
    private final AtomicInteger connectionReconnects = new AtomicInteger(0);
    
    private long monitorStartTime = 0;
    private boolean isMonitoring = false;
    private Handler monitorHandler;
    private Runnable monitorTask;
    
    /**
     * 开始监控
     */
    public void startMonitoring() {
        if (!CallingConfig.PERFORMANCE_MONITORING_ENABLED) {
            Log.d(TAG, "性能监控已禁用");
            return;
        }
        
        if (isMonitoring) {
            Log.w(TAG, "性能监控已在运行中");
            return;
        }
        
        isMonitoring = true;
        monitorStartTime = SystemClock.elapsedRealtime();
        resetCounters();
        
        startPeriodicReporting();
        
        Log.i(TAG, "轻量级性能监控已启动");
    }
    
    /**
     * 停止监控并输出总结
     */
    public PerformanceSummary stopMonitoring() {
        if (!isMonitoring) {
            return new PerformanceSummary();
        }
        
        isMonitoring = false;
        stopPeriodicReporting();
        
        PerformanceSummary summary = generateSummary();
        
        Log.i(TAG, "性能监控已停止");
        Log.i(TAG, "=== 性能监控总结 ===");
        Log.i(TAG, "监控时长: " + (summary.monitoringDurationMs / 1000) + "秒");
        Log.i(TAG, "渲染帧数: " + summary.totalFramesRendered);
        Log.i(TAG, "平均帧率: " + summary.averageFPS + " FPS");
        Log.i(TAG, "丢帧数: " + summary.droppedFrames);
        Log.i(TAG, "网络延迟: " + summary.averageLatencyMs + "ms");
        Log.i(TAG, "质量切换: " + summary.qualitySwitches + "次");
        Log.i(TAG, "重连次数: " + summary.connectionReconnects + "次");
        
        return summary;
    }
    
    // === 各种性能事件记录方法 ===
    
    /**
     * 记录帧渲染事件
     * 用途: 监控视频渲染性能，发现渲染瓶颈
     */
    public void recordFrameRendered(long renderTimeMs) {
        if (!isMonitoring) return;
        
        totalFramesRendered.incrementAndGet();
        totalRenderTime.addAndGet(renderTimeMs);
        
        // 检测渲染慢的帧（超过33ms = 30FPS）
        if (renderTimeMs > 33) {
            Log.w(TAG, "检测到慢渲染帧: " + renderTimeMs + "ms");
        }
    }
    
    /**
     * 记录丢帧事件
     * 用途: 监控视频流畅度，用户体验关键指标
     */
    public void recordDroppedFrame(String reason) {
        if (!isMonitoring) return;
        
        droppedFrames.incrementAndGet();
        Log.w(TAG, "丢帧事件: " + reason + ", 总丢帧数: " + droppedFrames.get());
        
        // 丢帧率过高时警告
        int currentFrames = totalFramesRendered.get();
        if (currentFrames > 100) { // 至少100帧后开始计算
            float dropRate = (float) droppedFrames.get() / currentFrames;
            if (dropRate > 0.05f) { // 丢帧率超过5%
                Log.e(TAG, "⚠️ 丢帧率过高: " + String.format("%.2f%%", dropRate * 100));
            }
        }
    }
    
    /**
     * 记录网络延迟
     * 用途: 监控网络质量，影响通话质量的关键因素
     */
    public void recordNetworkLatency(long latencyMs) {
        if (!isMonitoring) return;
        
        networkLatency.set(latencyMs);
        
        // 延迟过高警告
        if (latencyMs > 200) {
            Log.w(TAG, "网络延迟较高: " + latencyMs + "ms");
        } else if (latencyMs > 500) {
            Log.e(TAG, "⚠️ 网络延迟严重: " + latencyMs + "ms，可能影响通话质量");
        }
    }
    
    /**
     * 记录视频质量切换
     * 用途: 监控自适应质量调节，网络适应性指标
     */
    public void recordQualitySwitch(String fromQuality, String toQuality, String reason) {
        if (!isMonitoring) return;
        
        qualitySwitches.incrementAndGet();
        Log.i(TAG, "视频质量切换: " + fromQuality + " → " + toQuality + " (" + reason + ")");
        
        // 频繁切换警告
        if (qualitySwitches.get() > 10) {
            long duration = SystemClock.elapsedRealtime() - monitorStartTime;
            if (duration > 0) {
                float switchRate = (float) qualitySwitches.get() * 60000 / duration; // 每分钟切换次数
                if (switchRate > 5) {
                    Log.w(TAG, "⚠️ 视频质量切换频繁: " + String.format("%.1f", switchRate) + "次/分钟");
                }
            }
        }
    }
    
    /**
     * 记录连接重连事件
     * 用途: 监控连接稳定性，网络问题诊断
     */
    public void recordConnectionReconnect(String reason) {
        if (!isMonitoring) return;
        
        connectionReconnects.incrementAndGet();
        Log.w(TAG, "连接重连: " + reason + ", 重连次数: " + connectionReconnects.get());
        
        // 频繁重连警告
        if (connectionReconnects.get() >= 3) {
            Log.e(TAG, "⚠️ 连接不稳定，已重连 " + connectionReconnects.get() + " 次");
        }
    }
    
    /**
     * 记录内存使用情况
     * 用途: 监控内存泄漏，防止OOM
     */
    public void recordMemoryUsage() {
        if (!isMonitoring) return;
        
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        float memoryUsagePercent = (float) usedMemory / maxMemory * 100;
        
        if (memoryUsagePercent > 80) {
            Log.w(TAG, "⚠️ 内存使用率较高: " + String.format("%.1f%%", memoryUsagePercent));
        } else if (memoryUsagePercent > 90) {
            Log.e(TAG, "🚨 内存使用率危险: " + String.format("%.1f%%", memoryUsagePercent) + ", 可能OOM");
        }
    }
    
    /**
     * 开始定期性能报告
     */
    private void startPeriodicReporting() {
        if (monitorHandler == null) {
            monitorHandler = new Handler();
        }
        
        monitorTask = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    generatePeriodicReport();
                    recordMemoryUsage();
                    monitorHandler.postDelayed(this, 30000); // 30秒一次
                }
            }
        };
        
        monitorHandler.postDelayed(monitorTask, 30000);
    }
    
    /**
     * 停止定期报告
     */
    private void stopPeriodicReporting() {
        if (monitorHandler != null && monitorTask != null) {
            monitorHandler.removeCallbacks(monitorTask);
        }
    }
    
    /**
     * 生成定期性能报告
     */
    private void generatePeriodicReport() {
        long currentTime = SystemClock.elapsedRealtime();
        long duration = currentTime - monitorStartTime;
        int frames = totalFramesRendered.get();
        
        if (duration > 0 && frames > 0) {
            float avgFPS = (float) frames * 1000 / duration;
            Log.i(TAG, String.format("性能状态 - FPS: %.1f, 丢帧: %d, 延迟: %dms, 质量切换: %d次", 
                avgFPS, droppedFrames.get(), networkLatency.get(), qualitySwitches.get()));
        }
    }
    
    /**
     * 生成性能总结
     */
    private PerformanceSummary generateSummary() {
        long duration = SystemClock.elapsedRealtime() - monitorStartTime;
        PerformanceSummary summary = new PerformanceSummary();
        
        summary.monitoringDurationMs = duration;
        summary.totalFramesRendered = totalFramesRendered.get();
        summary.droppedFrames = droppedFrames.get();
        summary.qualitySwitches = qualitySwitches.get();
        summary.connectionReconnects = connectionReconnects.get();
        summary.averageLatencyMs = networkLatency.get();
        
        if (duration > 0) {
            summary.averageFPS = (float) summary.totalFramesRendered * 1000 / duration;
        }
        
        if (summary.totalFramesRendered > 0) {
            summary.dropFrameRate = (float) summary.droppedFrames / summary.totalFramesRendered * 100;
        }
        
        return summary;
    }
    
    /**
     * 重置计数器
     */
    private void resetCounters() {
        totalFramesRendered.set(0);
        totalRenderTime.set(0);
        droppedFrames.set(0);
        networkLatency.set(0);
        qualitySwitches.set(0);
        connectionReconnects.set(0);
    }
    
    /**
     * 性能总结数据类
     */
    public static class PerformanceSummary {
        public long monitoringDurationMs = 0;
        public int totalFramesRendered = 0;
        public int droppedFrames = 0;
        public int qualitySwitches = 0;
        public int connectionReconnects = 0;
        public long averageLatencyMs = 0;
        public float averageFPS = 0f;
        public float dropFrameRate = 0f;
        
        @Override
        public String toString() {
            return String.format("PerformanceSummary{duration=%ds, fps=%.1f, dropped=%d(%.2f%%), switches=%d, reconnects=%d, latency=%dms}",
                monitoringDurationMs / 1000, averageFPS, droppedFrames, dropFrameRate, 
                qualitySwitches, connectionReconnects, averageLatencyMs);
        }
    }
}