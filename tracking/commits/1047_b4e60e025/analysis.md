# 提交 1047：Build: Bump com.google.errorprone:error_prone_annotations (#10915)

## 提交信息

- **序号**：1047 / 4088
- **哈希**：b4e60e02523233ac3b90f8b74d4b0c4df0f1c413
- **短哈希**：b4e60e025
- **日期**：2024-08-12（Mon Aug 12 15:50:50 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#10915)
- **PR/Issue**：#10915

## 总体目的

`com.google.errorprone:error_prone_annotations` 是 Google Error Prone 项目提供的注解库（如 `@CanIgnoreReturnValue`、`@CheckReturnValue` 等），被 Iceberg 用于标注方法的返回值处理契约。dependabot 检测到该依赖有新版本 2.30.0（旧版本 2.29.2），本提交是例行将该依赖升级到最新版本，获取新版本的注解定义与缺陷修复。

## 如何达成设计目的

通过修改 Gradle 版本目录中 `errorprone-annotations` 的版本声明，从 `2.29.2` 改为 `2.30.0`。该版本号被 Iceberg 各模块通过 `${libs.errorprone-annotations}` 引用。这是 minor 版本升级，注解库保持向后兼容，无需改动使用方代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `error_prone_annotations` 依赖从 2.29.2 升到 2.30.0。

**工作逻辑**：将 `errorprone-annotations = "2.29.2"` 改为 `errorprone-annotations = "2.30.0"`。该变量是 Iceberg 对 errorprone 注解库的统一版本声明。

## 小结

- **成效**：把 `com.google.errorprone:error_prone_annotations` 从 2.29.2 升级到 2.30.0，获取该注解库的最新修复与改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行修改。
- **回迁到 1.4.x 的注意事项**：注解库 minor 版本升级二进制兼容，**回迁风险极低**，可按需回迁。若 1.4.x 未固定该依赖或固定在更老版本且无升级诉求，可不动。回迁后建议构建一次确认无引入新的告警规则。
