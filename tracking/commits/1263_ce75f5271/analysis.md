# 提交 1263：Build: Bump com.google.cloud:libraries-bom from 26.48.0 to 26.49.0 (#11363)

## 提交信息

- **序号**：1263 / 4088
- **哈希**：ce75f52719999d1b3aaba3ed4f10d4ca7fdb8bb8
- **短哈希**：ce75f5271
- **日期**：2024-10-21（Mon Oct 21 12:00:36 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.48.0 to 26.49.0
- **PR/Issue**：#11363

## 总体目的

Dependabot 自动生成的依赖升级提交，把 Google Cloud Libraries BOM 从 `26.48.0` 升级到 `26.49.0`（semver-minor 次版本升级）。

`com.google.cloud:libraries-bom` 是 Google Cloud 客户端库的依赖版本管理 BOM，Iceberg 的 `gcp` 模块（`GCSFileIO`、BigQuery 集成等）通过它统一管理所有 Google Cloud 子模块（`google-cloud-storage`、`google-cloud-nio` 等）的版本。升级动机是跟进上游 26.49.0 的改进与 bug 修复，保持 Google Cloud 客户端库最新。

## 如何达成设计目的

Iceberg 的依赖版本统一集中在 `gradle/libs.versions.toml` 中维护，`google-libraries-bom` 共享一个版本变量，只需把版本号从 `26.48.0` 改为 `26.49.0` 即可。BOM 机制会自动把所有 Google Cloud 子模块版本对齐。属于次版本升级（26.48 → 26.49），理论上不包含破坏性 API 变更，但需 CI 验证。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 Google Cloud Libraries BOM 版本。

**工作逻辑**：

```diff
-google-libraries-bom = "26.48.0"
+google-libraries-bom = "26.49.0"
```

该版本变量被 `google-libraries-bom = { module = "com.google.cloud:libraries-bom", version.ref = "google-libraries-bom" }` 引用，所有通过 `platform(libs.google.libraries.bom)` 引入 BOM 的模块（主要是 `gcp` 模块）会同步升级其使用的所有 Google Cloud 子模块版本。

## 小结

- **成效**：把 Google Cloud Libraries BOM 从 26.48.0 升级到 26.49.0，跟进上游次版本改进。仅修改 1 行 1 个文件，无源代码改动。
- **影响范围**：影响 `gcp` 模块的所有 Google Cloud 子模块版本（GCS、BigQuery 等），以及依赖 `gcp` 模块的引擎集成。生产环境使用 GCP 服务的用户在升级 Iceberg 后会随之升级 Google Cloud 客户端库版本。
- **回迁到 1.4.x 的注意事项**：纯依赖版本升级，回迁零风险。1.4.x 分支的 `gradle/libs.versions.toml` 可直接 cherry-pick。建议回迁后跑一次 `gcp` 模块测试确认无回归（次版本升级可能有小的行为变化）。
