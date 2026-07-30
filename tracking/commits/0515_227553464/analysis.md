# 提交 0515：Build: Bump software.amazon.awssdk:bom from 2.24.0 to 2.24.5

## 提交信息

- **序号**：0515 / 4088
- **哈希**：227553464a172b2df484b847f35f4790594d604c
- **短哈希**：227553464
- **日期**：2024-02-19（Mon Feb 19 10:24:46 2024 +0100）
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.24.0 to 2.24.5 (#9743)
- **PR/Issue**：#9743

## 总体目的

这是一次由 Dependabot 自动发起的依赖升级，把 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 2.24.0 升到 2.24.5（语义化版本中的 patch 升级，共跨 5 个 patch 版本）。Dependabot 在 PR 描述中标注了 `dependency-type: direct:production`、`update-type: version-update:semver-patch`。

AWS SDK for Java v2 的 BOM 是一个“平台依赖”（platform / BOM），它本身不含代码，而是统一管理 AWS SDK 全部模块的版本——S3、DynamoDB、Glue、KMS、LakeFormation、STS、IAM、S3Control、auth、apache-client、url-connection-client 等。Iceberg 的 `iceberg-aws` 与 `iceberg-aws-bundle` 模块通过 BOM 来声明对各 AWS 服务 SDK 的依赖而不必各自指定版本，BOM 升级后所有 AWS SDK 模块版本会同步提升。

本次升级的目标是跟进 AWS SDK 上游 5 个 patch 版本累积的修复与服务模型更新。AWS SDK v2 的 patch 版本通常包含：
- 对已有服务客户端的 bug 修复（如重试、签名、HTTP 客户端行为）
- 新增服务模型字段（向前兼容，旧代码不受影响）
- 内部依赖小版本升级（Netty、核心模块等）

## 如何达成设计目的

升级方式极简：只改一个文件 `gradle/libs.versions.toml`，把版本常量 `awssdk-bom` 从 `"2.24.0"` 改为 `"2.24.5"`。该常量通过 `version.ref` 被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，所有 `platform(libs.awssdk.bom)` 调用会一并升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 `awssdk-bom` 版本常量。

**工作逻辑**：
```toml
-awssdk-bom = "2.24.0"
+awssdk-bom = "2.24.5"
```
单行改动。该常量位于 `[versions]` 段（约第 28~32 行），与 `arrow`、`avro`、`assertj-core`、`awaitility`、`azuresdk-bom`、`awssdk-s3accessgrants`、`caffeine` 等版本常量并列。下游 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 在 `[libraries]` 段（第 82 行）通过 `version.ref` 引用，Gradle 会把所有依赖此 BOM 的位置统一升到 2.24.5。

## 消费链路：AWS SDK BOM 在 Iceberg 中的角色

为了让回迁评估更完整，下面说明 `software.amazon.awssdk:bom` 在 Iceberg 中的具体使用方式（基于 1.4.x 当前代码库搜索）：

1. **iceberg-aws 模块**（`build.gradle` 第 446~499 行）：
   - `compileOnly(platform(libs.awssdk.bom))`：编译期通过 BOM 平台声明对以下 AWS SDK 模块的依赖，但不打包进 iceberg-aws 的 jar（因为最终用户运行时通常已有自己的 AWS SDK 版本）：
     - `url-connection-client`、`apache-client`（HTTP 客户端实现）
     - `auth`（鉴权，含 `DefaultCredentialsProvider`、`AwsSessionCredentials`、`StaticCredentialsProvider` 等）
     - `s3`（S3 客户端与所有 `S3*` 模型类）
     - `kms`（KMS 客户端，用于 S3 加密）
     - `glue`（Glue 客户端，对应 `GlueCatalog` 实现）
     - `sts`（STS 客户端，用于 `StsAssumeRoleCredentialsProvider`、`AssumeRoleRequest` 等）
     - `dynamodb`（DynamoDB 客户端，对应 `DynamoDbCatalog`）
     - `lakeformation`（Lake Formation 客户端，用于 `LakeFormationAwsClientFactory` 获取临时凭证）
   - `testImplementation(platform(libs.awssdk.bom))` + `iam`、`s3control`：测试期额外引入 IAM、S3 Control 模块。

2. **iceberg-aws-bundle 模块**（`aws-bundle/build.gradle`）：
   - `implementation platform(libs.awssdk.bom)` + `implementation` 各 AWS SDK 模块（`apache-client`、`auth`、`iam`、`sso`、`s3`、`kms`、`glue`、`sts`、`dynamodb`、`lakeformation`）。
   - 这是一个“胖 jar” bundle，使用 shadow 插件把 AWS SDK 全部模块打包到一个 jar 中，方便用户在不想自行管理 AWS SDK 依赖时直接引入。同时 relocate `org.apache.http` 与 `io.netty` 以避免与用户其他依赖冲突。
   - 该模块是 **runtime** 依赖（implementation，不是 compileOnly），BOM 升级会直接影响打包进去的 AWS SDK 版本。

3. **代码层面使用**（基于 `aws/src/main/java` 搜索）：约 140 个不同的 AWS SDK 类被 import 使用，覆盖：
   - **S3**：`S3Client`、`S3ClientBuilder`、`S3Configuration` 及大量模型类（`PutObjectRequest`、`GetObjectRequest`、`DeleteObjectsRequest`、`CompletedMultipartUpload` 等），对应 `S3FileIO` 等文件 IO 实现。
   - **Glue**：`GlueClient`、`GlueClientBuilder` 及模型类（`CreateDatabaseRequest`、`GetTableResponse`、`TableInput` 等），对应 `GlueCatalog`。
   - **DynamoDB**：`DynamoDbClient` 及模型类（`AttributeValue`、`TransactWriteItemsRequest` 等），对应 `DynamoDbCatalog`。
   - **KMS**：`KmsClient`，用于 S3 客户端加密。
   - **Lake Formation**：`LakeFormationClient`、`GetTemporaryGlueTableCredentialsRequest`，用于跨账号访问授权。
   - **STS**：`StsClient`、`StsAssumeRoleCredentialsProvider`、`AssumeRoleRequest`，用于 AssumeRole 鉴权。
   - **签名**：`Aws4Signer`、`Aws4SignerParams`、`AwsS3V4SignerParams`、`SignerChecksumParams` 等，用于 `S3V4RestSignerClient`（参见 0511 提交）等自定义签名器。
   - **HTTP**：`SdkHttpClient`、`ApacheHttpClient`、`UrlConnectionHttpClient`。
   - **凭证**：`AwsBasicCredentials`、`AwsSessionCredentials`、`AwsCredentialsProvider`、`DefaultCredentialsProvider`、`StaticCredentialsProvider`、`AnonymousCredentialsProvider`。

   由于 BOM 是平台依赖，所有这些 import 的类版本在编译期与运行期都会跟随 BOM 升级而升级。

4. **运行时影响**：
   - `iceberg-aws` 模块：BOM 是 `compileOnly`，意味着该模块的 jar 不携带 AWS SDK，运行时由下游用户/引擎（如 Spark、Flink、Trino）提供 AWS SDK。BOM 升级只影响**编译期 API 兼容性**，不影响该模块 jar 的内容。
   - `iceberg-aws-bundle` 模块：BOM 是 `implementation`，会把对应 AWS SDK 版本的 jar 打包进 shadow jar，运行时直接生效。BOM 升级会**直接改变 bundle 内的 AWS SDK 版本**，使用 bundle 的用户会立即得到新版本。

## 小结

本提交是一次低风险的依赖维护升级：把 AWS SDK for Java v2 的 BOM 从 2.24.0 升到 2.24.5（跨 5 个 patch 版本），跟进上游累积的 bug 修复与服务模型更新。改动只有一行版本号，无源码改动，无 API 破坏（patch 版本保证向前兼容）。

**影响范围**：
- `iceberg-aws` 模块：仅编译期 API 跟随升级，运行时 jar 不含 AWS SDK，下游用户提供。需确认下游用户的 AWS SDK 版本 ≥ 2.24.5（一般不会有冲突，因为 AWS SDK v2 patch 版本完全兼容）。
- `iceberg-aws-bundle` 模块：bundle 内打包的 AWS SDK 版本升级到 2.24.5，使用 bundle 的用户运行时直接生效，享受上游修复。
- 不影响用户配置、不影响其他模块。

**回迁到 1.4.x 注意事项**：
1. 1.4.x 当前 `awssdk-bom` 版本为 **2.20.131**（比 main 还落后一个 minor，2.20.x → 2.24.x 跨了 4 个 minor），即 1.4.x 实际上是 2.20.131 → 2.24.5，跨度较大。本提交只覆盖 2.24.0 → 2.24.5 这一步；若 1.4.x 想完整追平 main，需要先做 2.20.131 → 2.24.0 的跨 minor 升级，再做本提交的 2.24.0 → 2.24.5。
2. 跨 minor（2.20.x → 2.24.x）升级通常包含较多变化：新服务模型、少量已弃用 API 可能被移除、HTTP 客户端与签名行为微调。需要重新编译 `iceberg-aws` 与 `iceberg-aws-bundle` 并跑一遍 aws 模块的全部单元测试与集成测试（特别是 S3FileIO、GlueCatalog、DynamoDbCatalog、LakeFormationAwsClientFactory、S3V4RestSignerClient 等），确认无 API break。
3. AWS SDK v2 在 2.21+ 引入了 S3 Express One Zone、新校验和算法（`SdkChecksum`、`Algorithm`）等；2.22~2.24 还涉及 S3 客户端行为变化（如 multipart upload、checksum 默认值）。若 1.4.x 用户曾对 AWS SDK 行为做过程序化依赖（例如绕过 SDK 自己拼签名），需要回归测试。
4. `iceberg-aws-bundle` 升级后 bundle 体积可能略有变化（依赖 jar 数量与大小），且 relocate 范围（`org.apache.http`、`io.netty`）不变，但被 relocate 的内部版本会随之升级——使用 bundle 的用户若同时引入了自己的 Netty / HttpClient，需注意版本冲突。
5. 由于 AWS SDK v2 patch 版本完全向前兼容，本提交（2.24.0 → 2.24.5）本身回迁到 1.4.x 风险极低；真正需要谨慎评估的是其前置依赖（2.20.131 → 2.24.0）的跨 minor 升级。
