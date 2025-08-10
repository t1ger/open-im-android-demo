package io.openim.android.ouicalling;

import android.content.Context;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.openim.android.ouicalling.adapter.GroupMemberAdapter;
import io.openim.android.ouicalling.databinding.DialogGroupCallBinding;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.sdk.models.SignalingInfo;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.GroupCallLogger;
import io.openim.android.ouicore.utils.LogExceptionHandler;

/**
 * 群组通话对话框
 * 重构后的统一九宫格架构，消除双重布局系统冲突
 * 
 * 架构设计原则：
 * 1. 统一布局管理：只使用标准GridLayoutManager
 * 2. 统一适配器：只使用GroupMemberAdapter  
 * 3. 清晰的状态管理：简化的九宫格模式
 * 4. 完全兼容BaseCallDialog接口
 */
public class GroupCallDialog extends BaseCallDialog {
    
    private static final String TAG = "GroupCallDialog";
    
    // UI组件
    private DialogGroupCallBinding groupView;
    private GroupMemberAdapter memberAdapter;
    private GridLayoutManager gridLayoutManager;
    private Handler updateHandler;
    private Runnable updateTask;
    
    public GroupCallDialog(@NonNull Context context, CallingService callingService, boolean isCallOut) {
        super(context, callingService, isCallOut);
        GroupCallLogger.logCriticalFlow("对话框创建", "类型: GroupCall", "统一九宫格架构初始化");
        GroupCallLogger.logDebug("构造函数", "context=" + context.getClass().getSimpleName() + ", isCallOut=" + isCallOut);
    }
    
    @Override
    protected void initSpecificView() {
        // 使用群组通话专用布局
        groupView = DialogGroupCallBinding.inflate(getLayoutInflater());
        setContentView(groupView.getRoot());
        
        // 设置通用UI属性
        setupCommonUI();
        
        // 初始化九宫格布局（统一架构）
        initUnifiedGridLayout();
        
        GroupCallLogger.logUIOperation("九宫格初始化", "统一GridLayoutManager架构完成");
    }
    
    /**
     * 设置通用UI属性
     */
    private void setupCommonUI() {
        if (groupView.zoomOut != null) {
            groupView.zoomOut.setVisibility(Common.isScreenLocked() ? View.GONE : View.VISIBLE);
        }
    }
    
    /**
     * 初始化统一的九宫格布局架构
     * 只使用一套布局系统，避免冲突
     */
    private void initUnifiedGridLayout() {
        if (groupView.viewRenderers == null) {
            L.e(TAG, "viewRenderers为null，无法初始化网格布局");
            return;
        }
        
        GroupCallLogger.logDebug("九宫格初始化", "开始设置GridLayoutManager和GroupMemberAdapter");
        
        // 1. 创建群组成员适配器
        memberAdapter = new GroupMemberAdapter(context, callingVM.getResourcePool(), callingVM.callViewModel);
        
        // 2. 创建标准网格布局管理器（1x1开始，动态调整）
        gridLayoutManager = new GridLayoutManager(context, 1);
        
        // 3. 应用到RecyclerView
        groupView.viewRenderers.setLayoutManager(gridLayoutManager);
        groupView.viewRenderers.setAdapter(memberAdapter);
        
        // 4. 设置RecyclerView引用用于视频绑定刷新
        memberAdapter.setRecyclerView(groupView.viewRenderers);
        
        GroupCallLogger.logCriticalFlow("九宫格架构", "初始化完成", "GridLayoutManager + GroupMemberAdapter");
    }
    
    @Override
    protected void bindSpecificData(SignalingInfo signalingInfo) {
        GroupCallLogger.logCriticalFlow("数据绑定", "群组通话", "开始绑定信令数据");
        GroupCallLogger.logSignaling("DATA_BINDING", "绑定群组通话数据", GroupCallLogger.formatSignalingData(signalingInfo));
        
        // 设置视频通话标识
        callingVM.setVideoCalls(Constants.MediaType.VIDEO.equals(signalingInfo.getInvitation().getMediaType()));
        
        // 配置视频相关控件
        setupVideoControls();
        
        // 设置控件默认状态
        setupDefaultControlStates();
        
        // 根据呼叫方向设置UI状态
        setupCallDirectionUI();
        
        GroupCallLogger.logCriticalFlow("数据绑定", "完成", "群组通话数据绑定成功");
    }
    
    /**
     * 配置视频相关控件
     */
    private void setupVideoControls() {
        if (groupView.cameraControl != null) {
            groupView.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
        }
        
        if (!callingVM.isVideoCalls) {
            // 音频通话配置
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
    }
    
    /**
     * 设置控件默认状态
     */
    private void setupDefaultControlStates() {
        if (groupView.micIsOn != null) {
            groupView.micIsOn.setChecked(true);
        }
        if (groupView.speakerIsOn != null) {
            groupView.speakerIsOn.setChecked(true);
        }
    }
    
    /**
     * 根据呼叫方向设置UI状态
     */
    private void setupCallDirectionUI() {
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
            
            // 设置群组通话专有的点击监听器
            setupGroupCallClickListeners();
        }
    }
    
    /**
     * 设置群组通话专有的点击监听器
     */
    private void setupGroupCallClickListeners() {
        // 接听按钮
        if (groupView.answer != null) {
            groupView.answer.setOnClickListener(v -> {
                GroupCallLogger.logCriticalFlow("接听操作", "群组通话", "用户点击接听按钮");
                callingVM.accept();
            });
        }
        
        // 拒绝按钮
        if (groupView.reject != null) {
            groupView.reject.setOnClickListener(v -> {
                GroupCallLogger.logCriticalFlow("拒绝操作", "群组通话", "用户点击拒绝按钮");
                callingVM.reject();
            });
        }
        
        // 挂断按钮
        if (groupView.hangUp != null) {
            groupView.hangUp.setOnClickListener(v -> {
                GroupCallLogger.logCriticalFlow("挂断操作", "群组通话", "用户点击挂断按钮");
                callingVM.hangup();
            });
        }
        
        // 切换摄像头
        if (groupView.switchCamera != null) {
            groupView.switchCamera.setOnClickListener(v -> {
                callingVM.callViewModel.switchCamera();
            });
        }
        
        // 麦克风控制
        if (groupView.micIsOn != null) {
            groupView.micIsOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
                callingVM.callViewModel.setMicrophoneEnabled(isChecked);
            });
        }
        
        // 扬声器控制
        if (groupView.speakerIsOn != null) {
            groupView.speakerIsOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
                callingVM.callViewModel.setSpeakerphoneEnabled(isChecked);
            });
        }
    }
    
    @Override
    protected void bindUserInfo(SignalingInfo signalingInfo) {
        GroupCallLogger.logDebug("用户信息绑定", "群组通话用户信息从 CallingVM 获取");
        // 群组通话的用户信息从CallingVM中获取
        refreshMemberList();
    }
    
    @Override
    protected void setupEventListeners(SignalingInfo signalingInfo) {
        GroupCallLogger.logCriticalFlow("事件监听", "设置", "群组通话事件监听器初始化");
        
        // 群组通话事件监听设置
        // 注意：CallingVM可能没有这些方法，需要通过其他方式监听
        // TODO: 实现成员列表变化和视频刷新的监听机制
    }
    
    @Override
    protected void handleShrink(boolean isShrink) {
        GroupCallLogger.logUIOperation("悬浮窗收起", "isShrink=" + isShrink);
        // 群组通话的悬浮窗逻辑
        if (isShrink) {
            // 收起时暂停视频渲染以节省资源
            if (memberAdapter != null) {
                // TODO: 暂停视频渲染逻辑
            }
        } else {
            // 恢复时重新开始视频渲染
            if (memberAdapter != null) {
                // TODO: 恢复视频渲染逻辑
            }
        }
    }
    
    @Override
    protected void cleanup() {
        GroupCallLogger.logCriticalFlow("资源清理", "开始", "群组通话资源清理");
        
        // 清理更新任务
        if (updateHandler != null && updateTask != null) {
            updateHandler.removeCallbacks(updateTask);
        }
        
        // 清理适配器
        if (memberAdapter != null) {
            GroupCallLogger.logDebug("资源清理", "清理GroupMemberAdapter");
            memberAdapter.cleanup();
            memberAdapter = null;
        }
        
        // 清理布局管理器
        gridLayoutManager = null;
        GroupCallLogger.logCriticalFlow("资源清理", "完成", "所有群组通话资源已清理");
    }
    
    @Override
    public void otherSideAccepted() {
        GroupCallLogger.logCriticalFlow("对方接受", "群组通话", "对方成员接受通话");
        // 群组通话中对方接受的处理逻辑
        refreshMemberList();
        refreshVideoViews();
    }
    
    @Override
    public String buildPrimaryKey() {
        if (signalingInfo != null && signalingInfo.getInvitation() != null) {
            return "group_call_" + signalingInfo.getInvitation().getGroupID();
        }
        return "group_call_unknown";
    }
    
    /**
     * 刷新成员列表
     */
    private void refreshMemberList() {
        if (memberAdapter != null) {
            int memberCount = callingVM.getGroupMembers().size();
            GroupCallLogger.logDebug("成员刷新", "刷新群组成员列表, 数量: " + memberCount);
            
            // 获取成员列表并更新适配器
            memberAdapter.notifyDataSetChanged();
            
            // 根据成员数量调整布局
            adjustGridLayout(memberCount);
        }
    }
    
    /**
     * 刷新视频视图
     */
    private void refreshVideoViews() {
        if (memberAdapter != null) {
            GroupCallLogger.logVideoRendering("所有成员", "刷新视频视图", "更新显示");
            memberAdapter.notifyDataSetChanged();
        }
    }
    
    /**
     * 根据成员数量动态调整网格布局
     */
    private void adjustGridLayout(int memberCount) {
        if (gridLayoutManager == null) return;
        
        int spanCount;
        if (memberCount <= 1) {
            spanCount = 1; // 1x1
        } else if (memberCount <= 4) {
            spanCount = 2; // 2x2
        } else {
            spanCount = 3; // 3x3
        }
        
        gridLayoutManager.setSpanCount(spanCount);
        GroupCallLogger.logGridLayout("动态调整", memberCount, spanCount + "x" + spanCount);
    }
}