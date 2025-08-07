package io.openim.android.ouicalling.entity

/**
 * 性能总结报告
 * Week 2 Day 6: 用于展示多路视频流管理的整体性能表现
 */
data class PerformanceSummary(
    val monitoringDurationMs: Long = 0L,
    val totalFramesRendered: Long = 0L,
    val totalQualitySwitches: Int = 0,
    val totalPrioritySwitches: Int = 0,
    val averageMemoryUsageMb: Float = 0f,
    val averageCpuUsage: Float = 0f,
    val peakMemoryUsageMb: Float = 0f,
    val peakCpuUsage: Float = 0f,
    val networkStability: NetworkStability = NetworkStability.STABLE,
    val startTime: Long = 0L,
    val endTime: Long = System.currentTimeMillis()
) {
    
    enum class NetworkStability {
        STABLE, UNSTABLE, POOR
    }
    
    /**
     * 获取平均帧率
     */
    fun getAverageFrameRate(): Float {
        val durationSeconds = monitoringDurationMs / 1000f
        return if (durationSeconds > 0) {
            totalFramesRendered / durationSeconds
        } else 0f
    }
    
    /**
     * 获取性能评分 (0-100)
     */
    fun getPerformanceScore(): Int {
        var score = 100
        
        // 根据CPU使用率扣分
        when {
            averageCpuUsage > 80f -> score -= 30
            averageCpuUsage > 60f -> score -= 20
            averageCpuUsage > 40f -> score -= 10
        }
        
        // 根据内存使用扣分
        when {
            averageMemoryUsageMb > 500f -> score -= 25
            averageMemoryUsageMb > 300f -> score -= 15
            averageMemoryUsageMb > 200f -> score -= 5
        }
        
        // 根据网络稳定性扣分
        when (networkStability) {
            NetworkStability.POOR -> score -= 30
            NetworkStability.UNSTABLE -> score -= 15
            NetworkStability.STABLE -> score -= 0
        }
        
        return maxOf(0, score)
    }
    
    /**
     * 获取性能等级描述
     */
    fun getPerformanceLevel(): String {
        return when (getPerformanceScore()) {
            in 90..100 -> "优秀"
            in 75..89 -> "良好"
            in 60..74 -> "一般"
            in 40..59 -> "较差"
            else -> "差"
        }
    }
    
    /**
     * 获取监控持续时间（格式化）
     */
    fun getFormattedDuration(): String {
        val seconds = monitoringDurationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h${minutes % 60}m${seconds % 60}s"
            minutes > 0 -> "${minutes}m${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}