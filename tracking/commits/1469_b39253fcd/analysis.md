# 提交 1469：Build: Bump com.google.cloud:libraries-bom from 26.50.0 to 26.51.0 (#11724)

## 提交信息

- **序号**：1469 / 4088
- **哈希**：b39253fcd4fdde765ba3a83f84b4160b9bdcb2a8
- **短哈希**：b39253fcd
- **日期**：2024-12-09（Mon Dec 9 11:48:51 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.50.0 to 26.51.0 (#11724)
- **PR/Issue**：#11724

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。`com.google.cloud:libraries-bom` 是 Google Cloud Java 客户端库的 BOM（Bill of Materials），通过导入该 BOM 统一管理 Google Cloud 各服务客户端（GCS、BigQuery、Pub/Sub、Secret Manager 等）的版本。Iceberg 的 GCS catalog（`gcp` 模块）依赖 Google Cloud Storage 客户端，因此该 BOM 版本直接决定 Iceberg 与 Google Cloud 交互的客户端版本。

Dependabot 检测到从 26.50.0 升级到 26.51.0，属于 `direct:production` 类型、`version-update:semver-minor` 级别的升级（注意本次是 minor 版本升级而非 patch）。minor 升级可能引入新功能，但 Google Cloud Java 客户端库遵循语义化版本，一般保持向后兼容。本提交的目的是跟进 Google Cloud 客户端库上游修复与功能更新。Dependabot 在提交说明中附带了发布说明、更新日志和版本对比链接，便于人工审查变更内容。

## 如何达成设计目的

实现方式是单行版本号替换：在 `gradle/libs.versions.toml` 的版本目录中，把 `google-libraries-bom = "26.50.0"` 改为 `google-libraries-bom = "26.51.0"`。引用该版本变量的 BOM 依赖会自动同步升级，所有从 BOM 继承版本的 Google Cloud 模块随之统一到 26.51.0。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Google Cloud libraries-bom 版本变量从 26.50.0 升级到 26.51.0。

**工作逻辑**：在 `[versions]` 段中（位于 flink120 与 guava 之间）：
```
-google-libraries-bom = "26.50.0"
+google-libraries-bom = "26.51.0"
```
该变量被 `[libraries]` 段中 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用，BOM 通过 `platform` 方式导入到 `gcp` 等模块的依赖配置中，从而统一所有 `com.google.cloud:*` 模块的版本。相邻行（flink118、flink119、flink120、guava、hadoop2、hadoop3 等）保持不变。

## 小结

- **成效**：将 Google Cloud libraries-bom 从 26.50.0 升级到 26.51.0，统一提升所有 Google Cloud 客户端模块版本，跟进上游一个 minor 版本的修复与功能更新。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。影响 `gcp` 模块（GCS catalog 与 IO 实现）等依赖 Google Cloud 客户端的运行时版本，但不修改产品代码逻辑。
- **回迁到 1.4.x 的注意事项**：本提交是依赖版本升级，回迁风险较低。需注意：(1) 1.4.x 分支的 `libs.versions.toml` 中 `google-libraries-bom` 版本可能与 main 不同，cherry-pick 时直接同步版本号即可；(2) 本次是 minor 版本升级（非 patch），相比 patch 升级需更谨慎，建议回迁后跑一遍 GCS 相关集成测试确认无回归；(3) Google Cloud 客户端库 minor 升级一般保持向后兼容，但仍需关注 GCS 客户端行为（重试、超时、认证）是否有变化。整体可选回迁，建议结合 1.4.x 实际 GCS 集成测试结果决定。
