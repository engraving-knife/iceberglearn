# 提交 3421：AWS: Add scheduled refresh for the S3FileIO held storage credentials (#15678)

## 提交信息

- **序号**：3421 / 4088
- **哈希**：1009ef41b25b4602baa949c15c31db34b7a2ad96
- **短哈希**：1009ef41b2
- **日期**：2026-03-19 16:50:42 -0700
- **作者**：Daniel Weeks
- **提交说明**：AWS: Add scheduled refresh for the S3FileIO held storage credentials (#15678)
- **PR/Issue**：#15678

## 总体目的

为 S3FileIO 持有的存储凭证添加定时刷新机制。当 S3FileIO 通过 REST 服务器获取了 vended credentials（临时存储凭证）时，这些凭证有过期时间。此前凭证不会自动刷新，长时间运行的作业可能在凭证过期后失败。本提交通过 ScheduledExecutorService 在凭证过期前自动刷新，确保长时间运行的作业能持续访问 S3。

## 如何达成设计目的

1. 将 ExecutorService 升级为 ScheduledExecutorService 以支持定时任务
2. 在初始化客户端后调用 `scheduleCredentialRefresh()` 安排凭证刷新
3. `scheduleCredentialRefresh()` 从 storageCredentials 中找到最早过期的凭证，在过期前5分钟安排刷新
4. `refreshStorageCredentials()` 通过 VendedCredentialsProvider 重新获取凭证并更新 storageCredentials
5. 在 `setCredentials` 和 `close` 方法中取消已安排的刷新任务
6. `setCredentials` 时关闭现有客户端以触发重建

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (+81/-7 lines)

**修改目的**：实现凭证定时刷新机制。

**工作逻辑**：

**scheduleCredentialRefresh 方法**：
- 从所有 storageCredentials 中找到最早的 `SESSION_TOKEN_EXPIRES_AT_MS` 值
- 计算在过期前 5 分钟进行刷新
- 使用 `executorService().schedule()` 安排 `refreshStorageCredentials` 任务

**refreshStorageCredentials 方法**：
- 检查 `isResourceClosed` 避免在关闭后刷新
- 通过 `VendedCredentialsProvider.create(properties)` 获取新凭证
- 过滤前缀为 "s3" 的凭证并更新 `storageCredentials`
- 异常时记录警告日志

**executorService 升级**：
- 从 `ExecutorService` 改为 `ScheduledExecutorService`
- 使用 `ThreadPools.newExitingScheduledPool` 替代 `newExitingWorkerPool`

**setCredentials 方法更新**：
- 取消已安排的刷新任务
- 关闭并置空现有 `clientByPrefix`，触发客户端重建

**close 方法更新**：
- 取消 `refreshFuture`

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (+2/-1 lines)

**修改目的**：小幅调整以支持刷新场景。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java` (+56 lines)

**修改目的**：测试凭证刷新相关功能。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIOCredentialRefresh.java` (+159 lines, 新文件)

**修改目的**：专门的凭证刷新测试。

**工作逻辑**：
- 测试凭证刷新在过期前被正确安排
- 测试刷新后凭证被更新
- 测试关闭后刷新不会执行

## 总结

本提交为 S3FileIO 的 vended credentials 添加了定时刷新机制，通过 ScheduledExecutorService 在凭证过期前 5 分钟自动刷新。这解决了长时间运行作业中临时凭证过期导致 S3 访问失败的问题。刷新通过 VendedCredentialsProvider 重新获取凭证，并在刷新时关闭旧客户端以触发重建。
