package io.openim.android.ouigroup.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.github.promeg.pinyinhelper.Pinyin;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import io.openim.android.ouigroup.debug.GroupCallDebugLogger;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.adapter.ViewHol;
import io.openim.android.ouicore.base.BaseActivity;
import io.openim.android.ouicore.base.vm.injection.Easy;
import io.openim.android.ouicore.databinding.LayoutPopSelectedFriendsBinding;
import io.openim.android.ouicore.entity.ExGroupMemberInfo;
import io.openim.android.ouicore.entity.ExUserInfo;
import io.openim.android.ouicore.entity.SelectableUser;
import io.openim.android.ouicore.entity.GroupMemberSelectable;
import io.openim.android.ouicore.base.BaseApp;

import io.openim.android.ouicore.ex.MultipleChoice;
import io.openim.android.ouicore.net.bage.GsonHel;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.ouicore.utils.Routes;


import io.openim.android.ouicore.vm.SelectTargetVM;
import io.openim.android.ouigroup.databinding.ActivityInitiateGroupBinding;

import io.openim.android.ouicore.vm.GroupVM;

import io.openim.android.sdk.models.FriendInfo;
import io.openim.android.sdk.models.GroupMembersInfo;
import io.openim.android.sdk.models.UserInfo;

/**
 * 发起群聊/邀请入群/移除群聊/选择群成员
 */
@Route(path = Routes.Group.CREATE_GROUP)
public class InitiateGroupActivity extends BaseActivity<GroupVM, ActivityInitiateGroupBinding> {

    private RecyclerViewAdapter<ExUserInfo, RecyclerView.ViewHolder> adapter;


    private boolean isInviteToGroup = false;
    private boolean isRemoveGroup = false;
    private boolean isSelectMember = false;
    private boolean isSelectFriend = false;
    private int maxNum;

    //选择的人数
    private int selectMemberNum;
    private String title;
    //默认已选择的id
    private String defSelectId;

    private SelectTargetVM selectTargetVM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isInviteToGroup = getIntent().getBooleanExtra(Constants.IS_INVITE_TO_GROUP, false);
        isRemoveGroup = getIntent().getBooleanExtra(Constants.IS_REMOVE_GROUP, false);
        isSelectMember = getIntent().getBooleanExtra(Constants.IS_SELECT_MEMBER, false);
        isSelectFriend = getIntent().getBooleanExtra(Constants.IS_SELECT_FRIEND, false);
        maxNum = getIntent().getIntExtra(Constants.K_SIZE, 0);
        String groupId = getIntent().getStringExtra(Constants.K_GROUP_ID);
        title = getIntent().getStringExtra(Constants.K_NAME);
        defSelectId = getIntent().getStringExtra(Constants.K_ID);

        if (isInviteToGroup || isRemoveGroup)
            bindVMByCache(GroupVM.class);
        else
            bindVM(GroupVM.class, true);

        super.onCreate(savedInstanceState);
        bindViewDataBinding(ActivityInitiateGroupBinding.inflate(getLayoutInflater()));

        initView();

        if (isSelectMember) {
            GroupCallDebugLogger.logMemberSelectionStart(groupId, getIntent().getBooleanExtra("isVideo", false));
            vm.groupId = groupId;
            // ✅ 修复：在成员选择模式下也需要获取群信息，避免空指针异常
            GroupCallDebugLogger.logGroupInfoInit(groupId, true);
            vm.getGroupsInfo();
            vm.getGroupMemberList();
        } else
            vm.getAllFriend();
        listener();
        buildSelectFriendsVM();
    }

    private void buildSelectFriendsVM() {
        try {
            selectTargetVM = Easy.find(SelectTargetVM.class);
            selectMemberNum = selectTargetVM.metaData.getValue().size();
            selectTargetVM.bindDataToView(view.bottom);
            selectTargetVM.showPopAllSelectFriends(view.bottom,
                LayoutPopSelectedFriendsBinding.inflate(getLayoutInflater()));
            selectTargetVM.submitTap(view.bottom.submit);

            selectTargetVM.metaData.observe(this, v -> adapter.notifyDataSetChanged());
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isInviteToGroup && !isRemoveGroup) removeCacheVM();
    }

    private void initView() {
        sink();
        if (isInviteToGroup)
            view.title.setText(io.openim.android.ouicore.R.string.Invite_to_the_group);
        if (isRemoveGroup)
            view.title.setText(io.openim.android.ouicore.R.string.remove_group);
        if (isSelectMember) {
            view.title.setText(io.openim.android.ouicore.R.string.selete_member);
            view.bottom.submit.setText("确定（0/" + maxNum + "）");
        }
        if (!TextUtils.isEmpty(title)) view.title.setText(title);

        view.scrollView.fullScroll(View.FOCUS_DOWN);
        view.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter<ExUserInfo, RecyclerView.ViewHolder>() {
            private int STICKY = 1;
            private int ITEM = 2;

            private String lastSticky = "";

            @Override
            public void setItems(List<ExUserInfo> items) {
                if (items.isEmpty()) return;
                lastSticky = items.get(0).sortLetter;
                items.add(0, getExUserInfo());
                for (int i = 0; i < items.size(); i++) {
                    ExUserInfo userInfo = items.get(i);
                    if (!lastSticky.equals(userInfo.sortLetter)) {
                        lastSticky = userInfo.sortLetter;
                        items.add(i, getExUserInfo());
                    }
                }

                super.setItems(items);
            }

            @NonNull
            private ExUserInfo getExUserInfo() {
                ExUserInfo exUserInfo = new ExUserInfo();
                exUserInfo.sortLetter = lastSticky;
                exUserInfo.isSticky = true;
                return exUserInfo;
            }

            @Override
            public int getItemViewType(int position) {
                return getItems().get(position).isSticky ? STICKY : ITEM;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                              int viewType) {
                if (viewType == ITEM) return new ViewHol.ItemViewHo(parent);
                return new ViewHol.StickyViewHo(parent);
            }

            @Override
            public void onBindView(@NonNull RecyclerView.ViewHolder holder, ExUserInfo data,
                                   int position) {
                if (getItemViewType(position) == ITEM) {
                    ViewHol.ItemViewHo itemViewHo = (ViewHol.ItemViewHo) holder;
                    
                    // 🔥 使用适配器模式统一处理数据显示
                    if (data.selectableUser != null) {
                        // ✅ 新的适配器逻辑：统一处理所有数据源
                        SelectableUser user = data.selectableUser;
                        itemViewHo.view.avatar.load(user.getAvatarUrl());
                        itemViewHo.view.nickName.setText(user.getDisplayName());
                        
                        // 设置按钮可用性
                        itemViewHo.view.item.setEnabled(user.isEnabled());
                        itemViewHo.view.item.setAlpha(user.isEnabled() ? 1.0f : 0.5f);
                        
                        Log.d("InitiateGroupActivity", "使用适配器显示用户: " + user.getDisplayName() + " (" + user.getSourceType() + ")");
                        
                    } else if (isRemoveGroup || isSelectMember) {
                        // 📌 兼容性逻辑：群成员管理（旧逻辑）
                        ExGroupMemberInfo memberInfo = data.exGroupMemberInfo;
                        if (memberInfo != null && memberInfo.groupMembersInfo != null) {
                            itemViewHo.view.avatar.load(memberInfo.groupMembersInfo.getFaceURL());
                            itemViewHo.view.nickName.setText(memberInfo.groupMembersInfo.getNickname());
                        }
                        
                    } else {
                        // 📌 兼容性逻辑：朋友选择（旧逻辑）
                        FriendInfo friendInfo = data.userInfo.getFriendInfo();
                        if (friendInfo != null) {
                            itemViewHo.view.avatar.load(friendInfo.getFaceURL());
                            itemViewHo.view.nickName.setText(friendInfo.getNickname());
                        } else {
                            // 如果FriendInfo为null，使用UserInfo的信息
                            itemViewHo.view.avatar.load(data.userInfo.getFaceURL());
                            itemViewHo.view.nickName.setText(data.userInfo.getNickname());
                            Log.w("InitiateGroupActivity", "FriendInfo为null，使用UserInfo信息");
                        }
                    }
                    itemViewHo.view.select.setVisibility(View.VISIBLE);
                    itemViewHo.view.select.setChecked(data.isSelect);
                    if (!data.isEnabled) itemViewHo.view.item.setOnClickListener(null);
                    else itemViewHo.view.item.setOnClickListener(v -> {
                        if (isSelectMember && selectMemberNum >= maxNum) {
                            toast(String.format(getString(io.openim.android.ouicore.R.string.select_tips), maxNum));
                            return;
                        }
                        data.isSelect = !data.isSelect;
                        notifyItemChanged(position);
                        selected();

                        if (null != selectTargetVM) {
                            // 🔥 使用适配器统一获取用户信息
                            String userId, displayName, avatarUrl;
                            
                            if (data.selectableUser != null) {
                                // ✅ 优先使用适配器
                                SelectableUser user = data.selectableUser;
                                userId = user.getUserId();
                                displayName = user.getDisplayName();
                                avatarUrl = user.getAvatarUrl();
                            } else {
                                // 📌 兼容性逻辑
                                userId = data.getUserId();
                                displayName = data.getDisplayName();
                                avatarUrl = data.getAvatarUrl();
                            }
                            
                            if (data.isSelect) {
                                selectTargetVM.addMetaData(userId, displayName, avatarUrl);
                            } else {
                                selectTargetVM.removeMetaData(userId);
                            }
                        }

                    });
                } else {
                    ViewHol.StickyViewHo stickyViewHo = (ViewHol.StickyViewHo) holder;
                    stickyViewHo.view.title.setText(data.sortLetter);
                }
            }
        };
        view.recyclerView.setAdapter(adapter);
    }

    private void selected() {
        selectMemberNum = getSelectNum();
        view.bottom.selectNum.setText(String.format(getString(io.openim.android.ouicore.R.string.selected_tips), selectMemberNum));
        if (isSelectMember)
            view.bottom.submit.setText("确定（" + selectMemberNum + "/" + maxNum + "）");
        else
            view.bottom.submit.setText("确定（" + selectMemberNum + "/999）");

        if (isInviteToGroup){
            view.bottom.selectNum.setVisibility(vm.selectedFriendInfoV3.size()>0?View.VISIBLE:View.GONE);
            view.bottom.submit.setEnabled(selectMemberNum > 0&&vm.selectedFriendInfoV3.size()>0);
        }else {
            view.bottom.submit.setEnabled(selectMemberNum > 0);
        }


    }

    private int getSelectNum() {
        List<FriendInfo> friendInfos = new ArrayList<>();
        vm.selectedFriendInfoV3.clear();
        int num = 0;
        for (ExUserInfo item : adapter.getItems()) {
            if (item.isSelect) {
                num++;
                if (isRemoveGroup || isSelectMember) {
                    FriendInfo friendInfo = new FriendInfo();
                    friendInfo.setUserID(item.exGroupMemberInfo.groupMembersInfo.getUserID());
                    friendInfos.add(friendInfo);
                    continue;
                }
                // 🔥 修复空指针异常：安全处理FriendInfo
                FriendInfo friendInfo = item.userInfo.getFriendInfo();
                if (friendInfo != null) {
                    friendInfos.add(friendInfo);
                    if (item.isEnabled) {
                        vm.selectedFriendInfoV3.add(friendInfo);
                    }
                } else {
                    // 如果FriendInfo为null，创建一个FriendInfo对象
                    FriendInfo newFriendInfo = new FriendInfo();
                    newFriendInfo.setUserID(item.userInfo.getUserID());
                    newFriendInfo.setNickname(item.userInfo.getNickname());
                    newFriendInfo.setFaceURL(item.userInfo.getFaceURL());
                    friendInfos.add(newFriendInfo);
                    if (item.isEnabled) {
                        vm.selectedFriendInfoV3.add(newFriendInfo);
                    }
                    Log.w("InitiateGroupActivity", "FriendInfo为null，创建新的FriendInfo对象");
                }
            }
        }
        vm.selectedFriendInfo.setValue(friendInfos);
        return num;
    }

    private void listener() {
        if (isRemoveGroup || isSelectMember) {
            vm.groupLetters.observe(this, v -> {
                if (null == v || v.isEmpty()) return;
                StringBuilder letters = new StringBuilder();
                for (String s : v) {
                    letters.append(s);
                }
                view.sortView.setLetters(letters.toString());
            });
            vm.exGroupMembers.observe(this, v -> {
                if (null == v || v.isEmpty()) return;
                
                // 🔥 关键修复：使用适配器模式统一处理群成员数据
                List<ExGroupMemberInfo> groupMemberInfo = new ArrayList<>();
                groupMemberInfo.addAll(v);
                
                try {
                    // 处理群管理员
                    // ✅ 修复：在isSelectMember模式下，groupsInfo可能为null
                    String groupOwnerId = "";
                    if (vm.groupsInfo.getValue() != null) {
                        groupOwnerId = vm.groupsInfo.getValue().getOwnerUserID();
                        GroupCallDebugLogger.logGroupInfoResult(true, groupOwnerId);
                    } else {
                        GroupCallDebugLogger.logGroupInfoResult(false, null);
                        GroupCallDebugLogger.logNullPointerHandled("exGroupMembers.observe", "vm.groupsInfo.getValue()为null，使用空字符串作为groupOwnerId");
                    }
                    
                    for (ExGroupMemberInfo memberInfo : vm.exGroupManagement.getValue()) {
                        if (!memberInfo.groupMembersInfo.getUserID().equals(groupOwnerId)) {
                            String nickName = memberInfo.groupMembersInfo.getNickname();
                            String letter = Pinyin.toPinyin(nickName.charAt(0));
                            memberInfo.sortLetter = (letter.charAt(0) + "").trim().toUpperCase();

                            boolean notContain = true;
                            for (int i = 0; i < v.size(); i++) {
                                if (v.get(i).sortLetter.equals(memberInfo.sortLetter)) {
                                    groupMemberInfo.add(i, memberInfo);
                                    notContain = false;
                                    break;
                                }
                            }
                            if (notContain) groupMemberInfo.add(0, memberInfo);
                        }
                    }
                } catch (Exception e) {
                    Log.w("InitiateGroupActivity", "处理群管理员失败", e);
                }

                // 🔥 使用适配器模式转换数据
                List<ExUserInfo> exUserInfos = new ArrayList<>();
                String currentUserId = BaseApp.inst().loginCertificate.userID;
                // ✅ 重新获取groupOwnerId以确保作用域正确
                String groupOwnerId = "";
                if (vm.groupsInfo.getValue() != null) {
                    groupOwnerId = vm.groupsInfo.getValue().getOwnerUserID();
                } else {
                    GroupCallDebugLogger.logNullPointerHandled("适配器作用域", "vm.groupsInfo.getValue()仍为null");
                }
                boolean isForGroupCall = getIntent().getBooleanExtra("isGroupCall", false);
                
                for (ExGroupMemberInfo exGroupMemberInfo : groupMemberInfo) {
                    ExUserInfo exUserInfo = new ExUserInfo();
                    exUserInfo.sortLetter = exGroupMemberInfo.sortLetter;
                    exUserInfo.exGroupMemberInfo = exGroupMemberInfo;
                    
                    // ✅ 使用适配器代替原有的UserInfo创建逻辑
                    GroupMemberSelectable selectable = new GroupMemberSelectable(
                        exGroupMemberInfo.groupMembersInfo, 
                        currentUserId, 
                        groupOwnerId, 
                        isForGroupCall
                    );
                    exUserInfo.selectableUser = selectable;
                    
                    // 保留原有的userInfo用于兼容性（可选）
                    UserInfo userInfo = new UserInfo();
                    userInfo.setUserID(exGroupMemberInfo.groupMembersInfo.getUserID());
                    userInfo.setNickname(exGroupMemberInfo.groupMembersInfo.getNickname());
                    userInfo.setFaceURL(exGroupMemberInfo.groupMembersInfo.getFaceURL());
                    exUserInfo.userInfo = userInfo;
                    
                    exUserInfos.add(exUserInfo);
                }
                
                GroupCallDebugLogger.logMemberDataLoaded(exUserInfos.size());
                adapter.setItems(exUserInfos);
            });
        } else {
            vm.letters.observe(this, v -> {
                if (null == v || v.isEmpty()) return;
                StringBuilder letters = new StringBuilder();
                for (String s : v) {
                    letters.append(s);
                }
                view.sortView.setLetters(letters.toString());
            });
            vm.exUserInfo.observe(this, v -> {
                if (null == v || v.isEmpty()) return;
                List<ExUserInfo> exUserInfos = new ArrayList<>(v);
                for (ExUserInfo exUserInfo : exUserInfos) {
                    // 🔥 修复空指针异常：检查userInfo和getFriendInfo()是否为null
                    if (exUserInfo == null || exUserInfo.userInfo == null) {
                        Log.w("InitiateGroupActivity", "跳过null的ExUserInfo或UserInfo");
                        continue;
                    }
                    
                    String userId = null;
                    // 安全获取userId：优先使用getFriendInfo()，如果为null则使用getUserID()
                    if (exUserInfo.userInfo.getFriendInfo() != null) {
                        userId = exUserInfo.userInfo.getFriendInfo().getUserID();
                    } else {
                        userId = exUserInfo.userInfo.getUserID();
                        Log.w("InitiateGroupActivity", "FriendInfo为null，使用UserInfo.getUserID(): " + userId);
                    }
                    
                    if (TextUtils.isEmpty(userId)) {
                        Log.w("InitiateGroupActivity", "userId为空，跳过该用户");
                        continue;
                    }
                    
                    ExGroupMemberInfo exGroupMemberInfo = new ExGroupMemberInfo();
                    exGroupMemberInfo.groupMembersInfo = new GroupMembersInfo();
                    exGroupMemberInfo.groupMembersInfo.setUserID(userId);

                    if (vm.exGroupMembers.getValue().contains(exGroupMemberInfo)
                        || vm.exGroupManagement.getValue().contains(exGroupMemberInfo)
                        || userId.equals(defSelectId)) {
                        exUserInfo.isEnabled = false;
                        exUserInfo.isSelect = true;
                    }

                    if (null != selectTargetVM) {
                        MultipleChoice data=new MultipleChoice();
                        data.key=userId;
                        exUserInfo.isSelect = selectTargetVM.contains(data);
                    }
                }
                adapter.setItems(exUserInfos);
            });
        }

        view.sortView.setOnLetterChangedListener((letter, position) -> {
            for (int i = 0; i < adapter.getItems().size(); i++) {
                ExUserInfo exUserInfo = adapter.getItems().get(i);
                if (!exUserInfo.isSticky) continue;
                if (exUserInfo.sortLetter.equalsIgnoreCase(letter)) {
                    View viewByPosition =
                        view.recyclerView.getLayoutManager().findViewByPosition(i);
                    if (viewByPosition != null) {
                        view.scrollView.smoothScrollTo(0, viewByPosition.getTop());
                    }
                    return;
                }
            }
        });
        view.bottom.submit.setOnClickListener(new OnDedrepClickListener(850) {
            @Override
            public void click(View v) {
                try {
                    if (isInviteToGroup) {
//                        vm.inviteUserToGroup(vm.selectedFriendInfoV3);
                        return;
                    }
                    if (isSelectMember) {
                        GroupCallDebugLogger.logMemberSelectionConfirm(vm.selectedFriendInfo.getValue().size());
                        ArrayList<String> ids = new ArrayList<>();
                        for (FriendInfo friendInfo : vm.selectedFriendInfo.getValue()) {
                            ids.add(friendInfo.getUserID());
                        }
                        GroupCallDebugLogger.logMemberSelectionResult(ids);
                        setResult(RESULT_OK, new Intent().putStringArrayListExtra(Constants.K_RESULT,
                            ids));
                        finish();
                        return;
                    }
                    if (isSelectFriend) {
                        setResult(RESULT_OK, new Intent().putExtra(Constants.K_RESULT,
                            GsonHel.toJson(vm.selectedFriendInfo.getValue())));
                        finish();
                        return;
                    }
                    createLauncher.launch(getIntent().setClass(InitiateGroupActivity.this,
                        CreateGroupActivity.class));
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void onSuccess(Object body) {
        super.onSuccess(body);
        finish();
    }

    private final ActivityResultLauncher<Intent> createLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                removeCacheVM();
                finish();
            }
        });

}
