# 提交 0184：Build: Bump software.amazon.awssdk:bom from 2.21.22 to 2.21.26 (#9105)

## 提交信息

- **序号**：0184 / 4088
- **哈希**：f8d21116b1990ff0f7d7960a0f41f3a807756141
- **短哈希**：f8d21116b
- **日期**：2023-11-20 08:38:33 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.22 to 2.21.26 (#9105)
- **PR/Issue**：#9105

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖升级提交，将 [AWS SDK for Java](https://github.com/aws/aws-sdk-java-v2) 的 BOM（Bill of Materials）从 2.21.22 升级到 2.21.26。

AWS SDK for Java v2 是 Iceberg 与 AWS 云服务交互的核心依赖。在 Iceberg 项目中，`aws` 模块（`aws/src/main/java/org/apache/iceberg/aws/`）提供了对多种 AWS 服务的集成支持，包括：
- **S3**：通过 `S3FileIO` 实现对象存储的读写，是 Iceberg 最常用的存储后端之一。
- **Glue**：通过 `GlueCatalog` 实现基于 AWS Glue 数据目录的表元数据管理。
- **DynamoDB**：用于 `DynamoDbCatalog` 以及作为锁管理器（lock manager）支持并发提交控制。
- **Lake Formation**：提供细粒度访问控制集成。

BOM（Bill of Materials）是一种特殊的 POM，通过 `platform` 机制统一管理一组相关依赖的版本，确保同一 SDK 生态中各模块（如 `s3`、`glue`、`dynamodb`、`sts` 等）版本一致，避免模块间版本不兼容。通过升级 BOM 版本号，项目中所有 AWS SDK 子模块的版本会自动同步升级到 2.21.26。

2.21.22 到 2.21.26 是一个 semver-patch（补丁版本）升级，属于 `direct:production` 类型的直接生产依赖。补丁升级通常包含 bug 修复和安全补丁，不引入 API 破坏性变更，风险较低。持续跟踪 AWS SDK 版本更新对 Iceberg 尤为重要，因为 S3FileIO 等组件在生产环境中的稳定性和安全性直接依赖 SDK 的质量。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，从 `2.21.22` 改为 `2.21.26`。该版本目录中同时定义了版本号（`awssdk-bom`）和库别名（`awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`），项目通过引入该 BOM 作为 platform 依赖来统一管理所有 AWS SDK 子模块的版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK for Java BOM 版本从 2.21.22 升级到 2.21.26。

**工作逻辑**：该文件是 Gradle 的版本目录，集中定义项目所有依赖的版本号。修改发生在第 27 行附近，将 `awssdk-bom = "2.21.22"` 改为 `awssdk-bom = "2.21.26"`。此 BOM 被项目中的 `aws` 模块（以及 `aws-bundle`、`s3` 等相关模块）通过 `libs.awssdk.bom` 引用，以 `platform`/`enforcedPlatform` 方式引入后，Gradle 会自动将所有 `software.amazon.awssdk:*` 子模块的版本对齐到 BOM 声明的 2.21.26。升级后，S3FileIO、GlueCatalog、DynamoDbCatalog 等组件所依赖的 AWS SDK 模块（如 `s3`、`glue`、`dynamodb`、`sts`、`apache-client`/`url-connection-client` 等）统一获得 2.21.26 版本的 bug 修复和改进。

## 小结

这是 AWS SDK for Java BOM 的例行补丁版本升级，确保 Iceberg 的 AWS 集成模块（S3、Glue、DynamoDB、Lake Formation 等）运行在最新且稳定的 SDK 版本上。
