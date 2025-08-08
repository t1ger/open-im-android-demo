package io.openim.android.ouicore.im;

import java.util.List;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.ouicore.factory.SignalingInfoFactory;

/**
 * IMUtil中SignalingInfo相关方法的重构版本
 * 用于替换原有的重复代码
 */
public class IMUtilSignalingPatch {
    
    /**
     * Build SignalingInfo for single call
     * ✅ 重构：使用SignalingInfoFactory消除重复代码
     *
     * @param isVideoCalls   if true, called by video.
     * @param inviteeUserIDs invited user
     * @return calling parameter
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public static SignalingInfo buildSignalingInfo(boolean isVideoCalls, List<String> inviteeUserIDs) {
        try {
            return SignalingInfoFactory.buildSingleCallSignaling(isVideoCalls, inviteeUserIDs);
        } catch (Exception e) {
            android.util.Log.e("IMUtil", "构建单人通话信令失败", e);
            throw e; // 重新抛出异常，让调用者处理
        }
    }

    /**
     * 构建群组通话的SignalingInfo
     * ✅ 重构：使用SignalingInfoFactory消除重复代码
     *
     * @param isVideoCalls   if true, called by video.
     * @param groupId        群组ID
     * @param inviteeUserIDs invited user
     * @return calling parameter
     * @throws IllegalArgumentException 参数无效时抛出
     */
    public static SignalingInfo buildGroupSignalingInfo(boolean isVideoCalls, String groupId, List<String> inviteeUserIDs) {
        try {
            return SignalingInfoFactory.buildGroupCallSignaling(isVideoCalls, groupId, inviteeUserIDs);
        } catch (Exception e) {
            android.util.Log.e("IMUtil", "构建群组通话信令失败", e);
            throw e; // 重新抛出异帰，让调用者处理
        }
    }
}