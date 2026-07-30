# 提交 1361：Spark: Exclude reading _pos column if it's not in the scan list (#11390)

## 提交信息

- **序号**：1361 / 4088
- **哈希**：1c576c5952fbc623591e800408cdba1518e6a410
- **短哈希**：1c576c595
- **日期**：2024-11-08（Fri Nov 8 15:57:37 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: Exclude reading _pos column if it's not in the scan list (#11390)
- **PR/Issue**：#11390

## 总体目的

Iceberg 在读取带删除文件的数据文件时，需要在读取出的行上应用位置删除（按行号 `_pos` 过滤被删行）。为此，`DeleteFilter.fileProjection` 会在用户请求的投影 Schema 基础上自动补齐 `_pos`（`MetadataColumns.ROW_POSITION`）等元数据列，确保读取数据文件时能拿到行号用于删除判定。

Spark 的 `BatchDataReader`（向量化批量读取路径）在读取 Parquet 时，会把 `DeleteFilter.requiredSchema()` 直接作为 Parquet 读取的投影。但 `requiredSchema()` 在 `DeleteFilter` 内部还会被 `ReadConf.generateOffsetToStartPos` 等下游逻辑使用——这些逻辑需要 `_pos` 列存在以计算行偏移。当用户扫描列中**不包含** `_pos` 时，`requiredSchema()` 是否含 `_pos` 取决于 `DeleteFilter` 是否补齐了它，而这又取决于是否存在位置删除文件。

此前的实现存在两个问题：

1. `DeleteFilter` 一旦发现存在位置删除文件（`!posDeletes.isEmpty()`），就无条件把 `_pos` 加入 `requiredSchema`，即使该读取路径实际上不需要 `_pos`（例如 Spark 向量化批量读取在 `#10107` 合并前的过渡期，下游 `ReadConf.generateOffsetToStartPos` 仍依赖 `_pos`，但用户扫描并未请求 `_pos`，多读一列纯属浪费 IO）。
2. `BatchDataReader` 直接用 `requiredSchema()` 作为 Parquet 投影，没有在"需要为下游补 `_pos`"与"仅用于删除过滤"两种语义间区分。

本提交通过引入 `needRowPosCol` 布尔参数，把"是否要把 `_pos` 加入 requiredSchema"的决策权交给调用方：

- `BatchDataReader`（向量化批量读取）传 `false`——它不需要 `_pos` 进入 requiredSchema 用于删除过滤（向量化路径另有处理），但仍需 `_pos` 存在于传给 Parquet 读取的投影中以喂给 `ReadConf.generateOffsetToStartPos`，因此在 `BaseBatchReader` 中显式补 `_pos` 到 Parquet 投影。
- 其他读取器（`RowDataReader`、`ChangelogRowReader`、`EqualityDeleteRowReader`）传 `true`——这些行式读取路径仍需要 `_pos` 在 requiredSchema 中用于删除过滤。

这样 `BatchDataReader` 的 `requiredSchema()` 不再含多余的 `_pos`，同时通过 `BaseBatchReader` 的显式补列保证下游 `ReadConf` 逻辑仍能拿到 `_pos`。

## 如何达成设计目的

通过在 `DeleteFilter` 构造器与 `fileProjection` 中新增 `needRowPosCol` 参数，让调用方决定是否把 `_pos` 加入 requiredSchema；在 `BaseBatchReader` 中根据是否存在位置删除，显式把 `_pos` 加到传给 Parquet 的投影（仅为 `ReadConf.generateOffsetToStartPos` 用，注释明确这是 `#10107` 合并前的过渡处理）；并在各 Spark 读取器（v3.3 / v3.4 / v3.5）的 `SparkDeleteFilter` 构造与调用处传入合适的 `needRowPosCol` 值。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java`

**修改目的**：让"是否把 `_pos` 加入 requiredSchema"可由调用方控制。

**工作逻辑**：

1. 新增带 `needRowPosCol` 参数的构造器：

   ```java
   protected DeleteFilter(
       String filePath,
       List<DeleteFile> deletes,
       Schema tableSchema,
       Schema requestedSchema,
       DeleteCounter counter,
       boolean needRowPosCol) {
     ...
     this.requiredSchema =
         fileProjection(tableSchema, requestedSchema, posDeletes, eqDeletes, needRowPosCol);
     ...
   }
   ```

   并保留原 5 参构造器，内部委托为新构造器并传 `needRowPosCol = true`（默认行为不变，向后兼容）：

   ```java
   protected DeleteFilter(
       String filePath, List<DeleteFile> deletes, Schema tableSchema,
       Schema requestedSchema, DeleteCounter counter) {
     this(filePath, deletes, tableSchema, requestedSchema, counter, true);
   }
   ```

2. `fileProjection` 新增 `needRowPosCol` 参数，把原来"有位置删除就加 `_pos`"改为"needRowPosCol 且有位置删除才加 `_pos`"：

   ```java
   if (needRowPosCol && !posDeletes.isEmpty()) {
     requiredIds.add(MetadataColumns.ROW_POSITION.fieldId());
   }
   ```

   当 `needRowPosCol=false` 时，`_pos` 不会进入 requiredIds，因此 `requiredSchema` 不会包含 `_pos`（除非用户请求的 requestedSchema 本身就有 `_pos`）。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：在向量化批量读取路径中，当存在位置删除时显式把 `_pos` 加到传给 Parquet 的投影，喂给 `ReadConf.generateOffsetToStartPos`。

**工作逻辑**：在 `newBatchIterable` 前增加：

```java
Schema requiredSchema = deleteFilter != null ? deleteFilter.requiredSchema() : expectedSchema();
boolean hasPositionDelete = deleteFilter != null ? deleteFilter.hasPosDeletes() : false;
Schema projectedSchema = requiredSchema;
if (hasPositionDelete) {
  // We need to add MetadataColumns.ROW_POSITION in the schema for
  // ReadConf.generateOffsetToStartPos(Schema schema). This is not needed any
  // more after #10107 is merged.
  List<Types.NestedField> columns = Lists.newArrayList(requiredSchema.columns());
  if (!columns.contains(MetadataColumns.ROW_POSITION)) {
    columns.add(MetadataColumns.ROW_POSITION);
    projectedSchema = new Schema(columns);
  }
}

return Parquet.read(inputFile)
    .project(projectedSchema)
    ...
```

即：以 `requiredSchema` 为基础，若存在位置删除且 `requiredSchema` 不含 `_pos`，则新建一个含 `_pos` 的 `projectedSchema` 传给 `Parquet.read`。注释明确这是 `#10107` 合并前的过渡方案——`#10107` 合并后 `ReadConf.generateOffsetToStartPos` 不再依赖 `_pos`，届时这层显式补列可移除。

同时新增 import：`java.util.List`、`Lists`、`Types`。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：让 `SparkDeleteFilter` 内部构造接受 `needRowPosCol` 并透传给 `DeleteFilter`。

**工作逻辑**：`SparkDeleteFilter` 构造器签名由

```java
SparkDeleteFilter(String filePath, List<DeleteFile> deletes, DeleteCounter counter) {
  super(filePath, deletes, tableSchema, expectedSchema, counter);
```

改为

```java
SparkDeleteFilter(
    String filePath, List<DeleteFile> deletes, DeleteCounter counter, boolean needRowPosCol) {
  super(filePath, deletes, tableSchema, expectedSchema, counter, needRowPosCol);
```

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：向量化批量读取路径传 `needRowPosCol = false`，让 `requiredSchema` 不含多余 `_pos`。

**工作逻辑**：

```java
SparkDeleteFilter deleteFilter =
    task.deletes().isEmpty()
        ? null
        : new SparkDeleteFilter(filePath, task.deletes(), counter(), false);
```

`_pos` 不再进入 `requiredSchema`，但仍由 `BaseBatchReader` 显式补到 Parquet 投影中供 `ReadConf` 使用。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/ChangelogRowReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：changelog 行读取路径传 `needRowPosCol = true`，保持原有行为。

**工作逻辑**：`openAddedRowsScanTask` 与 `openDeletedDataFileScanTask` 中的 `SparkDeleteFilter` 构造均加 `, true` 参数。这两个路径需要 `_pos` 在 requiredSchema 中用于删除过滤与 changelog 行定位。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/EqualityDeleteRowReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：等值删除行读取路径传 `needRowPosCol = true`，保持原有行为。

**工作逻辑**：`open` 方法中 `SparkDeleteFilter` 构造加 `, true`。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/RowDataReader.java`（及 v3.4、v3.5 同名文件）

**修改目的**：行式数据读取路径传 `needRowPosCol = true`，保持原有行为。

**工作逻辑**：`open` 方法中 `SparkDeleteFilter` 构造加 `, true`。

### 三套 Spark 版本同步

上述改动在 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 三个模块的对应文件中同步应用（共 7 个文件 × 3 套 = 21 个文件，其中 `data/DeleteFilter.java` 仅 1 处）。每个版本的 `BaseBatchReader`、`BaseReader`、`BatchDataReader`、`ChangelogRowReader`、`EqualityDeleteRowReader`、`RowDataReader` 改动完全一致。

## 小结

- **成效**：Spark 向量化批量读取路径（`BatchDataReader`）不再把 `_pos` 列无谓地加入 `DeleteFilter.requiredSchema`，避免了在用户扫描不含 `_pos` 时多读一列的 IO 浪费；同时通过 `BaseBatchReader` 显式补 `_pos` 到 Parquet 投影，保证 `ReadConf.generateOffsetToStartPos` 仍能拿到 `_pos`（`#10107` 合并前的过渡方案）。其他行式读取路径行为不变。三套 Spark 版本（3.3 / 3.4 / 3.5）同步更新。
- **影响范围**：共 19 个文件（`data/DeleteFilter.java` 1 个 + 三个 Spark 版本各 6 个），+94 / -28 行。改动集中在 `DeleteFilter` 构造器签名与各 Spark 读取器对 `SparkDeleteFilter` 的构造调用，逻辑清晰、向后兼容（保留原 5 参构造器）。
- **回迁到 1.4.x 的注意事项**：1.4.x 维护分支同样支持 Spark 3.3 / 3.4 / 3.5，其 `BatchDataReader` / `DeleteFilter` 等代码结构与本提交修改前的 main 一致，存在同样的多读 `_pos` 问题。**理论上可以回迁**此优化以减少 1.4.x 上 Spark 向量化读取的 IO。但需注意：(1) 1.4.x 的 `BaseBatchReader` 是否已包含 `#10107` 相关改动——若 1.4.x 早于 `#10107`，则 `ReadConf.generateOffsetToStartPos` 仍依赖 `_pos`，本提交中 `BaseBatchReader` 的显式补列逻辑必须一并回迁，否则会破坏向量化读取的行偏移计算；(2) 1.4.x 的 `DeleteFilter` 可能与 main 略有差异（如 DV 支持等），回迁时需以 1.4.x 当时的代码为基线调整；(3) 这是一个性能优化（非正确性修复），优先级低于 Bug 修复。建议在 1.4.x 上评估 `#10107` 是否已存在后再决定是否回迁——若 `#10107` 已在 1.4.x，则可简化回迁（移除 `BaseBatchReader` 的过渡补列）；若未在，则需完整回迁本提交。回迁后应运行 Spark 读取相关测试（尤其向量化 + 位置删除场景）验证。
