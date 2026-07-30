# 提交 1706：Build: Bump software.amazon.awssdk:bom from 2.30.11 to 2.30.16 (#12208)

## 提交信息

- **序号**：1706 / 4088
- **哈希**：2af78e21357cf1bfa82a70dee8b448742ff59473
- **短哈希**：2af78e213
- **日期**：2025-02-10（Mon Feb 10 07:38:14 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.30.11 to 2.30.16 (#12208)
- **PR/Issue**：#12208

## 总体目的

Dependabot 自动升级提交。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的 BOM（Bill of Materials），用于统一管理所有 AWS SDK 模块的版本。Iceberg 的 `aws` 模块（S3FileIO、DynamoDB 等）依赖此 BOM。本提交把 BOM 版本从 `2.30.11` 升级到 `2.30.16`（patch 级），获取 AWS SDK 的最新 bug 修复与安全补丁。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `awssdk-bom = "2.30.11"` 改为 `awssdk-bom = "2.30.16"`。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1/-1 行）

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：修改版本变量定义，所有通过 `platform(libs.awssdk.bom)` 引用 AWS SDK 的模块自动使用新版本。

## 小结

- **成效**：升级 AWS SDK 到 2.30.16，获取 bug 修复与安全补丁。
- **影响范围**：仅构建配置，无源代码变更。影响 `aws` 模块及所有使用 S3FileIO 等AWS 集成的场景。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯版本号升级。需确认 1.4.x 的 AWS SDK 版本，直接升级即可。AWS SDK patch 升级通常向后兼容。
