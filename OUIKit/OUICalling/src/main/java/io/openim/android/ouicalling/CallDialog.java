package io.openim.android.ouicalling;


import android.content.Context;
import android.os.Handler;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;


import com.hjq.permissions.Permission;
import com.hjq.window.EasyWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.recyclerview.widget.GridLayoutManager;

import io.livekit.android.events.RoomEvent;
import io.livekit.android.room.participant.Participant;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.RemoteVideoTrack;
import io.openim.android.ouicalling.adapter.GroupMemberAdapter;
import io.openim.android.ouicalling.databinding.DialogCallBinding;
import io.openim.android.ouicalling.databinding.LayoutFloatViewBinding;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicalling.entity.StreamStatistics;
import io.openim.android.ouicalling.entity.PerformanceSummary;
import io.openim.android.ouicalling.utils.VideoResourcePool;
import io.openim.android.ouicalling.helper.GroupCallViewHelper;
import io.openim.android.ouicalling.state.DialogSwitchStateMachine;
import io.openim.android.ouicalling.vm.CallingVM;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseDialog;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.net.bage.GsonHel;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import io.openim.android.ouicore.utils.NotificationUtil;
import io.openim.android.ouicore.utils.Obs;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.enums.ConversationType;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.PublicUserInfo;
import io.openim.android.sdk.models.SignalingInfo;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;


public class CallDialog extends BaseDialog {

    protected final HasPermissions hasShoot, hasRecord, hasSystemAlert;
    protected Context context;
    private DialogCallBinding view;
    private GroupCallViewHelper groupViewHelper; // 群组通话视图助手
    private GroupMemberAdapter groupMemberAdapter; // 群组成员适配器
    private View groupView; // 群组通话视图
    private boolean isGroupCall = false; // 是否群组通话
    private Handler updateHandler; // 用于定时更新的Handler
    private Runnable updateTask; // 更新任务
    
    // === 延迟切换优化 ===
    private DialogSwitchStateMachine switchStateMachine;
    public CallingVM callingVM;
    protected SignalingInfo signalingInfo;

    protected EasyWindow easyWindow;
    protected LayoutFloatViewBinding floatViewBinding;
    private boolean isSubscribe;

    /**
     * 弹出通话界面
     *
     * @param context        上下文
     * @param callingService 通话服务
     * @param isCallOut      是否呼出
     */
    public CallDialog(@NonNull Context context, CallingService callingService, boolean isCallOut) {
        super(context);
        this.context = context;
        hasShoot = new HasPermissions(context, Permission.CAMERA, Permission.RECORD_AUDIO);
        hasRecord = new HasPermissions(context, Permission.RECORD_AUDIO);
        hasSystemAlert = new HasPermissions(context, Permission.SYSTEM_ALERT_WINDOW);

        callingVM = new CallingVM(callingService, isCallOut);
        callingVM.setDismissListener(v -> {
            dismiss();
        });
        callingVM.callViewModel.subscribe(callingVM.callViewModel.getRoom().getEvents().getEvents(), (v) -> {
            if (v instanceof RoomEvent.ParticipantDisconnected
                && v.getRoom().getRemoteParticipants().size() == 0) {
                //当只有1个人时关闭会议
                dismiss();
            }
            return null;
        }, callingVM.scope);

        initSwitchStateMachine();
        initView();
        initRendererView();
    }
    
    /**
     * 初始化延迟切换状态机
     */
    private void initSwitchStateMachine() {
        switchStateMachine = new DialogSwitchStateMachine();
        switchStateMachine.setListener(new DialogSwitchStateMachine.SwitchStateListener() {
            @Override
            public void onReadyToSwitch() {
                L.critical("CallDialog", "数据就绪，开始切换UI");
                performGroupUISwitch();
            }
            
            @Override
            public void onSwitchCompleted() {
                L.businessFlow("CallDialog", "群组UI切换", "切换完成");
                // ✅ 简化：专注核心切换完成逻辑
            }
            
            @Override
            public void onSwitchFailed(String reason) {
                LogExceptionHandler.handleException("CallDialog", "群组UI切换失败", LogExceptionHandler.ExceptionType.UI_ERROR, null);
                L.w("CallDialog", "切换失败原因: " + reason + ", 回退到单人模式");
                Toast.makeText(context, "群组通话加载失败，使用单人模式", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onStateChanged(DialogSwitchStateMachine.SwitchState oldState, DialogSwitchStateMachine.SwitchState newState) {
                L.stateChange("CallDialog", oldState.getDescription(), newState.getDescription());
            }
        });
    }
    
    /**
     * 启动延迟群组切换流程
     */
    private void startDelayedGroupSwitch() {
        L.businessFlow("CallDialog", "延迟群组切换", "流程开始");
        
        // 启动状态机
        switchStateMachine.startSwitch();
        
        // 通知信令数据就绪（已经有了）
        switchStateMachine.notifySignalingReady();
        
        // UI已经准备就绪（对话框已经显示）
        switchStateMachine.notifyUIReady();
        
        // 异步加载成员数据
        loadGroupMembersAsync();
        
        // 启动超时检查
        startSwitchTimeoutCheck();
    }
    
    /**
     * 异步加载群组成员数据
     */
    private void loadGroupMembersAsync() {
        L.d("CallDialog", "开始异步加载群组成员数据");
        
        // 模拟异步加载过程
        new Handler().postDelayed(() -> {
            try {
                // 检查是否已经取消或失败
                if (switchStateMachine.getCurrentState() == DialogSwitchStateMachine.SwitchState.FAILED) {
                    L.w("CallDialog", "切换已失败，取消成员数据加载");
                    return;
                }
                
                // 模拟成员数据加载（实际应该是从 CallingVM 获取）
                int memberCount = callingVM.getParticipantCount();
                
                if (memberCount > 1) {
                    L.businessFlow("CallDialog", "成员数据加载", "完成，成员数: " + memberCount);
                    switchStateMachine.notifyMembersReady();
                } else {
                    L.w("CallDialog", "成员数不足，不适合群组模式: " + memberCount);
                    switchStateMachine.notifySwitchFailed("成员数不足");
                }
                
            } catch (Exception e) {
                LogExceptionHandler.handleException("CallDialog", "加载群组成员数据", LogExceptionHandler.ExceptionType.DATA_ERROR, e);
                switchStateMachine.notifySwitchFailed("成员数据加载失败: " + e.getMessage());
            }
        }, 500); // 模拟500ms加载时间
    }
    
    /**
     * 执行群组UI切换（数据就绪后调用）
     */
    private void performGroupUISwitch() {
        try {
            android.util.Log.d("CallDialog", "开始执行UI切换到群组模式");
            
            // 调用原有的切换逻辑
            switchToGroupCallModeInternal();
            
            // 通知状态机切换完成
            switchStateMachine.notifySwitchCompleted();
            
        } catch (Exception e) {
            android.util.Log.e("CallDialog", "UI切换失败", e);
            switchStateMachine.notifySwitchFailed("UI切换异常: " + e.getMessage());
        }
    }
    
    /**
     * 启动超时检查
     */
    private void startSwitchTimeoutCheck() {
        new Handler().postDelayed(() -> {
            if (switchStateMachine.checkTimeout()) {
                android.util.Log.w("CallDialog", "群组切换超时，回退到单人模式");
            }
        }, 6000); // 6秒超时
    }

    public void initRendererView() {
        if (isGroupCall) {
            // 群组通话模式：初始化本地视频和群组成员网格
            if (groupViewHelper != null && groupViewHelper.localSpeakerVideoView != null) {
                callingVM.initLocalSpeakerVideoView(groupViewHelper.localSpeakerVideoView);
            }
        } else {
            // 单人通话模式：使用原有逻辑
            callingVM.initLocalSpeakerVideoView(view.localSpeakerVideoView);
            callingVM.initRemoteVideoRenderer(view.remoteSpeakerVideoView,
                view.remoteSpeakerVideoView2, floatViewBinding.shrinkRemoteSpeakerVideoView);
        }
    }

    private void initView() {
        floatViewBinding = LayoutFloatViewBinding.inflate(getLayoutInflater());
        Window window = getWindow();
        
        // 先初始化单人通话布局，稍后根据实际情况切换
        view = DialogCallBinding.inflate(getLayoutInflater());
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(view.getRoot());
        
        //背景状态栏透明
        window.setDimAmount(1f);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Common.addTypeSystemAlert(params);
        window.setAttributes(params);

        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        view.zoomOut.setVisibility(Common.isScreenLocked() ? View.GONE : View.VISIBLE);
    }
    
    /**
     * 切换到群组通话模式（内部实现）
     * ✅ 重命名以区分新旧实现
     */
    private void switchToGroupCallModeInternal() {
        L.d("CallDialog", "switchToGroupCallModeInternal() 调用，当前isGroupCall = " + isGroupCall);
        
        // 🔥 修夏：检查是否已经切换了UI，而不是状态
        if (groupView != null && groupViewHelper != null) {
            L.d("CallDialog", "群组UI已经初始化，跳过切换");
            return; // 已经切换过UI
        }
        
        L.businessFlow("CallDialog", "群组模式切换", "开始切换...");
        groupView = getLayoutInflater().inflate(R.layout.dialog_group_call, null);
        groupViewHelper = new GroupCallViewHelper(groupView);
        setContentView(groupView);
        L.d("CallDialog", "群组布局加载完成，设置为内容视图");
        
        // 重新设置窗口属性
        Window window = getWindow();
        window.setDimAmount(1f);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        
        Common.addTypeSystemAlert(params);
        window.setAttributes(params);
        
        if (groupViewHelper.zoomOut != null) {
            groupViewHelper.zoomOut.setVisibility(Common.isScreenLocked() ? View.GONE : View.VISIBLE);
        }
        
        // 初始化群组成员网格布局
        initGroupMemberGrid();
    }
    
    /**
     * 初始化群组成员网格布局
     */
    private void initGroupMemberGrid() {
        if (groupViewHelper == null) return;
        
        // 创建群组成员适配器
        groupMemberAdapter = new GroupMemberAdapter(context, callingVM.getResourcePool(), callingVM.callViewModel);
        
        // 设置网格布局管理器 - 默认1x1，会根据成员数量动态调整
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 1);
        if (groupViewHelper.viewRenderers != null) {
            groupViewHelper.viewRenderers.setLayoutManager(gridLayoutManager);
            groupViewHelper.viewRenderers.setAdapter(groupMemberAdapter);
            // 设置RecyclerView引用用于视频绑定刷新
            groupMemberAdapter.setRecyclerView(groupViewHelper.viewRenderers);
        }
        
        // 监听群组成员变化
        observeGroupMembers();
    }
    
    /**
     * 监听群组成员变化
     */
    private void observeGroupMembers() {
        if (groupMemberAdapter == null || !isGroupCall) return;
        
        // 初始化定时更新机制
        setupGroupMemberUpdateTask();
        
        // 立即更新一次
        updateGroupMemberGrid();
    }
    
    /**
     * 更新群组成员网格布局
     */
    private void updateGroupMemberGrid() {
        if (groupMemberAdapter == null || groupView == null || !isGroupCall) return;
        
        List<GroupCallMember> members = new ArrayList<>(callingVM.groupMembers);
        int memberCount = members.size();
        
        // 根据成员数量动态调整网格布局
        if (groupViewHelper != null && groupViewHelper.viewRenderers != null) {
            GridLayoutManager gridLayoutManager = (GridLayoutManager) groupViewHelper.viewRenderers.getLayoutManager();
            if (gridLayoutManager != null) {
                int spanCount = calculateGridSpanCount(memberCount);
                gridLayoutManager.setSpanCount(spanCount);
            }
        }
        
        // 更新适配器数据
        groupMemberAdapter.updateMembers(members);
        
        // 刷新视频绑定状态
        refreshGroupVideoBindings();
    }
    
    /**
     * 刷新群组视频绑定状态
     * Week 2 Day 6: 简化，由MultiStreamManager自动处理
     */
    private void refreshGroupVideoBindings() {
        if (!isGroupCall || groupMemberAdapter == null || callingVM.callViewModel == null) return;
        
        try {
            // Week 2 Day 6: MultiStreamManager会自动处理视频流的优先级和绑定
            // 不需要手动绑定，只需要通知适配器更新状态
            
            // 获取当前流统计信息用于日志
            if (callingVM.callViewModel.getAllGroupParticipants() != null) {
                StreamStatistics streamStats = callingVM.callViewModel.getStreamStatistics();
                
                L.d("CallDialog", "refreshGroupVideoBindings: 群组视频绑定已刷新");
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallDialog", "刷新群组视频绑定", LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    // ✅ 简化：移除复杂的性能监控方法，专注核心功能实现
    
    /**
     * 根据成员数量计算网格列数
     */
    private int calculateGridSpanCount(int memberCount) {
        if (memberCount <= 1) return 1;
        if (memberCount <= 4) return 2;
        if (memberCount <= 9) return 3;
        return 3; // 最多9宫格
    }
    
    /**
     * 设置群组成员更新任务
     */
    private void setupGroupMemberUpdateTask() {
        if (updateHandler == null) {
            updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        // 清理之前的任务
        clearUpdateTask();
        
        // ✅ 简化：专注核心成员更新功能
        
        updateTask = new Runnable() {
            @Override
            public void run() {
                if (isGroupCall && groupMemberAdapter != null && !isFinishing()) {
                    updateGroupMemberGrid();
                    updateHandler.postDelayed(this, 1000); // 每秒检查一次更新
                }
            }
        };
        
        updateHandler.post(updateTask);
    }
    
    /**
     * 清理更新任务防止内存泄漏
     */
    private void clearUpdateTask() {
        if (updateHandler != null && updateTask != null) {
            updateHandler.removeCallbacks(updateTask);
            updateTask = null;
        }
        
        // ✅ 简化：专注核心任务清理功能
    }
    
    /**
     * 检查Dialog是否即将关闭
     */
    private boolean isFinishing() {
        return !isShowing() || context == null;
    }


    //    收起/展开
    public void shrink(boolean isShrink) {
        if (isShrink) {
            showFloatView();
        } else if (null != easyWindow) {
            easyWindow.cancel();
        }
        view.home.setVisibility(isShrink ? View.GONE : View.VISIBLE);
        getWindow().setDimAmount(isShrink ? 0f : 1f);

        if (callingVM.isStartCall) {
            floatViewBinding.sTips.setText(io.openim.android.ouicore.R.string.calling);
        } else {
            floatViewBinding.sTips.setText(callingVM.isCallOut ?
                context.getString(io.openim.android.ouicore.R.string.waiting_tips2) :
                context.getString(io.openim.android.ouicore.R.string.waiting_tips3));
        }
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = isShrink ? ViewGroup.LayoutParams.WRAP_CONTENT :
            ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = isShrink ? ViewGroup.LayoutParams.WRAP_CONTENT :
            ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = isShrink ? (Gravity.TOP | Gravity.END) : Gravity.CENTER;
        getWindow().setAttributes(params);
    }

    protected void showFloatView() {
        // 传入 Activity 对象表示设置成局部的，不需要有悬浮窗权限
        // 传入 Application 对象表示设置成全局的，但需要有悬浮窗权限
        if (null == easyWindow) {
            easyWindow =
                new EasyWindow<>(BaseApp.inst()).setContentView(floatViewBinding.getRoot()).setGravity(Gravity.END | Gravity.TOP)
                    // 设置成可拖拽的
                    .setDraggable();
            floatViewBinding.shrink.setOnClickListener(v -> shrink(false));
        }
        if (!easyWindow.isShowing()) easyWindow.show();
    }

    public void bindData(SignalingInfo signalingInfo) {
        this.signalingInfo = signalingInfo;
        // 🔍 关键调试：记录信令信息
        L.businessFlow("CallDialog", "bindData", "开始绑定数据");
        L.d("CallDialog", "SessionType: " + L.safeToString(signalingInfo.getInvitation().getSessionType()));
        L.d("CallDialog", "GroupID: " + L.safeToString(signalingInfo.getInvitation().getGroupID()));
        L.d("CallDialog", "InviteeList: " + L.safeToString(signalingInfo.getInvitation().getInviteeUserIDList()));
        
        // ✅ 使用统一状态管理，不再直接设置 isGroup
        // 更新信令信息到状态管理器
        callingVM.updateSignalingInfo(signalingInfo);
        
        boolean isGroupFromState = callingVM.isGroupCall();
        L.d("CallDialog", "计算 isGroupCall = " + isGroupFromState + ", stateManager: " + callingVM.getCallTypeDescription());
        
        // 🔥 关键修复：更新CallDialog的isGroupCall字段
        this.isGroupCall = isGroupFromState;
        L.critical("CallDialog", "isGroupCall字段已更新: " + this.isGroupCall);
        
        // 检测是否为群组通话并启动延迟切换流程
        if (isGroupFromState) {
            L.businessFlow("CallDialog", "群组通话检测", "启动延迟切换流程");
            startDelayedGroupSwitch();
        } else {
            L.d("CallDialog", "保持单人通话模式");
        }
        
        callingVM.setVideoCalls(Constants.MediaType.VIDEO.equals(signalingInfo.getInvitation().getMediaType()));
        // 根据通话模式设置控件可见性
        if (isGroupCall && groupViewHelper != null) {
            // 群组通话模式
            if (groupViewHelper.cameraControl != null) {
                groupViewHelper.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
            }
            if (!callingVM.isVideoCalls) {
                callingVM.callViewModel.setCameraEnabled(false);
                if (groupViewHelper.localSpeakerVideoView != null) {
                    groupViewHelper.localSpeakerVideoView.setVisibility(View.GONE);
                }
                if (groupViewHelper.timeTv != null) {
                    groupViewHelper.timeTv.setVisibility(View.GONE);
                }
                if (groupViewHelper.headTips != null) {
                    groupViewHelper.headTips.setVisibility(View.GONE);
                }
            }
        } else if (view != null) {
            // 单人通话模式
            view.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
            if (!callingVM.isVideoCalls) {
                callingVM.callViewModel.setCameraEnabled(false);
                view.localSpeakerVideoView.setVisibility(View.GONE);
                view.timeTv.setVisibility(View.GONE);
                view.headTips.setVisibility(View.GONE);
                view.audioCall.setVisibility(View.VISIBLE);
            }
        }
        
        // 根据通话模式设置控件默认状态
        if (isGroupCall && groupViewHelper != null) {
            // 群组通话模式
            if (groupViewHelper.micIsOn != null) {
                groupViewHelper.micIsOn.setChecked(true);
            }
            if (groupViewHelper.speakerIsOn != null) {
                groupViewHelper.speakerIsOn.setChecked(true);
            }
            if (callingVM.isCallOut) {
                if (groupViewHelper.callingMenu != null) {
                    groupViewHelper.callingMenu.setVisibility(View.VISIBLE);
                }
                if (groupViewHelper.ask != null) {
                    groupViewHelper.ask.setVisibility(View.GONE);
                }
            } else {
                if (groupViewHelper.callingMenu != null) {
                    groupViewHelper.callingMenu.setVisibility(View.GONE);
                }
                if (groupViewHelper.ask != null) {
                    groupViewHelper.ask.setVisibility(View.VISIBLE);
                }
            }
        } else if (view != null) {
            // 单人通话模式
            view.micIsOn.setChecked(true);
            view.speakerIsOn.setChecked(true);
            if (callingVM.isCallOut) {
                view.callingMenu.setVisibility(View.VISIBLE);
                view.ask.setVisibility(View.GONE);
                
                view.callingTips.setText(context.getString(io.openim.android.ouicore.R.string.waiting_tips) + "...");
                view.callingTips2.setText(context.getString(io.openim.android.ouicore.R.string.waiting_tips) + "...");
            } else {
                view.callingMenu.setVisibility(View.GONE);
                view.ask.setVisibility(View.VISIBLE);
            }
        }
        
        // 发起信令通话
        if (callingVM.isCallOut) {
            callingVM.signalingInvite(signalingInfo);
        }
        bindUserInfo(signalingInfo);
        listener(signalingInfo);
    }

    /**
     * 绑定用户信息
     */
    public void bindUserInfo(SignalingInfo signalingInfo) {
        try {
            if (isGroupCall) {
                // 群组通话模式：显示群组信息和邀请者信息
                bindGroupCallUserInfo(signalingInfo);
            } else {
                // 单人通话模式：使用原有逻辑
                bindSingleCallUserInfo(signalingInfo);
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallDialog", "绑定用户信息", LogExceptionHandler.ExceptionType.DATA_ERROR, e);
        }
    }
    
    /**
     * 单人通话用户信息绑定
     */
    private void bindSingleCallUserInfo(SignalingInfo signalingInfo) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(callingVM.isCallOut ?
            signalingInfo.getInvitation().getInviteeUserIDList().get(0) :
            signalingInfo.getInvitation().getInviterUserID());

        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
            @Override
            public void onError(int code, String error) {
                LogExceptionHandler.handleException("CallDialog", "获取用户信息失败", LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                L.e("CallDialog", "获取用户信息失败: " + error + ", code: " + code);
                Toast.makeText(context, error + code, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<PublicUserInfo> data) {
                if (data.isEmpty() || view == null) return;
                PublicUserInfo userInfo = data.get(0);
                view.avatar.load(userInfo.getFaceURL());
                floatViewBinding.sAvatar.load(userInfo.getFaceURL(), userInfo.getNickname());
                view.name.setText(userInfo.getNickname());

                //audio call
                if (view.avatar2 != null) view.avatar2.load(userInfo.getFaceURL());
                if (view.name2 != null) view.name2.setText(userInfo.getNickname());
            }
        }, ids);
    }
    
    /**
     * 群组通话用户信息绑定
     */
    private void bindGroupCallUserInfo(SignalingInfo signalingInfo) {
        // 获取邀请者信息
        String inviterUserId = signalingInfo.getInvitation().getInviterUserID();
        ArrayList<String> ids = new ArrayList<>();
        ids.add(inviterUserId);

        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
            @Override
            public void onError(int code, String error) {
                LogExceptionHandler.handleException("CallDialog", "获取群组通话用户信息失败", LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                L.e("CallDialog", "获取群组通话用户信息失败: " + error + ", code: " + code);
                Toast.makeText(context, error + code, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<PublicUserInfo> data) {
                if (data.isEmpty() || groupViewHelper == null) return;
                PublicUserInfo inviterInfo = data.get(0);
                
                // 设置邀请者头像和信息
                if (groupViewHelper.avatar != null) {
                    groupViewHelper.avatar.load(inviterInfo.getFaceURL());
                }
                if (floatViewBinding != null && floatViewBinding.sAvatar != null) {
                    floatViewBinding.sAvatar.load(inviterInfo.getFaceURL(), inviterInfo.getNickname());
                }
                
                // 设置群组通话提示信息
                if (groupViewHelper.tips1 != null) {
                    groupViewHelper.tips1.setText(inviterInfo.getNickname() + " 邀请您加入群组通话");
                }
                if (groupViewHelper.tips2 != null) {
                    String mediaType = callingVM.isVideoCalls ? "视频通话" : "音频通话";
                    groupViewHelper.tips2.setText("群组" + mediaType);
                }
            }
        }, ids);
    }

    public final Observer<String> bindTime = new Observer<String>() {
        @Override
        public void onChanged(String s) {
            if (TextUtils.isEmpty(s)) return;
            
            if (isGroupCall && groupViewHelper != null) {
                // 群组通话模式
                if (groupViewHelper.timeTv != null) {
                    groupViewHelper.timeTv.setText(s);
                }
            } else if (view != null) {
                // 单人通话模式
                if (view.timeTv != null) {
                    view.timeTv.setText(s);
                }
                if (view.callingTips2 != null) {
                    view.callingTips2.setText(s);
                }
            }
        }
    };

    public void listener(SignalingInfo signalingInfo) {
        // 监听远程参与者变化
        if (isGroupCall) {
            // 群组模式：监听多个参与者的视频状态变化
            callingVM.callViewModel.subscribe(callingVM.callViewModel.getRemoteParticipants(), (v) -> {
                // 群组成员视频状态变化由GroupMemberAdapter处理
                updateGroupMemberGrid();
                return null;
            }, callingVM.scope);
        } else {
            // 单人模式：使用原有逻辑
            callingVM.callViewModel.subscribe(callingVM.callViewModel.getRemoteParticipants(), (v) -> {
                if (isSubscribe || view == null) return null;
                Object[] toArray = v.toArray();
                if (toArray.length == 0) return null;
                callingVM.callViewModel.subscribe(((RemoteParticipant) toArray[0]).getEvents().getEvents(), (event) -> {
                    isSubscribe = true;
                    if (view != null && view.remoteSpeakerVideoView != null) {
                        view.remoteSpeakerVideoView.setVisibility(event.getParticipant().isCameraEnabled() ? View.VISIBLE : View.GONE);
                    }
                    return null;
                }, callingVM.scope);
                return null;
            }, callingVM.scope);
        }

        // 通话时间监听
        callingVM.timeStr.observeForever(bindTime);
        
        // 根据通话模式绑定事件监听器
        if (isGroupCall && groupView != null) {
            bindGroupCallListeners(signalingInfo);
        } else if (view != null) {
            bindSingleCallListeners(signalingInfo);
        }
    }
    
    /**
     * 绑定单人通话事件监听器
     */
    private void bindSingleCallListeners(SignalingInfo signalingInfo) {
        if (view == null) return;
        
        // 摄像头控制
        if (view.closeCamera != null) {
            view.closeCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
                hasShoot.safeGo(() -> {
                    boolean isEnabled = !isChecked;
                    callingVM.callViewModel.setCameraEnabled(isEnabled);
                    if (view.localSpeakerVideoView != null) {
                        view.localSpeakerVideoView.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                    }
                });
            });
        }
        
        if (view.switchCamera != null) {
            view.switchCamera.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    callingVM.callViewModel.flipCamera();
                }
            });
        }
        
        // 麦克风控制
        if (view.micIsOn != null) {
            view.micIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    view.micIsOn.setText(view.micIsOn.isChecked() ?
                        context.getString(io.openim.android.ouicore.R.string.microphone_on) :
                        context.getString(io.openim.android.ouicore.R.string.microphone_off));
                    //关闭麦克风
                    callingVM.callViewModel.setMicEnabled(view.micIsOn.isChecked());
                }
            });
        }

        // 扬声器控制
        if (view.speakerIsOn != null) {
            view.speakerIsOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
                view.speakerIsOn.setText(isChecked ?
                    context.getString(io.openim.android.ouicore.R.string.speaker_on) :
                    context.getString(io.openim.android.ouicore.R.string.speaker_off));
                // 打开扬声器
                callingVM.setSpeakerphoneOn(isChecked);
            });
        }

        // 通话控制按钮 - 迁移到统一方案
        if (view.hangUp != null) {
            view.hangUp.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    callingVM.renewalDB(callingVM.buildPrimaryKey(signalingInfo), (realm,
                                                                                   callHistory) -> callHistory.setDuration((int) (System.currentTimeMillis() - callHistory.getDate())));
                    // ✅ 迁移：使用统一的挂断接口
                    callingVM.hangup(signalingInfo);
                }
            });
        }
        
        if (view.reject != null) {
            view.reject.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    // ✅ 迁移：使用统一的挂断接口（拒接也是挂断的一种）
                    callingVM.hangup(signalingInfo);
                }
            });
        }
        
        if (view.answer != null) {
            view.answer.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    answerClick(signalingInfo);
                }
            });
        }
        
        if (view.zoomOut != null) {
            view.zoomOut.setOnClickListener(v -> {
                zoomOutClick();
            });
        }
        
        if (view.shrink != null) {
            view.shrink.setOnClickListener(v -> {
                shrink(false);
            });
        }
        
        // 本地视频点击切换
        if (view.localSpeakerVideoView != null) {
            view.localSpeakerVideoView.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    if (view.remoteSpeakerVideoView == null) return;
                    Object object = view.remoteSpeakerVideoView.getTag();
                    if (null != object) {
                        Participant participant = object instanceof RemoteVideoTrack
                            ? (Participant) callingVM.callViewModel.getRoom().getLocalParticipant()
                            : (Participant) callingVM.callViewModel.getSingleRemotePar();
                        Participant participant2 = object instanceof RemoteVideoTrack
                            ? (Participant) callingVM.callViewModel.getSingleRemotePar()
                            : (Participant) callingVM.callViewModel.getRoom().getLocalParticipant();
                        if (null == participant2 || null == participant) return;

                        callingVM.callViewModel.bindRemoteViewRenderer(view.localSpeakerVideoView,
                            participant2, callingVM.scope, new Continuation<Unit>() {
                                @NonNull
                                @Override
                                public CoroutineContext getContext() {
                                    return null;
                                }

                                @Override
                                public void resumeWith(@NonNull Object o) {

                                }
                            });
                        callingVM.callViewModel.bindRemoteViewRenderer(view.remoteSpeakerVideoView,
                            participant, callingVM.scope, new Continuation<Unit>() {
                                @NonNull
                                @Override
                                public CoroutineContext getContext() {
                                    return null;
                                }

                                @Override
                                public void resumeWith(@NonNull Object o) {

                                }
                            });
                    }
                }
            });
        }
    }
    
    /**
     * 绑定群组通话事件监听器
     */
    private void bindGroupCallListeners(SignalingInfo signalingInfo) {
        if (groupViewHelper == null) return;
        
        // 摄像头控制
        if (groupViewHelper.closeCamera != null) {
            groupViewHelper.closeCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
                hasShoot.safeGo(() -> {
                    boolean isEnabled = !isChecked;
                    callingVM.callViewModel.setCameraEnabled(isEnabled);
                    if (groupViewHelper.localSpeakerVideoView != null) {
                        groupViewHelper.localSpeakerVideoView.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                    }
                });
            });
        }
        
        if (groupViewHelper.switchCamera != null) {
            groupViewHelper.switchCamera.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    callingVM.callViewModel.flipCamera();
                }
            });
        }
        
        // 麦克风控制
        if (groupViewHelper.micIsOn != null) {
            groupViewHelper.micIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    groupViewHelper.micIsOn.setText(groupViewHelper.micIsOn.isChecked() ?
                        context.getString(io.openim.android.ouicore.R.string.microphone_on) :
                        context.getString(io.openim.android.ouicore.R.string.microphone_off));
                    //关闭麦克风
                    callingVM.callViewModel.setMicEnabled(groupViewHelper.micIsOn.isChecked());
                }
            });
        }

        // 扬声器控制
        if (groupViewHelper.speakerIsOn != null) {
            groupViewHelper.speakerIsOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
                groupViewHelper.speakerIsOn.setText(isChecked ?
                    context.getString(io.openim.android.ouicore.R.string.speaker_on) :
                    context.getString(io.openim.android.ouicore.R.string.speaker_off));
                // 打开扬声器
                callingVM.setSpeakerphoneOn(isChecked);
            });
        }

        // 通话控制按钮 - 迁移到统一方案
        if (groupViewHelper.hangUp != null) {
            groupViewHelper.hangUp.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    callingVM.renewalDB(callingVM.buildPrimaryKey(signalingInfo), (realm,
                                                                                   callHistory) -> callHistory.setDuration((int) (System.currentTimeMillis() - callHistory.getDate())));
                    // ✅ 迁移：使用统一的挂断接口，支持群组通话特殊逻辑
                    callingVM.hangup(signalingInfo);
                }
            });
        }
        
        if (groupViewHelper.reject != null) {
            groupViewHelper.reject.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    // ✅ 迁移：使用统一的挂断接口（群组拒接也是挂断的一种）
                    callingVM.hangup(signalingInfo);
                }
            });
        }
        
        if (groupViewHelper.answer != null) {
            groupViewHelper.answer.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    answerClick(signalingInfo);
                }
            });
        }
        
        if (groupViewHelper.zoomOut != null) {
            groupViewHelper.zoomOut.setOnClickListener(v -> {
                zoomOutClick();
            });
        }
    }

    public void zoomOutClick() {
        hasSystemAlert.safeGo(() -> shrink(true));
    }

    public void answerClick(SignalingInfo signalingInfo) {
        if (callingVM.isVideoCalls) {
            hasShoot.safeGo(() -> signalingAccept(signalingInfo));
        } else {
            hasRecord.safeGo(() -> signalingAccept(signalingInfo));
        }
    }

    public void signalingAccept(SignalingInfo signalingInfo) {
        callingVM.signalingAccept(signalingInfo, new OnBase() {
            @Override
            public void onError(int code, String error) {
            }

            @Override
            public void onSuccess(Object data) {
                changeView();

                callingVM.renewalDB(callingVM.buildPrimaryKey(signalingInfo),
                    (realm, v1) -> v1.setSuccess(true));
            }
        });
    }


    public void changeView() {
        if (isGroupCall && groupViewHelper != null) {
            // 群组通话模式
            if (groupViewHelper.headTips != null) groupViewHelper.headTips.setVisibility(View.GONE);
            if (groupViewHelper.ask != null) groupViewHelper.ask.setVisibility(View.GONE);
            if (groupViewHelper.callingMenu != null) groupViewHelper.callingMenu.setVisibility(View.VISIBLE);
            if (groupViewHelper.cameraControl != null) {
                groupViewHelper.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
            }
        } else if (view != null) {
            // 单人通话模式
            if (view.headTips != null) view.headTips.setVisibility(View.GONE);
            if (view.ask != null) view.ask.setVisibility(View.GONE);
            if (view.callingMenu != null) view.callingMenu.setVisibility(View.VISIBLE);
            if (view.cameraControl != null) {
                view.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
            }
        }

        waitingHandle();
    }


    @Override
    public void show() {
        playRingtone();
        super.show();
    }

    public void playRingtone() {
        try {
            Common.wakeUp(context);
//           Ringtone铃声
            if (!MediaPlayerUtil.INSTANCE.isPlaying()) {
                MediaPlayerUtil.INSTANCE.initMedia(BaseApp.inst(), R.raw.incoming_call_ring);
                MediaPlayerUtil.INSTANCE.loopPlay();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dismiss() {
        try {
            // 清理定时任务防止内存泄漏
            clearUpdateTask();
            
            if (null != easyWindow) {
                easyWindow.cancel();
            }
            insertChatHistory();
            MediaPlayerUtil.INSTANCE.pause();
            MediaPlayerUtil.INSTANCE.release();
            callingVM.setSpeakerphoneOn(true);
            callingVM.timeStr.removeObserver(bindTime);
            videoViewRelease();
            callingVM.unBindView();
            super.dismiss();
            ((CallingServiceImp) callingVM.callingService).callDialog = null;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void videoViewRelease() {
        // 释放视频资源
        if (isGroupCall) {
            // 群组模式：释放群组视频资源
            if (groupViewHelper != null && groupViewHelper.localSpeakerVideoView != null) {
                groupViewHelper.localSpeakerVideoView.release();
            }
                // 释放群组成员适配器中的视频资源
            if (groupMemberAdapter != null) {
                groupMemberAdapter.releaseAllVideoRenderers();
            }
            // Week 2 Day 6: 通过Manager层清理视频资源，遵循信号驱动架构
            callingVM.cleanupGroupVideoResources();
            // 清理定时任务
            clearUpdateTask();
        } else {
            // 单人模式：使用原有逻辑
            if (view != null) {
                if (view.localSpeakerVideoView != null) view.localSpeakerVideoView.release();
                if (view.remoteSpeakerVideoView != null) view.remoteSpeakerVideoView.release();
                if (view.remoteSpeakerVideoView2 != null) view.remoteSpeakerVideoView2.release();
            }
        }
        
        // 释放浮窗视频资源
        if (floatViewBinding != null && floatViewBinding.shrinkRemoteSpeakerVideoView != null) {
            floatViewBinding.shrinkRemoteSpeakerVideoView.release();
        }
    }

    private void insertChatHistory() {
        boolean isGroup = callingVM.isGroupCall(); // ✅ 使用统一状态管理
        if (!isShowing() || isGroup || (null != signalingInfo && TextUtils.isEmpty(callingVM.buildPrimaryKey(signalingInfo))))
            return;
        String id = callingVM.buildPrimaryKey(signalingInfo);
        String senderID = isGroup ? BaseApp.inst().loginCertificate.userID :
            signalingInfo.getInvitation().getInviterUserID();
        String receiver = signalingInfo.getInvitation().getInviteeUserIDList().get(0);

        callingVM.renewalDB(id, (realm, callHistory) -> {
            callHistory = realm.copyFromRealm(callHistory);
            try {
                callHistory.setDuration((int)(System.currentTimeMillis() - callHistory.getDate()));
            } catch (Exception e){}

            HashMap<String, Object> map = new HashMap<>();
            map.put(Constants.K_CUSTOM_TYPE, Constants.MsgType.LOCAL_CALL_HISTORY);
            map.put(Constants.K_DATA, callHistory);

            String data = GsonHel.toJson(map);
            Message message = OpenIMClient.getInstance().messageManager.createCustomMessage(data,
                "", "");
            message.setRead(true);
            OpenIMClient.getInstance().messageManager.insertSingleMessageToLocalStorage(new IMUtil.IMCallBack<String>() {
                @Override
                public void onSuccess(String data) {
                    Obs.newMessage(Constants.Event.INSERT_MSG);
                }
            }, message, receiver, senderID);
        });
    }


    public void otherSideAccepted() {
        callingVM.isStartCall = true;
        callingVM.buildTimer();
        
        // 隐藏头部提示
        if (isGroupCall && groupViewHelper != null) {
            if (groupViewHelper.headTips != null) {
                groupViewHelper.headTips.setVisibility(View.GONE);
            }
        } else if (view != null) {
            if (view.headTips != null) {
                view.headTips.setVisibility(View.GONE);
            }
        }
        
        // 停止铃声
        MediaPlayerUtil.INSTANCE.pause();
        MediaPlayerUtil.INSTANCE.release();

        waitingHandle();
    }

    public String buildPrimaryKey() {
        return CallingVM.buildPrimaryKey(signalingInfo);
    }

    private void waitingHandle() {
        if (callingVM.isVideoCalls) floatViewBinding.waiting.setVisibility(View.GONE);

        if (callingVM.isStartCall) {
            floatViewBinding.sTips.setText(io.openim.android.ouicore.R.string.calling);
        } else {
            floatViewBinding.sTips.setText(callingVM.isCallOut ?
                context.getString(io.openim.android.ouicore.R.string.waiting_tips2) :
                context.getString(io.openim.android.ouicore.R.string.waiting_tips3));
        }
    }
}
