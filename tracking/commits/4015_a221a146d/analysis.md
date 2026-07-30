# 提交 4015：Build: Bump software.amazon.awssdk:bom from 2.46.17 to 2.46.21 (#17168)

## 提交信息

- **序号**：4015 / 4088
- **哈希**：a221a146d278d8a81f22e4f46ffd2ff6bade482e
- **短哈希**：a221a146d
- **日期**：2026-07-11 23:57:08 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.46.17 to 2.46.21 (#17168)
- **PR/Issue**：#17168

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 AWS SDK for Java 的 BOM (`software.amazon.awssdk:bom`) 从 2.46.17 升级到 2.46.21。该 BOM 统一管理 AWS SDK 所有模块（S3、Glue、KMS、DynamoDB、STS 等）的版本。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 版本目录中更新 `awssdk-bom` 版本号。属于 patch 版本升级（2.46.17 → 2.46.21），包含多个 patch 版本的 bug 修复和小改进，无破坏性 API 变更。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
```toml
# 修改前
awssdk-bom = "2.46.17"
# 修改后
awssdk-bom = "2.46.21"
```
版本目录统一管理，所有 AWS SDK 模块自动升级到 BOM 指定的版本。

## 总结

这是一次常规的 Dependabot 依赖升级，AWS SDK BOM patch 版本更新（2.46.17 → 2.46.21），影响所有 AWS 相关模块。无功能影响，保持 AWS SDK 最新稳定版本以获取 bug 修复和改进。
