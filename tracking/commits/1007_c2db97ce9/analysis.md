# 提交 1007：Build: Bump com.adobe.testing:s3mock-junit5 from 2.11.0 to 2.17.0 (#10851)

## 提交信息

- **序号**：1007 / 4088
- **哈希**：c2db97ce9a31d0af21341deaa22f7b91da04cd32
- **短哈希**：c2db97ce9
- **日期**：2024-08-02 14:02:56 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Bump com.adobe.testing:s3mock-junit5 from 2.11.0 to 2.17.0 (#10851)
- **PR/Issue**：#10851

## 总体目的

本提交由 dependabot 风格的依赖升级驱动，将 Iceberg 测试中用于模拟 S3 服务的 `com.adobe.testing:s3mock-junit5` 从 2.11.0 升级到 2.17.0。s3mock 是 AWS 模块测试（`TestS3FileIO`、`TestS3InputStream`、`TestS3OutputStream` 等）使用的本地 S3 兼容服务，定期升级以获取 bug 修复、新特性与安全补丁。

升级到 2.17.0 后，s3mock 在创建已存在的 bucket 时返回的异常行为发生变化：除了原本的 `BucketAlreadyExistsException`，现在还可能抛出 `BucketAlreadyOwnedByYouException`（这与真实 AWS S3 的行为更一致——当 bucket 已被当前账号拥有时返回此异常）。如果不调整测试代码中的 catch 子句，原本幂等的 `createBucket` 帮助方法在新版 s3mock 下会失败，导致测试无法通过。因此本提交在升级版本号的同时，配套修改了三个测试文件中 `createBucket` 方法的异常捕获，把 `BucketAlreadyOwnedByYouException` 也一并吞掉。

## 如何达成设计目的

实现方式分两步：

1. 在 `gradle/libs.versions.toml` 中把 `s3mock-junit5` 的版本由 `2.11.0` 改为 `2.17.0`。
2. 在 `aws/src/test/java/org/apache/iceberg/aws/s3/` 下的 `TestS3FileIO.java`、`TestS3InputStream.java`、`TestS3OutputStream.java` 三个测试类的 `createBucket` 方法中，import `BucketAlreadyOwnedByYouException`，并把 catch 子句由 `catch (BucketAlreadyExistsException e)` 改为 `catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e)`，使两种"bucket 已存在"的异常都被忽略，保持 `createBucket` 的幂等语义。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 s3mock-junit5 版本号。

**工作逻辑**：`s3mock-junit5 = "2.11.0"` 改为 `s3mock-junit5 = "2.17.0"`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`

**修改目的**：适配新版 s3mock 的 bucket 已存在异常行为。

**工作逻辑**：新增 `import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;`；`createBucket` 方法的 catch 由 `BucketAlreadyExistsException` 改为 `BucketAlreadyExistsException | BucketAlreadyOwnedByYouException`。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java`

**修改目的**：同上，适配新版 s3mock。

**工作逻辑**：同上，新增 import 并扩展 catch 子句。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3OutputStream.java`

**修改目的**：同上，适配新版 s3mock。

**工作逻辑**：同上，新增 import 并扩展 catch 子句。

## 小结

- **成效**：将 s3mock-junit5 测试依赖从 2.11.0 升级到 2.17.0，并同步适配三个 S3 测试类的 `createBucket` 异常捕获，使测试在新版 s3mock 下保持幂等与通过。
- **影响范围**：仅测试相关，4 个文件、+5/-2 行，无生产代码变更。
- **回迁到 1.4.x 的注意事项**：可以回迁，风险低。这是测试依赖升级 + 配套适配，不影响生产产物。回迁时需确认 1.4.x 的 `gradle/libs.versions.toml` 中 s3mock 版本与三个测试文件结构一致。如果 1.4.x 上有其它依赖 s3mock 行为的测试，也需一并检查。整体属于低风险回迁，主要价值是让 1.4.x 测试用上更新的 s3mock 修复。
