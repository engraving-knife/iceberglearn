# 提交 3898：Kafka Connect: Make CommitState.isCommitReady() O(1) (#16453)

## 提交信息

- **序号**：3898 / 4088
- **哈希**：a165b1c1551dcdb215fb10a610f7d29edfa25df3
- **短哈希**：a165b1c15
- **日期**：2026-06-17 19:49:30 +0200
- **作者**：Henry Haiying Cai
- **提交说明**：Kafka Connect: Make CommitState.isCommitReady() O(1) (#16453)
- **PR/Issue**：#16453, Closes #16361

## 总体目的

修复 `CommitState.isCommitReady()` 方法的 O(N^2) 性能问题。此前，每当收到一个 `DATA_COMPLETE` 信封时，`isCommitReady()` 都会重新扫描整个 `readyBuffer` 来统计已接收的分区数。在控制主题（control topic）积压的情况下，这会导致每次提交的工作量随缓冲消息数呈平方增长，加剧积压并使恢复更加困难。

该性能问题在 Issue #16361 中被报告：当控制主题积压大量消息时，Coordinator 处理每个 DATA_COMPLETE 都触发全量扫描，形成恶性循环。

## 如何达成设计目的

维护一个运行中的 `receivedPartitionCount` 计数器，在 `addReady()` 时递增、在 `endCurrentCommit()` 时重置，使 `isCommitReady()` 变为与 `expectedPartitionCount` 的常量时间比较。同时验证了提交失败后重新开始提交、以及僵尸 Coordinator（zombie coordinator）场景下的正确性。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitState.java` (+5/-6 lines)

**修改目的**：将 isCommitReady() 从 O(N) 优化为 O(1)。

**工作逻辑**：

1. 新增运行计数器字段：
```java
private int receivedPartitionCount = 0;
```

2. 在 `addReady()` 中递增（仅当 commitId 匹配当前提交时）：
```java
} else if (Objects.equals(currentCommitId, dataComplete.commitId())) {
  receivedPartitionCount += dataComplete.assignments().size();
}
```
注意 commitId 匹配检查确保僵尸 Coordinator 的过期消息不会被计入。

3. 在 `endCurrentCommit()` 中重置：
```java
void endCurrentCommit() {
  readyBuffer.clear();
  receivedPartitionCount = 0;
  currentCommitId = null;
}
```

4. 简化 `isCommitReady()` - 移除全量扫描：
```java
// 旧代码（O(N) 扫描）：
int receivedPartitionCount =
    readyBuffer.stream()
        .filter(payload -> payload.commitId().equals(currentCommitId))
        .mapToInt(payload -> payload.assignments().size())
        .sum();

// 新代码：直接使用维护的计数器（O(1)）
if (receivedPartitionCount >= expectedPartitionCount) {
```

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCommitState.java` (+51/-0 lines)

**修改目的**：验证优化后的行为正确性，特别是边界场景。

**工作逻辑**：

1. `testIsCommitReadyResetsBetweenCommits`：验证计数器在提交间正确重置
   - 第一次提交添加 2 个分区 -> isCommitReady(2) 为 true
   - endCurrentCommit + startNewCommit 后 -> isCommitReady(1) 为 false（计数器已重置）
   - 添加 1 个分区 -> isCommitReady(1) 为 true, isCommitReady(2) 为 false

2. `testIsCommitReadyIgnoresZombieCoordinatorPayloads`：验证僵尸 Coordinator 消息被忽略
   - 添加一个不同 commitId 的僵尸 payload（2 个分区）
   - 添加当前 commitId 的 payload（1 个分区）
   - isCommitReady(1) 为 true（只计当前提交的分区）
   - isCommitReady(2) 为 false（僵尸的 2 个分区被忽略）

## 总结

将 `CommitState.isCommitReady()` 从 O(N) 全量扫描优化为 O(1) 常量时间比较，通过维护运行计数器避免了平方级性能问题。优化特别注意了边界场景的正确性：提交间重置和僵尸 Coordinator 消息过滤。该修复能显著缓解控制主题积压时的性能恶化，使恢复更加迅速。

值得注意的是，该提交的 Co-author 为 Claude（Slack 的 AI 开发助手），体现了 AI 辅助开发在性能优化场景中的应用。
