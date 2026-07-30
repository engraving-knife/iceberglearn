# 提交 2053：Spark 3.5: Update MERGE and UPDATE for row lineage (#12736)

## 提交信息

- **序号**：2053 / 4088
- **哈希**：829ae7a11dc1eb62246c801ce1c7e501356c5463
- **短哈希**：829ae7a11
- **日期**：2025-04-28 10:57:32 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.5: Update MERGE and UPDATE for row lineage (#12736)
- **PR/Issue**：#12736

## 总体目的

Iceberg v3 表格式引入了行级血缘（row lineage）能力，通过 `_row_id` 和 `_last_updated_sequence_number` 两个元数据列追踪每行数据的身份和最后更新序号。在 Spark 3.5 模块中，原有 `MERGE` 和 `UPDATE` 写入路径并未正确地把行血缘列携带到重写后的数据文件中——例如 COPY-ON-WRITE 与 MERGE-ON-READ（位置增量）写入时不会在 output 中包含行 ID 与序列号，导致行级血缘在更新/合并操作后丢失。

本提交为 Spark 3.5 实现 MERGE 与 UPDATE 操作对行血缘的完整支持：通过自定义 Spark 分析期规则在逻辑计划层注入行血缘列与赋值（保留原 row_id、将 last_updated_sequence_number 置空以便后续由 Iceberg 提交侧填充），并在写入器/扫描器层将行血缘列当作普通数据列处理以绕过 Spark 元数据列输出限制。配套新增了优化器规则用于清理写入计划 `originalTable` 上多出的血缘列，避免影响下游（如 relation caching）。同时移除了 `SparkTable` 中按 format version 条件返回血缘元数据列的限制，使血缘列在任何版本下都暴露为元数据列。

## 如何达成设计目的

整体设计思路：
1. **公共工具**：在 core 模块新增 `MetadataColumns.schemaWithRowLineage(Schema)`（将 ROW_ID 与 LAST_UPDATED_SEQUENCE_NUMBER 加入给定 schema）和 `TableUtil.supportsRowLineage(Table)`（判断表是否为非元数据表且 format version ≥ 3）。
2. **分析期规则**：新增 trait `RewriteOperationForRowLineage`（共享判断与属性查找逻辑），以及两个具体规则 `RewriteUpdateTableForRowLineage` 和 `RewriteMergeIntoTableForRowLineage`。它们在逻辑计划已 resolved 后，如果目标表支持行血缘且 output 中尚未包含血缘列，就把 row_id 与 last_updated_sequence_number 加入目标表 output，并为每个 UPDATE 赋值追加 `Assignment(rowId, rowId)`（保留原值）和 `Assignment(lastUpdatedSequenceNumber, Literal(null))`（留空让 Iceberg 在 commit 时填入新序号）。
3. **绕过元数据列限制**：`findRowLineageAttributes` 中通过 `removeMetadataColumnAttribute` 把血缘属性的 `__metadata_col` 元数据键移除，伪装为数据列，规避 Spark `ExposesMetadataColumns` 限制（一个元数据列在 output 时会抑制其他元数据列）。`SparkCopyOnWriteScan.readSchema()` 中同样实现 `rowLineageAsDataCols` 把血缘字段去元数据化。
4. **写入器/操作改造**：`SparkCopyOnWriteOperation` 与 `SparkPositionDeltaOperation` 的 `requiredMetadataAttributes()` 在表支持血缘时追加 ROW_ID 与 LAST_UPDATED_SEQUENCE_NUMBER。`SparkPositionDeltaWriteBuilder` 与 `SparkWriteBuilder` 在生成 data/metadata schema 时根据是否需要写入血缘来使用 `schemaWithRowLineage` 包裹的 schema。`SparkWriteBuilder` 区分 overwrite 与非 overwrite：仅 overwrite 才在输出文件里包含血缘列（其他场景新写的行 row_id 全为 null，无需写出血缘列）。
5. **优化器清理规则**：`RemoveRowLineageOutputFromOriginalTable` 在 `WriteDelta` 与 `ReplaceData` 的 `originalTable` 上过滤掉血缘列，避免影响物理规划与缓存。
6. **SparkTable 元数据列改造**：`SparkTable.metadataColumns()` 不再根据 `formatVersion >= 3` 才暴露血缘列，改为无条件暴露（让 Spark 总能 resolve 这些列），同时移除了原来对应的 V1/V2 解析失败测试。
7. **测试**：新增抽象类 `TestRowLevelOperationsWithLineage`（492 行）覆盖非分区/分区、COW/MOR 下 MERGE 与 UPDATE 的血缘保留、序号更新、firstRowId 分配等场景，`TestCopyOnWriteWithLineage` 与 `TestMergeOnReadWithLineage` 作为子类分别配置 COW/MOR 表属性；`SparkRowLevelOperationsTestBase` 增加一个 formatVersion=3 的参数化用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataColumns.java` (修改, +5/-0 lines)

**修改目的**：提供"在 schema 上叠加行血缘列"的工具方法。

**工作逻辑**：
新增静态方法 `schemaWithRowLineage(Schema schema)`，使用 `TypeUtil.join(schema, new Schema(ROW_ID, LAST_UPDATED_SEQUENCE_NUMBER))` 将两个血缘元数据列与给定 schema 合并，返回带血缘列的新 Schema，供写入器构造写入 schema 使用。

### `core/src/main/java/org/apache/iceberg/TableUtil.java` (修改, +9/-0 lines)

**修改目的**：提供"判断表是否支持行血缘"的工具方法。

**工作逻辑**：
新增 `supportsRowLineage(Table table)`：先校验非空，`BaseMetadataTable` 视为不支持（避免在元数据表上启用），否则通过 `formatVersion(table) >= TableMetadata.MIN_FORMAT_VERSION_ROW_LINEAGE` 判断（即 v3 及以上）。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/iceberg/spark/extensions/IcebergSparkSessionExtensions.scala` (修改, +6/-0 lines)

**修改目的**：注册新增的分析期规则与优化器规则。

**工作逻辑**：
通过 `extensions.injectResolutionRule` 注入 `RewriteUpdateTableForRowLineage` 与 `RewriteMergeIntoTableForRowLineage`；通过 `extensions.injectOptimizerRule` 注入 `RemoveRowLineageOutputFromOriginalTable`。使规则在 Spark 分析与优化阶段生效。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteOperationForRowLineage.scala` (新增, +87 lines)

**修改目的**：为 MERGE/UPDATE 行血缘重写提供共享 trait。

**工作逻辑**：
- 定义血缘属性名常量（取自 `MetadataColumns`）。
- `shouldUpdatePlan(table)`：仅当目标表是 `DataSourceV2Relation` 包装的 `SparkTable` 且 `TableUtil.supportsRowLineage` 返回 true，同时 output 中尚无 row_id 时返回 true（保证规则幂等）。
- `findRowLineageAttributes(expressions)`：从目标表 `metadataOutput` 中收集 row_id 与 last_updated_sequence_number 的 `AttributeReference`，并通过 `removeMetadataColumnAttribute` 去掉 `__metadata_col` 键，伪装为数据列，避免 Spark 元数据列输出限制。
- `isMetadataColumn`：判断属性是否带 `__metadata_col`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteUpdateTableForRowLineage.scala` (新增, +53 lines)

**修改目的**：为 `UpdateTable` 注入行血缘列与赋值。

**工作逻辑**：
匹配 `UpdateTable` 且 `shouldUpdatePlan` 为 true 的计划，调用 `updatePlanWithRowLineage`：在目标 `DataSourceV2Relation` 的 output 上追加 row_id 与 last_updated_sequence_number 属性，并在原 assignments 后追加两个赋值——`Assignment(lastUpdatedSequence, Literal(null))` 与 `Assignment(rowId, rowId)`。前者置空以便 Iceberg 在提交时填入新序号；后者保留原 row_id 以维持行身份。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteMergeIntoTableForRowLineage.scala` (新增, +75 lines)

**修改目的**：为 `MergeIntoTable` 注入行血缘列与赋值。

**工作逻辑**：
匹配已 resolved、rewritable、aligned 且 `shouldUpdatePlan` 为 true 的 `MergeIntoTable`。对 `matchedActions` 与 `notMatchedBySourceActions` 中的 `UpdateAction` 追加同样的两个血缘赋值；delete action 不变。最后把目标表 output 扩展为包含血缘属性并复制回 `MergeIntoTable`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/optimizer/RemoveRowLineageOutputFromOriginalTable.scala` (新增, +54 lines)

**修改目的**：清理 DSv2 写入计划 `originalTable` 上的血缘列。

**工作逻辑**：
对 `WriteDelta` 与 `ReplaceData` 节点，调用 `removeRowLineageOutput` 过滤掉 `originalTable`（`DataSourceV2Relation`）output 中名为 `_row_id` 与 `_last_updated_sequence_number` 的属性。这样下游物理规划与 relation cache 不会因为这些额外列而出问题。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteOperation.java` (修改, +14/-5 lines)

**修改目的**：让 COW 操作在表支持血缘时要求读取 row_id 与 last_updated_sequence_number 元数据。

**工作逻辑**：
将 `requiredMetadataAttributes()` 由返回固定数组改为基于 `List<NamedReference>` 动态构造：始终要求 `FILE_PATH`，DELETE/UPDATE 时追加 `ROW_POSITION`，若 `TableUtil.supportsRowLineage(table)` 则再追加 `ROW_ID` 与 `LAST_UPDATED_SEQUENCE_NUMBER`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (修改, +35/-0 lines)

**修改目的**：让 COW 扫描在向 Spark 暴露 readSchema 时把血缘字段当数据列处理。

**工作逻辑**：
重写 `readSchema()`，缓存惰性计算的 `rowLineageAsDataCols(SparkSchemaUtil.convert(expectedSchema()))`。`rowLineageAsDataCols` 遍历 StructField，对名为 `_row_id` 或 `_last_updated_sequence_number` 且含 `__metadata_col` 元数据键的字段，移除该键后 `copy` 为新字段，绕过 Spark DELETE 优化器规则对元数据列输出的限制。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaOperation.java` (修改, +11/-3 lines)

**修改目的**：让位置增量（MOR）操作在表支持血缘时要求读取血缘元数据列。

**工作逻辑**：
`requiredMetadataAttributes()` 改为动态构造：保留 `SPEC_ID` 与 `PARTITION_COLUMN_NAME`，若支持血缘则追加 `ROW_ID` 与 `LAST_UPDATED_SEQUENCE_NUMBER`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWriteBuilder.java` (修改, +9/-2 lines)

**修改目的**：位置增量写入时使用带血缘列的 schema 进行校验与转换。

**工作逻辑**：
- `dataSchema()` 计算 writeSchema：若表支持血缘则 `MetadataColumns.schemaWithRowLineage(table.schema())`，否则原 `table.schema()`；据此转换并校验 info.schema。
- metadata schema 部分同样在支持血缘时用 `schemaWithRowLineage` 包裹 `expectedMetadataSchema`，再与 info.metadataSchema 转换校验。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (修改, +6/-17 lines)

**修改目的**：让 SparkTable 无条件暴露行血缘元数据列。

**工作逻辑**：
`metadataColumns()` 改为直接返回固定数组（包含 SPEC_ID、PARTITION、FILE_PATH、ROW_POSITION、IS_DELETED、ROW_ID、LAST_UPDATED_SEQUENCE_NUMBER），不再按 `formatVersion >= 3` 条件追加血缘列。同时移除对 `TableUtil` 与 `ImmutableList` 的相关 import。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (修改, +16/-4 lines)

**修改目的**：在普通写入（含 overwrite）路径下正确处理血缘列是否写入数据文件。

**工作逻辑**：
- `build()` 计算 `writeIncludesRowLineage = TableUtil.supportsRowLineage(table) && overwriteFiles`，仅 overwrite 时才在输出文件中包含血缘列（其他写入新行 row_id 全为 null 无需写出）。
- `validateOrMergeWriteSchema` 新增 `writeIncludesRowLineage` 参数：merge schema 分支下若需要则把 merged schema 与 `schemaWithRowLineage(table.schema())` join；非 merge 分支下据此选择以 `schemaWithRowLineage` 包裹的 schema 进行 convert 与 validate。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java` (修改, +0/-23 lines)

**修改目的**：移除原 V1/V2 不允许解析血缘列的测试。

**工作逻辑**：
删除 `testRowLineageColumnsResolvedInV3OrHigher` 测试方法及对应 `AnalysisException` import，因为 `SparkTable.metadataColumns()` 现在无条件暴露血缘列，V1/V2 也能解析（仅写入时不写入实际值）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java` (修改, +12/-2 lines)

**修改目的**：在参数化测试矩阵中新增一个 formatVersion=3 的用例。

**工作逻辑**：
新增一组参数：catalog=testhadoop、Hadoop 类型、Parquet、`vectorized=false`、HASH 分布、branch=true、 LOCAL isolation、`formatVersion=3`，使行血缘测试能在 v3 表上运行。原 RANDOM.nextBoolean() 改为显式值。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCopyOnWriteWithLineage.java` (新增, +35 lines)

**修改目的**：COW 模式下行血缘测试子类。

**工作逻辑**：
继承 `TestRowLevelOperationsWithLineage`，覆写 `extraTableProperties()` 返回 MERGE/UPDATE/DELETE 均为 `COPY_ON_WRITE` 模式的属性。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadWithLineage.java` (新增, +35 lines)

**修改目的**：MOR 模式下行血缘测试子类。

**工作逻辑**：
同上，但 MERGE/UPDATE/DELETE 配置为 `MERGE_ON_READ` 模式。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (新增, +492 lines)

**修改目的**：行级操作的行血缘保留与序号更新验证。

**工作逻辑**：
抽象测试基类，使用 `assumeThat(formatVersion).isGreaterThanOrEqualTo(3)`、Parquet、非向量化作为前置条件。覆盖多个场景：非分区/分区 MERGE（matched+not matched）、UPDATE、DELETE 等。通过 `rowsWithLineageAndFilePos()` 读取带 `_row_id`、`_last_updated_sequence_number` 的行，校验：被携带的行保留原 row_id 与原序号；被更新的行保留原 row_id 但序号变为本次快照序号；新增的行获得从 `snapshot.firstRowId()` 起始的新 row_id 与本次快照序号。`SCHEMA` 与 `INITIAL_RECORDS` 预置了带 row_id=0..4、seq=1 的初始数据，便于断言。

## 总结

本提交为 Spark 3.5 完整实现了 MERGE 与 UPDATE 对 Iceberg v3 行级血缘的支持。核心机制是：在 Spark 分析期通过自定义规则把行血缘列注入目标表 output 与赋值（保留 row_id、清空 seq），并在扫描/写入层把血缘列伪装为数据列以绕过 Spark 元数据列输出限制；仅 overwrite 写入才把血缘列写入数据文件，其他写入依赖 Iceberg commit 侧填充序号。配套优化器规则清理 `originalTable` 上的血缘列避免影响下游。新增 492 行测试覆盖 COW/MOR、分区/非分区下血缘与序号的正确性。
