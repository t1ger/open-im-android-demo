package io.openim.android.ouicalling.entity

/**
 * 视频流统计信息
 * Week 2 Day 6: 用于多路视频流管理的性能统计
 */
data class StreamStatistics(
    val totalStreams: Int = 0,
    val activeStreams: Int = 0,
    val highPriorityStreams: Int = 0,
    val mediumPriorityStreams: Int = 0,
    val lowPriorityStreams: Int = 0,
    val averageFrameRate: Float = 0f,
    val totalBandwidthKbps: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取流利用率百分比
     */
    fun getStreamUtilization(): Float {
        return if (totalStreams > 0) {
            (activeStreams.toFloat() / totalStreams) * 100f
        } else 0f
    }
    
    /**
     * 检查是否有高优先级流
     */
    fun hasHighPriorityStreams(): Boolean = highPriorityStreams > 0
    
    /**
     * 获取优先级分布描述
     */
    fun getPriorityDistribution(): String {
        return "高:$highPriorityStreams, 中:$mediumPriorityStreams, 低:$lowPriorityStreams"
    }
}