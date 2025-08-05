package io.openim.android.ouicalling.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ajalt.timberkt.Timber
import io.openim.android.ouicalling.R
import io.openim.android.ouicalling.adapter.SmartVideoStreamAdapter
import io.openim.android.ouicalling.adapter.VideoStreamItem
import io.openim.android.ouicalling.manager.StreamPriority
import io.openim.android.ouicalling.utils.VideoStreamMonitor
import io.openim.android.ouicalling.vm.CallViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 多路视频流管理演示Activity
 * Week 2 Day 6: 展示MultiStreamManager的功能
 */
class MultiStreamDemoActivity : AppCompatActivity() {
    
    private val callViewModel: CallViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SmartVideoStreamAdapter
    
    // 演示数据
    private val demoStreamItems = listOf(
        VideoStreamItem(
            participantId = "user_001",
            displayName = "Alice",
            priority = StreamPriority.HIGH,
            isSpeaking = true,
            hasVideo = true
        ),
        VideoStreamItem(
            participantId = "user_002", 
            displayName = "Bob",
            priority = StreamPriority.NORMAL,
            isSpeaking = false,
            hasVideo = true
        ),
        VideoStreamItem(
            participantId = "user_003",
            displayName = "Charlie",
            priority = StreamPriority.NORMAL,
            isSpeaking = false,
            hasVideo = false,
            isMuted = true
        ),
        VideoStreamItem(
            participantId = "user_004",
            displayName = "David",
            priority = StreamPriority.LOW,
            isSpeaking = false,
            hasVideo = true
        ),
        VideoStreamItem(
            participantId = "user_005",
            displayName = "Eve",
            priority = StreamPriority.NORMAL,
            isSpeaking = false,
            hasVideo = true
        )
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)
        
        initViews()
        setupAdapter()
        observeMultiStreamStates()
        startDemo()
    }
    
    private fun initViews() {
        // 创建RecyclerView（如果layout中没有的话）
        recyclerView = findViewById<RecyclerView?>(R.id.recyclerView) ?: RecyclerView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 如果没有找到RecyclerView，设置为内容视图
            setContentView(this)
        }
    }
    
    private fun setupAdapter() {
        adapter = SmartVideoStreamAdapter(callViewModel) { participantId ->
            // 点击事件：提升优先级
            callViewModel.updateStreamPriority(participantId, StreamPriority.HIGH)
            Toast.makeText(this, "提升 $participantId 的优先级为HIGH", Toast.LENGTH_SHORT).show()
        }
        
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter
        
        // 提交演示数据
        adapter.submitList(demoStreamItems)
    }
    
    /**
     * 观察多流管理状态
     */
    private fun observeMultiStreamStates() {
        lifecycleScope.launch {
            // 观察活跃说话者变化
            callViewModel.activeSpeakersMulti.collect { speakers ->
                Timber.d { "[MultiStreamDemo] 活跃说话者数量: ${speakers.size}" }
                
                // 根据说话者更新优先级
                speakers.forEach { speaker ->
                    // 尝试不同的identity访问方式
                    val participantId = try {
                        speaker.identity?.value ?: ""
                    } catch (e: Exception) {
                        speaker.identity?.toString() ?: ""
                    }
                    if (participantId.isNotEmpty()) {
                        callViewModel.updateStreamPriority(participantId, StreamPriority.HIGH)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            // 观察主要说话者变化
            callViewModel.primarySpeakerMulti.collect { primarySpeaker ->
                if (primarySpeaker != null) {
                    Timber.d { "[MultiStreamDemo] 主要说话者: $primarySpeaker" }
                    Toast.makeText(this@MultiStreamDemoActivity, 
                        "主要说话者: $primarySpeaker", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        lifecycleScope.launch {
            // 观察性能报告
            callViewModel.performanceReport.collect { report ->
                Timber.v { 
                    "[MultiStreamDemo] 性能报告 - " +
                    "活跃流: ${report.streamStatistics.activeStreams}/${report.streamStatistics.totalStreams}, " +
                    "内存: ${report.memoryUsage}MB, " +
                    "CPU: ${"%.1f".format(report.cpuUsage)}%"
                }
            }
        }
    }
    
    /**
     * 开始演示
     */
    private fun startDemo() {
        // 开始性能监控
        callViewModel.startPerformanceMonitoring()
        
        // 模拟流优先级变化
        lifecycleScope.launch {
            simulateStreamPriorityChanges()
        }
        
        Toast.makeText(this, "多路视频流管理演示已开始", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 模拟流优先级变化
     */
    private suspend fun simulateStreamPriorityChanges() {
        kotlinx.coroutines.delay(3000) // 等待3秒
        
        // 切换说话者
        callViewModel.updateStreamPriority("user_001", StreamPriority.NORMAL)
        callViewModel.updateStreamPriority("user_002", StreamPriority.HIGH)
        
        kotlinx.coroutines.delay(5000) // 等待5秒
        
        // 再次切换
        callViewModel.updateStreamPriority("user_002", StreamPriority.NORMAL) 
        callViewModel.updateStreamPriority("user_003", StreamPriority.HIGH)
        
        kotlinx.coroutines.delay(3000)
        
        // 切换自适应质量
        val currentEnabled = callViewModel.adaptiveQualityEnabled.value
        callViewModel.setAdaptiveQualityEnabled(!currentEnabled)
        
        Toast.makeText(this, "自适应质量控制: ${if (!currentEnabled) "开启" else "关闭"}", 
            Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 展示流统计信息
     */
    private fun showStreamStatistics() {
        val stats = callViewModel.getStreamStatistics()
        val summary = callViewModel.getPerformanceSummary()
        
        val message = """
            流统计信息:
            总流数: ${stats.totalStreams}
            活跃流数: ${stats.activeStreams}
            高优先级流: ${stats.highPriorityStreams}
            中优先级流: ${stats.mediumPriorityStreams}
            低优先级流: ${stats.lowPriorityStreams}
            
            性能摘要:
            监控时长: ${summary.monitoringDurationMs / 1000}秒
            渲染帧数: ${summary.totalFramesRendered}
            质量切换: ${summary.totalQualitySwitches}次
            优先级切换: ${summary.totalPrioritySwitches}次
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("多路视频流统计")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add("显示统计信息").setOnMenuItemClickListener {
            showStreamStatistics()
            true
        }
        
        menu.add("切换自适应质量").setOnMenuItemClickListener {
            val currentEnabled = callViewModel.adaptiveQualityEnabled.value
            callViewModel.setAdaptiveQualityEnabled(!currentEnabled)
            Toast.makeText(this, "自适应质量: ${if (!currentEnabled) "开启" else "关闭"}", 
                Toast.LENGTH_SHORT).show()
            true
        }
        
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止性能监控
        callViewModel.stopPerformanceMonitoring()
        
        // 清理适配器资源
        adapter.release()
        
        Timber.d { "[MultiStreamDemo] Activity销毁，资源已清理" }
    }
}