# 提交 1122：Build: Bump com.google.errorprone:error_prone_annotations (#11055)

## 提交信息

- **序号**：1122 / 4088
- **哈希**：d128a2a9811fd46d581b36d29b4e62b7be9246b8
- **短哈希**：d128a2a98
- **日期**：2024-09-01（Sun Sep 1 07:22:59 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#11055)
- **PR/Issue**：#11055

## 总体目的

这是 Dependabot 自动生成的依赖升级提交。`com.google.errorprone:error_prone_annotations` 是 Google error-prone 项目提供的注解库（如 `@CanIgnoreReturnValue`、`@CheckReturnValue`、`@FormatMethod` 等），Iceberg 在编译期通过 error-prone 插件做静态检查时引用这些注解。本次将版本从 2.30.0 升级到 2.31.0，属于 semver-minor（次版本）升级，目的是跟随上游获得 bug 修复与新增能力，同时保持与 error-prone 编译插件的版本一致性。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，把 `errorprone-annotations` 版本号变量从 `2.30.0` 改为 `2.31.0`。所有依赖该变量的模块（通过 `version.ref` 引用）会自动应用新版本，无需逐模块修改。这是 Dependabot 的标准升级方式。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 error-prone 注解库版本。

**工作逻辑**：仅一行变更：

```toml
- errorprone-annotations = "2.30.0"
+ errorprone-annotations = "2.31.0"
```

该变量被 libs.versions.toml 中对应的 `errorprone-annotations = { module = "com.google.errorprone:error_prone_annotations", version.ref = "errorprone-annotations" }` 引用，下游各模块通过 `libs.errorprone.annotations` 等方式消费，因此版本变更会全局生效。

## 小结

- **成效**：error-prone 注解库升至 2.31.0，跟随上游；后续若启用 2.31.0 新增的注解/检查会更方便。
- **影响范围**：仅版本目录一个文件、一行变更，属纯构建依赖升级，不影响运行时行为与公共 API。
- **回迁到 1.4.x 的注意事项**：这是构建期依赖升级，对 1.4.x 发布产物（jar 内容）无功能性影响，主要用于 main 分支构建。1.4.x 作为维护分支通常不需要主动跟进此类次版本依赖升级；若 1.4.x 的 libs.versions.toml 仍停留在 2.30.0，**无需强制回迁**，除非 1.4.x 也启用了需要 2.31.0 的 error-prone 检查（本批后续提交 1128 启用更多检查时可能间接相关）。如确要回迁，单独 cherry-pick 本提交即可，无冲突风险。
