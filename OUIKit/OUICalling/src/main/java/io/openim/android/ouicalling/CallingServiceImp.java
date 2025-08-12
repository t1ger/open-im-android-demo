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
    
    // 🎯 预初始化数据结构 - 支持业界最佳实践
    private PreInitializedGroupData preInitializedGroupData;
    
    // 预初始化数据类
    private static class PreInitializedGroupData {
        final String groupId;
        final List<String> memberIds;
        final int memberCount;
        
        PreInitializedGroupData(String groupId, List<String> memberIds, int memberCount) {
            this.groupId = groupId;
            this.memberIds = new ArrayList<>(memberIds); // 防止外部修改
            this.memberCount = memberCount;
        }
    }


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
        android.util.Log.e("CallFlow", "📨📨📨 [INVITE] 收到来电信令 - onReceiveNewInvitation");
        
        // 详细记录信令信息
        if (s != null && s.getInvitation() != null) {
            android.util.Log.e("CallFlow", "📨 [INVITE] 发起方: " + s.getInvitation().getInviterUserID());
            android.util.Log.e("CallFlow", "📨 [INVITE] 檒体类型: " + s.getInvitation().getMediaType());
            android.util.Log.e("CallFlow", "📨 [INVITE] 会话类型: " + s.getInvitation().getSessionType());
            android.util.Log.e("CallFlow", "📨 [INVITE] 房间ID: " + s.getInvitation().getRoomID());
        }
        
        if (callDialog != null) {
            android.util.Log.e("CallFlow", "⚠️ [INVITE] 已有通话进行中，忽略新来电");
            return;
        }
        
        android.util.Log.e("CallFlow", "🚀 [INVITE] 开始处理来电信令");
        Context context = BaseApp.inst();
        Common.wakeUp(context);
        setSignalingInfo(s);
        isBeCalled = true;
        android.util.Log.e("CallFlow", "✅ [INVITE] 基本状态设置完成 - isBeCalled=true");

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
                // 简化处理：直接显示Dialog（参考main分支的简洁实现）
                android.util.Log.e("CallFlow", "🚀 [INVITE] 前台显示来电界面");
                BaseCallDialog dialog = buildCallDialog(getContext(), null, false);
                if (dialog != null) {
                    dialog.show();
                    android.util.Log.e("CallFlow", "✅ [INVITE] 来电界面显示成功");
                } else {
                    android.util.Log.e("CallFlow", "❌ [INVITE] Dialog创建失败");
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
            
            // 创建通话对话框
            callDialog = CallDialogFactory.create(context, this, signalingInfo, isCallOut, dismissListener);
            
            L.d(TAG, "创建的对话框类型: " + callDialog.getClass().getSimpleName());
            
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
            
            // 关键修复：只在使用Application Context时设置TYPE_APPLICATION_OVERLAY
            if (context == BaseApp.inst() && callDialog.getWindow() != null) {
                try {
                    callDialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    L.d(TAG, "使用Application Context，已设置TYPE_APPLICATION_OVERLAY");
                } catch (Exception windowException) {
                    L.e(TAG, "Window类型设置失败", windowException);
                }
            }
            
            // 插入数据库记录（仅单人通话）
            insertCallHistoryRecord();
            
            // buildCallDialog完成，已有businessFlow日志记录
            
        } catch (CallDialogFactory.CallDialogCreationException e) {
            L.e(TAG, e.getCallType() + "创建失败", e);
            LogExceptionHandler.handleException(TAG, e.getCallType() + "创建失败", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
            
            String errorMessage = e.isGroupCall() ? 
                "群组通话暂时不可用，请稍后重试" : 
                "通话功能暂时不可用，请稍后重试";
            
            showErrorToast(context, errorMessage);
            return null;
            
        } catch (Exception e) {
            L.e(TAG, "buildCallDialog未知异常", e);
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
        
        // 🔍 详细调试日志：检查SignalingInfo数据
        android.util.Log.e("GroupCallFlow", "🔍🔍🔍 [CallingServiceImp] call()方法开始 - 详细数据检查");
        android.util.Log.e("GroupCallFlow", "📧 [DEBUG] SignalingInfo: " + (signalingInfo != null ? "不为null" : "为null"));
        if (signalingInfo != null && signalingInfo.getInvitation() != null) {
            android.util.Log.e("GroupCallFlow", "📧 [DEBUG] SessionType: " + signalingInfo.getInvitation().getSessionType());
            android.util.Log.e("GroupCallFlow", "📧 [DEBUG] GroupID: " + signalingInfo.getInvitation().getGroupID());
            List<String> memberIds = signalingInfo.getInvitation().getInviteeUserIDList();
            android.util.Log.e("GroupCallFlow", "📧 [DEBUG] InviteeUserIDList: " + (memberIds != null ? (memberIds.size() + "个成员: " + memberIds) : "为null"));
        }
        
        try {
            // 直接创建和显示通话界面
            buildCallDialog(getContext(), null, true);
            
            if (callDialog != null) {
                android.util.Log.e("GroupCallFlow", "✅ [DEBUG] CallDialog创建成功，开始检查群组初始化条件");
                
                // 群组通话初始化
                if (signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT) {
                    android.util.Log.e("GroupCallFlow", "🎯 [DEBUG] 确认为群组通话，开始获取成员数据");
                    
                    List<String> memberIds = signalingInfo.getInvitation().getInviteeUserIDList();
                    String groupId = signalingInfo.getInvitation().getGroupID();
                    
                    android.util.Log.e("GroupCallFlow", "📋 [DEBUG] 获取到GroupID: " + groupId);
                    android.util.Log.e("GroupCallFlow", "📋 [DEBUG] 获取到MemberIds: " + (memberIds != null ? (memberIds.size() + "个成员: " + memberIds) : "为null"));
                    
                    if (memberIds != null && !memberIds.isEmpty()) {
                        android.util.Log.e("GroupCallFlow", "🚀 [DEBUG] 开始调用initializeGroupMembers");
                        callDialog.getCallingVM().initializeGroupMembers(memberIds, groupId);
                        android.util.Log.e("GroupCallFlow", "✅ [DEBUG] initializeGroupMembers调用完成");
                        L.d(TAG, "群组数据初始化: " + (memberIds.size() + 1) + "个成员");
                        
                        // 验证数据是否正确设置
                        android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] 验证initializeGroupMembers是否生效");
                        List<?> currentMembers = callDialog.getCallingVM().getGroupMembers();
                        android.util.Log.e("GroupCallFlow", "📊 [DEBUG] 当前CallingVM中的成员数量: " + (currentMembers != null ? currentMembers.size() : "null"));
                    } else {
                        android.util.Log.e("GroupCallFlow", "❌ [ERROR] MemberIds为空或null，无法初始化群组成员");
                    }
                } else {
                    android.util.Log.e("GroupCallFlow", "ℹ️ [INFO] 非群组通话，跳过群组成员初始化");
                }
                
                // 显示Dialog
                callDialog.show();
                L.d(TAG, "通话界面显示成功");
                
                // 🔍 [CRITICAL] 验证Dialog是否真的可见
                android.util.Log.e("GroupCallFlow", "🔍🔍🔍 [CRITICAL] Dialog可见性验证开始");
                
                // 检查Dialog状态
                android.util.Log.e("GroupCallFlow", "📱 [CRITICAL] callDialog.isShowing(): " + callDialog.isShowing());
                
                // 检查Window状态
                if (callDialog.getWindow() != null) {
                    android.util.Log.e("GroupCallFlow", "🪟 [CRITICAL] Window不为null");
                    android.util.Log.e("GroupCallFlow", "🪟 [CRITICAL] Window.isActive(): " + callDialog.getWindow().isActive());
                    
                    // 检查Window属性
                    android.view.WindowManager.LayoutParams params = callDialog.getWindow().getAttributes();
                    if (params != null) {
                        android.util.Log.e("GroupCallFlow", "⚙️ [CRITICAL] Window类型: " + params.type);
                        android.util.Log.e("GroupCallFlow", "⚙️ [CRITICAL] Window标志: " + params.flags);
                        android.util.Log.e("GroupCallFlow", "⚙️ [CRITICAL] 透明度: " + params.alpha);
                        android.util.Log.e("GroupCallFlow", "⚙️ [CRITICAL] 宽度x高度: " + params.width + "x" + params.height);
                    }
                } else {
                    android.util.Log.e("GroupCallFlow", "❌ [CRITICAL] Window为null！");
                }
                
                // 延迟检查是否真的显示了
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    android.util.Log.e("GroupCallFlow", "⏰ [CRITICAL] 延迟500ms后检查Dialog状态");
                    android.util.Log.e("GroupCallFlow", "📱 [CRITICAL] 延迟检查 - isShowing(): " + (callDialog != null && callDialog.isShowing()));
                    
                    // 尝试强制置顶
                    if (callDialog != null && callDialog.getWindow() != null) {
                        try {
                            callDialog.getWindow().addFlags(
                                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            );
                            android.util.Log.e("GroupCallFlow", "🔧 [CRITICAL] 已添加强制显示标志");
                        } catch (Exception flagException) {
                            android.util.Log.e("GroupCallFlow", "❌ [CRITICAL] 添加显示标志失败: " + flagException.getMessage());
                        }
                    }
                }, 500);
                
                android.util.Log.e("GroupCallFlow", "✅✅✅ [CRITICAL] Dialog可见性验证结束");
                
            } else {
                android.util.Log.e("GroupCallFlow", "❌ [ERROR] CallDialog创建失败");
                L.e(TAG, "Dialog创建失败");
            }
            
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] call()方法异常: " + e.getMessage(), e);
            L.e(TAG, "Service处理通话请求异常", e);
        }
        
        android.util.Log.e("GroupCallFlow", "🏁 [CallingServiceImp] call()方法结束");
    }

    public boolean isCallingTips() {
        boolean is = isCalling();
        if (is) {
            Toast.makeText(BaseApp.inst(), "正在通话中", Toast.LENGTH_SHORT).show();
        }
        return is;
    }

    public boolean isCalling() {
        return callDialog != null && callDialog.isShowing();
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


