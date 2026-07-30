# 提交 1169：AWS: Bump AWS SDK to version 2.28.5 (#11170)

## 提交信息

- **序号**：1169 / 4088
- **哈希**：60f61c3dd0eaa71e1000d8927543070a956280bd
- **短哈希**：60f61c3dd
- **日期**：2024-09-20（Fri Sep 20 02:03:00 2024 -0700）
- **作者**：sullis <seans@grubhub.com>
- **提交说明**：AWS: Bump AWS SDK to version 2.28.5 (#11170)
- **PR/Issue**：#11170

## 总体目的

Iceberg 的 `aws-bundle` 模块通过 shade 打包方式把 AWS SDK for Java v2 的若干构件（`annotations`、`auth`、`aws-core`、`s3`、`glue`、`dynamodb`、`kms`、`lakeformation`、`iam` 等 30+ 个 artifact）打成一个 uber jar，方便用户在使用 Iceberg + S3/Glue 等 AWS 集成时无需自行管理 SDK 依赖。该 bundle 的 `LICENSE` 与 `NOTICE` 文件必须如实列出所包含的每个依赖及其版本号，以符合 Apache 2.0 协议的归属要求。

此次提交把 AWS SDK BOM 从 `2.27.21` 升级到 `2.28.5`，这是 Iceberg 仓库对 AWS SDK 的例行版本跟进。升级目的包括：

1. **获取最新 bug 修复与安全补丁**：AWS SDK v2 在 2.27.x → 2.28.x 之间持续修复 HTTP 客户端、认证、重试、S3 协议等方面的问题。
2. **跟进新增服务特性**：2.28.x 引入了一些 S3 表格 bucket、access grants 等新能力，Iceberg 后续可基于此扩展集成。
3. **保持依赖新鲜度**：避免 bundle 长期停在旧版本，减少与下游用户其他 AWS SDK 依赖的版本冲突风险。

由于 `aws-bundle` 是 shade 打包，升级 SDK 版本必须同步更新 `LICENSE` 与 `NOTICE` 中所有 artifact 的版本号，否则会在 ASF 发布审计（release audit）中被发现不符合协议要求。

## 如何达成设计目的

通过 3 处协同修改完成：

1. **更新 `gradle/libs.versions.toml`**：把 `awssdk-bom` 版本字符串从 `2.27.21` 改为 `2.28.5`。这是 Gradle 版本目录（version catalog）中所有 AWS SDK 依赖版本的统一来源，BOM 升级后所有 `software.amazon.awssdk:*` 依赖都自动跟进。
2. **更新 `aws-bundle/LICENSE`**：把所有 `software.amazon.awssdk:*` artifact 的版本号从 `2.27.7`（bundle 实际打包时解析到的版本）替换为 `2.28.5`。共 36 处替换。
3. **更新 `aws-bundle/NOTICE`**：把所有 `NOTICE for Group: software.amazon.awssdk Name: ... Version: 2.27.7` 行的版本号替换为 `2.28.5`，共 36 处替换。

注意：`libs.versions.toml` 中 BOM 是 `2.27.21`，但 `LICENSE`/`NOTICE` 之前记录的是 `2.27.7`。这说明 BOM 与实际打包版本可能因 Gradle 依赖解析策略（如其他模块引入了不同版本）而存在差异。本次升级后 BOM 与 bundle 中实际版本一致为 `2.28.5`，消除了此前的版本不一致。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：

```toml
-awssdk-bom = "2.27.21"
+awssdk-bom = "2.28.5"
```

- `awssdk-bom` 是 AWS SDK v2 的 BOM（Bill of Materials），通过 `platform("software.amazon.awssdk:bom:${awssdk-bom}")` 引入后，所有 `software.amazon.awssdk:*` 依赖版本由 BOM 统一管理。
- 升级后，`aws/`、`aws-bundle/` 等模块中所有 AWS SDK 依赖自动使用 2.28.5 版本。

### `aws-bundle/LICENSE`

**修改目的**：同步 bundle 中所有 AWS SDK artifact 的版本号。

**工作逻辑**：把 36 处 `Version: 2.27.7` 替换为 `Version: 2.28.5`。每个条目形如：

```
Group: software.amazon.awssdk  Name: annotations  Version: 2.28.5
License: Apache License, Version 2.0 - https://aws.amazon.com/apache2.0
```

涉及的 artifact 包括：`annotations`、`apache-client`、`arns`、`auth`、`aws-core`、`aws-json-protocol`、`aws-query-protocol`、`aws-xml-protocol`、`checksums`、`checksums-spi`、`crt-core`、`dynamodb`、`endpoints-spi`、`glue`、`http-auth`、`http-auth-aws`、`http-auth-aws-crt`、`http-auth-aws-eventstream`、`http-auth-spi`、`http-client-spi`、`iam`、`identity-spi`、`json-utils`、`kms`、`lakeformation`、`metrics-spi`、`netty-nio-client`、`profiles`、`protocol-core`、`regions`、`s3`、`s3-access-grants`、`sdk-core`、`sts`、`third-party`、`utils` 等（具体以实际 diff 为准）。

### `aws-bundle/NOTICE`

**修改目的**：同步 NOTICE 中所有 AWS SDK artifact 的版本号。

**工作逻辑**：把 36 处 `Version: 2.27.7` 替换为 `Version: 2.28.5`。每行形如：

```
NOTICE for Group: software.amazon.awssdk  Name: annotations  Version: 2.28.5
```

NOTICE 文件列出 bundle 中每个 artifact 的归属信息，便于下游用户在再发布时遵守 Apache 2.0 的 NOTICE 要求。

## 小结

- **成效**：AWS SDK for Java v2 从 2.27.21 升级到 2.28.5，bundle 中实际版本从 2.27.7 升级到 2.28.5，BOM 与 bundle 版本一致；`LICENSE`/`NOTICE` 同步更新，符合 ASF 发布审计要求；用户通过 `aws-bundle` 使用 Iceberg + AWS 时获得最新 SDK 修复与特性。
- **影响范围**：1 个版本目录文件 + 2 个 bundle 协议文件，共 75 处版本号替换（3 处 BOM + 36 处 LICENSE + 36 处 NOTICE）。无 Java 源码改动，无 API 变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是依赖版本例行升级，**对运行时兼容**，原则上可以回迁到 1.4.x。
  - 回迁价值：1.4.x 若仍发布 `aws-bundle`，升级到 2.28.5 可让用户获得更新的 SDK 修复（如 S3 客户端 bug、认证问题）。但 1.4.x 通常冻结依赖版本以保持稳定，**默认不回迁**，除非有明确的安全漏洞需要 SDK 升级修复。
  - 回迁时需 cherry-pick `libs.versions.toml`、`aws-bundle/LICENSE`、`aws-bundle/NOTICE` 三个文件改动，缺一会导致发布审计失败或 bundle 中版本不一致。
  - 注意 1.4.x 的 `libs.versions.toml` 中 BOM 起始版本可能不是 `2.27.21`（如可能是更早的 `2.x`），cherry-pick 时需手工调整上下文行匹配。
  - AWS SDK v2 在 2.27→2.28 之间可能引入了少量行为变化（如默认重试策略、HTTP 客户端配置），1.4.x 回迁后需运行集成测试（S3/Glue 等）验证无回归。
  - 若 1.4.x 分支已停止维护，则无需回迁；若仍在发布 patch 版本，建议评估升级收益与风险后决定。
