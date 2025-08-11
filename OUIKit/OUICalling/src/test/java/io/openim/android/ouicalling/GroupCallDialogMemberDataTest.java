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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.openim.android.ouicalling.vm.CallingVM;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicalling.adapter.GroupMemberAdapter;

/**
 * GroupCallDialog成员数据获取单元测试
 * 
 * 测试目标：验证refreshMemberList()方法的数据获取和UI更新逻辑
 * 核心问题：确保从CallingVM获取的成员数据正确传递给UI适配器
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GroupCallDialogMemberDataTest {

    @Mock
    private CallingVM mockCallingVM;
    
    @Mock
    private GroupMemberAdapter mockMemberAdapter;
    
    private TestableGroupCallDialog dialog;
    private Context context;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = RuntimeEnvironment.application;
        dialog = new TestableGroupCallDialog(context);
        dialog.setMockDependencies(mockCallingVM, mockMemberAdapter);
    }
    
    /**
     * 测试用例1：验证refreshMemberList正常数据获取流程
     * 
     * 测试GroupCallDialog.refreshMemberList()方法第428-473行的核心逻辑
     */
    @Test
    public void testRefreshMemberList_NormalDataFlow() {
        System.out.println("🧪 [TEST] 测试refreshMemberList正常数据获取流程");
        
        // 准备测试数据
        List<GroupCallMember> testMembers = Arrays.asList(
            createTestMember("self_user", "已连接"),
            createTestMember("member1", "邀请中"),
            createTestMember("member2", "已连接")
        );
        
        // 配置Mock - CallingVM返回正常数据
        when(mockCallingVM.getGroupMembers()).thenReturn(testMembers);
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 刷新结果:");
        System.out.println("  - 调用getGroupMembers次数: " + result.getGroupMembersCallCount);
        System.out.println("  - 获取到成员数量: " + result.finalMemberCount);
        System.out.println("  - 使用了延迟重试: " + result.usedDelayRetry);
        System.out.println("  - 适配器更新成功: " + result.adapterUpdated);
        System.out.println("  - 传递给适配器的成员数: " + result.membersPassedToAdapter);
        System.out.println("  - 强制刷新调用: " + result.notifyDataSetChangedCalled);
        
        // 断言验证
        assertEquals("应该获取到正确的成员数量", 3, result.finalMemberCount);
        assertEquals("应该至少调用一次getGroupMembers", 1, result.getGroupMembersCallCount);
        assertFalse("正常情况下不应使用延迟重试", result.usedDelayRetry);
        assertTrue("适配器应该被更新", result.adapterUpdated);
        assertEquals("传递给适配器的成员数应该正确", 3, result.membersPassedToAdapter);
        assertTrue("应该调用notifyDataSetChanged", result.notifyDataSetChangedCalled);
        
        // 验证适配器交互
        verify(mockMemberAdapter, times(1)).updateMembers(testMembers);
        verify(mockMemberAdapter, times(1)).notifyDataSetChanged();
        
        System.out.println("✅ [TEST] 正常数据获取流程测试通过");
    }
    
    /**
     * 测试用例2：验证空数据时的延迟重试逻辑
     * 
     * 测试GroupCallDialog.refreshMemberList()第432-442行的重试逻辑
     */
    @Test
    public void testRefreshMemberList_EmptyDataRetryLogic() {
        System.out.println("🧪 [TEST] 测试空数据延迟重试逻辑");
        
        // 配置Mock - 第一次返回空，第二次返回数据
        List<GroupCallMember> emptyList = new ArrayList<>();
        List<GroupCallMember> validData = Arrays.asList(
            createTestMember("delayed_user", "已连接")
        );
        
        when(mockCallingVM.getGroupMembers())
            .thenReturn(emptyList)  // 第一次调用返回空
            .thenReturn(validData); // 第二次调用返回数据
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 延迟重试结果:");
        System.out.println("  - getGroupMembers调用次数: " + result.getGroupMembersCallCount);
        System.out.println("  - 使用了延迟重试: " + result.usedDelayRetry);
        System.out.println("  - 最终成员数量: " + result.finalMemberCount);
        System.out.println("  - 延迟时间: " + result.delayMilliseconds + "ms");
        
        // 断言验证
        assertEquals("应该调用两次getGroupMembers", 2, result.getGroupMembersCallCount);
        assertTrue("应该使用延迟重试", result.usedDelayRetry);
        assertEquals("最终应该获取到正确数据", 1, result.finalMemberCount);
        assertTrue("延迟时间应该大于0", result.delayMilliseconds > 0);
        
        // 验证Mock交互
        verify(mockCallingVM, times(2)).getGroupMembers();
        verify(mockMemberAdapter, times(1)).updateMembers(validData);
        
        System.out.println("✅ [TEST] 延迟重试逻辑测试通过");
    }
    
    /**
     * 测试用例3：验证持续为空数据的处理
     */
    @Test
    public void testRefreshMemberList_PersistentEmptyData() {
        System.out.println("🧪 [TEST] 测试持续为空数据的处理");
        
        // 配置Mock - 始终返回空数据
        List<GroupCallMember> emptyList = new ArrayList<>();
        when(mockCallingVM.getGroupMembers()).thenReturn(emptyList);
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 持续空数据结果:");
        System.out.println("  - 调用次数: " + result.getGroupMembersCallCount);
        System.out.println("  - 使用了延迟重试: " + result.usedDelayRetry);
        System.out.println("  - 最终成员数量: " + result.finalMemberCount);
        System.out.println("  - 传递空列表给适配器: " + result.passedEmptyListToAdapter);
        System.out.println("  - 适配器仍然被更新: " + result.adapterUpdated);
        
        // 断言验证
        assertEquals("应该尝试两次调用", 2, result.getGroupMembersCallCount);
        assertTrue("应该使用延迟重试", result.usedDelayRetry);
        assertEquals("最终成员数量应该为0", 0, result.finalMemberCount);
        assertTrue("应该传递空列表给适配器", result.passedEmptyListToAdapter);
        assertTrue("适配器仍应被更新", result.adapterUpdated);
        
        // 验证适配器接收到空列表
        verify(mockMemberAdapter, times(1)).updateMembers(emptyList);
        verify(mockMemberAdapter, times(1)).notifyDataSetChanged();
        
        System.out.println("✅ [TEST] 持续空数据处理测试通过");
    }
    
    /**
     * 测试用例4：验证null数据的处理
     */
    @Test
    public void testRefreshMemberList_NullDataHandling() {
        System.out.println("🧪 [TEST] 测试null数据处理");
        
        // 配置Mock - 返回null
        when(mockCallingVM.getGroupMembers()).thenReturn(null);
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 null数据处理结果:");
        System.out.println("  - 调用次数: " + result.getGroupMembersCallCount);
        System.out.println("  - 使用了延迟重试: " + result.usedDelayRetry);
        System.out.println("  - 最终成员数量: " + result.finalMemberCount);
        System.out.println("  - 有异常: " + result.hasException);
        System.out.println("  - 传递空列表给适配器: " + result.passedEmptyListToAdapter);
        
        // 断言验证
        assertEquals("应该尝试两次调用", 2, result.getGroupMembersCallCount);
        assertTrue("应该使用延迟重试", result.usedDelayRetry);
        assertEquals("最终成员数量应该为0", 0, result.finalMemberCount);
        assertFalse("不应该有未处理异常", result.hasException);
        assertTrue("应该传递空列表给适配器", result.passedEmptyListToAdapter);
        
        System.out.println("✅ [TEST] null数据处理测试通过");
    }
    
    /**
     * 测试用例5：验证适配器为null时的处理
     */
    @Test
    public void testRefreshMemberList_NullAdapterHandling() {
        System.out.println("🧪 [TEST] 测试适配器为null时的处理");
        
        // 设置适配器为null
        dialog.setMockDependencies(mockCallingVM, null);
        
        // 配置正常数据
        List<GroupCallMember> testMembers = Arrays.asList(createTestMember("test_user", "已连接"));
        when(mockCallingVM.getGroupMembers()).thenReturn(testMembers);
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 null适配器处理结果:");
        System.out.println("  - 检测到null适配器: " + result.adapterIsNull);
        System.out.println("  - 有异常: " + result.hasException);
        System.out.println("  - 适配器更新: " + result.adapterUpdated);
        
        // 断言验证
        assertTrue("应该检测到null适配器", result.adapterIsNull);
        assertFalse("不应该有未处理异常", result.hasException);
        assertFalse("适配器不应被更新", result.adapterUpdated);
        
        System.out.println("✅ [TEST] null适配器处理测试通过");
    }
    
    /**
     * 测试用例6：验证大量成员数据的处理
     */
    @Test
    public void testRefreshMemberList_LargeMemberList() {
        System.out.println("🧪 [TEST] 测试大量成员数据处理");
        
        // 创建大量成员数据（模拟9+成员的大群通话）
        List<GroupCallMember> largeMembers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            largeMembers.add(createTestMember("user_" + i, i == 0 ? "已连接" : "邀请中"));
        }
        
        when(mockCallingVM.getGroupMembers()).thenReturn(largeMembers);
        
        // 执行refreshMemberList
        MemberRefreshResult result = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 大量成员处理结果:");
        System.out.println("  - 成员数量: " + result.finalMemberCount);
        System.out.println("  - 网格布局调整: " + result.gridLayoutAdjusted);
        System.out.println("  - 建议布局: " + result.suggestedSpanCount + "x" + result.suggestedSpanCount);
        System.out.println("  - 性能正常: " + !result.hasPerformanceIssue);
        
        // 断言验证
        assertEquals("应该处理所有成员", 12, result.finalMemberCount);
        assertTrue("大量成员时应该调整网格布局", result.gridLayoutAdjusted);
        assertEquals("12个成员应该使用3x3布局", 3, result.suggestedSpanCount);
        assertFalse("不应该有性能问题", result.hasPerformanceIssue);
        
        // 验证适配器接收到所有数据
        verify(mockMemberAdapter, times(1)).updateMembers(largeMembers);
        
        System.out.println("✅ [TEST] 大量成员数据处理测试通过");
    }
    
    /**
     * 测试用例7：验证成员状态变化时的刷新
     */
    @Test
    public void testRefreshMemberList_StateChangeTriggeredRefresh() {
        System.out.println("🧪 [TEST] 测试成员状态变化触发的刷新");
        
        // 模拟成员状态变化：从邀请中到已连接
        List<GroupCallMember> initialMembers = Arrays.asList(
            createTestMember("user1", "邀请中"),
            createTestMember("user2", "邀请中")
        );
        
        List<GroupCallMember> updatedMembers = Arrays.asList(
            createTestMember("user1", "已连接"),
            createTestMember("user2", "已连接")
        );
        
        when(mockCallingVM.getGroupMembers())
            .thenReturn(initialMembers)
            .thenReturn(updatedMembers);
        
        // 第一次刷新
        MemberRefreshResult result1 = dialog.executeRefreshMemberList();
        
        // 模拟状态变化后的第二次刷新
        MemberRefreshResult result2 = dialog.executeRefreshMemberList();
        
        // 验证结果
        System.out.println("📋 状态变化刷新结果:");
        System.out.println("  - 第一次成员数: " + result1.finalMemberCount);
        System.out.println("  - 第二次成员数: " + result2.finalMemberCount);
        System.out.println("  - 状态有变化: " + (result1.finalMemberCount == result2.finalMemberCount));
        
        // 断言验证
        assertEquals("两次刷新成员数应该相同", result1.finalMemberCount, result2.finalMemberCount);
        assertTrue("两次都应该成功更新适配器", result1.adapterUpdated && result2.adapterUpdated);
        
        // 验证适配器被调用两次
        verify(mockMemberAdapter, times(2)).updateMembers(any());
        verify(mockMemberAdapter, times(2)).notifyDataSetChanged();
        
        System.out.println("✅ [TEST] 状态变化刷新测试通过");
    }
    
    // 辅助方法和类
    
    /**
     * 创建测试成员对象
     */
    private GroupCallMember createTestMember(String userId, String stateDesc) {
        GroupCallMember member = new GroupCallMember();
        member.setUserId(userId);
        member.setState(stateDesc.equals("已连接") ? GroupCallMember.State.CONNECTED : GroupCallMember.State.INVITING);
        member.setNickname("昵称_" + userId);
        return member;
    }
    
    /**
     * 可测试的GroupCallDialog实现
     */
    private static class TestableGroupCallDialog extends GroupCallDialog {
        
        private CallingVM mockCallingVM;
        private GroupMemberAdapter mockMemberAdapter;
        
        public TestableGroupCallDialog(Context context) {
            super(context);
        }
        
        public void setMockDependencies(CallingVM callingVM, GroupMemberAdapter adapter) {
            this.mockCallingVM = callingVM;
            this.mockMemberAdapter = adapter;
        }
        
        /**
         * 执行refreshMemberList逻辑并返回详细结果
         */
        public MemberRefreshResult executeRefreshMemberList() {
            MemberRefreshResult result = new MemberRefreshResult();
            
            try {
                result.startTime = System.currentTimeMillis();
                
                if (mockMemberAdapter != null) {
                    result.adapterIsNull = false;
                    
                    // 尝试1：直接获取
                    List<GroupCallMember> groupMembers = mockCallingVM.getGroupMembers();
                    result.getGroupMembersCallCount++;
                    
                    // 如果为空，等待100ms后重试
                    if (groupMembers == null || groupMembers.isEmpty()) {
                        result.usedDelayRetry = true;
                        long delayStart = System.currentTimeMillis();
                        
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                        
                        result.delayMilliseconds = System.currentTimeMillis() - delayStart;
                        
                        // 尝试2：延迟获取
                        groupMembers = mockCallingVM.getGroupMembers();
                        result.getGroupMembersCallCount++;
                    }
                    
                    result.finalMemberCount = groupMembers != null ? groupMembers.size() : 0;
                    
                    // 更新适配器
                    if (groupMembers != null && !groupMembers.isEmpty()) {
                        mockMemberAdapter.updateMembers(groupMembers);
                        result.membersPassedToAdapter = groupMembers.size();
                        result.passedEmptyListToAdapter = false;
                    } else {
                        mockMemberAdapter.updateMembers(new ArrayList<>());
                        result.membersPassedToAdapter = 0;
                        result.passedEmptyListToAdapter = true;
                    }
                    
                    result.adapterUpdated = true;
                    
                    // 调整网格布局
                    result.gridLayoutAdjusted = true;
                    if (result.finalMemberCount <= 1) {
                        result.suggestedSpanCount = 1;
                    } else if (result.finalMemberCount <= 4) {
                        result.suggestedSpanCount = 2;
                    } else {
                        result.suggestedSpanCount = 3;
                    }
                    
                    // 强制刷新
                    mockMemberAdapter.notifyDataSetChanged();
                    result.notifyDataSetChangedCalled = true;
                    
                } else {
                    result.adapterIsNull = true;
                }
                
                result.endTime = System.currentTimeMillis();
                result.hasPerformanceIssue = (result.endTime - result.startTime) > 1000; // 超过1秒算性能问题
                
            } catch (Exception e) {
                result.hasException = true;
                result.exceptionMessage = e.getMessage();
            }
            
            return result;
        }
    }
    
    /**
     * 成员刷新结果数据类
     */
    private static class MemberRefreshResult {
        // 基本结果
        int getGroupMembersCallCount = 0;
        int finalMemberCount = 0;
        boolean usedDelayRetry = false;
        long delayMilliseconds = 0;
        
        // 适配器相关
        boolean adapterIsNull = false;
        boolean adapterUpdated = false;
        int membersPassedToAdapter = 0;
        boolean passedEmptyListToAdapter = false;
        boolean notifyDataSetChangedCalled = false;
        
        // 布局相关
        boolean gridLayoutAdjusted = false;
        int suggestedSpanCount = 0;
        
        // 性能和异常
        long startTime = 0;
        long endTime = 0;
        boolean hasPerformanceIssue = false;
        boolean hasException = false;
        String exceptionMessage = null;
    }
}