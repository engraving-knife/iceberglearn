# 提交 0019：Build: Bump com.google.cloud:libraries-bom from 26.18.0 to 26.24.0 (#8735)

## 提交信息

- **序号**：0019 / 4088
- **哈希**：9bb7de118b5ade56904c494d7b2dfd0c064d05b3
- **短哈希**：9bb7de118
- **日期**：2023-10-09 08:11:46 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.18.0 to 26.24.0 (#8735)
- **PR/Issue**：#8735

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 Google Cloud Java 库 BOM（Bill of Materials）`com.google.cloud:libraries-bom` 从 `26.18.0` 升级到 `26.24.0`，跨 6 个小版本（26.19→26.24），属于 semver-minor 级别的依赖更新。

`libraries-bom` 是 Google 官方维护的 BOM，统一管理 Google Cloud Java 客户端库（如 GCS Storage、BigQuery、Pub/Sub、Secret Manager 等）及其传递依赖的版本。Iceberg 的 `gcp` 模块（`gcs` 集成）通过引入这个 BOM 来对齐 Google Cloud 相关 artifact 的版本，避免手工维护多个互相耦合的版本号。升级 BOM 通常会带来 bug 修复、新特性、安全补丁以及对底层 gRPC/HTTP/认证库的兼容性改进。

本次升级属于 `version-update:semver-minor`（次版本号升级），按 Dependabot 的分类标准意味着只有兼容性变更（minor），不包含破坏性 major 升级，风险较低。Dependabot 在 PR 描述中给出了 [release notes](https://github.com/googleapis/java-cloud-bom/releases)、[changelog](https://github.com/googleapis/java-cloud-bom/blob/main/release-please-config.json) 和 [commits 对比](https://github.com/googleapis/java-cloud-bom/compare/v26.18.0...v26.24.0) 供维护者审阅。Iceberg 维护者合并此 PR 即表示认可升级带来的变更在 Iceberg 使用范围内是兼容的。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `google-libraries-bom = "26.18.0"` 改为 `google-libraries-bom = "26.24.0"`。所有通过 `libs.bundles.google.cloud` 或直接引用该 BOM 的模块会自动解析到新版本，无需逐个修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `com.google.cloud:libraries-bom` 的版本从 26.18.0 升级到 26.24.0。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 25 行附近），原行 `google-libraries-bom = "26.18.0"` 被改为 `google-libraries-bom = "26.24.0"`。该版本常量在 Iceberg 构建中被 `gcp` 模块通过 platform BOM 方式引用，控制 GCS 相关客户端库及其传递依赖的版本。升级后，相关 artifact（如 `google-cloud-storage`、`google-cloud-core`、`google-auth-library-oauth2-http` 等）会按 26.24.0 BOM 解析到对应的较新版本。

## 小结

该提交由 Dependabot 自动将 Google Cloud libraries-bom 从 26.18.0 升级到 26.24.0，使 Iceberg 的 GCS 集成跟进 Google Cloud Java 库的最新兼容版本，获取 bug 修复与改进。
