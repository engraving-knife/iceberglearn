# 提交 0055：Build: Bump com.google.cloud:libraries-bom from 26.24.0 to 26.25.0 (#8841)

## 提交信息

- **序号**：0055 / 4088
- **哈希**：fd1eae815af69efe543ec147a2ea13f6f16ba7a6
- **短哈希**：fd1eae815
- **日期**：2023-10-16 07:50:18 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.24.0 to 26.25.0 (#8841)
- **PR/Issue**：#8841

## 总体目的

本提交由 Dependabot 自动生成，目的是把 Iceberg 依赖的 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）版本从 `26.24.0` 升级到 `26.25.0`，以获取上游 Google Cloud Java 客户端库的新版本修复与改进。

背景与动机上，Iceberg 的 GCP 集成模块（`iceberg-gcp`）使用 Google Cloud Storage（`google-cloud-storage`）作为底层对象存储实现，并通过 `com.google.cloud:libraries-bom` 这个 BOM 统一管理 Google Cloud 各客户端库的版本。BOM 通过 Gradle 的 `platform(libs.google.libraries.bom)` 引入（在根 `build.gradle` 中作为 `compileOnly` 平台），约束 `google-cloud-storage`、`google-cloud-nio`（测试用）等模块的版本。Dependabot 定期扫描并提交 PR 升级到新版本；本次属于 `semver-minor` 级别的升级（26.24.x → 26.25.x），按 SemVer 约定向后兼容，通常包含新功能与 bug 修复。提交说明里也附带了 Google Cloud BOM 仓库的 Release notes、Changelog 与 Commits 对比链接，便于审查者核对变更内容。

对 Iceberg 演进的意义与 0054 类似：保持云厂商客户端库的现代化，让用户受益于 GCS 客户端的最新稳定性修复与功能，是项目持续维护云存储集成的常规手段。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一项的版本字符串，从 `26.24.0` 改为 `26.25.0`。由于项目用 BOM + `version.ref` 的方式集中管理 Google Cloud 库版本，所有引用 `google-libraries-bom` 的位置都会自动获得新版本，无需多处改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Google Cloud Libraries BOM 版本从 26.24.0 升级到 26.25.0。

**工作逻辑**：

修改前（位于版本目录的 `[versions]` 段，第 25 行附近）：
```toml
google-libraries-bom = "26.24.0"
```

修改后：
```toml
google-libraries-bom = "26.25.0"
```

该版本号通过 `version.ref` 被 `[libraries]` 段的 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用，进而被根 `build.gradle` 的 `compileOnly platform(libs.google.libraries.bom)` 使用，统一约束 `com.google.cloud:google-cloud-storage`（编译期）与 `com.google.cloud:google-cloud-nio`（测试期）等 Google Cloud 子模块的版本。因此单行改动即可让 GCP 集成模块的依赖同步升级到 26.25.0 系列。

版本变化含义：从 `26.24.0` 升级到 `26.25.0`，属于 Google Cloud Libraries BOM 26.x 内的 minor 升级，按 SemVer 向后兼容。

## 小结

本提交通过单行版本目录改动把 Google Cloud Libraries BOM 从 26.24.0 升级到 26.25.0，是 Dependabot 的常规依赖升级，使 Iceberg 的 GCP 集成模块跟上 Google Cloud 客户端库的最新版本。
