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
        
        // 查找对应的轨道
        val trackPublication = when (mediaType.lowercase()) {
            "audio" -> participant.audioTrackPublications.values.firstOrNull()
            "video" -> participant.videoTrackPublications.values.firstOrNull()
            else -> null
        }
        
        // 如果找到轨道，更新其状态并发送事件
        trackPublication?.let { publication ->
            coroutineScope.launch {
                if (isEnabled) {
                    _groupParticipantChanges.emit(
                        GroupParticipantChange.TrackSubscribed(userId, participant, publication)
                    )
                } else {
                    _groupParticipantChanges.emit(
                        GroupParticipantChange.TrackUnsubscribed(userId, participant, publication)
                    )
                }
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
     */
    fun isParticipantCameraEnabled(participantId: String): Boolean {
        val participant = getParticipantById(participantId) ?: return false
        return participant.isCameraEnabled()
    }
    
    /**
     * 检查参与者的麦克风是否开启
     */
    fun isParticipantMicrophoneEnabled(participantId: String): Boolean {
        val participant = getParticipantById(participantId) ?: return false
        return participant.isMicrophoneEnabled()
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
        Timber.d { "[GroupCallManager] 群组通话状态已重置" }
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
}