# 提交 0650：将 software.amazon.awssdk:bom 从 2.25.18 升级到 2.25.21

## 提交信息

- **序号**：0650 / 4088
- **哈希**：78128283534f13d7fa5146743c0761c14a06354b
- **短哈希**：781282835
- **日期**：2024-03-31
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.18 to 2.25.21 (#10072)
- **PR/Issue**：PR #10072（Dependabot 自动生成）

## 总体目的

本提交是 Dependabot 自动发起的依赖版本升级，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.25.18` 升级到 `2.25.21`。属于 semver 级别的 patch 升级（2.25.18 → 2.25.21，三个 patch 版本）。

目的是持续跟进 AWS SDK 的补丁更新，获取 bug 修复、安全补丁与小改进，避免依赖长期落后积累风险。AWS SDK BOM 用于统一管理所有 `software.amazon.awssdk:*` 模块（如 s3、sts、dynamodb、kms 等）的版本，确保各模块版本兼容。

## 如何达成设计目的

Dependabot 通过比对 `gradle/libs.versions.toml` 中声明的 `awssdk-bom` 版本与上游 Maven Central 发布的最新版本，发现 2.25.21 可用，于是生成此 PR，仅修改版本号一处。Gradle 的版本目录（version catalog）机制下，所有通过 `libs.awssdk.bom` 引用该 BOM 的地方会自动生效，无需逐模块改动。

BOM 在 `build.gradle` 中以 `platform(libs.awssdk.bom)` 形式消费（compileOnly 与 testImplementation 两个配置），它本身不引入任何 jar，只是为 `software.amazon.awssdk:*` 系列模块提供统一版本约束。因此升级 BOM 即等价于升级所有 AWS SDK 模块的版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `awssdk-bom` 的版本声明从 `2.25.18` 提升到 `2.25.21`。

**工作逻辑**：仅一行变更：

```toml
- awssdk-bom = "2.25.18"
+ awssdk-bom = "2.25.21"
```

该版本号通过 `version.ref = "awssdk-bom"` 被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，进而被 `build.gradle` 的 `platform(libs.awssdk.bom)` 消费。升级后，所有 `software.amazon.awssdk:*` 模块（s3、sts、glue、dynamodb、kms、lakeformation 等，Iceberg 的 `aws` 模块及各集成测试依赖）会统一使用 2.25.21 提供的版本。2.25.18 → 2.25.21 属于同一次版本内的 patch 累积，按 AWS SDK 的版本约定应仅含 bug 修复与向后兼容的小改进，无 API 破坏。

## 小结

- **成效**：例行依赖维护，将 AWS SDK BOM 推进 3 个 patch 版本，获取最新的 bug 修复与安全补丁。改动面极小（1 行），风险极低。
- **影响范围**：影响所有依赖 `software.amazon.awssdk:*` 模块的构建与运行时（主要是 `aws/` 模块以及 S3相关的集成测试）。由于是 patch 升级且通过 BOM 统一管理，理论上无行为变化。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支当前的 `awssdk-bom` 版本（本仓库工作树显示为 `2.20.131`，远低于 main 的 2.25.21）与 main 差距较大，不能直接 cherry-pick 此提交。需要先评估 1.4.x 是否已逐步升级到 2.25.x 系列；若仍停留在 2.20.x，应按 1.4.x 的依赖升级节奏分步推进，而非直接跳到 2.25.21。
  - Dependabot 这类自动升级在维护分支上通常需要人工评估：确认 2.25.21 与 1.4.x 其他依赖（如 Hadoop、Spark、Flink 的 AWS 相关模块）兼容，且不引入新的传递依赖冲突。
  - 升级后建议运行 `aws/` 模块与 S3 相关集成测试（如 `TestS3FileIO`、`TestGlueCatalog`）验证无回归。
  - 此类纯版本号变更无回迁难度，关键是版本兼容性确认。
