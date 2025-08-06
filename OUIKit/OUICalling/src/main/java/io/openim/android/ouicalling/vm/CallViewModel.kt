package io.openim.android.ouicalling.vm

import android.app.Application
import android.content.Intent
import androidx.lifecycle.*
import androidx.lifecycle.viewModelScope
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.util.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

// Manager导入
import io.openim.android.ouicalling.manager.*

// 群组通话相关导入
import io.openim.android.ouicalling.entity.CallMemberState
import io.openim.android.ouicalling.entity.GroupCallMember

// 性能监控导入
import io.openim.android.ouicalling.utils.VideoStreamMonitor

/**
 * 重构后的CallViewModel - 轻量级协调器
 * 
 * 架构说明:
 * - CallViewModel: 协调器，负责Manager之间的协调和UI接口
 * - CallRoomManager: 房间连接管理
 * - MediaDeviceManager: 媒体设备控制
 * - GroupCallManager: 群组通话逻辑
 * - VideoBindingManager: 视频绑定管理
 * - SpeakerManager: 扬声器管理
 * - CoroutineScopeManager: 协程生命周期管理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModel(application: Application) : AndroidViewModel(application) {
    
    // ===== Manager组件 =====
    private val roomManager = CallRoomManager(application)
    private val deviceManager = MediaDeviceManager(roomManager.room, viewModelScope)
    private val groupManager = GroupCallManager(roomManager.room, viewModelScope)  
    private val videoManager = VideoBindingManager(roomManager.room, viewModelScope)
    private val speakerManager = SpeakerManager(roomManager.room, viewModelScope)
    private val scopeManager = CoroutineScopeManager()
    
    // Week 2 Day 6: 多路视频流管理器
    private val multiStreamManager = MultiStreamManager(roomManager.room, viewModelScope)
    
    // Week 2 Day 6: 视频流性能监控器
    private val streamMonitor = VideoStreamMonitor(this)
    
    // ===== 音频处理器 =====
    val audioHandler = roomManager.room.audioHandler as AudioSwitchHandler
    
    // ===== 参与者相关 =====
    val allParticipants = speakerManager.allParticipants
    // 暂时移除直接的Flow暴露，改为通过信令更新
    // val remoteParticipants = roomManager.room.remoteParticipants.flow
    var singleRemotePar: RemoteParticipant? = null
    
    // ===== 状态管理 =====
    // 房间连接状态和错误
    val error = roomManager.error
    val connectionState = roomManager.connectionState
    
    // 主要扬声器
    val primarySpeaker = speakerManager.primarySpeaker
    val activeSpeakers = speakerManager.activeSpeakers
    
    // 媒体设备状态
    val micEnabled = deviceManager.micEnabled
    val cameraEnabled = deviceManager.cameraEnabled
    val flipButtonVideoEnabled = deviceManager.flipVideoButtonEnabled
    val screenshareEnabled = deviceManager.screencastEnabled
    
    // 群组通话状态
    val isGroupCall = groupManager.isGroupCall
    val groupMembers = groupManager.groupMembers
    val groupParticipantChanges = groupManager.groupParticipantChanges
    
    // 多路视频流状态 (Week 2 Day 6)
    val activeSpeakersMulti = multiStreamManager.activeSpeakers
    val primarySpeakerMulti = multiStreamManager.primarySpeaker
    val adaptiveQualityEnabled = multiStreamManager.adaptiveQualityEnabled
    
    // 性能监控状态 (Week 2 Day 6)
    val monitoringState = streamMonitor.monitoringState
    val performanceReport = streamMonitor.performanceReport
    
    // 房间元数据
    // 暂时移除直接的Flow暴露，改为通过信令更新
    // val roomMetadata = roomManager.room.metadata.flow
    
    // 数据接收
    private val mutableDataReceived = MutableSharedFlow<String>()
    val dataReceived = mutableDataReceived
    
    // 权限管理
    private val mutablePermissionAllowed = MutableStateFlow(true)
    val permissionAllowed = mutablePermissionAllowed.hide()

    // ===== 生命周期管理 =====
    init {
        initializeEventListeners()
    }
    
    /**
     * 初始化事件监听器
     */
    private fun initializeEventListeners() {
        viewModelScope.launch {
            // 监听错误
            launch {
                error.collect { Timber.e(it) }
            }

            // 处理扬声器变化
            launch {
                combine(
                    allParticipants, 
                    speakerManager.activeSpeakers
                ) { participants, speakers -> participants to speakers }
                .collect { (participantsList, speakers) ->
                    speakerManager.handlePrimarySpeaker(participantsList, speakers)
                }
            }

            // 处理房间事件 - 使用内部封装方法处理
            launch {
                try {
                    // ✅ 修复: 不直接访问 roomManager.room.events
                    // 改为提供一个事件Flow给外部订阅者
                    collectAndProcessRoomEvents()
                } catch (e: Exception) {
                    Timber.e(e) { "[CallViewModel] Room event collection error" }
                }
            }
        }
    }

    // ===== 房间连接管理接口 =====
    
    lateinit var url: String
    lateinit var token: String
    
    /**
     * 连接到房间
     */
    suspend fun connectToRoom(url: String, token: String) {
        this.url = url
        this.token = token
        roomManager.connectToRoom(url, token)
        deviceManager.syncDeviceStates()
    }
    
    /**
     * 断开连接
     */
    fun disconnect() = roomManager.disconnect()
    
    /**
     * 重连
     */
    fun reconnect() {
        viewModelScope.launch {
            roomManager.reconnect()
        }
    }
    
    // ===== 媒体设备控制接口 =====
    
    /**
     * 设置麦克风开关
     */
    fun setMicEnabled(enabled: Boolean) = deviceManager.setMicEnabled(enabled)
    
    /**
     * 设置摄像头开关
     */
    fun setCameraEnabled(enabled: Boolean) = deviceManager.setCameraEnabled(enabled)
    
    /**
     * 切换摄像头
     */
    fun flipCamera() = deviceManager.flipCamera()
    
    /**
     * 开始屏幕共享
     */
    fun startScreenCapture(mediaProjectionPermissionResultData: Intent) =
        deviceManager.startScreenCapture(mediaProjectionPermissionResultData)
    
    /**
     * 停止屏幕共享  
     */
    fun stopScreenCapture() = deviceManager.stopScreenCapture()
    
    // ===== 视频绑定管理接口 =====
    
    /**
     * 绑定远程视频渲染器
     */
    suspend fun bindRemoteViewRenderer(
        viewRenderer: TextureViewRenderer, 
        participant: Participant, 
        scope: CoroutineScope
    ) = videoManager.bindRemoteViewRenderer(viewRenderer, participant, scope)
    
    /**
     * 绑定视频轨道
     */
    fun bindVideoTrack(viewRenderer: TextureViewRenderer, videoTrack: VideoTrack) =
        videoManager.bindVideoTrack(viewRenderer, videoTrack)
    
    /**
     * 获取视频轨道
     */
    fun getVideoTrack(participant: Participant): VideoTrack? =
        videoManager.getVideoTrack(participant)
    
    /**
     * 为GroupMemberAdapter绑定群组成员视频渲染器
     */
    suspend fun bindGroupMemberVideoRenderer(
        viewRenderer: TextureViewRenderer,
        participantId: String,
        scope: CoroutineScope
    ) = videoManager.bindGroupMemberVideoRenderer(viewRenderer, participantId, groupManager)
    
    /**
     * Java友好的非异步视频绑定方法
     */
    fun bindGroupMemberVideoRendererSync(
        viewRenderer: TextureViewRenderer,
        participantId: String
    ) = videoManager.bindGroupMemberVideoRendererSync(viewRenderer, participantId, groupManager)
    
    /**
     * 解绑群组成员视频渲染器
     */
    fun unbindGroupMemberVideoRenderer(viewRenderer: TextureViewRenderer) =
        videoManager.unbindVideoRenderer(viewRenderer)
    
    // ===== 多路视频流管理接口 (Week 2 Day 6) =====
    
    /**
     * 注册视频流到多流管理器
     * @param participantId 参与者ID
     * @param renderer 视频渲染器
     * @param priority 流优先级
     */
    fun registerVideoStream(
        participantId: String,
        renderer: TextureViewRenderer,
        priority: StreamPriority = StreamPriority.NORMAL
    ) = multiStreamManager.registerVideoStream(participantId, renderer, priority)
    
    /**
     * 注销视频流
     */
    fun unregisterVideoStream(participantId: String) = 
        multiStreamManager.unregisterVideoStream(participantId)
    
    /**
     * 更新流优先级
     */
    fun updateStreamPriority(participantId: String, priority: StreamPriority) =
        multiStreamManager.updateStreamPriority(participantId, priority)
    
    /**
     * 切换自适应质量控制
     */
    fun setAdaptiveQualityEnabled(enabled: Boolean) =
        multiStreamManager.setAdaptiveQualityEnabled(enabled)
    
    /**
     * 获取流统计信息
     */
    fun getStreamStatistics(): StreamStatistics =
        multiStreamManager.getStreamStatistics()
    
    /**
     * 开始性能监控
     */
    fun startPerformanceMonitoring() = 
        streamMonitor.startMonitoring(viewModelScope)
    
    /**
     * 停止性能监控
     */
    fun stopPerformanceMonitoring() = streamMonitor.stopMonitoring()
    
    /**
     * 获取性能摘要
     */
    fun getPerformanceSummary() = streamMonitor.getPerformanceSummary()
    
    /**
     * 获取LiveKit Room实例（兼容现有CallingVM调用）
     */
    fun getRoom() = roomManager.room
    
    // ===== 群组通话管理接口 =====
    
    /**
     * 连接到群组房间
     */
    suspend fun connectToGroupRoom(
        url: String,
        token: String,
        memberIds: List<String>,
        callback: (Result<Boolean>) -> Unit
    ) {
        this.url = url
        this.token = token
        groupManager.connectToGroupRoom(url, token, memberIds, callback)
        deviceManager.syncDeviceStates()
    }
    
    /**
     * 获取所有群组参与者（不包含本地用户）
     */
    fun getAllGroupParticipants(): StateFlow<List<Participant>> =
        groupManager.getAllGroupParticipants()
    
    /**
     * 根据ID获取参与者
     */
    fun getParticipantById(participantId: String): Participant? =
        groupManager.getParticipantById(participantId)
    
    /**
     * 检查参与者的摄像头是否开启
     */
    fun isParticipantCameraEnabled(participantId: String): Boolean =
        groupManager.isParticipantCameraEnabled(participantId)
    
    /**
     * 检查参与者的麦克风是否开启
     */
    fun isParticipantMicrophoneEnabled(participantId: String): Boolean =
        groupManager.isParticipantMicrophoneEnabled(participantId)
    
    /**
     * 获取参与者的连接质量
     */
    fun getParticipantConnectionQuality(participantId: String): StateFlow<ConnectionQuality>? =
        groupManager.getParticipantConnectionQuality(participantId)
    
    // ===== 扬声器管理接口 =====
    
    /**
     * 获取活跃扬声器Flow
     */
    fun getActiveSpeakersFlow(): StateFlow<List<Participant>> =
        speakerManager.getActiveSpeakersFlow()
    
    // ===== 协程管理接口 =====
    
    /**
     * 构建协程作用域
     */
    fun buildScope(): CoroutineScope = scopeManager.buildScope()
    
    /**
     * 取消协程作用域
     */
    fun scopeCancel(scope: CoroutineScope) = scopeManager.scopeCancel(scope)
    
    /**
     * 订阅Flow
     */
    @JvmOverloads
    fun <T> subscribe(
        flow: Flow<T>, 
        function: (T) -> Any,
        scope: CoroutineScope = viewModelScope,
    ) = scopeManager.subscribe(flow, function, scope)
    
    // ===== 其他功能接口 =====
    
    /**
     * 获取连接质量Flow
     */
    /**
     * 获取连接质量 - 不直接暴露Flow，改为返回当前值
     */
    fun getConnectionQuality(p: Participant): ConnectionQuality {
        return try {
            p.connectionQuality
        } catch (e: Exception) {
            Timber.w(e) { "[CallViewModel] Failed to get connection quality" }
            ConnectionQuality.UNKNOWN
        }
    }
    
    /**
     * 发送数据
     */
    fun sendData(message: String) {
        viewModelScope.launch {
            roomManager.room.localParticipant.publishData(message.toByteArray(Charsets.UTF_8))
        }
    }
    
    /**
     * 切换订阅权限
     */
    fun toggleSubscriptionPermissions() {
        mutablePermissionAllowed.value = !mutablePermissionAllowed.value
        roomManager.room.localParticipant.setTrackSubscriptionPermissions(mutablePermissionAllowed.value)
    }
    
    /**
     * 模拟迁移
     */
    fun simulateMigration() {
        roomManager.room.sendSimulateScenario(
            livekit.LivekitRtc.SimulateScenario.newBuilder().setMigration(true).build()
        )
    }
    
    /**
     * 清除错误
     */
    fun dismissError() = roomManager.clearError()
    
    // ===== 生命周期管理 =====
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            Timber.d { "[CallViewModel] 开始释放资源" }
            
            deviceManager.release()
            groupManager.release()  
            videoManager.release()
            speakerManager.release()
            multiStreamManager.release()  // Week 2 Day 6
            streamMonitor.release()       // Week 2 Day 6
            scopeManager.release()
            roomManager.disconnect()
            
            Timber.d { "[CallViewModel] 资源释放完成" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[CallViewModel] 释放资源异常" }
        }
    }
    
    /**
     * ViewModel销毁时自动释放资源
     */
    override fun onCleared() {
        super.onCleared()
        release()
    }
    
    // ===== 事件处理封装 =====
    
    /**
     * 收集并处理房间事件 - 提供正确的事件处理封装
     * ✅ 修复: 替代直接访问 roomManager.room.events 的封装方法
     */
    private suspend fun collectAndProcessRoomEvents() {
        try {
            Timber.d { "[CallViewModel] 开始监听房间事件" }
            // 这里应该提供房间事件的封装访问
            // 但目前我们采用信令驱动模式，所以这个方法暂时不需要实现具体的事件监听
            // 所有事件处理都通过CallingVM的信令处理来完成
            Timber.d { "[CallViewModel] 房间事件监听已启动(信令驱动模式)" }
        } catch (e: Exception) {
            Timber.e(e) { "[CallViewModel] 房间事件监听异常" }
        }
    }
    
    /**
     * 获取房间事件Flow - 兼容原有的订阅模式
     * 提供类似 getRoom().getEvents().getEvents() 的接口
     */
    fun getRoomEventsFlow(): Flow<RoomEvent> {
        return try {
            roomManager.room.events.events
        } catch (e: Exception) {
            Timber.w(e) { "[CallViewModel] Failed to get room events flow" }
            emptyFlow()
        }
    }
}

// ===== 扩展函数 =====
public fun <T> LiveData<T>.hide(): LiveData<T> = this
public fun <T> MutableStateFlow<T>.hide(): StateFlow<T> = this
public fun <T> Flow<T>.hide(): Flow<T> = this

fun Participant.getIdentity(): String {
    if (null != this.identity)
        return this.identity!!.value
    return ""
}