# 提交 1450：Build: Bump software.amazon.awssdk:bom from 2.29.20 to 2.29.23 (#11683)

## 提交信息

- **序号**：1450
- **哈希**：233364044e058799b8e1882f1a0282849ef8b077
- **短哈希**：233364044
- **日期**：2024-12-02（Mon Dec 2 06:22:29 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.20 to 2.29.23 (#11683)
- **PR/Issue**：#11683
- **协同作者**：dependabot[bot] <support@github.com>

## 总体目的

这是 Dependabot 自动生成的依赖版本升级 PR。AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.29.20` 升级到 `2.29.23`，属于 semver patch 级别的小版本升级。

AWS SDK BOM 在 Iceberg 中作为 `iceberg-aws` 模块及其相关测试的依赖版本统管器（通过 `libs.versions.toml` 中的 `awssdk-bom` 版本引用 + `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 库定义），用于统一管理所有 AWS SDK v2 子模块（如 s3、sts、kms、dynamodb 等）的版本，避免子模块间版本不一致。

patch 级别升级通常包含 bug 修复和小的功能改进，不引入破坏性 API 变更。本次升级跨 3 个 patch 版本（2.29.20 → 2.29.21 → 2.29.22 → 2.29.23），累积了 AWS SDK 团队在这段时间内的修复。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，将其从 `2.29.20` 改为 `2.29.23`。由于该版本通过 `version.ref` 被 `awssdk-bom` 库定义引用，所有依赖 `awssdk-bom` 的模块会自动使用新版本，无需逐个修改各模块的 `build.gradle`。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK v2 BOM 版本。

**工作逻辑**：

```toml
# 修改前：
awssdk-bom = "2.29.20"

# 修改后：
awssdk-bom = "2.29.23"
```

该版本号通过 `version.ref = "awssdk-bom"` 被 `software.amazon.awssdk:bom` 库定义引用，进而被 `iceberg-aws` 等模块的 `build.gradle` 通过 `platform libs.awssdk.bom` 导入，统一约束所有 AWS SDK v2 子模块版本。

## 小结

- **成效**：将 AWS SDK for Java v2 BOM 从 2.29.20 升级到 2.29.23，获取最新的 bug 修复和小改进，保持依赖最新。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 1 行，无代码变更。
- **回迁到 1.4.x 的注意事项**：依赖版本升级，**可以回迁**但需谨慎：
  1. **版本一致性**：1.4.x 上 `awssdk-bom` 的当前版本可能与 main 不同（1.4.x 可能还在用更老的 2.x 版本）。回迁前需确认 1.4.x 上的 `awssdk-bom` 版本是否就在 2.29.x 系列。如果 1.4.x 上是更老的版本（如 2.25.x），直接跳到 2.29.23 可能引入兼容性问题，建议按 1.4.x 自己的节奏升级。
  2. **测试验证**：回迁后需运行 `iceberg-aws` 模块的完整测试套件（特别是 S3FileIO、GlueCatalog 等相关测试），确保新版本不破坏现有功能。
  3. **License/NOTICE**：AWS SDK patch 升级通常不改变 license（仍为 Apache 2.0），但若 1.4.x 有严格的 LICENSE/NOTICE 审查流程，需确认版本号是否需要在 LICENSE 中更新（通常 patch 升级不需要，因为 LICENSE 中列的是 major.minor 级别的组件声明）。
  4. **与 #1442 的关系**：#1442 在 `open-api/LICENSE` 中列出了 AWS SDK 2.29.6 的组件清单。本升级到 2.29.23 不影响 `open-api/LICENSE`（因为 #1442 引入的是 `iceberg-aws-bundle` 的传递依赖，其版本由 bundle 决定，不一定与 `awssdk-bom` 一致）。但如果 1.4.x 上 `iceberg-aws-bundle` 的版本与 `awssdk-bom` 联动，需一并检查。
  5. **Dependabot 自动化**：如果 1.4.x 也启用了 Dependabot，类似升级会自动生成 PR，无需手动回迁。
