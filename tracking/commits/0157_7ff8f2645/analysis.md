# 提交 0157：Build: Bump com.google.cloud:libraries-bom from 26.26.0 to 26.27.0 (#9036)

## 提交信息

- **序号**：0157 / 4088
- **哈希**：7ff8f264581c0878e85c360b92a79f925aad758e
- **短哈希**：7ff8f2645
- **日期**：2023-11-13 10:09:36 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.26.0 to 26.27.0 (#9036)
- **PR/Issue**：#9036

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 26.26.0 升级到 26.27.0。这是一个 `version-update:semver-minor` 级别的次版本升级（minor），由 dependabot 主动发起。

`libraries-bom` 是 Google Cloud 客户端库的 BOM（Bill of Materials），通过它导入的依赖会被统一对齐到一组相互兼容的版本。Iceberg 的 GCS 集成（`gcs` 模块）以及与云存储交互的代码会经由该 BOM 间接拉取 `google-cloud-storage`、`google-auth-library-*` 等制品。升级 BOM 即可一次性把这些传递依赖升级到 26.27.0 对应的兼容矩阵，而不需要逐个调整。

引入升级的意义在于：BOM 26.27.0 包含 Google Cloud 各客户端库的 bug 修复、新的 API 支持（例如 Storage 客户端的改进）和潜在的安全补丁；保持与上游同步可以避免后续在升级 GCS 集成或排查兼容性问题时被过旧的传递依赖拖累。由于 BOM 本身是版本对齐工具，次版本升级通常向后兼容，但 Iceberg 在合入前仍会通过 CI 跑 GCS 相关集成测试来验证。

## 如何达成设计目的

通过修改 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../gradle/libs.versions.toml) 中 `google-libraries-bom` 版本别名，由 `26.26.0` 改为 `26.27.0`。该别名在 `[libraries]` 段被 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用，构建脚本通过 `platform(...)` 方式导入该 BOM 来统一管理 Google Cloud 系列制品版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Google Cloud Libraries BOM 从 26.26.0 升级到 26.27.0，统一跟进 Google Cloud 客户端库的兼容版本矩阵。

**工作逻辑**：在 `[versions]` 段将 `google-libraries-bom = "26.26.0"` 修改为 `google-libraries-bom = "26.27.0"`（位于约第 42 行，紧随 flink 系列版本之后）。BOM 本身不直接提供 API，而是作为 Gradle 的 `platform` 依赖被引入，从而让 `google-cloud-storage` 等具体制品的版本由 BOM 决定。这意味着本次升级实际影响的传递依赖范围比单行改动要广得多——所有由该 BOM 管理的 Google Cloud 制品都会在解析时被对齐到 26.27.0 对应的版本。

## 小结

通过 dependabot 升级 Google Cloud Libraries BOM 到 26.27.0，让 Iceberg 的 GCS 集成所依赖的 Google Cloud 客户端库矩阵保持与上游兼容版本同步，属常规维护性升级。
