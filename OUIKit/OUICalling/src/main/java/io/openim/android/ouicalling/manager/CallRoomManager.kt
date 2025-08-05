package io.openim.android.ouicalling.manager

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

/**
 * 房间连接管理器
 * 专门负责LiveKit房间的连接、断开、重连等操作
 */
class CallRoomManager(application: Application) {
    
    val room = LiveKit.create(
        appContext = application,
        options = RoomOptions(
            adaptiveStream = true, 
            dynacast = true
        ),
    )
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()
    
    // 缓存连接信息用于重连
    private var cachedUrl: String = ""
    private var cachedToken: String = ""
    
    /**
     * 连接到房间
     */
    suspend fun connectToRoom(url: String, token: String) {
        try {
            Timber.d { "[CallRoomManager] 开始连接房间: $url" }
            _connectionState.value = ConnectionState.CONNECTING
            
            // 缓存连接信息
            cachedUrl = url
            cachedToken = token
            
            room.connect(url = url, token = token)
            
            // 设置默认音视频状态
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            localParticipant.setCameraEnabled(true)
            
            _connectionState.value = ConnectionState.CONNECTED
            _error.value = null
            
            Timber.d { "[CallRoomManager] 房间连接成功" }
            
        } catch (e: Throwable) {
            Timber.e(e) { "[CallRoomManager] 房间连接失败" }
            _connectionState.value = ConnectionState.FAILED
            _error.value = e
            throw e
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            Timber.d { "[CallRoomManager] 断开房间连接" }
            _connectionState.value = ConnectionState.DISCONNECTING
            
            room.disconnect()
            
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = null
            
        } catch (e: Throwable) {
            Timber.e(e) { "[CallRoomManager] 断开连接异常" }
            _error.value = e
        }
    }
    
    /**
     * 重连
     */
    suspend fun reconnect() {
        try {
            Timber.d { "[CallRoomManager] 开始重连房间" }
            
            if (cachedUrl.isEmpty() || cachedToken.isEmpty()) {
                throw IllegalStateException("没有缓存的连接信息，无法重连")
            }
            
            // 先断开现有连接
            room.disconnect()
            
            // 重新连接
            connectToRoom(cachedUrl, cachedToken)
            
        } catch (e: Throwable) {
            Timber.e(e) { "[CallRoomManager] 重连失败" }
            throw e
        }
    }
    
    /**
     * 获取本地参与者
     */
    fun getLocalParticipant(): LocalParticipant = room.localParticipant
    
    /**
     * 清理错误状态
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * 房间连接状态枚举
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING, 
        CONNECTED,
        DISCONNECTING,
        FAILED
    }
}