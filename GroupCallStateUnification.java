/**
 * 群组通话状态统一管理方案
 * 解决多重状态源和状态不一致问题
 */
public class GroupCallStateUnification {
    
    /**
     * 问题1：多重状态源导致不一致
     * 
     * 现状：
     * - CallingVM.groupMembers
     * - GroupCallStateManager.groupMembers  
     * - CallViewModel内部状态
     * 
     * 解决方案：单一数据源原则 (Single Source of Truth)
     */
    
    // 修复方案1：统一状态源
    public static class UnifiedGroupCallState {
        // 单一数据源：所有状态都在StateManager中
        private static GroupCallStateManager stateManager = GroupCallStateManager.getInstance();
        
        // CallingVM不再直接维护成员列表，通过StateManager获取
        public List<GroupCallMember> getGroupMembers() {
            return stateManager.getGroupMembers();
        }
        
        // 所有状态变更都通过StateManager
        public void updateMemberState(String userId, CallMemberState state) {
            stateManager.updateMemberState(userId, state);
            // StateManager通知所有观察者，确保UI同步更新
        }
        
        // UI层统一从StateManager获取状态
        public void bindToUI(GroupCallDialog dialog) {
            stateManager.addObserver(dialog);
            // 初始化时同步状态
            dialog.refreshMemberList(stateManager.getGroupMembers());
        }
    }
    
    /**
     * 问题2：生命周期不匹配
     * 
     * 现状：
     * - Dialog可能先于StateManager销毁
     * - StateManager是单例，可能内存泄漏
     * - CallingVM与Dialog生命周期绑定，但状态独立管理
     */
    
    // 修复方案2：生命周期对齐
    public static class LifecycleAlignedStateManager {
        
        // 不使用单例模式，与通话会话绑定
        public static class GroupCallSession {
            private String sessionId;
            private GroupCallStateManager stateManager;
            private List<StateChangeObserver> observers = new ArrayList<>();
            
            public GroupCallSession(String sessionId) {
                this.sessionId = sessionId;
                this.stateManager = new GroupCallStateManager(); // 非单例
            }
            
            // 与Dialog生命周期同步
            public void attachDialog(GroupCallDialog dialog) {
                stateManager.addObserver(dialog);
                dialog.setOnDismissListener(() -> {
                    // Dialog销毁时清理状态
                    cleanup();
                });
            }
            
            // 清理资源，避免内存泄漏
            public void cleanup() {
                observers.clear();
                stateManager.cleanup();
                stateManager = null;
            }
        }
    }
    
    /**
     * 问题3：异步状态更新导致的短暂不一致
     * 
     * 微信群组音视频最佳实践：
     * 1. 状态变更立即反映到UI（乐观更新）
     * 2. 网络确认后修正状态（如果失败）
     * 3. 关键状态变更使用同步机制
     */
    
    // 修复方案3：同步状态更新机制
    public static class SynchronizedStateUpdate {
        
        // 关键状态变更使用同步机制
        public synchronized boolean updateMemberStateSync(String userId, CallMemberState newState) {
            try {
                // 1. 立即更新内存状态
                boolean success = stateManager.updateMemberState(userId, newState);
                if (!success) {
                    return false;
                }
                
                // 2. 立即通知UI（同步）
                notifyUIImmediately(userId, newState);
                
                // 3. 异步发送网络确认
                sendNetworkConfirmationAsync(userId, newState);
                
                return true;
            } catch (Exception e) {
                // 状态更新失败，回滚
                rollbackStateChange(userId);
                return false;
            }
        }
        
        // 立即通知UI，避免延迟
        private void notifyUIImmediately(String userId, CallMemberState newState) {
            // 在主线程中立即更新UI
            if (Looper.myLooper() == Looper.getMainLooper()) {
                notifyObserversDirectly(userId, newState);
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    notifyObserversDirectly(userId, newState);
                });
            }
        }
    }
    
    /**
     * 问题4：成员状态转换不规范
     * 
     * 微信群组通话状态流：
     * IDLE -> INVITING -> RINGING -> CONNECTING -> CONNECTED -> SPEAKING/MUTED -> DISCONNECTED
     * 
     * 当前实现可能跳过某些状态或允许非法转换
     */
    
    // 修复方案4：标准化状态转换
    public static class StandardizedStateTransition {
        
        // 定义合法的状态转换规则
        private static final Map<CallMemberState, Set<CallMemberState>> VALID_TRANSITIONS = 
            new EnumMap<CallMemberState, Set<CallMemberState>>(CallMemberState.class) {{
                put(CallMemberState.IDLE, EnumSet.of(CallMemberState.INVITING));
                put(CallMemberState.INVITING, EnumSet.of(
                    CallMemberState.RINGING, CallMemberState.DECLINED, CallMemberState.TIMEOUT
                ));
                put(CallMemberState.RINGING, EnumSet.of(
                    CallMemberState.CONNECTING, CallMemberState.DECLINED, CallMemberState.TIMEOUT
                ));
                put(CallMemberState.CONNECTING, EnumSet.of(
                    CallMemberState.CONNECTED, CallMemberState.CONNECTION_FAILED
                ));
                put(CallMemberState.CONNECTED, EnumSet.of(
                    CallMemberState.SPEAKING, CallMemberState.MUTED, CallMemberState.DISCONNECTED
                ));
                // ... 更多转换规则
            }};
        
        // 验证状态转换的合法性
        public static boolean isValidTransition(CallMemberState from, CallMemberState to) {
            Set<CallMemberState> validNextStates = VALID_TRANSITIONS.get(from);
            return validNextStates != null && validNextStates.contains(to);
        }
        
        // 安全的状态转换方法
        public boolean transitionMemberState(String userId, CallMemberState newState) {
            GroupCallMember member = findMember(userId);
            if (member == null) {
                return false;
            }
            
            CallMemberState currentState = member.getState();
            
            // 验证转换合法性
            if (!isValidTransition(currentState, newState)) {
                L.e("StateTransition", String.format(
                    "非法状态转换: %s -> %s for user %s", 
                    currentState, newState, userId
                ));
                return false;
            }
            
            // 执行转换
            return member.setState(newState);
        }
    }
}