# 提交 0092：Build: Bump software.amazon.awssdk:bom from 2.21.0 to 2.21.5 (#8896)

## 提交信息

- **序号**：0092 / 4088
- **哈希**：94ae419c59b30336b3db85f5873569201b3fe0e7
- **短哈希**：94ae419c5
- **日期**：2023-10-25 14:01:17 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.0 to 2.21.5
- **PR/Issue**：#8896

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 2.21.0 升级到 2.21.5，属于语义化版本中的补丁版本（patch）升级。

AWS SDK BOM 是 Iceberg 与 AWS 服务集成时的核心依赖。Iceberg 的 `aws/` 模块（含 S3、Glue、DynamoDB、STS、KMS 等集成）以及 `mr/`、`data/` 等模块通过引用该 BOM 来统一管理 AWS SDK 各子模块（`s3`、`glue`、`sts`、`dynamodb`、`kms`、`apache-client`、`netty-nio-client` 等）的版本，避免各子模块版本不一致带来的兼容性问题。BOM 本身不携带代码，只是一个版本对齐清单（`<dependencyManagement>` 形式）。

本次升级跨越 2.21.0 → 2.21.5 共 5 个 patch 版本，按 AWS SDK 的版本策略，patch 版本仅包含 bug 修复与小的功能改进，不引入破坏性 API 变更。这期间通常包含 S3 客户端的稳定性修复、HTTP 客户端（Apache/Sync、Netty/Async）的问题修复、签名与重试逻辑的改进等。对于依赖 S3/Glue 等服务作为 catalog 后端的 Iceberg 而言，及时跟进 AWS SDK patch 版本有助于修复与云服务交互时偶发的稳定性问题。

与 0091 类似，这也是 Iceberg 持续依赖维护工作的一部分，由 Dependabot 自动发起，维护者评审合入。

## 如何达成设计目的

改动同样只动一处：在 Gradle 版本目录 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本字符串从 `"2.21.0"` 改为 `"2.21.5"`。由于 Iceberg 各模块通过 `platform(libs.awssdk.bom)` 引入该 BOM 来对齐 AWS SDK 全系版本，改这一处常量即可让所有 AWS SDK 子模块统一跳到 2.21.5。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：将 AWS SDK BOM 版本从 2.21.0 升级到 2.21.5。

**工作逻辑**：版本目录的 `[versions]` 段中只改了一行：

```toml
-awssdk-bom = "2.21.0"
+awssdk-bom = "2.21.5"
```

该版本常量在 `[libraries]` 段被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，进而在各 Gradle 构建脚本中以 `platform(libs.awssdk.bom)` 的形式作为 BOM 引入。改完后所有依赖 AWS SDK 子模块的版本都会被对齐到 2.21.5 的清单。BOM 升级是 patch 级别，按 AWS SDK 兼容性承诺不会破坏 Iceberg 现有编译与运行行为。

## 小结

该提交通过 Dependabot 将 AWS SDK BOM 从 2.21.0 升到 2.21.5，是 Iceberg AWS 集成依赖的一次常规 patch 升级，保持与 AWS SDK 上游修复同步。
