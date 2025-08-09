package io.openim.android.ouicalling;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.openim.android.ouicalling.databinding.DialogCallBinding;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.PublicUserInfo;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 单人通话对话框
 * 专门处理1v1音视频通话，使用dialog_call.xml布局
 */
public class SingleCallDialog extends BaseCallDialog {
    
    private DialogCallBinding view;
    
    public SingleCallDialog(@NonNull Context context, CallingService callingService, boolean isCallOut) {
        super(context, callingService, isCallOut);
        L.d("SingleCallDialog", "创建单人通话对话框");
    }
    
    @Override
    protected void initSpecificView() {
        // 使用单人通话专用布局
        view = DialogCallBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        
        // 设置通用UI属性
        view.zoomOut.setVisibility(Common.isScreenLocked() ? View.GONE : View.VISIBLE);
        
        L.d("SingleCallDialog", "单人通话UI初始化完成");
    }
    
    @Override
    protected void bindSpecificData(SignalingInfo signalingInfo) {
        // 设置视频通话标识
        callingVM.setVideoCalls(Constants.MediaType.VIDEO.equals(signalingInfo.getInvitation().getMediaType()));
        
        // 根据通话类型设置控件可见性
        view.cameraControl.setVisibility(callingVM.isVideoCalls ? View.VISIBLE : View.GONE);
        
        if (!callingVM.isVideoCalls) {
            // 音频通话设置
            callingVM.callViewModel.setCameraEnabled(false);
            view.localSpeakerVideoView.setVisibility(View.GONE);
            view.timeTv.setVisibility(View.GONE);
            view.headTips.setVisibility(View.GONE);
            view.audioCall.setVisibility(View.VISIBLE);
        }
        
        // 设置控件默认状态
        view.micIsOn.setChecked(true);
        view.speakerIsOn.setChecked(true);
        
        if (callingVM.isCallOut) {
            // 呼出状态
            view.callingMenu.setVisibility(View.VISIBLE);
            view.ask.setVisibility(View.GONE);
            view.callingTips.setText(context.getString(io.openim.android.ouicore.R.string.waiting_tips) + "...");
            view.callingTips2.setText(context.getString(io.openim.android.ouicore.R.string.waiting_tips) + "...");
        } else {
            // 被呼状态
            view.callingMenu.setVisibility(View.GONE);
            view.ask.setVisibility(View.VISIBLE);
        }
        
        L.d("SingleCallDialog", "单人通话数据绑定完成 - isVideo: " + callingVM.isVideoCalls + ", isCallOut: " + callingVM.isCallOut);
    }
    
    @Override
    protected void bindUserInfo(SignalingInfo signalingInfo) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(callingVM.isCallOut ?
            signalingInfo.getInvitation().getInviteeUserIDList().get(0) :
            signalingInfo.getInvitation().getInviterUserID());

        OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
            @Override
            public void onError(int code, String error) {
                LogExceptionHandler.handleException("SingleCallDialog", "获取用户信息失败", 
                    LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                L.e("SingleCallDialog", "获取用户信息失败: " + error + ", code: " + code);
                Toast.makeText(context, error + code, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(List<PublicUserInfo> data) {
                if (data.isEmpty() || view == null) return;
                
                PublicUserInfo userInfo = data.get(0);
                L.d("SingleCallDialog", "获取用户信息成功: " + userInfo.getNickname());
                
                // 更新UI显示用户信息
                updateUserInfoUI(userInfo);
            }
        }, ids);
    }
    
    /**
     * 更新用户信息到UI
     */
    private void updateUserInfoUI(PublicUserInfo userInfo) {
        try {
            view.nickName.setText(userInfo.getNickname());
            view.nickName2.setText(userInfo.getNickname());
            
            // 加载头像
            if (userInfo.getFaceURL() != null && !userInfo.getFaceURL().isEmpty()) {
                // 使用图片加载库加载头像
                // Glide.with(context).load(userInfo.getFaceURL()).into(view.avatar);
                // Glide.with(context).load(userInfo.getFaceURL()).into(view.avatar2);
            }
            
            L.d("SingleCallDialog", "用户信息UI更新完成");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("SingleCallDialog", "更新用户信息UI", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    @Override
    protected void setupEventListeners(SignalingInfo signalingInfo) {
        // 切换摄像头
        view.switchCamera.setOnClickListener(new OnDedrepClickListener(1000) {
            @Override
            public void click(View v) {
                callingVM.callViewModel.switchCamera();
                L.d("SingleCallDialog", "切换摄像头");
            }
        });

        // 麦克风开关
        view.micIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
            @Override
            public void click(View v) {
                boolean isChecked = view.micIsOn.isChecked();
                callingVM.callViewModel.setMicrophoneEnabled(isChecked);
                L.d("SingleCallDialog", "麦克风状态: " + isChecked);
            }
        });

        // 扬声器开关  
        view.speakerIsOn.setOnClickListener(new OnDedrepClickListener(1000) {
            @Override
            public void click(View v) {
                boolean isChecked = view.speakerIsOn.isChecked();
                callingVM.callViewModel.setSpeakerphoneEnabled(isChecked);
                L.d("SingleCallDialog", "扬声器状态: " + isChecked);
            }
        });

        // 摄像头开关
        view.cameraControl.setOnClickListener(new OnDedrepClickListener(1000) {
            @Override
            public void click(View v) {
                boolean isEnabled = !callingVM.callViewModel.isCameraEnabled();
                callingVM.callViewModel.setCameraEnabled(isEnabled);
                L.d("SingleCallDialog", "摄像头状态: " + isEnabled);
            }
        });

        // 挂断
        view.hangUp.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                callingVM.hangup();
                L.d("SingleCallDialog", "用户挂断通话");
            }
        });

        // 拒接
        view.reject.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                callingVM.reject();
                L.d("SingleCallDialog", "用户拒接通话");
            }
        });

        // 接听
        view.answer.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                callingVM.accept();
                L.d("SingleCallDialog", "用户接听通话");
            }
        });

        // 最小化
        view.zoomOut.setOnClickListener(v -> {
            shrink(true);
            L.d("SingleCallDialog", "最小化通话窗口");
        });

        // 收起
        view.shrink.setOnClickListener(v -> {
            shrink(true);
            L.d("SingleCallDialog", "收起通话窗口");
        });

        // 本地视频点击
        view.localSpeakerVideoView.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                callingVM.callViewModel.switchCamera();
                L.d("SingleCallDialog", "点击本地视频切换摄像头");
            }
        });

        L.d("SingleCallDialog", "事件监听器设置完成");
    }
    
    @Override
    protected void handleShrink(boolean isShrink) {
        if (view != null) {
            view.home.setVisibility(isShrink ? View.GONE : View.VISIBLE);
        }
        
        // 更新悬浮窗状态显示
        if (isShrink && floatViewBinding != null) {
            if (callingVM.isStartCall) {
                floatViewBinding.sTips.setText(io.openim.android.ouicore.R.string.calling);
            } else {
                floatViewBinding.sTips.setText(io.openim.android.ouicore.R.string.call_in);
            }
        }
    }
    
    @Override
    protected void cleanup() {
        try {
            L.d("SingleCallDialog", "开始清理单人通话资源");
            
            // 清理视频渲染器
            if (callingVM != null && callingVM.callViewModel != null) {
                callingVM.callViewModel.clearVideoRenderers();
            }
            
            // 清理绑定
            if (view != null) {
                view = null;
            }
            
            L.d("SingleCallDialog", "单人通话资源清理完成");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("SingleCallDialog", "清理资源", 
                LogExceptionHandler.ExceptionType.CLEANUP_ERROR, e);
        }
    }
    
    @Override
    public void otherSideAccepted() {
        try {
            // 单人通话对方接受后的UI更新
            if (view != null) {
                // 隐藏接听/拒绝按钮，显示通话中控制
                view.ask.setVisibility(View.GONE);
                view.callingMenu.setVisibility(View.VISIBLE);
                
                // 更新状态文字
                view.callingTips.setText("通话中...");
                view.callingTips2.setText("通话中...");
            }
            
            L.d("SingleCallDialog", "对方接受通话，UI更新完成");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("SingleCallDialog", "处理对方接受通话", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    @Override
    public String buildPrimaryKey() {
        try {
            if (signalingInfo != null && signalingInfo.getInvitation() != null) {
                // 使用发起人和被邀请人构建唯一键
                String inviterID = signalingInfo.getInvitation().getInviterUserID();
                String inviteeID = "";
                
                if (signalingInfo.getInvitation().getInviteeUserIDList() != null && 
                    !signalingInfo.getInvitation().getInviteeUserIDList().isEmpty()) {
                    inviteeID = signalingInfo.getInvitation().getInviteeUserIDList().get(0);
                }
                
                return inviterID + "_" + inviteeID + "_" + System.currentTimeMillis();
            }
            
            // 降级方案
            return "single_call_" + System.currentTimeMillis();
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("SingleCallDialog", "构建主键", 
                LogExceptionHandler.ExceptionType.DATA_ERROR, e);
            return "single_call_fallback_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 获取视图绑定（用于测试或特殊场景）
     */
    public DialogCallBinding getViewBinding() {
        return view;
    }
}