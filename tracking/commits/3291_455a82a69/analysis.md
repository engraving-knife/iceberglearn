# 提交 3291：Spark 4.1: Refactor metadata column references to use asRef() method (#15376)

## 提交信息

- **序号**：3291 / 4088
- **哈希**：455a82a6921a1cf40ff69f548f449d2748cfc09b
- **短哈希**：455a82a69
- **日期**：2026-02-19
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Refactor metadata column references to use asRef() method (#15376)
- **PR/Issue**：#15376

## 总体目的

在 Spark 4.1 的 copy-on-write 与 position-delta 写入路径中，`SparkCopyOnWriteOperation`、`SparkCopyOnWriteScan`、`SparkPositionDeltaOperation` 这几个类需要向 Spark 声明"本操作依赖哪些元数据列"（通过实现 `requiredMetadataAttributes()`、`filterAttributes()`、`rowId()` 等返回 `NamedReference[]` 的方法）。这些元数据列包括 `_spec_id`、`_partition`、`file_path`、`_pos`、`_row_id`、`_last_updated_sequence` 等。

重构前，构造这些 `NamedReference` 的统一写法是 `Expressions.column(MetadataColumns.FILE_PATH.name())`——即先用 Iceberg Core 的 `MetadataColumns` 常量拿到列名字符串，再调用 Spark 的 `Expressions.column(...)` 包装为命名引用。这种写法存在两处耦合：①每个使用方都要同时导入 Spark 的 `Expressions` 和 Iceberg 的 `MetadataColumns`；②元数据列的"名字"与"引用"分散在两个地方维护，而 Spark 侧其实已有 `SparkMetadataColumn`（实现了 Spark `MetadataColumn` 接口）这一封装，它本身就持有列名，理应能自己产出对应的 `NamedReference`。

本提交在 `SparkMetadataColumn` 上新增 `asRef()` 方法（内部 `Expressions.column(name())`），把"由元数据列生成命名引用"的能力内聚到元数据列对象本身，随后将上述三个操作/扫描类中所有 `Expressions.column(MetadataColumns.XXX.name())` 的调用替换为 `SparkMetadataColumns.XXX.asRef()`。这消除了对 `Expressions` 与 `MetadataColumns` 的直接依赖，使元数据列引用的构造更内聚、更类型安全（直接用 Spark 侧的 `SparkMetadataColumns` 常量而非 Core 侧字符串），属于 Spark 4.1 适配链中的代码质量改进。值得一提的是，`SparkPositionDeltaOperation` 中分区列的引用由原先的 `Expressions.column(MetadataColumns.PARTITION_COLUMN_NAME)`（静态字符串常量）改为 `SparkMetadataColumns.partition(table).asRef()`，通过 Spark 元数据列抽象按表获取分区列引用，抽象层次更准确。

## 如何达成设计目的

先在 `SparkMetadataColumn` 类中新增 `public NamedReference asRef()` 方法，复用已有的 `name()` 返回 `Expressions.column(name())`；然后在三个使用类中把 `Expressions.column(MetadataColumns.X.name())` 替换为对应的 `SparkMetadataColumns.X.asRef()`，并删除不再需要的 `Expressions` 与 `MetadataColumns` 导入。改动涉及 4 个文件，均为引用构造方式的重构，运行时行为不变。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMetadataColumn.java` (+6/-0 lines)

**修改目的**：为元数据列对象新增生成 `NamedReference` 的能力。

**工作逻辑**：
新增导入 `org.apache.spark.sql.connector.expressions.Expressions` 与 `NamedReference`。新增方法 `public NamedReference asRef() { return Expressions.column(name()); }`，直接复用该元数据列已有的 `name()`（即该列在 Spark 中的名字）生成 Spark 命名引用。这样元数据列"知道自己叫什么"，引用构造不再散落到调用方。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteOperation.java` (+8/-10 lines)

**修改目的**：用 `asRef()` 重写 COW 操作所需元数据属性的声明。

**工作逻辑**：
`requiredMetadataAttributes()` 中，原先 `metadataAttributes.add(Expressions.column(MetadataColumns.FILE_PATH.name()))` 等改为 `metaAttrs.add(SparkMetadataColumns.FILE_PATH.asRef())`。依次声明 `FILE_PATH`；当命令为 DELETE 或 UPDATE 时声明 `ROW_POSITION`；当 `TableUtil.supportsRowLineage(table)` 时声明 `ROW_ID` 与 `LAST_UPDATED_SEQUENCE_NUMBER`。局部变量名由 `metadataAttributes` 简化为 `metaAttrs`。移除 `MetadataColumns` 与 `Expressions` 导入，新增对 `SparkMetadataColumns` 的使用。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+1/-3 lines)

**修改目的**：用 `asRef()` 重写 COW 扫描的过滤属性声明。

**工作逻辑**：
`filterAttributes()` 原先用 `NamedReference file = Expressions.column(MetadataColumns.FILE_PATH.name()); return new NamedReference[] {file};`，改为单行 `return new NamedReference[] {SparkMetadataColumns.FILE_PATH.asRef()};`，告知 Spark 该扫描支持按 `file_path` 做运行时过滤。移除 `Expressions` 导入。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaOperation.java` (+11/-11 lines)

**修改目的**：用 `asRef()` 重写 position-delta 操作所需元数据属性与行 ID 的声明。

**工作逻辑**：
- `requiredMetadataAttributes()`：原先 `Expressions.column(MetadataColumns.SPEC_ID.name())` 与 `Expressions.column(MetadataColumns.PARTITION_COLUMN_NAME)` 改为 `SparkMetadataColumns.SPEC_ID.asRef()` 与 `SparkMetadataColumns.partition(table).asRef()`。注意分区列由静态字符串常量改为按表获取 `SparkMetadataColumn` 再转引用，抽象更准确（分区列名与表相关）。行血缘列 `ROW_ID`、`LAST_UPDATED_SEQUENCE_NUMBER` 同样改为 `asRef()`。局部变量名简化为 `metaAttrs`。
- `rowId()`：原先分别构造 `file` 与 `pos` 两个 `NamedReference` 再返回数组，改为直接 `return new NamedReference[] { SparkMetadataColumns.FILE_PATH.asRef(), SparkMetadataColumns.ROW_POSITION.asRef() };`。该方法声明 position-delta 操作以 `(file_path, _pos)` 作为行标识。
- 移除 `MetadataColumns` 与 `Expressions` 导入。

## 总结

本提交通过在 `SparkMetadataColumn` 上新增 `asRef()` 方法，将元数据列命名引用的构造内聚到元数据列对象本身，并据此重构 COW 操作/扫描与 position-delta 操作中所有元数据属性声明，消除了对 `Expressions` 与 Core 侧 `MetadataColumns` 的直接依赖，使引用构造更类型安全、更紧凑，是 Spark 4.1 适配链中提升代码内聚性的有益重构。
