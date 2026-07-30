# 提交 1043：AWS: Implement SupportsRecoveryOperations mixin for S3FileIO (#10721)

## 提交信息

- **序号**：1043 / 4088
- **哈希**：70c506ebad2dfc6d61b99c05efd59e884282bfa6
- **短哈希**：70c506eba
- **日期**：2024-08-08（Thu Aug 8 14:35:17 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：AWS: Implement SupportsRecoveryOperations mixin for S3FileIO (#10721)
- **PR/Issue**：#10721

## 总体目的

Iceberg 表的元数据（manifest、manifest list、metadata.json）会引用底层存储上的具体数据文件。一旦存储上的文件意外丢失——例如运维误删、S3 端的对象被无意删除、或前一次 `deleteFile` 在状态不确定的情况下被调用——就会出现"manifest 指向一个磁盘上已不存在的数据文件"的损坏场景，后续读取会因 `NotFoundException` 失败。

针对这类"可达文件丢失"的损坏，本提交为 `S3FileIO` 增加了一个"尽力而为"的文件恢复能力 `recoverFile(String path)`，通过实现前置提交（#10711）引入的 `SupportsRecoveryOperations` 标记接口暴露给上层。其核心思路是借助 S3 的对象版本（object versioning）机制：若 bucket 开启了版本化，被"删除"的对象其实只是被加了一个 delete marker，历史版本仍保留在 S3 内部，可以通过版本拷贝恢复出可读的对象。

需要强调的是，这是"尽力而为"恢复——只有当 bucket 开启了版本化、且历史版本仍存在（未被生命周期策略清理）时才能成功；对未开启版本化的 bucket，恢复会返回 `false`。本能力不替代快照过期等正常数据管理流程，而是为表修复场景提供一个工具。

## 如何达成设计目的

整体设计分三层：

1. **接口契约**：复用前置提交 #10711 新增的 `SupportsRecoveryOperations` 接口（位于 `api/src/main/java/org/apache/iceberg/io/SupportsRecoveryOperations.java`），其唯一方法 `boolean recoverFile(String path)` 表达"尽力而为恢复一个文件"的语义。`S3FileIO` 声明实现该接口。

2. **生产实现**：在 `S3FileIO.recoverFile` 中，使用 AWS SDK 的 `listObjectVersionsPaginator` 列出该路径下所有版本，取 `lastModified` 最大（最新）的版本作为恢复目标——注意这里取的是 `lastModified` 而非 `isLatest`，因为 `isLatest` 在对象被删除时为 true 的是 delete marker 而非真实数据版本。拿到目标版本后，调用 `copyObject` 把该版本拷贝回原 key（而非删除 delete marker），这样恢复动作不依赖 delete 权限，权限要求更低；若 `copyObject` 抛 `SdkException` 则记日志并返回 `false`。

3. **测试**：集成测试 `TestS3FileIOIntegration` 在 bucket 初始化时开启版本化（`putBucketVersioning` 设为 `ENABLED`），新增两个用例：`testFileRecoveryHappyPath` 验证版本化 bucket 中删掉文件后能成功恢复；`testFileRecoveryFailsToRecover` 验证把 bucket 版本化挂起（`SUSPENDED`）后写入并删除的"无版本"文件无法恢复，返回 `false`。同时 `AwsIntegTestUtil.cleanS3Bucket` 改造为按版本删除（带 `versionId`），确保开启版本化后集成测试仍能彻底清理 bucket。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java`

**修改目的**：让 `S3FileIO` 实现 `SupportsRecoveryOperations`，提供基于 S3 版本化的文件恢复能力。

**工作逻辑**：

1. 类声明从 `implements CredentialSupplier, DelegateFileIO` 改为 `implements CredentialSupplier, DelegateFileIO, SupportsRecoveryOperations`，新增对应 import（`SupportsRecoveryOperations`、`ObjectVersion`、`ListObjectVersionsIterable`、`Comparator`、`Optional`、`SdkException`）。

2. 新增 `recoverFile(String path)` 方法：
   - 用 `S3URI` 解析路径得到 bucket 与 key；
   - 调 `client().listObjectVersionsPaginator(builder -> builder.bucket(...).prefix(...))` 获取所有版本的可迭代结果；
   - 在 `response.versions()` 流上用 `max(Comparator.comparing(ObjectVersion::lastModified))` 取最近修改的版本。注释中明确说明：用 `lastModified` 而非 `isLatest`，因为对被删除对象 `isLatest` 指向的是 delete marker；
   - 如果找不到版本返回 `false`，否则委托给 `recoverObject`。

3. 新增私有方法 `recoverObject(ObjectVersion version, String bucket)`：
   - 若 `version.isLatest()` 为 true（说明对象当前可见，无需恢复）直接返回 `true`；
   - 否则调用 `client().copyObject(builder -> builder.sourceBucket(bucket).sourceKey(key).sourceVersionId(version.versionId()).destinationBucket(bucket).destinationKey(key))` 把指定版本拷贝回原 key。注释说明采用 copy 而非删除 delete marker，是为了让恢复不依赖 delete 权限；
   - 捕获 `SdkException`，记 warn 日志后返回 `false`；成功返回 `true`。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：为新增的 `recoverFile` 能力添加集成测试覆盖，并在测试 bucket 上开启版本化以便恢复可工作。

**工作逻辑**：

1. 新增若干 import（`BucketVersioningStatus`、`PutBucketVersioningRequest`、`VersioningConfiguration`）。
2. 在 `@BeforeAll` 初始化（创建 bucket/access point 之后）增加 `s3.putBucketVersioning(...)` 把测试 bucket 的版本化状态设为 `ENABLED`。
3. 新增 `testFileRecoveryHappyPath`：用 `S3FileIO` 写入一个文件，删除它，断言 `exists()` 为 false；然后调 `s3FileIO.recoverFile(filePath)` 断言返回 `true`，并断言文件再次 `exists()`。
4. 新增 `testFileRecoveryFailsToRecover`：把测试 bucket 的版本化挂起（`SUSPENDED`），写入并删除一个文件，断言 `recoverFile` 返回 `false`，覆盖"无历史版本可恢复"的失败分支。

### `aws/src/integration/java/org/apache/iceberg/aws/AwsIntegTestUtil.java`

**修改目的**：让 `cleanS3Bucket` 能正确清理开启了版本化的 bucket，避免历史版本残留影响后续测试。

**工作逻辑**：原实现用 `listObjectsV2` + `deleteObjects`（仅按 key 删除）循环清理。改造为：

1. 用 `listObjectVersionsPaginator` 列出所有对象版本，import 改为 `ListObjectVersionsRequest`、`ObjectVersion`、`ListObjectVersionsIterable`；
2. 遍历 `response.versions()`，按 1000 个一批累计后调用新增的私有方法 `deleteObjectVersions` 批量删除，每批用 `ObjectIdentifier.builder().key(...).versionId(...)` 携带版本 ID 删除；
3. 末尾把剩余不足一批的也删掉。

新增私有方法 `deleteObjectVersions(S3Client, String bucket, List<ObjectVersion>)` 封装带 `versionId` 的批量删除请求。这样无论 bucket 是否开启版本化都能彻底清空。

## 小结

- **成效**：为 `S3FileIO` 增加了基于 S3 版本化的文件恢复能力，使得当表引用的数据文件被意外删除时，只要 bucket 开启了版本化且历史版本仍在，就可以通过 `recoverFile` 把丢失的对象恢复为可读状态，为修复"可达文件丢失"的损坏表提供了工具化手段。
- **影响范围**：`aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java`（生产代码新增 `recoverFile`/`recoverObject`），`aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`（集成测试），`aws/src/integration/java/org/apache/iceberg/aws/AwsIntegTestUtil.java`（测试工具支持版本化清理），共 3 个文件、128 行新增、22 行删除。
- **回迁到 1.4.x 的注意事项**：回迁本提交**必须先回迁**前置提交 #10711（即引入 `SupportsRecoveryOperations` 接口的提交 e9364faab），否则 `S3FileIO` 实现该接口会编译失败。1.4.x 若已合入 #10711 则可直接 cherry-pick 本提交。功能本身是新增的"尽力而为"恢复能力，不影响已有读写删除链路，回迁风险较低；集成测试依赖真实 S3 环境且需要在测试 bucket 上开启版本化，回迁时若 1.4.x 的集成测试基类与本提交假设的初始化流程有差异需要相应调整。是否回迁移取决于 1.4.x 是否需要该修复工具能力；若维护分支暂不引入新接口，可暂缓回迁。
