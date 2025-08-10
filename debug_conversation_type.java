import io.openim.android.sdk.enums.ConversationType;

public class debug_conversation_type {
    public static void main(String[] args) {
        System.out.println("=== ConversationType 值调试 ===");
        
        // 输出各种ConversationType的值
        System.out.println("SINGLE_CHAT: " + ConversationType.SINGLE_CHAT);
        System.out.println("GROUP_CHAT: " + ConversationType.GROUP_CHAT);
        
        // 检查是否还有其他类型
        try {
            System.out.println("SUPER_GROUP_CHAT: " + ConversationType.SUPER_GROUP_CHAT);
        } catch (Exception e) {
            System.out.println("SUPER_GROUP_CHAT 不存在或出错: " + e.getMessage());
        }
        
        // 验证群组通话判断逻辑
        int sessionType = 2;  // 从日志中看到的值
        System.out.println("\n=== 判断逻辑验证 ===");
        System.out.println("SessionType从日志: " + sessionType);
        System.out.println("GROUP_CHAT常量: " + ConversationType.GROUP_CHAT);
        System.out.println("是否相等: " + (sessionType == ConversationType.GROUP_CHAT));
    }
}