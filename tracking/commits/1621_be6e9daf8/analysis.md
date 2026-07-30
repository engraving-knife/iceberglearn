# 提交 1621 be6e9daf8 分析

## 提交信息
- 哈希：be6e9daf8901cdee63197e77fcf95624bb694f39
- 日期：2025-01-22 11:15:44 -0800
- 作者：Huaxin Gao
- 消息：Spark 3.5: Refactor delete logic in batch reading (#11933)

## 总体目的

本提交重构 Spark 3.5 向量化读取路径中"应用删除（position deletes + equality deletes）"的逻辑，把原本散落在 `ColumnarBatchReader.ColumnBatchLoader` 内部的删除处理代码抽到独立的 `ColumnarBatchUtil` 工具类，并简化 `ColumnVectorBuilder`、`DeletedColumnVector` 等协作类的接口。

**背景**：Iceberg 的向量化读取（`ColumnarBatchReader`）在读取每个 ColumnarBatch 时，需要根据位置删除（position deletes）和等值删除（equality deletes）过滤掉已删除的行。Spark 的向量化读取有两种工作模式：

1. **过滤模式**（不带 `_deleted` 元数据列）：通过 `rowIdMapping` 数组把已删除行"挤掉"，最终 `ColumnarBatch` 的行数 < 原始批次行数。`ColumnVectorWithFilter` 包装每个 `ColumnVector`，按 `rowIdMapping` 重定向读取。
2. **标记模式**（带 `_deleted` 元数据列，用于 COPY-ON-WRITE 的 MERGE/UPDATE 等场景）：不真正过滤行，而是把每个原始行都保留在 batch 中，由 `DeletedColumnVector` 提供 `_deleted` 列的布尔值，让上层根据该列决定行是否可见。

重构前的代码存在几个问题：
- 删除处理逻辑（`initRowIdMapping`、`posDelRowIdMapping`、`buildPosDelRowIdMapping`、`initEqDeleteRowIdMapping`、`applyEqDelete`、`removeExtraColumns`）全部塞在 `ColumnBatchLoader` 内部类中，方法之间通过可变实例字段（`rowIdMapping`、`isDeleted`）传递状态，难以理解、难以测试、容易出错。
- 位置删除和等值删除分两步应用：先用 `buildPosDelRowIdMapping` 生成位置删除后的 `rowIdMapping`，再用 `applyEqDelete` 在该 mapping 基础上进一步压缩。两步式逻辑要求 `applyEqDelete` 通过 `rowIdMapping[rowId]` 间接访问原始行下标，且要在 `hasIsDeletedColumn && rowIdMapping != null` 时额外"重置 mapping"以保留全部行（用于标记模式），逻辑分支多且相互交织。
- `ColumnVectorBuilder.withDeletedRows(rowIdMapping, isDeleted)` 把两种模式的状态都耦合到 builder 中，`DeletedColumnVector` 在构造时就必须传入 `isDeleted` 数组，无法延迟设置。

本提交的目标是把这些逻辑拆分到 `ColumnarBatchUtil` 的两个清晰入口——`buildRowIdMapping`（过滤模式）和 `buildIsDeleted`（标记模式）——一次性同时应用位置删除和等值删除，消除两步式逻辑与可变实例字段，让 `ColumnarBatchReader` 的主流程更易读、更易维护。

## 如何达成设计目的

设计思路是"先读所有列向量为 `IcebergArrowColumnVector`，再按删除模式后处理"：

1. **读阶段**：`readDataToColumnVectors` 用 `ColumnVectorBuilder.build(holder, numRows)` 把每个 `VectorHolder` 转成 `IcebergArrowColumnVector`（或 dummy 向量如 `DeletedColumnVector`），不再在 builder 中传入 `rowIdMapping`/`isDeleted`。`ColumnVectorBuilder` 退化为单纯的"按 holder 类型选 ColumnVector 实现类"工厂。

2. **删除应用阶段**：在 `loadDataToColumnBatch` 中按是否带 `_deleted` 列分两路：
   - **标记模式**：调用 `ColumnarBatchUtil.buildIsDeleted` 一次性算出 `boolean[] isDeleted`，然后遍历所有 `ColumnVector`，对其中是 `DeletedColumnVector` 的实例调用 `setValue(isDeleted)` 注入该数组。batch 行数保持 `batchSize`。
   - **过滤模式**：调用 `ColumnarBatchUtil.buildRowIdMapping` 一次性算出 `int[] rowIdMapping` 与存活行数 `numLiveRows`。若返回 null（无删除），batch 保持原样；否则遍历所有 `ColumnVector`，对其中是 `IcebergArrowColumnVector` 的实例用 `new ColumnVectorWithFilter(vector.vector(), rowIdMapping)` 包装一层。最后 `setNumRows(numLiveRows)`。

3. **等值删除额外列清理**：若 `deletes.hasEqDeletes()`，调用 `ColumnarBatchUtil.removeExtraColumns(deletes, arrowColumnVectors)` 截掉末尾用于等值删除判定但不在最终输出 schema 中的列。

4. **新增 `IcebergArrowColumnVector.vector()` getter**：因为现在需要在已经构造好的 `IcebergArrowColumnVector` 之外（即删除处理阶段）再包装 `ColumnVectorWithFilter`，而后者构造需要原始 `VectorHolder`，所以给 `IcebergArrowColumnVector` 加一个 `vector()` 方法暴露 holder。

5. **`DeletedColumnVector` 改为延迟设置**：构造时只传类型，`isDeleted` 字段改为非 final，新增 `setValue(boolean[])` 方法在删除处理阶段注入。这避免了 `ColumnVectorBuilder` 必须提前知道 `isDeleted` 数组。

`ColumnarBatchUtil.buildRowIdMapping` 与 `buildIsDeleted` 共享同一个 `isDeleted(pos, row, deletedPositions, eqDeleteFilter)` 私有方法：用 `ColumnarBatchRow` 在遍历时复用同一个 row 对象（只更新 `rowId` 字段）以减少 GC，先查位置删除（用绝对位置 `rowStartPosInBatch + rowId`），再查等值删除（用 `Predicate<InternalRow>` 测试当前行）。两个 if 分支独立判断以减少分支预测的开销（注释说明）。返回值统一用 `liveRowId == batchSize ? null : Pair.of(...)` 表示"是否有删除发生"。

### 修改详情

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchUtil.java（新文件，160 行）

新工具类，提供三个静态方法：

- `buildRowIdMapping(ColumnVector[], DeleteFilter, long rowStartPosInBatch, int batchSize)`：返回 `Pair<int[], Integer>` 或 null。遍历 batch 中每一行，对未删除行写入 `rowIdMapping[liveRowId] = rowId` 并递增 `liveRowId`，对已删除行调用 `deletes.incrementDeleteCount()`。若全部存活返回 null（调用方据此跳过包装）。
- `buildIsDeleted(ColumnVector[], DeleteFilter, long rowStartPosInBatch, int batchSize)`：返回 `boolean[] isDeleted`。遍历每一行，对已删除行设置 `isDeleted[rowId] = true` 并递增删除计数。即使 `deletes == null` 也返回全 false 数组（标记模式下需要 `_deleted` 列存在且全 false）。
- `removeExtraColumns(DeleteFilter, ColumnVector[])`：用 `deletes.expectedSchema().columns().size()` 截断列向量数组，去掉等值删除判定用的额外列。
- 私有 `isDeleted(long pos, InternalRow row, PositionDeleteIndex, Predicate<InternalRow>)`：先查位置删除（按绝对位置），再查等值删除（按行内容），任一命中即视为已删除。

这些方法把原本散落在 `ColumnBatchLoader` 中的所有删除算法集中到一处，且通过参数显式传入所有依赖（无实例状态），便于单元测试。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java

`ColumnBatchLoader` 内部类大幅瘦身（从约 220 行减到约 70 行）：
- 删除 `rowIdMapping`、`isDeleted` 实例字段，仅保留 `batchSize`；
- 删除 `initRowIdMapping`、`posDelRowIdMapping`、`buildPosDelRowIdMapping`、`initEqDeleteRowIdMapping`、`applyEqDelete`、`hasEqDeletes`、`removeExtraColumns` 等方法；
- 删除 `import` 的 `Arrays`、`Iterator`、`PositionDeleteIndex`；
- `loadDataToColumnBatch` 改为：先 `readDataToColumnVectors` → 按 `hasIsDeletedColumn` 调用 `buildIsDeleted` 或 `buildRowIdMapping` 做后处理 → 若有等值删除调 `removeExtraColumns` → 构造 `ColumnarBatch` 并 `setNumRows`。
- `readDataToColumnVectors` 中 `ColumnVectorBuilder` 调用去掉 `.withDeletedRows(...)` 链式调用，直接 `columnVectorBuilder.build(vectorHolders[i], numRowsInVector)`。

修改后主流程清晰：读 → 算删除 → 包装/注入 → 截额外列 → 返回。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnVectorBuilder.java

- 删除 `isDeleted`、`rowIdMapping` 实例字段及 `withDeletedRows(int[], boolean[])` 方法；
- `build` 方法简化为：dummy holder 且是 `DeletedVectorHolder` → `new DeletedColumnVector(type)`（不再传 isDeleted）；dummy 且是 `ConstantVectorHolder` → `ConstantColumnVector`；否则 → `new IcebergArrowColumnVector(holder)`。不再有 `rowIdMapping != null` 分支（过滤包装移到 `ColumnarBatchReader` 中按需做）。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/DeletedColumnVector.java

- `isDeleted` 字段从 `final` 改为非 final；
- 构造函数 `DeletedColumnVector(Type type, boolean[] isDeleted)` 改为 `DeletedColumnVector(Type type)`，删除 `Preconditions.checkArgument(isDeleted != null, ...)` 校验；
- 新增 `setValue(boolean[] deleted)` 方法用于延迟注入 isDeleted 数组；
- 删除 `Preconditions` 与 `relocated.com.google.common.base.Preconditions` 的 import。

这让 `DeletedColumnVector` 可以在构造时只指定类型，等删除处理阶段算出 isDeleted 数组后再注入，与新的"先构造后处理"流程匹配。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java

- 新增 `private final VectorHolder holder` 字段，在构造函数中赋值；
- 新增 `public VectorHolder vector()` getter，暴露内部 holder。

该 getter 让 `ColumnarBatchReader` 在删除处理阶段能从已构造的 `IcebergArrowColumnVector` 拿回 `VectorHolder`，进而构造 `ColumnVectorWithFilter(holder, rowIdMapping)` 包装层。

## 小结

此次重构把 Spark 3.5 向量化批读取中的删除处理逻辑从 `ColumnarBatchReader` 内部类抽到独立的 `ColumnarBatchUtil`，统一了位置删除与等值删除的应用方式（一次性遍历同时检查两种删除），消除了两步式逻辑与可变实例字段，简化了 `ColumnVectorBuilder` 与 `DeletedColumnVector` 的接口。重构后代码行数减少（净 -191 + 210 = +19 行，但 `ColumnarBatchReader` 本身从约 220 行降到约 70 行），可读性与可测试性显著提升。

行为上保持等价：标记模式仍保留全部行并通过 `_deleted` 列标识删除状态；过滤模式仍通过 `rowIdMapping` 压缩 batch 行数。重构后的删除计数（`deletes.incrementDeleteCount`）逻辑与原有一致，等值删除额外列的截断逻辑也保留。

影响范围：仅限 Spark 3.5 模块的向量化读取路径（`spark/v3.5/spark/.../vectorized/`）。非向量化读取路径、其他 Spark 版本（3.3/3.4）、Flink、Core 等模块不受影响。本提交未附带新测试，依赖既有向量化读取测试（如 `TestParquetVectorizedReads`）覆盖回归。

回迁到 1.4.x 分支的注意事项：1.4.x 通常较老，其 Spark 3.5 模块（若存在）的向量化读取代码可能与本提交前的 main 分支已有较大差异。回迁前需确认 1.4.x 中：
- `ColumnarBatchReader.ColumnBatchLoader` 的结构是否与本提交前一致（若 1.4.x 已经有不同重构，则不能机械套用）；
- `DeleteFilter` 是否提供 `deletedRowPositions()`、`eqDeletedRowFilter()`、`expectedSchema()`、`hasEqDeletes()`、`hasPosDeletes()`、`incrementDeleteCount()` 等方法；
- `ColumnarBatchRow` 是否可公开构造（重构中 `ColumnarBatchUtil` 直接 `new ColumnarBatchRow(columnVectors)` 并修改 `row.rowId` 字段，需要该字段可访问）；
- `VectorHolder`、`ColumnVectorWithFilter`、`IcebergArrowColumnVector` 等类的接口是否兼容。

由于这是纯重构（无行为改变、无新功能），回迁的主要价值是降低 1.4.x 后续维护成本。若 1.4.x 已不再接受重构性改动（只接受 bug fix），则可跳过本提交。若要回迁，建议先在 1.4.x 上跑完整的向量化读取测试套件确认行为等价。注意 `ColumnarBatchRow.rowId` 字段的访问权限可能需要调整（在 main 分支该字段是包级可见的，`ColumnarBatchUtil` 与 `ColumnarBatchRow` 同包即可访问）。
