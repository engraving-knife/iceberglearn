# 提交 2991：Flink: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812)

## 提交信息

- **序号**：2991 / 4088
- **哈希**：11188387fd8e9b8f6944b760d294f262bc7bff32
- **短哈希**：11188387f
- **日期**：2025-12-10
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812)
- **PR/Issue**：#14812

## 总体目的

Flink 的 Dynamic Sink（动态写入）特性依赖 `TableMetadataCache` 缓存目标表的元数据，以决定是新建表还是更新已有表。当 Flink 作业写入一个目标表时，`TableMetadataCache.schema()` 会调用 `catalog.loadTable(identifier)` 探测表是否存在：若抛 `NoSuchTableException` 则视为表不存在，将"不存在"结果写入缓存并返回 `NOT_FOUND`，后续由 `TableUpdater` 负责建表。

问题在于：许多 Catalog 实现（包括 REST Catalog、Hive Catalog 等）在表所属的命名空间（namespace）本身不存在时，`loadTable` 抛的不是 `NoSuchTableException` 而是 `NoSuchNamespaceException`。原先的 catch 只捕获了 `NoSuchTableException`，导致 `NoSuchNamespaceException` 直接向上抛出，使整个写入作业失败——而实际上 Dynamic Sink 的 `TableUpdater` 完全有能力在写入时自动创建缺失的命名空间。

本提交修正这一缺陷：将 `NoSuchNamespaceException` 与 `NoSuchTableException` 一并捕获，视为"表不存在"，写入缓存后返回 `NOT_FOUND`，从而让 `TableUpdater` 在后续 `update()` 中按需创建命名空间与表，避免作业因命名空间缺失而直接失败。

## 如何达成设计目的

在 `TableMetadataCache` 的 `schema` 方法中把 catch 子句从 `NoSuchTableException` 扩展为 `NoSuchTableException | NoSuchNamespaceException`，并相应调整日志措辞与缓存写入逻辑（缓存一个"不存在"的 `CacheItem`，后续调用直接命中缓存避免重复探测）。同时在测试侧补充命名空间不存在、表不存在、以及 `TableUpdater` 自动创建命名空间+表的端到端测试。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+5/-2 lines)

**修改目的**：在表探测时同时捕获命名空间不存在的异常。

**工作逻辑**：

`schema()` 方法原本在 `catalog.loadTable(identifier)` 抛 `NoSuchTableException` 时进入 catch：记录 debug 日志、向 `tableCache` 放入一个 `exists=false` 的 `CacheItem`，并返回 `Tuple2.of(false, e)`。修改后 catch 改为 `NoSuchTableException | NoSuchNamespaceException`，日志改为 "Table or namespace doesn't exist {}"。这样无论表不存在还是其命名空间不存在，都会被当作"目标表不存在"处理，缓存负结果并交由 `TableUpdater` 在写入时创建命名空间与表。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+24/-0 lines)

**修改目的**：验证命名空间不存在与表不存在两种场景的缓存行为。

**工作逻辑**：

新增 `testNoSuchNamespaceExceptionHandling`：用一个不存在的命名空间 `nonexistent_namespace` 构造 `TableIdentifier`，调用 `cache.schema(...)` 断言返回 `NOT_FOUND`，并验证缓存中已写入该 identifier 的条目（避免后续重复探测）。新增 `testNoSuchTableExceptionHandling`：用 `default.nonexistent_table`（命名空间存在但表不存在）验证同样的行为，保证原有逻辑不回归。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+47/-0 lines)

**修改目的**：验证 `TableUpdater` 在命名空间缺失时自动创建命名空间与表。

**工作逻辑**：

新增 `testNamespaceAndTableCreation`：对完全不存在的 `new_namespace` 调用 `tableUpdater.update(...)`，断言调用后命名空间与表都被创建，且返回的 schema 比较结果为 `SAME`。新增 `testTableCreationWithExistingNamespace`：先手动创建 `existing_namespace`，再对其下不存在的表调用 `update`，断言表被创建且 schema 一致。两者共同验证了"命名空间存在/不存在"两种情况下 `TableUpdater` 的建表（及建命名空间）链路，确认修复后的 catch 行为与下游建表逻辑正确衔接。

## 总结

本提交修复了 Flink Dynamic Sink 在目标命名空间不存在时作业直接失败的问题，通过在 `TableMetadataCache` 中同时捕获 `NoSuchNamespaceException`，将其与表不存在等价处理，使 `TableUpdater` 能够按需自动创建命名空间和表。改动小但影响实际可用性，配套的缓存行为测试与建表端到端测试覆盖了关键路径。
