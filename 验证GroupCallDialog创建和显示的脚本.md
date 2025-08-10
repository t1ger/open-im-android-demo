# GroupCallDialog 创建和显示验证脚本

## 快速验证脚本

### 1. 一键验证脚本
```bash
#!/bin/bash
# 群组通话验证脚本
echo "开始监控群组通话流程..."
adb logcat -c  # 清空日志
adb logcat -s GroupCallFlow:V CallingService:V CallDialogFactory:V GroupCallDialog:V | while read line; do
    echo "$(date '+%H:%M:%S') $line"
    
    # 检查关键节点
    if [[ "$line" == *"成员选择完成"* ]]; then
        echo "✅ 步骤1: 成员选择成功"
    elif [[ "$line" == *"构建群组信令"* ]]; then
        echo "✅ 步骤2: 群组信令构建"
    elif [[ "$line" == *"创建对话框类型: GroupCall"* ]]; then
        echo "✅ 步骤3: GroupCallDialog创建"
    elif [[ "$line" == *"九宫格: VISIBLE, headTips: GONE"* ]]; then
        echo "✅ 步骤4: UI显示逻辑执行"
        echo "🎉 群组通话流程验证成功！"
    elif [[ "$line" == *"创建对话框类型: SingleCall"* ]]; then
        echo "❌ 错误: 误判为单人通话！"
    fi
done
```

### 2. 分步验证脚本

#### 验证CallDialogFactory判断逻辑
```bash
echo "=== 验证CallDialogFactory判断逻辑 ==="
adb logcat -c
adb logcat | grep -E "(CallDialogFactory|isGroupCall|SessionType)" --line-buffered | while read line; do
    echo "$(date '+%H:%M:%S') $line"
done
```

#### 验证GroupCallDialog创建
```bash
echo "=== 验证GroupCallDialog创建 ==="
adb logcat -c
adb logcat | grep -E "(GroupCallDialog|SingleCallDialog)" --line-buffered | while read line; do
    echo "$(date '+%H:%M:%S') $line"
    if [[ "$line" == *"GroupCallDialog"* ]]; then
        echo "✅ GroupCallDialog被创建"
    elif [[ "$line" == *"SingleCallDialog"* ]]; then
        echo "❌ 错误：创建了SingleCallDialog而不是GroupCallDialog"
    fi
done
```

#### 验证UI显示逻辑
```bash
echo "=== 验证UI显示逻辑 ==="
adb logcat -c
adb logcat | grep -E "(headTips|viewRenderers|setupVideoControls)" --line-buffered | while read line; do
    echo "$(date '+%H:%M:%S') $line"
    if [[ "$line" == *"headTips: GONE"* ]]; then
        echo "✅ 单人界面被隐藏"
    elif [[ "$line" == *"viewRenderers: VISIBLE"* ]]; then
        echo "✅ 九宫格界面显示"
    fi
done
```

## 详细检查清单

### ✅ 检查点1: 信令类型判断
```bash
# 查看群组信令的SessionType
adb logcat | grep -E "(SessionType|GROUP_CHAT|SINGLE_CHAT)"
```
**期望结果**：应该看到 `SessionType: GROUP_CHAT`

### ✅ 检查点2: CallDialogFactory创建类型
```bash
# 查看创建的对话框类型
adb logcat | grep -E "(CallDialogFactory.*create|创建对话框类型)"
```
**期望结果**：应该看到 `创建对话框类型: GroupCall`

### ✅ 检查点3: GroupCallDialog初始化
```bash
# 查看GroupCallDialog的初始化过程
adb logcat | grep -E "(GroupCallDialog.*构造|initSpecificView|九宫格架构)"
```
**期望结果**：应该看到构造函数、布局初始化等日志

### ✅ 检查点4: 布局显示控制
```bash
# 查看布局显示控制逻辑
adb logcat | grep -E "(setupVideoControls|headTips.*GONE|viewRenderers.*VISIBLE)"
```
**期望结果**：应该看到headTips隐藏、viewRenderers显示的日志

### ✅ 检查点5: 成员适配器绑定
```bash
# 查看成员列表适配器绑定
adb logcat | grep -E "(GroupMemberAdapter|GridLayoutManager|refreshMemberList)"
```
**期望结果**：应该看到适配器创建和数据绑定的日志

## 问题诊断表

| 现象 | 可能原因 | 验证方法 |
|------|----------|----------|
| 无GroupCallDialog创建日志 | CallDialogFactory判断错误 | 检查isGroupCall()返回值 |
| 创建了SingleCallDialog | SessionType不是GROUP_CHAT | 检查信令数据构建 |
| GroupCallDialog创建但无显示 | setupVideoControls()未执行 | 检查bindSpecificData()调用 |
| headTips仍然可见 | 显示控制逻辑失效 | 检查UI组件引用是否为null |
| viewRenderers不可见 | RecyclerView初始化失败 | 检查GridLayoutManager设置 |

## 实时监控命令

### 启动完整监控
```bash
# 在一个终端中运行
adb logcat -c && adb logcat -s GroupCallFlow:V CallingService:V CallDialogFactory:V GroupCallDialog:V
```

### 启动简化监控（仅关键信息）
```bash
# 只看关键流程节点
adb logcat | grep -E "(成员选择完成|构建群组信令|创建对话框类型|九宫格.*VISIBLE)"
```

### 启动错误监控
```bash
# 只看错误和异常
adb logcat | grep -E "(ERROR|Exception|错误|失败|null)"
```

## 快速诊断步骤

### 操作步骤
1. 运行上述监控脚本之一
2. 在手机上执行群组通话操作：
   - 进入群聊
   - 点击音视频通话按钮
   - 选择成员
   - 点击确定按钮
3. 观察日志输出，按照检查清单验证各个节点

### 期望的完整流程日志
```
12:34:56 [GroupCallFlow] 成员选择完成: 选中3个成员
12:34:56 [GroupCallFlow] 构建群组信令: SessionType=GROUP_CHAT
12:34:56 [CallingService] 创建对话框类型: GroupCall
12:34:56 [GroupCallDialog] 对话框创建: 统一九宫格架构初始化
12:34:56 [GroupCallFlow] 九宫格架构初始化完成: GridLayoutManager + GroupMemberAdapter
12:34:56 [GroupCallFlow] UI配置 群组通话界面: 九宫格: VISIBLE, headTips: GONE, 视频: true
```

如果看到这样的完整日志流程，说明群组通话功能正常。如果某个环节缺失，就是问题所在。