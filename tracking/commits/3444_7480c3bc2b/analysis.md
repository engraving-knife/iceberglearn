# 提交 3444：AWS: Schedule next credential refresh (#15732)

## 提交信息

- **序号**：3444 / 4088
- **哈希**：7480c3bc2be4c24750fbf39320f845e578a4f03b
- **短哈希**：7480c3bc2b
- **日期**：2026-03-23 16:32:28 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS: Schedule next credential refresh (#15732)
- **PR/Issue**：#15732

## 总体目的

修复 S3FileIO 中存储凭证刷新的一个关键缺陷。在凭证刷新成功后，没有调度下一次刷新，导致刷新后的凭证可能过期而无法继续访问 S3。此提交在每次成功刷新凭证后重新调度下一次刷新，确保凭证持续保持有效。

## 如何达成设计目的

- 在 S3FileIO 的凭证刷新成功回调中，添加 `scheduleCredentialRefresh()` 调用
- 这样每次刷新完成后会自动调度下一次刷新，形成持续的刷新循环
- 添加测试验证多次刷新链式调用的正确性

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (+1/-0 lines)

**修改目的**：在凭证刷新成功后调度下一次刷新。

**工作逻辑**：
- 在凭证刷新逻辑中，当 `refreshed` 列表不为空且资源未关闭时，更新 `storageCredentials`
- 紧接着添加 `scheduleCredentialRefresh()` 调用
- 这确保每次刷新成功后，系统会自动调度下一次刷新，避免凭证过期

关键代码：
```java
if (!refreshed.isEmpty() && !isResourceClosed.get()) {
    this.storageCredentials = Lists.newArrayList(refreshed);
    scheduleCredentialRefresh();  // 新增：调度下一次刷新
}
```

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOCredentialRefresh.java` (+109/-0 lines)

**修改目的**：添加测试验证凭证刷新会链式调度下一次刷新。

**工作逻辑**：
- 测试方法 `credentialRefreshSchedulesNextRefresh()` 验证以下场景：
  1. 初始凭证即将过期（3分钟后过期）
  2. 第一次刷新返回的凭证也即将过期（2分钟后过期）
  3. 第二次刷新返回的凭证有效期较长（1小时后过期）
- 使用 mockServer 设置两次不同的刷新响应
  - 第一次请求返回 `firstRefreshResponse`（即将过期凭证）
  - 后续请求返回 `secondRefreshResponse`（长效凭证）
- 验证 mockServer 被调用至少 2 次（证明第二次刷新被自动调度）
- 验证最终凭证是第二次刷新返回的凭证（`secondRefreshedAccessKey` 等）
- 使用 Awaitility 等待异步刷新完成

## 总结

该提交修复了 S3FileIO 凭证刷新链断裂的 bug。之前在凭证刷新成功后没有调度下一次刷新，导致刷新后的凭证可能在过期后无法继续使用。通过在每次刷新成功后调用 `scheduleCredentialRefresh()`，确保证书刷新形成持续的循环，保持凭证长期有效。测试验证了多次刷新的链式调度行为。
