import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.models.*;
import io.openim.android.ouicalling.state.CallStateManager;
import io.openim.android.ouicore.im.IMUtil;
import java.util.Arrays;
import java.util.List;

/**
 * 群组通话问题调试测试
 * 用于验证SignalingInfo构建和isGroupCall判断是否正确
 */
public class GroupCallDebugTest {
    
    public static void debugGroupCall() {
        System.out.println("=== 群组通话问题调试 ===");
        
        // 模拟构建群组信令
        String groupId = "test_group_123";
        List<String> memberIds = Arrays.asList("user_1", "user_2", "user_3");
        boolean isVideo = true;
        
        // 使用IMUtil构建群组信令
        SignalingInfo groupSignalingInfo = IMUtil.buildGroupSignalingInfo(isVideo, groupId, memberIds);
        
        if (groupSignalingInfo == null) {
            System.out.println("❌ ERROR: buildGroupSignalingInfo 返回 null");
            return;
        }
        
        if (groupSignalingInfo.getInvitation() == null) {
            System.out.println("❌ ERROR: getInvitation() 返回 null");
            return;
        }
        
        // 检查关键信息
        SignalingInvitationInfo invitation = groupSignalingInfo.getInvitation();
        System.out.println("📋 构建的群组信令信息：");
        System.out.println("   SessionType: " + invitation.getSessionType());
        System.out.println("   GroupID: " + invitation.getGroupID());
        System.out.println("   InviteeList: " + invitation.getInviteeUserIDList());
        System.out.println("   MediaType: " + invitation.getMediaType());
        
        // 检查ConversationType常量值
        System.out.println("\n📊 ConversationType常量值：");
        System.out.println("   GROUP_CHAT: " + ConversationType.GROUP_CHAT);
        System.out.println("   SINGLE_CHAT: " + ConversationType.SINGLE_CHAT);
        System.out.println("   SUPER_GROUP_CHAT: " + ConversationType.SUPER_GROUP_CHAT);
        
        // 使用CallStateManager判断是否为群组通话
        boolean isGroupCall = CallStateManager.isGroupCall(groupSignalingInfo);
        System.out.println("\n🔍 CallStateManager.isGroupCall() 结果：");
        System.out.println("   判断结果: " + (isGroupCall ? "✅ 群组通话" : "❌ 单人通话"));
        System.out.println("   SessionType == GROUP_CHAT: " + (invitation.getSessionType() == ConversationType.GROUP_CHAT));
        
        // 问题诊断
        if (!isGroupCall) {
            System.out.println("\n🚨 问题诊断：");
            System.out.println("   IMUtil.buildGroupSignalingInfo() 没有正确设置 SessionType 为 GROUP_CHAT");
            System.out.println("   实际 SessionType: " + invitation.getSessionType());
            System.out.println("   期望 SessionType: " + ConversationType.GROUP_CHAT);
        } else {
            System.out.println("\n✅ 信令构建正确，问题可能在其他地方");
        }
    }
}