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
    // 群组通话成员列表（线程安全）
    public final CopyOnWriteArrayList<GroupCallMember> groupMembers = new CopyOnWriteArrayList<>();
    // 当前发言人ID（v1.2实现）
    public String currentSpeaker = "";
    
    // 群组通话相关字段（兼容旧代码）
    private boolean isGroupCall = false;
    private String groupRoomId = "";
    private String groupId = "";
    
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
        stateManager.updateSignalingInfo(signalingInfo);
        android.util.Log.d(TAG, "信令状态已更新: " + stateManager.getDebugInfo());
    }
    
    /**
     * 获取状态管理器（供内部使用）
     */
    public CallStateManager getStateManager() {
        return stateManager;
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
                callViewModel.subscribe(callViewModel.getAllParticipants(), (v) -> {
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
        Common.UIHandler.postDelayed(this::dismissUI, 18 * 1000);
        if (!isStartCall) {
            signalingCancel(signalingInfo);
            return;
        }
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
                // 查找对应的参与者 - 使用CallViewModel接口而非直接访问LiveKit
                Participant liveKitParticipant = callViewModel.getParticipantById(member.getUserID());
                
                if (liveKitParticipant != null) {
                    // ✅ 通过CallViewModel获取状态，不直接访问LiveKit
                    member.setState(CallMemberState.CONNECTED);
                    member.setMicrophoneOn(
                        callViewModel.isParticipantMicrophoneEnabled(member.getUserID())
                    );
                    member.setCameraOn(
                        callViewModel.isParticipantCameraEnabled(member.getUserID())
                    );
                } else {
                    // 参与者可能还未连接
                    if (member.getState() == CallMemberState.INVITING) {
                        // 保持邀请状态
                    } else {
                        member.setState(CallMemberState.DISCONNECTED);
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
     * 注意：单人通话需要传入 SignalingInfo 参数
     */
    public void accept() {
        try {
            L.d("CallingVM", "接受通话");
            // 群组通话现在通过标准信令流程处理
            // 需要传入 SignalingInfo 参数
            L.w("CallingVM", "群组通话接受逻辑需要使用 accept(SignalingInfo) 方法");
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "接受通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 接受单人通话（带SignalingInfo参数）
     */
    public void accept(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "接受单人通话");
            signalingAccept(signalingInfo, new OnBase() {
                @Override
                public void onError(int code, String error) {
                    L.e("CallingVM", "接受通话失败: " + error);
                }
                
                @Override
                public void onSuccess(Object data) {
                    L.d("CallingVM", "接受通话成功");
                }
            });
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "接受单人通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 拒绝通话/群组通话
     */
    public void reject() {
        try {
            L.d("CallingVM", "拒绝通话");
            if (isGroupCall()) {
                // 原 MultiPartySignaling 已移除，使用标准 signalingReject
                L.w("CallingVM", "群组通话拒绝逻辑暂未实现，需要配合标准信令流程");
            } else {
                // 单人通话拒绝逻辑 - 需要SignalingInfo参数
                L.w("CallingVM", "单人通话拒绝需要调用 reject(SignalingInfo) 方法");
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "拒绝通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
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
     * 挂断通话/群组通话
     */
    public void hangup() {
        try {
            L.d("CallingVM", "挂断通话");
            if (isGroupCall()) {
                // 原 MultiPartySignaling 已移除，使用标准 signalingHungUp
                L.w("CallingVM", "群组通话挂断逻辑暂未实现，需要配合标准信令流程");
            } else {
                // 单人通话挂断逻辑 - 需要SignalingInfo参数
                L.w("CallingVM", "单人通话挂断需要调用 hangup(SignalingInfo) 方法");
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "挂断通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
        }
    }
    
    /**
     * 挂断单人通话（带SignalingInfo参数）
     */
    public void hangup(SignalingInfo signalingInfo) {
        try {
            L.d("CallingVM", "挂断单人通话");
            signalingCancel(signalingInfo); // 使用现有的signalingCancel方法
        } catch (Exception e) {
            LogExceptionHandler.handleException("CallingVM", "挂断单人通话", LogExceptionHandler.ExceptionType.CALLING_ERROR, e);
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

}
