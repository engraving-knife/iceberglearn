# 提交 2482：Build: Bump software.amazon.awssdk:bom from 2.32.14 to 2.32.19 (#13778)

## 提交信息

- **序号**：2482 / 4088
- **哈希**：1e4eeecf08adb029e1e8e6d7fdfaf36beb37384e
- **短哈希**：1e4eeecf0
- **日期**：2025-08-10 22:57:30 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.32.14 to 2.32.19 (#13778)
- **PR/Issue**：#13778

## 总体目的

该提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.32.14 升级到 2.32.19，以获取 AWS SDK 的最新补丁修复。

AWS SDK for Java 的 BOM 用于统一管理所有 AWS SDK 模块的版本，确保各模块版本兼容。Iceberg 的 S3 相关模块（如 `aws-bundle`、`s3` 等）依赖 AWS SDK 进行对象存储操作。2.32.19 是一个补丁版本（semver-patch），跨越了 5 个补丁版本（2.32.14 → 2.32.19），可能包含多个 bug 修复和安全补丁。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 版本目录文件中，将 `awssdk-bom` 的版本号从 `2.32.14` 改为 `2.32.19`。这是单行版本号修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：

修改前：
```toml
awssdk-bom = "2.32.14"
```

修改后：
```toml
awssdk-bom = "2.32.19"
```

在 Gradle 版本目录中更新 `awssdk-bom` 的版本号，所有依赖 AWS SDK BOM 的模块在构建时会自动使用新版本管理的 SDK 模块版本。

## 总结

这是一个由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java BOM 从 2.32.14 升级到 2.32.19（补丁版本）。该提交仅修改版本目录中一行版本号配置，获取 AWS SDK 的最新 bug 修复和安全补丁，属于常规的依赖维护工作。
