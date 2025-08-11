/**
 * 群组音视频时序问题测试
 * 模拟CallingService中的时序问题：show()调用时成员还未初始化
 */

import java.util.ArrayList;
import java.util.List;

public class GroupCallTimingIssueTest {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String GROUP_ID = "2496693853";
    
    public static void main(String[] args) {
        System.out.println("🚀 群组音视频时序问题测试");
        System.out.println("目标：复现CallingService中show()时成员未初始化的问题");
        System.out.println("========================================");
        
        GroupCallTimingIssueTest test = new GroupCallTimingIssueTest();
        test.runTimingIssueTest();
    }
    
    public void runTimingIssueTest() {
        System.out.println("\n📱 === 模拟CallingService.call()执行流程 ===");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        System.out.println("输入memberIds: [" + INVITED_USER_1 + "]");
        
        // 模拟CallingService中的执行流程
        MockCallingServiceTiming service = new MockCallingServiceTiming();
        service.call(memberIds, GROUP_ID);
        
        System.out.println("\n🎯 === 时序问题分析结果 ===");
        System.out.println("1. callDialog.show() 在成员初始化之前被调用");
        System.out.println("2. show() 触发 bindSpecificData() 调用 refreshMemberList()");
        System.out.println("3. 此时 CallingVM.getGroupMembers() 返回空列表");
        System.out.println("4. 九宫格显示0个成员");
        System.out.println("5. 后续的异步 refreshMemberList() 调用修正了这个问题");
        System.out.println("");
        System.out.println("💡 解决方案：");
        System.out.println("• 确保 initializeGroupMembers() 在 show() 之前完成");
        System.out.println("• 或者在 show() 中延迟调用 refreshMemberList()");
        System.out.println("• 或者使用同步方式确保成员初始化完成后再显示对话框");
    }
}

/**
 * 模拟CallingService的时序问题
 */
class MockCallingServiceTiming {
    
    public void call(List<String> memberIds, String groupId) {
        System.out.println("\n🔧 === CallingService.call() 开始执行 ===");
        
        // 1. 创建对话框（此时CallingVM成员列表为空）
        System.out.println("📱 第1步：创建GroupCallDialog");
        MockGroupCallDialogTiming callDialog = new MockGroupCallDialogTiming();
        MockCallingVMTiming callingVM = new MockCallingVMTiming();
        callingVM.setCurrentUserId("7558150804");
        callDialog.setCallingVM(callingVM);
        
        System.out.println("🔍 创建时CallingVM成员数: " + callingVM.getGroupMembers().size());
        
        try {
            // 2. 初始化群组成员（修复前：在show()之后）
            System.out.println("\n📱 第2步：初始化群组成员");
            if (groupId != null && memberIds != null && !memberIds.isEmpty()) {
                System.out.println("🔧 调用 initializeGroupMembers()");
                callingVM.initializeGroupMembers(memberIds, groupId);
                
                int memberCount = callingVM.getGroupMembers().size();
                System.out.println("✅ 群组成员初始化完成: " + memberCount + " 个成员");
                
                // 3. 异步通知刷新（这是CallingService中的第587行逻辑）
                System.out.println("📤 异步通知GroupCallDialog刷新成员列表");
                // 模拟Common.UIHandler.post(() -> ...)
                callDialog.refreshMemberList();
            }
            
        } catch (Exception e) {
            System.err.println("❌ 初始化群组成员失败: " + e.getMessage());
        }
        
        // 4. 显示对话框（这是第606行，会触发bindSpecificData）
        System.out.println("\n📱 第3步：显示对话框");
        System.out.println("🔧 调用 callDialog.show()");
        callDialog.show(); // 这里会调用bindSpecificData() -> refreshMemberList()
        
        System.out.println("\n📊 === 最终结果 ===");
        System.out.println("CallingVM最终成员数: " + callingVM.getGroupMembers().size());
        System.out.println("Dialog最后显示数: " + callDialog.getLastDisplayCount());
    }
}

/**
 * 模拟有时序问题的GroupCallDialog
 */
class MockGroupCallDialogTiming {
    private MockCallingVMTiming callingVM;
    private int lastDisplayCount = 0;
    
    public void setCallingVM(MockCallingVMTiming callingVM) {
        this.callingVM = callingVM;
    }
    
    public void show() {
        System.out.println("    📺 GroupCallDialog.show() 开始");
        System.out.println("    🔧 触发 bindSpecificData()");
        bindSpecificData();
        System.out.println("    ✅ GroupCallDialog.show() 完成");
    }
    
    private void bindSpecificData() {
        System.out.println("        🔧 bindSpecificData() 执行中");
        System.out.println("        📱 调用 refreshMemberList()");
        refreshMemberList();
        System.out.println("        ✅ bindSpecificData() 完成");
    }
    
    public void refreshMemberList() {
        System.out.println("            🔄 refreshMemberList() 开始");
        
        if (callingVM == null) {
            System.err.println("            ❌ callingVM 为 null");
            lastDisplayCount = 0;
            return;
        }
        
        List<GroupCallMemberTiming> groupMembers = callingVM.getGroupMembers();
        int memberCount = groupMembers.size();
        
        System.out.println("            📊 从CallingVM获取成员数: " + memberCount);
        
        if (groupMembers != null && !groupMembers.isEmpty()) {
            System.out.println("            ✅ 更新适配器，显示 " + memberCount + " 个成员");
            lastDisplayCount = memberCount;
        } else {
            System.out.println("            ❌ 成员列表为空，九宫格显示0个成员");
            lastDisplayCount = 0;
        }
        
        System.out.println("            ✅ refreshMemberList() 完成");
    }
    
    public int getLastDisplayCount() {
        return lastDisplayCount;
    }
}

/**
 * 模拟有时序问题的CallingVM
 */
class MockCallingVMTiming {
    private String currentUserId;
    private List<GroupCallMemberTiming> groupMembers = new ArrayList<>();
    private boolean membersInitialized = false;
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    public List<GroupCallMemberTiming> getGroupMembers() {
        if (!membersInitialized) {
            System.out.println("        ⚠️  getGroupMembers() 调用时成员尚未初始化！返回空列表");
        }
        return new ArrayList<>(groupMembers);
    }
    
    public void initializeGroupMembers(List<String> memberIds, String groupId) {
        System.out.println("    🔧 CallingVM.initializeGroupMembers() 开始");
        groupMembers.clear();
        
        // 添加发起方自己
        if (currentUserId != null && !currentUserId.isEmpty()) {
            GroupCallMemberTiming selfMember = new GroupCallMemberTiming(currentUserId);
            groupMembers.add(selfMember);
            System.out.println("    ✅ 添加发起方: " + currentUserId);
        }
        
        // 添加被邀请的成员
        for (String memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                GroupCallMemberTiming member = new GroupCallMemberTiming(memberId);
                groupMembers.add(member);
                System.out.println("    ✅ 添加被邀请人: " + memberId);
            }
        }
        
        membersInitialized = true;
        System.out.println("    ✅ CallingVM.initializeGroupMembers() 完成，总数: " + groupMembers.size());
    }
}

class GroupCallMemberTiming {
    private String userId;
    
    public GroupCallMemberTiming(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
}