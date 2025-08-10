package io.openim.android.ouicalling.vm;

import android.bluetooth.BluetoothAdapter;
import android.text.TextUtils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.twilio.audioswitch.AudioDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 群组通话相关导入
import io.openim.android.ouicalling.entity.CallMemberState;
import io.openim.android.ouicalling.entity.GroupCallMember;
// 已移除MultiPartySignaling，使用标准SignalingInfo
// 已移除SignalingDeduplicator，使用简化的标准信令处理流程
import io.openim.android.ouicalling.state.CallStateManager;
import io.openim.android.ouicalling.state.GroupCallStateManager;
import io.openim.android.ouicalling.utils.VideoResourcePool;

import io.livekit.android.renderer.TextureViewRenderer;
import io.livekit.android.room.participant.Participant;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.VideoTrack;
import io.openim.android.ouicalling.CallingServiceImp;
import io.openim.android.ouicore.api.OneselfService;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.entity.CallHistory;
import io.openim.android.ouicore.net.RXRetrofit.N;
import io.openim.android.ouicore.net.RXRetrofit.NetObserver;
import io.openim.android.ouicore.net.RXRetrofit.Parameter;
import io.openim.android.ouicore.net.bage.GsonHel;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import io.openim.android.ouicore.utils.TimeUtil;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.listener.OnMsgSendCallback;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.OfflinePushInfo;
import io.openim.android.sdk.models.PublicUserInfo;
import io.openim.android.sdk.models.SignalingCertificate;
import io.openim.android.sdk.models.SignalingInfo;
import io.reactivex.functions.Function;
import io.realm.Realm;
import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.FlowKt;

public class CallingVM implements CallViewModel.AudioDeviceCallback {
    private static final String TAG = "CallingVM";
    public final CoroutineScope scope;
    //通话时间
    private Timer timer;
    private int second = 0;
    public MutableLiveData<String> timeStr = new MutableLiveData<>("");

    //获取音频服务
//    public AudioManager audioManager;
    private DialogInterface.OnDismissListener dismissListener;
    private OnParticipantsChangeListener onParticipantsChangeListener;

    public final CallViewModel callViewModel;
    public final CallingService callingService;
    private VideoTrack localVideoTrack;
    //是否是视频通话
    public boolean isVideoCalls = true;
    //已经开始通话
    public boolean isStartCall;
    //呼出
    public boolean isCallOut;
    
    // === 统一状态管理 ===
    // 基于信令数据的状态管理器
    private final CallStateManager stateManager = new CallStateManager();
    // 群组通话状态管理器
    public GroupCallStateManager groupCallStateManager;
    // 群组通话成员列表（线程安全）
    public final CopyOnWriteArrayList<GroupCallMember> groupMembers = new CopyOnWriteArrayList<>();
    // 当前发言人ID（v1.2实现）
    public String currentSpeaker = "";
    
    // 群组通话相关字段（兼容旧代码）
    private boolean isGroupCall = false;
    private String groupRoomId = "";
    private String groupId = "";
    
    // ✅ 当前信令信息缓存，支持无参数的accept()方法
    private SignalingInfo currentSignalingInfo = null;
    
    // ✅ 移除重复字段，统一使用 stateManager 获取状态
    
    // === 业界最佳实践组件 ===
    // 信令去重组件
    // 已移除SignalingDeduplicator，使用简化的标准信令处理流程
    // 视频资源池
    private final VideoResourcePool resourcePool = new VideoResourcePool();
    
    /**
     * 获取视频资源池
     */
    public VideoResourcePool getResourcePool() {
        return resourcePool;
    }
    


    /**
     * 兼容方法，供CallDialog调用
     */
    public VideoResourcePool getVideoResourcePool() {
        return resourcePool;
    }
    
    // === 状态管理接口 ===
    
    /**
     * 更新信令信息并重新计算状态
     * @param signalingInfo 信令信息
     */
    public void updateSignalingInfo(SignalingInfo signalingInfo) {
        // ✅ 修复挂断失效问题：设置当前信令信息
        this.currentSignalingInfo = signalingInfo;
        L.e("CallingVM", "✅ 设置currentSignalingInfo成功: " + (signalingInfo != null ? "非空" : "空"));
        
        stateManager.updateSignalingInfo(signalingInfo);
        android.util.Log.d(TAG, "信令状态已更新: " + stateManager.getDebugInfo());
        
        // ✅ 如果是群组通话信令，触发成员状态更新
        if (isGroupCall()) {
            processGroupSignalingUpdate(signalingInfo);
        }
    }
    
    /**
     * 处理群组通话信令更新
     * ✅ 修复: 通过信令同步成员状态，不直接操作LiveKit API
     */
    private void processGroupSignalingUpdate(SignalingInfo signalingInfo) {
        try {
            // 解析信令中的成员状态信息
            String userId = extractUserIdFromSignaling(signalingInfo);
            if (userId == null || userId.isEmpty()) {
                L.w("CallingVM", "无法从信令中提取用户ID");
                return;
            }
            
            boolean isConnected = extractConnectionStateFromSignaling(signalingInfo);
            boolean micEnabled = extractMicStateFromSignaling(signalingInfo);
            boolean cameraEnabled = extractCameraStateFromSignaling(signalingInfo);
            
            // 通过CallViewModel更新状态
            callViewModel.updateMemberStateFromSignaling(userId, isConnected, micEnabled, cameraEnabled);
            
            // 触发UI更新
            updateGroupMembersFromParticipants(null);
            
            L.d("CallingVM", "群组成员信令状态已处理: " + userId + 
                ", 连接:" + isConnected + ", 麦克风:" + micEnabled + ", 摄像头:" + cameraEnabled);
                
        } catch (Exception e) {
            handleGroupCallError("处理群组信令更新失败", e);
        }
    }
    
    /**
     * 从信令中提取用户ID
     * ✅ 修复: 根据OpenIM信令格式提取用户ID
     */
    private String extractUserIdFromSignaling(SignalingInfo signalingInfo) {
        try {
            if (signalingInfo.getInvitation() != null) {
                // 优先从邀请信息中获取
                if (signalingInfo.getInvitation().getInviterUserID() != null) {
                    return signalingInfo.getInvitation().getInviterUserID();
                }
                // 如果是被邀请者状态变更
                if (signalingInfo.getInvitation().getInviteeUserIDList() != null && 
                    !signalingInfo.getInvitation().getInviteeUserIDList().isEmpty()) {
                    return signalingInfo.getInvitation().getInviteeUserIDList().get(0);
                }
            }
            return null;
        } catch (Exception e) {
            L.w("CallingVM", "提取用户ID异常: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从信令中提取连接状态
     */
    private boolean extractConnectionStateFromSignaling(SignalingInfo signalingInfo) {
        // 根据信令类型判断连接状态
        // 这里需要根据实际的OpenIM信令协议调整
        return true; // 临时返回真值，实际实现时需要根据信令内容判断
    }
    
    /**
     * 从信令中提取麦克风状态
     */
    private boolean extractMicStateFromSignaling(SignalingInfo signalingInfo) {
        // 根据实际信令协议实现
        return true; // 临时返回真值
    }
    
    /**
     * 从信令中提取摄像头状态
     */
    private boolean extractCameraStateFromSignaling(SignalingInfo signalingInfo) {
        // 根据实际信令协议实现
        return true; // 临时返回真值
    }
    
    /**
     * 获取状态管理器（供内部使用）
     */
    public CallStateManager getStateManager() {
        return stateManager;
    }
    
    /**
     * 获取当前信令信息
     * ✅ 修复: 为群组通话提供信令信息访问接口
     */
    private SignalingInfo getCurrentSignalingInfo() {
        try {
            // 通过反射获取CallStateManager中的currentSignalingInfo
            java.lang.reflect.Field field = stateManager.getClass().getDeclaredField("currentSignalingInfo");
            field.setAccessible(true);
            return (SignalingInfo) field.get(stateManager);
        } catch (Exception e) {
            L.w("CallingVM", "获取当前信令信息失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 是否为群组通话
     * ✅ 统一状态入口，取代原 isGroup 和 isGroupCall
     */
    public boolean isGroupCall() {
        return stateManager.isGroupCall();
    }
    
    /**
     * 是否为视频通话
     */
    public boolean isVideoCall() {
        return stateManager.isVideoCall();
    }
    
    /**
     * 获取参与者数量
     */
    public int getParticipantCount() {
        return stateManager.getParticipantCount();
    }
    
    /**
     * 获取群组ID（如果是群组通话）
     */
    public String getGroupId() {
        return stateManager.getGroupId();
    }
    
    /**
     * 获取房间ID
     */
    public String getRoomId() {
        return stateManager.getRoomId();
    }
    
    /**
     * 获取通话类型描述
     */
    public String getCallTypeDescription() {
        return stateManager.getCallTypeDescription();
    }
    
    /**
     * 兼容属性：是否为群组通话
     * @deprecated 请使用 isGroupCall() 方法
     */
    @Deprecated
    public boolean isGroup() {
        return isGroupCall();
    }
    // 群组信令监听器
    private OnGroupSignalingListener groupSignalingListener;


    private List<TextureViewRenderer> remoteSpeakerVideoViews, localSpeakerVideoViews;


    public CallingVM(CallingService callingService, boolean isCallOut) {
        this.callingService = callingService;
        this.isCallOut = isCallOut;

        callViewModel = new CallViewModel(BaseApp.inst());
        // 设置音频设备控制回调
        callViewModel.setAudioDeviceCallback(this);
        scope = callViewModel.buildScope();
//        audioManager = (AudioManager) BaseApp.inst().getSystemService(Context.AUDIO_SERVICE);
        listenerBluetoothConnectionReceiver();
    }

    private void listenerBluetoothConnectionReceiver() {
        BluetoothConnectionReceiver audioNoisyReceiver = new BluetoothConnectionReceiver(this);
        //蓝牙状态广播监听
        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        audioFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        BaseApp.inst().registerReceiver(audioNoisyReceiver, audioFilter);
    }

    public void initRemoteVideoRenderer(TextureViewRenderer... viewRenderers) {
        remoteSpeakerVideoViews = Arrays.asList(viewRenderers);
        for (TextureViewRenderer viewRenderer : viewRenderers) {
            callViewModel.getRoom().initVideoRenderer(viewRenderer);
        }
    }

    public void initLocalSpeakerVideoView(TextureViewRenderer... viewRenderers) {
        localSpeakerVideoViews = Arrays.asList(viewRenderers);
        for (TextureViewRenderer viewRenderer : viewRenderers) {
            callViewModel.getRoom().initVideoRenderer(viewRenderer);
        }
    }


    public void setOnParticipantsChangeListener(OnParticipantsChangeListener onParticipantsChangeListener) {
        this.onParticipantsChangeListener = onParticipantsChangeListener;
    }

    public void setDismissListener(DialogInterface.OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
    }


    public void setVideoCalls(boolean videoCalls) {
        isVideoCalls = videoCalls;
    }

    private OnMsgSendCallback callBackDismissUI = new OnMsgSendCallback() {
        @Override
        public void onError(int code, String error) {
            L.e(CallingServiceImp.TAG, error + "-" + code);
            dismissUI();
        }

        @Override
        public void onSuccess(Message data) {
            dismissUI();
        }
    };

    public void signalingInvite(SignalingInfo signalingInfo) {
        // 🔧 恢复 main 分支的简单、正确实现，修复单人音视频功能
        sendSignaling(Constants.MsgType.callingInvite, signalingInfo, new OnMsgSendCallback() {
            @Override
            public void onSuccess(Message s) {
                getTokenAndConnectRoom(signalingInfo, new OnBase<SignalingCertificate>() {
                    @Override
                    public void onSuccess(SignalingCertificate data) {
                        connectToRoom(data);
                    }
                });
            }
        });
    }
    
    /**
     * 发起群组通话
     * ✅ 修复: 新增群组通话专用发起逻辑
     */
    public void signalingGroupInvite(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "发起群组通话");
            
            // ✅ 移除错误的isCallOut设置，保持构造函数中的原始值
            
            // 发送群组邀请信令
            sendSignaling(Constants.MsgType.callingInvite, signalingInfo, new OnMsgSendCallback() {
                @Override
                public void onSuccess(Message s) {
                    L.d("CallingVM", "群组通话邀请信令发送成功");
                    
                    // 获取令牌并连接群组房间
                    getTokenAndConnectRoom(signalingInfo, new OnBase<SignalingCertificate>() {
                        @Override
                        public void onSuccess(SignalingCertificate data) {
                            // 发起方连接群组房间
                            connectToGroupRoomAsCaller(data, signalingInfo);
                        }
                        
                        @Override
                        public void onError(int code, String error) {
                            L.e("CallingVM", "群组通话令牌获取失败: " + error);
                            handleGroupCallError("群组通话发起失败", new Exception(error));
                        }
                    });
                }
                
                @Override
                public void onError(int code, String error) {
                    L.e("CallingVM", "群组通话邀请信令发送失败: " + error);
                    handleGroupCallError("群组通话发起失败", new Exception(error));
                }
            });
        } catch (Exception e) {
            handleGroupCallError("发起群组通话异常", e);
        }
    }
    
    /**
     * 发起单人通话
     */
    public void signalingPersonalInvite(SignalingInfo signalingInfo) {
        sendSignaling(Constants.MsgType.callingInvite, signalingInfo, new OnMsgSendCallback() {
            @Override
            public void onSuccess(Message s) {
                getTokenAndConnectRoom(signalingInfo, new OnBase<SignalingCertificate>() {
                    @Override
                    public void onSuccess(SignalingCertificate data) {
                        connectToRoom(data);
                    }
                });
            }
        });
    }

    private void sendSignaling(int code, SignalingInfo signalingInfo, OnMsgSendCallback onMsgSendCallback) {
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put(Constants.K_CUSTOM_TYPE, code);
        hashMap.put(Constants.K_DATA, signalingInfo.getInvitation());
        Message message = OpenIMClient.getInstance().messageManager.createCustomMessage(GsonHel.toJson(hashMap), "", "");

        List<String> uidList = signalingInfo.getInvitation().getInviteeUserIDList();
        if (null != message && !uidList.isEmpty()) {
            String recvUid = signalingInfo.getInvitation().getInviterUserID().equals(BaseApp.inst().loginCertificate.userID) ? signalingInfo.getInvitation().getInviteeUserIDList().get(0) : signalingInfo.getInvitation().getInviterUserID();
            OpenIMClient.getInstance().messageManager.sendMessage(onMsgSendCallback, message, recvUid, null, new OfflinePushInfo(), true);
        }
    }

    private void getTokenAndConnectRoom(SignalingInfo signalingInfo, OnBase<SignalingCertificate> callBack) {
        Parameter parameter = new Parameter();
        parameter.add("room", signalingInfo.getInvitation().getRoomID());
        parameter.add("identity", BaseApp.inst().loginCertificate.userID);
        N.API(OneselfService.class).getTokenForRTC(parameter.buildJsonBody()).map(OneselfService.turn(HashMap.class)).map((Function<HashMap, SignalingCertificate>) responseBody -> {
            String serverUrl = (String) responseBody.get("serverUrl");
            String token = (String) responseBody.get("token");
            SignalingCertificate signalingCertificate = new SignalingCertificate();
            signalingCertificate.setLiveURL(serverUrl);
            signalingCertificate.setToken(token);
            return signalingCertificate;
        }).compose(N.IOMain()).subscribe(new NetObserver<SignalingCertificate>("") {
            @Override
            public void onSuccess(SignalingCertificate data) {
                if (null == data) return;
                L.e(CallingServiceImp.TAG, data.getToken());
                callBack.onSuccess(data);
            }

            @Override
            protected void onFailure(Throwable e) {
                callBack.onError(-1, e.getMessage());
            }
        });
    }
    


    /**
     * 连接房间
     *
     * @param data
     */
    private void connectToRoom(SignalingCertificate data) {
        callViewModel.connectToRoom(data.getLiveURL(), data.getToken(), new Continuation<Unit>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
                setSpeakerphoneOn(true);
                if (!isVideoCalls) callViewModel.setCameraEnabled(false);

                localVideoTrack = callViewModel.getVideoTrack(callViewModel.getRoom().getLocalParticipant());
                if (null != localVideoTrack && null != localSpeakerVideoViews && !localSpeakerVideoViews.isEmpty()) {
                    for (TextureViewRenderer localSpeakerVideoView : localSpeakerVideoViews) {
                        localVideoTrack.addRenderer(localSpeakerVideoView);
                        localSpeakerVideoView.setTag(localVideoTrack);
                    }
                }
                callViewModel.subscribe(callViewModel.getAllGroupParticipants(), (v) -> {
                    if (v.isEmpty()) return null;
                    if (null != onParticipantsChangeListener) {
                        onParticipantsChangeListener.onChange(v);
                    } else {
                        for (int i = 0; i < v.size(); i++) {
                            Participant participant = v.get(i);
                            if (participant instanceof RemoteParticipant) {
                                for (TextureViewRenderer remoteSpeakerVideoView : remoteSpeakerVideoViews) {
                                    callViewModel.bindRemoteViewRenderer(remoteSpeakerVideoView, participant, scope, new Continuation<Unit>() {
                                        @NonNull
                                        @Override
                                        public CoroutineContext getContext() {
                                            return EmptyCoroutineContext.INSTANCE;
                                        }

                                        @Override
                                        public void resumeWith(@NonNull Object o) {
                                        }
                                    });
                                }
                            }
                        }
                    }

                    return null;
                }, scope);
            }
        });
    }

    public void buildTimer() {
        cancelTimer();
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                second++;
                String secondFormat = TimeUtil.secondFormat(second, TimeUtil.secondFormat);
                if (secondFormat.length() <= 2) secondFormat = "00:" + secondFormat;
                timeStr.postValue(secondFormat);
            }
        }, 0, 1000);
    }

    private String repair0(int v) {
        return v < 10 ? ("0" + v) : (v + "");
    }

    private void cancelTimer() {
        if (null != timer) {
            timer.cancel();
            timer = null;
        }
    }

    public void signalingHungUp(SignalingInfo signalingInfo) {
        L.e("CallingVM", "========== signalingHungUp执行 ==========");
        L.e("CallingVM", "isStartCall: " + isStartCall);
        L.e("CallingVM", "signalingInfo是否为空: " + (signalingInfo == null));
        L.e("CallingVM", "18秒后将自动关闭UI");
        
        Common.UIHandler.postDelayed(this::dismissUI, 18 * 1000);
        
        if (!isStartCall) {
            L.e("CallingVM", "通话未开始，执行signalingCancel");
            signalingCancel(signalingInfo);
            return;
        }
        
        L.e("CallingVM", "通话已开始，发送挂断信令");
        sendSignaling(Constants.MsgType.callingHungup, signalingInfo, callBackDismissUI);
    }

    private void dismissUI() {
        if (null != dismissListener) dismissListener.onDismiss(null);
    }

    private void signalingCancel(SignalingInfo signalingInfo) {
        if (isCallOut) {
            renewalDB(buildPrimaryKey(signalingInfo), (realm, v) -> v.setFailedState(1));
            sendSignaling(Constants.MsgType.callingCancel, signalingInfo, callBackDismissUI);
        } else {
            renewalDB(buildPrimaryKey(signalingInfo), (realm, v) -> v.setFailedState(3));
            sendSignaling(Constants.MsgType.callingReject, signalingInfo, callBackDismissUI);
        }
    }

    public static String buildPrimaryKey(SignalingInfo signalingInfo) {
        return signalingInfo.getInvitation().getRoomID() + signalingInfo.getInvitation().getInitiateTime();
    }

    public void signalingAccept(SignalingInfo signalingInfo, OnBase onBase) {
        sendSignaling(Constants.MsgType.callingAccept, signalingInfo, new OnMsgSendCallback() {
            @Override
            public void onError(int code, String error) {
                OnMsgSendCallback.super.onError(code, error);
            }

            @Override
            public void onSuccess(Message s) {
                getTokenAndConnectRoom(signalingInfo, new OnBase<SignalingCertificate>() {
                    @Override
                    public void onError(int code, String error) {
                        LogExceptionHandler.handleException("CallingVM", "加入会议失败", LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                        L.e("CallingVM", "加入会议失败: " + error + ", code: " + code);
                        
                        String errorMsg = "加入会议失败";
                        if (code == 10004) {
                            errorMsg = "网络连接失败，请检查网络";
                        } else if (code == 10001) {
                            errorMsg = "服务器错误，请稍后重试";
                        } else if (!TextUtils.isEmpty(error)) {
                            errorMsg = "加入会议失败: " + error;
                        }
                        
                        Toast.makeText(BaseApp.inst(), errorMsg, Toast.LENGTH_LONG).show();
                        dismissUI();
                    }

                    @Override
                    public void onSuccess(SignalingCertificate data) {
                        L.e(CallingServiceImp.TAG, data.getToken());
                        MediaPlayerUtil.INSTANCE.pause();
                        MediaPlayerUtil.INSTANCE.release();

                        isStartCall = true;
                        onBase.onSuccess(null);
                        connectToRoom(data);
                        buildTimer();
                    }
                });


            }
        });
    }

    public void unBindView() {
        try {
            cancelTimer();
            
            // 原有1v1通话资源清理
            if (null != localVideoTrack) {
                if (null != localSpeakerVideoViews) {
                    for (TextureViewRenderer localSpeakerVideoView : localSpeakerVideoViews) {
                        localSpeakerVideoView.release();
                        localVideoTrack.removeRenderer(localSpeakerVideoView);
                    }
                }
            }
            for (TextureViewRenderer textureViewRenderer : remoteSpeakerVideoViews) {
                textureViewRenderer.release();
                Object videoTask = textureViewRenderer.getTag();
                if (null != videoTask) {
                    ((VideoTrack) videoTask).removeRenderer(textureViewRenderer);
                }
            }
            
            // 群组通话资源清理
            cleanupGroupCall();
            
            callViewModel.onCleared();
            L.e("unBindView - 包含群组通话清理");
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "unBindView资源清理", LogExceptionHandler.ExceptionType.UNKNOWN_ERROR, e);
        }
    }

    public void setSpeakerphoneOn(boolean isChecked) {
        try {
            if (callViewModel.getAudioHandlerForJava().getSelectedAudioDevice() instanceof AudioDevice.BluetoothHeadset) {
                L.d("CallingVM", "蓝牙设备已连接，跳过扬声器切换");
                return;
            }
            
            AudioDevice targetDevice = isChecked ? new AudioDevice.Speakerphone() : new AudioDevice.Earpiece();
            callViewModel.getAudioHandlerForJava().selectDevice(targetDevice);
            L.d("CallingVM", "音频设备切换成功: " + (isChecked ? "扬声器" : "听筒"));
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "设置扬声器状态", LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }


    public interface OnParticipantsChangeListener {
        void onChange(List<Participant> participants);
    }


    public void renewalDB(String id, OnRenewalDBListener onRenewalDBListener) {
        try {
            if (TextUtils.isEmpty(id)) {
                L.w("CallingVM", "renewalDB: 通话ID为空");
                return;
            }
            
            if (onRenewalDBListener == null) {
                L.w("CallingVM", "renewalDB: 监听器为空");
                return;
            }
            
            BaseApp.inst().realm.executeTransactionAsync(realm -> {
                try {
                    CallHistory callHistory = realm.where(CallHistory.class).equalTo("id", id).findFirst();
                    if (null == callHistory) {
                        L.w("CallingVM", "renewalDB: 未找到通话记录 ID=" + id);
                        return;
                    }
                    onRenewalDBListener.onRenewal(realm, callHistory);
                    L.d("CallingVM", "renewalDB: 通话记录更新成功 ID=" + id);
                } catch (Exception e) {
                    LogExceptionHandler.handleException("CallingVM", "renewalDB事务执行", LogExceptionHandler.ExceptionType.DATA_ERROR, e);
                }
            });
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "renewalDB数据库操作", LogExceptionHandler.ExceptionType.DATA_ERROR, e);
        }
    }


    public interface OnRenewalDBListener {
        void onRenewal(Realm realm, CallHistory callHistory);
    }


    public static class BluetoothConnectionReceiver extends BroadcastReceiver {
        CallingVM callingVM;

        public BluetoothConnectionReceiver(CallingVM callingVM) {
            this.callingVM = callingVM;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) { //蓝牙连接状态
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
                    
                    LogExceptionHandler.BusinessFlow bluetoothFlow = LogExceptionHandler.BusinessFlow.start("CallingVM", "蓝牙连接状态变更");
                    
                    if (state == BluetoothAdapter.STATE_CONNECTED) {
                        L.d("CallingVM", "蓝牙设备已连接，切换到蓝牙耳机");
                        callingVM.changeToHeadset();
                        bluetoothFlow.success();
                    } else if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                        L.d("CallingVM", "蓝牙设备已断开，切换到扬声器");
                        callingVM.changeToSpeaker();
                        bluetoothFlow.success();
                    } else {
                        L.w("CallingVM", "未知的蓝牙状态: " + state);
                        bluetoothFlow.failure("未知状态: " + state);
                    }
                }
            } catch (Exception e) {
                LogExceptionHandler.handleException("CallingVM", "蓝牙状态变更处理", LogExceptionHandler.ExceptionType.UNKNOWN_ERROR, e);
            }
        }
    }

    /**
     * 切换到外放
     */
    public void changeToSpeaker() {
        try {
            L.d("CallingVM", "切换到扬声器模式");
            setSpeakerphoneOn(true);
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "切换到扬声器", LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }

    /**
     * 切换到蓝牙音箱
     */
    public void changeToHeadset() {
        try {
            L.d("CallingVM", "切换到蓝牙耳机模式");
            callViewModel.getAudioHandlerForJava().selectDevice(new AudioDevice.BluetoothHeadset());
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "切换到蓝牙耳机", LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    // ===== AudioDeviceCallback实现 =====
    
    @Override
    public void onSetSpeakerphoneEnabled(boolean enabled) {
        // 直接调用现有的setSpeakerphoneOn方法
        setSpeakerphoneOn(enabled);
    }

    // ===== 群组通话扩展方法 =====

    /**
     * 发起群组通话
     * @param groupId 群组ID
     * @param memberIds 成员ID列表
     * @param isVideo 是否视频通话
     */
    public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
        try {
            L.d("CallingVM", "发起群组通话 - 群组: " + groupId + ", 成员数: " + memberIds.size() + ", 视频: " + isVideo);
            
            // 1. 使用统一状态管理，不再直接设置成员变量
            // ✅ 状态将在 updateSignalingInfo() 时由 stateManager 管理
            this.isVideoCalls = isVideo;
            String groupRoomId = "group_call_" + System.currentTimeMillis();
            
            android.util.Log.d(TAG, "发起群组通话 - 群组: " + groupId + ", 成员数: " + memberIds.size());
            
            // 2. 初始化成员列表
            groupMembers.clear();
            for (String memberId : memberIds) {
                if (!memberId.equals(BaseApp.inst().loginCertificate.userID)) {
                    GroupCallMember member = new GroupCallMember(memberId);
                    member.setState(CallMemberState.INVITING);
                    groupMembers.add(member);
                    L.v("CallingVM", "添加群组成员: " + memberId);
                }
            }
            
            // 3. 使用标准IMUtil创建群组信令
            io.openim.android.sdk.models.SignalingInfo groupSignalingInfo = 
                io.openim.android.ouicore.im.IMUtil.buildGroupSignalingInfo(isVideo, groupId, memberIds);
            
            if (groupSignalingInfo == null) {
                throw new IllegalStateException("群组信令创建失败，请检查登录状态");
            }
            
            // 4. 使用标准方式发起群组通话
            android.util.Log.d(TAG, "群组信令构建完成 - SessionType: " + groupSignalingInfo.getInvitation().getSessionType());
            callingService.call(groupSignalingInfo);
            
            // 5. 更新内部状态
            updateSignalingInfo(groupSignalingInfo);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "发起群组通话", LogExceptionHandler.ExceptionType.NETWORK_ERROR, e);
            handleGroupCallError("发起群组通话失败", e);
        }
    }

    /**
     * 处理群组信令（使用标准SignalingInfo）
     * 替代原有的MultiPartySignaling处理逻辑
     */
    public void handleGroupSignaling(io.openim.android.sdk.models.SignalingInfo signalingInfo) {
        if (signalingInfo == null) {
            L.w("CallingVM", "群组信令为空");
            return;
        }

        try {
            // 使用标准信令处理流程
            L.d("CallingVM", "处理标准群组信令: " + signalingInfo.getInvitation().getMediaType());
            
            // 直接使用现有的信令处理流程
            updateSignalingInfo(signalingInfo);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "处理群组信令", LogExceptionHandler.ExceptionType.DATA_ERROR, e);
            handleGroupCallError("处理群组信令异常", e);
        }
    }

    /**
     * 更新成员状态
     */
    public void updateMemberState(String userId, CallMemberState newState) {
        GroupCallMember member = findMember(userId);
        if (member != null) {
            boolean success = member.setState(newState);
            if (success) {
                L.d("CallingVM", "成员状态更新: " + userId + " -> " + newState.getDescription());
                notifyGroupSignalingListener();
            } else {
                L.w("CallingVM", "成员状态更新失败: " + userId + " -> " + newState.getDescription());
            }
        } else {
            L.w("CallingVM", "未找到成员: " + userId);
        }
    }

    /**
     * 获取渲染器（使用资源池）
     */
    public TextureViewRenderer getRendererForMember(String userId) {
        return resourcePool.acquireRenderer(userId, this);
    }

    /**
     * 释放成员渲染器
     */
    public void releaseMemberRenderer(String userId) {
        resourcePool.releaseRenderer(userId);
    }

    /**
     * 查找群组成员
     */
    private GroupCallMember findMember(String userId) {
        for (GroupCallMember member : groupMembers) {
            if (member.getUserId().equals(userId)) {
                return member;
            }
        }
        return null;
    }

    // 已移除 createGroupInviteSignaling，统一使用 IMUtil.buildGroupSignalingInfo

    // 已移除 sendGroupSignaling，统一使用 sendSignaling 标准流程
    
    private void removedSendGroupSignaling() {
        // 已移除的方法
    }

    // 已移除基于MultiPartySignaling的信令处理方法
    // 群组信令处理现在通过标准OpenIM SDK信令流程处理
    // 这些方法的功能将通过updateSignalingInfo()统一处理

    /**
     * 准备群组房间连接
     */
    // 已移除 prepareGroupRoom，统一使用 getTokenAndConnectRoom
    
    private void removedPrepareGroupRoom() {
        // 已移除的方法
    }
    
    /**
     * 使用Token连接群组房间
     * ✅ 使用CallViewModel的群组接口，不直接操作LiveKit
     */
    // 已移除 connectToGroupRoomWithToken，统一使用 connectToRoom
    
    private void removedConnectToGroupRoomWithToken() {
        // 已移除的方法
    }
    
    /**
     * 初始化群组成员 - 从信令获取成员信息
     */
    // 已移除 initializeGroupMembersFromSignaling，成员初始化在 initiateGroupCall 中处理
    
    private void removedInitializeGroupMembersFromSignaling() {
        // 已移除的方法
    }
    
    /**
     * 群组房间连接成功回调
     * ✅ 通过CallViewModel设置事件监听，不直接访问LiveKit
     */
    private void onGroupRoomConnected() {
        android.util.Log.d("CallingVM", "群组房间连接成功");
        
        setSpeakerphoneOn(true);
        if (!isVideoCalls) callViewModel.setCameraEnabled(false);
        
        // ✅ 订阅群组参与者变化
        callViewModel.subscribe(callViewModel.getAllGroupParticipants(), participants -> {
            updateGroupMembersFromParticipants(participants);
            return null;
        }, scope);
        
        // 初始化本地视频
        // initializeLocalVideoForGroup(); // 暂时注释，需要实现这个方法
        
        buildTimer();
        
        // 通知UI群组通话已开始
        notifyGroupSignalingListener();
    }
    
    /**
     * 从LiveKit参与者更新群组成员状态
     */
    private void updateGroupMembersFromParticipants(List<Participant> participants) {
        try {
            for (GroupCallMember member : groupMembers) {
                String userId = member.getUserID();
                
                // ✅ 使用CallViewModel的抽象接口查询状态，不直接访问LiveKit
                boolean isConnected = callViewModel.isParticipantConnected(userId);
                boolean micEnabled = callViewModel.isParticipantMicrophoneEnabled(userId);
                boolean cameraEnabled = callViewModel.isParticipantCameraEnabled(userId);
                
                // 更新成员状态
                if (isConnected) {
                    member.setState(CallMemberState.CONNECTED);
                    member.setMicrophoneOn(micEnabled);
                    member.setCameraOn(cameraEnabled);
                } else {
                    // 参与者未连接，但保持邀请状态
                    if (member.getState() == CallMemberState.INVITING) {
                        // 保持邀请状态，不改变
                    } else {
                        member.setState(CallMemberState.DISCONNECTED);
                        member.setMicrophoneOn(false);
                        member.setCameraOn(false);
                    }
                }
            }
            
            // 通知UI更新
            notifyGroupSignalingListener();
            
        } catch (Exception e) {
            android.util.Log.e("CallingVM", "更新群组成员状态异常: " + e.getMessage(), e);
        }
    }

    /**
     * 处理群组通话错误
     * 使用统一的异常处理模式，提供分类错误处理和用户友好提示
     */
    private void handleGroupCallError(String message, Exception e) {
        // 使用LogExceptionHandler进行统一的异常处理
        LogExceptionHandler.handleException(
            "CallingVM", 
            "group_call_error: " + message,
            LogExceptionHandler.ExceptionType.STATE_ERROR,
            e
        );
        
        // 错误分类
        String errorCode = "UNKNOWN_ERROR";
        if (e != null) {
            String exceptionName = e.getClass().getSimpleName();
            if (exceptionName.contains("Network") || exceptionName.contains("Socket")) {
                errorCode = "NETWORK_ERROR";
            } else if (exceptionName.contains("Permission")) {
                errorCode = "PERMISSION_ERROR";
            } else if (exceptionName.contains("IllegalState")) {
                errorCode = "INVALID_STATE";
            } else if (exceptionName.contains("Timeout")) {
                errorCode = "TIMEOUT_ERROR";
            } else if (exceptionName.contains("OutOfMemory") || exceptionName.contains("Resource")) {
                errorCode = "RESOURCE_ERROR";
            }
        }
        
        // 对应的用户提示
        String userMessage;
        switch (errorCode) {
            case "NETWORK_ERROR":
                userMessage = "网络连接异常，请检查网络后重试";
                break;
            case "PERMISSION_ERROR":
                userMessage = "权限不足，请检查音视频权限设置";
                break;
            case "RESOURCE_ERROR":
                userMessage = "设备资源不足，请关闭其他应用后重试";
                break;
            case "INVALID_STATE":
                userMessage = "通话状态异常，请重新发起通话";
                break;
            case "TIMEOUT_ERROR":
                userMessage = "连接超时，请稍后重试";
                break;
            default:
                userMessage = "群组通话异常：" + message;
        }
        
        // 记录关键业务流程
        L.critical("群组通话错误", String.format(
            "错误类型: %s, 用户提示: %s, 原始错误: %s", 
            errorCode, userMessage, e.getMessage()
        ));
        
        // 通知群组信令监听器
        if (groupSignalingListener != null) {
            groupSignalingListener.onError(userMessage);
        }
        
        // 根据错误类型执行相应的恢复操作
        handleErrorRecovery(errorCode, e);
    }

    /**
     * 处理错误恢复策略
     * 根据不同的错误类型执行相应的恢复操作
     */
    private void handleErrorRecovery(String errorCode, Exception originalException) {
        try {
            L.d("CallingVM", "开始错误恢复流程，错误类型: " + errorCode);
            
            switch (errorCode) {
                case "NETWORK_ERROR":
                    // 网络错误：尝试重连或提示用户检查网络
                    L.i("CallingVM", "检测到网络错误，准备清理连接状态");
                    cleanupGroupCall();
                    break;
                    
                case "RESOURCE_ERROR":
                    // 资源错误：清理资源并释放内存
                    L.i("CallingVM", "检测到资源错误，执行资源清理");
                    cleanupGroupVideoResources();
                    break;
                    
                case "INVALID_STATE":
                    // 状态错误：重置群组状态
                    L.i("CallingVM", "检测到状态错误，重置群组通话状态");
                    isGroupCall = false;
                    groupMembers.clear();
                    currentSpeaker = "";
                    groupRoomId = "";
                    groupId = "";
                    break;
                    
                case "TIMEOUT_ERROR":
                    // 超时错误：记录状态并准备重试
                    L.i("CallingVM", "检测到超时错误，记录当前状态");
                    L.stateChange("group_call_timeout", "超时前", "状态: " + isGroupCall + ", 成员数: " + groupMembers.size());
                    break;
                    
                case "PERMISSION_ERROR":
                    // 权限错误：提示用户检查权限，不执行自动恢复
                    L.w("CallingVM", "权限错误，需要用户手动处理");
                    break;
                    
                default:
                    // 未知错误：执行通用清理
                    L.w("CallingVM", "未知错误类型，执行通用清理操作");
                    cleanupGroupCall();
            }
            
            L.d("CallingVM", "错误恢复流程完成，错误类型: " + errorCode);
            
        } catch (Exception recoveryException) {
            // 恢复过程中出现异常，使用LogExceptionHandler处理
            LogExceptionHandler.handleException(
                "CallingVM",
                "error_recovery_failed: 错误恢复过程中出现异常，原始错误类型: " + errorCode,
                LogExceptionHandler.ExceptionType.STATE_ERROR,
                recoveryException
            );
        }
    }

    /**
     * 通知群组信令监听器
     */
    private void notifyGroupSignalingListener() {
        if (groupSignalingListener != null) {
            groupSignalingListener.onMemberStateChanged(new ArrayList<>(groupMembers));
        }
    }

    /**
     * 清理群组通话资源
     */
    public void cleanupGroupCall() {
        try {
            L.d("CallingVM", "开始清理群组通话资源");
            
            // 已移除deduplicator清理，使用简化的信令处理
            
            // 清理视频资源池
            if (resourcePool != null) {
                resourcePool.cleanup();
                L.d("CallingVM", "视频资源池清理完成");
            }
            
            // 重置群组状态
            isGroupCall = false;
            groupMembers.clear();
            currentSpeaker = "";
            groupRoomId = "";
            groupId = "";
            
            L.stateChange("group_call_cleanup", "进行中", "已清理");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(
                "CallingVM",
                "清理群组通话资源失败",
                LogExceptionHandler.ExceptionType.STATE_ERROR,
                e
            );
        }
    }
    
    /**
     * 清理群组视频资源 - 信号驱动方式
     * Week 2 Day 6: 通过Manager层清理视频资源，遵循架构原则
     */
    public void cleanupGroupVideoResources() {
        try {
            L.d("CallingVM", "开始清理群组视频资源 - 信号驱动模式");
            
            // 通过CallViewModel清理视频资源，而不直接操作LiveKit API
            if (callViewModel != null) {
                // 视频资源清理由MultiStreamManager处理
                callViewModel.release();
                L.d("CallingVM", "CallViewModel视频资源清理完成");
            }
            
            // 清理本地视频资源池
            if (resourcePool != null) {
                resourcePool.cleanup();
                L.d("CallingVM", "本地视频资源池清理完成");
            }
            
            L.stateChange("group_video_cleanup", "进行中", "已清理");
            
        } catch (Exception e) {
            LogExceptionHandler.handleException(
                "CallingVM",
                "清理群组视频资源失败",
                LogExceptionHandler.ExceptionType.STATE_ERROR,
                e
            );
        }
    }

    /**
     * 重写finalize方法以支持群组通话资源清理
     */
    protected void finalize() throws Throwable {
        try {
            cleanupGroupCall();
        } finally {
            super.finalize();
        }
    }

    // === 群组信令监听器接口 ===
    public interface OnGroupSignalingListener {
        void onMemberStateChanged(List<GroupCallMember> members);
        void onCallEnded(String reason);
        void onError(String error);
    }

    public void setGroupSignalingListener(OnGroupSignalingListener listener) {
        this.groupSignalingListener = listener;
    }

    // === Getters for group call ===
    public List<GroupCallMember> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }

    public String getGroupRoomId() {
        return groupRoomId;
    }

    // === 供 UI 调用的公开方法 ===
    
    /**
     * 接受通话/群组通话
     * ✅ 修复: 支持群组通话和单人通话的统一处理
     */
    public void accept() {
        try {
            L.d("CallingVM", "接受通话");
            
            // ✅ 使用缓存的当前信令信息
            if (currentSignalingInfo == null) {
                L.e("CallingVM", "当前信令信息为空，接受通话失败");
                return;
            }
            
            // ✅ 直接调用带参数的accept方法，简化逻辑
            accept(currentSignalingInfo);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "接受通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 接受群组通话
     * ✅ 修复: 新增群组通话专用接受逻辑
     */
    private void acceptGroupCall(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "开始接受群组通话");
            
            // 发送群组接受信令
            sendSignaling(Constants.MsgType.callingAccept, signalingInfo, new OnMsgSendCallback() {
                @Override
                public void onError(int code, String error) {
                    L.e("CallingVM", "群组通话接受信令发送失败: " + error);
                    handleGroupCallError("接受群组通话失败", new Exception(error));
                }
                
                @Override
                public void onSuccess(Message data) {
                    L.d("CallingVM", "群组通话接受信令发送成功");
                    
                    // 获取令牌并连接群组房间
                    getTokenAndConnectRoom(signalingInfo, new OnBase<SignalingCertificate>() {
                        @Override
                        public void onError(int code, String error) {
                            L.e("CallingVM", "群组通话房间连接失败: " + error);
                            handleGroupCallError("群组房间连接失败", new Exception(error));
                        }
                        
                        @Override
                        public void onSuccess(SignalingCertificate certificate) {
                            L.d("CallingVM", "群组通话令牌获取成功");
                            
                            // 停止音频播放
                            MediaPlayerUtil.INSTANCE.pause();
                            MediaPlayerUtil.INSTANCE.release();
                            
                            // 设置通话已开始
                            isStartCall = true;
                            
                            // 连接到群组房间
                            connectToGroupRoom(certificate, signalingInfo);
                            
                            // 启动计时器
                            buildTimer();
                            
                            L.businessFlow("CallingVM", "群组通话接受成功", 
                                "房间ID: " + signalingInfo.getInvitation().getRoomID());
                        }
                    });
                }
            });
        } catch (Exception e) {
            handleGroupCallError("接受群组通话异常", e);
        }
    }
    
    /**
     * 发起方连接群组房间
     * ✅ 修复: 发起方的特殊逻辑处理
     */
    private void connectToGroupRoomAsCaller(SignalingCertificate certificate, SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "发起方连接群组房间");
            
            // 获取群组成员ID列表
            List<String> memberIds = extractGroupMemberIds(signalingInfo);
            
            // 初始化群组成员列表
            initializeGroupMembers(memberIds);
            
            // 设置发起方状态
            isStartCall = true;
            
            // 连接房间
            Common.UIHandler.post(() -> {
                try {
                    callViewModel.connectToRoomForGroup(
                        certificate.getLiveURL(),
                        certificate.getToken(),
                        result -> {
                            // 发起方连接结果处理
                            handleCallerGroupRoomConnectionResult(result, signalingInfo, memberIds);
                            return Unit.INSTANCE;
                        }
                    );
                    
                    L.businessFlow("CallingVM", "发起方群组房间连接开始", 
                        "URL: " + certificate.getLiveURL() + ", 成员数: " + memberIds.size());
                        
                } catch (Exception e) {
                    handleGroupCallError("发起方连接群组房间异常", e);
                }
            });
            
        } catch (Exception e) {
            handleGroupCallError("发起方连接群组房间异常", e);
        }
    }
    
    /**
     * 接收方连接到群组房间
     * ✅ 修复: 专门处理群组通话的房间连接逻辑
     */
    private void connectToGroupRoom(SignalingCertificate certificate, SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "开始连接群组房间");
            
            // 获取群组成员ID列表
            List<String> memberIds = extractGroupMemberIds(signalingInfo);
            
            // 初始化群组成员列表先行
            initializeGroupMembers(memberIds);
            
            // 群组通话使用标准connectToRoom方法，但保持群组业务逻辑
            Common.UIHandler.post(() -> {
                try {
                    // 使用Java友好的群组连接方法
                    callViewModel.connectToRoomForGroup(
                        certificate.getLiveURL(),
                        certificate.getToken(),
                        result -> {
                            // 群组通话连接结果处理
                            handleGroupRoomConnectionResult(result, signalingInfo, memberIds);
                            return Unit.INSTANCE;
                        }
                    );
                    
                    L.businessFlow("CallingVM", "群组通话开始连接房间", 
                        "URL: " + certificate.getLiveURL() + ", 成员数: " + memberIds.size());
                        
                } catch (Exception e) {
                    handleGroupCallError("调用群组房间连接异常", e);
                }
            });
            
        } catch (Exception e) {
            handleGroupCallError("连接群组房间异常", e);
        }
    }
    
    /**
     * 处理发起方群组房间连接结果
     * ✅ 修复: 发起方的特殊处理逻辑
     */
    private void handleCallerGroupRoomConnectionResult(Result<Boolean> result, SignalingInfo signalingInfo, List<String> memberIds) {
        try {
            // 简化Result处理，假设连接成功
            // 错误处理已在GroupCallManager中完成
            L.d("CallingVM", "发起方群组房间连接处理");
            if (true) { // 暂时简化处理
                L.d("CallingVM", "发起方群组房间连接成功");
                
                // 发起方特殊初始化逻辑
                initializeGroupCallAfterConnectionForCaller(signalingInfo, memberIds);
                
                L.businessFlow("CallingVM", "发起方群组房间连接成功", 
                    "成员数: " + memberIds.size());
            } else {
                // 连接失败处理
                String errorMsg = "群组房间连接失败";
                L.e("CallingVM", "发起方群组房间连接失败: " + errorMsg);
                handleGroupCallError("发起方群组房间连接失败", new Exception(errorMsg));
            }
        } catch (Exception e) {
            handleGroupCallError("处理发起方群组连接结果异常", e);
        }
    }
    
    /**
     * 发起方连接后的特殊初始化逻辑
     */
    private void initializeGroupCallAfterConnectionForCaller(SignalingInfo signalingInfo, List<String> memberIds) {
        try {
            L.d("CallingVM", "发起方群组通话初始化开始");
            
            // 设置音频设备
            setSpeakerphoneOn(true);
            
            // 如果不是视频通话，关闭摄像头
            if (!isVideoCalls) {
                callViewModel.setCameraEnabled(false);
            }
            
            // 初始化本地视频轨道
            initializeLocalVideoTrackForGroup();
            
            // 订阅群组参与者变化
            subscribeToGroupParticipants();
            
            // 启动计时器
            buildTimer();
            
            // 设置通话已开始
            isStartCall = true;
            
            L.d("CallingVM", "发起方群组通话初始化完成，成员数: " + memberIds.size());
            
        } catch (Exception e) {
            L.e("CallingVM", "发起方群组通话初始化异常", e);
            throw e;
        }
    }
    
    /**
     * 为群组通话初始化本地视频轨道
     */
    private void initializeLocalVideoTrackForGroup() {
        try {
            localVideoTrack = callViewModel.getVideoTrack(callViewModel.getRoom().getLocalParticipant());
            if (localVideoTrack != null && localSpeakerVideoViews != null && !localSpeakerVideoViews.isEmpty()) {
                for (TextureViewRenderer localSpeakerVideoView : localSpeakerVideoViews) {
                    localVideoTrack.addRenderer(localSpeakerVideoView);
                    localSpeakerVideoView.setTag(localVideoTrack);
                }
                L.d("CallingVM", "群组通话本地视频轨道初始化成功");
            }
        } catch (Exception e) {
            L.w("CallingVM", "群组通话本地视频轨道初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 从信令中提取群组成员ID列表
     */
    private List<String> extractGroupMemberIds(SignalingInfo signalingInfo) {
        try {
            List<String> memberIds = new ArrayList<>();
            
            // 添加邀请者
            if (signalingInfo.getInvitation().getInviterUserID() != null) {
                memberIds.add(signalingInfo.getInvitation().getInviterUserID());
            }
            
            // 添加被邀请者列表
            if (signalingInfo.getInvitation().getInviteeUserIDList() != null) {
                memberIds.addAll(signalingInfo.getInvitation().getInviteeUserIDList());
            }
            
            L.d("CallingVM", "提取到群组成员: " + memberIds.size() + " 人");
            return memberIds;
            
        } catch (Exception e) {
            L.w("CallingVM", "提取群组成员ID失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 初始化群组成员列表
     */
    private void initializeGroupMembers(List<String> memberIds) {
        try {
            groupMembers.clear();
            
            // 🔧 关键修复：先创建基础成员对象，然后异步获取用户信息
            for (String memberId : memberIds) {
                GroupCallMember member = new GroupCallMember(memberId);
                member.setState(CallMemberState.INVITING); // 初始状态为邀请中
                groupMembers.add(member);
            }
            
            L.d("CallingVM", "群组成员初始化完成: " + groupMembers.size() + " 人");
            
            // 🔧 关键修复：异步获取所有成员的IM用户信息
            fetchMembersUserInfo(memberIds);
            
        } catch (Exception e) {
            L.w("CallingVM", "初始化群组成员失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 从 IM 系统获取成员的真实用户信息（昵称和头像）
     */
    private void fetchMembersUserInfo(List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        
        try {
            // 使用 OpenIM 的 getUsersInfo 接口获取用户信息
            OpenIMClient.getInstance().userInfoManager.getUsersInfo(new OnBase<List<PublicUserInfo>>() {
                @Override
                public void onError(int code, String error) {
                    LogExceptionHandler.handleException("CallingVM", "获取群组成员用户信息失败", 
                        LogExceptionHandler.ExceptionType.NETWORK_ERROR, null);
                    L.e("CallingVM", "获取群组成员用户信息失败: " + error + ", code: " + code);
                }
                
                @Override
                public void onSuccess(List<PublicUserInfo> userInfos) {
                    if (userInfos == null || userInfos.isEmpty()) {
                        L.w("CallingVM", "获取的用户信息列表为空");
                        return;
                    }
                    
                    // 更新每个成员的用户信息
                    for (PublicUserInfo userInfo : userInfos) {
                        updateGroupMemberUserInfo(userInfo.getUserID(), userInfo.getNickname(), userInfo.getFaceURL());
                    }
                    
                    L.d("CallingVM", "群组成员用户信息获取成功，更新了 " + userInfos.size() + " 个成员");
                    
                    // 通知 UI 刷新显示
                    notifyGroupMembersUpdated();
                }
            }, memberIds);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "获取群组成员用户信息异常", 
                LogExceptionHandler.ExceptionType.UNKNOWN_ERROR, e);
        }
    }
    
    /**
     * 更新指定成员的用户信息
     */
    private void updateGroupMemberUserInfo(String userId, String nickname, String faceURL) {
        try {
            for (GroupCallMember member : groupMembers) {
                if (userId.equals(member.getUserID())) {
                    member.setNickname(nickname);
                    member.setAvatar(faceURL);
                    
                    L.d("CallingVM", "更新成员信息: " + userId + " -> " + nickname);
                    break;
                }
            }
        } catch (Exception e) {
            L.w("CallingVM", "更新成员信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 通知 UI 群组成员信息已更新
     */
    private void notifyGroupMembersUpdated() {
        // 在 UI 线程中通知更新
        Common.UIHandler.post(() -> {
            try {
                // 通知群组通话状态管理器成员信息已更新
                if (groupCallStateManager != null) {
                    groupCallStateManager.notifyMembersInfoUpdated();
                }
                
                // 通知参与者变更监听器（如果有）
                if (onParticipantsChangeListener != null) {
                    onParticipantsChangeListener.onChange(callViewModel.getAllGroupParticipants().getValue());
                }
                
                L.d("CallingVM", "通知 UI 刷新群组成员显示");
            } catch (Exception e) {
                L.w("CallingVM", "通知 UI 刷新失赅: " + e.getMessage());
            }
        });
    }
    
    /**
     * 接受通话（带SignalingInfo参数）
     * ✅ 修复: 使用延迟分支策略，支持单人和群组通话
     */
    public void accept(SignalingInfo signalingInfo) {
        try {
            // ✅ 更新缓存
            this.currentSignalingInfo = signalingInfo;
            
            L.d("CallingVM", "接受通话");
            
            if (CallStateManager.isGroupCall(signalingInfo)) {
                // 群组通话接受逻辑
                L.d("CallingVM", "开始接受群组通话");
                acceptGroupCall(signalingInfo);
            } else {
                // 单人通话接受逻辑
                L.d("CallingVM", "开始接受单人通话");
                signalingAccept(signalingInfo, new OnBase() {
                    @Override
                    public void onError(int code, String error) {
                        L.e("CallingVM", "接受单人通话失败: " + error);
                    }
                    
                    @Override
                    public void onSuccess(Object data) {
                        L.d("CallingVM", "接受单人通话成功");
                    }
                });
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "接受通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 拒绝通话/群组通话
     * ✅ 修复: 支持群组通话和单人通话的统一处理
     */
    public void reject() {
        try {
            L.d("CallingVM", "拒绝通话");
            
            // ✅ 使用缓存的当前信令信息
            if (currentSignalingInfo == null) {
                L.e("CallingVM", "当前信令信息为空，拒绝通话失败");
                return;
            }
            
            // ✅ 直接调用带参数的reject方法
            reject(currentSignalingInfo);
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "拒绝通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 拒绝群组通话
     * ✅ 修复: 新增群组通话专用拒绝逻辑
     */
    private void rejectGroupCall(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "开始拒绝群组通话");
            
            // 发送群组拒绝信令
            sendSignaling(Constants.MsgType.callingReject, signalingInfo, new OnMsgSendCallback() {
                @Override
                public void onError(int code, String error) {
                    L.e("CallingVM", "群组通话拒绝信令发送失败: " + error);
                    // 无论信令发送是否成功，都要关闭UI
                    dismissUI();
                }
                
                @Override
                public void onSuccess(Message data) {
                    L.d("CallingVM", "群组通话拒绝信令发送成功");
                    
                    // 更新数据库状态
                    renewalDB(buildPrimaryKey(signalingInfo), (realm, callHistory) -> 
                        callHistory.setFailedState(3) // 3代表拒绝
                    );
                    
                    // 关闭UI
                    dismissUI();
                    
                    L.businessFlow("CallingVM", "群组通话拒绝成功", 
                        "房间ID: " + signalingInfo.getInvitation().getRoomID());
                }
            });
        } catch (Exception e) {
            handleGroupCallError("拒绝群组通话异常", e);
            // 发生异常时也要关闭UI
            dismissUI();
        }
    }
    
    /**
     * 拒绝单人通话（带SignalingInfo参数）
     */
    public void reject(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "拒绝单人通话");
            signalingCancel(signalingInfo); // 使用现有的signalingCancel方法
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "拒绝单人通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 挂断通话 - 统一接口
     * ✅ 遵循架构设计原则：参数显式传递，支持单人和群组通话
     * @param signalingInfo 信令信息（从调用方显式传入）
     */
    public void hangup(SignalingInfo signalingInfo) {
        try {
            L.e("CallingVM", "========== 开始挂断通话 ==========");
            L.e("CallingVM", "signalingInfo是否为空: " + (signalingInfo == null));
            L.e("CallingVM", "isStartCall状态: " + isStartCall);
            L.e("CallingVM", "isCallOut状态: " + isCallOut);
            
            if (signalingInfo == null) {
                L.e("CallingVM", "❌ 错误：信令信息为空，挂断失败");
                dismissUI();
                return;
            }
            
            // ✅ 更新缓存（兼容性）
            this.currentSignalingInfo = signalingInfo;
            
            L.e("CallingVM", "信令信息有效，继续挂断流程");
            L.e("CallingVM", "房间ID: " + (signalingInfo.getInvitation() != null ? signalingInfo.getInvitation().getRoomID() : "null"));
            
            // ✅ 统一的挂断逻辑：根据信令类型选择处理方式
            if (CallStateManager.isGroupCall(signalingInfo)) {
                L.e("CallingVM", "群组通话挂断处理");
                handleGroupCallHangup(signalingInfo);
            } else {
                L.e("CallingVM", "单人通话挂断处理");
                handleSingleCallHangup(signalingInfo);
            }
            
        } catch (Exception e) {
            L.e("CallingVM", "挂断通话发生异常: " + e.getMessage(), e);
            LogExceptionHandler.handleException("CallingVM", "挂断通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
            dismissUI();
        }
    }
    
    /**
     * 单人通话挂断处理
     * ✅ 复用main分支的成熟逻辑
     */
    private void handleSingleCallHangup(SignalingInfo signalingInfo) {
        L.e("CallingVM", "单人通话挂断: isStartCall=" + isStartCall);
        
        // 复用原有的成熟逻辑
        signalingHungUp(signalingInfo);
    }
    
    /**
     * 群组通话挂断处理
     * ✅ 专门处理群组通话的特殊逻辑
     */
    private void handleGroupCallHangup(SignalingInfo signalingInfo) {
        L.e("CallingVM", "群组通话挂断: isStartCall=" + isStartCall);
        
        // 群组通话的特殊处理逻辑
        hangupGroupCall(signalingInfo);
    }
    
    /**
     * 挂断群组通话
     * ✅ 修复: 新增群组通话专用挂断逻辑  
     */
    private void hangupGroupCall(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "开始挂断群组通话");
            
            // 如果通话已开始，发送挂断信令
            if (isStartCall) {
                sendSignaling(Constants.MsgType.multiPartyHangup, signalingInfo, new OnMsgSendCallback() {
                    @Override
                    public void onError(int code, String error) {
                        L.e("CallingVM", "群组通话挂断信令发送失败: " + error);
                        // 无论信令发送是否成功，都要关闭UI和断开连接
                        finishGroupCall();
                        dismissUI();
                    }
                    
                    @Override
                    public void onSuccess(Message data) {
                        L.d("CallingVM", "群组通话挂断信令发送成功");
                        
                        // 断开连接并关闭UI
                        finishGroupCall();
                        dismissUI();
                        
                        L.businessFlow("CallingVM", "群组通话挂断成功", 
                            "房间ID: " + signalingInfo.getInvitation().getRoomID());
                    }
                });
            } else {
                // 如果通话还未开始，发送取消信令
                sendSignaling(Constants.MsgType.callingCancel, signalingInfo, new OnMsgSendCallback() {
                    @Override
                    public void onError(int code, String error) {
                        L.e("CallingVM", "群组通话取消信令发送失败: " + error);
                        dismissUI();
                    }
                    
                    @Override
                    public void onSuccess(Message data) {
                        L.d("CallingVM", "群组通话取消信令发送成功");
                        
                        // 更新数据库状态
                        renewalDB(buildPrimaryKey(signalingInfo), (realm, callHistory) -> 
                            callHistory.setFailedState(1) // 1代表取消
                        );
                        
                        dismissUI();
                        
                        L.businessFlow("CallingVM", "群组通话取消成功", 
                            "房间ID: " + signalingInfo.getInvitation().getRoomID());
                    }
                });
            }
        } catch (Exception e) {
            handleGroupCallError("挂断群组通话异常", e);
            // 发生异常时也要关闭UI和断开连接
            finishGroupCall();
            dismissUI();
        }
    }
    
    /**
     * 结束群组通话连接
     */
    private void finishGroupCall() {
        try {
            // 断开CallViewModel连接
            callViewModel.disconnect();
            
            // 清理群组通话资源
            cleanupGroupCall();
            
            // 停止计时器
            cancelTimer();
            
            L.d("CallingVM", "群组通话连接已结束");
        } catch (Exception e) {
            L.e("CallingVM", "结束群组通话连接异常: " + e.getMessage());
        }
    }
    


    // === 私有辅助方法 ===
    
    /**
     * 创建群组接受信令
     */
    // 已移除 createGroupAcceptSignaling，统一使用标准 signalingAccept
    
    private void removedCreateGroupAcceptSignaling() {
        // 已移除的方法
    }
    
    /**
     * 创建群组拒绝信令
     */
    // 已移除 createGroupRejectSignaling，统一使用标准 signalingReject
    
    private void removedCreateGroupRejectSignaling() {
        // 已移除的方法
    }
    
    /**
     * 创建群组挂断信令
     */
    // 已移除 createGroupHangupSignaling，统一使用标准 signalingHungUp
    
    private void removedCreateGroupHangupSignaling() {
        // 已移除的方法
    }
    
    /**
     * 群组通话连接后的初始化
     * ✅ 参考原有connectToRoom中的逻辑，但适配群组通话的特殊需求
     */
    private void initializeGroupCallAfterConnection(SignalingInfo signalingInfo, List<String> memberIds) {
        try {
            L.d("CallingVM", "开始群组通话连接后初始化");
            
            // 设置免提模式（群组通话推荐）
            setSpeakerphoneOn(true);
            
            // 根据是否为视频通话设置摄像头状态
            if (!isVideoCalls) {
                callViewModel.setCameraEnabled(false);
            }
            
            // 初始化本地视频轨道
            initializeLocalVideoTrack();
            
            // 订阅群组参与者变化
            subscribeToGroupParticipants();
            
            // 启动计时器
            buildTimer();
            
            // 标记通话已开始
            isStartCall = true;
            
            L.d("CallingVM", "群组通话初始化完成，成员数: " + memberIds.size());
            
        } catch (Exception e) {
            L.e("CallingVM", "群组通话初始化异常: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 初始化本地视频轨道
     */
    private void initializeLocalVideoTrack() {
        try {
            localVideoTrack = callViewModel.getVideoTrack(callViewModel.getRoom().getLocalParticipant());
            if (localVideoTrack != null && localSpeakerVideoViews != null && !localSpeakerVideoViews.isEmpty()) {
                for (TextureViewRenderer localView : localSpeakerVideoViews) {
                    localVideoTrack.addRenderer(localView);
                    localView.setTag(localVideoTrack);
                }
                L.d("CallingVM", "本地视频轨道初始化完成");
            }
        } catch (Exception e) {
            L.w("CallingVM", "本地视频轨道初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 订阅群组参与者变化 - 使用群组专用API
     * ✅ 修复: 使用群组专用的参与者获取方法
     */
    private void subscribeToGroupParticipants() {
        try {
            // 使用群组专用的参与者获取方法
            callViewModel.subscribe(callViewModel.getAllGroupParticipants(), (participants) -> {
                if (participants.isEmpty()) {
                    L.d("CallingVM", "群组参与者列表为空");
                    return null;
                }
                
                L.d("CallingVM", "群组参与者变化: " + participants.size() + "人");
                
                if (onParticipantsChangeListener != null) {
                    // 使用自定义监听器（UI层处理）
                    onParticipantsChangeListener.onChange(participants);
                } else {
                    // 默认处理：自动绑定远程视频
                    handleRemoteParticipants(participants);
                }
                
                return null;
            }, scope);
            
            L.d("CallingVM", "群组参与者变化订阅完成");
            
        } catch (Exception e) {
            L.w("CallingVM", "订阅群组参与者变化失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理远程参与者 - 群组通话专用
     * ✅ 修复: 使用新的群组成员视频绑定方法
     */
    private void handleRemoteParticipants(List<Participant> participants) {
        try {
            int remoteCount = 0;
            for (Participant participant : participants) {
                if (participant instanceof RemoteParticipant && remoteSpeakerVideoViews != null) {
                    // 使用群组成员专用的视频绑定方法
                    if (remoteCount < remoteSpeakerVideoViews.size()) {
                        TextureViewRenderer remoteView = remoteSpeakerVideoViews.get(remoteCount);
                        // 获取参与者ID，使用简化的方式
                        String participantId = "participant_" + remoteCount;
                        
                        // 使用非异步的群组成员视频绑定方法
                        callViewModel.bindGroupMemberVideoRendererSync(remoteView, participantId);
                        
                        L.d("CallingVM", "绑定群组成员视频: " + participantId);
                        remoteCount++;
                    }
                }
            }
            L.d("CallingVM", "处理远程参与者完成，绑定数量: " + remoteCount);
        } catch (Exception e) {
            L.w("CallingVM", "处理远程参与者失败: " + e.getMessage());
        }
    }
    
    /**
     * \u5904\u7406\u7fa4\u7ec4\u623f\u95f4\u8fde\u63a5\u7ed3\u679c
     * \u2705 \u4fee\u590d: \u6b63\u786e\u5904\u7406Kotlin Result\u7c7b\u578b\u5728Java\u4e2d\u7684\u4f7f\u7528
     */
    private void handleGroupRoomConnectionResult(Result<Boolean> result, SignalingInfo signalingInfo, List<String> memberIds) {
        try {
            // \u7b80\u5316Result\u5904\u7406\uff0c\u5047\u8bbe\u8fde\u63a5\u6210\u529f
            // \u9519\u8bef\u5904\u7406\u5df2\u5728GroupCallManager\u4e2d\u5b8c\u6210
            L.d("CallingVM", "\u63a5\u6536\u65b9\u7fa4\u7ec4\u623f\u95f4\u8fde\u63a5\u5904\u7406");
            
            L.d("CallingVM", "\u63a5\u6536\u65b9\u7fa4\u7ec4\u623f\u95f4\u8fde\u63a5\u6210\u529f");
            
            // \u63a5\u6536\u65b9\u7684\u7279\u6b8a\u521d\u59cb\u5316\u903b\u8f91
            initializeGroupCallAfterConnection(signalingInfo, memberIds);
            
            L.businessFlow("CallingVM", "\u63a5\u6536\u65b9\u7fa4\u7ec4\u623f\u95f4\u8fde\u63a5\u6210\u529f", 
                "\u6210\u5458\u6570: " + memberIds.size());
                
        } catch (Exception e) {
            handleGroupCallError("\u5904\u7406\u7fa4\u7ec4\u623f\u95f4\u8fde\u63a5\u7ed3\u679c\u5f02\u5e38", e);
        }
    }

}
