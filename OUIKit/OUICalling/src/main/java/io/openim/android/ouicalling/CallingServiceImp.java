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
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import io.openim.android.ouicore.utils.NotificationUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.PublicUserInfo;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.ouicalling.vm.CallingVM;

@Route(path = Routes.Service.CALLING)
public class CallingServiceImp implements CallingService {
    private OnServicePriorLoginCallBack onServicePriorLoginCallBack;
    public static final String TAG = "CallingServiceImp";
    public CallDialog callDialog;
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
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setFailedState(1));
        dismissDialog();
    }

    @Override
    public void onInvitationTimeout(SignalingInfo s) {
        L.e(TAG, "----onInvitationTimeout-----");
        cancelNotify();
        if (null == callDialog) return;
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setFailedState(3));
        dismissDialog();
    }

    @Override
    public void onReceiveNewInvitation(SignalingInfo s) {
        L.e(TAG, "========== onReceiveNewInvitation 被呼叫方接收信令 ==========");
        L.e(TAG, "SignalingInfo: " + (s != null ? "valid" : "null"));
        if (s != null && s.getInvitation() != null) {
            L.e(TAG, "RoomID: " + s.getInvitation().getRoomID());
            L.e(TAG, "SessionType: " + s.getInvitation().getSessionType());
            L.e(TAG, "MediaType: " + s.getInvitation().getMediaType());
            L.e(TAG, "InviterUserID: " + s.getInvitation().getInviterUserID());
            L.e(TAG, "InviteeList: " + s.getInvitation().getInviteeUserIDList());
        }
        
        if (callDialog != null) {
            L.e(TAG, "⚠️ 已存在CallDialog，忽略新邀请");
            return;
        }
        
        Context context = BaseApp.inst();
        L.e(TAG, "Context: " + context.getClass().getSimpleName());
        Common.wakeUp(context);
        setSignalingInfo(s);
        isBeCalled = true;

        boolean isSystemAlert = new HasPermissions(BaseApp.inst(),
            Permission.SYSTEM_ALERT_WINDOW).isAllGranted();
        L.e(TAG, "SystemAlert权限: " + isSystemAlert);
        
        Intent hangIntent;
        boolean backgroundStart =
            BackgroundStartPermissions.INSTANCE.isBackgroundStartAllowed(context);
        L.e(TAG, "BackgroundStart权限: " + backgroundStart);
        L.e(TAG, "App是否在后台: " + BaseApp.inst().isAppBackground.val());
        
        if (isSystemAlert && backgroundStart) {
            L.e(TAG, "🔒 使用LockPushActivity显示通话");
            hangIntent =
                new Intent(context, LockPushActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(hangIntent);
        } else {
            if (BaseApp.inst().isAppBackground.val()) {
                L.e(TAG, "🔔 应用在后台，发送通知");
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
                L.e(TAG, "📱 应用在前台，直接显示通话界面");
                // 🔧 修复：使用buildCallDialog方法创建适当的Dialog类型
                CallDialog dialog = buildCallDialog(getContext(), null, false);
                if (dialog != null) {
                    L.e(TAG, "✅ CallDialog创建成功，isShowing: " + dialog.isShowing());
                    dialog.show();
                    L.e(TAG, "✅ CallDialog.show()调用完成，当前isShowing: " + dialog.isShowing());
                } else {
                    L.e(TAG, "❌ CallDialog创建失败！");
                }
            }
        }
        L.e(TAG, "========== onReceiveNewInvitation 处理完成 ==========");
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

    public CallDialog buildCallDialog(Context context,
                                  DialogInterface.OnDismissListener dismissListener,
                                  boolean isCallOut) {
        L.e(TAG, "========== buildCallDialog 开始 ==========");
        L.e(TAG, "Context: " + (context != null ? context.getClass().getSimpleName() : "null"));
        L.e(TAG, "isCallOut: " + isCallOut);
        L.e(TAG, "signalingInfo: " + (signalingInfo != null ? "valid" : "null"));
        
        try {
            if (callDialog != null) {
                L.e(TAG, "CallDialog已存在，返回现有实例");
                return callDialog;
            }
            
            if (context == null) {
                L.e(TAG, "❌ Context为null，无法创建Dialog");
                return null;
            }
            
            if (signalingInfo == null) {
                L.e(TAG, "❌ SignalingInfo为null，无法创建Dialog");
                return null;
            }
            
            // 🎯 重点修复：根据SignalingInfo判断通话类型
            boolean isGroupCall = isGroupCall(signalingInfo);
            L.e(TAG, "通话类型: " + (isGroupCall ? "群组通话" : "单人通话"));
            
            // 直接创建CallDialog，它已经支持群组通话
            L.e(TAG, "开始创建CallDialog...");
            callDialog = new CallDialog(context, this, isCallOut);
            L.e(TAG, "✅ CallDialog创建成功");
            
            L.e(TAG, "开始绑定数据...");
            callDialog.bindData(signalingInfo);
            L.e(TAG, "✅ 数据绑定成功");
            
            if (!callDialog.callingVM.isCallOut) {
                L.e(TAG, "设置被叫方监听器...");
                callDialog.setOnDismissListener(dialog -> {
                    isBeCalled = false;
                    if (null != dismissListener) dismissListener.onDismiss(dialog);
                });
                if (!Common.isScreenLocked() && Common.hasSystemAlertWindow()) {
                    callDialog.setOnShowListener(dialog -> ARouter.getInstance().build(Routes.Main.HOME).navigation());
                }
                L.e(TAG, "✅ 被叫方监听器设置成功");
            }
            
            L.e(TAG, "开始插入数据库记录...");
            insetDB();
            L.e(TAG, "✅ 数据库记录完成");
            
        } catch (Exception e) {
            L.e(TAG, "❌ buildCallDialog发生异常", e);
            L.e(TAG, "异常信息: " + e.getMessage());
            if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                L.e(TAG, "异常堆栈: " + e.getStackTrace()[0].toString());
            }
            callDialog = null; // 确保异常时清空
        }
        
        L.e(TAG, "========== buildCallDialog 结束，返回: " + (callDialog != null ? "valid" : "null") + " ==========");
        return callDialog;
    }

    /**
     * 判断是否为群组通话
     */
    private boolean isGroupCall(SignalingInfo signalingInfo) {
        if (signalingInfo == null || signalingInfo.getInvitation() == null) {
            return false;
        }
        
        // 通过SessionType判断
        int sessionType = signalingInfo.getInvitation().getSessionType();
        return sessionType == ConversationType.GROUP_CHAT;
    }

    @Override
    public void call(SignalingInfo signalingInfo) {
        if (isCallingTips()) return;
        setSignalingInfo(signalingInfo);

        buildCallDialog(getContext(), null, true);
        Common.UIHandler.post(() -> {
            callDialog.show();
        });
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
        if (null == callDialog) return;
        
        // 🔧 修复：移除isGroup判断，因为群组和单人都需要记录挂断时间
        callDialog.callingVM.renewalDB(callDialog.buildPrimaryKey(),
            (realm, callHistory) -> callHistory.setDuration((int)
                (System.currentTimeMillis() - callHistory.getDate())));
        dismissDialog();
    }

    private void insetDB() {
        // 🔧 修复：群组通话也需要记录历史，移除isGroup限制
        CallingVM callingVM = callDialog.callingVM;
        if (callingVM == null) return;
        
        List<String> ids = new ArrayList<>();
        if (callingVM.isCallOut) {
            // 主叫方记录被叫方信息
            List<String> inviteeIds = signalingInfo.getInvitation().getInviteeUserIDList();
            if (inviteeIds != null && !inviteeIds.isEmpty()) {
                if (isGroupCall(signalingInfo)) {
                    // 群组通话记录所有被邀请者
                    ids.addAll(inviteeIds);
                } else {
                    // 单人通话只记录第一个
                    ids.add(inviteeIds.get(0));
                }
            }
        } else {
            // 被叫方记录主叫方信息
            ids.add(signalingInfo.getInvitation().getInviterUserID());
        }

        if (ids.isEmpty()) return;

        boolean isCallOut = !callingVM.isCallOut;
        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
            @Override
            public void onError(int code, String error) {
                L.e(TAG, "获取用户信息失败: " + error);
            }

            @Override
            public void onSuccess(List<PublicUserInfo> data) {
                if (data.isEmpty() || null == callDialog) return;
                PublicUserInfo userInfo = data.get(0);
                BaseApp.inst().realm.executeTransactionAsync(realm -> {
                    if (null == callDialog) return;
                    CallHistory callHistory = new CallHistory(callDialog.buildPrimaryKey(),
                        userInfo.getUserID(), userInfo.getNickname(), userInfo.getFaceURL(),
                        signalingInfo.getInvitation().getMediaType(), 
                        isGroupCall(signalingInfo), 0, isCallOut,
                        System.currentTimeMillis(), 0);
                    realm.insert(callHistory);
                });
            }
        }, ids);
    }

    private void dismissDialog() {
        if (null == callDialog) return;
        Common.UIHandler.post(() -> {
            if (callDialog.isShowing())
                callDialog.dismiss();
            callDialog = null;
        });
    }

    public void dismissDialogSafely() {
        try {
            dismissDialog();
        } catch (Exception e) {
            L.e(TAG, "dismissDialog异常", e);
            callDialog = null;
        }
    }
}