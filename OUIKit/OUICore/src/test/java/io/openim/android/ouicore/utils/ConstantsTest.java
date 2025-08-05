package io.openim.android.ouicore.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Constants 单元测试
 * 测试信令类型定义的正确性和完整性
 */
public class ConstantsTest {

    @Test
    public void testSingleCallSignalingTypes() {
        // 测试现有1v1通话信令类型定义
        assertEquals(200, Constants.MsgType.callingInvite);
        assertEquals(201, Constants.MsgType.callingAccept);
        assertEquals(202, Constants.MsgType.callingReject);
        assertEquals(203, Constants.MsgType.callingCancel);
        assertEquals(204, Constants.MsgType.callingHungup);
    }

    @Test
    public void testGroupCallSignalingTypes() {
        // 测试新增群组通话信令类型定义
        assertEquals(210, Constants.MsgType.multiPartyInvite);
        assertEquals(211, Constants.MsgType.multiPartyAccept);
        assertEquals(212, Constants.MsgType.multiPartyReject);
        assertEquals(213, Constants.MsgType.multiPartyCancel);
        assertEquals(214, Constants.MsgType.multiPartyHangup);
        assertEquals(215, Constants.MsgType.multiPartyMemberJoin);
        assertEquals(216, Constants.MsgType.multiPartyMemberLeave);
        assertEquals(217, Constants.MsgType.multiPartyMemberStateChange);
        assertEquals(218, Constants.MsgType.multiPartySpeakingState);
        assertEquals(219, Constants.MsgType.multiPartyQualityReport);
    }

    @Test
    public void testSignalingTypeRanges() {
        // 测试信令类型范围不冲突
        assertTrue("Single call range should be 200-204", 
                Constants.MsgType.callingInvite >= 200 && 
                Constants.MsgType.callingHungup <= 204);

        assertTrue("Group call range should be 210-219", 
                Constants.MsgType.multiPartyInvite >= 210 && 
                Constants.MsgType.multiPartyQualityReport <= 219);
    }

    @Test
    public void testNoSignalingTypeConflicts() {
        // 测试信令类型无冲突
        int[] singleCallTypes = {
                Constants.MsgType.callingInvite,
                Constants.MsgType.callingAccept,
                Constants.MsgType.callingReject,
                Constants.MsgType.callingCancel,
                Constants.MsgType.callingHungup
        };

        int[] groupCallTypes = {
                Constants.MsgType.multiPartyInvite,
                Constants.MsgType.multiPartyAccept,
                Constants.MsgType.multiPartyReject,
                Constants.MsgType.multiPartyCancel,
                Constants.MsgType.multiPartyHangup,
                Constants.MsgType.multiPartyMemberJoin,
                Constants.MsgType.multiPartyMemberLeave,
                Constants.MsgType.multiPartyMemberStateChange,
                Constants.MsgType.multiPartySpeakingState,
                Constants.MsgType.multiPartyQualityReport
        };

        // 检查单人通话类型内部无重复
        for (int i = 0; i < singleCallTypes.length; i++) {
            for (int j = i + 1; j < singleCallTypes.length; j++) {
                assertNotEquals("Single call types should be unique", 
                        singleCallTypes[i], singleCallTypes[j]);
            }
        }

        // 检查群组通话类型内部无重复
        for (int i = 0; i < groupCallTypes.length; i++) {
            for (int j = i + 1; j < groupCallTypes.length; j++) {
                assertNotEquals("Group call types should be unique", 
                        groupCallTypes[i], groupCallTypes[j]);
            }
        }

        // 检查两个范围之间无冲突
        for (int singleType : singleCallTypes) {
            for (int groupType : groupCallTypes) {
                assertNotEquals("Single and group call types should not conflict", 
                        singleType, groupType);
            }
        }
    }

    @Test
    public void testLocalCallHistoryType() {
        // 测试本地呼叫记录类型
        assertEquals(-110, Constants.MsgType.LOCAL_CALL_HISTORY);
    }

    @Test
    public void testMediaTypes() {
        // 测试媒体类型定义
        assertEquals("video", Constants.MediaType.VIDEO);
        assertEquals("audio", Constants.MediaType.AUDIO);
    }

    @Test
    public void testMaxCallNumber() {
        // 测试最大通话人数常量
        assertEquals(9, Constants.MAX_CALL_NUM);
    }

    @Test
    public void testSignalingTypesCoverage() {
        // 测试信令类型覆盖完整性 - 群组通话核心操作都有对应信令
        
        // 基础操作信令
        assertTrue("Should have invite signaling", 
                Constants.MsgType.multiPartyInvite > 0);
        assertTrue("Should have accept signaling", 
                Constants.MsgType.multiPartyAccept > 0);
        assertTrue("Should have reject signaling", 
                Constants.MsgType.multiPartyReject > 0);
        assertTrue("Should have cancel signaling", 
                Constants.MsgType.multiPartyCancel > 0);
        assertTrue("Should have hangup signaling", 
                Constants.MsgType.multiPartyHangup > 0);

        // 成员管理信令
        assertTrue("Should have member join signaling", 
                Constants.MsgType.multiPartyMemberJoin > 0);
        assertTrue("Should have member leave signaling", 
                Constants.MsgType.multiPartyMemberLeave > 0);
        assertTrue("Should have member state change signaling", 
                Constants.MsgType.multiPartyMemberStateChange > 0);

        // 高级功能信令
        assertTrue("Should have speaking state signaling", 
                Constants.MsgType.multiPartySpeakingState > 0);
        assertTrue("Should have quality report signaling", 
                Constants.MsgType.multiPartyQualityReport > 0);
    }
}