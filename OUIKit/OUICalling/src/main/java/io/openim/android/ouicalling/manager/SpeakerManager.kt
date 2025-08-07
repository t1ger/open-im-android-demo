package io.openim.android.ouicalling.manager

import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import com.github.ajalt.timberkt.Timber

/**
 * 扬声器管理器 - 重构版本
 * 
 * 架构原则：
 * 1. 不直接暴露LiveKit的Flow
 * 2. 通过信令驱动状态更新
 * 3. 提供业务级别的抽象接口
 * 4. 完全封装LiveKit实现细节
 */
class SpeakerManager(
    private val room: Room,
    private val coroutineScope: CoroutineScope
) {
    
    // ===== 状态管理 =====
    // 主要扬声器状态
    private val _primarySpeaker = MutableStateFlow<Participant?>(null)
    val primarySpeaker: StateFlow<Participant?> = _primarySpeaker.asStateFlow()
    
    // 活跃扬声器列表 - 通过信令更新，而非直接订阅LiveKit
    private val _activeSpeakers = MutableStateFlow<List<Participant>>(emptyList())
    val activeSpeakers: StateFlow<List<Participant>> = _activeSpeakers.asStateFlow()
    
    // 所有参与者（本地 + 远程） - 通过信令更新
    private val _allParticipants = MutableStateFlow<List<Participant>>(listOf(room.localParticipant))
    val allParticipants: StateFlow<List<Participant>> = _allParticipants.asStateFlow()
    
    // ===== 初始化 =====
    init {
        initializeSpeakerManagement()
    }
    
    /**
     * 初始化扬声器管理 - 通过信令驱动，而非直接订阅LiveKit事件
     */
    private fun initializeSpeakerManagement() {
        try {
            Timber.d { "[SpeakerManager] 初始化扬声器管理 - 信令驱动模式" }
            // 设置初始状态
            updateAllParticipants()
            _primarySpeaker.value = room.localParticipant
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 初始化扬声器管理异常" }
        }
    }
    
    // ===== 对外接口 =====
    
    /**
     * 获取活跃扬声器Flow
     */
    fun getActiveSpeakersFlow(): StateFlow<List<Participant>> = activeSpeakers
    
    // ===== 信令驱动的状态更新接口 =====
    
    /**
     * 通过信令更新活跃扬声器 - 由CallingVM调用
     * @param speakers 活跃扬声器列表
     */
    fun updateActiveSpeakers(speakers: List<Participant>) {
        try {
            _activeSpeakers.value = speakers
            Timber.d { "[SpeakerManager] 通过信令更新活跃扬声器: ${speakers.size}" }
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 更新活跃扬声器异常" }
        }
    }
    
    /**
     * 通过信令更新所有参与者 - 由CallingVM调用
     */
    fun updateAllParticipants() {
        try {
            val participants = listOf<Participant>(room.localParticipant) + room.remoteParticipants.values
            _allParticipants.value = participants
            Timber.d { "[SpeakerManager] 通过信令更新所有参与者: ${participants.size}" }
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 更新所有参与者异常" }
        }
    }
    
    /**
     * 通过信令更新参与者变化 - 由CallingVM调用
     * @param participantId 参与者ID
     * @param joined 是否加入（true=加入，false=离开）
     */
    fun updateParticipantChange(participantId: String, joined: Boolean) {
        try {
            updateAllParticipants()
            
            if (!joined) {
                // 如果离开的是当前主要扬声器，需要重新选择
                val currentPrimary = _primarySpeaker.value
                if (currentPrimary?.identity?.value == participantId) {
                    selectNewPrimarySpeaker()
                }
            }
            
            Timber.d { "[SpeakerManager] 通过信令更新参与者变化: $participantId, joined=$joined" }
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 更新参与者变化异常" }
        }
    }
    
    // ===== 业务逻辑 =====
    
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
     * 选择新的主要扬声器
     */
    private fun selectNewPrimarySpeaker() {
        try {
            val participants = _allParticipants.value
            val newSpeaker = participants
                .filterIsInstance<RemoteParticipant>()
                .firstOrNull() ?: room.localParticipant
                
            _primarySpeaker.value = newSpeaker
            Timber.d { "[SpeakerManager] 选择新的主要扬声器: ${newSpeaker?.identity?.value}" }
        } catch (e: Exception) {
            Timber.e(e) { "[SpeakerManager] 选择新主要扬声器异常" }
        }
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
     * 获取当前活跃扬声器列表
     */
    fun getCurrentActiveSpeakers(): List<Participant> {
        return _activeSpeakers.value
    }
    
    /**
     * 获取当前所有参与者列表
     */
    fun getCurrentAllParticipants(): List<Participant> {
        return _allParticipants.value
    }
    
    /**
     * 重置扬声器状态
     */
    fun reset() {
        try {
            Timber.d { "[SpeakerManager] 重置扬声器状态" }
            _primarySpeaker.value = null
            _activeSpeakers.value = emptyList()
            _allParticipants.value = listOf(room.localParticipant)
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