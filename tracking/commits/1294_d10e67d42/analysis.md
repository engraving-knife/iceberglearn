# 提交 1294：Build: Bump com.google.errorprone:error_prone_annotations (#11403)

## 提交信息

- **序号**：1294 / 4088
- **哈希**：d10e67d422f2b58ed13bcf826bc95fe1e5acc0f4
- **短哈希**：d10e67d42
- **日期**：2024-10-28（Mon Oct 28 14:16:44 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#11403)
- **PR/Issue**：#11403

## 总体目的

Iceberg 仓库使用 Google Error Prone 的注解库（`com.google.errorprone:error_prone_annotations`）来标注代码中的编译时警告抑制和错误检测行为。本次提交将该依赖从 2.34.0 升级到 2.35.1，属于 semver-minor 级别的次版本更新。升级可获取新的错误检测规则注解和上游 bug 修复。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `errorprone-annotations` 版本声明为 2.34.0，自动将其更新为 2.35.1。所有通过 `libs.errorprone.annotations` 引用该库的模块自动获取新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Error Prone 注解库版本从 2.34.0 升级到 2.35.1。

**工作逻辑**：在版本目录的 `[versions]` 区块中，将：

```toml
errorprone-annotations = "2.34.0"
```

修改为：

```toml
errorprone-annotations = "2.35.1"
```

该版本号位于 `esotericsoftware-kryo` 和 `failsafe` 之间。Error Prone 注解库通常作为传递依赖或编译时依赖引入，主要用于 `@SuppressWarning` 等注解的标注，不会显著影响运行时行为。

## 小结

- **成效**：Error Prone 注解库升级到 2.35.1，获取上游次版本更新和 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：Error Prone 注解库是编译时辅助依赖，对运行时行为影响极小。1.4.x 分支无需强制回迁。如果 1.4.x 当前使用 2.34.0 且编译正常，可以保持不变。若需要利用 2.35.x 的新注解功能或修复的注解兼容性问题，则可考虑回迁，风险较低。
