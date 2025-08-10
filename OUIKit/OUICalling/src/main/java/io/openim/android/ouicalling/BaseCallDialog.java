package io.openim.android.ouicalling;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import io.openim.android.ouicalling.databinding.LayoutFloatViewBinding;
import io.openim.android.ouicalling.vm.CallingVM;
import io.openim.android.ouicore.base.BaseDialog;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.LogExceptionHandler;
import io.openim.android.ouicore.utils.MediaPlayerUtil;
import com.hjq.window.EasyWindow;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.sdk.models.SignalingInfo;

/**
 * 通话对话框基础类
 * 提供通用的通话对话框功能，由具体的单人和群组对话框继承
 */
public abstract class BaseCallDialog extends BaseDialog {
    
    protected final Context context;
    protected CallingVM callingVM;
    protected SignalingInfo signalingInfo;
    
    // 通用组件
    protected LayoutFloatViewBinding floatViewBinding;
    protected EasyWindow easyWindow;
    protected HasPermissions hasShoot, hasRecord, hasSystemAlert;
    
    public BaseCallDialog(@NonNull Context context, CallingService callingService, boolean isCallOut) {
        super(context);
        this.context = context;
        
        // 初始化权限检查
        hasShoot = new HasPermissions(context, 
            android.Manifest.permission.CAMERA, 
            android.Manifest.permission.RECORD_AUDIO);
        hasRecord = new HasPermissions(context, android.Manifest.permission.RECORD_AUDIO);
        hasSystemAlert = new HasPermissions(context, android.Manifest.permission.SYSTEM_ALERT_WINDOW);
        
        // 初始化业务层
        callingVM = new CallingVM(callingService, isCallOut);
        callingVM.setDismissListener(v -> dismiss());
        
        // 必须在setContentView之前设置窗口属性
        setupWindowAttributes();
        
        // 初始化悬浮窗布局
        initFloatViewBinding();
        
        // 子类实现具体的UI初始化（这里会调用setContentView）
        initSpecificView();
    }
    
    /**
     * 初始化悬浮窗绑定
     */
    private void initFloatViewBinding() {
        floatViewBinding = LayoutFloatViewBinding.inflate(getLayoutInflater());
        floatViewBinding.shrink.setOnClickListener(v -> shrink(false));
    }
    
    /**
     * 设置窗口通用属性
     */
    private void setupWindowAttributes() {
        Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // 背景状态栏透明
        window.setDimAmount(1f);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        
        Common.addTypeSystemAlert(params);
        window.setAttributes(params);
        
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }
    
    /**
     * 绑定信令数据 - 通用逻辑
     */
    public final void bindData(SignalingInfo signalingInfo) {
        this.signalingInfo = signalingInfo;
        
        android.util.Log.d("GroupCallFlow", "🔧 [BaseCallDialog] 开始绑定数据 - 类型: " + this.getClass().getSimpleName());
        
        try {
            // 更新信令信息到状态管理器
            android.util.Log.d("GroupCallFlow", "📊 [BaseCallDialog] 更新信令信息到状态管理器");
            callingVM.updateSignalingInfo(signalingInfo);
            
            // 子类处理具体的数据绑定
            android.util.Log.d("GroupCallFlow", "🎯 [BaseCallDialog] 调用子类bindSpecificData - " + this.getClass().getSimpleName());
            bindSpecificData(signalingInfo);
            
            // 发起信令通话（如果是呼出）
            if (callingVM.isCallOut) {
                android.util.Log.d("GroupCallFlow", "📞 [BaseCallDialog] 发起信令通话 - 呼出模式");
                callingVM.signalingInvite(signalingInfo);
            }
            
            // 绑定用户信息
            android.util.Log.d("GroupCallFlow", "👤 [BaseCallDialog] 绑定用户信息");
            bindUserInfo(signalingInfo);
            
            // 设置事件监听
            android.util.Log.d("GroupCallFlow", "🔊 [BaseCallDialog] 设置事件监听");
            setupEventListeners(signalingInfo);
            
            android.util.Log.d("GroupCallFlow", "✅ [BaseCallDialog] 数据绑定完成 - " + this.getClass().getSimpleName());
            
        } catch (Exception e) {
            android.util.Log.e("GroupCallFlow", "❌ [BaseCallDialog] 数据绑定异常 - " + this.getClass().getSimpleName() + ": " + e.getMessage(), e);
            LogExceptionHandler.handleException("BaseCallDialog", "绑定数据失败", 
                LogExceptionHandler.ExceptionType.DATA_ERROR, e);
        }
    }
    
    /**
     * 悬浮窗显示/隐藏
     */
    public void shrink(boolean isShrink) {
        if (isShrink) {
            showFloatView();
        } else if (null != easyWindow) {
            easyWindow.cancel();
        }
        
        // 子类处理具体的收起逻辑
        handleShrink(isShrink);
        
        getWindow().setDimAmount(isShrink ? 0f : 1f);
    }
    
    /**
     * 显示悬浮窗
     */
    private void showFloatView() {
        if (null == easyWindow) {
            easyWindow = new EasyWindow<>(BaseApp.inst())
                .setContentView(floatViewBinding.getRoot())
                .setDraggable();
        }
        if (!easyWindow.isShowing()) easyWindow.show();
    }
    
    @Override
    public void show() {
        playRingtone();
        super.show();
    }
    
    /**
     * 播放铃声 - 统一使用incoming_call_ring铃声
     */
    public void playRingtone() {
        try {
            Common.wakeUp(context);
            
            if (!MediaPlayerUtil.INSTANCE.isPlaying()) {
                // 统一使用incoming_call_ring铃声，不管是呼出还是被呼叫
                MediaPlayerUtil.INSTANCE.initMedia(BaseApp.inst(), R.raw.incoming_call_ring);
                MediaPlayerUtil.INSTANCE.loopPlay();
            }
        } catch (Exception e) {
            LogExceptionHandler.handleException("BaseCallDialog", "播放铃声失败", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    @Override
    public void dismiss() {
        try {
            // 停止播放铃声
            MediaPlayerUtil.INSTANCE.pause();
            MediaPlayerUtil.INSTANCE.release();
            
            // 清理资源
            cleanup();
            
            // 清理悬浮窗
            if (easyWindow != null) {
                easyWindow.cancel();
                easyWindow = null;
            }
            
            super.dismiss();
            
        } catch (Exception e) {
            LogExceptionHandler.handleException("BaseCallDialog", "关闭对话框", 
                LogExceptionHandler.ExceptionType.UI_ERROR, e);
        }
    }
    
    // ========== 抽象方法，子类必须实现 ==========
    
    /**
     * 初始化具体类型的视图（单人或群组）
     */
    protected abstract void initSpecificView();
    
    /**
     * 绑定具体类型的数据
     */
    protected abstract void bindSpecificData(SignalingInfo signalingInfo);
    
    /**
     * 绑定用户信息（单人和群组不同）
     */
    protected abstract void bindUserInfo(SignalingInfo signalingInfo);
    
    /**
     * 设置事件监听器
     */
    protected abstract void setupEventListeners(SignalingInfo signalingInfo);
    
    /**
     * 处理悬浮窗收起逻辑
     */
    protected abstract void handleShrink(boolean isShrink);
    
    /**
     * 清理资源
     */
    protected abstract void cleanup();
    
    // ========== 需要子类实现的业务方法 ==========
    
    /**
     * 对方接受通话的回调
     */
    public abstract void otherSideAccepted();
    
    /**
     * 构建主键用于数据库操作
     */
    public abstract String buildPrimaryKey();
    
    // ========== Getter方法 ==========
    
    public CallingVM getCallingVM() {
        return callingVM;
    }
    
    public SignalingInfo getSignalingInfo() {
        return signalingInfo;
    }
    
    public Context getDialogContext() {
        return context;
    }
}