# 提交 2321：Build: Bump software.amazon.awssdk:bom from 2.31.73 to 2.31.77 (#13475)

## 提交信息

- **序号**：2321 / 4088
- **哈希**：9d917a4d373631719b4dc32bee325c7c652d9e2a
- **短哈希**：9d917a4d3
- **日期**：2025-07-07 09:52:55 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.73 to 2.31.77 (#13475)
- **PR/Issue**：#13475

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM 从版本 2.31.73 升级到 2.31.77。这是提交 2309 升级到 2.31.73 后的后续升级。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `libs.versions.toml`，将 `awssdk-bom` 的版本号从 `2.31.73` 更新为 `2.31.77`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom = "2.31.73"` 修改为 `awssdk-bom = "2.31.77"`。

## 总结

这是一个常规的依赖维护提交，通过 dependabot 自动升级 AWS SDK 版本以获取最新的 bug 修复和安全补丁。变更仅涉及版本号修改。
