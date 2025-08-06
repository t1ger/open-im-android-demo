package io.openim.android.ouicalling.manager

import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.*
import io.livekit.android.room.track.video.ViewVisibility
import io.livekit.android.util.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

/**
 * 视频绑定管理器
 * 专门负责视频轨道的绑定、解绑和渲染管理
 */
class VideoBindingManager(
    private val room: Room,
    private val coroutineScope: CoroutineScope
) {
    
    // 活跃的绑定映射 <ViewRenderer -> VideoTrack>
    private val activeBindings = mutableMapOf<TextureViewRenderer, VideoTrack>()
    
    /**
     * 绑定远程视频渲染器
     * @param viewRenderer 视频渲染器
     * @param participant 参与者
     * @param scope 协程作用域
     */
    suspend fun bindRemoteViewRenderer(
        viewRenderer: TextureViewRenderer, 
        participant: Participant, 
        scope: CoroutineScope
    ) {
        try {
            Timber.d { "[VideoBindingManager] 绑定远程视频渲染器: ${participant.identity?.value}" }
            
            // 先解绑之前的视频轨道
            unbindVideoRenderer(viewRenderer)
            
            // 观察视频轨道变化
            val videoTrackPubFlow = try {
                // LiveKit 2.0.1 API: 直接使用 videoTrackPublications
                flowOf(participant.videoTrackPublications)
                    .map { videoTracks -> participant to videoTracks }
                    .flatMapLatest { (participant, videoTracks) ->
                        // 优先选择屏幕共享，其次是摄像头
                        val trackPublication = participant.getTrackPublication(Track.Source.SCREEN_SHARE) 
                            ?: participant.getTrackPublication(Track.Source.CAMERA)
                            ?: videoTracks.values.firstOrNull()
                        flowOf<TrackPublication?>(trackPublication)
                    }
            } catch (e: Exception) {
                flowOf<TrackPublication?>(null)
            }
            
            scope.launch {
                try {
                    videoTrackPubFlow.flatMapLatest { pub: TrackPublication? ->
                        if (pub != null) {
                            try {
                                // 修复: track 属性可能不支持 asFlow(), 使用 flowOf
                                flowOf<VideoTrack?>(pub.track as? VideoTrack)
                            } catch (e: Exception) {
                                flowOf<VideoTrack?>(null)
                            }
                        } else {
                            flowOf<VideoTrack?>(null)
                        }
                    }.collect { videoTrack: VideoTrack? ->
                        if (videoTrack is VideoTrack) {
                            bindVideoTrack(viewRenderer, videoTrack)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e) { "[VideoBindingManager] Video track Flow collection error" }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 绑定远程视频渲染器失败" }
        }
    }
    
    /**
     * 绑定视频轨道到渲染器
     * @param viewRenderer 视频渲染器
     * @param videoTrack 视频轨道
     */
    fun bindVideoTrack(viewRenderer: TextureViewRenderer, videoTrack: VideoTrack) {
        try {
            // 先解绑之前的轨道
            unbindVideoRenderer(viewRenderer)
            
            // 绑定new轨道
            viewRenderer.tag = videoTrack
            activeBindings[viewRenderer] = videoTrack
            
            if (videoTrack is RemoteVideoTrack) {
                videoTrack.addRenderer(
                    viewRenderer, 
                    ViewVisibility(viewRenderer.rootView)
                )
            } else {
                videoTrack.addRenderer(viewRenderer)
            }
            
            Timber.d { "[VideoBindingManager] 视频轨道绑定成功: ${videoTrack.name}" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 绑定视频轨道失败" }
        }
    }
    
    /**
     * 解绑视频渲染器
     * @param viewRenderer 视频渲染器
     */
    fun unbindVideoRenderer(viewRenderer: TextureViewRenderer) {
        try {
            val videoTrack = activeBindings[viewRenderer] ?: viewRenderer.tag as? VideoTrack
            
            if (videoTrack != null) {
                videoTrack.removeRenderer(viewRenderer)
                viewRenderer.tag = null
                activeBindings.remove(viewRenderer)
                
                Timber.d { "[VideoBindingManager] 视频渲染器解绑成功: ${videoTrack.name}" }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 解绑视频渲染器失败" }
        }
    }
    
    /**
     * 为GroupMemberAdapter绑定群组成员视频渲染器
     * @param viewRenderer 视频渲染器
     * @param participantId 参与者ID
     * @param groupCallManager 群组通话管理器引用
     */
    suspend fun bindGroupMemberVideoRenderer(
        viewRenderer: TextureViewRenderer,
        participantId: String,
        groupCallManager: GroupCallManager
    ) {
        try {
            // 获取对应的参与者
            val participant = groupCallManager.getParticipantById(participantId)
            if (participant == null) {
                Timber.w { "[VideoBindingManager] 未找到参与者: $participantId" }
                return
            }
            
            // 绑定视频渲染器
            bindRemoteViewRenderer(viewRenderer, participant, coroutineScope)
            
            Timber.d { "[VideoBindingManager] 成功绑定群组成员视频渲染器: $participantId" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 绑定群组成员视频渲染器失败: $participantId" }
        }
    }
    
    /**
     * Java友好的非异步视频绑定方法
     * @param viewRenderer 视频渲染器  
     * @param participantId 参与者ID
     * @param groupCallManager 群组通话管理器引用
     */
    fun bindGroupMemberVideoRendererSync(
        viewRenderer: TextureViewRenderer,
        participantId: String,
        groupCallManager: GroupCallManager
    ) {
        coroutineScope.launch {
            try {
                bindGroupMemberVideoRenderer(viewRenderer, participantId, groupCallManager)
            } catch (e: Exception) {
                Timber.e(e) { "[VideoBindingManager] 同步绑定群组成员视频渲染器失败: $participantId" }
            }
        }
    }
    
    /**
     * 获取参与者的视频轨道
     * @param participant 参与者
     * @return 视频轨道或null
     */
    fun getVideoTrack(participant: Participant): VideoTrack? {
        return participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }
    
    /**
     * 获取所有活跃的绑定信息
     */
    fun getActiveBindings(): Map<TextureViewRenderer, VideoTrack> {
        return activeBindings.toMap()
    }
    
    /**
     * 清理所有绑定
     */
    fun clearAllBindings() {
        try {
            Timber.d { "[VideoBindingManager] 清理所有视频绑定: ${activeBindings.size} 个" }
            
            activeBindings.keys.toList().forEach { renderer ->
                unbindVideoRenderer(renderer)
            }
            
            activeBindings.clear()
            
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 清理所有绑定异常" }
        }
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[VideoBindingManager] 清理视频绑定管理器资源" }
            clearAllBindings()
        } catch (e: Exception) {
            Timber.e(e) { "[VideoBindingManager] 清理资源异常" }
        }
    }
}