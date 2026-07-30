# 提交 0101：Build: Bump com.google.cloud:libraries-bom from 26.25.0 to 26.26.0 (#8940)

## 提交信息

- **序号**：0101 / 4088
- **哈希**：b5aba2ffeb6303c9e2dba518b8e08d419797286c
- **短哈希**：b5aba2ffe
- **日期**：2023-10-30 08:22:15 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.25.0 to 26.26.0 (#8940)
- **PR/Issue**：#8940

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 Google Cloud Java 客户端的物料清单（BOM）`com.google.cloud:libraries-bom` 从 26.25.0 升级到 26.26.0。该 BOM 用于统一管理 Google Cloud 各客户端库（如 GCS、BigQuery、Pub/Sub 等）的版本，Iceberg 在 GCS 集成模块中依赖它来引入 `google-cloud-storage` 等组件。

Iceberg 通过 `gradle/libs.versions.toml` 集中管理所有第三方依赖版本，Dependabot 监控这些依赖并周期性地提交 PR 进行小版本升级。这类 semver-minor（次要版本）升级通常包含新功能与缺陷修复，但不引入破坏性 API 变更，因此风险较低。

该升级对 Iceberg 演进的意义在于：保持 Google Cloud 相关集成（特别是 `gcp` 模块下的 `GCSFileIO`、`BigQuery` 等）所依赖的客户端库处于最新稳定版本，从而获得最新的缺陷修复、性能改进和云服务 API 兼容性，避免因客户端库过时而积累技术债。

## 如何达成设计目的

设计思路非常直接：Dependabot 仅修改 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一个版本变量，从 `26.25.0` 改为 `26.26.0`。由于 Iceberg 在各模块的 `build.gradle` 中通过 `platform(libs.google.libraries.bom)` 方式引用该 BOM，所有由 BOM 管理版本的 Google Cloud 传递依赖都会随之统一升级，无需逐个修改各模块的依赖声明。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Google Cloud libraries-bom 的版本号从 26.25.0 提升到 26.26.0。

**工作逻辑**：仅修改一行版本常量。该文件第 42 行附近的 `google-libraries-bom = "26.25.0"` 被改为 `google-libraries-bom = "26.26.0"`。此变量随后在 `libs` 的依赖目录中被引用为 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }`（属 platform BOM 用法），从而让 GCP 模块在编译/运行时拉取新版本的 Google Cloud SDK 及其传递依赖。提交说明中的 `updated-dependencies` 元数据表明该依赖属于 `direct:production`、`version-update:semver-minor` 类型。

## 小结

这是 Dependabot 自动化依赖维护的一次例行 semver-minor 升级，通过单行版本号变更让 Iceberg 的 Google Cloud 集成模块跟随上游 SDK 的演进。
