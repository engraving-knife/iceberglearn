# 提交 3636：Build: Bump software.amazon.awssdk:bom from 2.42.36 to 2.42.41 (#16206)

## 提交信息

- **序号**：3636 / 4088
- **哈希**：33e173c0d6afa96f86d9f2d6da4030d9c691b920
- **短哈希**：33e173c0d
- **日期**：2026-05-03 18:59:03 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.36 to 2.42.41 (#16206)
- **PR/Issue**：#16206

## 总体目的

这个提交将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从版本 2.42.36 升级到 2.42.41，是一个 patch 级别的版本更新。

AWS SDK for Java 是 Iceberg 与 AWS 服务（如 S3、Glue、DynamoDB、KMS 等）交互的核心依赖。通过 BOM 机制统一管理 AWS SDK 各模块的版本，确保所有 AWS SDK 模块使用一致的版本，避免版本冲突。升级到 2.42.41 可以获得最新的 bug 修复和小改进。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本号，并同步更新 `aws-bundle` 和 `kafka-connect-runtime` 的 `runtime-deps.txt` 文件中所有 AWS SDK 子模块的版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：
```toml
awssdk-bom = "2.42.41"  # 从 2.42.36 升级
```

### `aws-bundle/runtime-deps.txt` (+45/-45 lines)

**修改目的**：同步更新 aws-bundle 中所有 AWS SDK 子模块的版本号。

**工作逻辑**：将所有 `software.amazon.awssdk:*` 模块的版本从 2.42.36 更新到 2.42.41，包括 `annotations`、`apache-client`、`arns`、`auth`、`aws-core`、`s3`、`glue`、`kms`、`sts` 等模块。同时保留 `software.amazon.awssdk.crt:aws-crt:0.44.0`（CRT 模块单独管理版本）。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+40/-40 lines)

**修改目的**：同步更新 kafka-connect-runtime 中 AWS SDK 子模块的版本号。

**工作逻辑**：与 aws-bundle 类似，将所有 AWS SDK 子模块版本更新到 2.42.41。

## 总结

这是一个 Dependabot 自动依赖升级提交，将 AWS SDK for Java 2.x 从 2.42.36 升级到 2.42.41（patch 版本）。作为 patch 级别升级，通常包含 bug 修复，风险较低。AWS SDK 是 Iceberg 与 AWS 服务交互的核心依赖，此次升级同时更新了 BOM 版本号和各 bundle 的 runtime-deps.txt 文件以保持一致性。
