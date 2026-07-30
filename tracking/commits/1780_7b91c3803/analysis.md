# 提交 1780：Build: Bump software.amazon.awssdk:bom from 2.30.21 to 2.30.26 (#12379)

## 提交信息

- **序号**：1780 / 4088
- **哈希**：7b91c3803310a4c49edb375128f9fdfeb7f01752
- **短哈希**：7b91c3803
- **日期**：2025-02-24 14:33:06 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.30.21 to 2.30.26 (#12379)
- **PR/Issue**：#12379

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java BOM 从 2.30.21 版本升级到 2.30.26 版本。AWS SDK BOM 是一个物料清单，用于统一管理 AWS SDK 各模块（如 S3、DynamoDB、Glue 等）的版本。Iceberg 项目在 AWS 集成模块（S3 文件 IO、Glue 目录、DynamoDB 锁管理等）中广泛使用 AWS SDK。此次升级为补丁版本升级（semver-patch），获取 AWS SDK 的 bug 修复和改进。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 awssdk-bom 的版本号来完成升级。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：将 `awssdk-bom = "2.30.21"` 修改为 `awssdk-bom = "2.30.26"`。

## 小结

- **成效**：将 AWS SDK for Java BOM 升级到 2.30.26 补丁版本。
- **影响范围**：影响所有使用 AWS SDK 的模块（S3、Glue、DynamoDB 等）。BOM 补丁版本升级通常向后兼容。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。补丁版本升级风险低，回迁时需确认 AWS 相关集成测试通过。无前置依赖。
