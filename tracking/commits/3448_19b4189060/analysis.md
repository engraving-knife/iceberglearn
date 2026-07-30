# 提交 3448：GCP: Add scheduled refresh for storage credentials held by GCSFileIO (#15696)

## 提交信息

- **序号**：3448 / 4088
- **哈希**：19b418906043dbfc2aba7d8947404f778414b71a
- **短哈希**：19b4189060
- **日期**：2026-03-23 11:41:12 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：GCP: Add scheduled refresh for storage credentials held by GCSFileIO (#15696)
- **PR/Issue**：#15696

## 总体目的

为 GCSFileIO 添加存储凭证的定时刷新机制。之前 GCSFileIO 持有的存储凭证在过期后无法自动刷新，导致长时间运行的任务可能因凭证过期而失败。此提交实现了与 S3FileIO 类似的凭证调度刷新机制，确保 GCS 凭证在过期前自动更新。

## 如何达成设计目的

- 在 GCSFileIO 中添加调度凭证刷新逻辑
- 使用 ScheduledExecutorService 在凭证过期前 5 分钟调度刷新
- 刷新成功后递归调度下一次刷新
- 在 close() 时取消刷新任务
- 重构 OAuth2RefreshCredentialsHandler，提取 fetchCredentials() 公共方法

## 修改详情

### `build.gradle` (+1/-0 lines)

**修改目的**：为 GCP 模块添加 awaitility 测试依赖。

**工作逻辑**：
- 在 `:iceberg-gcp` 项目的依赖中添加 `testImplementation libs.awaitility`
- 用于异步凭证刷新测试中的等待断言

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (+86/-9 lines)

**修改目的**：添加凭证调度刷新机制。

**工作逻辑**：

1. **新增字段和初始化**：
   - `volatile ScheduledExecutorService executorService`：静态共享调度线程池
   - `volatile List<StorageCredential> storageCredentials`：改为 volatile 以支持并发刷新
   - `transient volatile ScheduledFuture<?> refreshFuture`：持有的刷新任务句柄

2. **scheduleCredentialRefresh() 方法**：
   - 从所有存储凭证中找到最早过期的 token 过期时间
   - 计算预取时间（过期前 5 分钟）
   - 使用 ScheduledExecutorService 调度 `refreshStorageCredentials` 任务

3. **refreshStorageCredentials() 方法**：
   - 检查资源是否已关闭
   - 使用 OAuth2RefreshCredentialsHandler 获取新凭证
   - 过滤出 GCS 前缀的凭证
   - 更新 storageCredentials 并递归调度下一次刷新

4. **executorService() 方法**：
   - 双重检查锁初始化共享调度线程池
   - 使用 `ThreadPools.newExitingScheduledPool` 创建线程池

5. **close() 方法增强**：
   - 取消 refreshFuture

6. **setCredentials() 方法增强**：
   - 取消已有的刷新任务
   - 关闭已初始化的客户端，允许后续重建

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (+13/-8 lines)

**修改目的**：提取公共方法供 GCSFileIO 调用。

**工作逻辑**：
- 将 `refreshAccessToken()` 中的 HTTP 请求逻辑提取为新的 `fetchCredentials()` 方法
- `refreshAccessToken()` 改为调用 `fetchCredentials()`
- `fetchCredentials()` 返回 `LoadCredentialsResponse`，可供 GCSFileIO 的刷新逻辑直接使用

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGCSFileIO.java` (+39/-0 lines)

**修改目的**：添加 GCSFileIO 凭证刷新相关测试支持。

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/TestGCSFileIOCredentialRefresh.java` (+229/-0 lines)

**修改目的**：新增凭证刷新测试文件。

**工作逻辑**：
- 测试凭证调度刷新的完整流程
- 使用 mockserver 模拟凭证服务端
- 验证凭证在过期前被自动刷新
- 使用 Awaitility 等待异步刷新完成

## 总结

该提交为 GCSFileIO 添加了与 S3FileIO 对等的凭证定时刷新机制。通过 ScheduledExecutorService 在凭证过期前 5 分钟自动调度刷新，刷新成功后递归调度下一次刷新，确保长时间运行的任务不会因凭证过期而失败。同时重构了 OAuth2RefreshCredentialsHandler 以支持凭证获取逻辑的复用。
