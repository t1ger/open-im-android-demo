# CallDialog.java 已废弃

## ⚠️ 重要通知

原有的 `CallDialog.java` 文件已经被新的架构替代，但为了避免编译错误，暂时保留。

## 🆕 新架构

新的通话对话框架构使用以下组件：

### 基础类
- **BaseCallDialog.java** - 通话对话框基础类
- **SingleCallDialog.java** - 单人通话专用对话框
- **GroupCallDialog.java** - 群组通话专用对话框

### 工厂和处理器
- **CallDialogFactory.java** - 对话框工厂，根据信令类型创建对应对话框
- **SignalingProcessor.java** - 统一信令处理器

### 核心修复
1. **解决两次群组选择问题** - 通过工厂模式直接创建正确类型的对话框
2. **解决无法跳转九宫格问题** - GroupCallDialog直接显示九宫格界面
3. **解决双重UI初始化问题** - 每种对话框只初始化一次UI

## 🗑️ 废弃的组件

以下组件在新架构中已被废弃：

### CallDialog.java 中废弃的部分
- `DialogSwitchStateMachine` - 延迟切换状态机
- `switchToGroupCallModeInternal()` - UI切换方法
- `startDelayedGroupSwitch()` - 延迟切换逻辑
- 双重UI初始化逻辑

### 原因
- **复杂性过高**: 延迟切换机制引入了不必要的复杂性
- **状态不一致**: UI状态与信令状态容易不同步
- **用户体验差**: 用户需要等待UI切换过程
- **维护困难**: 一个类处理两种不同的业务逻辑

## 🚀 迁移指南

### 对于 CallingServiceImp
```java
// 旧方式
callDialog = new CallDialog(context, this, isCallOut);

// 新方式  
callDialog = CallDialogFactory.create(context, this, signalingInfo, isCallOut);
```

### 对于其他调用者
```java
// 旧方式
if (callDialog instanceof CallDialog) {
    CallDialog dialog = (CallDialog) callDialog;
    // ...
}

// 新方式
if (callDialog instanceof SingleCallDialog) {
    SingleCallDialog dialog = (SingleCallDialog) callDialog;
    // ...
} else if (callDialog instanceof GroupCallDialog) {
    GroupCallDialog dialog = (GroupCallDialog) callDialog;
    // ...
}
```

## 📅 废弃时间线

- **2024年12月** - 新架构实现完成
- **即将废弃** - 旧CallDialog.java将在下个版本中移除
- **建议行动** - 所有新代码请使用新架构

## 🛠️ 技术细节

新架构的主要优势：

1. **职责分离** - 每个对话框类只处理一种类型的通话
2. **工厂模式** - 统一的创建入口，减少错误
3. **信令标准化** - SignalingProcessor确保状态一致性
4. **性能优化** - 避免不必要的UI切换和资源浪费
5. **可测试性** - 更容易编写单元测试和集成测试

## 📞 联系

如有任何关于新架构的问题，请参考：
- BaseCallDialog.java - 基础实现
- CallDialogFactory.java - 创建逻辑
- SignalingProcessor.java - 信令处理