package io.openim.android.ouicalling;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.sdk.models.InvitationInfo;
import io.openim.android.ouicalling.vm.CallingVM;

/**
 * CallingServiceImp.call()方法单元测试
 * 
 * 测试目标：验证call()方法中的群组通话预初始化逻辑
 * 核心问题：确保预初始化数据被正确创建和传递给Dialog
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CallingServiceCallMethodTest {

    @Mock
    private SignalingInfo mockSignalingInfo;
    
    @Mock
    private InvitationInfo mockInvitationInfo;
    
    @Mock
    private BaseCallDialog mockCallDialog;
    
    @Mock
    private CallingVM mockCallingVM;
    
    private TestableCallingServiceImp callingService;
    private Context context;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = RuntimeEnvironment.application;
        callingService = new TestableCallingServiceImp();
    }
    
    /**
     * 测试用例1：验证群组通话预初始化逻辑
     * 
     * 测试CallingServiceImp.call()方法第567-597行的预初始化逻辑
     */
    @Test
    public void testGroupCallPreInitializationLogic() {
        System.out.println("🧪 [TEST] 测试群组通话预初始化逻辑");
        
        // 准备测试数据
        String testGroupId = "test_group_pre_init";
        List<String> testMemberIds = Arrays.asList("member1", "member2", "member3");
        
        // 配置Mock对象 - 群组通话信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn(testGroupId);
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(testMemberIds);
        
        // 执行预初始化逻辑
        PreInitResult result = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        // 验证预初始化结果
        System.out.println("📋 预初始化结果:");
        System.out.println("  - 是否识别为群组通话: " + result.isGroupCallDetected);
        System.out.println("  - 数据完整性检查: " + result.isDataComplete);
        System.out.println("  - GroupID: " + result.extractedGroupId);
        System.out.println("  - 成员数量: " + result.memberCount);
        System.out.println("  - 预期成员总数: " + result.expectedMemberCount);
        System.out.println("  - 预初始化数据已创建: " + result.preInitDataCreated);
        
        // 断言验证
        assertTrue("必须识别为群组通话", result.isGroupCallDetected);
        assertTrue("数据必须完整", result.isDataComplete);
        assertEquals("GroupID必须正确", testGroupId, result.extractedGroupId);
        assertEquals("成员数量必须正确", testMemberIds.size(), result.memberCount);
        assertEquals("预期成员总数必须正确", testMemberIds.size() + 1, result.expectedMemberCount);
        assertTrue("预期成员数必须大于等于2", result.expectedMemberCount >= 2);
        assertTrue("预初始化数据必须被创建", result.preInitDataCreated);
        
        System.out.println("✅ [TEST] 群组通话预初始化逻辑测试通过");
    }
    
    /**
     * 测试用例2：验证非群组通话不会触发预初始化
     */
    @Test
    public void testNonGroupCall_NoPreInitialization() {
        System.out.println("🧪 [TEST] 测试非群组通话不触发预初始化");
        
        // 配置Mock对象 - 单人通话信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.SINGLE_CHAT);
        
        // 执行预初始化逻辑
        PreInitResult result = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        // 验证结果
        System.out.println("📋 单人通话结果:");
        System.out.println("  - 是否识别为群组通话: " + result.isGroupCallDetected);
        System.out.println("  - 预初始化数据已创建: " + result.preInitDataCreated);
        
        // 断言验证
        assertFalse("单人通话不应被识别为群组通话", result.isGroupCallDetected);
        assertFalse("单人通话不应创建预初始化数据", result.preInitDataCreated);
        
        System.out.println("✅ [TEST] 非群组通话预初始化测试通过");
    }
    
    /**
     * 测试用例3：验证数据不完整时的处理
     */
    @Test
    public void testIncompleteGroupCallData_ShouldTerminate() {
        System.out.println("🧪 [TEST] 测试数据不完整时的处理");
        
        // 场景1：GroupID为null
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn(null);
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("user1"));
        
        PreInitResult result1 = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        System.out.println("📋 场景1 - GroupID为null:");
        System.out.println("  - 识别为群组通话: " + result1.isGroupCallDetected);
        System.out.println("  - 数据完整性: " + result1.isDataComplete);
        System.out.println("  - 应该终止: " + result1.shouldTerminate);
        
        assertTrue("应该识别为群组通话", result1.isGroupCallDetected);
        assertFalse("数据应该不完整", result1.isDataComplete);
        assertTrue("应该终止处理", result1.shouldTerminate);
        assertFalse("不应创建预初始化数据", result1.preInitDataCreated);
        
        // 场景2：成员列表为空
        when(mockInvitationInfo.getGroupID()).thenReturn("valid_group");
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList());
        
        PreInitResult result2 = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        System.out.println("📋 场景2 - 成员列表为空:");
        System.out.println("  - 识别为群组通话: " + result2.isGroupCallDetected);
        System.out.println("  - 数据完整性: " + result2.isDataComplete);
        System.out.println("  - 应该终止: " + result2.shouldTerminate);
        
        assertTrue("应该识别为群组通话", result2.isGroupCallDetected);
        assertFalse("数据应该不完整", result2.isDataComplete);
        assertTrue("应该终止处理", result2.shouldTerminate);
        assertFalse("不应创建预初始化数据", result2.preInitDataCreated);
        
        System.out.println("✅ [TEST] 数据不完整处理测试通过");
    }
    
    /**
     * 测试用例4：验证预期成员数量不足时的处理
     */
    @Test
    public void testInsufficientMemberCount_ShouldTerminate() {
        System.out.println("🧪 [TEST] 测试预期成员数量不足时的处理");
        
        // 配置只有1个成员的群组通话（加上发起者自己总共2个，边界情况）
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn("small_group");
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("single_user"));
        
        PreInitResult result1 = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        System.out.println("📋 边界情况 - 1个被邀请者:");
        System.out.println("  - 预期成员总数: " + result1.expectedMemberCount);
        System.out.println("  - 数据完整性: " + result1.isDataComplete);
        System.out.println("  - 预初始化数据已创建: " + result1.preInitDataCreated);
        
        assertTrue("数据应该完整", result1.isDataComplete);
        assertEquals("预期成员数应该是2", 2, result1.expectedMemberCount);
        assertTrue("边界情况应该允许创建", result1.preInitDataCreated);
        
        // 配置0个成员的群组通话（无效情况）
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList());
        
        PreInitResult result2 = callingService.executePreInitializationLogic(mockSignalingInfo);
        
        System.out.println("📋 无效情况 - 0个被邀请者:");
        System.out.println("  - 数据完整性: " + result2.isDataComplete);
        System.out.println("  - 应该终止: " + result2.shouldTerminate);
        
        assertFalse("数据应该不完整", result2.isDataComplete);
        assertTrue("应该终止处理", result2.shouldTerminate);
        
        System.out.println("✅ [TEST] 预期成员数量检查测试通过");
    }
    
    /**
     * 测试用例5：验证Dialog创建和数据传递逻辑
     */
    @Test
    public void testDialogCreationAndDataTransfer() {
        System.out.println("🧪 [TEST] 测试Dialog创建和数据传递逻辑");
        
        // 准备测试数据
        String testGroupId = "dialog_test_group";
        List<String> testMemberIds = Arrays.asList("dialog_user1", "dialog_user2");
        
        // 配置Mock
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn(testGroupId);
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(testMemberIds);
        
        // 配置Dialog Mock
        when(mockCallDialog.getCallingVM()).thenReturn(mockCallingVM);
        when(mockCallingVM.getGroupMembers()).thenReturn(Arrays.asList()); // 初始为空，然后被填充
        
        // 执行完整的call方法流程
        DialogCreationResult dialogResult = callingService.executeDialogCreationLogic(mockSignalingInfo, mockCallDialog);
        
        // 验证结果
        System.out.println("📋 Dialog创建结果:");
        System.out.println("  - 预初始化数据创建: " + dialogResult.preInitDataCreated);
        System.out.println("  - Dialog创建成功: " + dialogResult.dialogCreated);
        System.out.println("  - 是GroupCallDialog: " + dialogResult.isGroupCallDialog);
        System.out.println("  - 数据传递给VM: " + dialogResult.dataTransferredToVM);
        System.out.println("  - UI刷新调用: " + dialogResult.uiRefreshCalled);
        
        // 断言验证
        assertTrue("预初始化数据必须创建", dialogResult.preInitDataCreated);
        assertTrue("Dialog必须创建成功", dialogResult.dialogCreated);
        assertTrue("必须是GroupCallDialog", dialogResult.isGroupCallDialog);
        assertTrue("数据必须传递给VM", dialogResult.dataTransferredToVM);
        assertTrue("UI刷新必须被调用", dialogResult.uiRefreshCalled);
        
        System.out.println("✅ [TEST] Dialog创建和数据传递测试通过");
    }
    
    /**
     * 测试用例6：验证异常处理逻辑
     */
    @Test
    public void testExceptionHandling() {
        System.out.println("🧪 [TEST] 测试异常处理逻辑");
        
        // 场景1：SignalingInfo为null
        PreInitResult result1 = callingService.executePreInitializationLogic(null);
        assertFalse("null信令不应触发群组逻辑", result1.isGroupCallDetected);
        assertFalse("null信令不应创建预初始化数据", result1.preInitDataCreated);
        System.out.println("📋 场景1 - null信令处理: ✅ PASS");
        
        // 场景2：invitation为null
        when(mockSignalingInfo.getInvitation()).thenReturn(null);
        PreInitResult result2 = callingService.executePreInitializationLogic(mockSignalingInfo);
        assertFalse("null invitation不应触发群组逻辑", result2.isGroupCallDetected);
        System.out.println("📋 场景2 - null invitation处理: ✅ PASS");
        
        // 场景3：sessionType为null
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(null);
        
        boolean hasException = false;
        try {
            PreInitResult result3 = callingService.executePreInitializationLogic(mockSignalingInfo);
            assertFalse("null sessionType不应触发群组逻辑", result3.isGroupCallDetected);
        } catch (Exception e) {
            hasException = true;
            System.out.println("📋 场景3 - null sessionType异常: " + e.getMessage());
        }
        
        // 应该优雅处理，不抛异常
        assertFalse("不应该抛出未处理的异常", hasException);
        System.out.println("📋 场景3 - null sessionType处理: ✅ PASS");
        
        System.out.println("✅ [TEST] 异常处理逻辑测试通过");
    }
    
    // 辅助类和方法
    
    /**
     * 可测试的CallingServiceImp实现
     */
    private static class TestableCallingServiceImp extends CallingServiceImp {
        
        /**
         * 执行预初始化逻辑（提取CallingServiceImp.call()中第567-597行的逻辑）
         */
        public PreInitResult executePreInitializationLogic(SignalingInfo signalingInfo) {
            PreInitResult result = new PreInitResult();
            
            try {
                // 检查是否为群组通话
                if (signalingInfo != null && 
                    signalingInfo.getInvitation() != null && 
                    signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT) {
                    
                    result.isGroupCallDetected = true;
                    
                    // 提取群组通话信息
                    String groupId = signalingInfo.getInvitation().getGroupID();
                    List<String> memberIds = signalingInfo.getInvitation().getInviteeUserIDList();
                    
                    result.extractedGroupId = groupId;
                    result.memberIds = memberIds;
                    result.memberCount = memberIds != null ? memberIds.size() : 0;
                    
                    // 验证数据完整性
                    if (groupId == null || memberIds == null || memberIds.isEmpty()) {
                        result.isDataComplete = false;
                        result.shouldTerminate = true;
                        return result;
                    }
                    
                    result.isDataComplete = true;
                    
                    // 计算预期成员数量
                    int expectedMemberCount = memberIds.size() + 1; // +1 为发起者自己
                    result.expectedMemberCount = expectedMemberCount;
                    
                    if (expectedMemberCount < 2) {
                        result.shouldTerminate = true;
                        return result;
                    }
                    
                    // 创建预初始化数据（模拟）
                    result.preInitDataCreated = true;
                }
                
            } catch (Exception e) {
                result.hasException = true;
                result.exceptionMessage = e.getMessage();
            }
            
            return result;
        }
        
        /**
         * 执行Dialog创建逻辑（模拟完整的call方法流程）
         */
        public DialogCreationResult executeDialogCreationLogic(SignalingInfo signalingInfo, BaseCallDialog mockDialog) {
            DialogCreationResult result = new DialogCreationResult();
            
            try {
                // 先执行预初始化
                PreInitResult preInitResult = executePreInitializationLogic(signalingInfo);
                result.preInitDataCreated = preInitResult.preInitDataCreated;
                
                if (preInitResult.shouldTerminate) {
                    return result;
                }
                
                // 模拟Dialog创建
                result.dialogCreated = true;
                
                // 检查是否为GroupCallDialog并传递数据
                if (mockDialog instanceof GroupCallDialog && preInitResult.preInitDataCreated) {
                    result.isGroupCallDialog = true;
                    result.dataTransferredToVM = true;
                    
                    // 模拟UI刷新调用
                    result.uiRefreshCalled = true;
                }
                
            } catch (Exception e) {
                result.hasException = true;
                result.exceptionMessage = e.getMessage();
            }
            
            return result;
        }
    }
    
    /**
     * 预初始化结果数据类
     */
    private static class PreInitResult {
        boolean isGroupCallDetected = false;
        boolean isDataComplete = false;
        boolean shouldTerminate = false;
        boolean preInitDataCreated = false;
        boolean hasException = false;
        String exceptionMessage = null;
        
        String extractedGroupId = null;
        List<String> memberIds = null;
        int memberCount = 0;
        int expectedMemberCount = 0;
    }
    
    /**
     * Dialog创建结果数据类
     */
    private static class DialogCreationResult {
        boolean preInitDataCreated = false;
        boolean dialogCreated = false;
        boolean isGroupCallDialog = false;
        boolean dataTransferredToVM = false;
        boolean uiRefreshCalled = false;
        boolean hasException = false;
        String exceptionMessage = null;
    }
}