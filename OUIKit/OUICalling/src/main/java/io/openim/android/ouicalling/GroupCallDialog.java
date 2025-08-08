package io.openim.android.ouicalling;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.ouicalling.adapter.GroupMemberAdapter;
import io.openim.android.ouicalling.databinding.DialogGroupCallBinding;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.callback.OnBase;
import io.openim.android.sdk.models.GroupInfo;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 群组通话对话框
 * 专门处理群组音视频通话，使用dialog_group_call.xml布局，直接显示九宫格界面
 */
public class GroupCallDialog extends BaseCallDialog {
    
    private DialogGroupCallBinding groupView;
    private GroupMemberAdapter groupMemberAdapter;
    private android.os.Handler updateHandler;
    private Runnable updateTask;
    
    public GroupCallDialog(@NonNull Context context, CallingService callingService, boolean isCallOut) {
        super(context, callingService, isCallOut);
        L.businessFlow("GroupCallDialog", "群组通话对话框创建", "直接显示九宫格界面");
    }
    
    @Override
    protected void initSpecificView() {
        // 直接使用群组通话布局 - 这是关键修复点
        groupView = DialogGroupCallBinding.inflate(getLayoutInflater());
        setContentView(groupView.getRoot());
        
        // 设置通用UI属性
        if (groupView.zoomOut != null) {
            groupView.zoomOut.setVisibility(Common.isScreenLocked() ? View.GONE : View.VISIBLE);
        }
        
        // 初始化群组成员网格布局
        initGroupMemberGrid();
        
        L.businessFlow("GroupCallDialog", "九宫格UI初始化", "完成");
    }
    
    /**
     * 初始化群组成员网格布局
     */
    private void initGroupMemberGrid() {
        if (groupView.viewRenderers == null) {
            L.w("GroupCallDialog", "viewRenderers为null，无法初始化网格布局");
            return;
        }
        
        // 创建群组成员适配器
        groupMemberAdapter = new GroupMemberAdapter(context, callingVM.getResourcePool(), callingVM.callViewModel);
        
        // 设置网格布局管理器 - 默认1x1，会根据成员数量动态调整
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 1);
        groupView.viewRenderers.setLayoutManager(gridLayoutManager);
        groupView.viewRenderers.setAdapter(groupMemberAdapter);
        
        // 设置RecyclerView引用用于视频绑定刷新
        groupMemberAdapter.setRecyclerView(groupView.viewRenderers);
        
        L.d("GroupCallDialog", "群组成员网格布局初始化完成");
    }
    
    @Override
    protected void bindSpecificData(SignalingInfo signalingInfo) {
        // 设置视频通话标识
        callingVM.setVideoCalls(Constants.MediaType.VIDEO.equals(signalingInfo.getInvitation().getMediaType()));
        
        // 根据通话类型设置控件可见性
        if (groupView.cameraControl != null) {
            groupView.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
        }
        
        if (!callingVM.isVideoCalls) {
            // 音频通话设置
            callingVM.callViewModel.setCameraEnabled(false);
            if (groupView.localSpeakerVideoView != null) {
                groupView.localSpeakerVideoView.setVisibility(View.GONE);
            }
            if (groupView.timeTv != null) {
                groupView.timeTv.setVisibility(View.GONE);
            }
            if (groupView.headTips != null) {
                groupView.headTips.setVisibility(View.GONE);
            }
        }
        
        // 设置控件默认状态
        if (groupView.micIsOn != null) {
            groupView.micIsOn.setChecked(true);
        }
        if (groupView.speakerIsOn != null) {
            groupView.speakerIsOn.setChecked(true);
        }
        
        if (callingVM.isCallOut) {
            // 呼出状态
            if (groupView.callingMenu != null) {
                groupView.callingMenu.setVisibility(View.VISIBLE);
            }
            if (groupView.ask != null) {
                groupView.ask.setVisibility(View.GONE);
            }
        } else {
            // 被呼状态
            if (groupView.callingMenu != null) {
                groupView.callingMenu.setVisibility(View.GONE);
            }
            if (groupView.ask != null) {
                groupView.ask.setVisibility(View.VISIBLE);
            }
        }
        
        // 启动群组成员更新任务
        setupGroupMemberUpdateTask();
        
        L.businessFlow("GroupCallDialog", "群组通话数据绑定", 
            "isVideo: " + callingVM.isVideoCalls + ", isCallOut: " + callingVM.isCallOut);
    }
    
    @Override
    protected void bindUserInfo(SignalingInfo signalingInfo) {
        // 群组通话：获取群组信息而不是单个用户信息
        String groupID = signalingInfo.getInvitation().getGroupID();
        
        if (groupID == null || groupID.isEmpty()) {
            L.w("GroupCallDialog", "群组ID为空，无法获取群组信息");
            return;
        }
        
        OpenIMClient.getInstance().groupManager.getGroupsInfo(new OnBase<List<GroupInfo>>() {
            @Override
            public void onError(int code, String error) {
                LogExceptionHandler.handleException("GroupCallDialog", "获取群组信息失败", 
                    LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                L.e("GroupCallDialog", "获取群组信息失败: " + error + ", code: " + code);
                Toast.makeText(context, "获取群组信息失败: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<GroupInfo> data) {
                if (data.isEmpty()) return;
                
                GroupInfo groupInfo = data.get(0);
                L.d("GroupCallDialog", "获取群组信息成功: " + groupInfo.getGroupName());
                
                // 更新UI显示群组信息
                updateGroupInfoUI(groupInfo);
            }
        }, List.of(groupID));
        
        // 同时初始化群组成员信息
        initializeGroupMembers(signalingInfo);
    }
    
    /**
     * 更新群组信息到UI
     */
    private void updateGroupInfoUI(GroupInfo groupInfo) {
        try {
            // 群组通话可能不需要显示群组名称，或者显示在特定位置
            // 这里可以根据UI设计需求进行调整
            
            L.d("GroupCallDialog", "群组信息UI更新完成: " + groupInfo.getGroupName());
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("GroupCallDialog", "更新群组信息UI", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    /**
     * 初始化群组成员信息
     */
    private void initializeGroupMembers(SignalingInfo signalingInfo) {
        try {
            // 从信令中获取邀请的成员列表
            List<String> inviteeList = signalingInfo.getInvitation().getInviteeUserIDList();
            String inviterID = signalingInfo.getInvitation().getInviterUserID();
            
            // 构建所有参与者ID列表（包括发起者和被邀请者）
            List<String> allMemberIds = new ArrayList<>();
            if (inviterID != null && !inviterID.isEmpty()) {
                allMemberIds.add(inviterID);
            }
            if (inviteeList != null && !inviteeList.isEmpty()) {
                allMemberIds.addAll(inviteeList);
            }
            
            L.d("GroupCallDialog", "初始化群组成员，总数: " + allMemberIds.size());
            
            // 直接初始化群组成员列表
            for (String memberId : allMemberIds) {
                GroupCallMember member = new GroupCallMember();
                member.setUserId(memberId);
                member.setNickname(memberId); // 暂时使用ID作为昵称，后续会更新
                member.setState(CallMemberState.IDLE);
                callingVM.groupMembers.add(member);
            }
            
            // 更新UI显示
            updateGroupMemberGrid();
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("GroupCallDialog", "初始化群组成员", 
                LogExceptionHandler.ExceptionType.DATA_ERROR, e);
        }
    }
    
    @Override
    protected void setupEventListeners(SignalingInfo signalingInfo) {
        // 切换摄像头
        if (groupView.switchCamera != null) {
            groupView.switchCamera.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    callingVM.callViewModel.switchCamera();
                    L.d("GroupCallDialog", "切换摄像头");
                }
            });
        }

        // 麦克风开关
        if (groupView.micIsOn != null) {
            groupView.micIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    boolean isChecked = groupView.micIsOn.isChecked();
                    callingVM.callViewModel.setMicrophoneEnabled(isChecked);
                    L.d("GroupCallDialog", "麦克风状态: " + isChecked);
                }
            });
        }

        // 扬声器开关  
        if (groupView.speakerIsOn != null) {
            groupView.speakerIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    boolean isChecked = groupView.speakerIsOn.isChecked();
                    callingVM.callViewModel.setSpeakerphoneEnabled(isChecked);
                    L.d("GroupCallDialog", "扬声器状态: " + isChecked);
                }
            });
        }

        // 摄像头开关
        if (groupView.cameraControl != null) {
            groupView.cameraControl.setOnClickListener(new OnDedrepClickListener(1000) {
                @Override
                public void click(View v) {
                    boolean isEnabled = !callingVM.callViewModel.isCameraEnabled();
                    callingVM.callViewModel.setCameraEnabled(isEnabled);
                    L.d("GroupCallDialog", "摄像头状态: " + isEnabled);
                }
            });
        }

        // 挂断
        if (groupView.hangUp != null) {
            groupView.hangUp.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    callingVM.hangup();
                    L.d("GroupCallDialog", "用户挂断群组通话");
                }
            });
        }

        // 拒接
        if (groupView.reject != null) {
            groupView.reject.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    callingVM.reject();
                    L.d("GroupCallDialog", "用户拒接群组通话");
                }
            });
        }

        // 接听
        if (groupView.answer != null) {
            groupView.answer.setOnClickListener(new OnDedrepClickListener() {
                @Override
                public void click(View v) {
                    callingVM.accept();
                    L.d("GroupCallDialog", "用户接听群组通话");
                }
            });
        }

        // 最小化
        if (groupView.zoomOut != null) {
            groupView.zoomOut.setOnClickListener(v -> {
                shrink(true);
                L.d("GroupCallDialog", "最小化群组通话窗口");
            });
        }

        L.d("GroupCallDialog", "群组通话事件监听器设置完成");
    }
    
    @Override
    protected void handleShrink(boolean isShrink) {
        if (groupView != null && groupView.home != null) {
            groupView.home.setVisibility(isShrink ? View.GONE : View.VISIBLE);
        }
        
        // 更新悬浮窗状态显示
        if (isShrink && floatViewBinding != null) {
            if (callingVM.isStartCall) {
                floatViewBinding.sTips.setText("群组通话中");
            } else {
                floatViewBinding.sTips.setText("群组通话邀请");
            }
        }
    }
    
    @Override
    protected void cleanup() {
        try {
            L.d("GroupCallDialog", "开始清理群组通话资源");
            
            // 清理更新任务
            clearUpdateTask();
            
            // 清理群组成员适配器
            if (groupMemberAdapter != null) {
                groupMemberAdapter.cleanup();
                groupMemberAdapter = null;
            }
            
            // 清理视频渲染器资源
            if (callingVM != null && callingVM.callViewModel != null) {
                callingVM.callViewModel.clearVideoRenderers();
            }
            
            // 清理资源池
            if (callingVM.getResourcePool() != null) {
                callingVM.getResourcePool().releaseAllRenderers();
            }
            
            // 清理绑定
            if (groupView != null) {
                groupView = null;
            }
            
            L.d("GroupCallDialog", "群组通话资源清理完成");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("GroupCallDialog", "清理资源", 
                LogExceptionHandler.ExceptionType.CLEANUP_ERROR, e);
        }
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
        
        updateTask = new Runnable() {
            @Override
            public void run() {
                if (groupMemberAdapter != null && !isFinishing()) {
                    updateGroupMemberGrid();
                    updateHandler.postDelayed(this, 1000); // 每秒检查一次更新
                }
            }
        };
        
        updateHandler.post(updateTask);
        L.d("GroupCallDialog", "群组成员更新任务启动");
    }
    
    /**
     * 更新群组成员网格布局
     */
    private void updateGroupMemberGrid() {
        if (groupMemberAdapter == null || groupView == null) return;
        
        List<GroupCallMember> members = new ArrayList<>(callingVM.groupMembers);
        int memberCount = members.size();
        
        // 根据成员数量动态调整网格布局
        if (groupView.viewRenderers != null) {
            GridLayoutManager gridLayoutManager = (GridLayoutManager) groupView.viewRenderers.getLayoutManager();
            if (gridLayoutManager != null) {
                int spanCount = calculateGridSpanCount(memberCount);
                gridLayoutManager.setSpanCount(spanCount);
            }
        }
        
        // 更新适配器数据
        groupMemberAdapter.updateMembers(members);
        
        L.d("GroupCallDialog", "群组成员网格更新完成，成员数: " + memberCount);
    }
    
    /**
     * 根据成员数量计算网格列数
     */
    private int calculateGridSpanCount(int memberCount) {
        if (memberCount <= 1) return 1;
        if (memberCount <= 4) return 2;  // 2x2 最多4人
        if (memberCount <= 9) return 3;  // 3x3 最多9人
        return 3; // 最多9宫格
    }
    
    /**
     * 清理更新任务防止内存泄漏
     */
    private void clearUpdateTask() {
        if (updateHandler != null && updateTask != null) {
            updateHandler.removeCallbacks(updateTask);
            updateTask = null;
        }
    }
    
    /**
     * 检查Dialog是否即将关闭
     */
    private boolean isFinishing() {
        return !isShowing() || context == null;
    }
    
    /**
     * 获取视图绑定（用于测试或特殊场景）
     */
    public DialogGroupCallBinding getViewBinding() {
        return groupView;
    }
    
    @Override
    public void otherSideAccepted() {
        try {
            // 群组通话中，对方接受通话的处理
            if (groupView != null) {
                // 隐藏接听/拒绝按钮，显示通话中控制
                if (groupView.ask != null) {
                    groupView.ask.setVisibility(View.GONE);
                }
                if (groupView.callingMenu != null) {
                    groupView.callingMenu.setVisibility(View.VISIBLE);
                }
            }
            
            // 更新群组成员状态：有成员接受了通话
            updateGroupMemberGrid();
            
            L.d("GroupCallDialog", "群组通话中有成员接受，UI更新完成");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("GroupCallDialog", "处理成员接受通话", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    @Override
    public String buildPrimaryKey() {
        try {
            if (signalingInfo != null && signalingInfo.getInvitation() != null) {
                // 群组通话使用群组ID和发起人构建唯一键
                String groupID = signalingInfo.getInvitation().getGroupID();
                String inviterID = signalingInfo.getInvitation().getInviterUserID();
                
                if (groupID != null && !groupID.isEmpty()) {
                    return "group_" + groupID + "_" + inviterID + "_" + System.currentTimeMillis();
                }
            }
            
            // 降级方案
            return "group_call_" + System.currentTimeMillis();
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("GroupCallDialog", "构建主键", 
                LogExceptionHandler.ExceptionType.DATA_ERROR, e);
            return "group_call_fallback_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 获取群组成员适配器（用于测试或特殊场景）
     */
    public GroupMemberAdapter getGroupMemberAdapter() {
        return groupMemberAdapter;
    }
}