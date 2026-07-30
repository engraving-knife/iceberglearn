# 提交 3361：Core: Deprecate unused methods in TableScanUtil (#15543)

## 提交信息

- **序号**：3361 / 4088
- **哈希**：32f5ee6c2d8d8d0476795efc4b1420e6c7f0b1c0
- **短哈希**：32f5ee6c2
- **日期**：2026-03-09
- **作者**：Manu Zhang
- **提交说明**：Core: Deprecate unused methods in TableScanUtil (#15543)
- **PR/Issue**：#15543

## 总体目的

该提交为 `TableScanUtil` 中两个已不再被使用的公共静态方法添加 `@Deprecated` 注解和弃用说明，明确标记它们将在未来版本中移除。这两个方法是 `hasDeletes(CombinedScanTask)` 和 `hasEqDeletes(CombinedScanTask)`。

`TableScanUtil` 是 Iceberg Core 模块中用于扫描任务处理的工具类。随着 Iceberg 代码库的演进，某些曾经被调用的公共方法逐渐失去了调用者，但作为公共 API 的一部分，直接删除会破坏外部兼容性。按照 Iceberg 的 API 弃用策略，需要先标记为 `@Deprecated` 并注明弃用版本和计划移除版本，给下游使用者足够的迁移时间。

PR 标题明确指出这些方法是"unused"（未使用的），即代码库内部已无调用。本次将它们标记为自 1.11.0 弃用，计划在 1.12.0 移除，遵循了 Iceberg 的小版本弃用周期约定。

## 如何达成设计目的

在 `core/src/main/java/org/apache/iceberg/util/TableScanUtil.java` 中，为 `hasDeletes` 和 `hasEqDeletes` 两个方法分别添加 `@Deprecated` 注解和 Javadoc `@deprecated` 标签，注明弃用版本（1.11.0）和计划移除版本（1.12.0）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/TableScanUtil.java` (+7/-0 lines)

**修改目的**：标记两个未使用的公共方法为弃用。

**工作逻辑**：
- 为 `hasDeletes(CombinedScanTask task)` 添加：
  ```java
  /**
   * @deprecated since 1.11.0 and will be removed in 1.12.0
   */
  @Deprecated
  public static boolean hasDeletes(CombinedScanTask task) {
  ```
  该方法通过流式判断任务中是否任一文件包含删除（`task.files().stream().anyMatch(TableScanUtil::hasDeletes)`）。

- 为 `hasEqDeletes(CombinedScanTask task)` 添加：
  ```java
  /**
   * This is temporarily introduced since we plan to support pos-delete vectorized read first, then
   * get to the equality-delete support. We will remove this method once both are supported.
   *
   * @deprecated since 1.11.0 and will be removed in 1.12.0
   */
  @Deprecated
  public static boolean hasEqDeletes(CombinedScanTask task) {
  ```
  该方法保留了原有的临时性说明（为支持 position-delete 向量化读而临时引入），并补充了弃用标注。方法体判断任务文件中是否包含 equality delete。

两个方法的方法体均未改动，仅添加注解和文档。

## 总结

本次提交为 `TableScanUtil` 中两个已无内部调用的公共方法（`hasDeletes` 和 `hasEqDeletes`）添加 `@Deprecated` 标注，明确弃用版本（1.11.0）和移除计划（1.12.0）。这是标准的 API 生命周期管理操作，为后续版本清理无用公共 API 做准备，同时提醒下游使用者避免依赖这些方法。
