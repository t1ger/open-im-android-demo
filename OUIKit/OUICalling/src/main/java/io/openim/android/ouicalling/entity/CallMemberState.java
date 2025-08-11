package io.openim.android.ouicalling.entity;

/**
 * 群组通话成员状态枚举
 * 替代简单的String状态，提供状态转换验证和业界标准的状态管理
 */
public enum CallMemberState {
    IDLE("idle", "空闲"),                    // 空闲状态
    INVITING("inviting", "邀请中"),          // 正在邀请
    RINGING("ringing", "响铃中"),            // 对方响铃
    CONNECTING("connecting", "连接中"),       // 正在连接
    CONNECTED("connected", "已连接"),         // 已连接通话
    SPEAKING("speaking", "正在发言"),        // 正在发言
    MUTED("muted", "已静音"),              // 已静音
    DISCONNECTED("disconnected", "已断开"),   // 已断开连接
    REJECTED("rejected", "已拒绝"),          // 拒绝通话
    TIMEOUT("timeout", "超时"),              // 邀请超时
    NETWORK_ERROR("network_error", "网络异常"), // 网络异常
    AUDIO_ONLY("audio_only", "仅音频");      // 仅音频模式

    private final String value;
    private final String description;

    CallMemberState(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 状态转换验证 - 业界最佳实践
     * 防止非法状态跳转，确保状态机正确性
     */
    public boolean canTransitionTo(CallMemberState target) {
        if (target == null) return false;
        
        switch (this) {
            case IDLE:
                // 空闲状态只能转到邀请中
                return target == INVITING;
                
            case INVITING:
                // 邀请中可以转到响铃、拒绝、超时、网络异常
                return target == RINGING || target == REJECTED || 
                       target == TIMEOUT || target == NETWORK_ERROR;
                       
            case RINGING:
                // 响铃中可以转到连接中、拒绝、超时、网络异常
                return target == CONNECTING || target == REJECTED || 
                       target == TIMEOUT || target == NETWORK_ERROR;
                       
            case CONNECTING:
                // 连接中可以转到已连接、断开、网络异常
                return target == CONNECTED || target == DISCONNECTED || 
                       target == NETWORK_ERROR;
                       
            case CONNECTED:
                // 已连接可以转到断开、仅音频、正在发言、已静音、网络异常
                return target == DISCONNECTED || target == AUDIO_ONLY || 
                       target == SPEAKING || target == MUTED ||
                       target == NETWORK_ERROR;
                       
            case SPEAKING:
                // 正在发言可以转到已连接、已静音、断开、网络异常
                return target == CONNECTED || target == MUTED || 
                       target == DISCONNECTED || target == NETWORK_ERROR;
                       
            case MUTED:
                // 已静音可以转到已连接、正在发言、断开、网络异常
                return target == CONNECTED || target == SPEAKING || 
                       target == DISCONNECTED || target == NETWORK_ERROR;
                       
            case AUDIO_ONLY:
                // 仅音频可以转到已连接、断开、网络异常
                return target == CONNECTED || target == DISCONNECTED || 
                       target == NETWORK_ERROR;
                       
            case NETWORK_ERROR:
                // 网络异常可以恢复到连接中、断开
                return target == CONNECTING || target == DISCONNECTED;
                
            case DISCONNECTED:
            case REJECTED:
            case TIMEOUT:
                // 终态，不能再转换 (除非重新开始)
                return target == IDLE;
                
            default:
                return false;
        }
    }

    /**
     * 判断是否为活跃状态 (正在通话中的状态)
     */
    public boolean isActiveState() {
        return this == CONNECTED || this == AUDIO_ONLY || this == CONNECTING ||
               this == SPEAKING || this == MUTED;
    }

    /**
     * 判断是否为终态 (通话结束的状态)
     */
    public boolean isFinalState() {
        return this == DISCONNECTED || this == REJECTED || this == TIMEOUT;
    }

    /**
     * 判断是否为等待状态 (等待用户响应的状态)
     */
    public boolean isWaitingState() {
        return this == INVITING || this == RINGING;
    }

    /**
     * 判断是否为错误状态
     */
    public boolean isErrorState() {
        return this == NETWORK_ERROR || this == TIMEOUT;
    }

    /**
     * 从字符串值获取枚举 (向后兼容)
     */
    public static CallMemberState fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return IDLE;
        }
        
        for (CallMemberState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        
        // 兼容旧版本可能的状态值
        switch (value.toLowerCase()) {
            case "calling":
            case "invite":
                return INVITING;
            case "ring":
                return RINGING;
            case "accept":
            case "accepted":
                return CONNECTED;
            case "reject":
                return REJECTED;
            case "cancel":
            case "hangup":
                return DISCONNECTED;
            default:
                return IDLE;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}