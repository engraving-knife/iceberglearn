# 提交 0356：Build: Bump software.amazon.awssdk:bom from 2.22.12 to 2.23.2 (#9471)

## 提交信息

- **序号**：0356
- **哈希**：4b6f5b7b87deaad8fa8999f604265d96ca7122d2
- **短哈希**：4b6f5b7b8
- **日期**：2024-01-15 09:14:58 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.22.12 to 2.23.2 (#9471)
- **PR/Issue**：#9471

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 AWS SDK for Java 2 BOM（`software.amazon.awssdk:bom`）从 `2.22.12` 升级到 `2.23.2`，属于一次常规的依赖版本维护（`version-update:semver-minor`）。AWS SDK BOM 在 Iceberg 中作为版本对齐基准（Bill of Materials），统一管控 `aws-sdk` 系列模块（如 `s3`、`glue`、`kms`、`sts`、`client-runtime`、`http-client-spi` 等）的版本，使各模块版本相互兼容、避免冲突。通过 BOM 锁版本而不是逐个声明每个 AWS SDK 子模块的版本，是 Gradle 多模块项目中常见的依赖治理实践。

升级跨度为 `2.22.12 → 2.23.2`，跨越约 1 个 minor 版本（从 2.22.x 到 2.23.x），属于 SDK 上游的常规迭代。这类升级的典型动机包括：获取上游缺陷修复（如 S3 客户端在特定 region 的 STS 假设角色流程、异步客户端的连接池泄漏、S3AccessGrants 凭证提供者的兼容性等）、获取新服务模型快照、对齐 AWS 服务的最新 API 行为。由于 Iceberg 的 `aws-bundle`、`nessie`（Nessie 也间接通过 S3 access grants 拉入 AWS SDK）、`s3`/`glue` catalog 实现都依赖 AWS SDK，BOM 的升级会自动传递到所有这些模块的传递依赖。

风险层面，本次升级仅修改 BOM 版本字符串，不触及任何业务代码，对运行时行为的影响主要取决于 AWS SDK 2.22.x → 2.23.x 的二进制兼容性。AWS SDK for Java 2 严格遵守 SemVer，minor 版本升级通常保持二进制兼容（除非有 deprecated API 被移除的极端情况），故 CI 流水线一般能直接通过。Dependabot 在 PR 描述中标注了 `dependency-type: direct:production`、`update-type: version-update:semver-minor`，说明这是一次直接生产依赖的 semver-minor 升级，预期无需代码改动。

## 如何达成设计目的

实现路径极简：在 `gradle/libs.versions.toml` 这个 Gradle 版本目录（Version Catalog）文件中，把 `awssdk-bom` 这一项的版本字符串从 `"2.22.12"` 改为 `"2.23.2"`。Gradle 在解析 `libs.versions.toml` 时会自动把该 BOM 作为平台依赖（`platform`）引入，所有引用 `software.amazon.awssdk:*` 子模块的工程都会按 BOM 锁定的版本解析，无需逐个修改子模块版本声明。这是 Iceberg 项目通过 Version Catalog 集中治理依赖版本的标准模式。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK for Java 2 BOM 的版本从 `2.22.12` 升级到 `2.23.2`，作为本次依赖升级的唯一改动点。

**工作逻辑**：在版本目录文件的 `[versions]` 段中，把 `awssdk-bom = "2.22.12"` 改为 `awssdk-bom = "2.23.2"`。该 key 在 `[libraries]` 段（或通过 BOM 平台依赖形式）被引用，控制所有 `software.amazon.awssdk:*` 子模块的解析版本。同文件中还独立声明了 `awssdk-s3accessgrants = "1.0.1"`（S3 Access Grants 凭证提供者插件），它不在 BOM 治理范围内、版本不变；BOM 升级只影响 BOM 内的模块。这是 Dependabot 升级 `direct:production` 依赖的标准最小改动模式。

## 小结

本次提交是 Dependabot 对 AWS SDK BOM 的一次常规 semver-minor 升级（2.22.12 → 2.23.2），仅修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 一项版本字符串，不动业务代码。AWS SDK for Java 2 的 SemVer 承诺保证了 minor 升级的二进制兼容性，本次升级通过 Version Catalog 集中治理依赖版本的机制，自动传递到所有依赖 AWS SDK 子模块的工程。这类例行依赖升级是项目维护健康度的重要指标，让 Iceberg 跟上 AWS SDK 上游的缺陷修复与新服务模型支持。
