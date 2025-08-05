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
import java.util.concurrent.CopyOnWriteArrayList;

import io.livekit.android.renderer.TextureViewRenderer;
import io.openim.android.ouicalling.R;
import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicalling.entity.GroupCallMember;
import io.openim.android.ouicalling.utils.VideoResourcePool;
import io.openim.android.ouicalling.vm.CallViewModel;
import io.openim.android.ouicalling.manager.StreamPriority;
import io.openim.android.ouicore.widget.AvatarImage;

/**
 * 群组通话成员适配器
 * 负责渲染群组通话中每个成员的视频流和状态信息
 * Week 2 Day 6: 集成MultiStreamManager自动优先级管理
 */
public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.GroupMemberViewHolder> {
    
    private final Context context;
    private final VideoResourcePool resourcePool;
    private final CallViewModel callViewModel;  // Week 2 Day 6
    private final List<GroupCallMember> memberList = new ArrayList<>();
    
    public GroupMemberAdapter(Context context, VideoResourcePool resourcePool, CallViewModel callViewModel) {
        this.context = context;
        this.resourcePool = resourcePool;
        this.callViewModel = callViewModel;  // Week 2 Day 6
    }
    
    /**
     * 更新成员列表
     */
    public void updateMembers(List<GroupCallMember> members) {
        memberList.clear();
        memberList.addAll(members);
        notifyDataSetChanged();
    }
    
    /**
     * 释放所有视频渲染器资源
     * Week 2 Day 6: 同时从 MultiStreamManager 清理
     */
    public void releaseAllVideoRenderers() {
        // Week 2 Day 6: 从 MultiStreamManager 注销所有流
        if (callViewModel != null) {
            for (GroupCallMember member : memberList) {
                if (member != null) {
                    callViewModel.unregisterVideoStream(member.getUserID());
                }
            }
        }
        
        // 清理当前适配器中的成员视频渲染器
        for (GroupCallMember member : memberList) {
            if (member != null && member.getVideoRenderer() != null) {
                member.setVideoRenderer(null);
            }
        }
        
        // 注意：不直接清理整个resourcePool，因为它可能被其他组件使用
        // 具体的视频渲染器释放由VideoResourcePool统一管理
    }
    
    @NonNull
    @Override
    public GroupMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member_renderer, parent, false);
        return new GroupMemberViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GroupMemberViewHolder holder, int position) {
        if (position < memberList.size()) {
            GroupCallMember member = memberList.get(position);
            holder.bind(member);
        }
    }
    
    @Override
    public int getItemCount() {
        return memberList.size();
    }
    
    /**
     * 群组成员ViewHolder
     */
    public class GroupMemberViewHolder extends RecyclerView.ViewHolder {
        
        private final TextureViewRenderer videoRenderer;
        private final AvatarImage avatarImage;
        private final View avatarLayout;
        private final TextView nameText;
        private final ImageView micIcon;
        
        private GroupCallMember currentMember;
        
        public GroupMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            
            videoRenderer = itemView.findViewById(R.id.remoteSpeakerVideoView);
            avatarImage = itemView.findViewById(R.id.avatar);
            avatarLayout = itemView.findViewById(R.id.avatarRl);
            nameText = itemView.findViewById(R.id.name);
            micIcon = itemView.findViewById(R.id.micOn);
            
            // 从资源池获取VideoRenderer
            if (resourcePool != null) {
                resourcePool.initRenderer(videoRenderer);
            }
        }
        
        /**
         * 绑定成员数据
         */
        public void bind(GroupCallMember member) {
            if (member == null) return;
            
            this.currentMember = member;
            
            // 设置成员名称
            if (nameText != null) {
                nameText.setText(member.getNickname());
            }
            
            // 设置头像
            if (avatarImage != null) {
                avatarImage.load(member.getFaceURL(), member.getNickname());
            }
            
            // Week 2 Day 6: 注册视频流到MultiStreamManager
            registerVideoStream(member);
            
            // 根据成员状态显示不同UI
            updateMemberStateUI(member);
            
            // 设置麦克风状态
            updateMicrophoneState(member);
            
            // 设置视频状态
            updateVideoState(member);
        }
        
        /**
         * 更新成员状态UI
         */
        private void updateMemberStateUI(GroupCallMember member) {
            CallMemberState state = member.getState();
            
            switch (state) {
                case CALLING:
                    // 呼叫中：显示头像，隐藏视频
                    showAvatar();
                    break;
                    
                case CONNECTED:
                    // 已连接：根据摄像头状态决定显示头像还是视频
                    if (member.isCameraEnabled()) {
                        showVideo();
                    } else {
                        showAvatar();
                    }
                    break;
                    
                case DISCONNECTED:
                case REJECTED:
                case TIMEOUT:
                    // 断开连接：显示头像，可能需要灰化处理
                    showAvatar();
                    itemView.setAlpha(0.5f);  // 半透明表示离线状态
                    break;
                    
                default:
                    showAvatar();
                    break;
            }
        }
        
        /**
         * 更新麦克风状态图标
         */
        private void updateMicrophoneState(GroupCallMember member) {
            if (micIcon != null) {
                if (member.isMicrophoneEnabled()) {
                    micIcon.setImageResource(R.mipmap.ic_mic_s_on);
                    micIcon.setVisibility(View.VISIBLE);
                    micIcon.setAlpha(1.0f);
                } else {
                    // 使用现有图标但设置半透明表示关闭状态
                    micIcon.setImageResource(R.mipmap.ic_mic_s_on);
                    micIcon.setVisibility(View.VISIBLE);
                    micIcon.setAlpha(0.3f);  // 半透明表示麦克风关闭
                }
            }
        }
        
        /**
         * 更新视频状态
         */
        private void updateVideoState(GroupCallMember member) {
            if (member.getState() == CallMemberState.CONNECTED && member.isCameraEnabled()) {
                // 连接状态且摄像头开启：尝试绑定视频流
                bindVideoRenderer(member);
            } else {
                // 其他状态：解绑视频流
                unbindVideoRenderer();
            }
        }
        
        /**
         * Week 2 Day 6: 注册视频流到MultiStreamManager
         */
        private void registerVideoStream(GroupCallMember member) {
            if (callViewModel == null || member == null || videoRenderer == null) return;
            
            try {
                // 确定流优先级
                StreamPriority priority = determineStreamPriority(member);
                
                // 注册到MultiStreamManager
                callViewModel.registerVideoStream(member.getUserID(), videoRenderer, priority);
                
                android.util.Log.d("GroupMemberAdapter", "registerVideoStream: Registered stream for " + member.getUserID() + " with priority " + priority);
            } catch (Exception e) {
                android.util.Log.e("GroupMemberAdapter", "Error registering video stream for " + member.getUserID(), e);
            }
        }
        
        /**
         * Week 2 Day 6: 确定流优先级
         */
        private StreamPriority determineStreamPriority(GroupCallMember member) {
            // 根据成员状态决定优先级
            if (member.getState() == CallMemberState.CONNECTED && member.isCameraEnabled()) {
                // 当前说话者最高优先级（需要从其他地方获取说话状态）
                if (isMemberSpeaking(member)) {
                    return StreamPriority.HIGH;
                }
                return StreamPriority.NORMAL;
            } else {
                return StreamPriority.LOW;
            }
        }
        
        /**
         * Week 2 Day 6: 检查成员是否在说话（先简单实现）
         */
        private boolean isMemberSpeaking(GroupCallMember member) {
            // TODO: 集成实际的说话检测逻辑
            // 可以通过callViewModel.activeSpeakersMulti获取
            return false;  // 临时返回false
        }
        
        /**
         * 绑定视频渲染器到成员的视频流
         * Week 2 Day 6: 简化，由MultiStreamManager自动处理
         */
        private void bindVideoRenderer(GroupCallMember member) {
            if (videoRenderer == null || member == null) return;
            
            try {
                // 设置成员的视频渲染器引用
                member.setVideoRenderer(videoRenderer);
                
                // Week 2 Day 6: MultiStreamManager会自动处理视频绑定和优先级
                // 不需要手动绑定，MultiStreamManager会根据优先级自动管理
                showVideo();
                
                android.util.Log.d("GroupMemberAdapter", "bindVideoRenderer: Video renderer bound for member " + member.getUserID());
            } catch (Exception e) {
                android.util.Log.e("GroupMemberAdapter", "Error binding video renderer for member " + member.getUserID(), e);
            }
        }
        
        /**
         * 解绑视频渲染器
         * Week 2 Day 6: 同时从 MultiStreamManager 注销
         */
        private void unbindVideoRenderer() {
            if (currentMember != null) {
                // Week 2 Day 6: 从 MultiStreamManager 注销视频流
                if (callViewModel != null) {
                    callViewModel.unregisterVideoStream(currentMember.getUserID());
                }
                currentMember.setVideoRenderer(null);
            }
        }
        
        /**
         * 显示头像，隐藏视频
         */
        private void showAvatar() {
            if (avatarLayout != null) {
                avatarLayout.setVisibility(View.VISIBLE);
            }
            if (videoRenderer != null) {
                videoRenderer.setVisibility(View.GONE);
            }
        }
        
        /**
         * 显示视频，隐藏头像
         */
        private void showVideo() {
            if (avatarLayout != null) {
                avatarLayout.setVisibility(View.GONE);
            }
            if (videoRenderer != null) {
                videoRenderer.setVisibility(View.VISIBLE);
            }
        }
        
        /**
         * 获取当前绑定的视频渲染器
         */
        public TextureViewRenderer getVideoRenderer() {
            return videoRenderer;
        }
        
        /**
         * 获取当前绑定的成员
         */
        public GroupCallMember getCurrentMember() {
            return currentMember;
        }
    }
    
    /**
     * 更新成员列表数据
     */
    public void updateMembers(List<GroupCallMember> newMembers) {
        if (newMembers == null) {
            newMembers = new ArrayList<>();
        }
        
        // 更新数据
        this.memberList.clear();
        this.memberList.addAll(newMembers);
        
        // 通知数据变化
        notifyDataSetChanged();
        
        android.util.Log.d("GroupMemberAdapter", "updateMembers: Updated " + newMembers.size() + " members");
    }
    
    /**
     * 刷新视频绑定状态
     */
    public void refreshVideoBindings() {
        try {
            // 遍历所有可见的ViewHolder，刷新视频绑定
            if (recyclerView != null) {
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View childView = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(childView);
                    if (holder instanceof GroupMemberViewHolder) {
                        GroupMemberViewHolder memberHolder = (GroupMemberViewHolder) holder;
                        GroupCallMember member = memberHolder.getCurrentMember();
                        if (member != null && member.getState() == CallMemberState.CONNECTED && member.isCameraEnabled()) {
                            // 重新绑定视频渲染器
                            memberHolder.bindVideoRenderer(member);
                        }
                    }
                }
            }
            android.util.Log.d("GroupMemberAdapter", "refreshVideoBindings: Refreshed video bindings");
        } catch (Exception e) {
            android.util.Log.e("GroupMemberAdapter", "Error refreshing video bindings", e);
        }
    }
    
    /**
     * 设置RecyclerView引用用于视频绑定刷新
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }
    
    private RecyclerView recyclerView;
}