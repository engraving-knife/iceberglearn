# 提交 2309：Build: Bump software.amazon.awssdk:bom from 2.31.63 to 2.31.73 (#13424)

## 提交信息

- **序号**：2309 / 4088
- **哈希**：5634a2b06480dd2573241233b4c6a03d4e0b5d41
- **短哈希**：5634a2b06
- **日期**：2025-07-02 18:17:49 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.63 to 2.31.73 (#13424)
- **PR/Issue**：#13424

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 AWS SDK for Java 的 BOM（Bill of Materials）从版本 2.31.63 升级到 2.31.73，跨越了 10 个 patch 版本。

AWS SDK BOM 是 Iceberg 项目中使用的关键依赖，用于管理 S3 等 AWS 服务的客户端访问。定期升级 SDK 版本可以确保项目获得最新的 bug 修复、安全补丁和功能改进。由于这是一个 semver-patch（语义化版本的补丁级别）升级，理论上不包含破坏性变更，风险较低。

## 如何达成设计目的

通过修改 Gradle 的版本目录文件 `libs.versions.toml`，将 `awssdk-bom` 的版本号从 `2.31.63` 更新为 `2.31.73`。这是标准的依赖升级方式，Gradle 会自动解析并应用所有关联的 AWS SDK 子模块版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom = "2.31.63"` 修改为 `awssdk-bom = "2.31.73"`。该文件是 Gradle 版本目录，集中管理项目中所有依赖的版本号。通过 BOM（Bill of Materials）机制，所有 AWS SDK 相关子模块（如 s3、kms、sts、dynmodb 等）的版本会自动对齐到 BOM 指定的版本。

## 总结

这是一个常规的依赖维护提交，通过 dependabot 自动升级 AWS SDK 版本以获取最新的 bug 修复和安全补丁。变更范围极小，仅涉及版本号修改，对项目功能无直接影响。
