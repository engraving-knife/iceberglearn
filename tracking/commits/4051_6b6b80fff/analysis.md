# 提交 4051：Build: Bump software.amazon.awssdk:bom from 2.46.21 to 2.47.2 (#17238)

## 提交信息

- **序号**：4051 / 4088
- **哈希**：6b6b80fff01bae9430f911a1f3df9d43a4973868
- **短哈希**：6b6b80fff
- **日期**：2026-07-16 10:42:29 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.46.21 to 2.47.2 (#17238)
- **PR/Issue**：#17238

## 总体目的

Dependabot 自动升级提交，将 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 2.46.21 升级到 2.47.2（semver minor 版本升级）。AWS SDK 是 Iceberg 与 AWS 服务（S3、Glue、DynamoDB、KMS、Lake Formation 等）交互的核心依赖，BOM 统一管理所有 AWS SDK 模块的版本。

本次升级涉及大量 AWS SDK 子模块的版本对齐（从 2.46 系列升级到 2.47 系列），因此除了版本目录的版本变量更新外，还同步更新了 `aws-bundle` 和 `kafka-connect-runtime` 的运行时依赖清单（`runtime-deps.txt`），这些清单记录了打包时包含的传递依赖及其版本，需要与 BOM 版本保持一致。minor 版本升级通常包含新服务支持、功能改进和 bug 修复。

## 如何达成设计目的

通过三处同步更新完成升级：(1) `gradle/libs.versions.toml` 中的 `aws-sdk` BOM 版本变量更新；(2) `aws-bundle/runtime-deps.txt` 中所有 AWS SDK 模块的版本前缀从 `2.46` 更新为 `2.47`；(3) `kafka-connect-runtime/runtime-deps.txt` 同步更新。这样确保构建依赖、打包清单与 Kafka Connect 运行时包三者版本一致。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本变量。

**工作逻辑**：将 `aws-sdk`（或对应的 BOM 变量）版本从 `2.46.21` 改为 `2.47.2`，所有引用该 BOM 的 AWS SDK 模块版本随之对齐。

### `aws-bundle/runtime-deps.txt` (+45/-45 lines)

**修改目的**：同步更新 AWS bundle 的运行时依赖清单。

**工作逻辑**：将清单中所有 `software.amazon.awssdk:*` 模块的版本前缀从 `2.46` 批量更新为 `2.47`（如 `software.amazon.awssdk:annotations:2.46` → `2.47`、`software.amazon.awssdk:glue:2.46` → `2.47` 等），涵盖 annotations、apache-client、auth、glue、dynamodb、kms、lakeformation、netty-nio-client、s3、sts 等数十个模块。该清单用于生成 uber jar / bundle 时的依赖版本固定。

### `kafka-connect-runtime/runtime-deps.txt` (+40/-40 lines)

**修改目的**：同步更新 Kafka Connect 运行时包的依赖清单。

**工作逻辑**：同样将 Kafka Connect 运行时打包清单中所有 AWS SDK 模块版本从 `2.46` 更新为 `2.47`，确保 Kafka Connect 发行版打包的 AWS 依赖与主构建版本一致。

## 总结

常规但规模较大的 AWS SDK minor 版本升级，涉及 BOM 版本变量和两个打包清单的同步更新，确保构建、AWS bundle 和 Kafka Connect 发行版的 AWS 依赖版本一致。2.47 系列带来新服务支持和功能改进。minor 级别升级通常向后兼容，但 AWS SDK 升级需关注行为变化，CI 会验证兼容性。
