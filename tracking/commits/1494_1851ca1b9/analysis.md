# 提交 1494：Build: Bump software.amazon.awssdk:bom from 2.29.29 to 2.29.34 (#11793)

## 提交信息

- **序号**：1494 / 4088
- **哈希**：1851ca1b9e6753aca4834aaeddf93e4c7504a6c9
- **短哈希**：1851ca1b9
- **日期**：2024-12-16（Mon Dec 16 08:26:19 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.29 to 2.29.34 (#11793)
- **PR/Issue**：#11793

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）从 `2.29.29` 升级到 `2.29.34`（5 个补丁版本的迭代）。AWS SDK BOM 用于统一管理 AWS 相关制品（如 S3、DynamoDB、Glue、STS 等客户端）的版本，确保彼此兼容。

升级补丁版本的目的通常是：

- 获得 AWS SDK 在 2.29.30 ~ 2.29.34 期间修复的 bug，包括 S3 客户端、异步客户端、HTTP 客户端等方面的稳定性改进。
- 获得安全补丁（若有）。
- 保持与最新 AWS 服务端 API 行为一致。

由于是 BOM 升级，Iceberg 中所有 AWS 相关模块（`iceberg-aws`、`iceberg-s3` 等）所引用的 SDK 制品版本会自动同步。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本变量的值，由 `2.29.29` 改为 `2.29.34`。BOM 通过平台依赖机制（platform dependency）将版本传递给所有 AWS SDK 制品，因此只需一处修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.29.29 升级到 2.29.34。

**工作逻辑**：版本目录中 `awssdk-bom = "..."` 声明 BOM 版本，构建脚本通过 `platform("software.amazon.awssdk:bom:${libs.versions.awssdk.bom.get()}")` 或类似方式引入 BOM，随后各 AWS 制品（如 `software.amazon.awssdk:s3`、`software.amazon.awssdk:glue`）不再显式声明版本，由 BOM 统一管理。仅修改这一行即可让所有 AWS SDK 制品升级到 2.29.34。

```toml
- awssdk-bom = "2.29.29"
+ awssdk-bom = "2.29.34"
```

## 小结

- **成效**：AWS SDK for Java 升级到 2.29.34，获得 5 个补丁版本的 bug 修复与稳定性改进；通过 BOM 机制一处升级、全模块同步。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行变更。无源代码逻辑改动，影响的是 AWS 相关模块的运行时与测试期依赖。
- **回迁到 1.4.x 的注意事项**：这是依赖版本升级，无功能变更。1.4.x 作为维护分支，原则上应保持依赖稳定。**一般情况下无需回迁**；但若 1.4.x 使用的 AWS SDK 2.29.29 存在已知影响 Iceberg 的 bug（如 S3 多段上传、IAM 假设角色、S3 Access Grants 等场景的问题），则可考虑回迁。回迁前需确认：
  - 2.29.34 与 1.4.x 时期 Iceberg `iceberg-aws` 代码所用 API 兼容（AWS SDK 2.x 在补丁版本内通常保持兼容，但偶有行为微调，如默认 HTTP 客户端、重试策略等）。
  - 运行 `iceberg-aws` 模块的集成测试（含 S3 mock / MinIO 等）确认无回归。
  - 若 1.4.x 同时使用了 `awssdk-s3accessgrants`（在版本目录中独立声明版本），需注意它与 BOM 版本的协调。
