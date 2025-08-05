package io.openim.android.ouicalling.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import io.livekit.android.renderer.TextureViewRenderer
import io.openim.android.ouicalling.manager.StreamPriority
import io.openim.android.ouicalling.vm.CallViewModel
import kotlinx.coroutines.*
import com.github.ajalt.timberkt.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 智能视频流适配器
 * 与MultiStreamManager协同工作，自动管理视频流的优先级和渲染
 */
class SmartVideoStreamAdapter(
    private val callViewModel: CallViewModel,
    private val onItemClick: ((String) -> Unit)? = null
) : ListAdapter<VideoStreamItem, SmartVideoStreamAdapter.ViewHolder>(DiffCallback()) {
    
    private val activeScopes = ConcurrentHashMap<String, CoroutineScope>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 这里需要根据实际的布局文件进行调整
        val view = LayoutInflater.from(parent.context)
            .inflate(io.openim.android.ouicalling.R.layout.item_member_renderer, parent, false)
        return ViewHolder(view, callViewModel)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemClick)
        
        // 管理协程作用域
        val scope = activeScopes.getOrPut(item.participantId) {
            CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        
        // 注册到多流管理器
        callViewModel.registerVideoStream(
            item.participantId,
            holder.renderer,
            item.priority
        )
    }
    
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        
        val item = holder.currentItem
        if (item != null) {
            // 注销流
            callViewModel.unregisterVideoStream(item.participantId)
            
            // 取消协程
            activeScopes[item.participantId]?.cancel()
            activeScopes.remove(item.participantId)
            
            // 清理渲染器
            holder.unbind()
        }
    }
    
    /**
     * 更新指定参与者的流优先级
     */
    fun updateStreamPriority(participantId: String, priority: StreamPriority) {
        callViewModel.updateStreamPriority(participantId, priority)
    }
    
    /**
     * 获取流统计信息
     */
    fun getStreamStatistics() = callViewModel.getStreamStatistics()
    
    /**
     * 清理所有资源
     */
    fun release() {
        try {
            // 取消所有协程
            activeScopes.values.forEach { it.cancel() }
            activeScopes.clear()
            
            Timber.d { "[SmartVideoStreamAdapter] 清理适配器资源" }
        } catch (e: Exception) {
            Timber.e(e) { "[SmartVideoStreamAdapter] 清理资源异常" }
        }
    }
    
    class ViewHolder(
        itemView: android.view.View,
        private val callViewModel: CallViewModel
    ) : RecyclerView.ViewHolder(itemView) {
        
        // 需要根据实际布局文件获取TextureViewRenderer
        val renderer: TextureViewRenderer by lazy {
            itemView as? TextureViewRenderer ?: TextureViewRenderer(itemView.context).apply {
                // 如果itemView不是TextureViewRenderer，创建一个新的并添加到布局中
                if (itemView is android.view.ViewGroup) {
                    itemView.addView(this)
                }
            }
        }
        
        var currentItem: VideoStreamItem? = null
        private var bindingJob: Job? = null
        
        fun bind(item: VideoStreamItem, onItemClick: ((String) -> Unit)?) {
            currentItem = item
            
            // 设置点击事件
            itemView.setOnClickListener { 
                onItemClick?.invoke(item.participantId)
            }
            
            // 绑定视频流 - 使用MultiStreamManager会自动处理优先级
            bindingJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    // MultiStreamManager会自动处理视频流的绑定和优先级
                    // 无需手动绑定，MultiStreamManager会根据优先级自动管理
                    
                    Timber.v { "[SmartVideoStreamAdapter] 绑定视频流: ${item.participantId}, 优先级: ${item.priority}" }
                } catch (e: Exception) {
                    Timber.e(e) { "[SmartVideoStreamAdapter] 绑定视频流异常: ${item.participantId}" }
                }
            }
        }
        
        fun unbind() {
            bindingJob?.cancel()
            currentItem = null
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<VideoStreamItem>() {
        override fun areItemsTheSame(oldItem: VideoStreamItem, newItem: VideoStreamItem): Boolean {
            return oldItem.participantId == newItem.participantId
        }
        
        override fun areContentsTheSame(oldItem: VideoStreamItem, newItem: VideoStreamItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * 视频流项目数据类
 */
data class VideoStreamItem(
    val participantId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val priority: StreamPriority = StreamPriority.NORMAL,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val hasVideo: Boolean = true,
    val connectionQuality: io.livekit.android.room.participant.ConnectionQuality = 
        io.livekit.android.room.participant.ConnectionQuality.UNKNOWN
)