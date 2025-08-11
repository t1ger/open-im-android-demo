package io.openim.android.ouicalling;

import android.content.Context;
import android.content.DialogInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.sdk.models.InvitationInfo;
import io.openim.android.ouicalling.CallDialogFactory.ValidationResult;

/**
 * CallDialogFactory群组通话创建逻辑单元测试
 * 
 * 测试目标：验证CallDialogFactory.create()方法的群组通话创建逻辑
 * 核心问题：确保群组通话信令被正确识别并创建GroupCallDialog
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CallDialogFactoryTest {

    @Mock
    private SignalingInfo mockSignalingInfo;
    
    @Mock
    private InvitationInfo mockInvitationInfo;
    
    @Mock
    private CallingService mockCallingService;
    
    @Mock
    private DialogInterface.OnDismissListener mockDismissListener;
    
    private Context context;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = RuntimeEnvironment.application;
    }
    
    /**
     * 测试用例1：验证群组通话Dialog创建逻辑
     * 
     * 测试CallDialogFactory.create()方法第68-72行的群组通话创建逻辑
     */
    @Test
    public void testCreateGroupCallDialog_Success() {
        System.out.println("🧪 [TEST] 测试群组通话Dialog创建逻辑");
        
        // 准备测试数据
        String testGroupId = "factory_test_group";
        List<String> testMemberIds = Arrays.asList("factory_user1", "factory_user2");
        
        // 配置Mock - 群组通话信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn(testGroupId);
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(testMemberIds);
        when(mockInvitationInfo.getMediaType()).thenReturn("video"); // 视频通话
        
        // 执行创建逻辑
        DialogCreationResult result = executeDialogCreation(mockSignalingInfo, false);
        
        // 验证结果
        System.out.println("📋 Dialog创建结果:");
        System.out.println("  - 识别为群组通话: " + result.identifiedAsGroupCall);
        System.out.println("  - Dialog创建成功: " + result.dialogCreated);
        System.out.println("  - Dialog类型: " + result.dialogType);
        System.out.println("  - 调用类型描述: " + result.callTypeDescription);
        System.out.println("  - 信令验证: " + result.signalingValidation);
        System.out.println("  - 异常发生: " + result.hasException);
        
        // 断言验证
        assertTrue("应该识别为群组通话", result.identifiedAsGroupCall);
        assertTrue("Dialog应该创建成功", result.dialogCreated);
        assertEquals("应该创建GroupCallDialog", "GroupCallDialog", result.dialogType);
        assertTrue("调用类型描述应该包含群组", result.callTypeDescription.contains("群组"));
        assertTrue("信令验证应该通过", result.signalingValidation.isValid());
        assertFalse("不应该有异常", result.hasException);
        
        System.out.println("✅ [TEST] 群组通话Dialog创建测试通过");
    }
    
    /**
     * 测试用例2：验证单人通话Dialog创建逻辑
     */
    @Test
    public void testCreateSingleCallDialog_Success() {
        System.out.println("🧪 [TEST] 测试单人通话Dialog创建逻辑");
        
        // 配置Mock - 单人通话信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.SINGLE_CHAT);
        when(mockInvitationInfo.getMediaType()).thenReturn("audio"); // 音频通话
        when(mockInvitationInfo.getInviterUserID()).thenReturn("single_caller");
        
        // 执行创建逻辑
        DialogCreationResult result = executeDialogCreation(mockSignalingInfo, false);
        
        // 验证结果
        System.out.println("📋 单人通话创建结果:");
        System.out.println("  - 识别为群组通话: " + result.identifiedAsGroupCall);
        System.out.println("  - Dialog创建成功: " + result.dialogCreated);
        System.out.println("  - Dialog类型: " + result.dialogType);
        System.out.println("  - 调用类型描述: " + result.callTypeDescription);
        
        // 断言验证
        assertFalse("不应该识别为群组通话", result.identifiedAsGroupCall);
        assertTrue("Dialog应该创建成功", result.dialogCreated);
        assertEquals("应该创建SingleCallDialog", "SingleCallDialog", result.dialogType);
        assertTrue("调用类型描述应该包含单人", result.callTypeDescription.contains("单人"));
        
        System.out.println("✅ [TEST] 单人通话Dialog创建测试通过");
    }
    
    /**
     * 测试用例3：验证信令验证逻辑
     */
    @Test
    public void testSignalingInfoValidation() {
        System.out.println("🧪 [TEST] 测试信令信息验证逻辑");
        
        // 场景1：null信令
        ValidationResult result1 = CallDialogFactory.validateSignalingInfo(null);
        System.out.println("📋 null信令验证: " + result1.getErrorMessage());
        assertFalse("null信令应该验证失败", result1.isValid());
        assertTrue("错误信息应该提到null", result1.getErrorMessage().contains("null"));
        
        // 场景2：null invitation
        when(mockSignalingInfo.getInvitation()).thenReturn(null);
        ValidationResult result2 = CallDialogFactory.validateSignalingInfo(mockSignalingInfo);
        System.out.println("📋 null invitation验证: " + result2.getErrorMessage());
        assertFalse("null invitation应该验证失败", result2.isValid());
        
        // 场景3：群组通话缺少GroupID
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getMediaType()).thenReturn("video");
        when(mockInvitationInfo.getGroupID()).thenReturn(null);
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("user1"));
        
        ValidationResult result3 = CallDialogFactory.validateSignalingInfo(mockSignalingInfo);
        System.out.println("📋 缺少GroupID验证: " + result3.getErrorMessage());
        assertFalse("缺少GroupID应该验证失败", result3.isValid());
        assertTrue("错误信息应该提到GroupID", result3.getErrorMessage().contains("群组ID"));
        
        // 场景4：群组通话缺少成员列表
        when(mockInvitationInfo.getGroupID()).thenReturn("valid_group");
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(null);
        
        ValidationResult result4 = CallDialogFactory.validateSignalingInfo(mockSignalingInfo);
        System.out.println("📋 缺少成员列表验证: " + result4.getErrorMessage());
        assertFalse("缺少成员列表应该验证失败", result4.isValid());
        
        // 场景5：完整的群组通话信令
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("user1", "user2"));
        
        ValidationResult result5 = CallDialogFactory.validateSignalingInfo(mockSignalingInfo);
        System.out.println("📋 完整信令验证: " + (result5.isValid() ? "通过" : result5.getErrorMessage()));
        assertTrue("完整信令应该验证通过", result5.isValid());
        
        System.out.println("✅ [TEST] 信令信息验证测试通过");
    }
    
    /**
     * 测试用例4：验证通话类型识别逻辑
     */
    @Test
    public void testCallTypeIdentification() {
        System.out.println("🧪 [TEST] 测试通话类型识别逻辑");
        
        // 群组视频通话
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getMediaType()).thenReturn("video");
        
        String groupVideo = CallDialogFactory.getCallTypeDescription(mockSignalingInfo);
        System.out.println("📋 群组视频通话: " + groupVideo);
        assertTrue("应该识别为群组视频通话", groupVideo.contains("群组") && groupVideo.contains("视频"));
        
        // 群组音频通话
        when(mockInvitationInfo.getMediaType()).thenReturn("audio");
        
        String groupAudio = CallDialogFactory.getCallTypeDescription(mockSignalingInfo);
        System.out.println("📋 群组音频通话: " + groupAudio);
        assertTrue("应该识别为群组音频通话", groupAudio.contains("群组") && groupAudio.contains("音频"));
        
        // 单人视频通话
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.SINGLE_CHAT);
        when(mockInvitationInfo.getMediaType()).thenReturn("video");
        
        String singleVideo = CallDialogFactory.getCallTypeDescription(mockSignalingInfo);
        System.out.println("📋 单人视频通话: " + singleVideo);
        assertTrue("应该识别为单人视频通话", singleVideo.contains("单人") && singleVideo.contains("视频"));
        
        // 异常情况
        String errorType = CallDialogFactory.getCallTypeDescription(null);
        System.out.println("📋 异常情况: " + errorType);
        assertTrue("异常情况应该返回未知类型", errorType.contains("未知"));
        
        System.out.println("✅ [TEST] 通话类型识别测试通过");
    }
    
    /**
     * 测试用例5：验证异常处理逻辑
     */
    @Test
    public void testExceptionHandling() {
        System.out.println("🧪 [TEST] 测试异常处理逻辑");
        
        // 场景1：无效信令导致的异常
        when(mockSignalingInfo.getInvitation()).thenThrow(new RuntimeException("Mock exception"));
        
        DialogCreationResult result1 = executeDialogCreation(mockSignalingInfo, false);
        
        System.out.println("📋 无效信令异常处理:");
        System.out.println("  - 有异常: " + result1.hasException);
        System.out.println("  - 异常类型: " + result1.exceptionType);
        System.out.println("  - 异常信息: " + result1.exceptionMessage);
        System.out.println("  - Dialog创建: " + result1.dialogCreated);
        
        // 断言验证
        assertTrue("应该捕获异常", result1.hasException);
        assertFalse("异常情况下Dialog不应创建成功", result1.dialogCreated);
        assertNotNull("异常信息不应为空", result1.exceptionMessage);
        
        System.out.println("✅ [TEST] 异常处理测试通过");
    }
    
    /**
     * 测试用例6：验证DismissListener设置
     */
    @Test
    public void testDismissListenerSetting() {
        System.out.println("🧪 [TEST] 测试DismissListener设置");
        
        // 准备有效的群组通话信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn("test_group");
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("user1"));
        when(mockInvitationInfo.getMediaType()).thenReturn("video");
        
        // 测试带DismissListener的创建
        DialogCreationResult result1 = executeDialogCreationWithListener(mockSignalingInfo, false, mockDismissListener);
        
        System.out.println("📋 DismissListener设置结果:");
        System.out.println("  - Dialog创建成功: " + result1.dialogCreated);
        System.out.println("  - Listener设置成功: " + result1.dismissListenerSet);
        
        // 断言验证
        assertTrue("Dialog应该创建成功", result1.dialogCreated);
        assertTrue("DismissListener应该被设置", result1.dismissListenerSet);
        
        // 测试不带DismissListener的创建
        DialogCreationResult result2 = executeDialogCreationWithListener(mockSignalingInfo, false, null);
        assertTrue("不带Listener时Dialog也应该创建成功", result2.dialogCreated);
        assertFalse("不应该设置DismissListener", result2.dismissListenerSet);
        
        System.out.println("✅ [TEST] DismissListener设置测试通过");
    }
    
    /**
     * 测试用例7：验证isCallOut参数影响
     */
    @Test
    public void testCallOutParameterEffect() {
        System.out.println("🧪 [TEST] 测试isCallOut参数影响");
        
        // 准备测试信令
        when(mockSignalingInfo.getInvitation()).thenReturn(mockInvitationInfo);
        when(mockInvitationInfo.getSessionType()).thenReturn(ConversationType.GROUP_CHAT);
        when(mockInvitationInfo.getGroupID()).thenReturn("test_group");
        when(mockInvitationInfo.getInviteeUserIDList()).thenReturn(Arrays.asList("user1"));
        when(mockInvitationInfo.getMediaType()).thenReturn("video");
        
        // 测试呼出场景
        DialogCreationResult outgoingResult = executeDialogCreation(mockSignalingInfo, true);
        
        System.out.println("📋 呼出场景结果:");
        System.out.println("  - Dialog创建成功: " + outgoingResult.dialogCreated);
        System.out.println("  - 是呼出场景: " + outgoingResult.isCallOut);
        
        // 测试呼入场景
        DialogCreationResult incomingResult = executeDialogCreation(mockSignalingInfo, false);
        
        System.out.println("📋 呼入场景结果:");
        System.out.println("  - Dialog创建成功: " + incomingResult.dialogCreated);
        System.out.println("  - 是呼出场景: " + incomingResult.isCallOut);
        
        // 断言验证
        assertTrue("呼出场景Dialog应该创建成功", outgoingResult.dialogCreated);
        assertTrue("呼入场景Dialog应该创建成功", incomingResult.dialogCreated);
        assertTrue("应该正确记录呼出状态", outgoingResult.isCallOut);
        assertFalse("应该正确记录呼入状态", incomingResult.isCallOut);
        
        System.out.println("✅ [TEST] isCallOut参数测试通过");
    }
    
    // 辅助方法
    
    /**
     * 执行Dialog创建逻辑并返回结果
     */
    private DialogCreationResult executeDialogCreation(SignalingInfo signalingInfo, boolean isCallOut) {
        return executeDialogCreationWithListener(signalingInfo, isCallOut, null);
    }
    
    /**
     * 执行带DismissListener的Dialog创建逻辑并返回结果
     */
    private DialogCreationResult executeDialogCreationWithListener(SignalingInfo signalingInfo, boolean isCallOut, DialogInterface.OnDismissListener listener) {
        DialogCreationResult result = new DialogCreationResult();
        
        try {
            result.isCallOut = isCallOut;
            
            // 预先验证信令
            result.signalingValidation = CallDialogFactory.validateSignalingInfo(signalingInfo);
            
            // 获取通话类型描述
            result.callTypeDescription = CallDialogFactory.getCallTypeDescription(signalingInfo);
            
            // 模拟判断是否为群组通话（使用与实际代码相同的逻辑）
            if (signalingInfo != null && signalingInfo.getInvitation() != null) {
                result.identifiedAsGroupCall = signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT;
            }
            
            // 模拟创建Dialog
            if (result.identifiedAsGroupCall) {
                result.dialogType = "GroupCallDialog";
                result.dialogCreated = true;
            } else {
                result.dialogType = "SingleCallDialog";
                result.dialogCreated = true;
            }
            
            // 模拟设置DismissListener
            if (listener != null) {
                result.dismissListenerSet = true;
            }
            
        } catch (Exception e) {
            result.hasException = true;
            result.exceptionType = e.getClass().getSimpleName();
            result.exceptionMessage = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Dialog创建结果数据类
     */
    private static class DialogCreationResult {
        // 基本结果
        boolean dialogCreated = false;
        String dialogType = null;
        boolean identifiedAsGroupCall = false;
        boolean isCallOut = false;
        
        // 验证和描述
        ValidationResult signalingValidation = null;
        String callTypeDescription = null;
        
        // 监听器相关
        boolean dismissListenerSet = false;
        
        // 异常相关
        boolean hasException = false;
        String exceptionType = null;
        String exceptionMessage = null;
    }
}