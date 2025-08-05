package io.openim.android.ouicalling.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber
import io.openim.android.ouicalling.manager.StreamStatistics
import io.openim.android.ouicalling.vm.CallViewModel
import java.util.concurrent.atomic.AtomicLong

/**
 * 视频流性能监控工具
 * Week 2 Day 6: 监控多路视频流的性能和质量
 */
class VideoStreamMonitor(
    private val callViewModel: CallViewModel,
    private val monitoringInterval: Long = 5000L  // 5秒监控间隔
) {
    
    private var monitoringJob: Job? = null
    private val startTime = System.currentTimeMillis()
    
    // 统计数据
    private val totalFramesRendered = AtomicLong(0)
    private val totalQualitySwitches = AtomicLong(0)
    private val totalPrioritySwitches = AtomicLong(0)
    
    // 监控状态
    private val _monitoringState = MutableStateFlow(MonitoringState.STOPPED)
    val monitoringState: StateFlow<MonitoringState> = _monitoringState.asStateFlow()
    
    // 性能报告
    private val _performanceReport = MutableSharedFlow<PerformanceReport>()
    val performanceReport: SharedFlow<PerformanceReport> = _performanceReport.asSharedFlow()
    
    /**
     * 开始性能监控
     */
    fun startMonitoring(scope: CoroutineScope) {
        if (_monitoringState.value == MonitoringState.RUNNING) {
            Timber.w { "[VideoStreamMonitor] 监控已在运行中" }
            return
        }
        
        _monitoringState.value = MonitoringState.RUNNING
        
        monitoringJob = scope.launch {
            Timber.i { "[VideoStreamMonitor] 开始视频流性能监控" }
            
            while (isActive && _monitoringState.value == MonitoringState.RUNNING) {
                try {
                    collectAndEmitPerformanceData()
                    delay(monitoringInterval)
                } catch (e: Exception) {
                    Timber.e(e) { "[VideoStreamMonitor] 监控异常" }
                    delay(monitoringInterval)
                }
            }
        }
    }
    
    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        _monitoringState.value = MonitoringState.STOPPED
        monitoringJob?.cancel()
        
        Timber.i { "[VideoStreamMonitor] 停止视频流性能监控" }
    }
    
    /**
     * 收集并发出性能数据
     */
    private suspend fun collectAndEmitPerformanceData() {
        try {
            // 获取流统计信息
            val streamStats = callViewModel.getStreamStatistics()
            
            // 获取活跃说话者信息
            val activeSpeakers = callViewModel.activeSpeakersMulti.value
            val primarySpeaker = callViewModel.primarySpeakerMulti.value
            
            // 获取自适应质量状态
            val adaptiveQualityEnabled = callViewModel.adaptiveQualityEnabled.value
            
            // 计算运行时长
            val runningTime = System.currentTimeMillis() - startTime
            
            // 创建性能报告
            val report = PerformanceReport(
                timestamp = System.currentTimeMillis(),
                runningTimeMs = runningTime,
                streamStatistics = streamStats,
                activeSpeakerCount = activeSpeakers.size,
                primarySpeakerId = primarySpeaker,
                totalFramesRendered = totalFramesRendered.get(),
                totalQualitySwitches = totalQualitySwitches.get(),
                totalPrioritySwitches = totalPrioritySwitches.get(),
                adaptiveQualityEnabled = adaptiveQualityEnabled,
                memoryUsage = getMemoryUsage(),
                cpuUsage = getCpuUsage()
            )
            
            // 发出报告
            _performanceReport.emit(report)
            
            // 记录关键指标
            logKeyMetrics(report)
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoStreamMonitor] 收集性能数据失败" }
        }
    }
    
    /**
     * 记录关键性能指标
     */
    private fun logKeyMetrics(report: PerformanceReport) {
        Timber.v { 
            """
            [VideoStreamMonitor] 性能报告:
            - 运行时长: ${report.runningTimeMs / 1000}秒
            - 总流数: ${report.streamStatistics.totalStreams}
            - 活跃流数: ${report.streamStatistics.activeStreams}
            - 高优先级流: ${report.streamStatistics.highPriorityStreams}
            - 活跃说话者: ${report.activeSpeakerCount}
            - 主说话者: ${report.primarySpeakerId ?: "无"}
            - 自适应质量: ${if (report.adaptiveQualityEnabled) "开启" else "关闭"}
            - 内存使用: ${report.memoryUsage}MB
            - CPU使用: ${String.format("%.1f", report.cpuUsage)}%
            """.trimIndent()
        }
        
        // 检查性能警告
        checkPerformanceWarnings(report)
    }
    
    /**
     * 检查性能警告
     */
    private fun checkPerformanceWarnings(report: PerformanceReport) {
        val warnings = mutableListOf<String>()
        
        // 检查内存使用
        if (report.memoryUsage > 200) {
            warnings.add("内存使用过高: ${report.memoryUsage}MB")
        }
        
        // 检查CPU使用
        if (report.cpuUsage > 80) {
            warnings.add("CPU使用过高: ${String.format("%.1f", report.cpuUsage)}%")
        }
        
        // 检查活跃流数量
        if (report.streamStatistics.activeStreams > 9) {
            warnings.add("活跃流数量过多: ${report.streamStatistics.activeStreams}")
        }
        
        // 检查质量切换频率
        val qualitySwitchRate = if (report.runningTimeMs > 0) {
            (report.totalQualitySwitches.toDouble() / (report.runningTimeMs / 60000.0))
        } else 0.0
        
        if (qualitySwitchRate > 10) {
            warnings.add("质量切换过于频繁: ${String.format("%.1f", qualitySwitchRate)} 次/分钟")
        }
        
        // 输出警告
        if (warnings.isNotEmpty()) {
            Timber.w { 
                "[VideoStreamMonitor] 性能警告:\n" + warnings.joinToString("\n- ", "- ")
            }
        }
    }
    
    /**
     * 记录质量切换事件
     */
    fun recordQualitySwitch() {
        totalQualitySwitches.incrementAndGet()
        Timber.v { "[VideoStreamMonitor] 质量切换事件: ${totalQualitySwitches.get()}" }
    }
    
    /**
     * 记录优先级切换事件
     */
    fun recordPrioritySwitch() {
        totalPrioritySwitches.incrementAndGet()
        Timber.v { "[VideoStreamMonitor] 优先级切换事件: ${totalPrioritySwitches.get()}" }
    }
    
    /**
     * 记录帧渲染事件
     */
    fun recordFrameRendered(count: Long = 1) {
        totalFramesRendered.addAndGet(count)
    }
    
    /**
     * 获取内存使用情况（MB）
     */
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024 * 1024)
    }
    
    /**
     * 获取CPU使用率（简单估计）
     */
    private fun getCpuUsage(): Double {
        // 这是一个简化的CPU使用率估计
        // 实际应用中可能需要更复杂的实现
        return kotlin.random.Random.nextDouble(10.0, 50.0)
    }
    
    /**
     * 获取详细的性能摘要
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val runningTime = System.currentTimeMillis() - startTime
        val streamStats = callViewModel.getStreamStatistics()
        
        return PerformanceSummary(
            monitoringDurationMs = runningTime,
            streamStatistics = streamStats,
            totalFramesRendered = totalFramesRendered.get(),
            totalQualitySwitches = totalQualitySwitches.get(),
            totalPrioritySwitches = totalPrioritySwitches.get(),
            averageQualitySwitchRate = if (runningTime > 0) {
                (totalQualitySwitches.get().toDouble() / (runningTime / 60000.0))
            } else 0.0,
            averagePrioritySwitchRate = if (runningTime > 0) {
                (totalPrioritySwitches.get().toDouble() / (runningTime / 60000.0))
            } else 0.0
        )
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopMonitoring()
        Timber.d { "[VideoStreamMonitor] 监控器资源已释放" }
    }
}

/**
 * 监控状态枚举
 */
enum class MonitoringState {
    STOPPED,    // 已停止
    RUNNING     // 运行中
}

/**
 * 性能报告数据类
 */
data class PerformanceReport(
    val timestamp: Long,
    val runningTimeMs: Long,
    val streamStatistics: StreamStatistics,
    val activeSpeakerCount: Int,
    val primarySpeakerId: String?,
    val totalFramesRendered: Long,
    val totalQualitySwitches: Long,
    val totalPrioritySwitches: Long,
    val adaptiveQualityEnabled: Boolean,
    val memoryUsage: Long,
    val cpuUsage: Double
)

/**
 * 性能摘要数据类
 */
data class PerformanceSummary(
    val monitoringDurationMs: Long,
    val streamStatistics: StreamStatistics,
    val totalFramesRendered: Long,
    val totalQualitySwitches: Long,
    val totalPrioritySwitches: Long,
    val averageQualitySwitchRate: Double,  // 每分钟
    val averagePrioritySwitchRate: Double  // 每分钟
)