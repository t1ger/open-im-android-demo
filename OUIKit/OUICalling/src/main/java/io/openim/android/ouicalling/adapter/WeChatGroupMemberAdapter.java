package io.openim.android.ouicalling.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.livekit.android.renderer.TextureViewRenderer;
import io.openim.android.ouicalling.R;
import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicalling.utils.VideoResourcePool;
import io.openim.android.ouicalling.vm.CallViewModel;
import io.openim.android.ouicalling.manager.StreamPriority;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.widget.AvatarImage;

/**
 * 微信风格群组通话成员适配器
 * 实现微信群视频通话的界面风格和交互逻辑
 */
public class WeChatGroupMemberAdapter extends RecyclerView.Adapter<WeChatGroupMemberAdapter.WeChatMemberViewHolder> {
    
    private static final String TAG = "WeChatGroupMemberAdapter";
    
    private final Context context;
    private final VideoResourcePool resourcePool;
    private final CallViewModel callViewModel;
    private final List<GroupCallMember> memberList = new ArrayList<>();
    
    // 主视频成员索引（-1表示无主视频）
    private int mainVideoPosition = -1;
    
    // 点击监听器
    private OnMemberClickListener onMemberClickListener;
    
    public interface OnMemberClickListener {
        void onMemberClick(GroupCallMember member, int position);
        void onMemberLongClick(GroupCallMember member, int position);
    }
    
    public WeChatGroupMemberAdapter(Context context, VideoResourcePool resourcePool, CallViewModel callViewModel) {
        this.context = context;
        this.resourcePool = resourcePool;
        this.callViewModel = callViewModel;
    }
    
    /**
     * 设置成员点击监听器
     */
    public void setOnMemberClickListener(OnMemberClickListener listener) {
        this.onMemberClickListener = listener;
    }
    
    /**
     * 更新成员列表
     */
    public void updateMembers(List<GroupCallMember> members) {
        memberList.clear();
        if (members != null) {
            memberList.addAll(members);
        }
        notifyDataSetChanged();
        L.d(TAG, "更新成员列表，当前成员数: " + memberList.size());
    }
    
    /**
     * 设置主视频成员
     * @param position 成员位置，-1表示取消主视频
     */
    public void setMainVideoMember(int position) {
        int oldMainPosition = mainVideoPosition;
        mainVideoPosition = position;
        
        // 刷新相关项目
        if (oldMainPosition != -1) {
            notifyItemChanged(oldMainPosition);
        }
        if (mainVideoPosition != -1) {
            notifyItemChanged(mainVideoPosition);
        }
        
        L.d(TAG, "设置主视频成员: " + position);
    }
    
    /**
     * 获取当前主视频成员位置
     */
    public int getMainVideoPosition() {
        return mainVideoPosition;
    }
    
    /**
     * 释放所有视频渲染器资源
     */
    public void releaseAllVideoRenderers() {
        // 从 CallViewModel 注销所有流
        if (callViewModel != null) {
            for (GroupCallMember member : memberList) {
                if (member != null) {
                    callViewModel.unregisterVideoStream(member.getUserID());
                }
            }
        }
        
        // 清理适配器中的成员视频渲染器
        for (GroupCallMember member : memberList) {
            if (member != null && member.getVideoRenderer() != null) {
                member.setVideoRenderer(null);
            }
        }
        
        L.d(TAG, "释放所有视频渲染器资源");
    }
    
    @NonNull
    @Override
    public WeChatMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member_renderer_wechat_style, parent, false);
        return new WeChatMemberViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull WeChatMemberViewHolder holder, int position) {
        if (position < memberList.size()) {
            GroupCallMember member = memberList.get(position);
            holder.bind(member, position);
        }
    }
    
    @Override
    public int getItemCount() {
        return memberList.size();
    }
    
    /**
     * 微信风格成员ViewHolder
     */
    public class WeChatMemberViewHolder extends RecyclerView.ViewHolder {
        
        private final TextureViewRenderer videoRenderer;
        private final AvatarImage avatarImage;
        private final View avatarLayout;
        private final TextView avatarName;
        private final TextView name;
        private final TextView statusText;
        private final TextView callStatusText;
        private final ImageView micIcon;
        private final ImageView cameraIcon;
        private final ImageView networkQuality;
        private final View speakingIndicator;
        private final View selectedBorder;
        private final View callStatusOverlay;
        
        private GroupCallMember currentMember;
        private int currentPosition;
        
        public WeChatMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            
            // 初始化视图组件
            videoRenderer = itemView.findViewById(R.id.remoteSpeakerVideoView);
            avatarImage = itemView.findViewById(R.id.avatar);
            avatarLayout = itemView.findViewById(R.id.avatarRl);
            avatarName = itemView.findViewById(R.id.avatarName);
            name = itemView.findViewById(R.id.name);
            statusText = itemView.findViewById(R.id.statusText);
            callStatusText = itemView.findViewById(R.id.callStatusText);
            micIcon = itemView.findViewById(R.id.micOn);
            cameraIcon = itemView.findViewById(R.id.cameraOn);
            networkQuality = itemView.findViewById(R.id.networkQuality);
            speakingIndicator = itemView.findViewById(R.id.speakingIndicator);
            selectedBorder = itemView.findViewById(R.id.selectedBorder);
            callStatusOverlay = itemView.findViewById(R.id.callStatusOverlay);
            
            // 设置点击监听器
            itemView.setOnClickListener(v -> {
                if (onMemberClickListener != null && currentMember != null) {
                    onMemberClickListener.onMemberClick(currentMember, currentPosition);
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                if (onMemberClickListener != null && currentMember != null) {
                    onMemberClickListener.onMemberLongClick(currentMember, currentPosition);
                    return true;
                }
                return false;
            });
        }
        
        /**
         * 绑定成员数据
         */
        public void bind(GroupCallMember member, int position) {
            if (member == null) return;
            
            this.currentMember = member;
            this.currentPosition = position;
            
            // 设置成员基本信息
            updateMemberBasicInfo(member);
            
            // 设置成员状态
            updateMemberState(member);
            
            // 注册视频流
            registerVideoStream(member);
            
            // 更新控制状态
            updateControlStates(member);
            
            // 更新选中状态
            updateSelectionState(position);
            
            // 更新说话状态
            updateSpeakingState(member);
            
            // 更新网络质量
            updateNetworkQuality(member);
        }
        
        /**
         * 更新成员基本信息
         */
        private void updateMemberBasicInfo(GroupCallMember member) {
            // 设置用户名称
            String displayName = member.getNickname();
            if (displayName == null || displayName.isEmpty()) {
                displayName = member.getUserID();
            }
            
            if (name != null) {
                name.setText(displayName);
            }
            if (avatarName != null) {
                avatarName.setText(displayName);
            }
            
            // 设置头像
            if (avatarImage != null) {
                avatarImage.load(member.getFaceURL(), displayName);
            }
        }
        
        /**
         * 更新成员状态
         */
        private void updateMemberState(GroupCallMember member) {
            CallMemberState state = member.getState();
            
            // 更新状态文字
            if (statusText != null) {
                String stateText = getStateText(state);
                statusText.setText(stateText);
            }
            
            // 根据状态显示/隐藏遮罩层
            if (callStatusOverlay != null && callStatusText != null) {
                switch (state) {
                    case REJECTED:
                        callStatusOverlay.setVisibility(View.VISIBLE);
                        callStatusText.setText("已拒绝");
                        break;
                    case TIMEOUT:
                        callStatusOverlay.setVisibility(View.VISIBLE);
                        callStatusText.setText("未接听");
                        break;
                    case DISCONNECTED:
                        callStatusOverlay.setVisibility(View.VISIBLE);
                        callStatusText.setText("已挂断");
                        break;
                    default:
                        callStatusOverlay.setVisibility(View.GONE);
                        break;
                }
            }
            
            // 根据摄像头状态显示视频或头像
            boolean cameraEnabled = member.isCameraEnabled();
            if (avatarLayout != null) {
                avatarLayout.setVisibility(cameraEnabled ? View.GONE : View.VISIBLE);
            }
        }
        
        /**
         * 注册视频流到MultiStreamManager
         */
        private void registerVideoStream(GroupCallMember member) {
            if (callViewModel != null) {
                try {
                    // 注册视频流，使用优先级管理
                    boolean isMainVideo = (currentPosition == mainVideoPosition);
                    
                    // 注册视频流，使用优先级管理
                    StreamPriority streamPriority = isMainVideo ? StreamPriority.HIGH : StreamPriority.NORMAL;
                    callViewModel.registerVideoStream(
                        member.getUserID(), 
                        videoRenderer,
                        streamPriority
                    );
                    
                    L.d(TAG, "注册视频流: " + member.getUserID() + ", 优先级: " + streamPriority);
                } catch (Exception e) {
                    L.e(TAG, "注册视频流失败: " + member.getUserID(), e);
                }
            }
        }
        
        /**
         * 更新控制状态（麦克风、摄像头）
         */
        private void updateControlStates(GroupCallMember member) {
            // 更新麦克风状态
            if (micIcon != null) {
                boolean micEnabled = member.isMicrophoneEnabled();
                micIcon.setImageResource(micEnabled ? 
                    R.mipmap.ic_mic_s_on : R.mipmap.ic_mic_s_off);
                micIcon.setAlpha(micEnabled ? 1.0f : 0.5f);
            }
            
            // 更新摄像头状态
            if (cameraIcon != null) {
                boolean cameraEnabled = member.isCameraEnabled();
                cameraIcon.setImageResource(cameraEnabled ? 
                    R.mipmap.ic_open_camera : R.mipmap.ic_close_camera);
                cameraIcon.setVisibility(View.VISIBLE);
                cameraIcon.setAlpha(cameraEnabled ? 1.0f : 0.5f);
            }
        }
        
        /**
         * 更新选中状态
         */
        private void updateSelectionState(int position) {
            if (selectedBorder != null) {
                selectedBorder.setVisibility(
                    position == mainVideoPosition ? View.VISIBLE : View.GONE);
            }
        }
        
        /**
         * 更新说话状态指示器
         */
        private void updateSpeakingState(GroupCallMember member) {
            if (speakingIndicator != null) {
                boolean isSpeaking = member.isSpeaking();
                speakingIndicator.setVisibility(isSpeaking ? View.VISIBLE : View.GONE);
            }
        }
        
        /**
         * 更新网络质量指示器
         */
        private void updateNetworkQuality(GroupCallMember member) {
            if (networkQuality != null) {
                // 根据成员的网络质量显示相应的图标
                int quality = member.getNetworkQuality();
                int iconRes;
                
                switch (quality) {
                    case 1: // 优秀
                        // 使用现有的图标资源
                        iconRes = android.R.drawable.ic_dialog_info;
                        break;
                    case 2: // 良好  
                        iconRes = android.R.drawable.ic_dialog_alert;
                        break;
                    case 3: // 较差
                        iconRes = android.R.drawable.ic_dialog_dialer;
                        break;
                    default:
                        networkQuality.setVisibility(View.GONE);
                        return;
                }
                
                networkQuality.setImageResource(iconRes);
                networkQuality.setVisibility(View.VISIBLE);
            }
        }
        
        /**
         * 获取状态文字描述
         */
        private String getStateText(CallMemberState state) {
            switch (state) {
                case INVITING:
                    return "邀请中...";
                case CONNECTED:
                    return "通话中";
                case REJECTED:
                    return "已拒绝";
                case TIMEOUT:
                    return "未接听";
                case DISCONNECTED:
                    return "已挂断";
                case RINGING:
                    return "响铃中";
                case CONNECTING:
                    return "连接中";
                default:
                    return "等待中...";
            }
        }
    }
}