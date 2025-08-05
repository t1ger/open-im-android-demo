# 群组音视频功能实现计划

## 1. 总体实施策略

### 1.1 开发方法
- **测试驱动开发(TDD)**: 先写测试，再写功能代码
- **渐进式实现**: MVP优先，分阶段迭代
- **业界最佳实践**: 融入状态管理、资源管理、信令去重优化

### 1.2 版本规划
```
MVP v1.0 (基础功能) -> v1.1 (稳定性优化) -> v1.2 (体验优化) -> v1.3 (高级功能)
```

### 1.3 实施时间线
- **总计**: 6-8周
- **MVP v1.0**: 3-4周  
- **v1.1**: 1.5周
- **v1.2**: 1.5周
- **v1.3**: 按需开发

## 2. 阶段一：MVP v1.0 基础功能 (3-4周)

### 2.1 第1周：基础架构和信令系统

#### 2.1.1 任务清单
- [ ] **创建功能分支** `feat/multi-party-calling`
- [ ] **扩展信令协议** (1天)
  - [ ] Constants.java 新增 MsgType 210-219
  - [ ] 创建 MultiPartySignaling.java 数据结构
  - [ ] 编写信令处理单元测试
- [ ] **状态管理优化** (1天)
  - [ ] 创建 CallMemberState.java 枚举
  - [ ] 创建 GroupCallMember.java 实体类
  - [ ] 实现状态转换验证逻辑
  - [ ] 编写状态管理测试用例
- [ ] **业界最佳实践组件** (2天)
  - [ ] 实现 SignalingDeduplicator.java 
  - [ ] 实现 VideoResourcePool.java
  - [ ] 编写资源管理和去重测试
- [ ] **CallingVM 基础扩展** (1天)
  - [ ] 添加群组通话相关字段和方法
  - [ ] 集成最佳实践组件
  - [ ] 编写 CallingVM 扩展测试

#### 2.1.2 关键代码实现

**Constants.java 信令扩展**
```java
// 在现有 MsgType 枚举中添加
MULTI_PARTY_INVITE(210, "multiPartyInvite"),
MULTI_PARTY_ACCEPT(211, "multiPartyAccept"), 
MULTI_PARTY_REJECT(212, "multiPartyReject"),
MULTI_PARTY_CANCEL(213, "multiPartyCancel"),
MULTI_PARTY_HANGUP(214, "multiPartyHangup"),
MULTI_PARTY_MEMBER_JOIN(215, "multiPartyMemberJoin"),
MULTI_PARTY_MEMBER_LEAVE(216, "multiPartyMemberLeave"),
MULTI_PARTY_MEMBER_STATE_CHANGE(217, "multiPartyMemberStateChange"),
MULTI_PARTY_SPEAKING_STATE(218, "multiPartySpeakingState"),
MULTI_PARTY_QUALITY_REPORT(219, "multiPartyQualityReport");
```

**测试用例示例**
```java
@Test
public void testMultiPartySignalingCreation() {
    MultiPartySignaling signaling = new MultiPartySignaling();
    signaling.setType(Constants.MsgType.MULTI_PARTY_INVITE.name());
    signaling.setRoomID("room_123");
    signaling.setInviterID("user_001");
    
    assertNotNull(signaling.getMessageId());
    assertEquals("room_123", signaling.getRoomID());
}

@Test 
public void testCallMemberStateTransitions() {
    GroupCallMember member = new GroupCallMember("user_001");
    
    // 正常状态流转
    assertTrue(member.setState(CallMemberState.INVITING));
    assertTrue(member.setState(CallMemberState.RINGING));
    assertTrue(member.setState(CallMemberState.CONNECTING));
    assertTrue(member.setState(CallMemberState.CONNECTED));
    
    // 非法状态转换
    assertFalse(member.setState(CallMemberState.IDLE));
    assertFalse(member.setState(CallMemberState.INVITING));
}
```

### 2.2 第2周：LiveKit集成和UI基础

#### 2.2.1 任务清单
- [ ] **CallViewModel.kt 扩展** (2天)
  - [ ] 实现群组房间连接逻辑
  - [ ] 处理多参与者事件
  - [ ] 视频轨道管理优化
  - [ ] 编写LiveKit集成测试
- [ ] **CallDialog.java 基础扩展** (2天)
  - [ ] 群组/单人模式切换逻辑
  - [ ] 初始化群组UI组件
  - [ ] 成员网格布局管理
  - [ ] 编写UI组件测试
- [ ] **成员渲染适配器** (1天)
  - [ ] 创建 GroupMemberAdapter.java
  - [ ] 视频渲染器绑定逻辑
  - [ ] 成员状态UI更新

#### 2.2.2 关键实现

**CallViewModel.kt 群组房间管理**
```kotlin
class CallViewModel {
    fun connectToGroupRoom(
        roomUrl: String, 
        token: String, 
        memberIds: List<String>,
        callback: (Result<Boolean>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                room.connect(roomUrl, token)
                
                // 监听参与者事件
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.ParticipantConnected -> {
                            callingVM.updateMemberState(
                                event.participant.identity, 
                                CallMemberState.CONNECTED
                            )
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            callingVM.updateMemberState(
                                event.participant.identity, 
                                CallMemberState.DISCONNECTED
                            )
                        }
                    }
                }
                
                callback(Result.success(true))
            } catch (e: Exception) {
                L.e("群组房间连接失败", e)
                callback(Result.failure(e))
            }
        }
    }
}
```

### 2.3 第3周：完整通话流程

#### 2.3.1 任务清单
- [ ] **群组通话发起流程** (2天)
  - [ ] 集成GroupMemberVM选择成员
  - [ ] 创建LiveKit房间
  - [ ] 发送群组邀请信令
  - [ ] 显示群组通话界面
- [ ] **信令处理完善** (2天)
  - [ ] 处理各类群组信令
  - [ ] 成员状态同步
  - [ ] 异常情况处理
- [ ] **基础UI交互** (1天)
  - [ ] 麦克风/摄像头控制
  - [ ] 成员状态指示
  - [ ] 挂断和离开逻辑

#### 2.3.2 核心流程实现

**群组通话发起**
```java
public void initiateGroupCall(String groupId, List<String> memberIds, boolean isVideo) {
    try {
        // 1. 设置群组通话模式
        this.isGroupCall = true;
        this.isVideoCall = isVideo;
        
        // 2. 初始化成员列表
        groupMembers.clear();
        for (String memberId : memberIds) {
            GroupCallMember member = new GroupCallMember(memberId);
            member.setState(CallMemberState.INVITING);
            groupMembers.add(member);
        }
        
        // 3. 创建LiveKit房间
        String roomId = "group_call_" + System.currentTimeMillis();
        callViewModel.createGroupRoom(roomId, memberIds, result -> {
            if (result instanceof Result.Success) {
                // 4. 发送群组邀请信令
                sendGroupInviteSignaling(groupId, memberIds, roomId, isVideo);
                
                // 5. 显示群组通话界面
                showGroupCallDialog();
            } else {
                handleGroupCallError("房间创建失败", result.getException());
            }
        });
        
    } catch (Exception e) {
        L.e("发起群组通话失败", e);
        handleGroupCallError("发起群组通话失败", e);
    }
}
```

### 2.4 第4周：测试和优化

#### 2.4.1 任务清单
- [ ] **集成测试** (2天)
  - [ ] 多用户通话场景测试
  - [ ] 网络异常处理测试
  - [ ] 性能压力测试
- [ ] **UI优化和调试** (2天)
  - [ ] 九宫格布局优化
  - [ ] 视频渲染性能调优
  - [ ] 内存泄漏检查
- [ ] **功能验证** (1天)
  - [ ] 完整通话流程验证
  - [ ] 边界情况测试
  - [ ] 用户体验优化

#### 2.4.2 性能优化要点

**内存管理优化**
```java
public class CallDialog {
    @Override
    protected void onDestroy() {
        // 清理视频渲染器资源
        if (callingVM.isGroupCall && callingVM.resourcePool != null) {
            callingVM.resourcePool.cleanup();
        }
        
        // 清理成员列表
        if (memberAdapter != null) {
            memberAdapter.clear();
        }
        
        super.onDestroy();
    }
}
```

## 3. 阶段二：v1.1 稳定性优化 (1.5周)

### 3.1 第5周：错误恢复和网络优化

#### 3.1.1 任务清单
- [ ] **错误恢复机制** (2天)
  - [ ] 实现CallErrorRecovery断路器模式
  - [ ] 指数退避重试策略
  - [ ] 网络异常自动恢复
- [ ] **网络质量监控** (2天)
  - [ ] 实现CallQualityMonitor
  - [ ] 实时质量指标收集
  - [ ] 自动降级策略
- [ ] **稳定性测试** (1天)
  - [ ] 长时间通话测试
  - [ ] 弱网环境测试
  - [ ] 异常恢复测试

#### 3.1.2 错误恢复实现

**断路器模式**
```java
public class CallErrorRecovery {
    public class CallCircuitBreaker {
        private State state = State.CLOSED;
        private int failureCount = 0;
        private long lastFailureTime = 0;
        
        public boolean allowRequest() {
            if (state == State.CLOSED) return true;
            if (state == State.OPEN && isTimeoutExpired()) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        
        public void recordFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();
            if (failureCount >= 5) {
                state = State.OPEN;
                L.w("断路器开启，暂停请求");
            }
        }
    }
}
```

### 3.2 第6周前半：质量监控和自动优化

#### 3.2.1 任务清单
- [ ] **网络质量指示器** (2天)
  - [ ] UI质量星级显示
  - [ ] 实时网络状态更新
  - [ ] 质量变化提醒
- [ ] **智能降级策略** (1天)
  - [ ] 自动关闭视频保音频
  - [ ] 码率自适应调整
  - [ ] 用户确认机制

## 4. 阶段三：v1.2 体验优化 (1.5周)

### 4.1 第6周后半：用户体验增强

#### 4.1.1 任务清单
- [ ] **音频可视化** (2天)
  - [ ] 发言状态指示环
  - [ ] 音量波形显示
  - [ ] 当前发言人高亮
- [ ] **交互优化** (1天)
  - [ ] 成员列表管理
  - [ ] 快速静音操作
  - [ ] 通话状态提示

### 4.2 第7周：高级UI功能

#### 4.2.1 任务清单
- [ ] **布局切换功能** (2天)
  - [ ] 演讲者模式
  - [ ] 网格模式切换
  - [ ] 全屏显示
- [ ] **成员管理** (1天)
  - [ ] 踢出成员功能
  - [ ] 邀请新成员
  - [ ] 管理员权限

## 5. 阶段四：v1.3 高级功能 (按需开发)

### 5.1 高级功能规划
- [ ] **屏幕共享** (1周)
- [ ] **举手发言** (3天)
- [ ] **会议录制** (1周)
- [ ] **美颜滤镜** (1周)

## 6. 质量保证和测试计划

### 6.1 测试策略
```
单元测试: 每个功能点完成后立即编写测试
集成测试: 每周末进行集成测试
端到端测试: 版本发布前完整流程测试
性能测试: 每个版本发布前进行性能基准测试
```

### 6.2 测试覆盖目标
- **单元测试覆盖率**: ≥80%
- **关键路径覆盖**: 100%
- **异常场景覆盖**: ≥90%

### 6.3 持续集成
```yaml
# 每次代码提交自动触发
- 单元测试执行
- 代码质量检查 (SonarQube)
- 构建打包测试
- 自动化UI测试 (关键流程)
```

## 7. 风险管理和应对策略

### 7.1 技术风险
| 风险类型 | 风险等级 | 应对策略 |
|---------|---------|----------|
| LiveKit多人房间稳定性 | 中等 | 充分压力测试，备用降级方案 |
| 内存占用过高 | 中等 | 资源池优化，及时释放资源 |
| UI性能影响 | 低 | 渲染优化，异步处理 |

### 7.2 进度风险
- **评估偏差**: 每项任务预留20%缓冲时间
- **依赖阻塞**: 并行开发，降低相互依赖
- **人员风险**: 关键代码多人备份，知识共享

## 8. 开发环境和工具

### 8.1 开发分支策略
```
main (稳定版本)
  ├── develop (开发主分支)
      ├── feat/multi-party-calling (功能开发分支)
      ├── feat/ui-optimization (UI优化分支)  
      └── fix/performance-issues (性能修复分支)
```

### 8.2 代码审查流程
1. **自测**: 开发者本地完整测试
2. **单元测试**: 确保测试覆盖率达标
3. **代码审查**: 至少一人Review代码
4. **集成测试**: CI/CD自动化测试
5. **合并主分支**: 通过所有检查后合并

## 9. 验收标准

### 9.1 功能验收
- [ ] 支持2-9人群组音视频通话
- [ ] 九宫格布局正确显示
- [ ] 成员状态实时同步
- [ ] 网络异常自动恢复
- [ ] 资源正确释放，无内存泄漏

### 9.2 性能验收
- [ ] 9人视频通话内存占用 < 200MB
- [ ] UI操作响应时间 < 100ms
- [ ] 视频帧率稳定 ≥ 20fps
- [ ] 音频延迟 < 200ms

### 9.3 稳定性验收
- [ ] 连续1小时通话无崩溃
- [ ] 网络切换自动重连成功率 > 95%
- [ ] 弱网环境下音视频可用性 > 90%

## 10. 项目里程碑

### 10.1 关键时间节点
- **Week 1 End**: 基础架构和信令系统完成
- **Week 2 End**: LiveKit集成和UI基础完成
- **Week 3 End**: 完整通话流程打通
- **Week 4 End**: MVP v1.0 功能完整，测试通过
- **Week 5.5 End**: v1.1 稳定性优化完成
- **Week 7 End**: v1.2 体验优化完成

### 10.2 交付物清单
- [ ] 源代码 (包含完整注释)
- [ ] 单元测试用例 (覆盖率≥80%)
- [ ] 集成测试报告
- [ ] 性能测试报告
- [ ] API文档
- [ ] 用户使用说明

---

**本实现计划基于TDD方法论，优先保证代码质量和系统稳定性。通过分阶段迭代开发，确保每个版本都是可交付的高质量产品。**