/**
 * 群组音视频成员初始化单元测试
 * 
 * 测试目标：
 * 1. 验证发起方初始化逻辑 - 应该看到自己+被邀请人
 * 2. 验证CallingService调用的初始化逻辑
 * 3. 验证不同方法调用路径的一致性
 * 4. 找出当前实现的真正问题
 */

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import java.util.ArrayList;
import java.util.List;

public class GroupCallMemberInitializationTest {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String INVITED_USER_2 = "7483490110"; // 被邀请人2
    private static final String GROUP_ID = "2496693853";
    
    // 模拟CallingVM（简化版）
    private MockCallingVM callingVM;
    
    @Before
    public void setUp() {
        callingVM = new MockCallingVM();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
    }
    
    /**
     * 测试场景1：发起方通过initiateGroupCall初始化
     * 预期：自己(CONNECTED) + 被邀请人(INVITING) = 2个成员
     */
    @Test
    public void testInitiateGroupCall_ShouldIncludeSelfAndInvitees() {
        System.out.println("\n=== 测试场景1：发起方通过initiateGroupCall初始化 ===");
        
        // 准备测试数据
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        System.out.println("输入：memberIds = [" + INVITED_USER_1 + "]");
        System.out.println("当前用户：" + CURRENT_USER_ID);
        
        // 执行测试
        callingVM.initiateGroupCall(GROUP_ID, memberIds, false);
        
        // 验证结果
        List<GroupCallMember> members = callingVM.getGroupMembers();
        System.out.println("结果：groupMembers.size() = " + members.size());
        
        // 打印详细成员信息
        for (int i = 0; i < members.size(); i++) {
            GroupCallMember member = members.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + 
                " - 状态: " + member.getState() + 
                " - " + (member.getUserId().equals(CURRENT_USER_ID) ? "自己" : "被邀请人"));
        }
        
        // 断言验证
        Assert.assertEquals("发起方应该看到2个成员（自己+被邀请人）", 2, members.size());
        
        // 验证自己在成员列表中
        boolean foundSelf = false;
        boolean foundInvitee = false;
        for (GroupCallMember member : members) {
            if (member.getUserId().equals(CURRENT_USER_ID)) {
                foundSelf = true;
                Assert.assertEquals("发起方状态应该是CONNECTED", 
                    CallMemberState.CONNECTED, member.getState());
            } else if (member.getUserId().equals(INVITED_USER_1)) {
                foundInvitee = true;
                Assert.assertEquals("被邀请人状态应该是INVITING", 
                    CallMemberState.INVITING, member.getState());
            }
        }
        
        Assert.assertTrue("成员列表中应该包含发起方自己", foundSelf);
        Assert.assertTrue("成员列表中应该包含被邀请人", foundInvitee);
        
        System.out.println("✅ 测试场景1通过");
    }
    
    /**
     * 测试场景2：CallingService调用initializeGroupMembers
     * 这是当前日志显示的调用路径
     */
    @Test
    public void testCallingServiceInitialization_ShouldMatchInitiateGroupCall() {
        System.out.println("\n=== 测试场景2：CallingService调用initializeGroupMembers ===");
        
        // 准备测试数据
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        System.out.println("输入：memberIds = [" + INVITED_USER_1 + "]");
        System.out.println("当前用户：" + CURRENT_USER_ID);
        System.out.println("模拟CallingService调用：initializeGroupMembers(memberIds, groupId)");
        
        // 执行CallingService的调用路径
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        
        // 验证结果
        List<GroupCallMember> members = callingVM.getGroupMembers();
        System.out.println("结果：groupMembers.size() = " + members.size());
        
        // 打印详细成员信息
        for (int i = 0; i < members.size(); i++) {
            GroupCallMember member = members.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + 
                " - 状态: " + member.getState() + 
                " - " + (member.getUserId().equals(CURRENT_USER_ID) ? "自己" : "被邀请人"));
        }
        
        // 这里是关键：验证CallingService路径是否和initiateGroupCall一致
        Assert.assertEquals("CallingService路径应该和initiateGroupCall一致，显示2个成员", 2, members.size());
        
        // 验证成员组成
        boolean foundSelf = false;
        boolean foundInvitee = false;
        for (GroupCallMember member : members) {
            if (member.getUserId().equals(CURRENT_USER_ID)) {
                foundSelf = true;
            } else if (member.getUserId().equals(INVITED_USER_1)) {
                foundInvitee = true;
            }
        }
        
        Assert.assertTrue("CallingService路径也应该包含发起方自己", foundSelf);
        Assert.assertTrue("CallingService路径应该包含被邀请人", foundInvitee);
        
        System.out.println("✅ 测试场景2通过");
    }
    
    /**
     * 测试场景3：多个被邀请人的场景
     */
    @Test
    public void testMultipleInvitees_ShouldShowCorrectCount() {
        System.out.println("\n=== 测试场景3：多个被邀请人场景 ===");
        
        // 准备测试数据
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        memberIds.add(INVITED_USER_2);
        
        System.out.println("输入：memberIds = [" + INVITED_USER_1 + ", " + INVITED_USER_2 + "]");
        System.out.println("当前用户：" + CURRENT_USER_ID);
        
        // 执行测试
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        
        // 验证结果
        List<GroupCallMember> members = callingVM.getGroupMembers();
        System.out.println("结果：groupMembers.size() = " + members.size());
        
        // 应该是：自己 + 2个被邀请人 = 3个成员
        Assert.assertEquals("应该显示3个成员（自己+2个被邀请人）", 3, members.size());
        
        System.out.println("✅ 测试场景3通过");
    }
    
    /**
     * 测试场景4：边界情况 - memberIds包含自己
     */
    @Test
    public void testMemberIdsIncludingSelf_ShouldNotDuplicate() {
        System.out.println("\n=== 测试场景4：边界情况 - memberIds包含自己 ===");
        
        // 准备测试数据 - memberIds意外包含了自己
        List<String> memberIds = new ArrayList<>();
        memberIds.add(CURRENT_USER_ID); // 意外包含自己
        memberIds.add(INVITED_USER_1);
        
        System.out.println("输入：memberIds = [" + CURRENT_USER_ID + "(自己), " + INVITED_USER_1 + "]");
        
        // 执行测试
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        
        // 验证结果
        List<GroupCallMember> members = callingVM.getGroupMembers();
        System.out.println("结果：groupMembers.size() = " + members.size());
        
        // 应该去重，还是2个成员
        Assert.assertEquals("应该去重，显示2个成员", 2, members.size());
        
        // 验证没有重复
        int selfCount = 0;
        for (GroupCallMember member : members) {
            if (member.getUserId().equals(CURRENT_USER_ID)) {
                selfCount++;
            }
        }
        Assert.assertEquals("自己不应该重复", 1, selfCount);
        
        System.out.println("✅ 测试场景4通过");
    }
    
    /**
     * 运行所有测试的主方法
     */
    public static void main(String[] args) {
        System.out.println("🚀 群组音视频成员初始化单元测试开始");
        System.out.println("目标：找出当前实现的真正问题");
        
        GroupCallMemberInitializationTest test = new GroupCallMemberInitializationTest();
        
        try {
            test.setUp();
            test.testInitiateGroupCall_ShouldIncludeSelfAndInvitees();
            
            test.setUp();
            test.testCallingServiceInitialization_ShouldMatchInitiateGroupCall();
            
            test.setUp();
            test.testMultipleInvitees_ShouldShowCorrectCount();
            
            test.setUp();
            test.testMemberIdsIncludingSelf_ShouldNotDuplicate();
            
            System.out.println("\n🎉 所有测试通过！");
            
        } catch (AssertionError e) {
            System.err.println("\n❌ 测试失败: " + e.getMessage());
            System.err.println("这就是导致九宫格显示问题的根本原因！");
            
            // 打印当前实现的问题分析
            System.err.println("\n📋 问题分析:");
            System.err.println("1. 检查initiateGroupCall()是否正确添加自己");
            System.err.println("2. 检查initializeGroupMembers()公开方法是否调用了正确的私有方法");
            System.err.println("3. 检查私有的initializeGroupMembers(List<String>)是否包含自己");
            System.err.println("4. 确认CallingService调用的是哪个方法路径");
            
        } catch (Exception e) {
            System.err.println("\n💥 测试执行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * 简化的CallingVM模拟类，用于测试
 */
class MockCallingVM {
    private String currentUserId;
    private List<GroupCallMember> groupMembers = new ArrayList<>();
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    public List<GroupCallMember> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }
    
    // 模拟initiateGroupCall方法（基于我们的修复）
    public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
        groupMembers.clear();
        
        // 🎯 正确逻辑：先添加自己（发起方）到九宫格
        GroupCallMember selfMember = new GroupCallMember(currentUserId);
        selfMember.setState(CallMemberState.CONNECTED); // 发起方默认已连接状态
        groupMembers.add(selfMember);
        
        // 然后添加被邀请的成员
        for (String memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                GroupCallMember member = new GroupCallMember(memberId);
                member.setState(CallMemberState.INVITING); // 被邀请成员初始状态为邀请中
                groupMembers.add(member);
            }
        }
    }
    
    // 模拟initializeGroupMembers公开方法
    public void initializeGroupMembers(List<String> memberIds, String groupId) {
        // 调用私有实现方法
        initializeGroupMembersInternal(memberIds);
    }
    
    // 模拟私有的initializeGroupMembers方法（当前的实现）
    private void initializeGroupMembersInternal(List<String> memberIds) {
        groupMembers.clear();
        
        // ❌ 当前实现：只添加被邀请人，不添加自己！
        for (String memberId : memberIds) {
            GroupCallMember member = new GroupCallMember(memberId);
            member.setState(CallMemberState.INVITING);
            groupMembers.add(member);
        }
        
        // 🤔 问题：这里应该也要添加自己，但当前没有！
    }
}

/**
 * 简化的GroupCallMember类
 */
class GroupCallMember {
    private String userId;
    private CallMemberState state;
    
    public GroupCallMember(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public CallMemberState getState() {
        return state;
    }
    
    public void setState(CallMemberState state) {
        this.state = state;
    }
}

/**
 * 简化的CallMemberState枚举
 */
enum CallMemberState {
    CONNECTED("已连接"),
    INVITING("邀请中"),
    TIMEOUT("超时");
    
    private String description;
    
    CallMemberState(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}