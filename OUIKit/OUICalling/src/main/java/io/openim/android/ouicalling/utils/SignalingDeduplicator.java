package io.openim.android.ouicalling.utils;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.openim.android.ouicalling.entity.MultiPartySignaling;

/**
 * 信令去重器 - 业界最佳实践
 * 
 * 功能：
 * 1. 防止重复处理相同的信令消息
 * 2. 确保信令处理的幂等性
 * 3. 自动清理过期的消息记录
 * 
 * 参考：Kafka消息去重、RocketMQ幂等设计
 */
public class SignalingDeduplicator {
    private static final String TAG = "SignalingDeduplicator";
    
    // 消息保留时间：5分钟 (防止短时间内重复消息)
    private static final long MESSAGE_RETENTION_MS = 5 * 60 * 1000;
    
    // 清理间隔：1分钟
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000;
    
    // 已处理消息的记录 <MessageId, ProcessTime>
    private final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();
    
    // 上次清理时间
    private long lastCleanupTime = System.currentTimeMillis();

    /**
     * 检查消息是否已经处理过
     * @param messageId 消息唯一ID
     * @return true表示重复消息，false表示新消息
     */
    public boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            Log.w(TAG, "消息ID为空，视为新消息");
            return false;
        }

        // 执行定期清理
        performPeriodicCleanup();

        Long processTime = processedMessages.get(messageId);
        if (processTime != null) {
            long age = System.currentTimeMillis() - processTime;
            if (age < MESSAGE_RETENTION_MS) {
                Log.d(TAG, "发现重复消息: " + messageId + ", 距离上次处理: " + age + "ms");
                return true;
            } else {
                // 消息已过期，移除并视为新消息
                processedMessages.remove(messageId);
                Log.d(TAG, "过期消息清理: " + messageId);
                return false;
            }
        }

        return false;
    }

    /**
     * 标记消息已处理
     * @param messageId 消息唯一ID
     */
    public void markProcessed(String messageId) {
        if (messageId != null && !messageId.trim().isEmpty()) {
            processedMessages.put(messageId, System.currentTimeMillis());
            Log.v(TAG, "标记消息已处理: " + messageId);
        }
    }

    /**
     * 处理信令并自动去重
     * @param signaling 信令对象
     * @param processor 实际处理逻辑
     * @return 是否成功处理（false表示重复消息被忽略）
     */
    public boolean handleSignalingWithDeduplication(
            MultiPartySignaling signaling, 
            SignalingProcessor processor) throws Exception {
        
        if (signaling == null) {
            Log.w(TAG, "信令对象为空");
            return false;
        }

        String messageId = signaling.getMessageId();
        
        // 检查是否重复
        if (isDuplicate(messageId)) {
            Log.i(TAG, "重复信令，忽略处理: " + messageId + 
                    ", 类型: " + signaling.getType());
            return false;
        }

        // 标记为已处理
        markProcessed(messageId);

        try {
            // 执行实际处理逻辑
            processor.processSignaling(signaling);
            
            Log.d(TAG, "信令处理完成: " + messageId + 
                    ", 类型: " + signaling.getType());
            return true;
            
        } catch (Exception e) {
            // 处理失败时移除已处理标记，允许重试
            processedMessages.remove(messageId);
            
            Log.e(TAG, "信令处理失败: " + messageId + 
                    ", 类型: " + signaling.getType() + 
                    ", 错误: " + e.getMessage(), e);
            
            throw e;  // 重新抛出异常给上层处理
        }
    }

    /**
     * 定期清理过期消息
     */
    private void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            cleanupExpiredMessages();
            lastCleanupTime = currentTime;
        }
    }

    /**
     * 清理过期消息记录
     */
    public void cleanupExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        // 使用迭代器安全地移除过期元素
        processedMessages.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > MESSAGE_RETENTION_MS;
            if (expired) {
                Log.v(TAG, "清理过期消息: " + entry.getKey());
            }
            return expired;
        });

        if (removedCount > 0) {
            Log.d(TAG, "清理完成，移除过期消息: " + removedCount + "条, " +
                    "当前缓存消息: " + processedMessages.size() + "条");
        }
    }

    /**
     * 手动清理所有记录（通话结束时调用）
     */
    public void clearAll() {
        int count = processedMessages.size();
        processedMessages.clear();
        Log.i(TAG, "手动清理所有消息记录: " + count + "条");
    }

    /**
     * 获取当前缓存的消息数量
     */
    public int getCachedMessageCount() {
        return processedMessages.size();
    }

    /**
     * 获取消息处理统计信息
     */
    public String getStatistics() {
        return String.format("去重器统计 - 缓存消息: %d, 保留时间: %ds, 清理间隔: %ds",
                processedMessages.size(),
                MESSAGE_RETENTION_MS / 1000,
                CLEANUP_INTERVAL_MS / 1000);
    }

    /**
     * 信令处理器接口
     */
    public interface SignalingProcessor {
        /**
         * 处理信令的具体逻辑
         * @param signaling 要处理的信令
         * @throws Exception 处理过程中的异常
         */
        void processSignaling(MultiPartySignaling signaling) throws Exception;
    }
}