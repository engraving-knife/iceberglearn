# 提交 1195：Build: Bump software.amazon.awssdk:bom from 2.28.5 to 2.28.11 (#11229)

## 提交信息

- **序号**：1195 / 4088
- **哈希**：570af254c0867779e739372258212b67a369d7ae
- **短哈希**：570af254c
- **日期**：2024-09-30（Mon Sep 30 12:46:42 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.28.5 to 2.28.11 (#11229)
- **PR/Issue**：#11229

## 总体目的

Iceberg 与 AWS S3、Glue、DynamoDB 等服务集成时依赖 AWS SDK for Java v2，并通过 BOM（Bill of Materials）`software.amazon.awssdk:bom` 统一管理所有 AWS SDK 工件版本。本提交由 dependabot 触发，将 AWSSDK BOM 从 `2.28.5` 升级到 `2.28.11`，跨 6 个 patch 版本，目的是同步 AWS SDK 的缺陷修复与服务端 API 适配，保持与 AWS 最新行为一致。

BOM 升级后，所有通过 `libs.awssdk.bom` 引入的 AWS 工件（如 `s3`、`sts`、`glue`、`dynamodb` 等）会自动采用 2.28.11 的版本。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，从 `2.28.5` 改为 `2.28.11`。Gradle 通过 BOM 机制（`platform`）将版本统一应用到所有 AWS SDK 工件，无需逐个声明版本号。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK for Java v2 的 BOM 版本从 2.28.5 升级到 2.28.11。

**工作逻辑**：找到 `awssdk-bom = "2.28.5"` 这一行，改为：

```toml
awssdk-bom = "2.28.11"
```

该声明同时影响所有 AWS SDK 模块（S3、STS、Glue、DynamoDB、IAM 等）以及 `awssdk-s3accessgrants` 之外的所有工件。`awssdk-s3accessgrants` 在 catalog 中独立声明为 `2.0.0`，不受此次升级影响。其他依赖（`azuresdk-bom`、`caffeine` 等）也未改动。

## 小结

- **成效**：AWS SDK 升级到 2.28.11，获取 2.28.6 至 2.28.11 间的累积修复与改进；通过 BOM 机制一处升级覆盖所有 AWS 工件。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更；无源代码改动。
- **回迁到 1.4.x 的注意事项**：1.4.x 若仍使用较旧的 AWSSDK 版本，回迁此升级通常**风险较低但需测试**。AWS SDK patch 版本一般向后兼容，但建议在 1.4.x 上跑一遍 S3、Glue 等集成测试确认无回归。同时需确认 1.4.x 的 catalog 中 `awssdk-bom` 别名与 main 一致。
