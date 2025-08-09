package io.openim.android.ouiconversation.ui.fragment;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.alibaba.android.arouter.launcher.ARouter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hjq.permissions.Permission;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.openim.android.ouiconversation.R;
import io.openim.android.ouiconversation.databinding.FragmentInputExpandBinding;
import io.openim.android.ouiconversation.databinding.ItemExpandMenuBinding;
import io.openim.android.ouiconversation.ui.ChatActivity;
import io.openim.android.ouiconversation.ui.ShootActivity;
import io.openim.android.ouiconversation.vm.ChatVM;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseFragment;
import io.openim.android.ouicore.base.vm.injection.Easy;
import io.openim.android.ouicore.databinding.LayoutCommonDialogBinding;
import io.openim.android.ouicore.ex.MultipleChoice;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.ActivityManager;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.GetFilePathFromUri;
import io.openim.android.ouicore.utils.GlideEngine;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.MThreadTool;
import io.openim.android.ouicore.utils.MediaFileUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.vm.SelectTargetVM;
import io.openim.android.ouicore.widget.CommonDialog;
import io.openim.android.ouicore.widget.WebViewActivity;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.listener.OnBase;
import io.openim.android.sdk.models.CardElem;
import io.openim.android.sdk.models.FriendInfo;
import io.openim.android.sdk.models.Message;
import io.openim.android.sdk.models.UserInfo;

public class InputExpandFragment extends BaseFragment<ChatVM> {
    public static List<Integer> menuIcons =
        Arrays.asList(
            io.openim.android.ouicore.R.mipmap.ic_chat_photo,     // 相册
            io.openim.android.ouiconversation.R.mipmap.ic_chat_shoot,      // 拍摄  
            io.openim.android.ouiconversation.R.mipmap.ic_chat_menu_file,  // 文件
            io.openim.android.ouiconversation.R.mipmap.ic_chat_location,   // 位置
            io.openim.android.ouiconversation.R.mipmap.ic_business_card     // 名片
        );
    public static List<String> menuTitles =
        Arrays.asList(
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.album),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.shoot),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.file),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.location),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.business_card)
        );

    FragmentInputExpandBinding v;
    // permissions
    private HasPermissions hasStorage;
    
    // ActivityResultLaunchers for different features
    private ActivityResultLauncher<Intent> shootLauncher;
    private ActivityResultLauncher<Intent> fileLauncher;
    private ActivityResultLauncher<Intent> contactCardLauncher;
    private ActivityResultLauncher<Intent> locationLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MThreadTool.executorService.execute(() -> {
            hasStorage = new HasPermissions(getActivity(), Permission.MANAGE_EXTERNAL_STORAGE);
        });
        initActivityResultLaunchers();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        v = FragmentInputExpandBinding.inflate(inflater);
        init();
        return v.getRoot();
    }

    private void init() {
        v.getRoot().setLayoutManager(new GridLayoutManager(getContext(), 4));
        RecyclerViewAdapter adapter =
            new RecyclerViewAdapter<Object, ExpandHolder>(ExpandHolder.class) {

                @Override
                public void onBindView(@NonNull ExpandHolder holder, Object data, int position) {
                    holder.v.menu.setCompoundDrawablesRelativeWithIntrinsicBounds(null,
                        getContext().getDrawable(menuIcons.get(position)), null, null);
                    holder.v.menu.setText(menuTitles.get(position));
                    holder.v.menu.setOnClickListener(v -> {
                        switch (position) {
                            case 0: // 相册
                                showMediaPicker();
                                break;
                            case 1: // 拍摄
                                showCameraCapture();
                                break;
                            case 2: // 文件
                                showFilePicker();
                                break;
                            case 3: // 位置
                                showLocationPicker();
                                break;
                            case 4: // 名片
                                showContactCardPicker();
                                break;
                        }
                    });
                }
            };
        v.getRoot().setAdapter(adapter);
        adapter.setItems(menuIcons);
    }

    private final ActivityResultLauncher<Intent> captureLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    ArrayList<LocalMedia> files = PictureSelector.obtainSelectorList(data);

                    for (LocalMedia file : files) {
                        String path = GetFilePathFromUri.getFileAbsolutePath(InputExpandFragment.this.getActivity() ,Uri.parse(file.getAvailablePath()));
                        Message msg = null;
                        if (MediaFileUtil.isImageType(path)) {
                            msg =
                                OpenIMClient.getInstance().messageManager.createImageMessageFromFullPath(path);
                        }
                        if (MediaFileUtil.isVideoType(path)) {
                            Glide.with(this).asBitmap().load(path).into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap resource,
                                                            @Nullable Transition<? super Bitmap> transition) {
                                    String firstFame = MediaFileUtil.saveBitmap(resource,
                                        Constants.PICTURE_DIR, false);
                                    long duration = MediaFileUtil.getDuration(path) / 1000;
                                    Message msg =
                                        OpenIMClient.getInstance().messageManager.createVideoMessageFromFullPath(path, MediaFileUtil.getFileType(path).mimeType, duration, firstFame);
                                    vm.sendMsg(msg);
                                }
                            });
                            continue;
                        }
                        if (null == msg) {
                            // 对于其他文件类型，使用文件消息
                            msg = OpenIMClient.getInstance().messageManager.createFileMessageFromFullPath(path, new File(path).getName());
                        }
                        vm.sendMsg(msg);
                    }
                }
            } catch (Exception e) {
                L.e(e.getMessage());
            }
        });

    @SuppressLint("unchecked")
    private void showMediaPicker() {
        hasStorage.safeGo(() -> {
            try {
                PictureSelector.create(this)
                    .openGallery(SelectMimeType.ofAll())
                    .setImageEngine(GlideEngine.createGlideEngine())
                    .setMaxVideoSelectNum(9)
                    .setMaxSelectNum(9)
                    .forResult(captureLauncher);
            } catch (Exception e) {
                L.e(e.getMessage());
            }
        });
    }

    /**
     * 初始化所有ActivityResultLauncher
     */
    private void initActivityResultLaunchers() {
        // 拍摄功能launcher
        shootLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleShootResult(result.getData());
            }
        });
        
        // 文件选择launcher
        fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleFileResult(result.getData());
            }
        });
        
        // 联系人名片选择launcher
        contactCardLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleContactCardResult(result.getData());
            }
        });
        
        // 位置选择launcher
        locationLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                handleLocationResult(result.getData());
            }
        });
    }

    /**
     * 显示相机拍摄界面
     */
    private void showCameraCapture() {
        hasStorage.safeGo(() -> {
            try {
                Intent intent = new Intent(getActivity(), ShootActivity.class);
                intent.putExtra(Constants.K_RESULT, com.cjt2325.cameralibrary.JCameraView.BUTTON_STATE_BOTH);
                shootLauncher.launch(intent);
            } catch (Exception e) {
                L.e("showCameraCapture error: " + e.getMessage());
                Toast.makeText(getContext(), "相机启动失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 显示文件选择器
     */
    private void showFilePicker() {
        hasStorage.safeGo(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                fileLauncher.launch(Intent.createChooser(intent, "选择文件"));
            } catch (Exception e) {
                L.e("showFilePicker error: " + e.getMessage());
                Toast.makeText(getContext(), "文件选择器启动失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 显示位置选择器
     */
    private void showLocationPicker() {
        // 检查地图key是否配置
        if (TextUtils.isEmpty(WebViewActivity.mapAppKey)) {
            Toast.makeText(getContext(), "请配置地图key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent intent = new Intent(getActivity(), WebViewActivity.class);
            intent.putExtra(WebViewActivity.ACTION, WebViewActivity.LOCATION);
            locationLauncher.launch(intent);
        } catch (Exception e) {
            L.e("showLocationPicker error: " + e.getMessage());
            Toast.makeText(getContext(), "位置功能启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示联系人名片选择器
     */
    private void showContactCardPicker() {
        try {
            // 直接启动ForwardToActivity，传递名片选择模式参数
            Intent intent = (Intent) ARouter.getInstance()
                .build(Routes.Contact.FORWARD)
                .withString("mode", "business_card")
                .navigation();
            if (intent != null) {
                contactCardLauncher.launch(intent);
            } else {
                // 备用方案：直接创建 Intent
                try {
                    Class<?> activityClass = Class.forName("io.openim.android.ouicontact.ui.ForwardToActivity");
                    Intent directIntent = new Intent(getActivity(), activityClass);
                    directIntent.putExtra("mode", "business_card");
                    contactCardLauncher.launch(directIntent);
                } catch (ClassNotFoundException e2) {
                    L.e("ForwardToActivity class not found: " + e2.getMessage());
                    Toast.makeText(getContext(), "联系人选择器不可用", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            L.e("showContactCardPicker error: " + e.getMessage());
            Toast.makeText(getContext(), "联系人选择器启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理拍摄结果
     */
    private void handleShootResult(Intent data) {
        try {
            String fileUrl = data.getStringExtra("fileUrl");
            String firstFrameUrl = data.getStringExtra("firstFrameUrl");
            
            if (!TextUtils.isEmpty(fileUrl)) {
                Message msg = null;
                if (MediaFileUtil.isImageType(fileUrl)) {
                    msg = OpenIMClient.getInstance().messageManager.createImageMessageFromFullPath(fileUrl);
                } else if (MediaFileUtil.isVideoType(fileUrl)) {
                    long duration = MediaFileUtil.getDuration(fileUrl) / 1000;
                    msg = OpenIMClient.getInstance().messageManager.createVideoMessageFromFullPath(
                        fileUrl, MediaFileUtil.getFileType(fileUrl).mimeType, duration, firstFrameUrl);
                }
                
                if (msg != null) {
                    vm.sendMsg(msg);
                }
            }
        } catch (Exception e) {
            L.e("handleShootResult error: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件选择结果
     */
    private void handleFileResult(Intent data) {
        try {
            Uri uri = data.getData();
            if (uri != null) {
                String filePath = GetFilePathFromUri.getFileAbsolutePath(getActivity(), uri);
                if (!TextUtils.isEmpty(filePath)) {
                    Message msg = null;
                    
                    // 根据文件类型创建对应的消息
                    if (MediaFileUtil.isImageType(filePath)) {
                        // 图片文件使用图片消息
                        msg = OpenIMClient.getInstance().messageManager.createImageMessageFromFullPath(filePath);
                    } else if (MediaFileUtil.isVideoType(filePath)) {
                        // 视频文件使用视频消息
                        try {
                            long duration = MediaFileUtil.getDuration(filePath) / 1000;
                            // 使用空字符串作为首帧，让SDK自动处理
                            msg = OpenIMClient.getInstance().messageManager.createVideoMessageFromFullPath(
                                filePath, MediaFileUtil.getFileType(filePath).mimeType, duration, "");
                        } catch (Exception videoException) {
                            L.e("Video processing failed, treating as file: " + videoException.getMessage());
                            // 视频处理失败，降级为文件消息
                            msg = OpenIMClient.getInstance().messageManager.createFileMessageFromFullPath(filePath, new File(filePath).getName());
                        }
                    } else {
                        // 其他文件使用文件消息
                        msg = OpenIMClient.getInstance().messageManager.createFileMessageFromFullPath(filePath, new File(filePath).getName());
                    }
                    
                    if (msg != null) {
                        vm.sendMsg(msg);
                    }
                }
            }
        } catch (Exception e) {
            L.e("handleFileResult error: " + e.getMessage());
            Toast.makeText(getContext(), "文件处理失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 处理位置选择结果
     */
    private void handleLocationResult(Intent data) {
        try {
            // 从 WebViewActivity 返回的位置信息
            String locationInfo = data.getStringExtra("locationInfo");
            if (!TextUtils.isEmpty(locationInfo)) {
                // TODO: 解析位置信息并创建位置消息
                Toast.makeText(getContext(), "位置功能待完善", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            L.e("handleLocationResult error: " + e.getMessage());
        }
    }
    
    /**
     * 处理联系人名片选择结果
     */
    private void handleContactCardResult(Intent data) {
        try {
            // 从 ForwardToActivity 返回的联系人信息
            String userID = data.getStringExtra(Constants.K_ID);
            String userName = data.getStringExtra(Constants.K_NAME);
            
            if (!TextUtils.isEmpty(userID) && !TextUtils.isEmpty(userName)) {
                // 直接发送名片，ForwardToActivity已经有确认机制
                sendContactCard(userID, userName);
            }
        } catch (Exception e) {
            L.e("handleContactCardResult error: " + e.getMessage());
        }
    }
    
    /**
     * 发送联系人名片
     */
    private void sendContactCard(String userID, String userName) {
        try {
            // 获取完整的用户信息（包括头像）
            OpenIMClient.getInstance().friendshipManager.getFriendsInfo(new OnBase<List<UserInfo>>() {
                @Override
                public void onError(int code, String error) {
                    // 获取好友信息失败，使用基本信息发送
                    sendCardWithBasicInfo(userID, userName, "");
                }

                @Override
                public void onSuccess(List<UserInfo> userInfos) {
                    String faceURL = "";
                    // 查找对应的用户信息
                    for (UserInfo userInfo : userInfos) {
                        if (userID.equals(userInfo.getUserID())) {
                            faceURL = userInfo.getFaceURL() != null ? userInfo.getFaceURL() : "";
                            break;
                        }
                    }
                    sendCardWithBasicInfo(userID, userName, faceURL);
                }
            }, Arrays.asList(userID));
        } catch (Exception e) {
            L.e("sendContactCard error: " + e.getMessage());
            // 异常情况下使用基本信息发送
            sendCardWithBasicInfo(userID, userName, "");
        }
    }
    
    /**
     * 使用基本信息发送名片
     */
    private void sendCardWithBasicInfo(String userID, String userName, String faceURL) {
        try {
            // 创建名片消息，使用OpenIM SDK的标准名片消息类型
            CardElem cardElem = new CardElem();
            cardElem.setUserID(userID);
            cardElem.setNickname(userName);
            cardElem.setFaceURL(faceURL);
            
            // 尝试使用createCardMessage方法，如果不存在则使用自定义消息
            Message cardMsg = null;
            try {
                // 尝试使用标准名片消息创建方法
                cardMsg = OpenIMClient.getInstance().messageManager.createCardMessage(cardElem);
            } catch (Exception cardException) {
                // 如果没有createCardMessage方法，使用自定义消息作为备选方案
                L.e("createCardMessage not available, using custom message: " + cardException.getMessage());
                String cardData = "{\"userID\":\"" + userID + "\",\"nickname\":\"" + userName + "\",\"faceURL\":\"" + faceURL + "\"}";
                cardMsg = OpenIMClient.getInstance().messageManager.createCustomMessage(
                    cardData, "名片", "{\"type\":\"business_card\"}");
            }
                
            if (cardMsg != null) {
                vm.sendMsg(cardMsg);
                Toast.makeText(getContext(), "名片发送成功", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            L.e("sendCardWithBasicInfo error: " + e.getMessage());
            Toast.makeText(getContext(), "名片发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void setChatVM(ChatVM vm) {
        this.vm = vm;
    }

    public static class ExpandHolder extends RecyclerView.ViewHolder {
        public ItemExpandMenuBinding v;

        public ExpandHolder(@NonNull View itemView) {
            super(ItemExpandMenuBinding.inflate(LayoutInflater.from(itemView.getContext())).getRoot());
            v = ItemExpandMenuBinding.bind(this.itemView);
        }
    }
}
