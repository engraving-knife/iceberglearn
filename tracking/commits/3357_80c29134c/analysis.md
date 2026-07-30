# 提交 3357：Build: Bump software.amazon.awssdk:bom from 2.42.4 to 2.42.8 (#15540)

## 提交信息

- **序号**：3357 / 4088
- **哈希**：80c29134c6f3706f5ce469261617d54a9c75809b
- **短哈希**：80c29134c
- **日期**：2026-03-08
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.4 to 2.42.8
- **PR/Issue**：#15540

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，用于将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.42.4 升级到 2.42.8。AWS SDK BOM 是 Iceberg 项目中至关重要的依赖，因为 Iceberg 需要通过 AWS SDK 与 S3、Glue、DynamoDB 等 AWS 服务交互，包括读写 S3 上的表数据文件、访问 Glue 数据目录、使用 DynamoDB 进行锁管理等。

该 BOM 通过 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 中的 `awssdk-bom` 条目统一管理所有 AWS SDK 模块的版本，确保各模块之间版本兼容。从 2.42.4 到 2.42.8 属于补丁版本（semver-patch）升级，Dependabot 标注为 `update-type: version-update:semver-patch`，意味着只包含向后兼容的缺陷修复和小改进，不会引入破坏性 API 变更。升级后可获取 AWS SDK 在 2.42.5 至 2.42.8 之间累积的缺陷修复，例如 S3 客户端行为改进、稳定性修复等。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，将其从 `2.42.4` 更新为 `2.42.8`。由于使用 BOM 管理方式，所有依赖该 BOM 的 AWS SDK 模块版本会随之统一对齐到 2.42.8。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK BOM 版本从 2.42.4 升级到 2.42.8。

**工作逻辑**：
该文件是 Gradle 版本目录，集中声明项目所有依赖的版本号。本次仅修改一行：

```
-awssdk-bom = "2.42.4"
+awssdk-bom = "2.42.8"
```

`awssdk-bom` 通过 `software.amazon.awssdk:bom` 导入到依赖管理中，统一控制如 `s3`、`sts`、`glue`、`dynamodb`、`kms` 等多个 AWS SDK 模块的版本。同文件中还有 `awssdk-s3accessgrants = "2.4.1"` 等独立版本，本次未受影响。升级后，Iceberg 在与 AWS 服务交互时将使用更新版本的 SDK，获得 2.42.5–2.42.8 期间的修复与改进。

## 总结

本次为 AWS SDK for Java BOM 的补丁级升级，将版本从 2.42.4 提升到 2.42.8，用于获取累积的缺陷修复和稳定性改进。由于使用 BOM 统一管理，所有 AWS SDK 模块版本同步对齐，对 Iceberg 与 AWS 服务的集成能力无破坏性影响。
