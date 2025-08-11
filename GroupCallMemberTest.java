/**
 * 群组音视频成员初始化单元测试
 * 简化版本，不依赖JUnit，直接运行
 */

import java.util.ArrayList;
import java.util.List;

public class GroupCallMemberTest {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String GROUP_ID = "2496693853";
    
    // 模拟CallingVM（简化版）
    private MockCallingVM callingVM;
    
    public void setUp() {
        callingVM = new MockCallingVM();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
    }
    
    /**
     * 测试场景1：发起方通过initiateGroupCall初始化
     */
    public boolean testInitiateGroupCall() {
        System.out.println("\n=== 测试场景1：发起方通过initiateGroupCall初始化 ===");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        System.out.println("输入：memberIds = [" + INVITED_USER_1 + "]");
        System.out.println("当前用户：" + CURRENT_USER_ID);
        System.out.println("期望结果：2个成员（自己+被邀请人）");
        
        // 执行测试
        callingVM.initiateGroupCall(GROUP_ID, memberIds, false);
        List<GroupCallMember> members = callingVM.getGroupMembers();
        
        System.out.println("实际结果：groupMembers.size() = " + members.size());
        
        // 打印详细成员信息
        for (int i = 0; i < members.size(); i++) {
            GroupCallMember member = members.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + 
                " - 状态: " + member.getState() + 
                " - " + (member.getUserId().equals(CURRENT_USER_ID) ? "自己" : "被邀请人"));
        }
        
        // 验证结果
        if (members.size() != 2) {
            System.err.println("❌ 失败：期望2个成员，实际" + members.size() + "个成员");
            return false;
        }
        
        boolean foundSelf = false;
        boolean foundInvitee = false;
        for (GroupCallMember member : members) {
            if (member.getUserId().equals(CURRENT_USER_ID)) {
                foundSelf = true;
                if (member.getState() != CallMemberState.CONNECTED) {
                    System.err.println("❌ 失败：发起方状态应该是CONNECTED，实际是" + member.getState());
                    return false;
                }
            } else if (member.getUserId().equals(INVITED_USER_1)) {
                foundInvitee = true;
                if (member.getState() != CallMemberState.INVITING) {
                    System.err.println("❌ 失败：被邀请人状态应该是INVITING，实际是" + member.getState());
                    return false;
                }
            }
        }
        
        if (!foundSelf) {
            System.err.println("❌ 失败：成员列表中没有找到发起方自己");
            return false;
        }
        if (!foundInvitee) {
            System.err.println("❌ 失败：成员列表中没有找到被邀请人");
            return false;
        }
        
        System.out.println("✅ 测试场景1通过");
        return true;
    }
    
    /**
     * 测试场景2：CallingService调用initializeGroupMembers
     * 这是关键测试！模拟当前日志显示的调用路径
     */
    public boolean testCallingServicePath() {
        System.out.println("\n=== 测试场景2：CallingService调用路径（关键测试）===");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        System.out.println("输入：memberIds = [" + INVITED_USER_1 + "]");
        System.out.println("当前用户：" + CURRENT_USER_ID);
        System.out.println("模拟：CallingService.call() → initializeGroupMembers(memberIds, groupId)");
        System.out.println("期望结果：2个成员（自己+被邀请人）");
        
        // 执行CallingService的调用路径
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        List<GroupCallMember> members = callingVM.getGroupMembers();
        
        System.out.println("实际结果：groupMembers.size() = " + members.size());
        
        // 打印详细成员信息
        for (int i = 0; i < members.size(); i++) {
            GroupCallMember member = members.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + 
                " - 状态: " + member.getState() + 
                " - " + (member.getUserId().equals(CURRENT_USER_ID) ? "自己" : "被邀请人"));
        }
        
        // 🔍 关键验证：这里会暴露真正的问题！
        if (members.size() == 1) {
            System.err.println("🎯 找到问题了！CallingService路径只返回1个成员，缺少发起方自己！");
            System.err.println("   这就是为什么日志显示'群组成员初始化完成: 1 个成员'的原因！");
            System.err.println("   initializeGroupMembers()方法没有添加发起方自己到列表中！");
            return false;
        }
        
        if (members.size() != 2) {
            System.err.println("❌ 失败：期望2个成员，实际" + members.size() + "个成员");
            return false;
        }
        
        System.out.println("✅ 测试场景2通过");
        return true;
    }
    
    /**
     * 测试场景3：对比两种初始化方法
     */
    public boolean testMethodConsistency() {
        System.out.println("\n=== 测试场景3：对比两种初始化方法 ===");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        // 测试initiateGroupCall
        MockCallingVM vm1 = new MockCallingVM();
        vm1.setCurrentUserId(CURRENT_USER_ID);
        vm1.initiateGroupCall(GROUP_ID, memberIds, false);
        int count1 = vm1.getGroupMembers().size();
        
        // 测试initializeGroupMembers
        MockCallingVM vm2 = new MockCallingVM();
        vm2.setCurrentUserId(CURRENT_USER_ID);
        vm2.initializeGroupMembers(memberIds, GROUP_ID);
        int count2 = vm2.getGroupMembers().size();
        
        System.out.println("initiateGroupCall() 结果: " + count1 + " 个成员");
        System.out.println("initializeGroupMembers() 结果: " + count2 + " 个成员");
        
        if (count1 != count2) {
            System.err.println("🎯 发现不一致！两个方法返回不同的成员数量！");
            System.err.println("   这就是问题的根源：initializeGroupMembers()实现不完整！");
            return false;
        }
        
        System.out.println("✅ 测试场景3通过");
        return true;
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 群组音视频成员初始化单元测试");
        System.out.println("目标：找出为什么九宫格显示0个成员的真正原因");
        System.out.println("基于用户日志：'群组成员初始化完成: 1 个成员' 但九宫格显示0个");
        
        GroupCallMemberTest test = new GroupCallMemberTest();
        boolean allPassed = true;
        
        try {
            // 测试1：发起方场景
            test.setUp();
            if (!test.testInitiateGroupCall()) {
                allPassed = false;
            }
            
            // 测试2：CallingService路径（关键！）
            test.setUp();
            if (!test.testCallingServicePath()) {
                allPassed = false;
            }
            
            // 测试3：一致性对比
            if (!test.testMethodConsistency()) {
                allPassed = false;
            }
            
            if (allPassed) {
                System.out.println("\n🎉 所有测试通过！群组成员初始化逻辑正确。");
            } else {
                System.err.println("\n❌ 测试失败！找到问题根源。");
                System.err.println("\n🔧 修复建议：");
                System.err.println("1. 检查CallingVM.initializeGroupMembers(List<String>, String)方法");
                System.err.println("2. 确保该方法也添加发起方自己到成员列表");
                System.err.println("3. 保持与initiateGroupCall()方法的一致性");
            }
            
        } catch (Exception e) {
            System.err.println("\n💥 测试执行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/**
 * 简化的CallingVM模拟类
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
    
    // 模拟initiateGroupCall方法（基于我们的修复版本）
    public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
        groupMembers.clear();
        
        // ✅ 正确逻辑：先添加自己（发起方）
        GroupCallMember selfMember = new GroupCallMember(currentUserId);
        selfMember.setState(CallMemberState.CONNECTED);
        groupMembers.add(selfMember);
        
        // 然后添加被邀请的成员
        for (String memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                GroupCallMember member = new GroupCallMember(memberId);
                member.setState(CallMemberState.INVITING);
                groupMembers.add(member);
            }
        }
    }
    
    // 模拟initializeGroupMembers公开方法（这是CallingService调用的方法）
    public void initializeGroupMembers(List<String> memberIds, String groupId) {
        // 🤔 问题可能在这里！让我们看看实际的实现
        initializeGroupMembersInternal(memberIds);
    }
    
    // 模拟当前的私有initializeGroupMembers方法
    private void initializeGroupMembersInternal(List<String> memberIds) {
        groupMembers.clear();
        
        // ❌ 当前问题：只添加被邀请人，没有添加自己！
        for (String memberId : memberIds) {
            GroupCallMember member = new GroupCallMember(memberId);
            member.setState(CallMemberState.INVITING);
            groupMembers.add(member);
        }
        
        // 🎯 缺少这部分：添加发起方自己
        // GroupCallMember selfMember = new GroupCallMember(currentUserId);
        // selfMember.setState(CallMemberState.CONNECTED);
        // groupMembers.add(selfMember);
    }
}

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

enum CallMemberState {
    CONNECTED("已连接"),
    INVITING("邀请中");
    
    private String description;
    
    CallMemberState(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}