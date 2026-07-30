# 提交 2541：Flink: support source parallelism config via property or hint (#13878)

## 提交信息

- **序号**：2541 / 4088
- **哈希**：8ce6b9529bc466f7d8e0677e0d9e9485a770e361
- **短哈希**：8ce6b9529
- **日期**：2025-08-21 13:16:37 -0700
- **作者**：Swapna Marru
- **提交说明**：Flink: support source parallelism config via property or hint (#13878)
- **PR/Issue**：#13878

## 总体目的

Flink Iceberg source 之前没有暴露自定义 source 并行度的能力，用户只能依赖作业级 `parallelism` 或 Flink 的默认并行度，无法单独为 Iceberg source 设置合适的并行度。当 source 的 split 数量与作业并行度不匹配时，要么出现 source 算子空闲/过载，要么需要为整个作业调整并行度，影响其他算子。

Flink 的 `Source` Provider 接口提供 `getParallelism()` 方法，允许 source 动态返回期望并行度。本提交在 `IcebergTableSource` 的 `Source` 实现中重写 `getParallelism()`，从 source 的 properties 中读取 `FactoryUtil.SOURCE_PARALLELISM`（即 `scan.parallelism`）配置，若存在则返回该值作为 source 并行度。

由于 properties 既来自建表时的表属性，也来自查询时的 SQL hint（`OPTIONS('scan.parallelism'='N')`），且 hint 会覆盖表属性，因此用户既可以在 `CREATE TABLE ... WITH ('scan.parallelism'='N')` 中设定默认并行度，也可以在查询时通过 hint 临时调整，hint 优先级更高。

测试通过 `ExplainDetail.JSON_EXECUTION_PLAN` 解析执行计划，断言 source 算子的 `parallelism` 字段为预期值，覆盖三种场景：仅表属性、仅 hint、hint 覆盖表属性。

## 如何达成设计目的

- 在 `IcebergTableSource.createSource` 返回的 `Source<...>` 匿名类中重写 `getParallelism()`：
  - 用 `PropertyUtil.propertyAsNullableInt(properties, FactoryUtil.SOURCE_PARALLELISM.key())` 读取 `scan.parallelism`。
  - 返回 `Optional.ofNullable(...)`：有值则用，无值则空（让 Flink 走默认并行度）。
- 测试在 `TestStreamScanSql` 中新增三个用例：
  - `testWithParallelismWithProps`：建表时 `WITH ('scan.parallelism'=default+1)`，验证执行计划中 source 并行度为 `default+1`。
  - `testWithParallelismWithHints`：查询时 `OPTIONS('scan.parallelism'=default+1)`，验证生效。
  - `testWithParallelismHintsOverride`：建表设 `default+1`，查询 hint 设 `default+2`，验证 hint 覆盖（source 并行度为 `default+2`）。
- 测试通过 `table.explain(ExplainDetail.JSON_EXECUTION_PLAN)` 拿到 JSON 执行计划字符串，断言包含 `"parallelism" : <期望值>` 片段。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (+8)

**修改目的**：让 Iceberg source 支持 `scan.parallelism` 配置。

**工作逻辑**：在 `Source` 匿名类中新增 `@Override public Optional<Integer> getParallelism()`，从 `properties` 读取 `FactoryUtil.SOURCE_PARALLELISM`（key=`scan.parallelism`）作为 int，包装为 `Optional`。新增 `FactoryUtil` 与 `PropertyUtil` import。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java` (+56)

**修改目的**：覆盖三种并行度配置场景。

**工作逻辑**：
- 在 setup 中保存 `defaultJobParallelism = env.getParallelism()`，作为基准。
- 三个测试分别用表属性、hint、hint 覆盖表属性的方式设置 `scan.parallelism`，然后 `sqlQuery` + `explain(JSON_EXECUTION_PLAN)`，断言执行计划中 source 算子的 `parallelism` 字段为期望值。
- 新增 `ExplainDetail` import。

## 总结

在 Flink v2.0 Iceberg source 的 `Source` 实现中重写 `getParallelism()`，从 properties 读取 `scan.parallelism`，让用户可通过建表属性或查询 hint（后者优先）自定义 source 并行度。测试通过 JSON 执行计划断言覆盖三种配置场景。后续 #13894 会 backport 到 Flink 1.19/1.20。
