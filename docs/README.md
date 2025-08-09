open-im-android-demo/Demo on git feat/multi-party-calling [!] via gradle v7.5.1 via java v17.0.15 took 50s 
在# OpenIM Android 群组音视频项目

## 项目概述

基于 OpenIM SDK 和 LiveKit RTC 的企业级即时通讯应用，专注于群组音视频通话功能的完整实现。采用现代化的 Android 架构设计，提供微信群聊风格的多人音视频通话体验。

## 核心特性

### 🎯 群组音视频通话
- **多人通话**: 支持最多9人的群组音视频通话
- **微信风格**: 九宫格布局，支持手动切换主画面
- **实时同步**: 基于 IM 信令的状态实时同步
- **稳定可靠**: 完整的异常处理和网络重连机制

### 🏗️ 架构设计
- **信令驱动**: 所有业务状态通过 IM 信令 JSON 同步
- **业务解耦**: ViewModel 统一管理业务状态，RTC 只做流服务
- **身份统一**: 所有资料、身份、权限由 IM 统一管理

### 📱 技术栈
- **IM 系统**: OpenIM SDK
- **RTC 引擎**: LiveKit Android SDK  
- **架构模式**: MVVM + DataBinding
- **路由管理**: ARouter
- **依赖注入**: 基于接口的依赖注入

## 项目结构

```
OpenIM-Android/
├── Demo/                           # 主应用模块
│   └── app/src/main/java/io/openim/android/demo/
├── OUIKit/                         # UI 组件库
│   ├── OUICore/                    # 核心组件
│   ├── OUIConversation/            # 会话模块  
│   ├── OUICalling/                 # 通话模块
│   ├── OUIContact/                 # 联系人模块
│   ├── OUIGroup/                   # 群组模块
│   └── OUIApplet/                  # 小程序模块
└── docs/                           # 项目文档
```

## 关键组件

### 1. 通话核心组件
- **`GroupCallDialog`**: 群组通话主界面，九宫格视频布局
- **`CallStateManager`**: 通话状态统一管理，单例模式
- **`MultiStreamManager`**: 多路视频流管理和资源优化
- **`CallDialogFactory`**: 通话Dialog工厂，支持单人/群组通话

### 2. 信令处理
- **`ChatVM`**: 负责信令的构建和发送
- **`IMUtil`**: IM 工具类，处理群组信令信息构建
- **`SignalingDeduplicator`**: 信令去重机制

### 3. 路由管理
- **`Routes`**: ARouter 路由常量定义
- **`InitiateGroupActivity`**: 群组成员选择页面
- **成员选择模式**: 支持 `IS_SELECT_MEMBER` 参数

### 4. 数据模型
- **`GroupCallMember`**: 群组通话成员实体
- **`CallMemberState`**: 成员状态枚举 (CALLING/ACCEPTED/REJECTED/TIMEOUT)

## 业务流程

### 群组通话发起流程
```
1. 群聊页面点击群组通话按钮
   ↓
2. 弹窗选择音频/视频通话类型  
   ↓
3. 跳转InitiateGroupActivity选择成员（最多9人）
   ↓
4. ChatVM构建群组通话信令
   ↓ 
5. 通过OpenIM发送群组信令
   ↓
6. CallDialogFactory创建GroupCallDialog
   ↓
7. 九宫格主界面等待其他成员响应
```

### 信令格式
```json
{
  "signalID": "unique_signal_id",
  "opUserID": "operator_user_id",
  "groupID": "group_id", 
  "sessionType": 3,
  "callType": "video",
  "participants": [
    {
      "userID": "user_id",
      "status": "calling",
      "joinTime": 1699000000
    }
  ],
  "timestamp": 1699000000,
  "action": "invite"
}
```

## 开发环境

### 环境要求
- **Android Studio**: Arctic Fox 或更高版本
- **Android SDK**: API Level 21+ (Android 5.0+)
- **Java**: JDK 8 或更高版本
- **Gradle**: 7.0+

### 依赖配置
```gradle
// 核心 IM SDK
implementation 'io.openim:android-sdk:latest'

// RTC 引擎
implementation 'io.livekit:android-sdk:latest'

// 路由管理
implementation 'com.alibaba:arouter-api:latest'
annotationProcessor 'com.alibaba:arouter-compiler:latest'

// UI 相关
implementation 'androidx.databinding:databinding-runtime:latest'
implementation 'androidx.lifecycle:lifecycle-viewmodel:latest'
```

### 权限配置
```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 音视频权限 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- 其他必要权限 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/openimsdk/open-im-android-demo.git
cd open-im-android-demo
```

### 2. 配置参数
在 `Demo/app/src/main/java/io/openim/android/demo/` 中配置：
- OpenIM 服务器地址
- LiveKit 服务器配置
- 应用 ID 和密钥

### 3. 编译运行
```bash
./gradlew assembleDebug
```

### 4. 测试群组通话
1. 创建群聊
2. 点击群组通话按钮
3. 选择通话成员
4. 发起音视频通话

## 日志监控

项目集成了完善的日志系统，便于开发调试和线上问题排查：

```java
// 业务流程日志
L.businessFlow("群组成员选择", "启动InitiateGroupActivity");
L.businessFlow("群组信令构建", "参与者数量: " + participants.size());

// 关键操作日志  
L.critical("群组通话发起", "发送群组通话信令成功");
L.critical("成员响应处理", "收到成员接听信令");

// 异常处理日志
LogExceptionHandler.handle("群组通话异常", exception);
```

## 故障排查

### 常见问题

1. **选择成员后变成1v1通话**
   - 检查 `InitiateGroupActivity` 的 `IS_SELECT_MEMBER` 参数
   - 确认信令中 `sessionType` 为 3 (群组类型)
   - 查看 `CallStateManager.isGroupCall()` 判断逻辑

2. **视频无法显示**
   - 检查摄像头权限
   - 确认 LiveKit Room 连接状态
   - 查看 `MultiStreamManager` 视频流管理

3. **信令发送失败**
   - 确认 OpenIM 连接状态
   - 检查网络连接
   - 查看信令格式是否正确

### 调试技巧
- 启用详细日志输出
- 使用 ADB 查看实时日志
- 监控网络请求和响应
- 检查 LiveKit Dashboard

## 性能优化

### 1. 视频渲染优化
```java
// SurfaceView 复用池
VideoResourcePool.getInstance().reuseVideoView(surfaceView);

// 按需订阅视频流
if (member.isVideoEnabled()) {
    room.subscribeVideoTrack(member.getUserID());
}
```

### 2. 内存管理
```java
// 及时释放资源
@Override
protected void onDestroy() {
    super.onDestroy();
    MultiStreamManager.getInstance().cleanup();
    CallStateManager.getInstance().reset();
}
```

### 3. 网络优化
- 自适应码率调节
- 信令重传机制  
- 弱网环境降级策略

## 代码规范

### 命名规范
- **类名**: PascalCase (如 `GroupCallDialog`)
- **方法名**: camelCase (如 `buildGroupSignaling`)
- **常量**: UPPER_SNAKE_CASE (如 `IS_SELECT_MEMBER`)

### 注释规范
```java
/**
 * 群组通话对话框
 * 负责显示九宫格视频布局和通话控制
 * 
 * @author OpenIM Team
 * @since v3.0.0
 */
public class GroupCallDialog extends BaseDialog {
    // 实现细节...
}
```

## 贡献指南

1. Fork 项目到个人账户
2. 创建功能分支 (`git checkout -b feature/your-feature`)
3. 提交代码 (`git commit -am 'Add some feature'`)
4. 推送分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源协议。

## 联系我们

- **官网**: https://www.openim.io
- **文档**: https://docs.openim.io  
- **社区**: https://github.com/openimsdk/open-im-android-demo
- **邮箱**: contact@openim.io

---

## 更新日志

### v3.5.0 (2024-01-15)
- ✨ 新增群组音视频通话功能
- 🐛 修复 Java 兼容性问题 (List.of → Arrays.asList)
- 📝 完善日志监控体系
- ⚡ 优化视频资源管理

### v3.4.0 (2023-12-01)  
- 🚀 升级 LiveKit SDK
- 💯 完善异常处理机制
- 🎨 优化 UI 交互体验

---

**Built with ❤️ by OpenIM Team**