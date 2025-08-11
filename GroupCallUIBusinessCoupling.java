/**
 * 群组音视频UI层和业务层耦合问题分析
 * 
 * 基于微信群组通话架构的最佳实践对比
 */
public class GroupCallUIBusinessCoupling {
    
    /**
     * 问题1：UI直接访问业务层数据
     * 
     * 当前实现问题：
     * GroupCallDialog.refreshMemberList() {
     *     List<GroupCallMember> groupMembers = callingVM.getGroupMembers(); // 直接访问VM数据
     *     memberAdapter.updateMembers(groupMembers); // UI直接操作数据
     * }
     * 
     * 问题分析：
     * 1. 违反了MVVM架构原则
     * 2. UI层需要了解业务层数据结构
     * 3. 数据变更时，UI需要主动拉取更新
     * 4. 难以进行单元测试
     */
    
    // 当前实现的问题示例
    public static class CurrentCouplingProblems {
        
        /*
         * 问题示例1：UI层直接访问CallingVM数据
         * 
         * 文件：GroupCallDialog.java:424
         * 代码：callingVM.getGroupMembers()
         * 
         * 风险：
         * - UI需要了解GroupCallMember数据结构
         * - CallingVM数据结构变更会影响UI
         * - UI无法独立测试
         */
        
        // 当前的紧耦合实现
        public void currentTightCoupledImplementation() {
            // UI层直接访问业务数据
            java.util.List<GroupCallMember> groupMembers = callingVM.getGroupMembers();
            
            // UI层需要理解业务逻辑
            if (groupMembers != null && !groupMembers.isEmpty()) {
                memberAdapter.updateMembers(groupMembers);
            }
            
            // UI层需要主动拉取更新
            adjustGridLayout(groupMembers.size());
        }
    }
    
    /**
     * 解决方案：引入ViewModel和LiveData/观察者模式
     * 
     * 微信架构特点：
     * 1. UI只观察ViewModel
     * 2. ViewModel暴露UI需要的数据格式
     * 3. 业务层变更自动通知UI
     */
    
    public static class ImprovedArchitecture {
        
        // 方案1：专用的UI ViewModel
        public static class GroupCallUIViewModel extends AndroidViewModel {
            
            // UI专用的数据结构
            private final MutableLiveData<List<MemberUIModel>> membersLiveData = new MutableLiveData<>();
            private final MutableLiveData<CallUIState> callStateLiveData = new MutableLiveData<>();
            private final MutableLiveData<String> errorMessageLiveData = new MutableLiveData<>();
            
            // 业务层引用
            private CallingVM callingVM;
            private GroupCallStateManager stateManager;
            
            public GroupCallUIViewModel(@NonNull Application application) {
                super(application);
                initializeObservers();
            }
            
            // 初始化观察者，监听业务层变化
            private void initializeObservers() {
                // 监听业务层状态变化
                stateManager.addObserver(new GroupCallStateManager.StateChangeObserver() {
                    @Override
                    public void onMemberStateChanged(@NonNull GroupCallMember member) {
                        updateUIMembers();
                    }
                    
                    @Override
                    public void onMembersInfoUpdated() {
                        updateUIMembers();
                    }
                    
                    @Override
                    public void onCallEnded(@NonNull String reason) {
                        callStateLiveData.postValue(new CallUIState(CallUIState.ENDED, reason));
                    }
                });
            }
            
            // 将业务数据转换为UI数据
            private void updateUIMembers() {
                List<GroupCallMember> businessMembers = callingVM.getGroupMembers();
                List<MemberUIModel> uiMembers = transformToUIModels(businessMembers);
                membersLiveData.postValue(uiMembers);
            }
            
            // 数据转换：业务模型 -> UI模型
            private List<MemberUIModel> transformToUIModels(List<GroupCallMember> businessMembers) {
                return businessMembers.stream()
                    .map(member -> new MemberUIModel(
                        member.getUserID(),
                        member.getNickname() != null ? member.getNickname() : member.getUserID(),
                        member.getAvatar(),
                        mapStateToUIState(member.getState()),
                        member.isSpeaking(),
                        member.isMuted(),
                        member.isVideoEnabled()
                    ))
                    .collect(Collectors.toList());
            }
            
            // 状态转换：业务状态 -> UI状态
            private MemberUIState mapStateToUIState(CallMemberState businessState) {
                switch (businessState) {
                    case INVITING: return MemberUIState.CONNECTING;
                    case CONNECTED: return MemberUIState.ACTIVE;
                    case SPEAKING: return MemberUIState.SPEAKING;
                    case MUTED: return MemberUIState.MUTED;
                    case DISCONNECTED: return MemberUIState.DISCONNECTED;
                    case TIMEOUT: return MemberUIState.TIMEOUT;
                    default: return MemberUIState.UNKNOWN;
                }
            }
            
            // UI专用的数据模型
            public static class MemberUIModel {
                public final String userId;
                public final String displayName;
                public final String avatarUrl;
                public final MemberUIState state;
                public final boolean isSpeaking;
                public final boolean isMuted;
                public final boolean hasVideo;
                
                public MemberUIModel(String userId, String displayName, String avatarUrl, 
                                   MemberUIState state, boolean isSpeaking, boolean isMuted, boolean hasVideo) {
                    this.userId = userId;
                    this.displayName = displayName;
                    this.avatarUrl = avatarUrl;
                    this.state = state;
                    this.isSpeaking = isSpeaking;
                    this.isMuted = isMuted;
                    this.hasVideo = hasVideo;
                }
            }
            
            // UI专用的状态枚举
            public enum MemberUIState {
                CONNECTING("连接中..."),
                ACTIVE("已连接"),
                SPEAKING("正在发言"),
                MUTED("已静音"),
                DISCONNECTED("已断开"),
                TIMEOUT("连接超时"),
                UNKNOWN("未知状态");
                
                private final String description;
                MemberUIState(String description) { this.description = description; }
                public String getDescription() { return description; }
            }
            
            // UI状态模型
            public static class CallUIState {
                public static final int ACTIVE = 1;
                public static final int ENDED = 2;
                public static final int ERROR = 3;
                
                public final int state;
                public final String message;
                
                public CallUIState(int state, String message) {
                    this.state = state;
                    this.message = message;
                }
            }
            
            // 暴露给UI的LiveData
            public LiveData<List<MemberUIModel>> getMembers() { return membersLiveData; }
            public LiveData<CallUIState> getCallState() { return callStateLiveData; }
            public LiveData<String> getErrorMessage() { return errorMessageLiveData; }
            
            // UI调用的操作方法
            public void muteMember(String userId) {
                callingVM.muteMember(userId);
            }
            
            public void endCall() {
                callingVM.hangUp();
            }
        }
        
        // 改进后的UI层实现
        public static class ImprovedGroupCallDialog extends BaseCallDialog {
            
            private GroupCallUIViewModel uiViewModel;
            private RecyclerView memberRecyclerView;
            private GroupMemberUIAdapter memberAdapter;
            
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                
                // 初始化UI ViewModel
                uiViewModel = new ViewModelProvider(this).get(GroupCallUIViewModel.class);
                
                // 设置观察者
                setupObservers();
                
                // 初始化UI
                initializeUI();
            }
            
            private void setupObservers() {
                // 观察成员列表变化
                uiViewModel.getMembers().observe(this, members -> {
                    if (members != null) {
                        updateMemberList(members);
                    }
                });
                
                // 观察通话状态变化
                uiViewModel.getCallState().observe(this, callState -> {
                    if (callState != null) {
                        handleCallStateChange(callState);
                    }
                });
                
                // 观察错误消息
                uiViewModel.getErrorMessage().observe(this, error -> {
                    if (error != null && !error.isEmpty()) {
                        showErrorMessage(error);
                    }
                });
            }
            
            // UI只需要处理UI模型，不关心业务模型
            private void updateMemberList(List<GroupCallUIViewModel.MemberUIModel> uiMembers) {
                memberAdapter.updateMembers(uiMembers);
                adjustGridLayout(uiMembers.size());
                
                // UI层的显示逻辑
                if (uiMembers.isEmpty()) {
                    showEmptyState();
                } else {
                    hideEmptyState();
                }
            }
            
            private void handleCallStateChange(GroupCallUIViewModel.CallUIState callState) {
                switch (callState.state) {
                    case GroupCallUIViewModel.CallUIState.ENDED:
                        showCallEndedMessage(callState.message);
                        dismiss();
                        break;
                    case GroupCallUIViewModel.CallUIState.ERROR:
                        showErrorDialog(callState.message);
                        break;
                }
            }
            
            // UI操作方法只调用ViewModel
            public void onMuteMemberClick(String userId) {
                uiViewModel.muteMember(userId);
            }
            
            public void onEndCallClick() {
                uiViewModel.endCall();
            }
        }
        
        // UI专用的Adapter，只处理UI模型
        public static class GroupMemberUIAdapter extends RecyclerView.Adapter<MemberViewHolder> {
            
            private List<GroupCallUIViewModel.MemberUIModel> members = new ArrayList<>();
            
            public void updateMembers(List<GroupCallUIViewModel.MemberUIModel> newMembers) {
                this.members = newMembers;
                notifyDataSetChanged();
            }
            
            @Override
            public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_member_ui, parent, false);
                return new MemberViewHolder(view);
            }
            
            @Override
            public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
                GroupCallUIViewModel.MemberUIModel member = members.get(position);
                holder.bind(member);
            }
            
            @Override
            public int getItemCount() {
                return members.size();
            }
            
            // ViewHolder只需要处理UI模型
            static class MemberViewHolder extends RecyclerView.ViewHolder {
                private final TextView nameText;
                private final ImageView avatarImage;
                private final TextView statusText;
                private final ImageView speakingIndicator;
                
                public MemberViewHolder(@NonNull View itemView) {
                    super(itemView);
                    nameText = itemView.findViewById(R.id.tv_member_name);
                    avatarImage = itemView.findViewById(R.id.iv_member_avatar);
                    statusText = itemView.findViewById(R.id.tv_member_status);
                    speakingIndicator = itemView.findViewById(R.id.iv_speaking_indicator);
                }
                
                public void bind(GroupCallUIViewModel.MemberUIModel member) {
                    nameText.setText(member.displayName);
                    statusText.setText(member.state.getDescription());
                    speakingIndicator.setVisibility(member.isSpeaking ? View.VISIBLE : View.GONE);
                    
                    // 加载头像
                    if (member.avatarUrl != null && !member.avatarUrl.isEmpty()) {
                        Glide.with(itemView.getContext())
                            .load(member.avatarUrl)
                            .placeholder(R.drawable.default_avatar)
                            .into(avatarImage);
                    } else {
                        avatarImage.setImageResource(R.drawable.default_avatar);
                    }
                }
            }
        }
    }
    
    /**
     * 问题2：UI状态管理混乱
     * 
     * 当前问题：
     * - UI状态散布在多个地方
     * - 没有统一的状态管理机制
     * - 状态恢复困难
     */
    
    public static class UIStateManagementFix {
        
        // UI状态管理器
        public static class GroupCallUIState {
            // 成员显示状态
            public int gridSpanCount = 2;
            public boolean showEmptyState = false;
            public ScrollPosition scrollPosition = new ScrollPosition();
            
            // 控制按钮状态
            public boolean isMicrophoneEnabled = true;
            public boolean isCameraEnabled = true;
            public boolean isSpeakerEnabled = false;
            
            // 对话框状态
            public boolean isMinimized = false;
            public WindowPosition windowPosition = new WindowPosition();
            
            // 保存状态到Bundle
            public Bundle toBundle() {
                Bundle bundle = new Bundle();
                bundle.putInt("gridSpanCount", gridSpanCount);
                bundle.putBoolean("showEmptyState", showEmptyState);
                bundle.putBoolean("isMicrophoneEnabled", isMicrophoneEnabled);
                bundle.putBoolean("isCameraEnabled", isCameraEnabled);
                bundle.putBoolean("isSpeakerEnabled", isSpeakerEnabled);
                bundle.putBoolean("isMinimized", isMinimized);
                // ... 其他状态
                return bundle;
            }
            
            // 从Bundle恢复状态
            public static GroupCallUIState fromBundle(Bundle bundle) {
                GroupCallUIState state = new GroupCallUIState();
                if (bundle != null) {
                    state.gridSpanCount = bundle.getInt("gridSpanCount", 2);
                    state.showEmptyState = bundle.getBoolean("showEmptyState", false);
                    state.isMicrophoneEnabled = bundle.getBoolean("isMicrophoneEnabled", true);
                    state.isCameraEnabled = bundle.getBoolean("isCameraEnabled", true);
                    state.isSpeakerEnabled = bundle.getBoolean("isSpeakerEnabled", false);
                    state.isMinimized = bundle.getBoolean("isMinimized", false);
                    // ... 恢复其他状态
                }
                return state;
            }
        }
        
        static class ScrollPosition {
            int position = 0;
            int offset = 0;
        }
        
        static class WindowPosition {
            int x = 0;
            int y = 0;
        }
    }
}