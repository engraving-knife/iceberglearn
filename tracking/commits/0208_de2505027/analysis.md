# 提交 0208：Build: Bump software.amazon.awssdk:bom from 2.21.26 to 2.21.29 (#9154)

## 提交信息

- **序号**：0208 / 4088
- **哈希**：de2505027e82962fc7c48623c9e1a1e85a596f1b
- **短哈希**：de2505027
- **日期**：2023-11-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.26 to 2.21.29 (#9154)
- **PR/Issue**：#9154

## 总体目的

AWS SDK for Java v2 是 Apache Iceberg 对接 S3、Glue、DynamoDB（锁表）等 AWS 服务的核心客户端依赖。Iceberg 通过 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 集中管理依赖，并以 BOM（`software.amazon.awssdk:bom`）的方式统一约束所有 AWS SDK 子模块（如 `s3`、`sts`、`glue`、`dynamodb` 等）的版本，避免各子模块版本错配。本提交由 GitHub Dependabot 自动生成，把 `awssdk-bom` 版本号从 `2.21.26` 升级到 `2.21.29`，跨越三个 patch 版本（2.21.27、2.21.28、2.21.29）。

这是一次纯依赖维护升级，目的是把 AWS SDK 推进到最新的 patch 版本，及时纳入上游对 SDK 的 bug 修复、服务模型更新以及潜在的小幅 API 行为修正（如请求重试、HTTP 客户端底层修正、各服务模型字段微调）。Dependabot 把依赖标记为 `direct:production`、更新类型 `version-update:semver-patch`，即按 SemVer 应为向后兼容升级。对 Iceberg 演进的意义在于持续保持与 AWS 服务最新的兼容性、避免因 SDK 偏旧而暴露于已知 bug 之下。

## 如何达成设计目的

整体设计是一次单行版本号替换：在 `gradle/libs.versions.toml` 中把 `awssdk-bom = "2.21.26"` 改为 `awssdk-bom = "2.21.29"`。版本目录里 `awssdk-bom` 既定义了 BOM 的版本字符串（被其它各 AWS SDK 子模块通过 `version.ref` 引用），也通过 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 暴露为可被 `platform(...)` 引入的 BOM。改这一个字段后，所有 AWS SDK 子模块的版本会被 BOM 一并拉到 2.21.29 对应版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK for Java v2 的 BOM 版本从 2.21.26 升到 2.21.29，让所有 AWS SDK 子模块同步跟随 patch 修复。

**工作逻辑**：

`gradle/libs.versions.toml` 是 Iceberg 使用的 Gradle 版本目录，集中声明所有第三方依赖的版本与坐标。其中与 AWS SDK 相关的两行是：

```toml
awssdk-bom = "2.21.29"                                              # 版本字符串
...
awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }  # BOM 坐标
```

BOM（Bill of Materials）是 Maven/Gradle 的一种依赖管理机制：引入 `software.amazon.awssdk:bom:2.21.29` 后，所有在该 BOM 中声明的 AWS SDK 子模块（`s3`、`sts`、`glue`、`dynamodb`、`kms` 等）都会自动采用 BOM 中固定的版本，调用方不需要为每个子模块单独写版本号。Iceberg 在 `aws` 模块和 `aws-bundle`（ shaded bundle）等多处通过 `libs.platform.awssdk-bom` 引入该 BOM。

本次 diff 只改一行：

```diff
-awssdk-bom = "2.21.26"
+awssdk-bom = "2.21.29"
```

从 2.21.26 → 2.21.29 是三个 patch 版本，按 AWS SDK v2 的发布节奏通常包含：服务模型（service model）更新以反映 AWS 最新 API 字段、HTTP 客户端与重试逻辑的小幅修正、若干内部 bug 修复。因为是 patch 升级，Iceberg 调用代码无需任何改动，依赖通过版本目录自动传递生效。这种依赖跟随是 Iceberg 在多云存储抽象（S3 等）下保持兼容性的常规手段。

## 小结

本提交是 AWS SDK 依赖的例行维护：在 `gradle/libs.versions.toml` 中把 `awssdk-bom` 从 2.21.26 升到 2.21.29，通过 BOM 把所有 AWS SDK 子模块同步推进三个 patch 版本，纳入上游的 bug 修复与服务模型更新。
