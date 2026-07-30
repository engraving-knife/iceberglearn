# 提交 1215：Build: Bump com.google.cloud:libraries-bom from 26.47.0 to 26.48.0 (#11271)

## 提交信息

- **序号**：1215 / 4088
- **哈希**：5dde680797a21955964298d684f40f97fbc23406
- **短哈希**：5dde68079
- **日期**：2024-10-07（Mon Oct 7 09:25:30 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.47.0 to 26.48.0 (#11271)
- **PR/Issue**：#11271

## 总体目的

`com.google.cloud:libraries-bom` 是 Google Cloud 官方维护的 BOM（Bill of Materials），用于统一管理 Google Cloud Java 客户端库（如 GCS、BigQuery、Pub/Sub 等）及其传递依赖的版本，避免版本冲突。本提交由 dependabot 自动发起，将该 BOM 版本从 `26.47.0` 升级到 `26.48.0`（semver minor 升级），以获取最新的 bug 修复、安全补丁与功能改进。Iceberg 在 GCS 集成（`gcp` 模块）等场景下依赖该 BOM。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `google-libraries-bom` 的版本声明，从 `26.47.0` 改为 `26.48.0`。Gradle 在解析依赖时会通过该 BOM 统一覆盖所有 Google Cloud 相关依赖的版本，无需逐个修改。无代码逻辑变更。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Google Cloud libraries BOM 版本。

**工作逻辑**：将第 47 行附近的 `google-libraries-bom = "26.47.0"` 改为 `google-libraries-bom = "26.48.0"`。该变量在 Gradle 构建脚本中被引用（通常通过 `platform("com.google.cloud:libraries-bom:${google-libraries-bom}")`），从而把整个 Google Cloud 依赖树锁定到 BOM 指定的版本。本次为 minor 版本升级（26.47 → 26.48），属于向后兼容的依赖更新。

## 小结

- **成效**：Iceberg 使用的 Google Cloud 客户端库版本随 BOM 升级到 26.48.0，获取该版本包含的依赖更新与修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。受影响的是 `gcp` 等模块在构建期解析到的 Google Cloud 依赖版本。
- **回迁到 1.4.x 的注意事项**：依赖版本升级通常可以安全回迁，但需注意：1.4.x 发布时锁定的 BOM 版本可能与 26.48.0 存在传递依赖冲突（例如某些 Google Cloud 依赖要求更高版本的 Guava 或其他共享依赖）。**若 1.4.x 用户依赖 GCS 集成且需要 26.48.0 中的修复（如安全补丁），可考虑回迁**；否则 1.4.x 一般保持发布时的 BOM 版本不变，仅在必要时通过补丁版本升级。回迁前应运行 `gcp` 模块完整测试验证兼容性。
