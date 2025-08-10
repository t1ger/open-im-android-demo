package io.openim.android.ouicalling.manager

import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.util.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

/**
 * 信令轨道变化事件 - 用于替代直接访问LiveKit API
 */
sealed class SignalingTrackChange {
    data class TrackEnabled(
        val userId: String,
        val mediaType: String, // "audio" or "video"
        val isEnabled: Boolean
    ) : SignalingTrackChange()
    
    data class TrackDisabled(
        val userId: String,
        val mediaType: String
    ) : SignalingTrackChange()
}

/**
 * 群组通话管理器
 * 专门负责群组通话的业务逻辑：成员管理、事件处理等
 */
class GroupCallManager(
    private val room: Room,
    private val coroutineScope: CoroutineScope
) {
    
    // 是否群组通话模式
    private val _isGroupCall = MutableStateFlow(false)
    val isGroupCall: StateFlow<Boolean> = _isGroupCall.asStateFlow()
    
    // 群组成员参与者映射 <UserID, Participant>
    private val _groupMembers = MutableStateFlow<Map<String, Participant>>(emptyMap())
    val groupMembers: StateFlow<Map<String, Participant>> = _groupMembers.asStateFlow()
    
    // 群组通话的参与者变更监听器
    private val _groupParticipantChanges = MutableSharedFlow<GroupParticipantChange>()
    val groupParticipantChanges: SharedFlow<GroupParticipantChange> = _groupParticipantChanges.asSharedFlow()
    
    // 预期的群组成员ID列表
    private var expectedMemberIds: List<String> = emptyList()
    
    // 房间事件监听Job
    private var roomEventJob: Job? = null
    
    // ✅ 状态缓存 - 通过信令更新，避免直接访问LiveKit轨道
    private val audioStateCache = mutableMapOf<String, Boolean>()
    private val videoStateCache = mutableMapOf<String, Boolean>()
    private val connectionStateCache = mutableMapOf<String, Boolean>()
    
    /**
     * 连接到群组房间
     * @param url LiveKit服务器URL
     * @param token 房间访问令牌
     * @param memberIds 群组成员ID列表
     * @param callback 连接结果回调
     */
    suspend fun connectToGroupRoom(
        url: String,
        token: String,
        memberIds: List<String>,
        callback: (Result<Boolean>) -> Unit
    ) {
        try {
            Timber.d { "[GroupCallManager] 开始连接群组房间: $url, 成员数: ${memberIds.size}" }
            
            // 设置群组通话模式
            _isGroupCall.value = true
            expectedMemberIds = memberIds
            
            // 连接房间
            room.connect(url = url, token = token)
            
            // 设置音视频状态
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            localParticipant.setCameraEnabled(true)
            
            // 开始监听群组房间中的参与者变化
            startGroupParticipantMonitoring()
            
            Timber.d { "[GroupCallManager] 群组房间连接成功" }
            callback(Result.success(true))
            
        } catch (e: Throwable) {
            Timber.e(e) { "[GroupCallManager] 群组房间连接失败" }
            callback(Result.failure(e))
        }
    }
    
    /**
     * 开始监听群组参与者变化
     * ✅ 修复: 使用信令驱动模式，不直接监听LiveKit事件
     * 所有成员状态变化通过信令同步，LiveKit仅做流管理
     */
    private fun startGroupParticipantMonitoring() {
        Timber.d { "[GroupCallManager] 启动群组参与者监听(信令驱动模式)" }
        // 注意: 不直接监听LiveKit的room.events
        // 所有成员状态变化将通过CallingVM的信令处理来同步
        // 这里只做初始化准备工作
    }
    
    /**
     * 通过信令更新成员状态(取代直接处理LiveKit事件)
     * ✅ 修复: 所有成员状态变化通过信令驱动
     */
    fun updateMemberStateFromSignaling(userId: String, isConnected: Boolean) {
        if (expectedMemberIds.contains(userId)) {
            Timber.d { "[GroupCallManager] 通过信令更新成员状态: $userId -> 连接:$isConnected" }
            
            val currentMembers = _groupMembers.value.toMutableMap()
            
            if (isConnected) {
                // 成员加入 - 从房间中查找对应的Participant
                val participant = room.remoteParticipants.values.find { 
                    it.identity?.value == userId 
                }
                if (participant != null) {
                    currentMembers[userId] = participant
                    _groupMembers.value = currentMembers
                    
                    // ✅ 更新连接状态缓存
                    connectionStateCache[userId] = true
                    
                    // 发送事件通知
                    coroutineScope.launch {
                        _groupParticipantChanges.emit(
                            GroupParticipantChange.Connected(userId, participant)
                        )
                    }
                }
            } else {
                // 成员离开
                val participant = currentMembers.remove(userId)
                _groupMembers.value = currentMembers
                
                // ✅ 更新连接状态缓存
                connectionStateCache[userId] = false
                
                if (participant is RemoteParticipant) {
                    coroutineScope.launch {
                        _groupParticipantChanges.emit(
                            GroupParticipantChange.Disconnected(userId, participant)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 通过信令更新轨道状态(取代直接处理LiveKit事件)
     * ✅ 修复: 所有轨道变化通过信令驱动
     */
    fun updateTrackStateFromSignaling(userId: String, mediaType: String, isEnabled: Boolean) {
        // 查找目标参与者
        val participant = _groupMembers.value[userId] as? RemoteParticipant ?: return
        
        Timber.d { "[GroupCallManager] 通过信令更新轨道状态: $userId, 类型:$mediaType, 启用:$isEnabled" }
        
        // ✅ 修复: 不直接访问轨道，改为发送信令事件
        // 轨道的具体管理交给专门的VideoBindingManager和AudioManager
        val signalTrackChange = when (mediaType.lowercase()) {
            "audio" -> SignalingTrackChange.TrackEnabled(userId, "audio", isEnabled)
            "video" -> SignalingTrackChange.TrackEnabled(userId, "video", isEnabled)
            else -> null
        }
        
        // ✅ 更新状态缓存
        when (mediaType.lowercase()) {
            "audio" -> audioStateCache[userId] = isEnabled
            "video" -> videoStateCache[userId] = isEnabled
        }
        
        // 如果有有效的信令事件，发送轨道状态变化通知
        signalTrackChange?.let {
            coroutineScope.launch {
                _groupParticipantChanges.emit(
                    GroupParticipantChange.TrackStateChanged(
                        userId, 
                        participant, 
                        mediaType, 
                        isEnabled
                    )
                )
            }
        }
    }
    
    /**
     * 获取所有群组参与者（不包含本地用户）
     */
    fun getAllGroupParticipants(): StateFlow<List<Participant>> {
        return _groupMembers.map { memberMap ->
            memberMap.values.toList()
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
    
    /**
     * 根据ID获取参与者
     */
    fun getParticipantById(participantId: String): Participant? {
        // 先检查群组成员
        _groupMembers.value[participantId]?.let { return it }
        
        // 再检查所有远程参与者
        return room.remoteParticipants.values.find { 
            it.identity?.value == participantId 
        }
    }
    
    /**
     * 检查参与者的摄像头是否开启
     * ✅ 修复: 使用缓存机制，遵循信令驱动原则
     */
    fun isParticipantCameraEnabled(participantId: String): Boolean {
        return getParticipantVideoState(participantId)
    }
    
    /**
     * 检查参与者的麦克风是否开启
     * ✅ 修复: 使用缓存机制，遵循信令驱动原则
     */
    fun isParticipantMicrophoneEnabled(participantId: String): Boolean {
        return getParticipantAudioState(participantId)
    }
    
    /**
     * 获取参与者的连接质量 - 通过信令更新，不直接暴露LiveKit Flow
     */
    fun getParticipantConnectionQuality(participantId: String): io.livekit.android.room.participant.ConnectionQuality? {
        val participant = getParticipantById(participantId) ?: return null
        return try {
            // 直接返回当前值，不暴露Flow
            participant.connectionQuality
        } catch (e: Exception) {
            Timber.w(e) { "[GroupCallManager] Failed to get connection quality for $participantId" }
            io.livekit.android.room.participant.ConnectionQuality.UNKNOWN
        }
    }
    
    /**
     * 检查是否为群组通话模式
     */
    fun isGroupCallMode(): Boolean {
        return _isGroupCall.value
    }
    
    /**
     * 重置群组通话状态
     */
    fun resetGroupCallState() {
        _isGroupCall.value = false
        _groupMembers.value = emptyMap()
        expectedMemberIds = emptyList()
        roomEventJob?.cancel()
        roomEventJob = null
        
        // ✅ 清空状态缓存
        audioStateCache.clear()
        videoStateCache.clear()
        connectionStateCache.clear()
        
        Timber.d { "[GroupCallManager] 群组通话状态已重置" }
    }
    
    // ===== 状态查询接口（信令驱动） =====
    
    /**
     * 获取参与者音频状态
     * 通过缓存状态查询，避免直接访问轨道
     * ✅ 修复: 提供给CallViewModel的抽象接口
     */
    fun getParticipantAudioState(userId: String): Boolean {
        // 优先从缓存获取状态
        audioStateCache[userId]?.let { return it }
        
        // 如果缓存中没有，尝试从参与者获取（作为备选）
        return try {
            val participant = getParticipantById(userId)
            val audioEnabled = participant?.isMicrophoneEnabled() ?: false
            // 更新缓存
            audioStateCache[userId] = audioEnabled
            audioEnabled
        } catch (e: Exception) {
            Timber.w(e) { "[GroupCallManager] Failed to get participant audio state: $userId" }
            false
        }
    }
    
    /**
     * 获取参与者视频状态
     * 通过缓存状态查询，避免直接访问轨道
     * ✅ 修复: 提供给CallViewModel的抽象接口
     */
    fun getParticipantVideoState(userId: String): Boolean {
        // 优先从缓存获取状态
        videoStateCache[userId]?.let { return it }
        
        // 如果缓存中没有，尝试从参与者获取（作为备选）
        return try {
            val participant = getParticipantById(userId)
            val videoEnabled = participant?.isCameraEnabled() ?: false
            // 更新缓存
            videoStateCache[userId] = videoEnabled
            videoEnabled
        } catch (e: Exception) {
            Timber.w(e) { "[GroupCallManager] Failed to get participant video state: $userId" }
            false
        }
    }
    
    /**
     * 清空所有参与者状态缓存
     * ✅ 新增: 用于通话结束时清理状态缓存
     */
    fun clearParticipantStates() {
        try {
            audioStateCache.clear()
            videoStateCache.clear()
            connectionStateCache.clear()
            
            Timber.d { "[GroupCallManager] 参与者状态缓存已清空" }
        } catch (e: Exception) {
            Timber.w(e) { "[GroupCallManager] 清空参与者状态缓存失败" }
        }
    }
    
    /**
     * 获取参与者连接状态
     * 通过缓存状态查询，避免直接访问房间状态
     * ✅ 修复: 提供给CallViewModel的抽象接口
     */
    fun getParticipantConnectionState(userId: String): Boolean {
        // 优先从缓存获取状态
        connectionStateCache[userId]?.let { return it }
        
        // 如果缓存中没有，检查参与者是否在groupMembers中
        val isConnected = _groupMembers.value.containsKey(userId)
        connectionStateCache[userId] = isConnected
        return isConnected
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[GroupCallManager] 清理群组通话管理器资源" }
            resetGroupCallState()
        } catch (e: Exception) {
            Timber.e(e) { "[GroupCallManager] 清理资源异常" }
        }
    }
}

/**
 * 群组参与者变更事件
 */
sealed class GroupParticipantChange {
    data class Connected(
        val participantId: String,
        val participant: RemoteParticipant
    ) : GroupParticipantChange()
    
    data class Disconnected(
        val participantId: String,
        val participant: RemoteParticipant
    ) : GroupParticipantChange()
    
    data class TrackSubscribed(
        val participantId: String,
        val participant: RemoteParticipant,
        val publication: TrackPublication
    ) : GroupParticipantChange()
    
    data class TrackUnsubscribed(
        val participantId: String,
        val participant: RemoteParticipant,
        val publication: TrackPublication
    ) : GroupParticipantChange()
    
    data class TrackStateChanged(
        val participantId: String,
        val participant: RemoteParticipant,
        val mediaType: String,
        val isEnabled: Boolean
    ) : GroupParticipantChange()
}