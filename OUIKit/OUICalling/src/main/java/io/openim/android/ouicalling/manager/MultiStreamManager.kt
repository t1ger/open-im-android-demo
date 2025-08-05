package io.openim.android.ouicalling.manager

import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.*
import io.livekit.android.room.participant.ConnectionQuality
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 多路视频流管理器
 * 专门负责多路视频流的自动分发、优先级管理和渲染优化
 */
class MultiStreamManager(
    private val room: Room,
    private val coroutineScope: CoroutineScope
) {
    
    // 视频流优先级映射 <ParticipantId, Priority>
    private val streamPriorities = ConcurrentHashMap<String, StreamPriority>()
    
    // 活跃渲染器映射 <ParticipantId, RendererInfo>
    private val activeRenderers = ConcurrentHashMap<String, RendererInfo>()
    
    // 网络质量监控
    private val networkQualityMap = ConcurrentHashMap<String, ConnectionQuality>()
    
    // 说话者检测状态
    private val _activeSpeakers = MutableStateFlow<Set<String>>(emptySet())
    val activeSpeakers: StateFlow<Set<String>> = _activeSpeakers.asStateFlow()
    
    // 主要说话者（自动聚焦）
    private val _primarySpeaker = MutableStateFlow<String?>(null)
    val primarySpeaker: StateFlow<String?> = _primarySpeaker.asStateFlow()
    
    // 自适应质量控制
    private val _adaptiveQualityEnabled = MutableStateFlow(true)
    val adaptiveQualityEnabled: StateFlow<Boolean> = _adaptiveQualityEnabled.asStateFlow()
    
    // 配置参数
    private val maxSimultaneousStreams = 9 // 最多9路视频
    private val qualityUpdateInterval = 2000L // 质量更新间隔2秒
    private val speakerDetectionThreshold = 0.3f // 说话检测阈值
    
    init {
        startQualityMonitoring()
        startSpeakerDetection()
    }
    
    /**
     * 注册视频流
     * @param participantId 参与者ID
     * @param renderer 视频渲染器
     * @param priority 初始优先级
     */
    fun registerVideoStream(
        participantId: String,
        renderer: TextureViewRenderer,
        priority: StreamPriority = StreamPriority.NORMAL
    ) {
        try {
            Timber.d { "[MultiStreamManager] 注册视频流: $participantId, 优先级: $priority" }
            
            // 设置优先级
            streamPriorities[participantId] = priority
            
            // 记录渲染器信息
            activeRenderers[participantId] = RendererInfo(
                renderer = renderer,
                participantId = participantId,
                isActive = false,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            // 触发优先级调度
            scheduleStreamPriorities()
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 注册视频流失败: $participantId" }
        }
    }
    
    /**
     * 注销视频流
     */
    fun unregisterVideoStream(participantId: String) {
        try {
            Timber.d { "[MultiStreamManager] 注销视频流: $participantId" }
            
            streamPriorities.remove(participantId)
            activeRenderers.remove(participantId)
            
            // 重新调度剩余流
            scheduleStreamPriorities()
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 注销视频流失败: $participantId" }
        }
    }
    
    /**
     * 视频流优先级调度算法
     */
    private fun scheduleStreamPriorities() {
        coroutineScope.launch {
            try {
                // 1. 获取所有参与者及其优先级
                val participants = getAllParticipantsWithPriority()
                
                // 2. 按优先级排序
                val sortedParticipants = participants.sortedWith(compareBy<ParticipantPriorityInfo> { 
                    it.priority.ordinal 
                }.thenBy { 
                    it.networkQuality.ordinal 
                }.thenByDescending { 
                    it.isSpeaking 
                })
                
                // 3. 选择前N路进行渲染
                val selectedForRendering = sortedParticipants.take(maxSimultaneousStreams)
                
                // 4. 启用/禁用渲染
                updateRenderingStates(selectedForRendering)
                
                Timber.v { "[MultiStreamManager] 优先级调度完成: ${selectedForRendering.size}路视频" }
                
            } catch (e: Exception) {
                Timber.e(e) { "[MultiStreamManager] 视频流调度失败" }
            }
        }
    }
    
    /**
     * 获取所有参与者及其优先级信息
     */
    private fun getAllParticipantsWithPriority(): List<ParticipantPriorityInfo> {
        return streamPriorities.map { (participantId, priority) ->
            ParticipantPriorityInfo(
                participantId = participantId,
                priority = priority,
                networkQuality = networkQualityMap[participantId] ?: ConnectionQuality.UNKNOWN,
                isSpeaking = _activeSpeakers.value.contains(participantId),
                isPrimaryFocus = _primarySpeaker.value == participantId
            )
        }
    }
    
    /**
     * 更新渲染状态
     */
    private fun updateRenderingStates(selectedParticipants: List<ParticipantPriorityInfo>) {
        // 禁用所有当前渲染
        activeRenderers.values.forEach { rendererInfo ->
            if (rendererInfo.isActive) {
                setRendererActive(rendererInfo.participantId, false)
            }
        }
        
        // 启用选中的渲染
        selectedParticipants.forEach { participantInfo ->
            setRendererActive(participantInfo.participantId, true)
        }
    }
    
    /**
     * 设置渲染器激活状态
     */
    private fun setRendererActive(participantId: String, isActive: Boolean) {
        val rendererInfo = activeRenderers[participantId] ?: return
        
        try {
            if (isActive && !rendererInfo.isActive) {
                // 激活渲染器
                val participant = getParticipantById(participantId)
                if (participant != null) {
                    bindVideoToRenderer(rendererInfo.renderer, participant)
                    rendererInfo.isActive = true
                    rendererInfo.lastUpdateTime = System.currentTimeMillis()
                    
                    Timber.d { "[MultiStreamManager] 激活视频渲染: $participantId" }
                }
            } else if (!isActive && rendererInfo.isActive) {
                // 停用渲染器
                unbindVideoFromRenderer(rendererInfo.renderer)
                rendererInfo.isActive = false
                
                Timber.d { "[MultiStreamManager] 停用视频渲染: $participantId" }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 设置渲染器状态失败: $participantId" }
        }
    }
    
    /**
     * 绑定视频到渲染器（自适应质量）
     */
    private suspend fun bindVideoToRenderer(renderer: TextureViewRenderer, participant: Participant) {
        try {
            // 获取最佳质量的视频轨道
            val videoTrack = getBestQualityVideoTrack(participant)
            
            if (videoTrack != null) {
                // 应用自适应质量设置
                if (_adaptiveQualityEnabled.value) {
                    applyAdaptiveQuality(videoTrack, participant)
                }
                
                // 绑定到渲染器
                if (videoTrack is RemoteVideoTrack) {
                    videoTrack.addRenderer(
                        renderer,
                        io.livekit.android.room.track.video.ViewVisibility(renderer.rootView)
                    )
                } else {
                    videoTrack.addRenderer(renderer)
                }
                
                renderer.tag = videoTrack
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 绑定视频到渲染器失败" }
        }
    }
    
    /**
     * 从渲染器解绑视频
     */
    private fun unbindVideoFromRenderer(renderer: TextureViewRenderer) {
        try {
            val videoTrack = renderer.tag as? VideoTrack
            if (videoTrack != null) {
                videoTrack.removeRenderer(renderer)
                renderer.tag = null
            }
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 解绑视频失败" }
        }
    }
    
    /**
     * 获取最佳质量的视频轨道
     */
    private fun getBestQualityVideoTrack(participant: Participant): VideoTrack? {
        // 优先选择屏幕共享，其次是摄像头
        return participant.getTrackPublication(Track.Source.SCREEN_SHARE)?.track as? VideoTrack
            ?: participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }
    
    /**
     * 应用自适应质量控制
     */
    private suspend fun applyAdaptiveQuality(videoTrack: VideoTrack, participant: Participant) {
        try {
            val connectionQuality = networkQualityMap[participant.identity?.value]
            
            if (connectionQuality != null && videoTrack is RemoteVideoTrack) {
                val publication = participant.getTrackPublication(Track.Source.CAMERA) ?: return
                
                // 根据网络质量调整订阅质量
                val targetQuality = when (connectionQuality) {
                    ConnectionQuality.EXCELLENT, ConnectionQuality.GOOD -> {
                        VideoQuality.HIGH
                    }
                    ConnectionQuality.POOR -> {
                        VideoQuality.MEDIUM  
                    }
                    ConnectionQuality.LOST -> {
                        VideoQuality.LOW
                    }
                    else -> VideoQuality.MEDIUM
                }
                
                // 应用质量设置
                publication.setVideoQuality(targetQuality)
                
                Timber.v { "[MultiStreamManager] 自适应质量调整: ${participant.identity?.value} -> $targetQuality" }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 应用自适应质量失败" }
        }
    }
    
    /**
     * 开始网络质量监控
     */
    private fun startQualityMonitoring() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    updateNetworkQualities()
                    
                    // 触发重新调度（如果质量发生显著变化）
                    scheduleStreamPriorities()
                    
                    delay(qualityUpdateInterval)
                    
                } catch (e: Exception) {
                    Timber.e(e) { "[MultiStreamManager] 质量监控异常" }
                    delay(qualityUpdateInterval)
                }
            }
        }
    }
    
    /**
     * 更新网络质量信息
     */
    private fun updateNetworkQualities() {
        room.remoteParticipants.values.forEach { participant ->
            val participantId = participant.identity?.value
            if (participantId != null) {
                val currentQuality = participant.connectionQuality
                val previousQuality = networkQualityMap[participantId]
                
                // 质量发生变化时记录
                if (currentQuality != previousQuality) {
                    networkQualityMap[participantId] = currentQuality
                    Timber.v { "[MultiStreamManager] 网络质量更新: $participantId -> $currentQuality" }
                }
            }
        }
    }
    
    /**
     * 开始说话者检测
     */
    private fun startSpeakerDetection() {
        coroutineScope.launch {
            // 监听LiveKit的activeSpeakers事件
            room::activeSpeakers.flow.collect { speakers ->
                val speakerIds = speakers.mapNotNull { it.identity?.value }.toSet()
                _activeSpeakers.value = speakerIds
                
                // 更新主要说话者（选择第一个远程说话者）
                val primarySpeaker = speakers
                    .filterIsInstance<RemoteParticipant>()
                    .firstOrNull()?.identity?.value
                
                if (primarySpeaker != _primarySpeaker.value) {
                    _primarySpeaker.value = primarySpeaker
                    
                    // 主要说话者变化时，提升其优先级
                    if (primarySpeaker != null) {
                        updateStreamPriority(primarySpeaker, StreamPriority.HIGH)
                    }
                    
                    Timber.d { "[MultiStreamManager] 主要说话者变更: $primarySpeaker" }
                }
                
                // 触发优先级重新调度
                scheduleStreamPriorities()
            }
        }
    }
    
    /**
     * 更新流优先级
     */
    fun updateStreamPriority(participantId: String, priority: StreamPriority) {
        val oldPriority = streamPriorities[participantId]
        if (oldPriority != priority) {
            streamPriorities[participantId] = priority
            Timber.d { "[MultiStreamManager] 优先级更新: $participantId $oldPriority -> $priority" }
            
            // 触发重新调度
            scheduleStreamPriorities()
        }
    }
    
    /**
     * 切换自适应质量控制
     */
    fun setAdaptiveQualityEnabled(enabled: Boolean) {
        _adaptiveQualityEnabled.value = enabled
        Timber.d { "[MultiStreamManager] 自适应质量控制: $enabled" }
        
        if (enabled) {
            scheduleStreamPriorities()
        }
    }
    
    /**
     * 根据ID获取参与者
     */
    private fun getParticipantById(participantId: String): Participant? {
        return room.remoteParticipants.values.find { 
            it.identity?.value == participantId 
        }
    }
    
    /**
     * 获取流统计信息
     */
    fun getStreamStatistics(): StreamStatistics {
        return StreamStatistics(
            totalStreams = streamPriorities.size,
            activeStreams = activeRenderers.values.count { it.isActive },
            highPriorityStreams = streamPriorities.values.count { it == StreamPriority.HIGH },
            mediumPriorityStreams = streamPriorities.values.count { it == StreamPriority.NORMAL },
            lowPriorityStreams = streamPriorities.values.count { it == StreamPriority.LOW }
        )
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[MultiStreamManager] 清理多路视频流管理器资源" }
            
            // 清理所有渲染器
            activeRenderers.values.forEach { rendererInfo ->
                if (rendererInfo.isActive) {
                    unbindVideoFromRenderer(rendererInfo.renderer)
                }
            }
            
            // 清理数据
            streamPriorities.clear()
            activeRenderers.clear()
            networkQualityMap.clear()
            
        } catch (e: Exception) {
            Timber.e(e) { "[MultiStreamManager] 清理资源异常" }
        }
    }
}

/**
 * 视频流优先级枚举
 */
enum class StreamPriority {
    HIGH,    // 高优先级（主要说话者、屏幕共享）
    NORMAL,  // 普通优先级
    LOW      // 低优先级（网络差、非活跃用户）
}

/**
 * 渲染器信息
 */
data class RendererInfo(
    val renderer: TextureViewRenderer,
    val participantId: String,
    var isActive: Boolean,
    var lastUpdateTime: Long
)

/**
 * 参与者优先级信息
 */
data class ParticipantPriorityInfo(
    val participantId: String,
    val priority: StreamPriority,
    val networkQuality: ConnectionQuality,
    val isSpeaking: Boolean,
    val isPrimaryFocus: Boolean
)

/**
 * 流统计信息
 */
data class StreamStatistics(
    val totalStreams: Int,
    val activeStreams: Int,
    val highPriorityStreams: Int,
    val mediumPriorityStreams: Int,
    val lowPriorityStreams: Int
)