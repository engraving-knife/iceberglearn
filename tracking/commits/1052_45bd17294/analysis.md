# 提交 1052：Build: Bump com.google.cloud:libraries-bom from 26.43.0 to 26.44.0 (#10916)

## 提交信息

- **序号**：1052 / 4088
- **哈希**：45bd17294a355b9bab875d9c7ff037607a56526f
- **短哈希**：45bd17294
- **日期**：2024-08-12（Mon Aug 12 23:53:48 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.43.0 to 26.44.0 (#10916)
- **PR/Issue**：#10916

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。`com.google.cloud:libraries-bom` 是 Google Cloud 官方维护的 BOM（Bill of Materials）依赖清单，用于统一管理 Google Cloud Java 客户端库（如 GCS、BigQuery、Pub/Sub 等）的版本组合，避免版本冲突。

本次提交将 Iceberg 项目中通过 Gradle 版本目录（version catalog）声明的 `google-libraries-bom` 版本从 `26.43.0` 升级到 `26.44.0`，这是一个 semver-minor 级别的版本升级，主要目的是跟进上游 Google Cloud 库的小版本迭代，获得 bug 修复、性能优化以及新功能支持。Iceberg 的 `gcp` 模块依赖 Google Cloud Storage（GCS）作为底层对象存储，因此 BOM 的版本会传导影响 GCS 相关客户端的版本。

## 如何达成设计目的

实现方式是修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一项的版本字符串，从 `26.43.0` 改为 `26.44.0`。Gradle 在解析依赖时会自动从 BOM 中拉取对应版本的 Google Cloud 子模块，无需修改任何业务代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Google Cloud libraries BOM 版本号从 26.43.0 升级到 26.44.0。

**工作逻辑**：仅修改一行版本字符串：

```diff
-google-libraries-bom = "26.43.0"
+google-libraries-bom = "26.44.0"
```

该变量在版本目录中被声明后，会被 `gcp` 模块的 `build.gradle` 引用，从而在构建时自动从 BOM 中导入 GCS、IAM 等子依赖的最新匹配版本。

## 小结

- **成效**：完成 Google Cloud libraries BOM 的小版本升级（26.43.0 → 26.44.0），使 Iceberg 的 GCP 集成模块对齐上游最新发布版本。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行改动；运行时影响 `gcp` 模块依赖的 Google Cloud 客户端版本。
- **回迁到 1.4.x 的注意事项**：可选择性回迁。这是纯依赖升级，没有 API 改动。如果 1.4.x 分支的 GCP 集成在 26.43.0 上运行正常且无已知安全问题需要修复，可以不回迁；若 26.44.0 包含对 1.4.x 兼容的 bug 修复或安全补丁，则可以安全 cherry-pick。需在 1.4.x 上运行 GCP 模块的完整测试套件确认无回归。
