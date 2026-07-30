# 提交 1136：Build: Bump software.amazon.awssdk:bom from 2.27.12 to 2.27.21 (#11098)

## 提交信息

- **序号**：1136 / 4088
- **哈希**：3fe4f420dd4fb4c2a0ea6c732e18c1a2889f74ee
- **短哈希**：3fe4f420d
- **日期**：2024-09-09（Mon Sep 9 10:44:36 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.27.12 to 2.27.21 (#11098)
- **PR/Issue**：#11098

## 总体目的

Iceberg 通过 `software.amazon.awssdk:bom`（AWS SDK for Java v2 的 BOM，即物料清单）统一管理所有 AWS SDK 模块的版本，保证 `s3`、`sts`、`dynamodb`、`glue`、`kms`、`s3accessgrants` 等模块版本一致。AWS SDK v2 在 2.27.x 线上持续发布 patch 版本，包含 bug 修复和服务模型更新。

本提交由 dependabot 自动发起，把 `awssdk-bom` 从 `2.27.12` 升到 `2.27.21`（同一 minor 线内的 patch 升级，跨 9 个 patch 版本）。升级后所有依赖该 BOM 的 AWS SDK 模块都会同步升级到 2.27.21，获取累积的 bug 修复与改进。属于常规依赖滚动升级。

注意：本提交仅改 BOM 版本号一处；同仓库内 `awssdk-s3accessgrants` 是单独版本（`2.0.0`），不受此 BOM 影响。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 这一个版本变量，从 `"2.27.12"` 改为 `"2.27.21"`。下游所有 AWS SDK 模块通过 BOM 引入，版本由 BOM 统一决定。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：在依赖版本目录中，把：

```toml
awssdk-bom = "2.27.12"
```

改为：

```toml
awssdk-bom = "2.27.21"
```

该文件是 Gradle 版本目录，`awssdk-bom` 别名指向 AWS SDK v2 的 BOM 坐标。所有 AWS SDK 子模块（`s3`、`sts`、`dynamodb`、`glue`、`kms` 等）在 `libs.versions.toml` 的 `[libraries]` 段中以 `module = "software.amazon.awssdk:s3"` 形式声明、不带版本，运行时由 BOM 决定版本。因此一处改动即可让全部 AWS SDK 模块同步升级。同 minor 线内 patch 升级，向后兼容。

## 小结

- **成效**：AWS SDK for Java v2 全模块同步升级到 2.27.21，获取 2.27.13–2.27.21 累积的 bug 修复与服务模型更新，保持依赖健康度。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本号变更，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是依赖 patch 升级，风险较低。**可选回迁**——若 1.4.x 仍在维护并发布，且回迁能解决某个具体的 AWS SDK bug，则值得回迁；否则可不动。回迁前需确认 1.4.x 的 `awssdk-s3accessgrants` 等独立版本与 2.27.21 兼容（通常同 minor 内兼容）。AWS SDK 是 Iceberg 与 S3/Glue/DynamoDB 等交互的运行时依赖，回迁会影响发布产物的传递依赖版本，需做基本回归（至少 S3FileIO、GlueCatalog 的读写测试）。
