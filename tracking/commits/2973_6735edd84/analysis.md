# 提交 2973：Build: Bump software.amazon.awssdk:bom from 2.39.5 to 2.40.3 (#14788)

## 提交信息

- **序号**：2973 / 4088
- **哈希**：6735edd8494257af8cbfe92f38072136a2f97715
- **短哈希**：6735edd84
- **日期**：2025-12-06
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.39.5 to 2.40.3 (#14788)
- **PR/Issue**：#14788

## 总体目的

这是 Dependabot 自动生成的依赖升级。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的物料清单（BOM），Iceberg 通过导入该 BOM 统一对齐所有 AWS SDK v2 制品（S3、Glue、KMS、STS、S3 Access Grants 等）的版本，是 `aws` 模块及 AWS 相关集成的核心依赖。Dependabot 将 BOM 版本从 `2.39.5` 升级到 `2.40.3`，使 Iceberg 依赖的全部 AWS SDK v2 制品整体跨入 2.40.x 系列。本次升级属于 `semver-minor`（次版本）升级，依据 AWS SDK v2 在 2.x 内保持兼容的约定，预期包含新服务/新 API 与缺陷修复，向后兼容，但次版本升级仍可能伴随行为细微调整，需要 CI（尤其是 AWS 集成测试）验证。

值得注意的背景是：本提交与序列中较早的 2966（AWS HTTP 客户端连接池复用）改动同属 AWS 集成领域，但二者相互独立——2966 改的是 Iceberg 自身对 `SdkHttpClient` 的缓存复用逻辑，本提交只是把 SDK 整体版本对齐到更新的 2.40.3。

## 如何达成设计目的

Dependabot 仅修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本变量的值，BOM 机制会自动把所有 AWS SDK v2 制品的版本对齐到 2.40.3。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 AWS SDK v2 BOM 版本变量从 `2.39.5` 升级到 `2.40.3`。

**工作逻辑**：
在 `[versions]` 区段将 `awssdk-bom = "2.39.5"` 改为 `awssdk-bom = "2.40.3"`。该变量被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，并通过 platform/BOM 依赖机制统一约束所有 `software.amazon.awssdk:*` 制品版本。一次升级即可让 S3、Glue、KMS、STS、S3 Access Grants 等全部 AWS SDK v2 制品同步进入 2.40.3，避免单独制品版本不一致。`awssdk-s3accessgrants` 等独立版本变量的制品不在本次升级范围内。

## 总结

本提交是 Dependabot 对 AWS SDK v2 BOM 的次版本升级（2.39.5 → 2.40.3），通过 BOM 机制统一对齐 Iceberg 依赖的全部 AWS SDK v2 制品版本，服务于 `aws` 模块及相关集成。属于次版本升级，预期带来向后兼容的新功能与修复，是保持 AWS 集成跟上 SDK 上游发布的常规维护。
