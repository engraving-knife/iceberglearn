# 提交 0133：Build: Bump software.amazon.awssdk:bom from 2.21.10 to 2.21.15 (#8983)

## 提交信息

- **序号**：0133 / 4088
- **哈希**：f3e50717149b61d4701c2691be50cd2442afc967
- **短哈希**：f3e507171
- **日期**：2023-11-06 12:29:30 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.10 to 2.21.15 (#8983)
- **PR/Issue**：#8983

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 `2.21.10` 升级到 `2.21.15`。AWS SDK BOM 在 Iceberg 中用于统一管理所有 `software.amazon.awssdk:*` 子模块（如 S3、Glue、KMS、STS 等客户端）的版本，主要服务于 `aws-bundle`、`aws` 模块以及 S3/Glue catalog 的实现。

由于 `2.21.10` → `2.21.15` 属于同一 minor 系列（2.21.x）内的 patch 级别升级，Dependabot 标注 `update-type: version-update:semver-patch`，按 AWS SDK 的版本策略，这类升级通常只包含 bug 修复、安全补丁和小幅性能改进，不引入破坏性 API 变更。因此风险较低，是常规的依赖维护。

## 如何达成设计目的

与 0132 类似，目的只是在 version catalog 中将 `awssdk-bom` 版本号从 `2.21.10` 改为 `2.21.15`。Iceberg 通过 `gradle/libs.versions.toml` 的 `awssdk-bom` 变量集中管理，所有 AWS SDK 子模块的版本引用都跟随该变量，因此一处修改即可同步全部 AWS SDK 制品。

## 修改详情

### [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)

**修改目的**：将 AWS SDK BOM 版本从 2.21.10 提升到 2.21.15。

**工作逻辑**：在 `libs.versions.toml` 中（紧跟 0132 提交刚改动的 `arrow = "14.0.0"` 行之后），把 `awssdk-bom = "2.21.10"` 改为 `awssdk-bom = "2.21.15"`。该变量被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，通过 BOM 传递性地统一所有 `software.amazon.awssdk:*` 子模块版本。改动量为 1 行（1 增 1 删）。

## 小结

Dependabot 自动完成的 AWS SDK BOM patch 级升级，通过 version catalog 单行修改同步所有 AWS SDK 子模块版本，是低风险的常规依赖维护。
