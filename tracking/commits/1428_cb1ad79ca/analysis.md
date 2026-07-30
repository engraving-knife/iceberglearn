# 提交 1428：Build: Bump com.google.errorprone:error_prone_annotations (#11638)

## 提交信息

- **序号**：1428 / 4088
- **哈希**：cb1ad79cab49a3bd7202835b68b29596e37c0f37
- **短哈希**：cb1ad79ca
- **日期**：2024-11-25（Mon Nov 25 10:28:40 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations from 2.35.1 to 2.36.0 (#11638)
- **PR/Issue**：#11638

## 总体目的

由 dependabot 自动发起的依赖升级，将 `com.google.errorprone:error_prone_annotations` 从 2.35.1 升级到 2.36.0。`error_prone_annotations` 是 Google Error Prone 的注解包（如 `@CanIgnoreReturnValue`、`@CheckReturnValue` 等），被 Iceberg 多个模块作为编译期/运行时注解依赖使用。2.36.0 是一个 minor 版本升级，可能新增注解或修复注解处理器的兼容性问题。本次升级用于跟随上游版本，保持与 Error Prone 工具链的兼容性。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `errorprone-annotations` 的版本号即可。该变量在 catalog 中被 `errorprone-annotations` 依赖通过 `version.ref` 引用，改一行即可同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 errorprone-annotations 版本。

**工作逻辑**：

```toml
-errorprone-annotations = "2.35.1"
+errorprone-annotations = "2.36.0"
```

仅此一行变更。

## 小结

- **成效**：跟随上游 minor 版本，获得 2.36.0 的注解兼容性改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行，无源码或测试逻辑改动。
- **回迁到 1.4.x 的注意事项**：可以无风险回迁。`error_prone_annotations` 是纯注解包，minor 版本升级向后兼容，不影响运行时行为。如果 1.4.x 当前版本已稳定通过 CI，也可不回迁；若 1.4.x 的 Error Prone 静态检查有相关 warning 需要新版本注解来抑制，则回迁有意义。一般而言，维护分支不必强求跟随依赖小版本升级。
