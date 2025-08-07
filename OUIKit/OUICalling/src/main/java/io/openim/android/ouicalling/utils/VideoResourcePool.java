package io.openim.android.ouicalling.utils;

import android.util.Log;

import io.livekit.android.renderer.TextureViewRenderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;

import io.openim.android.ouicore.base.BaseApp;

/**
 * 视频资源池管理器 - 业界最佳实践
 * 
 * 功能：
 * 1. TextureViewRenderer对象池复用，减少内存分配
 * 2. 自动内存泄漏防护，通过WeakReference管理owner
 * 3. 资源自动清理和回收
 * 4. 性能监控和统计
 * 
 * 参考：Android CameraX资源管理、Glide内存池设计
 */
public class VideoResourcePool {
    private static final String TAG = "VideoResourcePool";
    
    // 最大渲染器数量（支持9人通话）
    private static final int MAX_TEXTURE_RENDERERS = 12;
    
    // 空闲渲染器池
    private final Queue<TextureViewRenderer> rendererPool = new LinkedList<>();
    
    // 活跃渲染器映射 <ParticipantId, Renderer>
    private final Map<String, TextureViewRenderer> activeRenderers = new HashMap<>();
    
    // Owner引用映射，用于内存泄漏防护 <Owner, RendererList>
    private final WeakHashMap<Object, List<TextureViewRenderer>> ownerMap = new WeakHashMap<>();
    
    // 性能统计
    private int totalCreated = 0;
    private int totalReused = 0;
    private int totalReleased = 0;

    /**
     * 获取渲染器
     * @param participantId 参与者ID
     * @param owner 拥有者对象（用于内存泄漏防护）
     * @return 渲染器实例
     */
    public synchronized TextureViewRenderer acquireRenderer(String participantId, Object owner) {
        if (participantId == null || participantId.trim().isEmpty()) {
            Log.w(TAG, "参与者ID为空，无法分配渲染器");
            return null;
        }

        if (owner == null) {
            Log.w(TAG, "Owner为空，可能存在内存泄漏风险");
        }

        // 检查是否已经分配过
        TextureViewRenderer existingRenderer = activeRenderers.get(participantId);
        if (existingRenderer != null) {
            Log.d(TAG, "重复请求渲染器: " + participantId);
            return existingRenderer;
        }

        TextureViewRenderer renderer;
        
        // 尝试从池中复用
        renderer = rendererPool.poll();
        if (renderer != null) {
            totalReused++;
            Log.d(TAG, "复用渲染器: " + participantId + ", 池中剩余: " + rendererPool.size());
        } else {
            // 创建新的渲染器
            if (totalCreated >= MAX_TEXTURE_RENDERERS) {
                Log.w(TAG, "渲染器数量已达上限: " + MAX_TEXTURE_RENDERERS);
                return null;
            }

            try {
                renderer = new TextureViewRenderer(BaseApp.inst());
                // 这里需要在实际使用时初始化renderer
                // callViewModel.getRoom().initVideoRenderer(renderer);
                
                totalCreated++;
                Log.d(TAG, "创建新渲染器: " + participantId + ", 总创建数: " + totalCreated);
                
            } catch (Exception e) {
                Log.e(TAG, "创建渲染器失败: " + participantId, e);
                return null;
            }
        }

        // 重置渲染器状态
        resetRenderer(renderer);
        
        // 记录活跃状态
        activeRenderers.put(participantId, renderer);
        
        // 内存泄漏防护：记录owner引用
        if (owner != null) {
            ownerMap.computeIfAbsent(owner, k -> new ArrayList<>()).add(renderer);
        }

        Log.v(TAG, "分配渲染器成功: " + participantId + 
                ", 活跃数: " + activeRenderers.size() + 
                ", 池中数: " + rendererPool.size());

        return renderer;
    }

    /**
     * 释放渲染器
     * @param participantId 参与者ID
     */
    public synchronized void releaseRenderer(String participantId) {
        if (participantId == null || participantId.trim().isEmpty()) {
            return;
        }

        TextureViewRenderer renderer = activeRenderers.remove(participantId);
        if (renderer == null) {
            Log.w(TAG, "尝试释放不存在的渲染器: " + participantId);
            return;
        }

        // 清理渲染器资源
        cleanupRenderer(renderer);

        // 回收到池中
        if (rendererPool.size() < MAX_TEXTURE_RENDERERS / 2) {
            rendererPool.offer(renderer);
            totalReleased++;
            Log.d(TAG, "渲染器回收到池: " + participantId + 
                    ", 池中数: " + rendererPool.size());
        } else {
            // 池已满，直接释放
            destroyRenderer(renderer);
            Log.d(TAG, "渲染器直接销毁: " + participantId);
        }

        Log.v(TAG, "释放渲染器: " + participantId + 
                ", 活跃数: " + activeRenderers.size());
    }

    /**
     * 批量释放指定owner的所有渲染器
     * @param owner 拥有者对象
     */
    public synchronized void releaseByOwner(Object owner) {
        if (owner == null) {
            return;
        }

        List<TextureViewRenderer> renderers = ownerMap.remove(owner);
        if (renderers == null || renderers.isEmpty()) {
            return;
        }

        int releasedCount = 0;
        
        // 查找并释放相关的渲染器
        for (Map.Entry<String, TextureViewRenderer> entry : new HashMap<>(activeRenderers).entrySet()) {
            if (renderers.contains(entry.getValue())) {
                releaseRenderer(entry.getKey());
                releasedCount++;
            }
        }

        Log.d(TAG, "按Owner批量释放渲染器: " + owner.getClass().getSimpleName() + 
                ", 数量: " + releasedCount);
    }

    /**
     * 清理所有资源（通话结束时调用）
     */
    public synchronized void cleanup() {
        Log.i(TAG, "开始清理所有渲染器资源");

        // 清理活跃渲染器
        for (Map.Entry<String, TextureViewRenderer> entry : activeRenderers.entrySet()) {
            cleanupRenderer(entry.getValue());
            Log.v(TAG, "清理活跃渲染器: " + entry.getKey());
        }
        activeRenderers.clear();

        // 清理池中渲染器
        while (!rendererPool.isEmpty()) {
            TextureViewRenderer renderer = rendererPool.poll();
            destroyRenderer(renderer);
        }

        // 清理owner映射
        ownerMap.clear();

        Log.i(TAG, "资源清理完成 - 总创建: " + totalCreated + 
                ", 总复用: " + totalReused + 
                ", 总释放: " + totalReleased);
    }

    /**
     * 检查并清理无效的owner引用
     */
    public synchronized void cleanupInvalidOwners() {
        int before = ownerMap.size();
        
        // WeakHashMap会自动清理GC的引用，但我们可以主动检查
        for (Object owner : new ArrayList<>(ownerMap.keySet())) {
            if (owner == null) {
                ownerMap.remove(owner);
            }
        }
        
        int after = ownerMap.size();
        if (before != after) {
            Log.d(TAG, "清理无效Owner引用: " + (before - after) + "个");
        }
    }

    /**
     * 重置渲染器状态
     */
    private void resetRenderer(TextureViewRenderer renderer) {
        try {
            // 清理之前的图像
            renderer.clearImage();
            
            // 重置镜像状态
            renderer.setMirror(false);
            
            // 重置缩放模式
            // TextureViewRenderer不需要设置ScalingType，使用默认的SCALE_ASPECT_FIT
            
        } catch (Exception e) {
            Log.w(TAG, "重置渲染器状态失败", e);
        }
    }

    /**
     * 清理渲染器资源
     */
    private void cleanupRenderer(TextureViewRenderer renderer) {
        try {
            renderer.clearImage();
            // 不释放surface，留待复用
        } catch (Exception e) {
            Log.w(TAG, "清理渲染器资源失败", e);
        }
    }

    /**
     * 销毁渲染器
     */
    private void destroyRenderer(TextureViewRenderer renderer) {
        try {
            renderer.clearImage();
            renderer.release();
        } catch (Exception e) {
            Log.w(TAG, "销毁渲染器失败", e);
        }
    }

    /**
     * 获取资源池统计信息
     */
    public synchronized String getStatistics() {
        return String.format("渲染器池统计 - 活跃: %d, 池中: %d, 创建: %d, 复用: %d, 释放: %d",
                activeRenderers.size(),
                rendererPool.size(),
                totalCreated,
                totalReused,
                totalReleased);
    }

    /**
     * 获取复用率
     */
    public synchronized double getReuseRate() {
        int totalAcquired = totalCreated + totalReused;
        return totalAcquired == 0 ? 0.0 : (double) totalReused / totalAcquired;
    }

    /**
     * 检查资源池健康状态
     */
    public synchronized boolean isHealthy() {
        // 检查是否有内存泄漏的迹象
        boolean noMemoryLeak = activeRenderers.size() <= MAX_TEXTURE_RENDERERS;
        
        // 检查复用率是否合理
        boolean goodReuseRate = getReuseRate() >= 0.5 || totalCreated <= 2;
        
        // 检查池大小是否合理
        boolean reasonablePoolSize = rendererPool.size() <= MAX_TEXTURE_RENDERERS / 2;
        
        return noMemoryLeak && goodReuseRate && reasonablePoolSize;
    }

    /**
     * 获取当前活跃渲染器数量
     */
    public synchronized int getActiveRendererCount() {
        return activeRenderers.size();
    }

    /**
     * 获取池中可用渲染器数量
     */
    public synchronized int getAvailableRendererCount() {
        return rendererPool.size();
    }
}