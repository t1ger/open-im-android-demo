package io.openim.android.ouicalling.manager

import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.util.flow
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

/**
 * 扬声器管理器
 * 专门负责主要扬声器的选择和音频焦点管理
 */
class SpeakerManager(private val room: Room) {
    
    // 主要扬声器
    private val _primarySpeaker = MutableStateFlow<Participant?>(null)
    val primarySpeaker: StateFlow<Participant?> = _primarySpeaker.asStateFlow()
    
    // 活跃扬声器列表
    val activeSpeakers: Flow<List<Participant>> = try {
        room.activeSpeakers
    } catch (e: Exception) {
        flowOf<List<Participant>>(emptyList())
    }
    
    // 所有参与者（本地 + 远程）
    val allParticipants: Flow<List<Participant>> = try {
        room.remoteParticipants.flow.map { remoteParticipants ->
            listOf<Participant>(room.localParticipant) + remoteParticipants.values
        }
    } catch (e: Exception) {
        flowOf<List<Participant>>(listOf(room.localParticipant))
    }
    
    /**
     * 处理主要扬声器逻辑
     * @param participantsList 所有参与者列表
     * @param speakers 当前活跃扬声器列表
     */
    fun handlePrimarySpeaker(
        participantsList: List<Participant>, 
        speakers: List<Participant>
    ) {
        var speaker = _primarySpeaker.value
        
        try {
            Timber.v { "[SpeakerManager] 处理主要扬声器: participants=${participantsList.size}, speakers=${speakers.size}" }
            
            // 如果当前扬声器是本地参与者，尝试找一个远程扬声器替换
            if (speaker is LocalParticipant) {
                val remoteSpeaker = participantsList
                    .filterIsInstance<RemoteParticipant>()
                    .firstOrNull()
                
                if (remoteSpeaker != null) {
                    speaker = remoteSpeaker
                    Timber.d { "[SpeakerManager] 从本地切换到远程扬声器: ${remoteSpeaker.identity?.value}" }
                }
            }
            
            // 如果之前的主要扬声器离开了房间
            if (!participantsList.contains(speaker)) {
                // 默认选择另一个人或本地参与者
                speaker = participantsList
                    .filterIsInstance<RemoteParticipant>()
                    .firstOrNull() ?: room.localParticipant
                    
                Timber.d { "[SpeakerManager] 扬声器离开，选择新扬声器: ${speaker?.identity?.value}" }
            }
            
            // 如果有活跃扬声器且当前扬声器不在活跃列表中
            if (speakers.isNotEmpty() && !speakers.contains(speaker)) {
                val remoteSpeaker = speakers
                    .filterIsInstance<RemoteParticipant>()
                    .firstOrNull()
                
                if (remoteSpeaker != null) {
                    speaker = remoteSpeaker
                    Timber.d { "[SpeakerManager] 切换到活跃远程扬声器: ${remoteSpeaker.identity?.value}" }
                }
            }
            
            // 更新主要扬声器
            if (_primarySpeaker.value != speaker) {
                _primarySpeaker.value = speaker
                Timber.d { "[SpeakerManager] 主要扬声器已更新: ${speaker?.identity?.value}" }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 处理主要扬声器异常" }
        }
    }
    
    /**
     * 获取活跃扬声器StateFlow
     */
    fun getActiveSpeakersFlow(): StateFlow<List<Participant>> {
        return activeSpeakers.stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
    
    /**
     * 手动设置主要扬声器
     * @param participant 要设置为主扬声器的参与者
     */
    fun setPrimarySpeaker(participant: Participant?) {
        try {
            _primarySpeaker.value = participant
            Timber.d { "[SpeakerManager] 手动设置主要扬声器: ${participant?.identity?.value}" }
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 设置主要扬声器异常" }
        }
    }
    
    /**
     * 获取当前主要扬声器
     */
    fun getCurrentPrimarySpeaker(): Participant? {
        return _primarySpeaker.value
    }
    
    /**
     * 重置扬声器状态
     */
    fun reset() {
        try {
            Timber.d { "[SpeakerManager] 重置扬声器状态" }
            _primarySpeaker.value = null
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 重置扬声器状态异常" }
        }
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[SpeakerManager] 清理扬声器管理器资源" }
            reset()
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 清理资源异常" }
        }
    }
}