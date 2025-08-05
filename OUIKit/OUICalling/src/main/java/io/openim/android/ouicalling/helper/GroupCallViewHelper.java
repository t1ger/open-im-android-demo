package io.openim.android.ouicalling.helper;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import io.livekit.android.renderer.TextureViewRenderer;
import io.openim.android.ouicalling.R;
import io.openim.android.ouicore.widget.AvatarImage;

/**
 * 群组通话视图助手类
 * 用于管理群组通话布局中的所有UI控件，避免重复的findViewById调用
 */
public class GroupCallViewHelper {
    
    // 主要控件
    public final View rootView;
    public final ImageView zoomOut;
    public final LinearLayout cameraControl;
    public final CheckBox closeCamera;
    public final CheckBox switchCamera;
    public final TextureViewRenderer localSpeakerVideoView;
    public final RecyclerView viewRenderers;
    
    // 头部提示相关
    public final LinearLayout headTips;
    public final AvatarImage avatar;
    public final TextView tips1;
    public final TextView tips2;
    public final RecyclerView memberRecyclerView;
    
    // 接听/拒绝按钮
    public final LinearLayout ask;
    public final LinearLayout reject;
    public final LinearLayout answer;
    
    // 通话控制相关
    public final TextView timeTv;
    public final LinearLayout callingMenu;
    public final CheckBox micIsOn;
    public final LinearLayout hangUp;
    public final CheckBox speakerIsOn;
    
    // 主容器
    public final View home;
    public final View shrink;
    public final AvatarImage sAvatar; // 收缩模式头像
    public final TextView sTips; // 收缩模式提示
    public final View waiting; // 等待视图
    
    public GroupCallViewHelper(View rootView) {
        this.rootView = rootView;
        
        // 主要控件初始化
        zoomOut = findViewById(R.id.zoomOut);
        cameraControl = findViewById(R.id.cameraControl);
        closeCamera = findViewById(R.id.closeCamera);
        switchCamera = findViewById(R.id.switchCamera);
        localSpeakerVideoView = findViewById(R.id.localSpeakerVideoView);
        viewRenderers = findViewById(R.id.viewRenderers);
        
        // 头部提示相关
        headTips = findViewById(R.id.headTips);
        avatar = findViewById(R.id.avatar);
        tips1 = findViewById(R.id.tips1);
        tips2 = findViewById(R.id.tips2);
        memberRecyclerView = findViewById(R.id.memberRecyclerView);
        
        // 接听/拒绝按钮
        ask = findViewById(R.id.ask);
        reject = findViewById(R.id.reject);
        answer = findViewById(R.id.answer);
        
        // 通话控制相关
        timeTv = findViewById(R.id.timeTv);
        callingMenu = findViewById(R.id.callingMenu);
        micIsOn = findViewById(R.id.micIsOn);
        hangUp = findViewById(R.id.hangUp);
        speakerIsOn = findViewById(R.id.speakerIsOn);
        
        // 主容器
        home = findViewById(R.id.home);
        shrink = findViewById(R.id.shrink);
        sAvatar = findViewById(R.id.sAvatar);
        sTips = findViewById(R.id.sTips);
        waiting = findViewById(R.id.waiting);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        return (T) rootView.findViewById(id);
    }
}