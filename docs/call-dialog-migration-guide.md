# CallDialog迁移指南

## 📋 迁移概述

由于CallDialog架构重构，原有的CallDialog类已被标记为@Deprecated。本指南将帮助开发者从旧架构迁移到新的分离式架构。

## 🎯 迁移目标

- 从单一CallDialog类迁移到职责分离的新架构
- 使用工厂模式替代复杂的条件判断
- 利用统一的信令处理机制
- 确保代码的可维护性和可扩展性

## 🔄 架构对比

### 旧架构 (已废弃)
```java
// 单一CallDialog处理所有通话类型
CallDialog dialog = new CallDialog(context);
dialog.setSignalingInfo(signaling);
dialog.show();

// 内部复杂的类型判断和界面切换
if (signaling.isGroupCall()) {
    dialog.switchToGroupMode(); // 复杂的状态切换
}
```

### 新架构 (推荐)
```java
// 工厂模式创建对应类型的对话框
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
dialog.show(); // 直接显示正确的界面，无切换过程
```

## 📝 具体迁移步骤

### 步骤1：更新导入语句
```java
// 旧导入
import io.openim.android.ouicalling.CallDialog;

// 新导入
import io.openim.android.ouicalling.CallDialogFactory;
import io.openim.android.ouicalling.BaseCallDialog;
import io.openim.android.ouicalling.SingleCallDialog;
import io.openim.android.ouicalling.GroupCallDialog;
```

### 步骤2：修改对话框创建代码
```java
// ❌ 旧方式
CallDialog dialog = new CallDialog(context);
dialog.setSignalingInfo(signaling);

// ✅ 新方式
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
```

### 步骤3：移除手动类型判断
```java
// ❌ 旧方式 - 手动判断和切换
CallDialog dialog = new CallDialog(context);
if (signaling.isGroupCall()) {
    dialog.enableGroupMode();
    dialog.setupGroupMembers(members);
} else {
    dialog.enableSingleMode();
    dialog.setupSingleCall(targetUser);
}

// ✅ 新方式 - 工厂自动处理
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
// 工厂会自动创建正确类型的对话框，无需手动判断
```

### 步骤4：更新事件监听器
```java
// ❌ 旧方式
CallDialog dialog = new CallDialog(context);
dialog.setOnCallDialogListener(new CallDialog.OnCallDialogListener() {
    @Override
    public void onDialogEvent(CallDialogEvent event) {
        // 处理事件
    }
});

// ✅ 新方式
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
dialog.setOnCallDialogListener(new BaseCallDialog.OnCallDialogListener() {
    @Override
    public void onDialogEvent(CallDialogEvent event) {
        // 处理事件 - 接口保持一致
    }
});
```

## 🔧 高级迁移场景

### 场景1：自定义对话框行为
```java
// ❌ 旧方式 - 通过继承CallDialog
public class CustomCallDialog extends CallDialog {
    @Override
    protected void initializeUI() {
        super.initializeUI();
        // 自定义初始化
    }
}

// ✅ 新方式 - 继承具体的Dialog类
public class CustomSingleCallDialog extends SingleCallDialog {
    @Override
    protected void initializeUI() {
        super.initializeUI();
        // 自定义单人通话初始化
    }
}

public class CustomGroupCallDialog extends GroupCallDialog {
    @Override
    protected void initializeUI() {
        super.initializeUI();
        // 自定义群组通话初始化
    }
}
```

### 场景2：条件创建逻辑
```java
// ❌ 旧方式 - 复杂的条件逻辑
public CallDialog createCallDialog(Context context, SignalingInfo signaling, boolean isDebugMode) {
    CallDialog dialog = new CallDialog(context);
    dialog.setSignalingInfo(signaling);
    
    if (signaling.isGroupCall()) {
        dialog.enableGroupMode();
        if (isDebugMode) {
            dialog.enableDebugMode();
        }
    } else {
        dialog.enableSingleMode();
    }
    
    return dialog;
}

// ✅ 新方式 - 利用工厂模式的扩展性
public BaseCallDialog createCallDialog(Context context, SignalingInfo signaling, boolean isDebugMode) {
    BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
    
    if (isDebugMode) {
        dialog.enableDebugMode(); // BaseCallDialog的通用方法
    }
    
    return dialog;
}
```

### 场景3：类型特定的操作
```java
// ❌ 旧方式 - 运行时类型检查
CallDialog dialog = createCallDialog(context, signaling);
if (dialog.isGroupCall()) {
    dialog.addGroupMember(newMember); // 运行时检查
}

// ✅ 新方式 - 编译时类型安全
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
if (dialog instanceof GroupCallDialog) {
    ((GroupCallDialog) dialog).addGroupMember(newMember); // 类型安全
}

// 🎯 更好的方式 - 多态处理
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
dialog.addMember(newMember); // BaseCallDialog的通用接口，子类各自实现
```

## 🚨 迁移注意事项

### 1. 接口兼容性
大部分公共接口保持兼容，但以下方法需要注意：
```java
// 已移除的方法
dialog.switchToGroupMode();     // ❌ 不再需要
dialog.switchToSingleMode();    // ❌ 不再需要
dialog.getDialogType();         // ❌ 使用 instanceof 替代

// 新增的方法
dialog.getCallType();           // ✅ 获取通话类型
dialog.isGroupCall();          // ✅ 判断是否群组通话
```

### 2. 状态管理差异
```java
// ❌ 旧方式 - 状态切换
CallDialog dialog = new CallDialog(context);
dialog.setState(CallDialog.STATE_SINGLE);
dialog.setState(CallDialog.STATE_GROUP); // 状态切换

// ✅ 新方式 - 状态固定
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
// Dialog创建后类型固定，无状态切换
```

### 3. 内存管理
```java
// 新架构下的正确清理方式
BaseCallDialog dialog = CallDialogFactory.createDialog(context, signaling);
try {
    dialog.show();
    // 使用对话框
} finally {
    dialog.dismiss(); // 确保正确清理
    dialog = null;    // 释放引用
}
```

## 🧪 迁移验证

### 验证清单
- [ ] 所有CallDialog实例创建已改为CallDialogFactory
- [ ] 移除了所有手动类型判断和切换逻辑
- [ ] 更新了自定义对话框的继承关系
- [ ] 验证事件监听器正常工作
- [ ] 确认内存泄漏已解决
- [ ] 单元测试通过

### 测试用例示例
```java
@Test
public void testGroupCallDialogCreation() {
    // 准备群组通话信令
    SignalingInfo groupSignaling = SignalingTestUtils.createGroupCallSignaling();
    
    // 使用工厂创建对话框
    BaseCallDialog dialog = CallDialogFactory.createDialog(context, groupSignaling);
    
    // 验证创建了正确类型的对话框
    assertTrue("应该创建GroupCallDialog", dialog instanceof GroupCallDialog);
    assertTrue("应该识别为群组通话", dialog.isGroupCall());
}

@Test
public void testSingleCallDialogCreation() {
    // 准备单人通话信令
    SignalingInfo singleSignaling = SignalingTestUtils.createSingleCallSignaling();
    
    // 使用工厂创建对话框
    BaseCallDialog dialog = CallDialogFactory.createDialog(context, singleSignaling);
    
    // 验证创建了正确类型的对话框
    assertTrue("应该创建SingleCallDialog", dialog instanceof SingleCallDialog);
    assertFalse("应该识别为单人通话", dialog.isGroupCall());
}
```

## 📚 相关文档

- [CallDialog架构重构完整总结](./call-dialog-architecture-refactor.md)
- [架构合规性修复记录](./architecture-compliance-fix.md)
- [群组音视频通话设计文档](./group-audio-video-design.md)

## 🆘 迁移支持

如果在迁移过程中遇到问题，请参考：
1. 查看新架构的JavaDoc文档
2. 参考项目中的示例代码
3. 运行提供的测试用例验证迁移结果

## 🎯 迁移完成标准

迁移完成后，你的代码应该：
- ✅ 不再直接使用CallDialog类
- ✅ 使用CallDialogFactory创建对话框
- ✅ 基于具体Dialog类型进行扩展
- ✅ 通过编译并且所有测试通过
- ✅ 群组通话能够正确显示九宫格界面