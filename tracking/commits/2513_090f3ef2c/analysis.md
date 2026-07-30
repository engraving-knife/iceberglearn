# 提交 2513：Build: Bump software.amazon.awssdk:bom from 2.32.19 to 2.32.24 (#13843)

## 提交信息

- **序号**：2513 / 4088
- **哈希**：090f3ef2c3bd6270d940db8dc963b7ee09f407b1
- **短哈希**：090f3ef2c
- **日期**：2025-08-17 22:34:22 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.32.19 to 2.32.24 (#13843)
- **PR/Issue**：#13843

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.32.19 升级到 2.32.24。

`software.amazon.awssdk:bom` 是 AWS SDK for Java 2.x 的 BOM（物料清单），用于统一管理 AWS SDK 所有模块的版本。Iceberg 项目使用 AWS SDK 来支持 S3、DynamoDB 等 AWS 服务的集成，包括 S3 文件系统访问、Glue Catalog、DynamoDB 锁管理等核心功能。

此次升级属于补丁版本更新（semver-patch），通常包含 bug 修复、性能改进和安全补丁。

## 如何达成设计目的

Dependabot 自动检测到 AWS SDK BOM 有新版本发布，在 `gradle/libs.versions.toml` 中将版本号从 2.32.19 更新为 2.32.24。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 AWS SDK BOM 依赖版本号。

**工作逻辑**：将 Gradle 版本目录文件中 `awssdk` 的版本号从 `2.32.19` 改为 `2.32.24`，使所有引用 AWS SDK 模块的项目自动使用新版本。

## 总结

这是一个常规的依赖维护提交，将 AWS SDK for Java 升级到最新补丁版本，获取 bug 修复和安全补丁。由于 AWS SDK 是 Iceberg 与 AWS 服务集成的核心依赖，保持其最新版本有助于确保与 AWS 服务的兼容性和稳定性。
