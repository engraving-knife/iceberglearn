# 提交 3127：Build: Bump software.amazon.awssdk:bom from 2.41.5 to 2.41.10 (#15077)

## 提交信息

- **序号**：3127 / 4088
- **哈希**：bfc76145f988564525e4934ba6910992f6cc41a0
- **短哈希**：bfc76145f
- **日期**：2026-01-17
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.5 to 2.41.10
- **PR/Issue**：#15077

## 总体目的

这是 GitHub Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.41.5` 升级到 `2.41.10`。

AWS SDK BOM 是 Iceberg 中最核心的依赖之一。Iceberg 原生支持以 S3 作为表数据存储、以 DynamoDB 作为锁与提交回调后端，这些集成都建立在 AWS SDK for Java v2 之上。BOM 的作用是通过 `platform` 依赖统一管理所有 AWS SDK 子模块（如 `s3`、`sts`、`dynamodb`、`apache-client`、`netty-nio-client` 等）的版本，避免各模块版本不一致导致的兼容性问题。Iceberg 在 `build.gradle` 与 `aws-bundle`、`iceberg-aws` 等模块中广泛引用该 BOM，因此 BOM 版本直接决定了整个 AWS 集成栈的基线。

Dependabot 标注本次为 `version-update:semver-patch` 补丁版本升级（2.41.5 → 2.41.10，跨越 5 个补丁版本）。AWS SDK v2 的补丁版本通常包含缺陷修复、性能改进以及对 AWS 服务 API 的增量适配，不涉及破坏性 API 变更。及时跟进可修复已知的客户端稳定性问题（如连接池、重试、S3 协议相关修复），保障 Iceberg 在 AWS 环境下的可靠读写。

## 如何达成设计目的

改动仅更新 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本号常量，由所有通过 `version.ref` 引用该常量的库声明与 `platform(libs.awssdk.bom)` 消费方自动继承新版本，无需改动任何源码或构建脚本逻辑。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 awssdk-bom 版本从 2.41.5 提升到 2.41.10。

**工作逻辑**：
该文件是集中式依赖版本目录。改动位于 `[versions]` 段，将 `awssdk-bom = "2.41.5"` 改为 `awssdk-bom = "2.41.10"`。在 `[libraries]` 段中已声明 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`。各 AWS 相关模块通过 `platform(libs.awssdk.bom)` 引入 BOM 后，其声明的 `awssdk-s3`、`awssdk-sts`、`awssdk-dynamodb` 等子模块无需指定版本，统一由 BOM 决定，因此一处版本号变更即可带动整套 AWS SDK 子模块升级。

## 总结

本次为 AWS SDK BOM 的补丁版本升级，通过更新版本目录一处版本号，将整个 AWS SDK for Java v2 模块栈从 2.41.5 统一推进到 2.41.10，为 Iceberg 的 S3、DynamoDB 等 AWS 集成带来上游累积的稳定性与缺陷修复，属于低风险、高价值的常规维护。
