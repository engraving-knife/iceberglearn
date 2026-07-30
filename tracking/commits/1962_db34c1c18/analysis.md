# 提交 1962：AWS: Add AWS integ tests to check task and enable tests based on required environment variables (#12671)

## 提交信息

- **序号**：1962 / 4088
- **哈希**：db34c1c18b20b389c64a449f21c2296f2f25402a
- **短哈希**：db34c1c18
- **日期**：2025-04-04 09:12:15 +0200
- **作者**：Leon Lin
- **提交说明**：AWS: Add AWS integ tests to check task and enable tests based on required environment variables (#12671)
- **PR/Issue**：#12671

## 总体目的

Iceberg 的 AWS 集成测试（`aws/src/integration/...`）依赖一组 AWS 环境变量（如 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`、`AWS_SESSION_TOKEN`、`AWS_REGION`、`AWS_TEST_BUCKET` 等）才能真正运行。此前这些测试类没有基于环境变量做启用/禁用判断，导致在缺少必要凭证的环境下（例如 CI 未配置 AWS 凭证时）运行集成测试会失败或报错，而非被跳过。

本提交的目的：
1. 在 `AwsIntegTestUtil` 中将所有依赖的环境变量名提取为 public 常量，便于在 `@EnabledIfEnvironmentVariable` 中引用。
2. 为各个 AWS 集成测试类添加 JUnit 5 的 `@EnabledIfEnvironmentVariables` / `@EnabledIfEnvironmentVariable` 注解，仅当所需环境变量都存在时才启用对应测试，否则自动跳过。
3. 在 `build.gradle` 中让 `:iceberg-aws` 的 `check` 任务依赖 `integrationTest`，使集成测试纳入常规 check 流程（在满足环境变量时运行）。
4. 同时对部分测试文件做了纯文件移动（rename，0 行变更），如把若干 `s3/` 下测试文件位置调整。

## 如何达成设计目的

通过 JUnit 5 的条件执行注解 `@EnabledIfEnvironmentVariable(named = ..., matches = ...)` 来控制测试启用。`matches = ".*"` 表示只要变量存在即启用；`matches = "\\d{12}"` 用于校验 account id 为 12 位数字。每个测试类根据自身所需的最小环境变量集合添加注解。

`AwsIntegTestUtil` 提供统一的环境变量名常量，避免各测试类中硬编码字符串，减少不一致风险。`build.gradle` 把 `integrationTest` 加入 `check` 依赖，使集成测试在构建流程中被执行（受环境变量控制）。

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/AwsIntegTestUtil.java` (修改, +26/-7 lines)

**修改目的**：将环境变量名提取为 public 常量并统一引用。

**工作逻辑**：
- 新增一组 public static final 常量：`AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`、`AWS_SESSION_TOKEN`、`AWS_REGION`、`AWS_CROSS_REGION`、`AWS_TEST_BUCKET`、`AWS_TEST_CROSS_REGION_BUCKET`、`AWS_TEST_ACCOUNT_ID`、`AWS_TEST_MULTI_REGION_ACCESS_POINT_ALIAS`。
- 各 `testRegion()`/`testCrossRegion()`/`testBucketName()` 等方法把原本硬编码的字符串（如 `"AWS_REGION"`）替换为引用常量。

### `aws/src/integration/java/org/apache/iceberg/aws/TestAssumeRoleAwsClientFactory.java` (修改, +10/-0 lines)

**修改目的**：根据所需环境变量启用测试。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables` 注解，要求 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`、`AWS_SESSION_TOKEN`、`AWS_TEST_ACCOUNT_ID`（`matches="\\d{12}"`）、`AWS_REGION`、`AWS_TEST_BUCKET` 均存在。

### `aws/src/integration/java/org/apache/iceberg/aws/TestDefaultAwsClientFactory.java` (修改, +9/-0 lines)

**修改目的**：同上，按需启用。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables` 注解（基础凭证 + region/bucket）。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbCatalog.java` (修改, +9/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加基础环境变量的 `@EnabledIfEnvironmentVariables` 注解。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbLockManager.java` (修改, +9/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：同上。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogCommitFailure.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables`（基础凭证 + region/bucket）。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogLock.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：同上。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogNamespace.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：同上。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables`（基础凭证 + region/bucket）。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationAwsClientFactory.java` (修改, +8/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables`。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationDataOperations.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：同上。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationMetadataOperations.java` (修改, +10/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：同上。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java` (修改, +14/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables`（含 cross region/multi-region access point 相关变量）。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3MultipartUpload.java` (修改, +9/-0 lines)

**修改目的**：启用条件控制。

**工作逻辑**：添加 `@EnabledIfEnvironmentVariables`。

### 文件移动（rename, 0 行变更）

**修改目的**：调整部分 s3 测试文件位置。

涉及文件：`MinioUtil.java`、`TestFlakyS3InputStream.java`、`TestMinioUtil.java`、`TestS3FileIO.java`、`TestS3InputStream.java`、`TestS3OutputStream.java`、`signer/S3SignerServlet.java`、`signer/TestS3RestSigner.java`（均 0 行变更，纯路径移动）。

### `build.gradle` (修改, +1/-0 lines)

**修改目的**：将集成测试纳入 check 流程。

**工作逻辑**：在 `:iceberg-aws` 项目下添加 `check.dependsOn integrationTest`，使 `check` 任务依赖集成测试任务（实际运行受环境变量控制）。

## 总结

本提交为 AWS 集成测试引入基于环境变量的启用条件控制：在 `AwsIntegTestUtil` 中提取环境变量名常量，为各 AWS 集成测试类添加 JUnit 5 `@EnabledIfEnvironmentVariable(s)` 注解（仅在所需 AWS 凭证/资源变量齐备时启用，否则跳过），并让 `:iceberg-aws` 的 `check` 依赖 `integrationTest`。同时包含若干 s3 测试文件的路径移动。这使集成测试在缺少 AWS 凭证的环境中能被优雅跳过而非失败。
