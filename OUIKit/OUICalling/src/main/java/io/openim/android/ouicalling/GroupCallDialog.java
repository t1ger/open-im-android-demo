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
import io.openim.android.ouicalling.state.GroupCallStateManager;
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
public class GroupCallDialog extends BaseCallDialog implements GroupCallStateManager.StateChangeObserver {
    
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
        android.util.Log.e("GroupCallFlow", "🔧🔧🔧 [initUnifiedGridLayout] 开始初始化统一九宫格布局");
        
        if (groupView.viewRenderers == null) {
            android.util.Log.e("GroupCallFlow", "❌ [initUnifiedGridLayout] viewRenderers为null!");
            L.e(TAG, "viewRenderers为null，无法初始化网格布局");
            return;
        }
        
        android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] viewRenderers已找到");
        GroupCallLogger.logDebug("九宫格初始化", "开始设置GridLayoutManager和GroupMemberAdapter");
        
        try {
            // 1. 创建群组成员适配器
            android.util.Log.e("GroupCallFlow", "🔧 [initUnifiedGridLayout] 创建GroupMemberAdapter");
            memberAdapter = new GroupMemberAdapter(context, callingVM.getResourcePool(), callingVM.callViewModel);
            android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] GroupMemberAdapter创建成功: " + (memberAdapter != null));
            
            // 2. 创建标准网格布局管理器（1x1开始，动态调整）
            android.util.Log.e("GroupCallFlow", "🔧 [initUnifiedGridLayout] 创建GridLayoutManager");
            gridLayoutManager = new GridLayoutManager(context, 1);
            android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] GridLayoutManager创建成功");
            
            // 3. 应用到RecyclerView
            android.util.Log.e("GroupCallFlow", "🔧 [initUnifiedGridLayout] 设置LayoutManager和Adapter");
            groupView.viewRenderers.setLayoutManager(gridLayoutManager);
            android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] LayoutManager设置成功");
            
            groupView.viewRenderers.setAdapter(memberAdapter);
            android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] Adapter设置成功");
            
            // 4. 设置RecyclerView引用用于视频绑定刷新
            android.util.Log.e("GroupCallFlow", "🔧 [initUnifiedGridLayout] 设置RecyclerView引用");
            memberAdapter.setRecyclerView(groupView.viewRenderers);
            android.util.Log.e("GroupCallFlow", "✅ [initUnifiedGridLayout] RecyclerView引用设置成功");
            
            android.util.Log.e("GroupCallFlow", "✅✅✅ [initUnifiedGridLayout] 统一九宫格布局初始化完成!!!");
            GroupCallLogger.logCriticalFlow("九宫格架构", "初始化完成", "GridLayoutManager + GroupMemberAdapter");
            
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] initUnifiedGridLayout异常: " + e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    protected void bindSpecificData(SignalingInfo signalingInfo) {
        // 📱 强制日志：确保这个方法被执行
        android.util.Log.e("GroupCallFlow", "📱📱📱 [GroupCallDialog] bindSpecificData 开始执行!!!");
        GroupCallLogger.logCriticalFlow("数据绑定", "群组通话", "开始绑定信令数据");
        GroupCallLogger.logSignaling("DATA_BINDING", "绑定群组通话数据", GroupCallLogger.formatSignalingData(signalingInfo));
        
        try {
            // 检查callingVM状态
            android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] callingVM是否为null: " + (callingVM == null));
            if (callingVM != null) {
                android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] 信令媒体类型: " + signalingInfo.getInvitation().getMediaType());
            }
            
            // 设置视频通话标识
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备设置视频通话标识");
            callingVM.setVideoCalls(Constants.MediaType.VIDEO.equals(signalingInfo.getInvitation().getMediaType()));
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] 视频通话标识设置完成: " + callingVM.isVideoCalls);
            
            // 检查groupView状态
            android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] groupView是否为null: " + (groupView == null));
            if (groupView != null) {
                android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] headTips是否为null: " + (groupView.headTips == null));
                android.util.Log.e("GroupCallFlow", "🔍 [DEBUG] viewRenderers是否为null: " + (groupView.viewRenderers == null));
            }
            
            // 🔧 关键修复：初始化九宫格适配器
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备初始化九宫格布局");
            initUnifiedGridLayout();
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] 九宫格布局初始化完成");
            
            // 🔧 添加测试数据确保九宫格可见
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备添加测试数据到九宫格");
            addTestDataToGrid();
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] 测试数据添加完成");
            
            // 配置视频相关控件
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备执行setupVideoControls");
            setupVideoControls();
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] setupVideoControls执行完成");
            
            // 设置控件默认状态
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备执行setupDefaultControlStates");
            setupDefaultControlStates();
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] setupDefaultControlStates执行完成");
            
            // 根据呼叫方向设置UI状态
            android.util.Log.e("GroupCallFlow", "🔧 [DEBUG] 准备执行setupCallDirectionUI");
            setupCallDirectionUI();
            android.util.Log.e("GroupCallFlow", "✅ [DEBUG] setupCallDirectionUI执行完成");
            
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] bindSpecificData异常: " + e.getMessage(), e);
            throw e;
        }
        
        GroupCallLogger.logCriticalFlow("数据绑定", "完成", "群组通话数据绑定成功");
    }
    
    /**
     * 添加测试数据到九宫格以验证显示
     */
    private void addTestDataToGrid() {
        if (memberAdapter == null) {
            android.util.Log.e("GroupCallFlow", "❌ [addTestDataToGrid] memberAdapter为null!");
            return;
        }
        
        try {
            // 创建测试成员数据
            java.util.List<Object> testMembers = new java.util.ArrayList<>();
            
            // 添加自己作为第一个成员
            Object selfMember = createTestMember("我", true);
            if (selfMember != null) {
                testMembers.add(selfMember);
                android.util.Log.e("GroupCallFlow", "✅ [addTestDataToGrid] 添加自己成员");
            }
            
            // 添加其他成员
            Object otherMember = createTestMember("群友", false);
            if (otherMember != null) {
                testMembers.add(otherMember);
                android.util.Log.e("GroupCallFlow", "✅ [addTestDataToGrid] 添加其他成员");
            }
            
            android.util.Log.e("GroupCallFlow", "✅ [addTestDataToGrid] 测试数据创建完成，数量: " + testMembers.size());
            
            // 通知适配器数据更新
            memberAdapter.notifyDataSetChanged();
            android.util.Log.e("GroupCallFlow", "✅ [addTestDataToGrid] notifyDataSetChanged已调用");
            
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] addTestDataToGrid异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建测试成员数据
     */
    private Object createTestMember(String name, boolean isSelf) {
        try {
            // 这里需要根据实际的Member类来创建对象
            // 先返回null，让我们看看日志输出
            android.util.Log.e("GroupCallFlow", "🔍 [createTestMember] 创建测试成员: " + name + ", isSelf: " + isSelf);
            return null; // 暂时返回null
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] createTestMember异常: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 配置视频相关控件
     */
    private void setupVideoControls() {
        android.util.Log.e("GroupCallFlow", "🔧🔧🔧 [setupVideoControls] 开始执行 setupVideoControls");
        
        try {
            // 🔧 关键修复：群组通话永远隐藏headTips，显示九宫格
            android.util.Log.e("GroupCallFlow", "🔍 [setupVideoControls] 检查headTips: " + (groupView.headTips != null));
            if (groupView.headTips != null) {
                android.util.Log.e("GroupCallFlow", "🙈 [setupVideoControls] headTips当前可见性: " + groupView.headTips.getVisibility());
                groupView.headTips.setVisibility(View.GONE);
                android.util.Log.e("GroupCallFlow", "✅ [setupVideoControls] headTips已设置为GONE");
                GroupCallLogger.logUIOperation("隐藏单人通话界面", "群组通话不显示headTips");
            } else {
                android.util.Log.e("GroupCallFlow", "❌ [setupVideoControls] headTips为null!");
            }
            
            // 🔧 关键修复：确保九宫格RecyclerView可见
            android.util.Log.e("GroupCallFlow", "🔍 [setupVideoControls] 检查viewRenderers: " + (groupView.viewRenderers != null));
            if (groupView.viewRenderers != null) {
                android.util.Log.e("GroupCallFlow", "🔲 [setupVideoControls] viewRenderers当前可见性: " + groupView.viewRenderers.getVisibility());
                groupView.viewRenderers.setVisibility(View.VISIBLE);
                android.util.Log.e("GroupCallFlow", "✅ [setupVideoControls] viewRenderers已设置为VISIBLE");
                GroupCallLogger.logUIOperation("显示九宫格界面", "viewRenderers设为可见");
            } else {
                android.util.Log.e("GroupCallFlow", "❌ [setupVideoControls] viewRenderers为null!");
            }
            
            // 视频/音频通话的摄像头控制
            android.util.Log.e("GroupCallFlow", "🔍 [setupVideoControls] 检查cameraControl: " + (groupView.cameraControl != null));
            if (groupView.cameraControl != null) {
                groupView.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
                android.util.Log.e("GroupCallFlow", "📹 [setupVideoControls] cameraControl可见性: " + (callingVM.isVideoCalls ? "VISIBLE" : "GONE"));
            }
            
            android.util.Log.e("GroupCallFlow", "🔍 [setupVideoControls] 是否为视频通话: " + callingVM.isVideoCalls);
            if (!callingVM.isVideoCalls) {
                android.util.Log.e("GroupCallFlow", "🎤 [setupVideoControls] 音频通话配置开始");
                // 音频通话配置
                callingVM.callViewModel.setCameraEnabled(false);
                android.util.Log.e("GroupCallFlow", "✅ [setupVideoControls] 摄像头已禁用");
                
                if (groupView.localSpeakerVideoView != null) {
                    groupView.localSpeakerVideoView.setVisibility(View.GONE);
                    android.util.Log.e("GroupCallFlow", "✅ [setupVideoControls] localSpeakerVideoView已隐藏");
                }
                if (groupView.timeTv != null) {
                    groupView.timeTv.setVisibility(View.GONE);
                    android.util.Log.e("GroupCallFlow", "✅ [setupVideoControls] timeTv己隐藏");
                }
            } else {
                android.util.Log.e("GroupCallFlow", "📹 [setupVideoControls] 视频通话配置");
            }
            
            android.util.Log.e("GroupCallFlow", "✅✅✅ [setupVideoControls] setupVideoControls执行完成!!!");
            GroupCallLogger.logCriticalFlow("UI配置", "群组通话界面", 
                "九宫格: VISIBLE, headTips: GONE, 视频: " + callingVM.isVideoCalls);
                
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [ERROR] setupVideoControls异常: " + e.getMessage(), e);
            throw e;
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
                // ✅ 使用统一的挂断接口，显式传递signalingInfo参数
                callingVM.renewalDB(callingVM.buildPrimaryKey(signalingInfo), (realm, callHistory) -> 
                    callHistory.setDuration((int) (System.currentTimeMillis() - callHistory.getDate()))
                );
                callingVM.hangup(signalingInfo);
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
        
        // 🔧 关键修复：注册StateChangeObserver监听成员信息更新
        if (callingVM.groupCallStateManager != null) {
            callingVM.groupCallStateManager.addObserver(this);
            GroupCallLogger.logCriticalFlow("事件监听", "注册成功", "群组通话状态监听器已注册");
        }
        
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
    
    // === 实现 StateChangeObserver 接口 ===
    
    @Override
    public void onStateChanged(@NonNull GroupCallStateManager.StateChangeType changeType) {
        GroupCallLogger.logDebug("状态变化", changeType.getDescription());
    }
    
    @Override
    public void onMemberStateChanged(@NonNull GroupCallMember member) {
        GroupCallLogger.logMemberStateChange(member.getUserID(), 
            member.getState().name(), member.getNickname());
        refreshMemberList();
    }
    
    @Override
    public void onMemberAdded(@NonNull GroupCallMember member) {
        GroupCallLogger.logMemberJoin(member.getUserID(), 
            callingVM.getGroupMembers().size());
        refreshMemberList();
    }
    
    @Override
    public void onMemberRemoved(@NonNull GroupCallMember member) {
        GroupCallLogger.logMemberLeave(member.getUserID(), "退出通话", 
            callingVM.getGroupMembers().size());
        refreshMemberList();
    }
    
    @Override
    public void onCurrentSpeakerChanged(String oldSpeaker, String newSpeaker) {
        GroupCallLogger.logDebug("发言人变化", 
            "from: " + oldSpeaker + " to: " + newSpeaker);
    }
    
    @Override
    public void onCallEnded(@NonNull String reason) {
        GroupCallLogger.logCriticalFlow("通话结束", reason, "群组通话已结束");
        // 群组通话结束时自动关闭对话框
        dismiss();
    }
    
    @Override
    public void onMembersInfoUpdated() {
        // 🔧 关键修复：成员信息更新时刷新UI显示
        GroupCallLogger.logCriticalFlow("成员信息更新", "刷新UI", 
            "用户名和头像已更新，刷新九宫格显示");
        refreshMemberList();
    }
}