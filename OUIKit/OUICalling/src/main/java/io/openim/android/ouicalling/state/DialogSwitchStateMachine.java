package io.openim.android.ouicalling.state;

import android.util.Log;

/**
 * CallDialog切换状态机 - 延迟切换优化
 * 
 * 设计原则：
 * 1. 确保数据完整性：等待必要数据加载完成后再切换UI
 * 2. 状态机管理：明确的状态转换，避免重复切换
 * 3. 异常安全：处理各种异常情况和超时
 * 4. 性能优化：避免不必要的UI操作
 */
public class DialogSwitchStateMachine {
    private static final String TAG = "DialogSwitchStateMachine";
    
    /**
     * 切换状态枚举
     */
    public enum SwitchState {
        INIT("初始状态"),
        WAITING_SIGNALING("等待信令数据"),
        WAITING_MEMBERS("等待成员数据"), 
        WAITING_UI_READY("等待UI准备"),
        SWITCHING("正在切换"),
        SWITCHED("切换完成"),
        FAILED("切换失败");
        
        private final String description;
        
        SwitchState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 数据准备状态
     */
    public static class DataReadyState {
        public boolean signalingReady = false;      // 信令数据就绪
        public boolean membersReady = false;        // 成员数据就绪
        public boolean uiReady = false;             // UI组件就绪
        
        public boolean isAllReady() {
            return signalingReady && membersReady && uiReady;
        }
        
        public String getReadyStatus() {
            return String.format("Signaling:%s, Members:%s, UI:%s", 
                signalingReady, membersReady, uiReady);
        }
    }
    
    private volatile SwitchState currentState = SwitchState.INIT;
    private final DataReadyState dataState = new DataReadyState();
    private final Object stateLock = new Object();
    private SwitchStateListener listener;
    private long switchStartTime = 0;
    private static final long SWITCH_TIMEOUT_MS = 5000; // 5秒超时
    
    /**
     * 状态变化监听器
     */
    public interface SwitchStateListener {
        /**
         * 可以开始切换时调用
         */
        void onReadyToSwitch();
        
        /**
         * 切换完成时调用
         */
        void onSwitchCompleted();
        
        /**
         * 切换失败时调用
         */
        void onSwitchFailed(String reason);
        
        /**
         * 状态变化时调用
         */
        void onStateChanged(SwitchState oldState, SwitchState newState);
    }
    
    public void setListener(SwitchStateListener listener) {
        this.listener = listener;
    }
    
    /**
     * 开始切换流程
     */
    public void startSwitch() {
        synchronized (stateLock) {
            if (currentState != SwitchState.INIT) {
                Log.w(TAG, "切换已在进行中，当前状态: " + currentState.getDescription());
                return;
            }
            
            switchStartTime = System.currentTimeMillis();
            changeState(SwitchState.WAITING_SIGNALING);
            Log.d(TAG, "开始切换流程");
        }
    }
    
    /**
     * 通知信令数据就绪
     */
    public void notifySignalingReady() {
        synchronized (stateLock) {
            if (currentState == SwitchState.FAILED) {
                Log.w(TAG, "切换已失败，忽略信令数据就绪通知");
                return;
            }
            
            dataState.signalingReady = true;
            Log.d(TAG, "信令数据就绪");
            
            if (currentState == SwitchState.WAITING_SIGNALING) {
                changeState(SwitchState.WAITING_MEMBERS);
            }
            
            checkAllDataReady();
        }
    }
    
    /**
     * 通知成员数据就绪
     */
    public void notifyMembersReady() {
        synchronized (stateLock) {
            if (currentState == SwitchState.FAILED) {
                Log.w(TAG, "切换已失败，忽略成员数据就绪通知");
                return;
            }
            
            dataState.membersReady = true;
            Log.d(TAG, "成员数据就绪");
            
            if (currentState == SwitchState.WAITING_MEMBERS) {
                changeState(SwitchState.WAITING_UI_READY);
            }
            
            checkAllDataReady();
        }
    }
    
    /**
     * 通知UI准备就绪
     */
    public void notifyUIReady() {
        synchronized (stateLock) {
            if (currentState == SwitchState.FAILED) {
                Log.w(TAG, "切换已失败，忽略UI就绪通知");
                return;
            }
            
            dataState.uiReady = true;
            Log.d(TAG, "UI准备就绪");
            
            checkAllDataReady();
        }
    }
    
    /**
     * 检查所有数据是否就绪
     */
    private void checkAllDataReady() {
        if (dataState.isAllReady() && currentState != SwitchState.SWITCHING && currentState != SwitchState.SWITCHED) {
            Log.d(TAG, "所有数据就绪，开始切换: " + dataState.getReadyStatus());
            changeState(SwitchState.SWITCHING);
            
            if (listener != null) {
                listener.onReadyToSwitch();
            }
        }
    }
    
    /**
     * 通知切换完成
     */
    public void notifySwitchCompleted() {
        synchronized (stateLock) {
            if (currentState != SwitchState.SWITCHING) {
                Log.w(TAG, "当前状态不是SWITCHING，无法完成切换: " + currentState.getDescription());
                return;
            }
            
            changeState(SwitchState.SWITCHED);
            long duration = System.currentTimeMillis() - switchStartTime;
            Log.d(TAG, "切换完成，耗时: " + duration + "ms");
            
            if (listener != null) {
                listener.onSwitchCompleted();
            }
        }
    }
    
    /**
     * 通知切换失败
     */
    public void notifySwitchFailed(String reason) {
        synchronized (stateLock) {
            if (currentState == SwitchState.SWITCHED) {
                Log.w(TAG, "切换已完成，忽略失败通知");
                return;
            }
            
            changeState(SwitchState.FAILED);
            long duration = System.currentTimeMillis() - switchStartTime;
            Log.e(TAG, "切换失败: " + reason + "，耗时: " + duration + "ms");
            
            if (listener != null) {
                listener.onSwitchFailed(reason);
            }
        }
    }
    
    /**
     * 检查是否超时
     */
    public boolean checkTimeout() {
        synchronized (stateLock) {
            if (currentState == SwitchState.SWITCHED || currentState == SwitchState.FAILED) {
                return false;
            }
            
            long elapsed = System.currentTimeMillis() - switchStartTime;
            if (elapsed > SWITCH_TIMEOUT_MS) {
                notifySwitchFailed("切换超时: " + elapsed + "ms");
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * 改变状态
     */
    private void changeState(SwitchState newState) {
        SwitchState oldState = currentState;
        currentState = newState;
        
        Log.d(TAG, "状态变化: " + oldState.getDescription() + " → " + newState.getDescription());
        
        if (listener != null) {
            listener.onStateChanged(oldState, newState);
        }
    }
    
    /**
     * 获取当前状态
     */
    public SwitchState getCurrentState() {
        return currentState;
    }
    
    /**
     * 获取数据状态
     */
    public DataReadyState getDataState() {
        return dataState;
    }
    
    /**
     * 重置状态机
     */
    public void reset() {
        synchronized (stateLock) {
            currentState = SwitchState.INIT;
            dataState.signalingReady = false;
            dataState.membersReady = false;
            dataState.uiReady = false;
            switchStartTime = 0;
            Log.d(TAG, "状态机已重置");
        }
    }
    
    /**
     * 获取调试信息
     */
    public String getDebugInfo() {
        synchronized (stateLock) {
            long elapsed = switchStartTime > 0 ? System.currentTimeMillis() - switchStartTime : 0;
            return String.format("State: %s, Data: [%s], Elapsed: %dms", 
                currentState.getDescription(), 
                dataState.getReadyStatus(), 
                elapsed);
        }
    }
}