# 提交 3327：Build: Bump software.amazon.awssdk:bom from 2.41.34 to 2.42.4 (#15485)

## 提交信息

- **序号**：3327 / 4088
- **哈希**：6f99c962fea4b0148de9d510e7c958d43778d5a4
- **短哈希**：6f99c962f
- **日期**：2026-02-28 22:08:29 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.34 to 2.42.4 (#15485)
- **PR/Issue**：#15485

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.41.34` 升级到 `2.42.4`。

AWS SDK for Java v2 是 Iceberg 与 AWS 云存储（S3、S3 Access Grants）、DynamoDB（用作表锁/目录）、Glue（catalog 元数据）等服务交互的核心依赖。Iceberg 通过引入 `awssdk-bom` 这个 BOM 来统一管理 AWS SDK 全套子模块（如 `s3`、`sts`、`glue`、`dynamodb-enhanced` 等）的版本，避免各子模块版本不一致导致的兼容性问题。BOM 本身不含代码，仅是一个版本清单（POM 的 `<dependencyManagement>` 段），被 import 后所有 `software.amazon.awssdk:*` 子模块都会按 BOM 中声明的版本解析。

本次升级属于语义化版本的 **minor（次版本）** 升级（`2.41.34` → `2.42.4`，`update-type: version-update:semver-minor`）。从 `2.41.x` 跳到 `2.42.x` 表示引入了新的次版本特性，同时也跨越了多个 patch 版本（34 → 4，即 2.42.0/2.42.1/2.42.2/2.42.3/2.42.4 的累积）。AWS SDK v2 在 minor 升级中通常保持二进制兼容，新增可选 API 并修复缺陷，但仍需关注 S3 客户端行为、重试策略、签名等关键路径的变更。Dependabot 将其标记为 `direct:production`，意味着这些 SDK 模块直接进入运行时 classpath。预期影响是获得 2.42 系列的新特性与缺陷修复，对 Iceberg 现有的 S3/Glue/DynamoDB 集成行为通常无破坏性影响，但建议关注 S3 客户端相关集成测试。

## 如何达成设计目的

改动仅修改版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 这一项的版本字符串。Iceberg 在 `build.gradle` 中通过 platform/BOM 机制 import 该版本变量所指向的 `software.amazon.awssdk:bom`，因此各 AWS SDK 子模块会自动跟随 BOM 版本升级，无需逐模块修改坐标版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本从 2.41.34 提升到 2.42.4。

**工作逻辑**：
在版本目录 `[versions]` 段中，将 `awssdk-bom = "2.41.34"` 修改为 `awssdk-bom = "2.42.4"`。该变量被用于在根 `build.gradle` 中 import `software.amazon.awssdk:bom` 平台，从而统一约束所有 `software.amazon.awssdk:*` 子模块（如 S3、STS、Glue、DynamoDB 等）的版本。这是一次纯版本号变更，不涉及代码逻辑改动；S3 Access Grants 相关坐标（`awssdk-s3accessgrants`）单独管理版本，不受本次 BOM 升级影响。

## 总结

本次提交通过 Dependabot 将 AWS SDK for Java 的 BOM 从 2.41.34 升级到 2.42.4（minor 级），以获取 2.42 系列的新特性与缺陷修复。改动仅涉及版本目录单行，借助 BOM 机制统一约束所有 AWS SDK 子模块版本，是日常云服务依赖维护的一部分，对 Iceberg 现有 AWS 集成通常无破坏性影响。
