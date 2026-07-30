# 提交 3036：Build: Bump software.amazon.awssdk:bom from 2.40.8 to 2.40.13 (#14904)

## 提交信息

- **序号**：3036 / 4088
- **哈希**：73c2aa2d1cce7a563c32c2e668bd7234cf5bfd55
- **短哈希**：73c2aa2d1
- **日期**：2025-12-20
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.40.8 to 2.40.13 (#14904)
- **PR/Issue**：#14904

## 总体目的

本提交是 Dependabot 自动生成的依赖版本升级，将 AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.40.8` 升级到 `2.40.13`。AWS SDK v2 是 Iceberg 与 AWS 云服务交互的核心依赖，涵盖 S3 对象存储访问、DynamoDB（用作 Glue Catalog 的锁表）、STS（临时凭证获取）、以及 S3 Access Grants 等多个 AWS 服务的客户端。`awssdk-bom` 作为 BOM，本身不提供代码，而是集中管理 AWS SDK 各模块（如 `s3`、`url-connection-client`、`apache-client`、`netty-nio-client`、`sts`、`dynamodb-enhanced` 等）的版本坐标，确保各模块版本一致、避免依赖冲突。

在 Iceberg 项目中，AWS SDK 通过 `gradle/libs.versions.toml` 的 `awssdk-bom` 条目导入，被 `iceberg-aws` 模块及其下游（如 `iceberg-spark`、`iceberg-flink` 的 AWS 集成）大量引用。Dependabot 元数据显示该依赖为 `direct:production` 类型，说明它进入发布制品的运行时路径，影响生产环境而非仅测试。本次升级属于语义版本的补丁级别升级（semver-patch，`2.40.8` → `2.40.13`），跨越 5 个补丁版本（.9/.10/.11/.12/.13），仅包含 bug 修复与向后兼容的改进，不引入破坏性 API 变更。

此次升级的动机属于常规依赖维护：AWS SDK 频繁发布补丁以修复安全漏洞、HTTP 客户端稳定性问题及各服务客户端的功能缺陷，保持与上游同步有助于减少生产环境中的潜在问题。

## 如何达成设计目的

改动仅涉及 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本字符串的更新。由于整个项目的 AWS SDK 模块均通过 `version.ref = "awssdk-bom"` 引用 BOM，单点修改即可同步升级所有 AWS SDK 子模块的版本，Gradle 在解析依赖时会用 BOM 中声明的版本覆盖各子模块的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK v2 BOM 版本号。

**工作逻辑**：
将版本目录中 `awssdk-bom = "2.40.8"` 修改为 `awssdk-bom = "2.40.13"`。该版本通过 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 定义为 BOM 依赖，被 `iceberg-aws` 等模块的 `build.gradle` 以 `platform` 方式导入。修改后，所有依赖 AWS SDK 子模块（s3、sts、dynamodb、apache-client 等）的模块将自动解析到 2.40.13 对应的版本，从而获得上游补丁修复。

## 总结

本提交是 Dependabot 自动维护的 AWS SDK v2 BOM 补丁版本升级（2.40.8 → 2.40.13），属于低风险的生产依赖版本维护。AWS SDK 作为 Iceberg 与 S3/DynamoDB/STS 等 AWS 服务交互的基础，补丁升级确保运行时稳定性与上游安全/缺陷修复同步，不引入破坏性变更。
