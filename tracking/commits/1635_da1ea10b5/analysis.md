# 提交 1635 da1ea10b5 分析

## 提交信息
- 哈希：da1ea10b58b7f023edee6147a5afc4f70a257da4
- 日期：2025-01-24 09:13:41 -0800
- 作者：Huaxin Gao
- 消息：Spark 3.4: Refactor delete logic in batch reading (#12061)（cherry-pick #11933 到 Spark 3.4）

## 总体目的

本提交对 Spark 3.4 模块中向量化批读取（vectorized batch reading）的“删除行处理逻辑”进行重构，把原本内嵌在 `ColumnarBatchReader.ColumnBatchLoader` 中、与向量构建强耦合的位置删除（position delete）与等值删除（equality delete）处理，抽取到独立的工具类 `ColumnarBatchUtil` 中，并调整了向量构建流程，使“读取向量”与“应用删除过滤”两个阶段解耦。

重构后，删除处理逻辑更清晰、可复用、可独立测试，也为后续（如 V3 表、行级血缘等）对删除逻辑的扩展打下更整洁的基础。本次提交明确是从 main 上的 #11933 cherry-pick 到 Spark 3.4，目的是让 3.4 与 3.5 的实现保持一致。

本提交是纯重构，不改变对外行为（读取结果、删除语义均不变），仅改变内部代码组织。

## 如何达成设计目的

设计思路：把删除处理拆成三个独立、职责单一的静态方法，集中到 `ColumnarBatchUtil`：
1. `buildRowIdMapping` —— 当不需要对外暴露“是否删除”列时，构建行号映射数组以跳过被删除行；
2. `buildIsDeleted` —— 当查询包含 `_deleted` 元数据列时，构建布尔数组标记每行是否被删除（不跳过行，仅标记）；
3. `removeExtraColumns` —— 等值删除需要额外读取用于过滤的列，输出时需移除这些额外列。

同时把“先建好带过滤的向量”改为“先读原始向量，再按需在批级别包裹过滤向量或注入 isDeleted 数组”，使删除判断只需遍历一次（原先位置删除与等值删除分两轮遍历）。

### 修改详情

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchUtil.java（新文件）

新增的静态工具类，包含三个公共方法和一个私有辅助方法：

1. `buildRowIdMapping(ColumnVector[], DeleteFilter, long rowStartPosInBatch, int batchSize)`：
   - 遍历 batch 中每一行，用 `isDeleted(pos, row, deletedPositions, eqDeleteFilter)` 同时判定位置删除与等值删除。
   - 未删除的行：把当前原始行号写入 `rowIdMapping[liveRowId]` 并 `liveRowId++`；被删除的行：`deletes.incrementDeleteCount()`。
   - 若 `liveRowId == batchSize`（无任何删除）返回 `null`，否则返回 `Pair.of(rowIdMapping, liveRowId)`。
   - 用 `ColumnarBatchRow(columnVectors)` 复用同一行对象，每行只更新 `row.rowId`，避免重复分配。
   - 注释用示例说明了位置删除+等值删除后映射数组的演变过程。

2. `buildIsDeleted(...)`：逻辑与 buildRowIdMapping 对称，但被删除行标记 `isDeleted[rowId]=true` 而非跳过，最终返回完整的 isDeleted 数组。用于 `_deleted` 元数据列场景——此时不物理跳过删除行，而是用标记让上层知道哪些行被删。

3. `removeExtraColumns(DeleteFilter, ColumnVector[])`：等值删除过滤可能要求读取 schema 之外的额外列（例如过滤条件引用了未被查询的列）。读取完成后，用 `deletes.expectedSchema().columns().size()` 作为期望列数，用 `Arrays.copyOf` 截断多余尾部列。

4. 私有 `isDeleted(pos, row, deletedPositions, eqDeleteFilter)`：先用独立 if 判位置删除（`deletedPositions.isDeleted(pos)`），再用独立 if 判等值删除（`!eqDeleteFilter.test(row)`）。注释说明用独立 if 而非 `||` 是为了“减少等值测试的推测执行（speculative execution）”几率——即位置删除命中时不必再算等值谓词。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java

`ColumnBatchLoader` 内部类大幅瘦身（约删除 130 行，新增 30 行）：
- 字段从 `numRowsToRead / rowIdMapping / isDeleted` 简化为 `batchSize`。
- `loadDataToColumnBatch()` 重写为新流程：
  1. `readDataToColumnVectors()` 读全部列向量（不再在构建向量时注入过滤）。
  2. 若 `hasIsDeletedColumn`：调 `ColumnarBatchUtil.buildIsDeleted(...)` 得到 isDeleted 数组，遍历向量把其中的 `DeletedColumnVector` 调 `setValue(isDeleted)` 注入标记。行数保持 batchSize（不跳过）。
  3. 否则：调 `ColumnarBatchUtil.buildRowIdMapping(...)`，若返回非 null，遍历向量把 `IcebergArrowColumnVector` 替换为 `ColumnVectorWithFilter(vector, rowIdMapping)`，并更新 `numLiveRows`。
  4. 若有等值删除：调 `ColumnarBatchUtil.removeExtraColumns(...)` 截断额外列。
  5. 用最终向量构造 `ColumnarBatch` 并 `setNumRows(numLiveRows)`。
- 删除了原来的 `hasEqDeletes()`、`initRowIdMapping()`、`posDelRowIdMapping()`、`buildPosDelRowIdMapping()`、`initEqDeleteRowIdMapping()`、`applyEqDelete()` 等方法——这些逻辑已迁移到 `ColumnarBatchUtil`，且原先“位置删除先建映射、等值删除再二次过滤映射”的两阶段流程被合并为单次遍历。
- `readDataToColumnVectors()` 中把 `numRowsToRead` 重命名为 `batchSize`，并移除了 `columnVectorBuilder.withDeletedRows(...)` 调用（该方法已删）。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnVectorBuilder.java

- 删除字段 `isDeleted`、`rowIdMapping` 及方法 `withDeletedRows(...)`。
- `build(...)` 简化：dummy 的 `DeletedVectorHolder` 分支改为 `new DeletedColumnVector(type)`（不再传 isDeleted）；移除 `rowIdMapping != null` 分支（不再在构建期包 `ColumnVectorWithFilter`，改由 ColumnBatchLoader 在批级别处理）；其余分支不变。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/DeletedColumnVector.java

- `isDeleted` 字段从 `final` 改为可变，移除构造参数与 `Preconditions` 非空校验。
- 新增 `setValue(boolean[] deleted)` 方法，用于在向量构建后再注入删除标记数组。
- 这样 `DeletedColumnVector` 可以先以空壳创建（在 readDataToColumnVectors 阶段），稍后再 `setValue` 填充实际标记，配合上述“先读后过滤”流程。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java

- 新增 `holder` 字段（final）保存构造时传入的 `VectorHolder`。
- 新增 `vector()` 访问器返回该 holder。
- 用途：ColumnBatchLoader 在批级别需要把 `IcebergArrowColumnVector` 重新包装为 `ColumnVectorWithFilter`，需要拿到底层 holder/vector，因此暴露该访问器。

## 小结

- 成效：删除处理逻辑被集中到 `ColumnarBatchUtil`，职责单一、注释充分（含示例）；`ColumnarBatchReader` 大幅瘦身；位置删除与等值删除合并为单次遍历，逻辑更直观。代码可维护性与可测试性提升，与 Spark 3.5 实现对齐。
- 影响范围：仅 Spark 3.4 向量化读取路径的内部重构，对外读取结果与删除语义不变。由于改变了 `ColumnVectorBuilder`/`DeletedColumnVector` 的构造方式与字段可见性，依赖这些内部类的同模块代码需同步调整（本提交已一并处理）。
- 回迁到 1.4.x 的注意事项：1.4.x 仅维护 Spark 3.5，而本提交是面向 Spark 3.4 的 cherry-pick。若 1.4.x 的 Spark 3.4 子模块仍在维护且尚未包含此重构，则可直接 cherry-pick 本提交；若 1.4.x 已放弃 Spark 3.4，则本提交无需回迁。回迁时需注意：`IcebergArrowColumnVector` 新增的 `vector()` 访问器与 `DeletedColumnVector.setValue` 是配套改动，必须整体回迁；同时确认 1.4.x 的 `DeleteFilter` API（`hasEqDeletes`/`eqDeletedRowFilter`/`expectedSchema` 等）与本提交用法一致。本提交无测试文件变更（属纯重构，依赖既有测试覆盖），回迁后建议运行向量化读取相关测试套件确认行为不变。
