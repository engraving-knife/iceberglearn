# 提交 2429：Flink: Add support for filter in RewriteDataFiles (#13669)

## 提交信息

- **序号**：2429 / 4088
- **哈希**：d6f22d5a51500848bfb1b6feb515ebe3a2e8cd34
- **短哈希**：d6f22d5a5
- **日期**：2025-07-29 17:21:33 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Add support for filter in RewriteDataFiles (#13669)
- **PR/Issue**：#13669

## 总体目的

本提交为 Flink 维护（maintenance）API 中的 `RewriteDataFiles` 操作新增了 `filter` 支持，使得用户可以在触发数据文件重写时通过 Iceberg 表达式（`Expression`）限定哪些数据文件需要参与重写。

在以往实现中，`RewriteDataFiles` 的 `BinPackRewriteFilePlanner` 会对表中的所有数据文件进行扫描并参与重写计算。在大表或分区众多的场景下，这种"全量扫描"既增加了规划阶段的 I/O 成本，也可能重写那些用户并不希望触碰的文件。通过引入 filter 表达式，用户可以基于分区、列值等条件缩小重写范围，从而做到精细化的数据维护。

该 filter 表达式最终会被传递到底层的 `BinPackRewriteFilePlanner` 构造函数（`new BinPackRewriteFilePlanner(table, filter)`），由 planner 在生成 `FileScanTask` 时使用该表达式过滤文件，确保只有满足条件的文件才会进入重写流程。

## 如何达成设计目的

1. 在 `RewriteDataFiles.Builder` 中新增 `filter` 字段（默认 `Expressions.alwaysTrue()`）和 `filter(Expression)` 方法，遵循 builder 模式链式调用。
2. 在构建 `DataFileRewritePlanner` 算子时，将 `filter` 作为构造参数传入。
3. `DataFileRewritePlanner` 内部将 `filter` 传递给 `BinPackRewriteFilePlanner(table, filter)`，使底层 planner 在扫描阶段应用过滤。
4. 同步更新所有受影响的测试构造调用（`RewriteUtil`、`TestDataFileRewritePlanner`、`TestDataFileRewriteRunner`）以传入 `Expressions.alwaysTrue()` 保持原有行为。
5. 新增 `testRewriteWithFilter` 测试用例，验证 filter 表达式确实只重写满足条件的文件。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+18/-1 lines)

**修改目的**：在 Builder 中暴露 filter 配置并将其传递给 planner 算子。

**工作逻辑**：新增 `private Expression filter = Expressions.alwaysTrue()` 字段，默认值保证不指定 filter 时行为不变（重写所有文件）。新增 `filter(Expression newFilter)` 方法用于覆盖默认值。在 `build()` 流程中，将 `filter` 作为构造 `DataFileRewritePlanner` 的最后一个参数传入。同时新增 `Expression` 和 `Expressions` 的 import。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+9/-2 lines)

**修改目的**：让 planner 算子接收 filter 表达式并传递给底层 `BinPackRewriteFilePlanner`。

**工作逻辑**：在类中新增 `private final Expression filter` 字段，构造函数新增 `Expression filter` 参数并赋值。在 `processElement` 中创建 planner 时由 `new BinPackRewriteFilePlanner(table)` 改为 `new BinPackRewriteFilePlanner(table, filter)`，使得 planner 在扫描文件时应用该过滤条件。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+41/-0 lines)

**修改目的**：验证 filter 功能正确工作。

**工作逻辑**：新增 `testRewriteWithFilter` 测试。向表插入 4 条记录（id=1..4），每条一个文件。配置 filter 为 `Expressions.in("id", 1, 2)`，只重写 id 为 1、2 的文件。运行后断言文件数从 4 减少到 3（id=1、2 的两个小文件被合并为一个），同时表中的 4 条记录完整保留，证明 filter 只影响重写范围、不改变数据内容。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+4/-1 lines)

**修改目的**：适配 `DataFileRewritePlanner` 新增的 filter 构造参数。

**工作逻辑**：在创建 `DataFileRewritePlanner` 的 test harness 时传入 `Expressions.alwaysTrue()`，保持原测试行为不变。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+7/-2 lines)

**修改目的**：适配构造函数签名变更。

**工作逻辑**：两处创建 `DataFileRewritePlanner` 的调用均补充 `Expressions.alwaysTrue()` 参数。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+4/-1 lines)

**修改目的**：适配构造函数签名变更。

**工作逻辑**：创建 planner 时补充 `Expressions.alwaysTrue()` 参数。

## 总结

本提交为 Flink 的 `RewriteDataFiles` 维护操作增加了基于表达式的过滤能力，使用户可以精确控制哪些数据文件参与重写。这是一个实用性很强的功能增强，特别适用于大表的增量维护场景。实现方式简洁，通过 builder 链式配置并将表达式一路传递到底层 planner，同时默认值 `alwaysTrue()` 保证了向后兼容。配套测试覆盖了过滤生效的场景，并同步更新了所有受影响的测试构造调用。
