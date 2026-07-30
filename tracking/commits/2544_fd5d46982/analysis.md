# 提交 2544：Flink: Backport#13878 custom source parallelism (#13894)

## 提交信息

- **序号**：2544 / 4088
- **哈希**：fd5d46982e48252fecaf186531660ac509b43bbe
- **短哈希**：fd5d46982
- **日期**：2025-08-21 17:27:57 -0700
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport#13878 custom source parallelism (#13894)
- **PR/Issue**：#13894（backport of #13878）

## 总体目的

#13878（提交 2541）为 Flink v2.0 Iceberg source 实现了通过 `scan.parallelism` 表属性或查询 hint 自定义 source 并行度的能力。Flink 1.19 与 1.20 模块同样需要该能力，本提交把 #13878 的实现原样 backport 到这两个版本，让 1.19/1.20 用户也能为 Iceberg source 单独配置并行度。

backport 内容与 v2.0 完全一致：在 `IcebergTableSource` 的 `Source` 匿名类中重写 `getParallelism()`，从 properties 读取 `FactoryUtil.SOURCE_PARALLELISM`（`scan.parallelism`），并新增三个测试覆盖表属性、hint、hint 覆盖表属性三种场景。

## 如何达成设计目的

- 在 `flink/v1.19` 与 `flink/v1.20` 的 `IcebergTableSource.java` 中：
  - 新增 `FactoryUtil` 与 `PropertyUtil` import。
  - 在 `Source` 匿名类中重写 `getParallelism()`，返回 `Optional.ofNullable(PropertyUtil.propertyAsNullableInt(properties, FactoryUtil.SOURCE_PARALLELISM.key()))`。
- 在两个版本的 `TestStreamScanSql.java` 中：
  - 保存 `defaultJobParallelism` 作为基准。
  - 新增 `testWithParallelismWithProps`、`testWithParallelismWithHints`、`testWithParallelismHintsOverride` 三个测试，通过 `explain(JSON_EXECUTION_PLAN)` 断言 source 算子并行度符合预期。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (+8)

**修改目的**：1.19 支持 `scan.parallelism`。

**工作逻辑**：重写 `getParallelism()` 从 properties 读取 `scan.parallelism`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java` (+56)

**修改目的**：1.19 测试覆盖三种场景。

**工作逻辑**：与 v2.0 测试一致，通过执行计划断言 source 并行度。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java` (+8)

**修改目的**：1.20 支持 `scan.parallelism`。

**工作逻辑**：同 1.19。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java` (+56)

**修改目的**：1.20 测试覆盖三种场景。

**工作逻辑**：同 1.19。

## 总结

将 #13878 的 `scan.parallelism` source 并行度配置能力 backport 到 Flink 1.19 与 1.20，在 `IcebergTableSource` 重写 `getParallelism()` 读取该属性，并复制三个测试用例覆盖表属性、hint、hint 覆盖表属性场景，使三个 Flink 版本行为一致。
