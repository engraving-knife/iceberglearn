# 提交 1456：Spark: Remove extra columns for ColumnarBatch (#11551)

## 提交信息

- **序号**：1456 / 4088
- **哈希**：af8e3f5a40f4f36bbe1d868146749e2341471586
- **短哈希**：af8e3f5a4
- **日期**：2024-12-02（Mon Dec 2 17:26:27 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: Remove extra columns for ColumnarBatch (#11551)
- **PR/Issue**：#11551

## 总体目的

Iceberg 在向量化读取（ColumnarBatch）路径下处理 equality delete（等值删除）时，需要把"删除条件涉及的列"也读出来，才能判断哪些行被删除。例如表有 C1-C5 五列，用户查询 `SELECT C5 FROM table`，但存在针对 C3、C4 列的等值删除文件——此时实际读取的 schema 会扩展为 `{C5, C3, C4}`（即 `requiredSchema`），以便在读出的批次上执行等值删除过滤。

问题在于：在 Spark 向量化路径（`ColumnarBatchReader`）中，等值删除过滤执行完毕后，这些"仅为过滤而引入的额外列"（C3、C4）**没有被移除**，导致最终返回给 Spark 上层的 `ColumnarBatch` 包含了用户未请求的列。这与非向量化路径的行为不一致，也可能导致 Spark 上层（如 `ColumnarToRow` 等）因列数与预期 schema 不匹配而出现错误或返回多余数据。

本提交在 `ColumnarBatchReader` 中，于等值删除过滤之后、返回批次之前，增加一步 `removeExtraColumns`：根据用户真正期望的 `expectedSchema` 截断 `ColumnarBatch` 的列向量数组，只保留用户请求的列。同时在 `DeleteFilter` 中把原 `requestedSchema` 参数重命名为 `expectedSchema` 并暴露为字段/getter，供 reader 获取期望列数。

## 如何达成设计目的

通过三处协同改动：

1. **`DeleteFilter` 暴露 `expectedSchema`**：把构造参数 `requestedSchema` 重命名为 `expectedSchema`（语义更准确——这是最终期望输出的 schema），存为实例字段并提供 `expectedSchema()` getter；同时把内部 `requiredSchema`（文件投影时实际读取的 schema，可能含删除列）的计算源从 `requestedSchema` 改为 `expectedSchema`（指向同一对象，仅命名调整）。
2. **`ColumnarBatchReader` 增加 `removeExtraColumns`**：在 `ColumnBatchLoader.loadDataToColumnBatch` 中，当存在等值删除时，在 `applyEqDelete` 之后调用 `removeExtraColumns`，按 `expectedSchema` 的列数截断列向量数组并构造新的 `ColumnarBatch`。
3. **测试覆盖**：新增 `testEqualityDeleteWithDifferentScanAndDeleteColumns`，构造"期望列（id）≠ 删除列（dt）"的场景，断言返回的 `ColumnarBatch` 只含 1 列（id，IntegerType）。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java`（修改，+5 行）

**修改目的**：让 `DeleteFilter` 持有并暴露用户期望的输出 schema，供 reader 在过滤后截断多余列。

**工作逻辑**：

- 新增实例字段 `private final Schema expectedSchema;`
- 构造函数参数 `requestedSchema` 重命名为 `expectedSchema`，并在函数体中 `this.expectedSchema = expectedSchema;`；
- `requiredSchema` 的计算 `fileProjection(tableSchema, expectedSchema, posDeletes, eqDeletes, needRowPosCol)` 改为用 `expectedSchema`（原为 `requestedSchema`，语义不变）；
- 新增 getter：
```java
public Schema expectedSchema() {
  return expectedSchema;
}
```

`requiredSchema` 与 `expectedSchema` 的关系：`requiredSchema` 是"为完成删除过滤而需从文件读取的 schema"（可能比 `expectedSchema` 多出删除条件列、行位置列、`_deleted` 列等）；`expectedSchema` 是"用户最终想看到的列"。本提交让 reader 能区分二者。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`（修改，+31 行）

**修改目的**：在等值删除过滤后，从 `ColumnarBatch` 中移除仅为过滤而引入的额外列。

**工作逻辑**：

1. 在 `ColumnBatchLoader.loadDataToColumnBatch` 中，`applyEqDelete` 之后新增一行：
```java
if (hasEqDeletes()) {
  applyEqDelete(newColumnarBatch);
  newColumnarBatch = removeExtraColumns(arrowColumnVectors, newColumnarBatch);
}
```

2. 新增方法 `removeExtraColumns`（位于 `ColumnBatchLoader` 内部类中）：
```java
ColumnarBatch removeExtraColumns(
    ColumnVector[] arrowColumnVectors, ColumnarBatch columnarBatch) {
  int expectedColumnSize = deletes.expectedSchema().columns().size();
  if (arrowColumnVectors.length > expectedColumnSize) {
    ColumnVector[] newColumns = Arrays.copyOf(arrowColumnVectors, expectedColumnSize);
    return new ColumnarBatch(newColumns, columnarBatch.numRows());
  } else {
    return columnarBatch;
  }
}
```

逻辑说明：
- `deletes.expectedSchema()` 从 `DeleteFilter` 获取用户期望的列数；
- 若实际读出的列向量数（`arrowColumnVectors.length`，对应 `requiredSchema`）大于期望列数，说明有额外列（删除条件列）需要移除；
- 用 `Arrays.copyOf` 截断数组到 `expectedColumnSize`，构造新的 `ColumnarBatch`（行数不变）；
- 若无额外列（例如删除条件列本就在用户请求中），直接返回原 batch。

注意额外列总是排在数组末尾——这由 `BaseBatchReader.newParquetIterable` 中 `requiredSchema` 的构造方式保证：`fileProjection` 把 `expectedSchema` 的列放在前面，删除条件列追加在后。

### `data/src/test/java/org/apache/iceberg/data/DeleteReadTests.java`（修改，1 行）

**修改目的**：把 `initDateTable()` 从 `private` 改为 `protected`，使 Spark 子类测试能调用。

```java
-private void initDateTable() throws IOException {
+protected void initDateTable() throws IOException {
```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`（修改，+49 行）

**修改目的**：新增针对"期望列与删除列不同"场景的回归测试。

**工作逻辑**：`testEqualityDeleteWithDifferentScanAndDeleteColumns`：

1. 初始化日期表（含 `id`、`dt` 等列）；
2. 构造针对 `dt` 列的等值删除（删除 `2021-09-01`、`2021-09-02`、`2021-09-03` 三天的行），写入并提交；
3. 用 `BatchDataReader` 以 `expectedSchema = select("id")`（只读 id 列）扫描；
4. 断言每个返回的 `ColumnarBatch`：
   - `numCols() == 1`（只有 id 列，没有泄漏 dt 列）；
   - `column(0).dataType() == IntegerType`（确认是 id 列而非 dt 列）。

若无本提交的修复，`ColumnarBatch` 会包含 2 列（id + dt），断言失败。

## 小结

- **成效**：修复了 Spark 向量化读取路径下，等值删除过滤后额外列未被移除的问题，使返回的 `ColumnarBatch` 列数与用户请求的 schema 一致。这对 Spark 上层算子（如 `ColumnarToRow`、`Project` 等）正确处理列式数据至关重要。
- **影响范围**：涉及 `data` 模块（`DeleteFilter`，通用删除过滤基类，所有引擎共享）与 Spark 3.5 模块（`ColumnarBatchReader`、测试）。`DeleteFilter` 的改动仅是重命名参数 + 新增 getter，不改变 `requiredSchema` 的计算逻辑，向后兼容。注意：本提交仅修了 Spark 3.5 的向量化 reader；ORC 路径（`newOrcIterable`）未走 `DeleteFilter` 的 `requiredSchema`，不受影响；其他 Spark 版本（3.3/3.4）若存在相同问题需单独修复。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。`DeleteFilter` 在 `data` 模块（1.4.x 与 main 共享），重命名参数需同步检查 1.4.x 上所有 `DeleteFilter` 的调用方（如 `SparkDeleteFilter`、Flink/Trino 等子类构造）是否传参顺序仍正确（参数名变了但位置不变，编译期可检出）。`ColumnarBatchReader` 的 `removeExtraColumns` 依赖 `deletes.expectedSchema()`，需确保 1.4.x 的 `SparkDeleteFilter` 正确传递了 expectedSchema。测试用到的 `BatchDataReader`、`FileHelpers`、`TableScanUtil` 等在 1.4.x 中应已存在。若 1.4.x 维护多个 Spark 版本，应确认 3.3/3.4 是否也有同样问题并按需回迁。
