package io.openim.android.ouicalling.vm;

import android.bluetooth.BluetoothAdapter;
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
import io.openim.android.ouicalling.entity.MultiPartySignaling;
import io.openim.android.ouicalling.utils.SignalingDeduplicator;
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

public class CallingVM {
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
    //是否是群
    public boolean isGroup;
    
    // === 群组通话扩展字段 ===
    // 是否群组通话
    public boolean isGroupCall = false;
    // 群组通话成员列表（线程安全）
    public final CopyOnWriteArrayList<GroupCallMember> groupMembers = new CopyOnWriteArrayList<>();
    // 当前发言人ID（v1.2实现）
    public String currentSpeaker = "";
    // 房间ID（群组通话用）
    private String groupRoomId = "";
    // 群组ID
    private String groupId = "";
    
    // === 业界最佳实践组件 ===
    // 信令去重组件
    private final SignalingDeduplicator deduplicator = new SignalingDeduplicator();
    // 视频资源池
    private final VideoResourcePool resourcePool = new VideoResourcePool();
    
    /**
     * 获取视频资源池
     */
    public VideoResourcePool getResourcePool() {
        return resourcePool;
    }
    // 群组信令监听器
    private OnGroupSignalingListener groupSignalingListener;


    private List<TextureViewRenderer> remoteSpeakerVideoViews, localSpeakerVideoViews;


    public CallingVM(CallingService callingService, boolean isCallOut) {
        this.callingService = callingService;
        this.isCallOut = isCallOut;

        callViewModel = new CallViewModel(BaseApp.inst());
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
                        Toast.makeText(BaseApp.inst(), "加入会议失败,服务器错误(" + error + ")", Toast.LENGTH_LONG).show();
                        L.e(CallingServiceImp.TAG, error + code);
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
            L.e("CallingVM", "unBindView清理失败", e);
        }
    }

    public void setSpeakerphoneOn(boolean isChecked) {
        if (callViewModel.getAudioHandler().getSelectedAudioDevice() instanceof AudioDevice.BluetoothHeadset) {
            return;
        }
        callViewModel.getAudioHandler().selectDevice(isChecked ? new AudioDevice.Speakerphone()
            : new AudioDevice.Earpiece());
    }


    public interface OnParticipantsChangeListener {
        void onChange(List<Participant> participants);
    }


    public void renewalDB(String id, OnRenewalDBListener onRenewalDBListener) {
        BaseApp.inst().realm.executeTransactionAsync(realm -> {
            CallHistory callHistory = realm.where(CallHistory.class).equalTo("id", id).findFirst();
            if (null == callHistory) return;
            onRenewalDBListener.onRenewal(realm, callHistory);
        });
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
            if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) { //蓝牙连接状态
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
                if (state == BluetoothAdapter.STATE_CONNECTED) {
                    //连接或失联，切换音频输出（到蓝牙、或者强制仍然扬声器外放）
                    callingVM.changeToHeadset();
                } else if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                    callingVM.changeToSpeaker();
                }
            }
        }
    }

    /**
     * 切换到外放
     */
    public void changeToSpeaker() {
        setSpeakerphoneOn(true);
    }

    /**
     * 切换到蓝牙音箱
     */
    public void changeToHeadset() {
        callViewModel.getAudioHandler().selectDevice(new AudioDevice.BluetoothHeadset());
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
            
            // 1. 设置群组通话模式
            this.isGroupCall = true;
            this.isVideoCalls = isVideo;
            this.groupId = groupId;
            this.groupRoomId = "group_call_" + System.currentTimeMillis();
            
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
            
            // 3. 创建群组信令并发送邀请
            MultiPartySignaling signaling = createGroupInviteSignaling(memberIds, isVideo);
            sendGroupSignaling(Constants.MsgType.multiPartyInvite, signaling);
            
            // 4. 获取房间Token并准备连接
            prepareGroupRoom(signaling);
            
        } catch (Exception e) {
            L.e("CallingVM", "发起群组通话失败", e);
            handleGroupCallError("发起群组通话失败", e);
        }
    }

    /**
     * 处理群组信令（带去重）
     */
    public void handleGroupSignaling(MultiPartySignaling signaling) {
        if (signaling == null) {
            L.w("CallingVM", "群组信令为空");
            return;
        }

        // 使用去重组件处理
        deduplicator.handleSignalingWithDeduplication(signaling, this::processGroupSignaling);
    }

    /**
     * 实际处理群组信令的逻辑
     */
    private void processGroupSignaling(MultiPartySignaling signaling) throws Exception {
        String type = signaling.getType();
        L.d("CallingVM", "处理群组信令: " + type);

        switch (type) {
            case "multiPartyInvite":
                handleGroupInvite(signaling);
                break;
            case "multiPartyAccept":
                handleGroupAccept(signaling);
                break;
            case "multiPartyReject":
                handleGroupReject(signaling);
                break;
            case "multiPartyCancel":
                handleGroupCancel(signaling);
                break;
            case "multiPartyHangup":
                handleGroupHangup(signaling);
                break;
            case "multiPartyMemberJoin":
                handleMemberJoin(signaling);
                break;
            case "multiPartyMemberLeave":
                handleMemberLeave(signaling);
                break;
            case "multiPartyMemberStateChange":
                handleMemberStateChange(signaling);
                break;
            default:
                L.w("CallingVM", "未知的群组信令类型: " + type);
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

    /**
     * 创建群组邀请信令
     */
    private MultiPartySignaling createGroupInviteSignaling(List<String> memberIds, boolean isVideo) {
        MultiPartySignaling signaling = new MultiPartySignaling();
        signaling.setType("multiPartyInvite");
        signaling.setRoomID(groupRoomId);
        signaling.setInviterID(BaseApp.inst().loginCertificate.userID);
        signaling.setInviteeList(new ArrayList<>(memberIds));
        signaling.setVideoCall(isVideo);
        return signaling;
    }

    /**
     * 发送群组信令
     */
    private void sendGroupSignaling(int msgType, MultiPartySignaling signaling) {
        try {
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put(Constants.K_CUSTOM_TYPE, msgType);
            hashMap.put(Constants.K_DATA, signaling);
            
            Message message = OpenIMClient.getInstance().messageManager.createCustomMessage(
                GsonHel.toJson(hashMap), "", ""
            );

            // 群组信令发送到群聊
            if (message != null && groupId != null && !groupId.isEmpty()) {
                OpenIMClient.getInstance().messageManager.sendMessage(
                    new OnMsgSendCallback() {
                        @Override
                        public void onError(int code, String error) {
                            L.e("CallingVM", "群组信令发送失败: " + error + "-" + code);
                            handleGroupCallError("信令发送失败", new Exception(error));
                        }

                        @Override
                        public void onSuccess(Message data) {
                            L.d("CallingVM", "群组信令发送成功: " + signaling.getType());
                        }
                    },
                    message, 
                    null,  // 接收用户ID (群组消息为null)
                    groupId,  // 群组ID
                    new OfflinePushInfo(), 
                    false  // 不是在线消息
                );
            }
            
        } catch (Exception e) {
            L.e("CallingVM", "发送群组信令异常", e);
            handleGroupCallError("信令发送异常", e);
        }
    }

    // 群组信令处理方法（简化版，后续完善）
    private void handleGroupInvite(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理群组邀请");
        // TODO: 实现群组邀请处理逻辑
    }

    private void handleGroupAccept(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理群组接受");
        updateMemberState(signaling.getMemberID(), CallMemberState.CONNECTED);
    }

    private void handleGroupReject(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理群组拒绝");
        updateMemberState(signaling.getMemberID(), CallMemberState.REJECTED);
    }

    private void handleGroupCancel(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理群组取消");
        // TODO: 实现群组取消处理逻辑
    }

    private void handleGroupHangup(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理群组挂断");
        updateMemberState(signaling.getMemberID(), CallMemberState.DISCONNECTED);
    }

    private void handleMemberJoin(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理成员加入");
        // TODO: 实现成员加入处理逻辑
    }

    private void handleMemberLeave(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理成员离开");
        updateMemberState(signaling.getMemberID(), CallMemberState.DISCONNECTED);
    }

    private void handleMemberStateChange(MultiPartySignaling signaling) {
        L.d("CallingVM", "处理成员状态变更");
        CallMemberState newState = CallMemberState.fromValue(signaling.getMemberState());
        updateMemberState(signaling.getMemberID(), newState);
    }

    /**
     * 准备群组房间连接
     */
    private void prepareGroupRoom(MultiPartySignaling signaling) {
        L.d("CallingVM", "准备群组房间连接: " + signaling.getRoomID());
        
        // ✅ 通过原有的token获取流程，复用单人通话逻辑
        Parameter parameter = new Parameter();
        parameter.add("room", signaling.getRoomID());
        parameter.add("identity", BaseApp.inst().loginCertificate.userID);
        
        N.API(OneselfService.class).getTokenForRTC(parameter.buildJsonBody())
            .map(OneselfService.turn(HashMap.class))
            .map((Function<HashMap, SignalingCertificate>) responseBody -> {
                String serverUrl = (String) responseBody.get("serverUrl");
                String token = (String) responseBody.get("token");
                SignalingCertificate signalingCertificate = new SignalingCertificate();
                signalingCertificate.setLiveURL(serverUrl);
                signalingCertificate.setToken(token);
                return signalingCertificate;
            })
            .compose(N.IOMain())
            .subscribe(new NetObserver<SignalingCertificate>("") {
                @Override
                public void onSuccess(SignalingCertificate data) {
                    if (data != null) {
                        connectToGroupRoomWithToken(data, signaling);
                    }
                }
                
                @Override
                protected void onFailure(Throwable e) {
                    L.e("CallingVM", "获取群组通话Token失败", e);
                    handleGroupCallError("获取房间Token失败", new Exception(e));
                }
            });
    }
    
    /**
     * 使用Token连接群组房间
     * ✅ 使用CallViewModel的群组接口，不直接操作LiveKit
     */
    private void connectToGroupRoomWithToken(SignalingCertificate certificate, MultiPartySignaling signaling) {
        try {
            // 获取所有成员ID（包括自己）
            List<String> allMemberIds = new ArrayList<>(signaling.getParticipants());
            if (!allMemberIds.contains(BaseApp.inst().loginCertificate.userID)) {
                allMemberIds.add(BaseApp.inst().loginCertificate.userID);
            }
            
            // ✅ 使用CallViewModel的群组连接接口
            callViewModel.connectToGroupRoom(
                certificate.getLiveURL(),
                certificate.getToken(),
                allMemberIds,
                result -> {
                    if (result.isSuccess()) {
                        onGroupRoomConnected();
                    } else {
                        L.e("CallingVM", "群组房间连接失败: " + result.getException());
                        handleGroupCallError("连接群组房间失败", new Exception(result.getException()));
                    }
                    return Unit.INSTANCE;
                }
            );
            
        } catch (Exception e) {
            L.e("CallingVM", "连接群组房间异常", e);
            handleGroupCallError("连接群组房间异常", e);
        }
    }
    
    /**
     * 群组房间连接成功回调
     * ✅ 通过CallViewModel设置事件监听，不直接访问LiveKit
     */
    private void onGroupRoomConnected() {
        L.d("CallingVM", "群组房间连接成功");
        
        setSpeakerphoneOn(true);
        if (!isVideoCalls) callViewModel.setCameraEnabled(false);
        
        // ✅ 订阅群组参与者变化
        callViewModel.subscribe(callViewModel.getAllGroupParticipants(), participants -> {
            updateGroupMembersFromParticipants(participants);
            return null;
        }, scope);
        
        // 初始化本地视频
        initializeLocalVideoForGroup();
        
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
                // 查找对应的LiveKit参与者
                Participant liveKitParticipant = null;
                for (Participant p : participants) {
                    if (p.getIdentity().equals(member.getUserID())) {
                        liveKitParticipant = p;
                        break;
                    }
                }
                
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
            L.e("CallingVM", "更新群组成员状态异常", e);
        }
    }

    /**
     * 处理群组通话错误
     */
    private void handleGroupCallError(String message, Exception e) {
        L.e("CallingVM", message, e);
        // TODO: 实现错误处理和UI提示
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
            L.d("CallingVM", "清理群组通话资源");
            
            // 清理信令去重器
            deduplicator.clearAll();
            
            // 清理视频资源池
            resourcePool.cleanup();
            
            // 重置群组状态
            isGroupCall = false;
            groupMembers.clear();
            currentSpeaker = "";
            groupRoomId = "";
            groupId = "";
            
        } catch (Exception e) {
            L.e("CallingVM", "清理群组通话资源失败", e);
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
    public boolean isGroupCall() {
        return isGroupCall;
    }

    public List<GroupCallMember> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }

    public String getGroupRoomId() {
        return groupRoomId;
    }

}
