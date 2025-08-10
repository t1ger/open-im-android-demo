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
                buildCallDialog(getContext(), null, false).show();
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

    private void cancelNotify() {
        isBeCalled = false;
        //未读消息sdk不能增加 所以我们这里只是发个通知
        NotificationUtil.cancelNotify(A_NOTIFY_ID);
        if (BaseApp.inst().isAppBackground.val())
            IMUtil.sendNotice(A_NOTIFY_ID);
        MediaPlayerUtil.INSTANCE.pause();
        MediaPlayerUtil.INSTANCE.release();
    }

    public Dialog buildCallDialog(Context context,
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
            
            // 调试输出：检查SessionType
            if (signalingInfo.getInvitation() != null) {
                L.critical(TAG, "📋 SessionType值: " + signalingInfo.getInvitation().getSessionType());
                L.critical(TAG, "📋 GROUP_CHAT常量: " + ConversationType.GROUP_CHAT);
                L.critical(TAG, "📋 SINGLE_CHAT常量: " + ConversationType.SINGLE_CHAT);
                L.critical(TAG, "📋 GroupID: " + signalingInfo.getInvitation().getGroupID());
                L.critical(TAG, "📋 InviteeList大小: " + (signalingInfo.getInvitation().getInviteeUserIDList() != null ? signalingInfo.getInvitation().getInviteeUserIDList().size() : "null"));
                L.critical(TAG, "🔍 是否相等: " + (signalingInfo.getInvitation().getSessionType() == ConversationType.GROUP_CHAT));
                L.critical(TAG, "🔍 CallStateManager判断结果: " + CallStateManager.isGroupCall(signalingInfo));
            }
            
            // ✅ 直接使用CallDialogFactory，跳过可能修改信令的SignalingProcessor
            // 根因修复：SignalingProcessor可能在处理过程中修改原始信令的SessionType
            callDialog = CallDialogFactory.create(context, this, signalingInfo, isCallOut, dismissListener);
            
            // 验证创建的对话框类型是否正确
            String createdDialogType = callDialog.getClass().getSimpleName();
            L.critical(TAG, "创建的对话框类型: " + createdDialogType);
            
            // 如果群组信令却创建了单人对话框，记录关键调试信息
            boolean isGroupSignaling = CallStateManager.isGroupCall(signalingInfo);
            boolean isGroupDialog = createdDialogType.equals("GroupCallDialog");
            if (isGroupSignaling && !isGroupDialog) {
                L.e(TAG, "❌ 严重错误：群组信令创建了单人对话框！");
                L.e(TAG, "调试信息 - SessionType: " + signalingInfo.getInvitation().getSessionType());
                L.e(TAG, "调试信息 - GroupID: " + signalingInfo.getInvitation().getGroupID());
                L.e(TAG, "调试信息 - InviteeList: " + signalingInfo.getInvitation().getInviteeUserIDList());
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
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(TAG, "创建通话对话框失败", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
            
            // 异常降级：创建基础对话框
            try {
                callDialog = CallDialogFactory.create(context, this, signalingInfo, isCallOut);
                L.w(TAG, "异常后降级创建对话框成功");
            } catch (Exception fallbackException) {
                LogExceptionHandler.handleException(TAG, "降级创建对话框也失败", 
                    LogExceptionHandler.ExceptionType.CRITICAL_ERROR, fallbackException);
                return null;
            }
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
        
        try {
            // 创建对应类型的通话对话框
            buildCallDialog(getContext(), null, true);
            
            if (callDialog == null) {
                L.e(TAG, "通话对话框创建失败");
                return;
            }
            
            // 在UI线程显示对话框
            Common.UIHandler.post(() -> {
                try {
                    callDialog.show();
                    L.businessFlow(TAG, "通话对话框显示", "成功");
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
        if (null == callDialog || callDialog.getCallingVM().isGroupCall()) return; // ✅ 使用统一状态管理
        callDialog.getCallingVM().renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setDuration((int)
                (System.currentTimeMillis() - callHistory.getDate())));
        dismissDialog();
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


