# 群组音视频通话ADB调试指南

## 🎯 关键路径日志标识

使用统一的TAG `GroupCallFlow` 来标识群组通话相关的关键路径日志，方便过滤和排查。

### 📱 基本ADB命令

```bash
# 获取所有群组通话流程日志
adb logcat | grep "GroupCallFlow"

# 清除旧日志后开始监控
adb logcat -c && adb logcat | grep "GroupCallFlow"

# 保存日志到文件
adb logcat | grep "GroupCallFlow" > group_call_debug.log
```

## 🔍 关键路径标识符

| 符号 | 含义 | 示例 |
|------|------|------|
| 🚀 | 启动流程 | 🚀 [ChatActivity] 群组成员选择启动 |
| 👥 | 成员选择 | 👥 [Data] 群成员数据加载完成 |
| ✅ | 成功操作 | ✅ [Selection] 成员选择完成 |
| ❌ | 错误处理 | ❌ [Signaling] 群组信令构建失败 |
| ⚠️ | 警告信息 | ⚠️ [NullPointer] 处理空指针异常 |
| 🔍 | 调试信息 | 🔍 [Init] 群信息初始化 |
| 📥 | 接收数据 | 📥 [ChatActivity] 成员选择返回 |
| 📤 | 发送数据 | 📤 [ChatVM] 发送群组信令给CallingService |
| 🔧 | 构建过程 | 🔧 [ChatVM] 开始构建群组信令 |
| 📞 | 通话相关 | 📞 [CallingService] 发起群组通话 |

## 🎬 完整流程日志示例

### 1. 正常成功流程
```
🚀 [ChatActivity] 群组成员选择启动 - groupId: group123, 类型: 视频
🔍 [Init] 群信息初始化 - groupId: group123, isSelectMember: true  
✅ [Init] 群信息获取成功 - ownerId: owner456
👥 [Data] 群成员数据加载完成 - 数量: 8
🚀 [Selection] 用户点击确定 - 已选择3个成员
✅ [Selection] 成员选择完成 - 返回3个成员: [user1, user2, user3]
📤 [Selection] 设置Activity结果并关闭页面
📥 [ChatActivity] 成员选择返回 - resultCode: -1
✅ [ChatActivity] 成员选择完成 - 数量: 3, 类型: 视频
📥 [ChatVM] 用户选择了 3 个成员进行群组通话 - isVideo: true
🔧 [ChatVM] 开始构建群组信令 - isVideo: true, groupId: group123, 成员数: 3
✅ [ChatVM] 群组信令构建完成，详细信息：
📤 [ChatVM] 发送群组信令给CallingService
✅ [ChatVM] 群组通话信令已成功发送
```

### 2. 异常情况处理
```
🚀 [ChatActivity] 群组成员选择启动 - groupId: group123, 类型: 视频
🔍 [Init] 群信息初始化 - groupId: group123, isSelectMember: true
⚠️ [Init] 群信息获取失败，groupsInfo为null
⚠️ [NullPointer] exGroupMembers.observe - vm.groupsInfo.getValue()为null，使用空字符串作为groupOwnerId
👥 [Data] 群成员数据加载完成 - 数量: 8
```

## 🔧 分类过滤命令

### 按流程阶段过滤
```bash
# 成员选择流程
adb logcat | grep "GroupCallFlow" | grep -E "(Selection|MemberSelection)"

# 信令构建流程  
adb logcat | grep "GroupCallFlow" | grep -E "(Signaling|ChatVM)"

# 初始化流程
adb logcat | grep "GroupCallFlow" | grep -E "(Init|群信息)"

# 数据加载流程
adb logcat | grep "GroupCallFlow" | grep -E "(Data|群成员数据)"
```

### 按严重程度过滤
```bash
# 错误信息
adb logcat | grep "GroupCallFlow" | grep "❌"

# 警告信息
adb logcat | grep "GroupCallFlow" | grep "⚠️"

# 成功信息
adb logcat | grep "GroupCallFlow" | grep "✅"
```

### 按组件过滤
```bash
# ChatActivity相关
adb logcat | grep "GroupCallFlow" | grep "ChatActivity"

# ChatVM相关  
adb logcat | grep "GroupCallFlow" | grep "ChatVM"

# 成员选择Activity相关
adb logcat | grep "GroupCallFlow" | grep -E "(Selection|MemberSelection)"
```

## 🏥 常见问题排查

### 1. 选择成员后无法进入九宫格
**症状**: 点击确定后没有进入通话界面
```bash
# 检查成员选择是否成功返回
adb logcat | grep "GroupCallFlow" | grep -E "(成员选择返回|成员选择完成)"

# 检查是否有空指针异常
adb logcat | grep "GroupCallFlow" | grep "⚠️"
```

**期望看到的正常日志**:
```
📥 [ChatActivity] 成员选择返回 - resultCode: -1
✅ [ChatActivity] 成员选择完成 - 数量: X, 类型: 视频/音频
```

### 2. 信令构建失败
**症状**: 成员选择成功但通话未启动
```bash
# 检查信令构建过程
adb logcat | grep "GroupCallFlow" | grep -E "(信令|Signaling)"
```

**期望看到的正常日志**:
```
🔧 [ChatVM] 开始构建群组信令
✅ [ChatVM] 群组信令构建完成，详细信息：
📤 [ChatVM] 发送群组信令给CallingService
```

### 3. 群信息初始化异常
**症状**: 成员列表显示异常或无法加载
```bash
# 检查群信息初始化
adb logcat | grep "GroupCallFlow" | grep -E "(Init|群信息)"
```

## 📋 调试检查清单

使用以下命令逐步验证每个关键环节：

### 1. ✅ 成员选择启动
```bash
adb logcat | grep "GroupCallFlow" | grep "群组成员选择启动"
```

### 2. ✅ 群信息初始化  
```bash
adb logcat | grep "GroupCallFlow" | grep "群信息初始化"
```

### 3. ✅ 成员数据加载
```bash
adb logcat | grep "GroupCallFlow" | grep "群成员数据加载完成"  
```

### 4. ✅ 用户选择确认
```bash
adb logcat | grep "GroupCallFlow" | grep "用户点击确定"
```

### 5. ✅ 选择结果返回
```bash
adb logcat | grep "GroupCallFlow" | grep "成员选择完成"
```

### 6. ✅ 信令构建
```bash
adb logcat | grep "GroupCallFlow" | grep "开始构建群组信令"
```

### 7. ✅ 通话启动
```bash
adb logcat | grep "GroupCallFlow" | grep "群组通话信令已成功发送"
```

## 💡 使用建议

1. **实时监控**: 在测试前先清空日志缓存 `adb logcat -c`
2. **保存日志**: 将关键测试的日志保存到文件以便分析
3. **组合过滤**: 使用多个grep命令组合来精确定位问题
4. **时间戳**: 可以加上 `-v time` 参数查看准确的时间戳

```bash
# 带时间戳的完整监控
adb logcat -c && adb logcat -v time | grep "GroupCallFlow"
```

通过这些日志，可以清楚地追踪群组音视频通话的完整流程，快速定位问题所在！