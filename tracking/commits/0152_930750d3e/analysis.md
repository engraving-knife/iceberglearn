# 提交 0152：Build: Bump software.amazon.awssdk:bom from 2.21.15 to 2.21.21 (#9044)

## 提交信息

- **序号**：0152 / 4088
- **哈希**：930750d3e21f38eb7a5ca53438279ad2a10656cf
- **短哈希**：930750d3e
- **日期**：2023-11-13 09:59:56 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.15 to 2.21.21 (#9044)
- **PR/Issue**：#9044

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.21.15` 升级到 `2.21.21`，跨 6 个 patch 版本（2.21.16→2.21.21），属于 `version-update:semver-patch` 级别的依赖更新。

`software.amazon.awssdk:bom` 是 AWS 官方维护的 BOM，统一管理 AWS SDK for Java v2 全套 artifact（如 S3、DynamoDB、KMS、STS、Glue 等客户端及其传递依赖）的版本。Iceberg 的 `aws` 模块（`iceberg-aws`）通过引入该 BOM 来对齐 AWS 相关客户端库版本，避免手工维护多个互相耦合的版本号。AWS SDK 是 Iceberg 对接 S3 作为数据/元数据存储、对接 Glue 作为目录、对接 DynamoDB 作为锁存储的基础设施，升级 BOM 通常会带来 bug 修复、稳定性改进、安全补丁以及对底层 HTTP/认证库的兼容性提升。

本次升级是 patch 级别（2.21.x 系列），按 Dependabot 分类属于 `version-update:semver-patch`，意味着只有兼容性补丁，不包含破坏性变更，风险较低。Iceberg 维护者合并此 PR 即表示认可升级带来的变更在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `awssdk-bom = "2.21.15"` 改为 `awssdk-bom = "2.21.21"`。所有通过 `libs.awssdk.bom` 引用该 BOM 的模块（主要是 `iceberg-aws` 及其集成测试）会自动解析到新版本，无需逐个修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `software.amazon.awssdk:bom` 的版本从 2.21.15 升级到 2.21.21。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（第 30 行附近），原行 `awssdk-bom = "2.21.15"` 被改为 `awssdk-bom = "2.21.21"`。该版本常量在第 82 行附近通过 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 绑定到具体 artifact，并被 `iceberg-aws` 模块以 `platform(libs.awssdk.bom)` 形式导入为 BOM，从而统一控制 S3、DynamoDB、Glue、STS、KMS 等 AWS 客户端 artifact 的版本。升级后，相关 artifact 会按 2.21.21 BOM 解析到对应的较新 patch 版本，获取 AWS SDK 在 2.21.16–2.21.21 期间累积的修复与改进。

## 小结

该提交由 Dependabot 自动将 AWS SDK for Java v2 BOM 从 2.21.15 升级到 2.21.21，使 Iceberg 的 S3/Glue/DynamoDB 等集成跟进 AWS SDK 最新 patch 版本，获取 bug 修复与稳定性改进。
