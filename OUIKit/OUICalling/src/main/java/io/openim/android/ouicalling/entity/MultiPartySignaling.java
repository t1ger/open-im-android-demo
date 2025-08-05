package io.openim.android.ouicalling.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 群组通话信令数据结构
 * 基于现有1v1通话信令扩展，保持数据结构一致性
 */
public class MultiPartySignaling {
    private String type;                    // 信令类型
    private String roomID;                  // 通话房间ID
    private String inviterID;               // 发起人ID
    private List<String> inviteeList;       // 被邀请人列表
    private String memberID;                // 成员ID（用于成员相关信令）
    private String memberState;             // 成员状态（暂时用String，后续优化为枚举）
    private boolean isVideoCall;            // 是否为视频通话
    private long timestamp;                 // 时间戳
    private String messageId;               // 消息唯一ID（去重用）
    private Map<String, Object> extraData;  // 扩展数据

    public MultiPartySignaling() {
        this.timestamp = System.currentTimeMillis();
        this.messageId = UUID.randomUUID().toString();
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoomID() {
        return roomID;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }

    public String getInviterID() {
        return inviterID;
    }

    public void setInviterID(String inviterID) {
        this.inviterID = inviterID;
    }

    public List<String> getInviteeList() {
        return inviteeList;
    }

    public void setInviteeList(List<String> inviteeList) {
        this.inviteeList = inviteeList;
    }

    public String getMemberID() {
        return memberID;
    }

    public void setMemberID(String memberID) {
        this.memberID = memberID;
    }

    public String getMemberState() {
        return memberState;
    }

    public void setMemberState(String memberState) {
        this.memberState = memberState;
    }

    public boolean isVideoCall() {
        return isVideoCall;
    }

    public void setVideoCall(boolean videoCall) {
        isVideoCall = videoCall;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }

    @Override
    public String toString() {
        return "MultiPartySignaling{" +
                "type='" + type + '\'' +
                ", roomID='" + roomID + '\'' +
                ", inviterID='" + inviterID + '\'' +
                ", memberID='" + memberID + '\'' +
                ", isVideoCall=" + isVideoCall +
                ", timestamp=" + timestamp +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}