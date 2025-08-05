package io.openim.android.ouicalling.manager

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalScreencastVideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.github.ajalt.timberkt.Timber

/**
 * 媒体设备管理器
 * 专门负责音视频设备的控制：麦克风、摄像头、屏幕共享等
 */
class MediaDeviceManager(
    private val room: Room,
    private val coroutineScope: CoroutineScope
) {
    
    // 音视频设备状态
    private val _micEnabled = MutableLiveData(true)
    val micEnabled: LiveData<Boolean> = _micEnabled
    
    private val _cameraEnabled = MutableLiveData(true)
    val cameraEnabled: LiveData<Boolean> = _cameraEnabled
    
    private val _flipVideoButtonEnabled = MutableLiveData(true)
    val flipVideoButtonEnabled: LiveData<Boolean> = _flipVideoButtonEnabled
    
    private val _screencastEnabled = MutableLiveData(false)
    val screencastEnabled: LiveData<Boolean> = _screencastEnabled
    
    // 屏幕共享轨道
    private var localScreencastTrack: LocalScreencastVideoTrack? = null
    
    /**
     * 设置麦克风开关
     */
    fun setMicEnabled(enabled: Boolean) {
        try {
            Timber.d { "[MediaDeviceManager] 设置麦克风状态: $enabled" }
            
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(enabled)
            _micEnabled.postValue(localParticipant.isMicrophoneEnabled())
            
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 设置麦克风状态失败" }
        }
    }
    
    /**
     * 设置摄像头开关
     */
    fun setCameraEnabled(enabled: Boolean) {
        try {
            Timber.d { "[MediaDeviceManager] 设置摄像头状态: $enabled" }
            
            val localParticipant = room.localParticipant
            localParticipant.setCameraEnabled(enabled)
            _cameraEnabled.postValue(localParticipant.isCameraEnabled())
            
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 设置摄像头状态失败" }
        }
    }
    
    /**
     * 切换摄像头（前置/后置）
     */
    fun flipCamera() {
        try {
            Timber.d { "[MediaDeviceManager] 切换摄像头" }
            
            val localParticipant = room.localParticipant
            val videoTrack = localParticipant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track
            
            if (videoTrack is io.livekit.android.room.track.LocalVideoTrack) {
                // 暂时禁用翻转按钮，防止快速点击
                _flipVideoButtonEnabled.postValue(false)
                
                coroutineScope.launch {
                    try {
                        videoTrack.switchCamera()
                        Timber.d { "[MediaDeviceManager] 摄像头切换成功" }
                    } finally {
                        _flipVideoButtonEnabled.postValue(true)
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 切换摄像头失败" }
            _flipVideoButtonEnabled.postValue(true)
        }
    }
    
    /**
     * 开始屏幕共享
     */
    fun startScreenCapture(mediaProjectionPermissionResultData: Intent) {
        try {
            Timber.d { "[MediaDeviceManager] 开始屏幕共享" }
            
            val localParticipant = room.localParticipant
            coroutineScope.launch {
                localScreencastTrack = localParticipant.createScreencastTrack(
                    mediaProjectionPermissionResultData = mediaProjectionPermissionResultData
                )
                localScreencastTrack?.let { track ->
                    localParticipant.publishVideoTrack(track)
                    _screencastEnabled.postValue(true)
                    Timber.d { "[MediaDeviceManager] 屏幕共享开始成功" }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 开始屏幕共享失败" }
        }
    }
    
    /**
     * 停止屏幕共享
     */
    fun stopScreenCapture() {
        try {
            Timber.d { "[MediaDeviceManager] 停止屏幕共享" }
            
            val localParticipant = room.localParticipant
            localScreencastTrack?.let { track ->
                localParticipant.unpublishTrack(track)
                track.stop()
                localScreencastTrack = null
                _screencastEnabled.postValue(false)
                Timber.d { "[MediaDeviceManager] 屏幕共享停止成功" }
            }
            
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 停止屏幕共享失败" }
        }
    }
    
    /**
     * 获取当前麦克风状态
     */
    fun getCurrentMicState(): Boolean {
        return room.localParticipant.isMicrophoneEnabled()
    }
    
    /**
     * 获取当前摄像头状态
     */
    fun getCurrentCameraState(): Boolean {
        return room.localParticipant.isCameraEnabled()
    }
    
    /**
     * 获取当前屏幕共享状态
     */
    fun getCurrentScreencastState(): Boolean {
        return localScreencastTrack != null
    }
    
    /**
     * 同步设备状态到LiveData
     */
    fun syncDeviceStates() {
        _micEnabled.postValue(getCurrentMicState())
        _cameraEnabled.postValue(getCurrentCameraState())
        _screencastEnabled.postValue(getCurrentScreencastState())
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[MediaDeviceManager] 清理设备管理器资源" }
            stopScreenCapture()
        } catch (e: Exception) {
            Timber.e(e) { "[MediaDeviceManager] 清理资源异常" }
        }
    }
}