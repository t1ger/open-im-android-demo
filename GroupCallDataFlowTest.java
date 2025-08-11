/**
 * 群组音视频数据流追踪测试
 * 追踪从CallingService到UI显示的完整数据流路径
 */

import java.util.ArrayList;
import java.util.List;

public class GroupCallDataFlowTest {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String GROUP_ID = "2496693853";
    
    public static void main(String[] args) {
        System.out.println("🔍 群组音视频数据流完整追踪测试");
        System.out.println("目标：找出为什么有1个成员但九宫格显示0个的真正原因");
        System.out.println("========================================");
        
        GroupCallDataFlowTest test = new GroupCallDataFlowTest();
        test.runCompleteDataFlowTest();
    }
    
    public void runCompleteDataFlowTest() {
        System.out.println("\n🚀 开始完整数据流追踪...");
        
        // 1. 模拟CallingService调用路径
        System.out.println("\n📱 === 第1步：CallingService.call() 执行 ===");
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        System.out.println("输入memberIds: [" + INVITED_USER_1 + "]");
        
        // 2. 模拟CallingVM初始化
        System.out.println("\n🧠 === 第2步：CallingVM.initializeGroupMembers() 执行 ===");
        MockCallingVMDataFlow callingVM = new MockCallingVMDataFlow();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        
        List<GroupCallMemberDataFlow> groupMembers = callingVM.getGroupMembers();
        System.out.println("CallingVM.getGroupMembers() 返回数量: " + groupMembers.size());
        for (int i = 0; i < groupMembers.size(); i++) {
            GroupCallMemberDataFlow member = groupMembers.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + " - " + member.getState());
        }
        
        // 3. 模拟GroupCallDialog.refreshMemberList()
        System.out.println("\n🖼️ === 第3步：GroupCallDialog.refreshMemberList() 执行 ===");
        MockGroupCallDialog dialog = new MockGroupCallDialog();
        dialog.setCallingVM(callingVM);
        dialog.refreshMemberList();
        
        // 4. 模拟GroupMemberAdapter.updateMembers()
        System.out.println("\n📋 === 第4步：GroupMemberAdapter.updateMembers() 执行 ===");
        MockGroupMemberAdapter adapter = new MockGroupMemberAdapter();
        List<GroupCallMemberDataFlow> membersFromDialog = dialog.getLastMembersPassedToAdapter();
        System.out.println("传递给Adapter的成员数量: " + (membersFromDialog != null ? membersFromDialog.size() : "null"));
        
        if (membersFromDialog != null) {
            for (int i = 0; i < membersFromDialog.size(); i++) {
                GroupCallMemberDataFlow member = membersFromDialog.get(i);
                System.out.println("  传递成员" + (i+1) + ": " + member.getUserId() + " - " + member.getState());
            }
        }
        
        adapter.updateMembers(membersFromDialog != null ? membersFromDialog : new ArrayList<>());
        
        // 5. 模拟Adapter.getItemCount()
        System.out.println("\n🔢 === 第5步：GroupMemberAdapter.getItemCount() 执行 ===");
        int itemCount = adapter.getItemCount();
        System.out.println("Adapter.getItemCount() 返回: " + itemCount);
        
        // 6. 分析问题
        System.out.println("\n🎯 === 数据流分析结果 ===");
        System.out.println("CallingVM成员数: " + groupMembers.size());
        System.out.println("Dialog传递数: " + (membersFromDialog != null ? membersFromDialog.size() : "null"));
        System.out.println("Adapter显示数: " + itemCount);
        
        if (groupMembers.size() > 0 && itemCount == 0) {
            System.err.println("\n❌ 发现问题！");
            System.err.println("数据在某个环节丢失了！");
            
            if (membersFromDialog == null || membersFromDialog.isEmpty()) {
                System.err.println("🎯 问题定位：Dialog -> Adapter 环节数据丢失！");
                System.err.println("可能原因：");
                System.err.println("1. GroupCallDialog.refreshMemberList() 方法有bug");
                System.err.println("2. callingVM.getGroupMembers() 在Dialog中返回空");
                System.err.println("3. 成员状态过滤逻辑有问题");
            } else {
                System.err.println("🎯 问题定位：Adapter内部数据处理有问题！");
                System.err.println("可能原因：");
                System.err.println("1. updateMembers()方法实现有bug");
                System.err.println("2. getItemCount()方法实现有bug");
                System.err.println("3. 成员列表被意外清空");
            }
        } else if (groupMembers.size() == itemCount && itemCount > 0) {
            System.out.println("✅ 数据流正常！修复成功！");
        } else {
            System.err.println("❓ 意外情况：CallingVM=" + groupMembers.size() + ", Adapter=" + itemCount);
        }
    }
}

/**
 * 模拟CallingVM（数据流版本）
 */
class MockCallingVMDataFlow {
    private String currentUserId;
    private List<GroupCallMemberDataFlow> groupMembers = new ArrayList<>();
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    public List<GroupCallMemberDataFlow> getGroupMembers() {
        System.out.println("    🔍 CallingVM.getGroupMembers() 被调用，返回 " + groupMembers.size() + " 个成员");
        return new ArrayList<>(groupMembers); // 返回副本
    }
    
    public void initializeGroupMembers(List<String> memberIds, String groupId) {
        System.out.println("    🔧 执行 initializeGroupMembers，输入: " + memberIds.size() + " 个memberIds");
        groupMembers.clear();
        
        // 🎯 使用修复后的逻辑：先添加发起方自己
        if (currentUserId != null && !currentUserId.isEmpty()) {
            GroupCallMemberDataFlow selfMember = new GroupCallMemberDataFlow(currentUserId);
            selfMember.setState(CallMemberStateDataFlow.CONNECTED);
            groupMembers.add(selfMember);
            System.out.println("    ✅ 添加发起方: " + currentUserId + " (CONNECTED)");
        }
        
        // 然后添加被邀请的成员
        for (String memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                GroupCallMemberDataFlow member = new GroupCallMemberDataFlow(memberId);
                member.setState(CallMemberStateDataFlow.INVITING);
                groupMembers.add(member);
                System.out.println("    ✅ 添加被邀请人: " + memberId + " (INVITING)");
            }
        }
        
        System.out.println("    📊 initializeGroupMembers 完成，总成员数: " + groupMembers.size());
    }
}

/**
 * 模拟GroupCallDialog（数据流版本）
 */
class MockGroupCallDialog {
    private MockCallingVMDataFlow callingVM;
    private List<GroupCallMemberDataFlow> lastMembersPassedToAdapter;
    
    public void setCallingVM(MockCallingVMDataFlow callingVM) {
        this.callingVM = callingVM;
    }
    
    public void refreshMemberList() {
        System.out.println("    🔄 GroupCallDialog.refreshMemberList() 开始执行");
        
        if (callingVM == null) {
            System.err.println("    ❌ callingVM 为 null！");
            lastMembersPassedToAdapter = null;
            return;
        }
        
        // 🔧 关键：获取真实的成员数据
        List<GroupCallMemberDataFlow> groupMembers = callingVM.getGroupMembers();
        System.out.println("    📊 从CallingVM获取到 " + groupMembers.size() + " 个成员");
        
        // 🔧 关键：检查数据是否为空
        if (groupMembers != null && !groupMembers.isEmpty()) {
            System.out.println("    ✅ 成员列表不为空，准备传递给Adapter");
            lastMembersPassedToAdapter = new ArrayList<>(groupMembers);
        } else {
            System.err.println("    ❌ 成员列表为空！传递空列表给Adapter");
            lastMembersPassedToAdapter = new ArrayList<>();
        }
        
        System.out.println("    📤 准备传递 " + lastMembersPassedToAdapter.size() + " 个成员给Adapter");
    }
    
    public List<GroupCallMemberDataFlow> getLastMembersPassedToAdapter() {
        return lastMembersPassedToAdapter;
    }
}

/**
 * 模拟GroupMemberAdapter（数据流版本）
 */
class MockGroupMemberAdapter {
    private List<GroupCallMemberDataFlow> memberList = new ArrayList<>();
    
    public void updateMembers(List<GroupCallMemberDataFlow> members) {
        System.out.println("    📋 GroupMemberAdapter.updateMembers() 接收到 " + (members != null ? members.size() : "null") + " 个成员");
        
        if (members == null) {
            System.err.println("    ❌ 接收到的members为null！");
            memberList.clear();
            return;
        }
        
        memberList.clear();
        memberList.addAll(members);
        System.out.println("    ✅ Adapter内部memberList更新完成，数量: " + memberList.size());
        
        // 模拟notifyDataSetChanged()
        System.out.println("    🔄 notifyDataSetChanged() 调用");
    }
    
    public int getItemCount() {
        int count = memberList.size();
        System.out.println("    🔢 getItemCount() 返回: " + count);
        return count;
    }
}

class GroupCallMemberDataFlow {
    private String userId;
    private CallMemberStateDataFlow state;
    
    public GroupCallMemberDataFlow(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public CallMemberStateDataFlow getState() {
        return state;
    }
    
    public void setState(CallMemberStateDataFlow state) {
        this.state = state;
    }
}

enum CallMemberStateDataFlow {
    CONNECTED("已连接"),
    INVITING("邀请中");
    
    private String description;
    
    CallMemberStateDataFlow(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}