# 提交 0253：Build: Bump software.amazon.awssdk:bom from 2.21.29 to 2.21.42 (#9259)

## 提交信息

- **序号**：0253 / 4088
- **哈希**：ce9186f6bc5dc1fdcce96775898e4b3f9e769ded
- **短哈希**：ce9186f6b
- **日期**：2023-12-10 11:05:03 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.29 to 2.21.42 (#9259)
- **PR/Issue**：#9259

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 2.21.29 升级到 2.21.42（一次 semver-patch 升级，但跨度较大，跨过 13 个 patch 版本）。

AWS SDK 是 Iceberg 最核心的云存储与目录后端依赖之一：Iceberg 的 S3 集成（`aws-bundle`、S3FileIO、S3 表操作）、Glue Catalog、DynamoDB 锁管理、KMS 加密等能力都建立在 AWS SDK v2 之上。通过引入 BOM（物料清单），Iceberg 不需要为每一个 AWS SDK 子模块单独声明版本，而是统一由 BOM 锁定所有 `software.amazon.awssdk:*` 工件的兼容版本矩阵，从而避免子模块间版本不一致导致的运行时冲突。

2.21.29 到 2.21.42 是同一 minor 线上的 patch 升级，覆盖了 S3、STS、Glue、DynamoDB、KMS 等多个客户端的 bug 修复与服务端 API 兼容性更新。由于 Iceberg 的 S3/Glue 集成在生产环境被广泛使用，保持 AWS SDK 为较新的 patch 版本对连接稳定性、重试逻辑、签名与区域处理都有正向意义。

## 如何达成设计目的

设计思路简单：仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 这一项的版本字符串，从 `2.21.29` 改为 `2.21.42`。因为使用 BOM 模式，所有 `software.amazon.awssdk:*` 子模块的版本都由该 BOM 统一解析，无需逐个修改。改动规模为 1 个文件、1 行。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK v2 BOM 的锁定版本从 2.21.29 提升至 2.21.42。

该文件是 Iceberg Gradle 构建的依赖版本目录（TOML 格式）。`awssdk-bom = "2.21.x"` 这一项以 BOM 形式被引入，下游所有引用 `software.amazon.awssdk:s3`、`software.amazon.awssdk:sts`、`software.amazon.awssdk:glue` 等子工件的模块都会统一获得 BOM 中声明的对应版本。本次仅把这一行从 2.21.29 改为 2.21.42，跨过 13 个 patch 版本，主要包含 AWS SDK 团队在这段时间内的累积修复与服务端兼容性更新。其余依赖项保持不变。

## 小结

该提交通过升级 AWS SDK for Java v2 BOM 至 2.21.42，让 Iceberg 的 S3/Glue/DynamoDB/KMS 等 AWS 集成获得最新的 patch 修复，属于关键的云后端依赖维护。
