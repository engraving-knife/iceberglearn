# 提交 3880：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#16804)

## 提交信息

- **序号**：3880 / 4088
- **哈希**：1f985f44e4f2f920088ec3ec90235b52f341379f
- **短哈希**：1f985f44e
- **日期**：2026-06-14 00:08:46 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#16804)
- **PR/Issue**：#16804

## 总体目的

由 Dependabot 自动生成的依赖升级提交，将 `jackson-annotations` 从 2.21 升级到 2.22。Jackson 是 Java 生态中最流行的 JSON 处理库，`jackson-annotations` 提供注解用于配置序列化/反序列化行为，被 Iceberg 广泛用于元数据、REST API 等场景的 JSON 处理。

这是 semver-minor 级别的升级，引入新功能但应保持向后兼容。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `jackson-annotations` 版本变量，从 `2.21` 改为 `2.22`。值得注意的是，此时 `jackson-bom` 仍为 `2.21.4`（在提交 3883 中会同步升级），所以这是一个渐进式的分步升级策略。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 jackson-annotations 版本变量。

**工作逻辑**：
```diff
-jackson-annotations = "2.21"
+jackson-annotations = "2.22"
```
单独升级 jackson-annotations 版本，而 jackson-bom 仍保持在 2.21.4，这是因为 annotations 模块相对独立，可以先单独升级。

## 总结

常规的依赖升级，将 Jackson annotations 从 2.21 升级到 2.22。值得注意的是此时尚未同步升级 jackson-bom（后续提交 3883 会处理），体现了渐进式升级策略。Jackson 作为核心 JSON 处理库，其升级需要关注兼容性。
