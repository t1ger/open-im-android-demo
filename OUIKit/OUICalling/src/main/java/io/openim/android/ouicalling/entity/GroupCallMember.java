package io.openim.android.ouicalling.entity;

import io.livekit.android.renderer.TextureViewRenderer;

/**
 * 群组通话成员实体类
 * 管理单个成员在群组通话中的所有状态和属性
 */
public class GroupCallMember {
    private String userId;                          // 用户ID
    private String nickname;                        // 昵称
    private String avatar;                          // 头像URL
    private CallMemberState state;                  // 成员状态
    private boolean isCameraEnabled = true;         // 摄像头是否开启
    private boolean isMicEnabled = true;            // 麦克风是否开启
    private boolean isSpeaking = false;             // 是否正在说话 (v1.2实现)
    private int networkQuality = 5;                 // 网络质量 1-5星 (v1.1实现)
    private long stateTimestamp;                    // 状态变更时间戳
    private TextureViewRenderer videoRenderer;     // 视频渲染器 (资源池管理)
    private long joinTime;                          // 加入通话时间
    private long totalDuration;                     // 总通话时长

    /**
     * 构造函数
     */
    public GroupCallMember(String userId) {
        this.userId = userId;
        this.state = CallMemberState.IDLE;
        this.stateTimestamp = System.currentTimeMillis();
        this.joinTime = 0;
        this.totalDuration = 0;
    }

    public GroupCallMember(String userId, String nickname, String avatar) {
        this(userId);
        this.nickname = nickname;
        this.avatar = avatar;
    }

    /**
     * 设置状态 - 带状态转换验证
     * @param newState 新状态
     * @return 是否成功设置
     */
    public boolean setState(CallMemberState newState) {
        if (newState == null) {
            return false;
        }

        // 状态转换验证
        if (!state.canTransitionTo(newState)) {
            // 记录非法状态转换
            android.util.Log.w("GroupCallMember", 
                String.format("非法状态转换: %s -> %s (用户: %s)", 
                    state.getDescription(), newState.getDescription(), userId));
            return false;
        }

        CallMemberState oldState = this.state;
        this.state = newState;
        this.stateTimestamp = System.currentTimeMillis();

        // 记录特殊状态变更
        if (newState == CallMemberState.CONNECTED && oldState != CallMemberState.CONNECTED) {
            this.joinTime = System.currentTimeMillis();
        } else if (newState.isFinalState() && oldState.isActiveState()) {
            // 计算通话时长
            if (joinTime > 0) {
                this.totalDuration += (System.currentTimeMillis() - joinTime);
            }
        }

        return true;
    }

    /**
     * 强制设置状态 (用于异常恢复)
     */
    public void forceSetState(CallMemberState newState) {
        this.state = newState;
        this.stateTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取状态持续时长 (毫秒)
     */
    public long getStateDuration() {
        return System.currentTimeMillis() - stateTimestamp;
    }

    /**
     * 获取当前通话时长 (毫秒)
     */
    public long getCurrentCallDuration() {
        if (joinTime > 0 && state.isActiveState()) {
            return System.currentTimeMillis() - joinTime;
        }
        return totalDuration;
    }

    /**
     * 检查是否需要状态超时处理
     */
    public boolean isStateTimeout(long timeoutMs) {
        if (state.isWaitingState()) {
            return getStateDuration() > timeoutMs;
        }
        return false;
    }

    /**
     * 更新音视频状态
     */
    public void updateMediaState(boolean camera, boolean mic) {
        this.isCameraEnabled = camera;
        this.isMicEnabled = mic;
    }

    /**
     * 设置网络质量 (1-5星，5为最好)
     */
    public void setNetworkQuality(int quality) {
        this.networkQuality = Math.max(1, Math.min(5, quality));
    }

    /**
     * 获取网络质量描述
     */
    public String getNetworkQualityDescription() {
        switch (networkQuality) {
            case 5: return "优秀";
            case 4: return "良好";
            case 3: return "一般";
            case 2: return "较差";
            case 1: return "很差";
            default: return "未知";
        }
    }

    /**
     * 判断成员是否在线并可以接收音视频
     */
    public boolean isOnlineAndActive() {
        return state.isActiveState();
    }

    /**
     * 判断成员是否可见（有视频输出）
     */
    public boolean isVideoVisible() {
        return isOnlineAndActive() && isCameraEnabled;
    }

    /**
     * 判断成员是否可听（有音频输出）
     */
    public boolean isAudioActive() {
        return isOnlineAndActive() && isMicEnabled;
    }

    /**
     * 重置成员状态（重新邀请时使用）
     */
    public void reset() {
        setState(CallMemberState.IDLE);
        this.isCameraEnabled = true;
        this.isMicEnabled = true;
        this.isSpeaking = false;
        this.networkQuality = 5;
        this.videoRenderer = null;
        this.joinTime = 0;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    // 别名方法，兼容性
    public String getUserID() {
        return userId;
    }

    public String getNickname() {
        return nickname != null ? nickname : userId;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    // 别名方法，兼容性
    public String getFaceURL() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public CallMemberState getState() {
        return state;
    }

    public boolean isCameraEnabled() {
        return isCameraEnabled;
    }

    public void setCameraEnabled(boolean cameraEnabled) {
        isCameraEnabled = cameraEnabled;
    }

    public boolean isMicEnabled() {
        return isMicEnabled;
    }

    // 别名方法，兼容性
    public boolean isMicrophoneEnabled() {
        return isMicEnabled;
    }

    public void setMicEnabled(boolean micEnabled) {
        isMicEnabled = micEnabled;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public void setSpeaking(boolean speaking) {
        isSpeaking = speaking;
    }

    public int getNetworkQuality() {
        return networkQuality;
    }

    public long getStateTimestamp() {
        return stateTimestamp;
    }

    public TextureViewRenderer getVideoRenderer() {
        return videoRenderer;
    }

    public void setVideoRenderer(TextureViewRenderer videoRenderer) {
        this.videoRenderer = videoRenderer;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupCallMember that = (GroupCallMember) o;
        return userId != null ? userId.equals(that.userId) : that.userId == null;
    }

    @Override
    public int hashCode() {
        return userId != null ? userId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GroupCallMember{" +
                "userId='" + userId + '\'' +
                ", nickname='" + nickname + '\'' +
                ", state=" + state.getDescription() +
                ", camera=" + isCameraEnabled +
                ", mic=" + isMicEnabled +
                ", quality=" + networkQuality +
                '}';
    }
}