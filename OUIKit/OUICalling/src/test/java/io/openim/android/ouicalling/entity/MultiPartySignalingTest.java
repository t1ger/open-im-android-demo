package io.openim.android.ouicalling.entity;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MultiPartySignaling 单元测试
 * TDD模式：先写测试，确保数据结构和功能正确性
 */
public class MultiPartySignalingTest {

    private MultiPartySignaling signaling;
    private static final String TEST_ROOM_ID = "room_123456";
    private static final String TEST_INVITER_ID = "user_001";
    private static final String TEST_MEMBER_ID = "user_002";

    @Before
    public void setUp() {
        signaling = new MultiPartySignaling();
    }

    @Test
    public void testSignalingCreation() {
        // 测试信令对象创建时自动生成ID和时间戳
        assertNotNull("messageId should be auto-generated", signaling.getMessageId());
        assertTrue("timestamp should be auto-generated", signaling.getTimestamp() > 0);
    }

    @Test
    public void testBasicProperties() {
        // 测试基础属性设置和获取
        signaling.setType("MULTI_PARTY_INVITE");
        signaling.setRoomID(TEST_ROOM_ID);
        signaling.setInviterID(TEST_INVITER_ID);
        signaling.setVideoCall(true);

        assertEquals("MULTI_PARTY_INVITE", signaling.getType());
        assertEquals(TEST_ROOM_ID, signaling.getRoomID());
        assertEquals(TEST_INVITER_ID, signaling.getInviterID());
        assertTrue(signaling.isVideoCall());
    }

    @Test
    public void testInviteeList() {
        // 测试被邀请人列表
        List<String> inviteeList = Arrays.asList("user_002", "user_003", "user_004");
        signaling.setInviteeList(inviteeList);

        assertNotNull(signaling.getInviteeList());
        assertEquals(3, signaling.getInviteeList().size());
        assertTrue(signaling.getInviteeList().contains("user_002"));
    }

    @Test
    public void testMemberProperties() {
        // 测试成员相关属性
        signaling.setMemberID(TEST_MEMBER_ID);
        signaling.setMemberState("CONNECTED");

        assertEquals(TEST_MEMBER_ID, signaling.getMemberID());
        assertEquals("CONNECTED", signaling.getMemberState());
    }

    @Test
    public void testExtraData() {
        // 测试扩展数据
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("quality", "high");
        extraData.put("duration", 3600);
        signaling.setExtraData(extraData);

        assertNotNull(signaling.getExtraData());
        assertEquals("high", signaling.getExtraData().get("quality"));
        assertEquals(3600, signaling.getExtraData().get("duration"));
    }

    @Test
    public void testMessageIdUniqueness() {
        // 测试消息ID唯一性
        MultiPartySignaling signaling1 = new MultiPartySignaling();
        MultiPartySignaling signaling2 = new MultiPartySignaling();

        assertNotEquals("Message IDs should be unique", 
                signaling1.getMessageId(), signaling2.getMessageId());
    }

    @Test
    public void testTimestampGeneration() {
        // 测试时间戳生成
        long beforeCreation = System.currentTimeMillis();
        MultiPartySignaling newSignaling = new MultiPartySignaling();
        long afterCreation = System.currentTimeMillis();

        assertTrue("Timestamp should be between creation time", 
                newSignaling.getTimestamp() >= beforeCreation && 
                newSignaling.getTimestamp() <= afterCreation);
    }

    @Test
    public void testToString() {
        // 测试toString方法
        signaling.setType("MULTI_PARTY_INVITE");
        signaling.setRoomID(TEST_ROOM_ID);
        signaling.setInviterID(TEST_INVITER_ID);
        signaling.setVideoCall(true);

        String result = signaling.toString();
        
        assertTrue("toString should contain type", result.contains("MULTI_PARTY_INVITE"));
        assertTrue("toString should contain roomID", result.contains(TEST_ROOM_ID));
        assertTrue("toString should contain inviterID", result.contains(TEST_INVITER_ID));
        assertTrue("toString should contain isVideoCall", result.contains("true"));
    }

    @Test
    public void testVideoCallFlag() {
        // 测试视频通话标识
        signaling.setVideoCall(true);
        assertTrue("Should be video call", signaling.isVideoCall());

        signaling.setVideoCall(false);
        assertFalse("Should be audio call", signaling.isVideoCall());
    }

    @Test
    public void testCustomMessageId() {
        // 测试自定义消息ID
        String customId = "custom_message_001";
        signaling.setMessageId(customId);
        assertEquals(customId, signaling.getMessageId());
    }
}