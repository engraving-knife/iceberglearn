# 提交 1364：Build: Bump software.amazon.awssdk:bom from 2.29.6 to 2.29.9 (#11509)

## 提交信息

- **序号**：1364 / 4088
- **哈希**：981f1ea2f5d11231139007866831288225d154a5
- **短哈希**：981f1ea2f
- **日期**：2024-11-11（Mon Nov 11 14:40:46 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.6 to 2.29.9 (#11509)
- **PR/Issue**：#11509

## 总体目的

Iceberg 通过 AWS SDK（`software.amazon.awssdk`）与 S3、Glue、DynamoDB 等 AWS 服务交互，是核心存储与元数据访问链路的关键依赖。Iceberg 在 `gradle/libs.versions.toml` 中以 BOM（Bill of Materials）方式统一管理 AWS SDK 各子模块的版本，确保所有 AWS SDK 组件版本一致、相互兼容。

本提交由 Dependabot 自动生成，将 `awssdk-bom` 从 2.29.6 升级到 2.29.9（patch 版本升级，跨 3 个 patch 版本），目的是跟进 AWS SDK 上游修复（通常含 bug 修复、性能改进、安全补丁或对新 AWS 服务的支持），保持 SDK 依赖新鲜度。

## 如何达成设计目的

Dependabot 自动扫描 `gradle/libs.versions.toml` 中锁定的 `awssdk-bom` 版本，发现上游 2.29.9 发布后，自动创建 PR 将版本号从 `2.29.6` 改为 `2.29.9`。Gradle 构建时通过 BOM 机制，所有 AWS SDK 子模块（如 s3、sts、glue 等）会统一使用 2.29.9 提供的兼容版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.29.6"` 改为 `awssdk-bom = "2.29.9"`。该文件是 Gradle 版本目录（Version Catalog），统一声明项目所有依赖版本。`awssdk-bom` 这一变量被项目中所有 AWS SDK 相关依赖引用，改一处即统一升级所有 AWS SDK 组件。

```diff
-awssdk-bom = "2.29.6"
+awssdk-bom = "2.29.9"
```

文件中其他 AWS 相关依赖（如 `awssdk-s3accessgrants = "2.3.0"`）保持不变，因为它们是独立的访问授权插件，不受 BOM 版本约束。

## 小结

- **成效**：AWS SDK BOM 升级至 2.29.9，所有 AWS SDK 子模块（S3、Glue、STS 等）统一跟进上游 patch 修复，可能包含 bug 修复与安全补丁。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行变更。但该变量被广泛引用，实际影响所有依赖 AWS SDK 的模块（core、aws、s3、glue 等）的运行时依赖版本。
- **回迁到 1.4.x 的注意事项**：AWS SDK 是 Iceberg 与云存储交互的核心依赖，1.4.x 若需修复与 AWS SDK 相关的 bug 或安全问题，可考虑回迁该升级（patch 版本，向后兼容，风险较低）。但需注意 1.4.x 已发布版本通常锁定依赖版本不再滚动升级，除非有明确的安全或 bug 修复需求。建议在 1.4.x 遇到 AWS SDK 相关问题时再回迁，否则**非必需回迁**。回迁时只需同步该行版本号变更，并验证与 S3、Glue 等服务的集成测试。
