# 提交 0642：Build: Bump software.amazon.awssdk:bom from 2.24.5 to 2.25.18

## 提交信息

- **序号**：0642 / 4088
- **哈希**：8e6c08e357a7c6024d146e0add958bf055d8c565
- **短哈希**：8e6c08e35
- **日期**：2024-03-28（Thu Mar 28 11:38:26 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.24.5 to 2.25.18 (#10050)
- **PR/Issue**：#10050

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）依赖版本从 `2.24.5` 升级到 `2.25.18`。

背景动机：
- Iceberg 的 `aws` 模块（S3、DynamoDB、Glue、KMS 等集成）依赖 AWS SDK for Java 2.x。通过引入 `software.amazon.awssdk:bom` 统一管理所有 AWS SDK 子模块的版本，避免版本不一致。
- Dependabot 定期扫描依赖，发现新版本后自动提 PR 升级。这是一次 semver-minor 级别的升级（2.24.x → 2.25.x），通常包含新功能、错误修复和性能改进，向后兼容。
- 保持依赖最新有助于获取安全修复、bug 修复和新特性，同时减少技术债务积累。

## 如何达成设计目的

采用 Gradle 版本目录（Version Catalog）统一管理依赖版本：

1. **集中声明版本**：在 `gradle/libs.versions.toml` 中通过 `awssdk-bom = "版本号"` 声明版本常量。
2. **BOM 引用**：通过 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 定义 BOM 依赖坐标，引用上述版本常量。
3. **单点修改**：升级时只需修改版本常量这一行，所有引用 `awssdk-bom` 的地方（通过 `platform(...)` 引入 BOM 的模块）会自动使用新版本，无需逐模块修改。

Dependabot 识别出 `update-type: version-update:semver-minor`（次版本升级），属于低风险变更，直接修改版本号即可。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.24.5 升级到 2.25.18。

**工作逻辑**：
- 文件第 31 行（diff 上下文显示在 `awaitility = "4.2.1"` 之后）：
  - 修改前：`awssdk-bom = "2.24.5"`
  - 修改后：`awssdk-bom = "2.25.18"`
- 该版本常量被同文件第 82 行的 BOM 依赖定义引用：`awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`。
- 各子模块（如 `iceberg-aws`）通过 `platform(libs.awssdk.bom)` 引入 BOM 后，其声明的 `software.amazon.awssdk:*` 依赖（s3、dynamodb、glue、kms、sts、iam 等）会统一采用 2.25.18 版本。

## 小结

本提交是一次标准的依赖版本升级，由自动化工具完成，改动极小（单行版本号变更）。

成效：
- AWS SDK for Java 升级到 2.25.18，获取 2.24.5 至 2.25.18 之间的所有改进（bug 修复、新 API、性能优化）。
- 通过 BOM 机制保证所有 AWS SDK 子模块版本一致，避免冲突。

影响范围：
- 仅改 `gradle/libs.versions.toml` 一行，不涉及代码逻辑。
- 影响所有依赖 AWS SDK 的模块（主要是 `iceberg-aws` 及其测试）。

回迁到 1.4.x 注意事项：
- 风险极低，可直接回迁。
- 需确认 1.4.x 当前 `awssdk-bom` 版本；若 1.4.x 已有更高版本或已单独升级过，则无需回迁此提交。
- 升级后建议运行 AWS 相关模块测试，确认无 API 不兼容（semver-minor 通常兼容，但需验证 S3 access grants 等子模块行为）。
