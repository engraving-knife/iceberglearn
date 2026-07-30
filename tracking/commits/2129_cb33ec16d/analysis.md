# 提交 2129：Common: Reduce visibility of deprecated method since 1.7.0

## 提交信息

- **序号**：2129 / 4088
- **哈希**：cb33ec16d0811c5a39be7e1d4f854742478c2fbb
- **短哈希**：cb33ec16d
- **日期**：2025-05-15 02:04:11 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Common: Reduce visibility of deprecated method since 1.7.0 (#13053)
- **PR/Issue**：#13053

## 总体目的

本提交按照之前版本的弃用计划，将自 1.7.0 起标记为 deprecated 的方法降低可见性。`DynConstructors.Ctor.invokeChecked` 和 `DynMethods.UnboundMethod.invokeChecked` 两个方法在 1.7.0 时被标记为 `@Deprecated` 并注明"visibility will be reduced in 1.8.0"，本提交将其从 `public` 降为 package-private（无修饰符）。这是 API 清理工作的一部分，按照 Iceberg 的 API 演进策略，先弃用再在后续版本收紧可见性，给予用户充足的迁移时间。同时在 Revapi 兼容性配置中记录这些预期的 API 破坏性变更。

## 如何达成设计目的

1. 移除 `DynConstructors.Ctor.invokeChecked` 方法的 `public` 修饰符和 `@Deprecated` 注解，改为 package-private
2. 移除 `DynMethods.UnboundMethod.invokeChecked` 方法的 `public` 修饰符和 `@Deprecated` 注解，改为 package-private
3. 在 `.palantir/revapi.yml` 中添加 1.9.0 版本的接受破坏性变更记录，说明这是预期的可见性降低

## 修改详情

### `common/src/main/java/org/apache/iceberg/common/DynConstructors.java` (修改, +1/-5 lines)

**修改目的**：降低 `invokeChecked` 方法的可见性。

**工作逻辑**：移除 `@Deprecated` 注解和弃用 Javadoc 注释，将 `public <R> R invokeChecked(Object target, Object... args) throws Exception` 改为 `<R> R invokeChecked(Object target, Object... args) throws Exception`（package-private）。

### `common/src/main/java/org/apache/iceberg/common/DynMethods.java` (修改, +1/-5 lines)

**修改目的**：降低 NOOP UnboundMessage 的 `invokeChecked` 方法的可见性。

**工作逻辑**：移除 `@Deprecated` 注解和弃用 Javadoc 注释，将 `public <R> R invokeChecked(Object target, Object... args)` 改为 `<R> R invokeChecked(Object target, Object... args)`（package-private）。

### `.palantir/revapi.yml` (修改, +7/-0 lines)

**修改目的**：记录预期的 API 破坏性变更。

**工作逻辑**：在 1.9.0 版本的 `acceptedBreaks` 中添加 `iceberg-common` 模块的 `java.method.visibilityReduced` 记录，标注 justification 为 "Reduce visibilty of deprecated method"，使 Revapi API 兼容性检查工具认可这个预期的变更。

## 总结

本提交是 API 演进计划的一部分，按照 1.7.0 的弃用承诺，在 1.9.0 中将 `DynConstructors` 和 `DynMethods` 的 `invokeChecked` 方法从 public 降为 package-private。这是 Iceberg 项目 API 清理的常规操作，通过 Revapi 配置记录预期的破坏性变更，确保 API 兼容性检查不会误报。
