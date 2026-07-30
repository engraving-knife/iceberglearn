# 提交 3267：Spark 4.1: Separate compaction and main operations (#15301)

## 提交信息

- **序号**：3267 / 4088
- **哈希**：9ce0e6e11893d412b4d7e836df7b71247f10ae2f
- **短哈希**：9ce0e6e11
- **日期**：2026-02-16
- **作者**：Anton Okolnochyi
- **提交说明**：Spark 4.1: Separate compaction and main operations (#15301)
- **PR/Issue**：#15301

## 总体目的

本提交是 Spark 4.1 适配器的一项重要架构改造：把"压缩/重写（compaction/rewrite）"这一类由 `RewriteDataFiles` action 触发的内部数据写入路径，从"主（main）"读写路径中彻底剥离，独立成一套专门的 catalog 与表实现。

此前 Spark 4.1 中压缩操作的读写混在主路径里：action runner 通过在 Spark 读写选项里塞两个魔法键 `SparkReadOptions.SCAN_TASK_SET_ID` 和 `SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID` 来"标记"这是一次重写；`SparkTable.newScanBuilder` 和 `newWriteBuilder` 在入口处检查这些选项，分别切到 `SparkStagedScanBuilder` 或 `SparkPositionDeletesRewriteBuilder`；`SparkWriteBuilder` 内部还要维护一个 `rewrittenFileSetId` 字段，并在 `overwriteFiles`、`overwriteDynamic`、`overwrite(Filter[])`、`toBatch`、`toStreaming` 多处加 `Preconditions.checkState(rewrittenFileSetId == null, ...)` 断言来禁止"重写 + 覆盖写"组合；`SparkReadConf.scanTaskSetId()` 与 `SparkWriteConf.rewrittenFileSetId()` 两个解析方法也只为压缩服务。这种设计让主路径的 `SparkTable`、`SparkWriteBuilder`、`SparkWriteConf`、`SparkReadConf` 充斥着与压缩耦合的分支与断言，难以推理、难以扩展，也让 row lineage 的判断逻辑（`writeRequiresRowLineage = supportsRowLineage(table) && (overwriteFiles || writeConf.rewrittenFileSetId() != null)`）被污染。

改造后，压缩读写走一个全新的、专用的 catalog：`SparkRewriteTableCatalog`。它不再依赖魔法选项字符串，而是通过 `SparkTableCache`（按 groupId → Table 缓存）来定位被压缩的表。`IcebergSource.inferSchema` 在发现路径命中 `TABLE_CACHE` 时，直接返回 `SparkRewriteTableCatalog` 并把 path 作为 groupId 当作 identifier，从而 Spark 解析阶段就把重写请求路由到专用 catalog。专用 catalog 加载出 `SparkRewriteTable`（同时实现 `SupportsRead`、`SupportsWrite`，但 capabilities 只有 `BATCH_READ`/`BATCH_WRITE`），其 `newScanBuilder` 返回 `SparkStagedScanBuilder`，`newWriteBuilder` 根据是否为 `PositionDeletesTable` 分别构造 `SparkPositionDeletesRewriteBuilder` 或 `SparkRewriteWriteBuilder`。这样主路径 `SparkTable`、`SparkWriteBuilder` 不再需要任何压缩分支判断，魔法选项与对应 conf 解析方法一并删除，主路径回归"纯净"。

此外，`SparkStagedScan` 新增了 `estimateStatistics()` 实现（基于 task group 估算行数与字节数），让 Spark 优化器在压缩场景下也能获得合理的统计信息；老的 `SparkCachedTableCatalog`（一个 257 行的、既支持 time travel 又支持 rewrite 的混合 catalog）被整体删除，由职责更单一的 `SparkRewriteTableCatalog` 取而代之；`SparkTableCache` 新增 `tables()` 访问器，方便测试断言缓存内容。

## 如何达成设计目的

整体设计是"引入专用 catalog + 通过表缓存路由 + 删除主路径中的压缩分支"。改动分布在三处：(1) 新增 `SparkRewriteTableCatalog`、`SparkRewriteTable`、`SparkRewriteWriteBuilder`、`BaseSparkTable`（提取 `SparkTable` 的公共基类）四个新类；(2) 改造 `IcebergSource`、`SparkTable`、`SparkStagedScan`、`SparkStagedScanBuilder`、`SparkWriteBuilder`、`SparkPositionDeletesRewriteBuilder`、`SparkPositionDeletesRewrite`、三个 action runner 以及 `SparkReadConf`/`SparkReadOptions`/`SparkWriteConf`/`SparkWriteOptions`/`SparkTableCache` 配置层，去除压缩耦合；(3) 同步更新测试，把原先通过 `.option(SCAN_TASK_SET_ID, ...)` + `.load(tableName)` / `.writeTo(tableName).option(REWRITTEN_FILE_SCAN_TASK_SET_ID, ...).append()` 的写法改为 `.load(groupId)` + `.write().format("iceberg").mode("append").save(groupId)`，并显式调用 `SparkTableCache.get().add(groupId, table)` 把表登记进缓存。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkTable.java` (+165/-0 lines，新文件)

**修改目的**：提取 `SparkTable` 中表与 Spark 元数据相关的公共逻辑为抽象基类，供 `SparkTable` 和新的 `SparkRewriteTable` 共享。

**工作逻辑**：`BaseSparkTable` 实现 `org.apache.spark.sql.connector.catalog.Table` 与 `SupportsMetadataColumns`，持有 `Table table` 与 `Schema schema`（注意：schema 由子类在构造时传入，不再固定为 `table.schema()`），并实现 `name()`、`schema()`、`partitioning()`、`properties()`、`metadataColumns()` 等公共方法。`metadataColumns()` 会根据 `TableUtil.supportsRowLineage(table)` 决定是否把 `ROW_ID`、`LAST_UPDATED_SEQUENCE_NUMBER` 加入元数据列；`properties()` 拼接 `provider/format/location/current-snapshot-id/format-version/sort-order/identifier-fields` 等保留属性并合并表属性。`schema()` 用 `lazySparkSchema` 惰性缓存。这层抽象让 `SparkRewriteTable` 可以传入"重写专用 schema"（含 row lineage 元数据列）而不污染 `SparkTable`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkRewriteTableCatalog.java` (+120/-0 lines，新文件)

**修改目的**：作为压缩读写的专用 catalog，替代被删除的 `SparkCachedTableCatalog`。

**工作逻辑**：实现 `TableCatalog, SupportsFunctions`，但绝大多数 DDL 操作（`listTables`、`createTable`、`alterTable`、`dropTable`、`purgeTable`、`renameTable`、time travel 版本的 `loadTable`）都抛 `UnsupportedOperationException`，只支持 `loadTable(Identifier)`：校验 `ident` 不带 namespace，从全局 `SparkTableCache` 按 `ident.name()`（即 groupId）取出 `Table`，包装成 `SparkRewriteTable(table, groupId)` 返回；若缓存中不存在则抛 `NoSuchTableException`。`initialize` 仅记录 catalog 名。设计上把"压缩专用 catalog"限制为只能读、不能做表管理，职责非常单一。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkRewriteTable.java` (+74/-0 lines，新文件)

**修改目的**：表示一个被压缩的表的 Spark 视图，提供专用的 scan/write builder。

**工作逻辑**：继承 `BaseSparkTable` 并同时实现 `SupportsRead`、`SupportsWrite`，capabilities 固定为 `{BATCH_READ, BATCH_WRITE}`。构造时通过 `rewriteSchema(table)` 决定 schema：若表支持 row lineage 则用 `MetadataColumns.schemaWithRowLineage(table.schema())`，否则用 `table.schema()`。`newScanBuilder` 返回 `SparkStagedScanBuilder(spark(), table(), groupId, options)`，把 groupId 直接传给 builder；`newWriteBuilder` 根据 `table() instanceof PositionDeletesTable` 分别返回 `SparkPositionDeletesRewriteBuilder` 或 `SparkRewriteWriteBuilder`，后者接收的是 `rewriteSchema(table)` 而非默认 schema，确保 row lineage 列能正确写入。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkRewriteWriteBuilder.java` (+91/-0 lines，新文件)

**修改目的**：为非 position-deletes 的数据文件重写构造 `Write`。

**工作逻辑**：构造时接收 `SparkSession`、`Table`、`Schema schema`（重写 schema）、`String groupId`、`LogicalWriteInfo info`，并据此建立 `SparkWriteConf`、`caseSensitive`、`checkNullability`、`checkOrdering`。`build()` 调用 `validateWriteSchema()`（用 `SparkSchemaUtil.convert` + `TypeUtil.validateWriteSchema` 校验），再 `SparkUtil.validatePartitionTransforms`，最后返回一个匿名继承 `SparkWrite` 的实例：只覆写 `toBatch()` 返回 `asRewrite(groupId)`，`toStreaming()` 抛 `UnsupportedOperationException("Streaming writes are not supported for rewrites")`。相比 `SparkWriteBuilder`，这里不需要任何 overwrite 分支，因为它只服务于压缩的 append 式重写。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+18/-21 lines)

**修改目的**：把压缩请求路由到新的 `SparkRewriteTableCatalog`，而不是老的 cache catalog。

**工作逻辑**：常量重命名：`DEFAULT_CACHE_CATALOG_NAME`/`DEFAULT_CACHE_CATALOG` 改为 `REWRITE_CATALOG_NAME`/`REWRITE_CATALOG`，删除 `REWRITE_SELECTOR = "rewrite"` 常量。`getCatalogAndIdentifier` 中原先要从 options 中读 `SCAN_TASK_SET_ID` / `REWRITTEN_FILE_SCAN_TASK_SET_ID` 来推断 selector，逻辑被删除；改为先判断 `TABLE_CACHE.contains(path)`，命中则返回 `catalogManager.catalog(REWRITE_CATALOG_NAME)` + `Identifier.of(EMPTY_NAMESPACE, path)`（path 直接作为 groupId，不再拼 selector），随后才走 path 含 `/` 的常规分支。`setupDefaultCatalog` 在初始化时把 `spark.sql.catalog.default_rewrite_catalog` 设为 `SparkRewriteTableCatalog` 的类名。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+3/-9 lines)

**修改目的**：从主表实现中移除压缩分支，并改为继承 `BaseSparkTable`。

**工作逻辑**：`newScanBuilder` 删除了 `if (options.containsKey(SCAN_TASK_SET_ID)) return new SparkStagedScanBuilder(...)` 的特判；`newWriteBuilder` 删除了 `if (icebergTable instanceof PositionDeletesTable) return new SparkPositionDeletesRewriteBuilder(...)` 的特判，直接返回 `new SparkWriteBuilder(...)`。相关 import（`PositionDeletesTable`、`SparkReadOptions`）也随之清理。`SparkTable` 改为继承 `BaseSparkTable`，从而复用 `schema()`、`properties()`、`metadataColumns()` 等实现。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+5/-17 lines)

**修改目的**：从主写 builder 中清除 `rewrittenFileSetId` 相关的所有断言与分支。

**工作逻辑**：删除字段 `rewrittenFileSetId`、构造器中的 `this.rewrittenFileSetId = writeConf.rewrittenFileSetId()`，以及 `overwriteFiles`/`withOverwriteByFilter`/`overwrite(Filter[])` 中三处 `Preconditions.checkState(rewrittenFileSetId == null, ...)` 断言。`build()` 中 `writeRequiresRowLineage` 简化为 `TableUtil.supportsRowLineage(table) && overwriteFiles`（不再需要 `|| writeConf.rewrittenFileSetId() != null`，因为重写现在走单独 builder）。`toBatch()` 删除 `if (rewrittenFileSetId != null) return asRewrite(rewrittenFileSetId)` 分支；`toStreaming()` 删除对应的 unsupported 断言。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewriteBuilder.java` (+12/-9 lines)

**修改目的**：把 `fileSetId` 从读 conf 推断改为构造时显式传入。

**工作逻辑**：构造器签名由 `(spark, table, branch, info)` 改为 `(spark, table, fileSetId, info)`，`writeConf` 改为不带 branch 构造（`new SparkWriteConf(spark, table, info.options())`）。`build()` 中原先要从 `writeConf.rewrittenFileSetId()` 取 fileSetId 并校验非空，现在直接用传入的 `fileSetId`，校验移除；`specId`、`partition` 两个私有方法改为 `static`，并把 `fileSetId` 作为参数传入。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java` (+7/-2 lines)

**修改目的**：把 `fileSetId` 改为构造参数而非从 conf 读取。

**工作逻辑**：构造器签名增加 `String fileSetId` 参数并赋值给字段，原先 `this.fileSetId = writeConf.rewrittenFileSetId()` 被删除。Javadoc 同步补充 `@param fileSetId`。字段声明顺序微调，把 `fileSetId` 提到 `table` 之后。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java` (+16/-5 lines)

**修改目的**：把 `taskSetId` 改为构造参数，并补充统计估算实现。

**工作逻辑**：构造器签名由 `(spark, table, expectedSchema, readConf)` 改为 `(spark, table, projection, taskSetId, readConf)`，原先 `this.taskSetId = readConf.scanTaskSetId()` 被显式传入替代。新增 `estimateStatistics()` 覆写：用 `taskGroups().stream().mapToLong(ScanTaskGroup::estimatedRowsCount).sum()` 累加行数，再用 `SparkSchemaUtil.estimateSize(readSchema(), rowsCount)` 估算字节大小，返回 `new Stats(sizeInBytes, rowsCount, Collections.emptyMap())`。这让 Spark 在压缩场景下也能拿到合理的统计信息用于优化。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScanBuilder.java` (+5/-2 lines)

**修改目的**：让 staged scan builder 接收 groupId 参数，不再依赖读选项。

**工作逻辑**：构造器签名由 `(spark, table, options)` 改为 `(spark, table, taskSetId, options)`，新增 `this.taskSetId = taskSetId`；`build()` 调用 `new SparkStagedScan(spark, table, schemaWithMetadataColumns(), taskSetId, readConf)`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCachedTableCatalog.java` (+0/-257 lines，删除)

**修改目的**：移除混合了 time travel 与 rewrite 的旧 catalog，由职责更单一的 `SparkRewriteTableCatalog` 取代。

**工作逻辑**：整个文件被删除。该 catalog 此前既要把缓存表暴露给 Spark（支持 `#at_timestamp_`/`#snapshot_id_` 的 time travel），又要配合压缩流程，导致 257 行实现复杂、关注点混杂；拆分后 time travel 由主 `SparkCatalog` 路径覆盖，rewrite 由新 catalog 覆盖，各自清晰。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+0/-4 lines)

**修改目的**：移除只为压缩服务的 `scanTaskSetId()` 解析方法。

**工作逻辑**：删除 `public String scanTaskSetId() { return confParser.stringConf().option(SparkReadOptions.SCAN_TASK_SET_ID).parseOptional(); }`。groupId 现在通过 catalog 路由，不再走读选项。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadOptions.java` (+0/-3 lines)

**修改目的**：移除 `SCAN_TASK_SET_ID` 常量。

**工作逻辑**：删除 `public static final String SCAN_TASK_SET_ID = "scan-task-set-id";` 及其注释。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+0/-7 lines)

**修改目的**：移除只为压缩服务的 `rewrittenFileSetId()` 解析方法。

**工作逻辑**：删除 `public String rewrittenFileSetId() {...}`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+0/-3 lines)

**修改目的**：移除 `REWRITTEN_FILE_SCAN_TASK_SET_ID` 常量。

**工作逻辑**：删除 `public static final String REWRITTEN_FILE_SCAN_TASK_SET_ID = "rewritten-file-scan-task-set-id";`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTableCache.java` (+5/-0 lines)

**修改目的**：新增 `tables()` 访问器供测试断言使用。

**工作逻辑**：新增 `public Collection<Table> tables() { return cache.values(); }`，并 import `java.util.Collection`。这让测试可以校验"压缩完成后表已从缓存中移除"，而不只是简单判断缓存大小为 0（因为可能存在并发或其它测试残留）。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkBinPackFileRewriteRunner.java` (+0/-2 lines)

**修改目的**：bin-pack 重写 runner 不再传魔法选项。

**工作逻辑**：读取时删除 `.option(SparkReadOptions.SCAN_TASK_SET_ID, groupId)`，写入时删除 `.option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)`。`groupId` 现在直接作为 `load`/`save` 的路径，由 `IcebergSource` 路由到 `SparkRewriteTableCatalog`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkRewritePositionDeleteRunner.java` (+0/-2 lines)

**修改目的**：position delete 重写 runner 同样去除魔法选项。

**工作逻辑**：读取/写入处分别删除 `SCAN_TASK_SET_ID` / `REWRITTEN_FILE_SCAN_TASK_SET_ID` 选项。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingFileRewriteRunner.java` (+2/-7 lines)

**修改目的**：sort-based 重写 runner 去除魔法选项。

**工作逻辑**：`doRewrite` 中读取由 `.option(SCAN_TASK_SET_ID, groupId).load(groupId)` 简化为直接 `.load(groupId)`；写入删除 `.option(REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)`。同时 import 清理 `SparkReadOptions`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestFileRewriteCoordinator.java` (+30/-41 lines)

**修改目的**：适配新路由，把测试改为通过 groupId 读写并显式登记缓存。

**工作逻辑**：三个测试方法中：`fileSetID` 变量重命名为 `groupId`；`taskSetManager.stageTasks(...)` 之后新增 `SparkTableCache.get().add(groupId, table)`；读取由 `.option(SCAN_TASK_SET_ID, ...).load(tableName)` 改为 `.load(groupId)`；写入由 `.writeTo(tableName).option(REWRITTEN_FILE_SCAN_TASK_SET_ID, ...).append()` 改为 `.write().format("iceberg").mode("append").save(groupId)`；后续 `fetchTasks`/`fetchNewFiles` 使用 groupId。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkCachedTableCatalog.java` (+0/-105 lines，删除)

**修改目的**：删除针对旧 `SparkCachedTableCatalog` 的 time travel 测试。

**工作逻辑**：整个文件删除，因为 `SparkCachedTableCatalog` 已不存在，相关 time travel 行为由主 catalog 承担。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java` (+30/-70 lines)

**修改目的**：position deletes 表测试改为通过 groupId 读写并登记缓存，并更新一个错误信息断言。

**工作逻辑**：多个测试方法中：删除局部变量 `posDeletesTableName = catalogName + ".default." + tableName + ".position_deletes"`；在 `stageTask` 前调用 `SparkTableCache.get().add(fileSetID, posDeletesTable)`；读取由 `.option(SCAN_TASK_SET_ID, ...).load(posDeletesTableName)` 改为 `.load(fileSetID)`；写入由 `.writeTo(posDeletesTableName).option(REWRITTEN_FILE_SCAN_TASK_SET_ID, ...).append()` 改为 `.write().format("iceberg").mode("append").save(fileSetID)`。最后一个测试方法原本断言 `IllegalArgumentException("Can only write to ... via actions")`，改为断言 `UnsupportedOperationException("Cannot append to a metadata table")`，因为现在直接 append 到 position_deletes 元数据表会走主路径并触发该错误。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkStagedScan.java` (+14/-12 lines)

**修改目的**：staged scan 测试适配新路由。

**工作逻辑**：`setID` 重命名为 `groupId`，`stageTasks` 后新增 `SparkTableCache.get().add(groupId, table)`，读取由 `.option(SCAN_TASK_SET_ID, ...).load(tableName)` 改为 `.load(groupId)`，保留 `SPLIT_SIZE`、`FILE_OPEN_COST` 等真实读选项。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+12/-3 lines)

**修改目的**：把"缓存大小为 0"的断言改为"缓存中不包含被测表"。

**工作逻辑**：三处原本 `assertThat(SparkTableCache.get().size()).as("Table cache must be empty").isZero()` 改为加载 `Table table = validationCatalog.loadTable(identifier)` 后断言 `assertThat(SparkTableCache.get().tables()).as("Table cache must not contain the test table").noneMatch(cachedTable -> cachedTable.uuid().equals(table.uuid()))`。新断言更精确：因为缓存可能含其它表（多个测试并发或顺序执行残留），只要不含本表即可，避免误报。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetaColumnProjectionWithStageScan.java` (+5/-5 lines)

**修改目的**：元数据列投影的 staged scan 测试适配新路由。

**工作逻辑**：删除局部变量 `tableLocation`，在 `stageTask` 前调用 `SparkTableCache.get().add(fileSetID, table)`，读取由 `.option(SCAN_TASK_SET_ID, ...).load(tableLocation)` 改为 `.load(fileSetID)`。

## 总结

本提交通过引入职责单一的 `SparkRewriteTableCatalog`/`SparkRewriteTable`/`SparkRewriteWriteBuilder` 并提取 `BaseSparkTable` 公共基类，把压缩/重写这一类内部操作从 Spark 4.1 的主读写路径中彻底剥离。魔法选项 `SCAN_TASK_SET_ID`/`REWRITTEN_FILE_SCAN_TASK_SET_ID` 与对应 conf 解析方法、`SparkWriteBuilder` 中的多处 `rewrittenFileSetId` 断言一并删除，主路径 `SparkTable`/`SparkWriteBuilder` 重新变得纯粹；同时 `SparkStagedScan` 补充了统计估算、`SparkTableCache` 暴露 `tables()` 便于精确断言。这是一次行为不变但显著降低认知复杂度、为后续 Spark 4.1 改造铺路的架构性重构。
