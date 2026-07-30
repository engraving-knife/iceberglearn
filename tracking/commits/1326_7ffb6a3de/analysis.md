# 提交 1326：Build: Bump com.google.cloud:libraries-bom from 26.49.0 to 26.50.0 (#11451)

## 提交信息

- **序号**：1326 / 4088
- **哈希**：7ffb6a3de78014c9917eaafbda947a3ebcdd1389
- **短哈希**：7ffb6a3de
- **日期**：2024-11-04（Mon Nov 4 08:50:40 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.49.0 to 26.50.0 (#11451)
- **PR/Issue**：#11451

## 总体目的

由 Dependabot 自动发起的依赖版本升级：将 Google Cloud Libraries BOM（`com.google.cloud:libraries-bom`）从 `26.49.0` 升级到 `26.50.0`，属 minor 版本升级。Iceberg 在 GCS 集成（`gcp` 模块、`gcp-bundle`）中使用 Google Cloud Storage 客户端，通过该 BOM 统一管理 Google Cloud 各客户端库的版本。升级目的是获取 26.50.0 中新增功能与 bug 修复。

Dependabot 标注 `update-type: version-update:semver-minor`，按语义化版本约定属向后兼容的功能性升级，风险高于 patch 但低于 major。

## 如何达成设计目的

只修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一个版本键的值。所有通过 `com.google.cloud:libraries-bom` 导入的 Google Cloud 子模块（如 `google-cloud-storage`、`google-cloud-core`、`google-auth-library` 等）会自动解析到 26.50.0 对齐的版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Google Cloud Libraries BOM 版本号。

**工作逻辑**：将第 47 行附近的版本声明由

```toml
google-libraries-bom = "26.49.0"
```

改为

```toml
google-libraries-bom = "26.50.0"
```

其他版本键保持不变。BOM 升级后，所有引用 `google-libraries-bom` 的模块（如 `gcp/build.gradle` 中的 `platform("com.google.cloud:libraries-bom:${libs.versions.google.libraries.bom.get()}")`）在依赖解析时使用 26.50.0 版本对应的 Google Cloud 子模块版本。

## 小结

- **成效**：Google Cloud Libraries BOM 升级至 26.50.0，获取 minor 版本中的新功能与修复。属依赖维护性升级，主要影响 GCS 集成模块的依赖解析。
- **影响范围**：仅 1 个文件、1 行版本号变更。运行时影响取决于 26.49.0→26.50.0 之间 Google Cloud 子模块（尤其是 `google-cloud-storage`）的具体改动；minor 升级理论上向后兼容，但仍需验证 GCS 读写测试通过。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 `libs.versions.toml` 管理 Google Cloud BOM，且 1.4.x 发布周期内 Google Cloud SDK 仍会持续发布新版本。回迁此类 minor 升级可获取 GCS 客户端的新功能与修复。但需注意：1.4.x 的 `libs.versions.toml` 可能与 main 不同步，回迁前应确认 1.4.x 当前 Google Cloud BOM 版本是否已高于 26.50.0，并验证 GCS 集成测试通过。若 1.4.x GCS 集成稳定且无相关 bug，可不必回迁。
