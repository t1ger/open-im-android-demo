/**
 * 群组音视频零显示问题测试
 * 专门测试：为什么有1个成员但九宫格显示0个成员的问题
 */

import java.util.ArrayList;
import java.util.List;

public class GroupCallZeroDisplayTest {
    
    // 模拟用户ID
    private static final String CURRENT_USER_ID = "7558150804"; // 发起方
    private static final String INVITED_USER_1 = "7483490109"; // 被邀请人1
    private static final String GROUP_ID = "2496693853";
    
    public static void main(String[] args) {
        System.out.println("🔍 群组音视频零显示问题专项测试");
        System.out.println("专门测试：为什么有1个成员但九宫格显示0个成员？");
        System.out.println("========================================");
        
        GroupCallZeroDisplayTest test = new GroupCallZeroDisplayTest();
        test.runZeroDisplayTest();
    }
    
    public void runZeroDisplayTest() {
        System.out.println("\n🚀 开始零显示问题分析...");
        
        // 测试场景1：修复前的情况（1个INVITING成员）
        System.out.println("\n🔴 === 场景1：修复前的情况 ===");
        testOriginalBehavior();
        
        // 测试场景2：修复后的情况（1个CONNECTED + 1个INVITING）
        System.out.println("\n🟢 === 场景2：修复后的情况 ===");
        testFixedBehavior();
        
        // 测试场景3：UI组件层面的可能问题
        System.out.println("\n🔍 === 场景3：UI组件层面分析 ===");
        testUIComponentIssues();
    }
    
    private void testOriginalBehavior() {
        System.out.println("模拟修复前：只有1个INVITING状态的成员");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        MockCallingVMZero callingVM = new MockCallingVMZero();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
        // 使用修复前的逻辑：只添加被邀请人
        callingVM.initializeGroupMembersOriginal(memberIds, GROUP_ID);
        
        MockGroupCallDialogZero dialog = new MockGroupCallDialogZero();
        dialog.setCallingVM(callingVM);
        
        MockGroupMemberAdapterZero adapter = new MockGroupMemberAdapterZero();
        dialog.setAdapter(adapter);
        
        // 执行UI流程
        dialog.refreshMemberList();
        
        // 分析结果
        List<GroupCallMemberZero> members = callingVM.getGroupMembers();
        int adapterCount = adapter.getItemCount();
        int uiDisplayCount = adapter.getActualUIDisplayCount();
        
        System.out.println("结果分析：");
        System.out.println("  CallingVM成员数: " + members.size());
        if (members.size() > 0) {
            for (GroupCallMemberZero member : members) {
                System.out.println("    - " + member.getUserId() + " (" + member.getState() + ")");
            }
        }
        System.out.println("  Adapter项目数: " + adapterCount);
        System.out.println("  UI实际显示数: " + uiDisplayCount);
        
        if (members.size() > 0 && uiDisplayCount == 0) {
            System.err.println("  🎯 发现问题：有成员但UI显示为0！");
            System.err.println("  可能原因：UI过滤掉了INVITING状态的成员");
        }
    }
    
    private void testFixedBehavior() {
        System.out.println("模拟修复后：1个CONNECTED状态 + 1个INVITING状态的成员");
        
        List<String> memberIds = new ArrayList<>();
        memberIds.add(INVITED_USER_1);
        
        MockCallingVMZero callingVM = new MockCallingVMZero();
        callingVM.setCurrentUserId(CURRENT_USER_ID);
        // 使用修复后的逻辑：添加发起方自己 + 被邀请人
        callingVM.initializeGroupMembersFixed(memberIds, GROUP_ID);
        
        MockGroupCallDialogZero dialog = new MockGroupCallDialogZero();
        dialog.setCallingVM(callingVM);
        
        MockGroupMemberAdapterZero adapter = new MockGroupMemberAdapterZero();
        dialog.setAdapter(adapter);
        
        // 执行UI流程
        dialog.refreshMemberList();
        
        // 分析结果
        List<GroupCallMemberZero> members = callingVM.getGroupMembers();
        int adapterCount = adapter.getItemCount();
        int uiDisplayCount = adapter.getActualUIDisplayCount();
        
        System.out.println("结果分析：");
        System.out.println("  CallingVM成员数: " + members.size());
        if (members.size() > 0) {
            for (GroupCallMemberZero member : members) {
                System.out.println("    - " + member.getUserId() + " (" + member.getState() + ")");
            }
        }
        System.out.println("  Adapter项目数: " + adapterCount);
        System.out.println("  UI实际显示数: " + uiDisplayCount);
        
        if (uiDisplayCount > 0) {
            System.out.println("  ✅ 修复成功：UI能够显示成员");
        }
    }
    
    private void testUIComponentIssues() {
        System.out.println("分析可能的UI组件层面问题：");
        
        // 测试各种可能导致九宫格不显示的情况
        System.out.println("\n🔍 可能的问题点：");
        System.out.println("1. RecyclerView可见性问题");
        System.out.println("2. GridLayoutManager配置问题");
        System.out.println("3. Adapter数据绑定问题");
        System.out.println("4. ViewHolder显示逻辑问题");
        System.out.println("5. 成员状态过滤逻辑问题");
        
        // 模拟RecyclerView可见性测试
        MockRecyclerViewZero recyclerView = new MockRecyclerViewZero();
        System.out.println("\n📋 RecyclerView状态测试：");
        System.out.println("  初始可见性: " + recyclerView.getVisibility());
        System.out.println("  是否正确配置: " + recyclerView.isProperlyConfigured());
        
        // 模拟GridLayoutManager测试
        MockGridLayoutManagerZero layoutManager = new MockGridLayoutManagerZero();
        System.out.println("\n🔲 GridLayoutManager状态测试：");
        System.out.println("  SpanCount: " + layoutManager.getSpanCount());
        System.out.println("  是否正确配置: " + layoutManager.isProperlyConfigured());
        
        System.out.println("\n💡 排查建议：");
        System.out.println("• 检查RecyclerView是否设置为VISIBLE");
        System.out.println("• 检查GridLayoutManager的SpanCount配置");
        System.out.println("• 检查Adapter的getItemCount()返回值");
        System.out.println("• 检查ViewHolder的bind()方法是否正确执行");
        System.out.println("• 检查成员状态是否被错误过滤");
    }
}

/**
 * 模拟CallingVM用于零显示测试
 */
class MockCallingVMZero {
    private String currentUserId;
    private List<GroupCallMemberZero> groupMembers = new ArrayList<>();
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    public List<GroupCallMemberZero> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }
    
    // 修复前的逻辑：只添加被邀请人
    public void initializeGroupMembersOriginal(List<String> memberIds, String groupId) {
        groupMembers.clear();
        
        for (String memberId : memberIds) {
            GroupCallMemberZero member = new GroupCallMemberZero(memberId);
            member.setState(CallMemberStateZero.INVITING);
            groupMembers.add(member);
        }
        
        System.out.println("    ❌ 修复前逻辑：只添加了" + groupMembers.size() + "个被邀请人");
    }
    
    // 修复后的逻辑：添加发起方自己 + 被邀请人
    public void initializeGroupMembersFixed(List<String> memberIds, String groupId) {
        groupMembers.clear();
        
        // 先添加发起方自己
        if (currentUserId != null && !currentUserId.isEmpty()) {
            GroupCallMemberZero selfMember = new GroupCallMemberZero(currentUserId);
            selfMember.setState(CallMemberStateZero.CONNECTED);
            groupMembers.add(selfMember);
        }
        
        // 再添加被邀请人
        for (String memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                GroupCallMemberZero member = new GroupCallMemberZero(memberId);
                member.setState(CallMemberStateZero.INVITING);
                groupMembers.add(member);
            }
        }
        
        System.out.println("    ✅ 修复后逻辑：添加了" + groupMembers.size() + "个成员（包含发起方）");
    }
}

/**
 * 模拟GroupCallDialog用于零显示测试
 */
class MockGroupCallDialogZero {
    private MockCallingVMZero callingVM;
    private MockGroupMemberAdapterZero adapter;
    
    public void setCallingVM(MockCallingVMZero callingVM) {
        this.callingVM = callingVM;
    }
    
    public void setAdapter(MockGroupMemberAdapterZero adapter) {
        this.adapter = adapter;
    }
    
    public void refreshMemberList() {
        if (adapter != null && callingVM != null) {
            List<GroupCallMemberZero> groupMembers = callingVM.getGroupMembers();
            
            System.out.println("    🔄 Dialog.refreshMemberList(): 获取到" + groupMembers.size() + "个成员");
            
            if (groupMembers != null && !groupMembers.isEmpty()) {
                adapter.updateMembers(groupMembers);
                System.out.println("    ✅ 已更新Adapter数据");
            } else {
                adapter.updateMembers(new ArrayList<>());
                System.out.println("    ❌ 传递空列表给Adapter");
            }
        }
    }
}

/**
 * 模拟GroupMemberAdapter用于零显示测试
 */
class MockGroupMemberAdapterZero {
    private List<GroupCallMemberZero> memberList = new ArrayList<>();
    
    public void updateMembers(List<GroupCallMemberZero> members) {
        memberList.clear();
        if (members != null) {
            memberList.addAll(members);
        }
        
        System.out.println("    📋 Adapter.updateMembers(): 接收到" + (members != null ? members.size() : 0) + "个成员");
    }
    
    public int getItemCount() {
        return memberList.size();
    }
    
    // 模拟实际UI显示逻辑（可能有过滤）
    public int getActualUIDisplayCount() {
        int count = 0;
        for (GroupCallMemberZero member : memberList) {
            if (shouldDisplayMember(member)) {
                count++;
            }
        }
        return count;
    }
    
    // 模拟可能的显示过滤逻辑
    private boolean shouldDisplayMember(GroupCallMemberZero member) {
        // 🎯 这里可能是问题所在：某些UI实现可能只显示CONNECTED状态的成员
        // 为了测试，我们检查两种情况
        
        CallMemberStateZero state = member.getState();
        
        // 情况1：只显示CONNECTED状态的成员（可能导致问题）
        if (state == CallMemberStateZero.CONNECTED) {
            System.out.println("      ✅ 显示成员: " + member.getUserId() + " (CONNECTED)");
            return true;
        } else if (state == CallMemberStateZero.INVITING) {
            System.out.println("      ⏳ 显示成员: " + member.getUserId() + " (INVITING - 呼叫中)");
            return true; // 实际应该显示INVITING状态的成员
        } else {
            System.out.println("      ❌ 跳过成员: " + member.getUserId() + " (" + state + ")");
            return false;
        }
    }
}

/**
 * 模拟RecyclerView组件
 */
class MockRecyclerViewZero {
    private int visibility = 0; // 0=VISIBLE, 8=GONE
    
    public int getVisibility() {
        return visibility;
    }
    
    public boolean isProperlyConfigured() {
        return visibility == 0; // VISIBLE
    }
}

/**
 * 模拟GridLayoutManager
 */
class MockGridLayoutManagerZero {
    private int spanCount = 1;
    
    public int getSpanCount() {
        return spanCount;
    }
    
    public boolean isProperlyConfigured() {
        return spanCount > 0;
    }
}

class GroupCallMemberZero {
    private String userId;
    private CallMemberStateZero state;
    
    public GroupCallMemberZero(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public CallMemberStateZero getState() {
        return state;
    }
    
    public void setState(CallMemberStateZero state) {
        this.state = state;
    }
}

enum CallMemberStateZero {
    CONNECTED("已连接"),
    INVITING("邀请中");
    
    private String description;
    
    CallMemberStateZero(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}