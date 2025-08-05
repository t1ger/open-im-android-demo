package io.openim.android.ouicalling.manager

import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.TrackPublication
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
     */
    private fun startGroupParticipantMonitoring() {
        roomEventJob?.cancel()
        roomEventJob = coroutineScope.launch {
            try {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.ParticipantConnected -> {
                            handleParticipantConnected(event.participant)
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            handleParticipantDisconnected(event.participant)
                        }
                        is RoomEvent.TrackSubscribed -> {
                            handleTrackSubscribed(event)
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            handleTrackUnsubscribed(event)
                        }
                        else -> {
                            Timber.v { "[GroupCallManager] Room event: $event" }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e) { "[GroupCallManager] Room event collection error" }
            }
        }
    }
    
    /**
     * 处理参与者连接
     */
    private suspend fun handleParticipantConnected(participant: RemoteParticipant) {
        val identity = participant.identity?.value ?: return
        
        if (expectedMemberIds.contains(identity)) {
            Timber.d { "[GroupCallManager] 成员加入: $identity" }
            
            // 更新成员映射
            val currentMembers = _groupMembers.value.toMutableMap()
            currentMembers[identity] = participant
            _groupMembers.value = currentMembers
            
            // 通知参与者变更
            _groupParticipantChanges.emit(
                GroupParticipantChange.Connected(identity, participant)
            )
        }
    }
    
    /**
     * 处理参与者断开连接
     */
    private suspend fun handleParticipantDisconnected(participant: RemoteParticipant) {
        val identity = participant.identity?.value ?: return
        
        Timber.d { "[GroupCallManager] 成员离开: $identity" }
        
        // 从成员映射中移除
        val currentMembers = _groupMembers.value.toMutableMap()
        currentMembers.remove(identity)
        _groupMembers.value = currentMembers
        
        // 通知参与者变更
        _groupParticipantChanges.emit(
            GroupParticipantChange.Disconnected(identity, participant)
        )
    }
    
    /**
     * 处理轨道订阅
     */
    private suspend fun handleTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        val participant = event.participant as? RemoteParticipant ?: return
        val identity = participant.identity?.value ?: return
        
        val trackPublication = event.trackPublication
        Timber.d { "[GroupCallManager] 成员轨道订阅: $identity, track: ${trackPublication.track?.kind}" }
        
        // 通知轨道变更
        _groupParticipantChanges.emit(
            GroupParticipantChange.TrackSubscribed(identity, participant, trackPublication)
        )
    }
    
    /**
     * 处理轨道取消订阅
     */
    private suspend fun handleTrackUnsubscribed(event: RoomEvent.TrackUnsubscribed) {
        val participant = event.participant as? RemoteParticipant ?: return
        val identity = participant.identity?.value ?: return
        
        val trackPublication = event.trackPublication
        Timber.d { "[GroupCallManager] 成员轨道取消订阅: $identity, track: ${trackPublication.track?.kind}" }
        
        // 通知轨道变更
        _groupParticipantChanges.emit(
            GroupParticipantChange.TrackUnsubscribed(identity, participant, trackPublication)
        )
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
     * 获取参与者的连接质量
     */
    fun getParticipantConnectionQuality(participantId: String): StateFlow<io.livekit.android.room.participant.ConnectionQuality>? {
        val participant = getParticipantById(participantId) ?: return null
        return try {
            participant.connectionQuality.asStateFlow()
        } catch (e: Exception) {
            Timber.w(e) { "[GroupCallManager] Failed to get connection quality for $participantId" }
            null
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