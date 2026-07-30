# 提交 1221：Build: Bump com.google.errorprone:error_prone_annotations (#11270)

## 提交信息

- **序号**：1221 / 4088
- **哈希**：5fa3bbeec21f3efb419878d18821636e6519c538
- **短哈希**：5fa3bbeec
- **日期**：2024-10-12（Sat Oct 12 21:07:15 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#11270)
- **PR/Issue**：#11270

## 总体目的

`com.google.errorprone:error_prone_annotations` 是 Google error-prone 静态分析工具提供的注解包（含 `@FormatMethod`、`@CanIgnoreReturnValue`、`@CheckReturnValue`、`@CompatibleWith` 等注解），Iceberg 在代码中使用这些注解来增强静态检查与文档化 API 契约。本提交由 dependabot 自动发起，把该依赖从 `2.31.0` 升级到 `2.33.0`（semver minor 升级），获取新版本注解定义与 bug 修复。

注意：此为 `error_prone_annotations`（运行时注解包），与 `baseline.gradle` 中配置的 error-prone 编译期检查工具（提交 1212 启用的检查规则）是配套但独立的依赖。注解包升级保证 Iceberg 代码中使用的注解与新版本 error-prone 检查工具兼容。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `errorprone-annotations` 的版本声明，从 `2.31.0` 改为 `2.33.0`。Gradle 在构建时通过该变量解析依赖版本。无代码逻辑变更。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 error_prone_annotations 版本。

**工作逻辑**：将 `errorprone-annotations = "2.31.0"` 改为 `errorprone-annotations = "2.33.0"`。该变量在 Gradle 构建脚本中被引用，用于声明对 `com.google.errorprone:error_prone_annotations` 的依赖版本。本次为 minor 版本升级（2.31.0 → 2.33.0，跳过 2.32.x），属于向后兼容的依赖更新。

## 小结

- **成效**：Iceberg 使用的 error-prone 注解包升级到 2.33.0，与新版本 error-prone 检查工具保持兼容，获取新版本注解定义与修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。受影响的是构建期解析到的注解包版本（注解包本身通常对运行时无影响，注解多为 `RetentionPolicy.SOURCE` 或 `CLASS`）。
- **回迁到 1.4.x 的注意事项**：注解包版本升级通常可以安全回迁，注解包向后兼容性良好。**若 1.4.x 需要与较新版本的 error-prone 检查工具配合（例如回迁了提交 1212 的新检查规则），建议同步回迁此注解包升级**以保证注解与检查工具版本一致。若 1.4.x 保持原有的 error-prone 检查配置，则单独回迁此注解升级也安全（新注解包对旧检查工具向后兼容）。回迁风险低。
