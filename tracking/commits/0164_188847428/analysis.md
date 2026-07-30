# 提交 0164：Build: Bump software.amazon.awssdk:bom from 2.21.21 to 2.21.22 (#9053)

## 提交信息

- **序号**：0164 / 4088
- **哈希**：188847428309701c2ec0debf9e94da94b39058b6
- **短哈希**：188847428
- **日期**：2023-11-14 10:36:30 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.21 to 2.21.22 (#9053)
- **PR/Issue**：#9053

## 总体目的

这个提交是 Dependabot 自动生成的依赖升级，将 Iceberg 构建中引用的 AWS SDK for Java BOM（`software.amazon.awssdk:bom`）从 2.21.21 升级到 2.21.22。

AWS SDK for Java 是 Iceberg 与 AWS 服务（S3、Glue、DynamoDB、KMS 等）集成的核心依赖。Iceberg 通过该 SDK 实现对 S3 的对象存储读写、对 Glue 的元数据目录访问、对 DynamoDB 的锁服务、对 KMS 的加解密等能力。由于 AWS SDK 模块众多，Iceberg 使用 BOM（Bill of Materials）方式统一管理所有 AWS SDK 子模块的版本，确保各模块版本相互兼容，避免单个模块单独声明版本时产生冲突。

本次升级属于 semver-patch 级别（Dependabot 元数据标注 `update-type: version-update:semver-patch`），从 2.21.21 到 2.21.22，是一个补丁版本。AWS SDK 的 patch 版本通常包含 bug 修复、小改进与服务模型（service model）更新（即对新 AWS API 字段或新服务的客户端支持），不引入破坏性 API 变更。升级动机在于：获取最新的 bug 修复与服务模型更新，保持与 AWS 服务的兼容性，并降低安全漏洞风险。

对 Iceberg 演进的意义在于：作为日常维护性的依赖补丁升级，保障 Iceberg 的 AWS 集成栈持续健康，使 S3/Glue/DynamoDB/KMS 等集成模块与 AWS SDK 最新补丁保持同步，为用户在生产环境与 AWS 交互时提供稳定的客户端基础。

## 如何达成设计目的

设计思路是单行版本号修改：Dependabot 仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本常量，由于 Iceberg 全工程通过版本目录引用该常量来导入 AWS SDK BOM，所有 AWS SDK 子模块的版本会随之统一对齐到 2.21.22。改动结构上只涉及一个文件的一行。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本常量从 2.21.21 升级到 2.21.22，统一推进全工程所有 AWS SDK 子模块版本。

**工作逻辑**：`gradle/libs.versions.toml` 是 Iceberg 的 Gradle 版本目录（Version Catalog），集中声明所有依赖的版本常量。本次改动将 `awssdk-bom = "2.21.21"` 改为 `awssdk-bom = "2.21.22"`。由于 Iceberg 各模块（`aws/`、`s3/`、`gcp/` 等需要访问 S3 的模块、`delta` 等其它集成）在 `build.gradle` 中通过 `platform(bundleVersionOf("awssdk-bom"))` 或类似方式引入该 BOM，BOM 版本号一改，所有 `software.amazon.awssdk:*` 子模块（如 `s3`、`glue`、`sts`、`dynamodb`、`kms`、`apache-client`、`netty-nio-client` 等）的版本都会同步更新到 2.21.22 提供的对齐版本。同文件中其它依赖（`arrow = "14.0.1"`、`avro = "1.11.3"`、`assertj-core = "3.24.2"`、`awaitility = "4.2.0"`、`azuresdk-bom = "1.2.18"`、`caffeine = "2.9.3"`、`calcite = "1.10.0"` 等）保持不变。这种集中式版本目录设计正是为了支持此类一行式升级。

## 小结

本次提交通过一行 Gradle 版本目录中的 BOM 版本常量升级，将 Iceberg 全工程的 AWS SDK for Java 推进到 2.21.22 patch 版本，属于维护性的 AWS 集成栈依赖更新，保障 S3/Glue/DynamoDB/KMS 等模块与 AWS SDK 最新补丁同步。
