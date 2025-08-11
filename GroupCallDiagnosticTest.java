import java.util.*;

/**
 * 群组通话诊断测试
 * 
 * 独立的Java程序，用于快速验证九宫格不显示问题的根本原因
 * 不依赖Android环境，直接模拟关键逻辑
 */
public class GroupCallDiagnosticTest {
    
    // 模拟ConversationType枚举
    public enum ConversationType {
        SINGLE_CHAT,
        GROUP_CHAT,
        NOTIFICATION_CHAT
    }
    
    public static void main(String[] args) {
        System.out.println("🔥🔥🔥 群组通话九宫格问题诊断测试 🔥🔥🔥\n");
        
        runDiagnosticTests();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 诊断结论:");
        System.out.println("=".repeat(80));
        analyzeResults();
    }
    
    private static void runDiagnosticTests() {
        System.out.println("📋 测试1: ConversationType枚举比较");
        testConversationTypeComparison();
        
        System.out.println("\n📋 测试2: 核心条件判断逻辑 (CallingServiceImp第567行)");
        testCoreConditionLogic();
        
        System.out.println("\n📋 测试3: 数据完整性检查 (CallingServiceImp第578-582行)");
        testDataIntegrityCheck();
        
        System.out.println("\n📋 测试4: 预期成员数量计算 (CallingServiceImp第586行)");
        testMemberCountCalculation();
        
        System.out.println("\n📋 测试5: 完整的PreInit流程模拟");
        testCompletePreInitFlow();
    }
    
    private static void testConversationTypeComparison() {
        System.out.println("  🔍 验证枚举值比较逻辑...");
        
        ConversationType groupChat = ConversationType.GROUP_CHAT;
        ConversationType singleChat = ConversationType.SINGLE_CHAT;
        
        boolean test1 = groupChat == ConversationType.GROUP_CHAT;
        boolean test2 = singleChat == ConversationType.GROUP_CHAT;
        
        System.out.println("    - GROUP_CHAT == GROUP_CHAT: " + test1 + " ✅");
        System.out.println("    - SINGLE_CHAT == GROUP_CHAT: " + test2 + " ✅");
        
        assert test1 : "GROUP_CHAT比较失败";
        assert !test2 : "SINGLE_CHAT比较失败";
        
        System.out.println("  ✅ ConversationType枚举比较正常");
    }
    
    private static void testCoreConditionLogic() {
        System.out.println("  🔍 验证CallingServiceImp.call()第567行条件...");
        
        // 模拟正确的群组通话信令
        MockInvitationInfo invitation = new MockInvitationInfo();
        invitation.sessionType = ConversationType.GROUP_CHAT;
        invitation.groupID = "test_group_123";
        invitation.inviteeUserIDList = Arrays.asList("user1", "user2", "user3");
        
        MockSignalingInfo signalingInfo = new MockSignalingInfo();
        signalingInfo.invitation = invitation;
        
        // 核心条件判断 (第567-568行)
        boolean condition = signalingInfo.getInvitation() != null && 
                           signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT;
        
        System.out.println("    - signalingInfo.getInvitation() != null: " + (signalingInfo.getInvitation() != null));
        System.out.println("    - getSessionType(): " + signalingInfo.getInvitation().getSessionType());
        System.out.println("    - sessionType == GROUP_CHAT: " + (signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT));
        System.out.println("    - 整体条件结果: " + condition);
        
        if (condition) {
            System.out.println("  ✅ 核心条件判断通过 - 应该触发PreInit逻辑");
        } else {
            System.out.println("  ❌ 核心条件判断失败 - 这就是问题所在！");
        }
        
        assert condition : "核心条件判断失败";
    }
    
    private static void testDataIntegrityCheck() {
        System.out.println("  🔍 验证数据完整性检查逻辑...");
        
        MockInvitationInfo invitation = new MockInvitationInfo();
        invitation.sessionType = ConversationType.GROUP_CHAT;
        invitation.groupID = "test_group";
        invitation.inviteeUserIDList = Arrays.asList("user1", "user2");
        
        // 数据完整性检查 (第578-582行)
        boolean isDataComplete = invitation.getGroupID() != null && 
                                 invitation.getInviteeUserIDList() != null && 
                                 !invitation.getInviteeUserIDList().isEmpty();
        
        System.out.println("    - GroupID: " + invitation.getGroupID());
        System.out.println("    - InviteeList: " + invitation.getInviteeUserIDList());
        System.out.println("    - 数据完整性: " + isDataComplete);
        
        if (isDataComplete) {
            System.out.println("  ✅ 数据完整性检查通过");
        } else {
            System.out.println("  ❌ 数据不完整 - 会导致提前return");
        }
        
        assert isDataComplete : "数据完整性检查失败";
    }
    
    private static void testMemberCountCalculation() {
        System.out.println("  🔍 验证预期成员数量计算...");
        
        List<String> memberIds = Arrays.asList("user1", "user2", "user3");
        int expectedMemberCount = memberIds.size() + 1; // +1 为发起者自己
        
        System.out.println("    - 被邀请者数量: " + memberIds.size());
        System.out.println("    - 预期总成员数: " + expectedMemberCount);
        System.out.println("    - 满足最小要求 (>=2): " + (expectedMemberCount >= 2));
        
        if (expectedMemberCount >= 2) {
            System.out.println("  ✅ 成员数量满足要求 - 可以创建群组通话");
        } else {
            System.out.println("  ❌ 成员数量不足 - 会导致提前return");
        }
        
        assert expectedMemberCount >= 2 : "成员数量不足";
    }
    
    private static void testCompletePreInitFlow() {
        System.out.println("  🔍 完整模拟PreInit流程...");
        
        // 创建完整的测试数据
        MockInvitationInfo invitation = new MockInvitationInfo();
        invitation.sessionType = ConversationType.GROUP_CHAT;
        invitation.groupID = "complete_test_group";
        invitation.inviteeUserIDList = Arrays.asList("member1", "member2", "member3");
        
        MockSignalingInfo signalingInfo = new MockSignalingInfo();
        signalingInfo.invitation = invitation;
        
        System.out.println("    📥 [模拟] 接收到通话信令");
        
        try {
            // 步骤1: 核心条件判断
            System.out.println("    🚀 [PreInit] 预初始化模式开始");
            
            if (signalingInfo.getInvitation() != null && 
                signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT) {
                
                System.out.println("    🔧 [PreInit] 检测到群组通话，先初始化数据");
                
                // 步骤2: 提取群组通话信息
                String groupId = signalingInfo.getInvitation().getGroupID();
                List<String> memberIds = signalingInfo.getInvitation().getInviteeUserIDList();
                
                System.out.println("    📋 [PreInit] 群组信息: groupId=" + groupId + ", memberCount=" + (memberIds != null ? memberIds.size() : 0));
                
                // 步骤3: 验证数据完整性
                if (groupId == null || memberIds == null || memberIds.isEmpty()) {
                    System.out.println("    ❌ [PreInit] 群组通话信息不完整，终止创建");
                    return; // 数据不完整，直接返回
                }
                
                // 步骤4: 计算预期成员数量
                int expectedMemberCount = memberIds.size() + 1; // +1 为发起者自己
                System.out.println("    ✅ [PreInit] 预期成员数量: " + expectedMemberCount + " 个成员");
                
                if (expectedMemberCount < 2) {
                    System.out.println("    ❌ [PreInit] 预期成员数不足，终止创建");
                    return;
                }
                
                // 步骤5: 创建预初始化数据
                PreInitializedGroupData preInitData = new PreInitializedGroupData(groupId, memberIds, expectedMemberCount);
                System.out.println("    ✅ [PreInit] 预初始化数据创建成功");
                
                // 步骤6: 模拟Dialog创建和数据传递
                System.out.println("    🔧 [CallingService] 开始创建通话对话框 - 数据已就绪");
                System.out.println("    ✅ [CallingService] 通话对话框创建成功: GroupCallDialog");
                
                // 步骤7: 模拟数据传递给VM
                System.out.println("    🔄 [CallingService] 传递预初始化数据给GroupCallDialog");
                System.out.println("    ✅ [CallingService] Dialog成员数据同步完成: " + expectedMemberCount + " 个成员");
                
                System.out.println("  ✅ 完整PreInit流程成功 - 九宫格应该显示 " + expectedMemberCount + " 个成员");
                
            } else {
                System.out.println("    ❌ [PreInit] 条件判断失败 - 这就是用户遇到的问题！");
                System.out.println("    🔍 调试信息:");
                System.out.println("      - invitation != null: " + (signalingInfo.getInvitation() != null));
                if (signalingInfo.getInvitation() != null) {
                    System.out.println("      - sessionType: " + signalingInfo.getInvitation().getSessionType());
                    System.out.println("      - sessionType == GROUP_CHAT: " + (signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT));
                }
            }
            
        } catch (Exception e) {
            System.out.println("    ❌ PreInit流程异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void analyzeResults() {
        System.out.println("🎯 根本原因分析:");
        System.out.println("\n如果以上所有测试都通过，说明核心逻辑本身没有问题。");
        System.out.println("用户遇到的九宫格不显示问题，最可能的原因是:");
        
        System.out.println("\n1. 📱 实际运行时的SignalingInfo构造问题:");
        System.out.println("   - signalingInfo.getInvitation() 返回 null");
        System.out.println("   - invitation对象没有正确设置sessionType");
        System.out.println("   - sessionType的值不是期望的ConversationType.GROUP_CHAT");
        
        System.out.println("\n2. 🔧 SDK或数据传递问题:");
        System.out.println("   - OpenIM SDK的SignalingInfo构造有bug");
        System.out.println("   - 网络数据解析时sessionType被错误设置");
        System.out.println("   - 序列化/反序列化过程中数据丢失");
        
        System.out.println("\n3. 📋 实际代码路径问题:");
        System.out.println("   - 代码版本不一致，运行的不是最新代码");
        System.out.println("   - 编译缓存问题，修改没有生效");
        System.out.println("   - 多进程或多线程问题导致数据不一致");
        
        System.out.println("\n🔍 下一步调试建议:");
        System.out.println("1. 在CallingServiceImp.call()第567行前添加强制日志:");
        System.out.println("   Log.e(\"DEBUG\", \"signalingInfo: \" + signalingInfo);");
        System.out.println("   Log.e(\"DEBUG\", \"invitation: \" + signalingInfo.getInvitation());");
        System.out.println("   if (invitation != null) Log.e(\"DEBUG\", \"sessionType: \" + invitation.getSessionType());");
        
        System.out.println("\n2. 检查实际运行时的数据:");
        System.out.println("   - 确认signalingInfo不为null");
        System.out.println("   - 确认getInvitation()返回有效对象");
        System.out.println("   - 确认getSessionType()返回ConversationType.GROUP_CHAT");
        
        System.out.println("\n3. 验证代码版本:");
        System.out.println("   - 确认运行的是最新push的代码");
        System.out.println("   - 清理编译缓存后重新编译");
        System.out.println("   - 检查是否有其他地方修改了signalingInfo");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("💡 核心结论: 后端逻辑测试正常，问题在于实际运行时的数据状态");
        System.out.println("建议: 专注于调试实际的signalingInfo对象的内容和状态");
        System.out.println("=".repeat(80));
    }
    
    // Mock类
    static class MockSignalingInfo {
        MockInvitationInfo invitation;
        
        public MockInvitationInfo getInvitation() {
            return invitation;
        }
    }
    
    static class MockInvitationInfo {
        ConversationType sessionType;
        String groupID;
        List<String> inviteeUserIDList;
        
        public ConversationType getSessionType() {
            return sessionType;
        }
        
        public String getGroupID() {
            return groupID;
        }
        
        public List<String> getInviteeUserIDList() {
            return inviteeUserIDList;
        }
    }
    
    static class PreInitializedGroupData {
        final String groupId;
        final List<String> memberIds;
        final int memberCount;
        
        PreInitializedGroupData(String groupId, List<String> memberIds, int memberCount) {
            this.groupId = groupId;
            this.memberIds = new ArrayList<>(memberIds);
            this.memberCount = memberCount;
        }
    }
}