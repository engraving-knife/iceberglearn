# 提交 0960：Build: Bump com.google.errorprone:error_prone_annotations (#10731)

## 提交信息

- **序号**：0960 / 4088
- **哈希**：ad59eb7d60e84e8b98415708ec38da5382cf6fc7
- **短哈希**：ad59eb7d6
- **日期**：2024-07-22（Mon Jul 22 09:47:13 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations (#10731)
- **PR/Issue**：#10731

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。`com.google.errorprone:error_prone_annotations` 是 Google Error Prone 项目提供的运行时注解包（如 `@CanIgnoreReturnValue`、`@CheckReturnValue`、`@CompatibleWith`、`@RequiresPermission` 等），用于在编译期由 Error Prone 编译器插件进行静态检查时辅助识别常见编程错误，同时这些注解也会作为运行时依赖被部分库（如 Guava）传递引用。

Iceberg 在代码中使用这些注解来标注方法返回值是否可忽略、参数类型兼容性等，以提升代码质量与静态检查能力。本次提交将 `error_prone_annotations` 从 2.28.0 升级到 2.29.2（semver minor 版本升级），属于 minor 级别升级，通常包含新注解、检查规则增强和 bug 修复。Dependabot 提交说明中明确 update-type 为 `version-update:semver-minor`。

目的是保持 Error Prone 注解依赖的最新状态，获取上游新增的注解与检查能力，跟进 Guava 等依赖可能要求的新版本注解。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `errorprone-annotations` 的版本声明从 `2.28.0` 改为 `2.29.2`。所有引用该版本号的模块在构建时自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 error_prone_annotations 依赖版本从 2.28.0 升级到 2.29.2。

**工作逻辑**：仅修改版本目录中的一行：

```diff
-errorprone-annotations = "2.28.0"
+errorprone-annotations = "2.29.2"
```

修改后，所有通过 `${libs.errorprone.annotations}` 引用该版本号的模块（Iceberg 各核心模块在生产代码中使用 Error Prone 注解的模块）在构建时拉取 2.29.2 版本的注解包。

## 小结

- **成效**：完成 error_prone_annotations 从 2.28.0 到 2.29.2 的 minor 版本升级，使静态检查注解依赖保持最新。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有使用 Error Prone 注解的模块的构建产物依赖版本，但不改变 Iceberg 自身业务逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**适合回迁**，风险较低。Error Prone 注解包向后兼容性良好，minor 升级一般不破坏现有 API。回迁到 1.4.x 需确认 Iceberg 代码中使用的注解在新版本中仍然存在且语义一致。若 1.4.x 的构建未启用 Error Prone 编译器插件，该升级仅影响运行时传递依赖版本，风险更低。
