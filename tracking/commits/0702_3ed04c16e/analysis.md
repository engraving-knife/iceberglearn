# 提交 0702：升级 AWS SDK BOM 至 2.25.35

## 提交信息
- **序号**：0702 / 4088
- **哈希**：3ed04c16ec3d89a95ef35d33b0f8a088d464ece8
- **短哈希**：3ed04c16e
- **日期**：2024-04-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.31 to 2.25.35 (#10192)
- **PR/Issue**：#10192

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）版本从 `2.25.31` 升级到 `2.25.35`，属于一个 patch 版本级别的依赖升级。

AWS SDK BOM 用于统一管理 AWS SDK 各模块（如 S3、DynamoDB、Kinesis、Glue 等）的版本，确保各模块版本兼容。Iceberg 项目通过 `gradle/libs.versions.toml` 中的 `awssdk-bom` 属性引用该 BOM，并在 `s3`、`glue`、`dynamodb` 等 catalog 实现及 `kafka-connect` 等模块中使用 AWS SDK 提供的客户端。

Dependabot 的例行升级目的在于：获取上游 bug 修复、安全补丁和小幅改进，保持依赖的最新稳定状态，避免长期不升级导致的版本债。从 `2.25.31` 到 `2.25.35` 跨越 4 个 patch 版本，属于低风险升级。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本属性即可。Gradle 的版本目录（Version Catalog）机制会自动将该版本应用到所有引用 `awssdk-bom` 的位置，无需修改其他文件。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 `awssdk-bom` 版本从 `2.25.31` 升级到 `2.25.35`。
**工作逻辑**：版本目录中 `awssdk-bom = "2.25.31"` 改为 `awssdk-bom = "2.25.35"`。该属性通过 `platform("software.amazon.awssdk:bom:${awssdk-bom}")` 引入到依赖管理中，所有 AWS SDK 模块依赖会自动使用 BOM 中指定的版本。

## 小结
- **成效**：成功达成目的，完成 AWS SDK BOM 的 patch 版本升级。
- **影响范围**：影响所有使用 AWS SDK 的模块，包括 `aws`、`aws-bundle`、`s3`、`glue`、`dynamodb`、`kafka-connect` 等，但因是 BOM 管理的 patch 升级，API 兼容，运行时行为变化极小。
- **回迁到 1.4.x 的注意事项**：可直接回迁。若 1.4.x 分支对 AWS SDK 版本有特殊锁定（如因兼容性测试），需确认升级后集成测试通过。注意与 `awssdk-s3accessgrants` 等相关依赖的版本兼容性。
