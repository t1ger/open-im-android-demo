package io.openim.android.ouicalling;

import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.alibaba.android.arouter.core.LogisticsCenter;
import com.alibaba.android.arouter.facade.Postcard;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.hjq.permissions.Permission;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.entity.CallHistory;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.ActivityManager;
import io.openim.android.ouicore.utils.BackgroundStartPermissions;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicalling.entity.GroupCallMember;
import java.util.List;
import io.openim.android.sdk.enums.ConversationType;
import android.widget.Toast;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.ouicalling.state.CallStateManager;
import io.openim.android.ouicore.utils.NotificationUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.PublicUserInfo;
import io.openim.android.sdk.models.SignalingInfo;

@Route(path = Routes.Service.CALLING)
public class CallingServiceImp implements CallingService {
    private OnServicePriorLoginCallBack onServicePriorLoginCallBack;
    public static final String TAG = "CallingServiceImp";
    public BaseCallDialog callDialog;
    private SignalingInfo signalingInfo;
    public static final int A_NOTIFY_ID = 100;
    public boolean isBeCalled = false;


    public void setSignalingInfo(SignalingInfo signalingInfo) {
        this.signalingInfo = signalingInfo;
    }

    @Override
    public void setOnServicePriorLoginCallBack(OnServicePriorLoginCallBack onServicePriorLoginCallBack) {
        this.onServicePriorLoginCallBack = onServicePriorLoginCallBack;
    }

    @Override
    public OnServicePriorLoginCallBack getOnServicePriorLoginCallBack() {
        return onServicePriorLoginCallBack;
    }

    @Override
    public void init(Context context) {
    }

    @Override
    public void onInvitationCancelled(SignalingInfo s) {
        cancelNotify();
        if (null == callDialog) return;
        callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setFailedState(1));
        dismissDialog();
    }

    @Override
    public void onInvitationTimeout(SignalingInfo s) {
        L.e(TAG, "----onInvitationTimeout-----");
        handleInvitationTimeout(s);
    }
    
    /**
     * 处理邀请超时事件
     * 微信模式：提供重连和用户友好的处理
     */
    private void handleInvitationTimeout(SignalingInfo signalingInfo) {
        try {
            if (signalingInfo == null) {
                L.w(TAG, "[超时处理] SignalingInfo为空，无法处理");
                return;
            }
            
            // 1. 记录超时事件
            String timeoutUserId = extractUserIdFromTimeout(signalingInfo);
            L.businessFlow(TAG, "邀请超时", "用户: " + timeoutUserId);
            
            // 2. 更新数据库记录
            if (callDialog != null) {
                callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(),
                    (realm, callHistory) -> {
                        callHistory.setFailedState(3); // 3表示超时
                        callHistory.setSuccess(false);
                    });
            }
            
            // 3. 区分群组通话和单人通话处理
            if (isGroupCall(signalingInfo)) {
                handleGroupCallTimeout(signalingInfo, timeoutUserId);
            } else {
                handleSingleCallTimeout(signalingInfo, timeoutUserId);
            }
            
        } catch (Exception e) {
            L.e(TAG, "[超时处理] 处理异常", e);
            // 异常情况下至少要关闭对话框
            dismissDialogSafely();
        }
    }
    
    /**
     * 处理群组通话超时
     */
    private void handleGroupCallTimeout(SignalingInfo signalingInfo, String timeoutUserId) {
        try {
            if (callDialog != null && callDialog instanceof GroupCallDialog) {
                GroupCallDialog groupDialog = (GroupCallDialog) callDialog;
                
                // 更新超时成员状态
                groupDialog.getCallingVM().updateMemberTimeout(timeoutUserId);
                
                // 检查是否还有其他成员
                List<GroupCallMember> remainingMembers = groupDialog.getCallingVM().getActiveMembers();
                
                if (remainingMembers.isEmpty()) {
                    // 所有成员都超时，结束通话
                    L.w(TAG, "[群组超时] 所有成员超时，结束通话");
                    showTimeoutMessage("通话无人接听，已自动结束");
                    dismissDialogSafely();
                } else {
                    // 还有其他成员，显示部分超时提示
                    L.d(TAG, "[群组超时] 部分成员超时，继续等待其他成员");
                    showTimeoutMessage("部分成员未接听，继续等待其他成员");
                    
                    // 可选：提供重新邀请超时成员的选项
                    offerReinviteOption(timeoutUserId);
                }
            } else {
                L.w(TAG, "[群组超时] 对话框不是GroupCallDialog类型，直接结束");
                dismissDialogSafely();
            }
            
        } catch (Exception e) {
            L.e(TAG, "[群组超时处理] 异常", e);
            dismissDialogSafely();
        }
    }
    
    /**
     * 处理单人通话超时
     */
    private void handleSingleCallTimeout(SignalingInfo signalingInfo, String timeoutUserId) {
        try {
            L.d(TAG, "[单人超时] 对方未接听: " + timeoutUserId);
            
            // 显示超时消息
            showTimeoutMessage("对方未接听，通话已结束");
            
            // 可选：提供重拨选项
            offerRedialOption(signalingInfo);
            
            // 结束通话
            dismissDialogSafely();
            
        } catch (Exception e) {
            L.e(TAG, "[单人超时处理] 异常", e);
            dismissDialogSafely();
        }
    }
    
    /**
     * 检查是否为群组通话
     */
    private boolean isGroupCall(SignalingInfo signalingInfo) {
        try {
            return signalingInfo.getInvitation() != null && 
                   signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT;
        } catch (Exception e) {
            L.e(TAG, "[群组检查] 异常", e);
            return false;
        }
    }
    
    /**
     * 显示超时消息
     */
    private void showTimeoutMessage(String message) {
        try {
            Context context = getContext();
            Common.UIHandler.post(() -> {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            });
            L.d(TAG, "[超时提示] " + message);
        } catch (Exception e) {
            L.e(TAG, "[超时提示] 显示异常", e);
        }
    }
    
    /**
     * 提供重新邀请选项（群组通话）
     */
    private void offerReinviteOption(String timeoutUserId) {
        try {
            // 这里可以实现重新邀请的UI选项
            // 例如在GroupCallDialog中添加"重新邀请"按钮
            L.d(TAG, "[重邀选项] 可重新邀请用户: " + timeoutUserId);
            
            // TODO: 实现重新邀请UI和逻辑
            // callDialog.showReinviteOption(timeoutUserId);
            
        } catch (Exception e) {
            L.e(TAG, "[重邀选项] 异常", e);
        }
    }
    
    /**
     * 提供重拨选项（单人通话）
     */
    private void offerRedialOption(SignalingInfo signalingInfo) {
        try {
            // 这里可以实现重拨的UI选项
            L.d(TAG, "[重拨选项] 可重拨通话");
            
            // TODO: 实现重拨UI和逻辑
            // showRedialDialog(signalingInfo);
            
        } catch (Exception e) {
            L.e(TAG, "[重拨选项] 异常", e);
        }
    }
    
    /**
     * 安全地关闭对话框
     */
    private void dismissDialogSafely() {
        try {
            Common.UIHandler.post(() -> {
                if (callDialog != null) {
                    callDialog.dismiss();
                    L.d(TAG, "[安全关闭] 对话框已关闭");
                }
            });
        } catch (Exception e) {
            L.e(TAG, "[安全关闭] 异常", e);
        }
    }
    
    /**
     * 从超时信令中提取用户ID
     */
    private String extractUserIdFromTimeout(SignalingInfo signalingInfo) {
        try {
            if (signalingInfo.getInvitation() != null) {
                // 优先获取邀请发起者
                if (signalingInfo.getInvitation().getInviterUserID() != null) {
                    return signalingInfo.getInvitation().getInviterUserID();
                }
                // 如果没有发起者，获取被邀请者列表的第一个
                if (signalingInfo.getInvitation().getInviteeUserIDList() != null && 
                    !signalingInfo.getInvitation().getInviteeUserIDList().isEmpty()) {
                    return signalingInfo.getInvitation().getInviteeUserIDList().get(0);
                }
            }
            return "unknown_user";
        } catch (Exception e) {
            L.e(TAG, "[提取用户ID] 异常", e);
            return "unknown_user";
        }
    }

    @Override
    public void onInviteeAccepted(SignalingInfo s) {
        if (null == callDialog) return;
        callDialog.otherSideAccepted();
        callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setSuccess(true));
    }

    @Override
    public void onInviteeAcceptedByOtherDevice(SignalingInfo s) {
        L.e(TAG, "----onInviteeAcceptedByOtherDevice-----");
        Toast.makeText(getContext(), io.openim.android.ouicore.R.string.other_accepted,
            Toast.LENGTH_SHORT).show();
        dismissDialog();
    }

    @Override
    public void onInviteeRejected(SignalingInfo s) {
        L.e(TAG, "----onInviteeRejected-----");
        if (null == callDialog) return;
        callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(), (realm, callHistory) -> {
            callHistory.setSuccess(false);
            callHistory.setFailedState(2);
        });
        dismissDialog();
    }

    private void dismissDialog() {
        Common.UIHandler.post(() -> {
            if (callDialog != null) {
                callDialog.dismiss();
            }
        });
    }

    @Override
    public void onInviteeRejectedByOtherDevice(SignalingInfo s) {
        L.e(TAG, "----onInviteeRejectedByOtherDevice-----");
        Toast.makeText(getContext(), io.openim.android.ouicore.R.string.other_rejected,
            Toast.LENGTH_SHORT).show();
        dismissDialog();
        cancelNotify();
    }

    @Override
    public void onReceiveNewInvitation(SignalingInfo s) {
        L.e(TAG, "----onReceiveNewInvitation-----");
        if (callDialog != null) return;
        Context context = BaseApp.inst();
        Common.wakeUp(context);
        setSignalingInfo(s);
        isBeCalled = true;

        boolean isSystemAlert = new HasPermissions(BaseApp.inst(),
            Permission.SYSTEM_ALERT_WINDOW).isAllGranted();
        Intent hangIntent;
        boolean backgroundStart =
            BackgroundStartPermissions.INSTANCE.isBackgroundStartAllowed(context);
        if (isSystemAlert && backgroundStart) {
            hangIntent =
                new Intent(context, LockPushActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(hangIntent);
        } else {
            if (BaseApp.inst().isAppBackground.val()) {
                Postcard postcard = ARouter.getInstance().build(Routes.Main.HOME);
                LogisticsCenter.completion(postcard);
                hangIntent =
                    new Intent(context, postcard.getDestination()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                MediaPlayerUtil.INSTANCE.initMedia(BaseApp.inst(), R.raw.incoming_call_ring);
                MediaPlayerUtil.INSTANCE.loopPlay();

                PendingIntent hangPendingIntent = PendingIntent.getActivity(context, 1,
                    hangIntent, PendingIntent.FLAG_MUTABLE);

                Notification notification =
                    NotificationUtil.builder(NotificationUtil.CALL_CHANNEL_ID).setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_CALL).setContentTitle("OpenIM").setContentText(context.getString(io.openim.android.ouicore.R.string.receive_call_invite)).setAutoCancel(true).setOngoing(true).setFullScreenIntent(hangPendingIntent, true).setContentIntent(hangPendingIntent).setCustomHeadsUpContentView(new RemoteViews(BaseApp.inst().getPackageName(), R.layout.layout_call_invite)).build();

                NotificationUtil.sendNotify(A_NOTIFY_ID, notification);
            } else {
                // 微信模式：安全创建和显示对话框
                try {
                    BaseCallDialog dialog = buildCallDialog(getContext(), null, false);
                    if (dialog != null) {
                        dialog.show();
                        android.util.Log.d("GroupCallFlow", "✅ [CallingService] 对话框显示成功");
                    } else {
                        android.util.Log.e("GroupCallFlow", "❌ [CallingService] 对话框为null，无法显示");
                        // TODO: 显示系统通知或Toast提示
                    }
                } catch (Exception showException) {
                    android.util.Log.e("GroupCallFlow", "❌ [CallingService] 对话框显示异常", showException);
                    showErrorToast(getContext(), "通话功能暂时不可用，请稍后重试");
                }
            }
        }
    }

    private Context getContext() {
        Context ctx;
        if (ActivityManager.getActivityStack().isEmpty())
            ctx = BaseApp.inst();
        else {
            ctx = ActivityManager.getActivityStack().peek();
        }
        return ctx;
    }
    
    /**
     * 微信模式：显示用户友好的错误提示
     */
    private void showErrorToast(Context context, String message) {
        try {
            // 如果有Activity上下文，在主线程显示Toast
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
                });
            } else {
                // 使用Application上下文显示Toast
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
            }
            android.util.Log.d("GroupCallFlow", "📱 [WeChat模式] 显示错误提示: " + message);
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [CallingService] Toast显示异常", e);
        }
    }

    private void cancelNotify() {
        isBeCalled = false;
        //未读消息sdk不能增加 所以我们这里只是发个通知
        NotificationUtil.cancelNotify(A_NOTIFY_ID);
        if (BaseApp.inst().isAppBackground.val())
            IMUtil.sendNotice(A_NOTIFY_ID);
        MediaPlayerUtil.INSTANCE.pause();
        MediaPlayerUtil.INSTANCE.release();
    }

    public BaseCallDialog buildCallDialog(Context context,
                                  DialogInterface.OnDismissListener dismissListener,
                                  boolean isCallOut) {
        try {
            if (callDialog != null) {
                L.d(TAG, "复用现有通话对话框");
                return callDialog;
            }
            
            // 🔧 关键修复：先验证信令类型，避免被意外修改
            String originalCallType = CallDialogFactory.getCallTypeDescription(signalingInfo);
            L.critical(TAG, "✅ 原始信令类型: " + originalCallType);
            android.util.Log.d("GroupCallFlow", "🔍 [buildCallDialog] 原始信令类型: " + originalCallType);
            
            // 调试输出：检查SessionType
            if (signalingInfo.getInvitation() != null) {
                L.critical(TAG, "📋 SessionType值: " + signalingInfo.getInvitation().getSessionType());
                L.critical(TAG, "📋 GROUP_CHAT常量: " + ConversationType.GROUP_CHAT);
                L.critical(TAG, "📋 SINGLE_CHAT常量: " + ConversationType.SINGLE_CHAT);
                L.critical(TAG, "📋 GroupID: " + signalingInfo.getInvitation().getGroupID());
                L.critical(TAG, "📋 InviteeList大小: " + (signalingInfo.getInvitation().getInviteeUserIDList() != null ? signalingInfo.getInvitation().getInviteeUserIDList().size() : "null"));
                L.critical(TAG, "🔍 是否相等: " + (signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT));
                L.critical(TAG, "🔍 CallStateManager判断结果: " + CallStateManager.isGroupCall(signalingInfo));
                
                android.util.Log.d("GroupCallFlow", "📋 [buildCallDialog] SessionType: " + signalingInfo.getInvitation().getSessionType() + ", GROUP_CHAT: " + ConversationType.GROUP_CHAT + ", 是群组: " + CallStateManager.isGroupCall(signalingInfo));
            }
            
            // ✅ 直接使用CallDialogFactory，跳过可能修改信令的SignalingProcessor
            // 根因修复：SignalingProcessor可能在处理过程中修改原始信令的SessionType
            callDialog = CallDialogFactory.create(context, this, signalingInfo, isCallOut, dismissListener);
            
            // 验证创建的对话框类型是否正确
            String createdDialogType = callDialog.getClass().getSimpleName();
            L.critical(TAG, "创建的对话框类型: " + createdDialogType);
            android.util.Log.d("GroupCallFlow", "✅ [buildCallDialog] 创建的对话框类型: " + createdDialogType);
            
            // 如果群组信令却创建了单人对话框，记录关键调试信息
            boolean isGroupSignaling = CallStateManager.isGroupCall(signalingInfo);
            boolean isGroupDialog = createdDialogType.equals("GroupCallDialog");
            if (isGroupSignaling && !isGroupDialog) {
                L.e(TAG, "❌ 严重错误：群组信令创建了单人对话框！");
                L.e(TAG, "调试信息 - SessionType: " + signalingInfo.getInvitation().getSessionType());
                L.e(TAG, "调试信息 - GroupID: " + signalingInfo.getInvitation().getGroupID());
                L.e(TAG, "调试信息 - InviteeList: " + signalingInfo.getInvitation().getInviteeUserIDList());
                android.util.Log.e("GroupCallFlow", "❌ [buildCallDialog] 严重错误：群组信令创建了单人对话框！SessionType: " + signalingInfo.getInvitation().getSessionType());
            } else if (isGroupSignaling && isGroupDialog) {
                android.util.Log.d("GroupCallFlow", "✅ [buildCallDialog] 群组信令正确创建了GroupCallDialog");
            } else if (!isGroupSignaling && !isGroupDialog) {
                android.util.Log.d("GroupCallFlow", "✅ [buildCallDialog] 单人信令正确创建了SingleCallDialog");
            }
            
            L.businessFlow(TAG, "通话对话框创建", 
                "类型: " + callDialog.getClass().getSimpleName() + 
                ", 通话类型: " + CallDialogFactory.getCallTypeDescription(signalingInfo));
            
            // 设置被呼状态的特殊处理
            if (!callDialog.getCallingVM().isCallOut) {
                callDialog.setOnDismissListener(dialog -> {
                    isBeCalled = false;
                    if (null != dismissListener) dismissListener.onDismiss(dialog);
                });
                
                // 锁屏状态处理
                if (!Common.isScreenLocked() && Common.hasSystemAlertWindow()) {
                    callDialog.setOnShowListener(dialog -> {
                        ARouter.getInstance().build(Routes.Main.HOME).navigation();
                    });
                }
            }
            
            // 插入数据库记录（仅单人通话）
            insertCallHistoryRecord();
            
        } catch (CallDialogFactory.CallDialogCreationException e) {
            // 📱 微信模式：显示用户友好的Toast提示
            android.util.Log.e("GroupCallFlow", "❌ [CallingService] " + e.getCallType() + "创建失败: " + e.getMessage(), e);
            LogExceptionHandler.handleException(TAG, e.getCallType() + "创建失败", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
            
            // 显示类似微信的错误提示
            String errorMessage = e.isGroupCall() ? 
                "群组通话暂时不可用，请稍后重试" : 
                "通话功能暂时不可用，请稍后重试";
            
            showErrorToast(context, errorMessage);
            
            return null;
            
        } catch (Exception e) {
            // 其他未预期的异常
            android.util.Log.e("GroupCallFlow", "❌ [CallingService] 未知异常: " + e.getMessage(), e);
            LogExceptionHandler.handleException(TAG, "未知错误", 
                LogExceptionHandler.ExceptionType.CRITICAL_ERROR, e);
            
            showErrorToast(context, "系统错误，请稍后重试");
            
            return null;
        }
        
        return callDialog;
    }

    @Override
    public void call(SignalingInfo signalingInfo) {
        L.businessFlow(TAG, "发起通话", "开始处理通话请求");
        android.util.Log.d("GroupCallFlow", "📥 [CallingService] 接收到通话信令 - 开始处理");
        
        // 检查是否已有通话进行中
        if (isCallingTips()) {
            L.w(TAG, "已有通话进行中，忽略新的通话请求");
            return;
        }
        
        // 设置信令信息
        setSignalingInfo(signalingInfo);
        
        // 记录通话类型用于调试
        String callTypeDesc = CallDialogFactory.getCallTypeDescription(signalingInfo);
        L.businessFlow(TAG, "通话类型识别", callTypeDesc);
        android.util.Log.d("GroupCallFlow", "🔍 [CallingService] 信令类型识别: " + callTypeDesc);
        
        try {
            // 创建对应类型的通话对话框
            android.util.Log.d("GroupCallFlow", "🔧 [CallingService] 开始创建通话对话框");
            buildCallDialog(getContext(), null, true);
            
            if (callDialog == null) {
                L.e(TAG, "通话对话框创建失败");
                android.util.Log.e("GroupCallFlow", "❌ [CallingService] 通话对话框创建失败");
                return;
            }
            
            android.util.Log.d("GroupCallFlow", "✅ [CallingService] 通话对话框创建成功: " + callDialog.getClass().getSimpleName());
            
            // 🔥 修复核心问题：如果是群组通话，需要初始化CallingVM的群组成员列表
            if (signalingInfo.getInvitation() != null && 
                signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT) {
                
                android.util.Log.d("GroupCallFlow", "🔧 [CallingService] 检测到群组通话，开始初始化群组成员");
                
                try {
                    // 提取群组通话信息
                    String groupId = signalingInfo.getInvitation().getGroupID();
                    List<String> memberIds = signalingInfo.getInvitation().getInviteeUserIDList();
                    // 判断是否为视频通话（根据实际SDK的字段调整）
                    boolean isVideo = false; // 暂时设为false，需要根据实际情况调整
                    
                    android.util.Log.d("GroupCallFlow", "📋 [CallingService] 群组信息: groupId=" + groupId + ", memberCount=" + (memberIds != null ? memberIds.size() : 0) + ", isVideo=" + isVideo);
                    
                    // 验证数据完整性
                    if (groupId != null && memberIds != null && !memberIds.isEmpty()) {
                        // 🚀 关键修复：调用CallingVM.initiateGroupCall()来初始化群组成员列表
                        // 注意：这里不会重复发送信令，因为信令已经通过ChatVM发送了
                        // 我们只需要初始化CallingVM内部的群组成员状态
                        callDialog.getCallingVM().initializeGroupMembers(memberIds, groupId);
                        
                        android.util.Log.d("GroupCallFlow", "✅ [CallingService] 群组成员初始化完成: " + callDialog.getCallingVM().getGroupMembers().size() + " 个成员");
                    } else {
                        android.util.Log.e("GroupCallFlow", "❌ [CallingService] 群组通话信息不完整: groupId=" + groupId + ", memberIds=" + memberIds);
                    }
                } catch (Exception e) {
                    android.util.Log.e("GroupCallFlow", "❌ [CallingService] 初始化群组成员失败: " + e.getMessage(), e);
                    LogExceptionHandler.handleException(TAG, "初始化群组成员失败", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
                }
            }
            
            // 在UI线程显示对话框
            Common.UIHandler.post(() -> {
                try {
                    callDialog.show();
                    L.businessFlow(TAG, "通话对话框显示", "成功");
                    android.util.Log.d("GroupCallFlow", "✅ [CallingService] 通话对话框显示成功");
                } catch (Exception e) {
                    LogExceptionHandler.handleException(TAG, "显示通话对话框失败", 
                        LogExceptionHandler.ExceptionType.UI_ERROR, e);
                }
            });
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "处理通话请求失败", 
                LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }

    public boolean isCallingTips() {
        boolean is = isCalling();
        if (is) {
            Toast.makeText(getContext(), io.openim.android.ouicore.R.string.now_calling,
                Toast.LENGTH_SHORT).show();
        }
        return is;
    }

    public boolean isCalling() {
        return null != callDialog
            && callDialog.isShowing();
    }

    @Override
    public void onHangup(SignalingInfo s) {
        L.e(TAG, "----onHangup-----");
        
        // 修复：这是信令系统的挂断回调，不是用户主动挂断
        // 无论单人还是群组通话，都需要处理对方挂断的信令
        if (null == callDialog) {
            L.w(TAG, "onHangup: 通话对话框为空");
            return;
        }
        
        // 记录通话时长（仅单人通话记录到通话历史）
        if (!callDialog.getCallingVM().isGroupCall()) {
            callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(),
                (realm, callHistory) -> callHistory.setDuration((int)
                    (System.currentTimeMillis() - callHistory.getDate())));
        }
        
        // 关闭对话框
        dismissDialog();
        
        L.businessFlow(TAG, "收到挂断信令", 
            "通话类型: " + (callDialog.getCallingVM().isGroupCall() ? "群组" : "单人"));
    }

    /**
     * 插入通话历史记录到数据库
     * 重命名并优化原有的insetDB方法
     */
    private void insertCallHistoryRecord() {
        try {
            // 群组通话暂不记录到通话历史（业务逻辑）
            if (callDialog.getCallingVM().isGroupCall()) {
                L.d(TAG, "群组通话不记录到通话历史");
                return;
            }
            
            List<String> ids = new ArrayList<>();
            ids.add(callDialog.getCallingVM().isCallOut ?
                signalingInfo.getInvitation().getInviteeUserIDList().get(0) :
                signalingInfo.getInvitation().getInviterUserID());

            boolean isCallOut = !callDialog.getCallingVM().isCallOut;
            
            OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
                @Override
                public void onError(int code, String error) {
                    LogExceptionHandler.handleException(TAG, "获取用户信息失败", 
                        LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                    L.e(TAG, "获取用户信息失败: " + error + ", code: " + code);
                }

                @Override
                public void onSuccess(List<PublicUserInfo> data) {
                    if (data.isEmpty() || null == callDialog) return;
                    
                    PublicUserInfo userInfo = data.get(0);
                    BaseApp.inst().realm.executeTransactionAsync(realm -> {
                        if (null == callDialog) return;
                        
                        try {
                            CallHistory callHistory = new CallHistory(callDialog.buildPrimaryKey(),
                                userInfo.getUserID(), userInfo.getNickname(), userInfo.getFaceURL(),
                                signalingInfo.getInvitation().getMediaType(), false, 0, isCallOut,
                                System.currentTimeMillis(), 0);
                            realm.insert(callHistory);
                            
                            L.d(TAG, "通话历史记录插入成功: " + userInfo.getNickname());
                            
                        } catch (Exception e) {
                            LogExceptionHandler.handleException(TAG, "插入通话历史记录失败", 
                                LogExceptionHandler.ExceptionType.DATABASE_ERROR, e);
                        }
                    });
                }
            }, ids);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "处理通话历史记录", 
                LogExceptionHandler.ExceptionType.DATA_ERROR, e);
        }
    }

}


