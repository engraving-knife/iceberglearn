# 提交 0254：Build: Bump com.google.cloud:libraries-bom from 26.27.0 to 26.28.0 (#9258)

## 提交信息

- **序号**：0254 / 4088
- **哈希**：0331aba1a3ea5853d320b3244bb096fc9eebdc55
- **短哈希**：0331aba1a
- **日期**：2023-12-10 11:05:20 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.27.0 to 26.28.0 (#9258)
- **PR/Issue**：#9258

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Google Cloud Java 库的 BOM（Bill of Materials）`com.google.cloud:libraries-bom` 从 26.27.0 升级到 26.28.0（一次 semver-minor 升级）。

Google Cloud libraries-bom 是 Google Cloud 客户端库的物料清单，统一锁定 `com.google.cloud:*`（如 GCS Storage 客户端 `google-cloud-storage`、BigQuery 客户端 `google-cloud-bigquery` 等）以及其传递依赖（如 `com.google.http-client:google-http-client`、`com.google.api-client:google-api-client`、`com.google.api.grpc:proto-google-*` 等）的版本矩阵。Iceberg 通过 GCS FileIO 提供 Google Cloud Storage 作为表存储后端，因此需要依赖 Google Cloud 的 GCS 客户端。引入 BOM 模式可以保证这些 Google 库子模块之间的版本相互兼容，避免运行时类不匹配等问题。

26.27.0 到 26.28.0 是 minor 版本升级，通常会带入 GCS、BigQuery 等客户端的新特性、API 增强与 bug 修复，以及底层 gRPC/HTTP 库的更新。保持该 BOM 为较新版本有助于 Iceberg 在 GCS 后端上的稳定性与新功能支持。

## 如何达成设计目的

设计思路简单：仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一项的版本字符串，从 `26.27.0` 改为 `26.28.0`。由于采用 BOM 模式，所有 `com.google.cloud:*` 子工件的版本都由该 BOM 统一解析，无需逐个修改。改动规模为 1 个文件、1 行。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Google Cloud libraries-bom 的锁定版本从 26.27.0 提升至 26.28.0。

该文件是 Iceberg Gradle 构建的依赖版本目录（TOML 格式）。`google-libraries-bom = "26.x.x"` 这一项以 BOM 形式被引入，下游引用 GCS Storage 等 Google Cloud 子工件的模块（主要用于 GCS 后端集成）会统一获得 BOM 中声明的兼容版本。本次仅把这一行从 26.27.0 改为 26.28.0，其余依赖项保持不变。这种集中式 BOM 管理让 Google Cloud 库的整体升级评审与回滚都极为轻量，同时避免了子模块版本漂移。

## 小结

该提交通过升级 Google Cloud libraries-bom 至 26.28.0，让 Iceberg 的 GCS 存储后端集成获得最新的 Google Cloud 客户端库版本，属于云后端依赖维护。
