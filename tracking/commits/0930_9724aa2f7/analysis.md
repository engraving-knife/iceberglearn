# 提交 0930：Spark 3.3, 3.4: Support read of partition metadata column when table is over 1k (#10641)

## 提交信息

- **序号**：0930 / 4088
- **哈希**：9724aa2f77a01537f65ca2b84185cc4c43ab2ab7
- **短哈希**：9724aa2f7
- **日期**：2024-07-12 16:32:12 -0700
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.3, 3.4: Support read of partition metadata column when table is over 1k (#10641)
- **PR/Issue**：#10641

## 总体目的

Iceberg Spark 集成支持在查询中读取元数据列（metadata column），其中 `_partition` 元数据列返回一个 struct，其字段对应分区 spec 的分区字段。这个 partition struct 的字段 ID 是基于 `MetadataColumns.PARTITION_COLUMN_ID` 起始按一定规则分配的。

当表的列数较多（超过约 1000 列）时，表的 schema 字段 ID 已经占用了一大片连续的 ID 空间，`_partition` 元数据列内部 struct 的字段 ID 就可能与表已有 schema 的字段 ID 发生**冲突**（同一 ID 被两个不同字段复用）。这会导致 Spark 读取 `_partition` 列时出现字段 ID 歧义、类型混乱或解析失败，从而无法在列数超过 1k 的表上读取分区元数据列。本提交的目的是在构造包含 `_partition` 元数据列的 schema 时，检测并重新分配 partition struct 内部的字段 ID，避免与表所有历史 schema 已用的 ID 冲突，使大宽表也能正常读取 `_partition`。

## 如何达成设计目的

在 `SparkScanBuilder.schemaWithMetadataColumns()` 中，原先直接用元数据列字段列表构造一个 `new Schema(fields)` 再与表 schema `join`。新实现抽取出 `calculateMetadataSchema(List<Types.NestedField>)` 方法：

1. 先在元数据列中找出 `_partition` 列（`fieldId == MetadataColumns.PARTITION_COLUMN_ID`）。若没有请求 `_partition` 列，则无需处理 ID 冲突，直接返回 `new Schema(metaColumnFields)`。
2. 若请求了 `_partition`，则收集 partition struct 内部所有字段 ID（`TypeUtil.indexById(partitionField.get().type().asStructType()).keySet()`）作为"待重新分配的 ID 集合"`idsToReassign`。
3. 计算"当前已占用的 ID 集合"`allUsedIds`：以元数据列字段 ID 为初始集合，再把表的所有历史 schema（`table.schemas().values()`）的全部字段 ID 通过 `Sets::union` 累加进来，得到一份完整的已用 ID 集合。
4. 构造新 `Schema` 时传入一个 ID 重新映射函数：对不在 `idsToReassign` 中的 ID 原样保留；对需要重新分配的 ID，用 `AtomicInteger` 从 1 开始递增寻找一个未被 `allUsedIds` 占用的候选 ID 返回。这样 partition struct 内部的字段 ID 会被改写为不与表任何 schema 冲突的新 ID。
5. 最终把这份重映射后的元数据 schema 与表 schema `TypeUtil.join` 得到读取 schema。

该方案对 Spark 3.3 和 3.4 两个版本同步应用，逻辑完全一致。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：在构造含 `_partition` 元数据列的 schema 时，重新分配 partition struct 字段 ID 以避免与表已有字段 ID 冲突。

**工作逻辑**：
- 新增 `Optional`、`Set`、`AtomicInteger`、`Sets` 等 import。
- `schemaWithMetadataColumns()` 中把变量 `fields`/`meta` 重命名为 `metadataFields`/`metadataSchema`，把原先 `new Schema(fields)` 替换为调用 `calculateMetadataSchema(metadataFields)`，最后仍 `TypeUtil.join(schema, metadataSchema)`。
- 新增 `calculateMetadataSchema(List<Types.NestedField> metaColumnFields)`：
  - 找出 partition 元数据列；不存在则直接 `new Schema(metaColumnFields)`。
  - `idsToReassign = TypeUtil.indexById(partitionField.get().type().asStructType()).keySet()`；
  - `currentlyUsedIds` = 元数据列自身 fieldId 集合；
  - `allUsedIds` = 把 `table.schemas().values()` 每个 schema 的全部字段 ID 与 `currentlyUsedIds` 不断 `Sets::union`；
  - 用 `new Schema(metaColumnFields, table.schema().identifierFieldIds(), oldId -> {...})` 构造：对 `idsToReassign` 中的旧 ID，用 `AtomicInteger nextId` 从 1 起递增，跳过 `allUsedIds` 已占用的，返回第一个空闲 ID；其余 ID 原样返回。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：同 Spark 3.3，对 3.4 应用相同的 ID 重映射逻辑。

**工作逻辑**：与 Spark 3.3 完全一致（import、`schemaWithMetadataColumns` 改名、新增 `calculateMetadataSchema` 方法），仅文件路径位于 `spark/v3.4/`。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：新增大宽表（>1k 列）下读取 `_partition` 的回归测试。

**工作逻辑**：新增 `testPartitionMetadataColumnWithManyColumns`：构造一个 1 列 `id` + 1009 列 `c1..c1009`（共 1010 列）的 schema，identity 分区 spec；提交表 schema 与 spec；写入 2 行数据（`id=0/1`，各 `c*` 列填 `id` 字符串）；断言 `SELECT *, _partition` 返回 2 行，且 `SELECT _partition, id, c999, c1000, c1001` 的结果符合预期（`_partition` 为 `row(0L)`/`row(1L)`）。验证字段 ID 不再冲突、读取正常。引入 `IntStream`、`StructType`、`expr` 等 import。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：同 Spark 3.3，对 3.4 补充相同测试。

**工作逻辑**：与 3.3 测试基本一致（构造 1010 列 schema、写 2 行、校验 `_partition` 读取），共 +47 行。

## 小结

- **成效**：修复了 Spark 3.3/3.4 下当表列数超过约 1k 时读取 `_partition` 元数据列因字段 ID 冲突而失败的问题，通过在构造元数据 schema 时把 partition struct 内部字段 ID 重新分配到未被表任何历史 schema 占用的 ID，实现 ID 去重；并补充了大宽表回归测试。
- **影响范围**：`spark/v3.3/spark` 与 `spark/v3.4/spark` 各自的 `SparkScanBuilder.java`（+49/-3 各）与 `TestSparkMetadataColumns.java`（+49、+47），共 4 个文件 +188/-6。仅影响含 `_partition` 元数据列的 Spark 读取路径。
- **回迁到 1.4.x 的注意事项**：1.4.x 对应的 Spark 版本通常为 3.3/3.4（以及 3.2），本提交同时覆盖 3.3/3.4，回迁价值高且逻辑独立。需确认 1.4.x 上 `SparkScanBuilder.schemaWithMetadataColumns()` 结构与 main 一致、`MetadataColumns.PARTITION_COLUMN_ID` 常量存在、`Schema` 构造器支持 `(fields, identifierFieldIds, idMappingFunction)` 三参重载。若 1.4.x 还支持 Spark 3.2，应评估是否需要对 3.2 做同样改动（本提交未含 3.2）。回迁后建议跑大宽表 + `_partition` 读取测试验证。注意 ID 重映射从 1 开始递增寻找空闲 ID，理论上不会与已用 ID 冲突，但若表字段 ID 极度密集需确认递增范围足够。
