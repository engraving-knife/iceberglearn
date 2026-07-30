# 提交 1649：Build: Bump software.amazon.awssdk:bom from 2.29.50 to 2.30.6 (#12109)

## 提交信息

- **序号**：1649 / 4088
- **哈希**：69c7bea3b2b36fa05d497a2454c89ed85e223b53
- **短哈希**：69c7bea3b
- **日期**：2025-01-28（Tue Jan 28 16:29:01 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.50 to 2.30.6 (#12109)
- **PR/Issue**：#12109

## 总体目的

由 Dependabot 自动发起的 AWS SDK for Java v2 BOM 版本升级。`software.amazon.awssdk:bom` 是 AWS SDK v2 的物料清单（BOM），用于统一管理 AWS SDK 各模块（S3、DynamoDB、STS、KMS 等）的版本，Iceberg 的 `aws-bundle` 模块及 S3 相关集成依赖此 BOM。本次从 `2.29.50` 升级到 `2.30.6`（semver minor 升级，跨 2.29.x → 2.30.x），目的是获取 AWS SDK 2.30.x 系列的 bug 修复、性能改进与新服务客户端支持，保持与 AWS 上游同步。属于常规依赖维护。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本有新发布，生成 PR 将版本字符串更新为 `2.30.6`，通过 CI 验证后合入。BOM 升级后，所有通过 BOM 管理的 AWS SDK 模块版本自动跟进。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1 / -1）

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：`awssdk-bom = "2.29.50"` 改为 `awssdk-bom = "2.30.6"`。该版本变量被 Gradle 版本目录引用，统一控制 AWS SDK 各模块依赖版本。

## 小结

- **成效**：AWS SDK 升级到 2.30.6，获取上游修复与改进。
- **影响范围**：仅依赖版本声明，不改产品代码。影响 `aws-bundle` 与所有 S3/AWS 集成模块的运行时依赖版本。AWS SDK 2.30.x 对 2.29.x 保持二进制兼容（同一 minor 语义下 API 稳定），风险低。
- **回迁到 1.4.x 的注意事项**：纯依赖版本变更，回迁安全。需确认 1.4.x 的 AWS 集成代码不依赖 2.29.x 中被移除/变更的 API（2.30.x 作为 minor 升级通常只新增不删除）。CI 回归测试应覆盖 S3 相关用例。注意 `awssdk-s3accessgrants` 等独立版本号的 AWS 相关依赖不受此 BOM 影响，需单独管理。
