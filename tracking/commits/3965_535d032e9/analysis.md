# 提交 3965：Build: Bump software.amazon.awssdk:bom from 2.46.10 to 2.46.15 (#16989)

## 提交信息

- **序号**：3965 / 4088
- **哈希**：535d032e964d4c2297a91823fb2964b3aa97cc0a
- **短哈希**：535d032e9
- **日期**：2026-06-29 09:51:47 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.46.10 to 2.46.15 (#16989)
- **PR/Issue**：#16989

## 总体目的

这是 Dependabot 自动升级 AWS SDK for Java 的 BOM（Bill of Materials）从 2.46.10 到 2.46.15 的提交。AWS SDK BOM 用于统一管理所有 AWS SDK 模块的版本。升级类型为补丁版本升级（`version-update:semver-patch`），包含 bug 修复和安全补丁。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）中的 `awssdk-bom` 版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
```toml
awssdk-bom = "2.46.15"  # 原为 "2.46.10"
```

## 总结

常规依赖升级，将 AWS SDK BOM 从 2.46.10 升级到 2.46.15，影响所有依赖 AWS SDK 的模块（S3、DynamoDB、KMS 等），属于补丁版本升级，风险较低。
