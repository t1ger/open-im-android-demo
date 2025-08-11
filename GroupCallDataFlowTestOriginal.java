/**
 * 群组音视频数据流追踪测试（修复前版本）
 * 模拟修复前的情况：1个成员但九宫格显示0个
 */

import java.util.ArrayList;
import java.util.List;

public class GroupCallDataFlowTestOriginal {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String GROUP_ID = "2496693853";
    
    public static void main(String[] args) {
        System.out.println("🔍 群组音视频数据流追踪测试（修复前版本）");
        System.out.println("目标：复现为什么有1个成员但九宫格显示0个的问题");
        System.out.println("模拟修复前的initializeGroupMembers()逻辑");
        System.out.println("========================================");
        
        GroupCallDataFlowTestOriginal test = new GroupCallDataFlowTestOriginal();
        test.runOriginalDataFlowTest();
    }
    
    public void runOriginalDataFlowTest() {
        System.out.println("\n🚀 开始修复前数据流追踪...");
        
        // 1. 模拟CallingService调用路径（修复前）
        System.out.println("\n📱 === 第1步：CallingService.call() 执行（修复前）===");
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        System.out.println("输入memberIds: [" + INVITED_USER_1 + "]");
        
        // 2. 模拟CallingVM初始化（修复前的错误逻辑）
        System.out.println("\n🧠 === 第2步：CallingVM.initializeGroupMembers()（修复前）===");
        MockCallingVMOriginal callingVM = new MockCallingVMOriginal();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
        callingVM.initializeGroupMembers(memberIds, GROUP_ID);
        
        List<GroupCallMemberOriginal> groupMembers = callingVM.getGroupMembers();
        System.out.println("CallingVM.getGroupMembers() 返回数量: " + groupMembers.size());
        for (int i = 0; i < groupMembers.size(); i++) {
            GroupCallMemberOriginal member = groupMembers.get(i);
            System.out.println("  成员" + (i+1) + ": " + member.getUserId() + " - " + member.getState());
        }
        
        // 3. 模拟GroupCallDialog.refreshMemberList()
        System.out.println("\n🖼️ === 第3步：GroupCallDialog.refreshMemberList() 执行 ===");
        MockGroupCallDialogOriginal dialog = new MockGroupCallDialogOriginal();
        dialog.setCallingVM(callingVM);
        dialog.refreshMemberList();
        
        // 4. 模拟GroupMemberAdapter.updateMembers()
        System.out.println("\n📋 === 第4步：GroupMemberAdapter.updateMembers() 执行 ===");
        MockGroupMemberAdapterOriginal adapter = new MockGroupMemberAdapterOriginal();
        List<GroupCallMemberOriginal> membersFromDialog = dialog.getLastMembersPassedToAdapter();
        System.out.println("传递给Adapter的成员数量: " + (membersFromDialog != null ? membersFromDialog.size() : "null"));
        
        if (membersFromDialog != null) {
            for (int i = 0; i < membersFromDialog.size(); i++) {
                GroupCallMemberOriginal member = membersFromDialog.get(i);
                System.out.println("  传递成员" + (i+1) + ": " + member.getUserId() + " - " + member.getState());
            }
        }
        
        adapter.updateMembers(membersFromDialog != null ? membersFromDialog : new ArrayList<>());
        
        // 5. 🎯 关键测试：模拟可能的UI过滤逻辑
        System.out.println("\n🔍 === 第5步：检查可能的UI过滤逻辑 ===");
        int actualDisplayCount = adapter.getActualDisplayCount();
        int itemCount = adapter.getItemCount();
        
        System.out.println("Adapter.getItemCount(): " + itemCount);
        System.out.println("实际UI显示数量: " + actualDisplayCount);
        
        // 6. 分析问题
        System.out.println("\n🎯 === 修复前问题分析结果 ===");
        System.out.println("CallingVM成员数: " + groupMembers.size());
        System.out.println("Dialog传递数: " + (membersFromDialog != null ? membersFromDialog.size() : "null"));
        System.out.println("Adapter内部数: " + itemCount);
        System.out.println("实际显示数: " + actualDisplayCount);
        
        System.out.println("\n🔍 问题分析：");
        if (groupMembers.size() == 1 && actualDisplayCount == 0) {
            System.err.println("✅ 成功复现问题！");
            System.err.println("🎯 问题根因：只有被邀请人（INVITING状态），但UI过滤掉了INVITING状态的成员！");
            System.err.println("\n💡 可能的原因：");
            System.err.println("1. UI只显示CONNECTED状态的成员");
            System.err.println("2. INVITING状态被当作'还未真正加入'而被过滤");
            System.err.println("3. 缺少发起方自己（CONNECTED状态），导致没有可显示的成员");
        } else if (groupMembers.size() == itemCount && actualDisplayCount == itemCount) {
            System.out.println("数据流正常，无问题");
        } else {
            System.err.println("发现其他问题: CallingVM=" + groupMembers.size() + 
                ", Adapter=" + itemCount + ", Display=" + actualDisplayCount);
        }
        
        System.out.println("\n🎯 === 总结 ===");
        System.err.println("修复前问题：");
        System.err.println("• CallingVM只有1个成员（被邀请人，INVITING状态）");
        System.err.println("• 没有发起方自己（CONNECTED状态）");
        System.err.println("• UI可能只显示CONNECTED状态的成员");
        System.err.println("• 结果：0个成员显示在九宫格");
        System.err.println("");
        System.out.println("修复后效果：");
        System.out.println("• CallingVM有2个成员（发起方CONNECTED + 被邀请人INVITING）");
        System.out.println("• 至少有1个CONNECTED状态的成员（发起方自己）");
        System.out.println("• UI能显示发起方，九宫格不为空");
    }
}

/**
 * 模拟修复前的CallingVM（错误逻辑）
 */
class MockCallingVMOriginal {
    private String currentUserId;
    private List<GroupCallMemberOriginal> groupMembers = new ArrayList<>();
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    public List<GroupCallMemberOriginal> getGroupMembers() {
        System.out.println("    🔍 CallingVM.getGroupMembers() 被调用，返回 " + groupMembers.size() + " 个成员");
        return new ArrayList<>(groupMembers);
    }
    
    public void initializeGroupMembers(List<String> memberIds, String groupId) {
        System.out.println("    🔧 执行 initializeGroupMembers（修复前错误逻辑），输入: " + memberIds.size() + " 个memberIds");
        groupMembers.clear();
        
        // ❌ 修复前的错误逻辑：只添加被邀请人，不添加发起方自己
        for (String memberId : memberIds) {
            GroupCallMemberOriginal member = new GroupCallMemberOriginal(memberId);
            member.setState(CallMemberStateOriginal.INVITING); // 被邀请人状态
            groupMembers.add(member);
            System.out.println("    ❌ 只添加被邀请人: " + memberId + " (INVITING)");
        }
        
        // ❌ 缺少这部分：没有添加发起方自己！
        System.out.println("    ❌ 缺失：没有添加发起方自己 " + currentUserId + " (CONNECTED)");
        
        System.out.println("    📊 initializeGroupMembers 完成（错误版本），总成员数: " + groupMembers.size());
    }
}

/**
 * 模拟GroupCallDialog（可能有UI过滤逻辑）
 */
class MockGroupCallDialogOriginal {
    private MockCallingVMOriginal callingVM;
    private List<GroupCallMemberOriginal> lastMembersPassedToAdapter;
    
    public void setCallingVM(MockCallingVMOriginal callingVM) {
        this.callingVM = callingVM;
    }
    
    public void refreshMemberList() {
        System.out.println("    🔄 GroupCallDialog.refreshMemberList() 开始执行");
        
        List<GroupCallMemberOriginal> groupMembers = callingVM.getGroupMembers();
        System.out.println("    📊 从CallingVM获取到 " + groupMembers.size() + " 个成员");
        
        // 🔧 模拟可能的过滤逻辑：只传递有效状态的成员
        List<GroupCallMemberOriginal> validMembers = new ArrayList<>();
        for (GroupCallMemberOriginal member : groupMembers) {
            if (member.getState() == CallMemberStateOriginal.CONNECTED) {
                validMembers.add(member);
                System.out.println("    ✅ 有效成员（CONNECTED）: " + member.getUserId());
            } else {
                System.out.println("    ⏳ 跳过邀请中成员（INVITING）: " + member.getUserId());
            }
        }
        
        System.out.println("    📊 过滤后有效成员数: " + validMembers.size());
        lastMembersPassedToAdapter = validMembers;
        System.out.println("    📤 准备传递 " + lastMembersPassedToAdapter.size() + " 个有效成员给Adapter");
    }
    
    public List<GroupCallMemberOriginal> getLastMembersPassedToAdapter() {
        return lastMembersPassedToAdapter;
    }
}

/**
 * 模拟GroupMemberAdapter（原始版本）
 */
class MockGroupMemberAdapterOriginal {
    private List<GroupCallMemberOriginal> memberList = new ArrayList<>();
    
    public void updateMembers(List<GroupCallMemberOriginal> members) {
        System.out.println("    📋 GroupMemberAdapter.updateMembers() 接收到 " + (members != null ? members.size() : "null") + " 个成员");
        
        if (members == null) {
            memberList.clear();
            return;
        }
        
        memberList.clear();
        memberList.addAll(members);
        System.out.println("    ✅ Adapter内部memberList更新完成，数量: " + memberList.size());
    }
    
    public int getItemCount() {
        int count = memberList.size();
        System.out.println("    🔢 getItemCount() 返回: " + count);
        return count;
    }
    
    // 模拟实际UI显示逻辑
    public int getActualDisplayCount() {
        int displayCount = 0;
        for (GroupCallMemberOriginal member : memberList) {
            if (member.getState() == CallMemberStateOriginal.CONNECTED) {
                displayCount++;
            }
        }
        System.out.println("    🖼️ 实际UI显示（只显示CONNECTED）: " + displayCount);
        return displayCount;
    }
}

class GroupCallMemberOriginal {
    private String userId;
    private CallMemberStateOriginal state;
    
    public GroupCallMemberOriginal(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public CallMemberStateOriginal getState() {
        return state;
    }
    
    public void setState(CallMemberStateOriginal state) {
        this.state = state;
    }
}

enum CallMemberStateOriginal {
    CONNECTED("已连接"),
    INVITING("邀请中");
    
    private String description;
    
    CallMemberStateOriginal(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}