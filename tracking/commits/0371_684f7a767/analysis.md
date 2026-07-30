# 提交 0371：Core, Spark 3.5: Read deletes in parallel and cache them on executors

## 提交信息

- **序号**：0371
- **哈希**：684f7a767c2c216a402b60b73d2d55ef605921a0
- **短哈希**：684f7a767
- **日期**：2024-01-16（作者日期与提交日期均为 Tue Jan 16 10:21:26 2024 -0800）
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Core, Spark 3.5: Read deletes in parallel and cache them on executors (#8755)
- **PR/Issue**：PR #8755

## 总体目的

这个提交是 Spark 3.5 读取路径上的一个重要性能优化。背景是 Iceberg 表在执行位置删除（position deletes）和等值删除（equality deletes）时，每个执行器（executor）在处理扫描任务（scan task）时都需要打开并解析对应的 delete 文件，把这些删除信息加载成内存数据结构（`PositionDeleteIndex` 或 `StructLikeSet`）后再过滤数据行。在原先的实现里，`DeleteFilter` 自己负责打开 Parquet/AVRO/ORC 格式的 delete 文件，且每个 task 独立读取、不做任何缓存。当一个查询中有多个 task 命中同一个 delete 文件（这是非常常见的场景，因为位置删除文件往往覆盖多个数据文件），或者连续查询重复读同一批 delete 文件时，会带来大量重复的 I/O 与解码开销。

提交的目的是双重的：第一，把 delete 文件的加载逻辑从 `DeleteFilter` 中抽离出来，形成一个独立的、可扩展的 `DeleteLoader` 抽象，让加载过程可以在多个 delete 文件之间并行执行；第二，引入一个 executor 级别的缓存（`SparkExecutorCache`），把已经加载好的删除索引/删除集合缓存在 executor JVM 内部，使得同一个 executor 上后续的 task 可以直接复用，避免重复读盘与解码。这对含有大量小删除文件、或者一个 delete 文件被多个数据文件引用的场景能显著降低读取延迟和 I/O 压力。

设计上，作者选择把缓存能力做成可选项（通过 `canCache` / `getOrLoad` 这两个钩子方法），核心模块（`data` 模块）的 `BaseDeleteLoader` 默认不缓存，只有 Spark 3.5 在 `BaseReader` 里通过一个内部类 `CachingDeleteLoader` 覆盖这两个钩子、对接 `SparkExecutorCache`。这种"核心无依赖、引擎层注入"的方式既保持了 core/data 模块的纯净性（不引入 Caffeine 之类的依赖），又给具体的执行引擎留出了扩展空间，是一种典型的可插拔设计。

## 如何达成设计目的

实现路径上有四条主线：(1) 在 `core` 模块扩展 `PositionDeleteIndex` 接口及其实现，提供空实现、合并工具与"按数据文件路径分组的索引"构造方法；(2) 在 `api` 模块的 `TypeUtil` 中加入 `estimateSize`，用于估算字段在内存中的占用大小，作为缓存决策的依据；(3) 在 `data` 模块新增 `DeleteLoader` 接口与 `BaseDeleteLoader` 实现，并把 `DeleteFilter` 原本内嵌的"打开 delete 文件 + 构造索引/集合"逻辑迁出，统一走 `DeleteLoader`，同时 `BaseDeleteLoader` 内部使用 `ThreadPools.getDeleteWorkerPool()` 并行加载多个 delete 文件；(4) 在 Spark 3.5 模块新增 `SparkExecutorCache`（基于 Caffeine 的单例缓存）和配套的 SQL 配置项，并在 `BaseReader` 中通过 `CachingDeleteLoader` 把缓存注入到 `DeleteLoader` 中，同时让 `SerializableTableWithSize` 在反序列化后的 `close()` 里调用 `invalidate` 清理对应表的缓存组，避免跨任务泄漏。同时把 `iceberg.worker.delete-num-threads` 默认值从 `availableProcessors()` 提到 `4 * availableProcessors()`，以匹配并行加载所需更高的线程数。

## 修改详情

### api/src/main/java/org/apache/iceberg/types/TypeUtil.java

**修改目的**：提供字段内存占用估算能力，供 `BaseDeleteLoader` 在决定是否缓存等值删除集合时使用。

**工作逻辑**：新增 `estimateSize(Types.NestedField field)` 公开方法及私有重载 `estimateSize(Type type)`。新增常量 `HEADER_SIZE = 12` 表示对象头开销。私有方法按 `Type.TypeID` 分支估算：boolean 1 字节，int/float/date 4 字节，long/double/time/timestamp 8 字节；字符串按 54 字节估算（12 头 + 6 字段 + 16 数组开销 + 20 字符内容），UUID 28 字节，DECIMAL 44 字节，BINARY 80 字节，FIXED 取其声明长度。对 STRUCT 递归为"头 + 各字段估算之和"；对 LIST 取"头 + 5 * 元素大小"（启发式假设平均 5 个元素）；对 MAP 取"头 + 5 * (头 + key + value)"。其它类型默认 16 字节。这是一组基于经验值的启发式估算，目的是给缓存准入决策提供一个可计算的尺子，而不是精确内存计量。

### core/src/main/java/org/apache/iceberg/SystemConfigs.java

**修改目的**：调整 delete worker 线程池默认大小以适应新的并行加载模型。

**工作逻辑**：将 `iceberg.worker.delete-num-threads` 配置项的默认值由 `Math.max(2, Runtime.getRuntime().availableProcessors())` 提升到 `Math.max(2, 4 * Runtime.getRuntime().availableProcessors())`。同时把注释从"用于为单个数据文件计算 PositionDeleteIndex 的线程数"改为"用于为数据文件读取 delete 文件的线程数"，因为现在该池不仅做索引计算，还要承担并行读取多个 delete 文件的 I/O，因此需要更高并发度来避免线程数过少成为瓶颈。

### core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java

**修改目的**：为合并多个位置删除索引提供基础能力。

**工作逻辑**：新增包级方法 `merge(BitmapPositionDeleteIndex that)`，实现为 `roaring64Bitmap.or(that.roaring64Bitmap)`，即直接对底层 Roaring64 位图做按位或运算，把另一个索引中的已删除位置并入当前索引。该方法被 `PositionDeleteIndexUtil.merge` 用于把多个 delete 文件加载出的索引合并成单一索引。

### core/src/main/java/org/apache/iceberg/deletes/Deletes.java

**修改目的**：提供按数据文件路径分组的索引构造方法，便于缓存整个 delete 文件的内容。

**工作逻辑**：新增静态方法 `toPositionIndexes(CloseableIterable<T> posDeletes)`，返回 `CharSequenceMap<PositionDeleteIndex>`。与已有的 `toPositionIndex(CharSequence, List)` 不同，本方法不做任何过滤，而是遍历所有位置删除记录，按 `FILE_PATH` 字段（通过 `FILENAME_ACCESSOR`）分组，对每个数据文件路径构造一个独立的 `BitmapPositionDeleteIndex`，调用 `index.delete(position)` 累积该路径下的所有删除位置。最终返回的 map 包含 delete 文件中引用的每个数据文件路径对应的完整索引。这种"全量加载 + 按路径切分"的方式让缓存可以一次缓存整个 delete 文件，后续按需取出对应路径的索引，避免为每个数据文件路径重复打开同一个 delete 文件。

### core/src/main/java/org/apache/iceberg/deletes/EmptyPositionDeleteIndex.java

**修改目的**：提供一个不可变的空 `PositionDeleteIndex` 实现，作为缓存未命中或没有删除时的占位符。

**工作逻辑**：新增的包级类，使用单例模式（`INSTANCE`），通过 `get()` 获取。`delete(long)` 与 `delete(long, long)` 抛出 `UnsupportedOperationException`；`isDeleted(long)` 恒返回 `false`；`isEmpty()` 恒返回 `true`；`toString()` 返回 `"PositionDeleteIndex{}"`。该类配合 `PositionDeleteIndex.empty()` 工厂方法使用，避免在缓存命中但当前数据文件路径没有删除时返回 null，从而简化调用方判空逻辑。

### core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java

**修改目的**：扩展接口，提供空实现工厂方法和便利方法。

**工作逻辑**：在接口中新增 `default boolean isNotEmpty()`（取 `isEmpty()` 的反），以及 `static PositionDeleteIndex empty()` 工厂方法返回 `EmptyPositionDeleteIndex.get()`。这两个方法让上层代码可以更自然地表达"非空判断"和"获取空索引"的语义。

### core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndexUtil.java

**修改目的**：提供把多个 `PositionDeleteIndex` 合并为一个的工具方法。

**工作逻辑**：新增的公开工具类（私有构造），提供静态方法 `merge(Iterable<? extends PositionDeleteIndex> indexes)`。该方法新建一个 `BitmapPositionDeleteIndex`，遍历输入索引：跳过空索引；对非空索引断言其必须是 `BitmapPositionDeleteIndex`（否则抛出 `IllegalArgumentException`，错误信息包含实际类型名），然后调用其 `merge` 方法并入结果。最终返回合并后的索引。这个方法是 `BaseDeleteLoader.loadPositionDeletes` 的最后一步：它并行加载多个 delete 文件得到多个索引，再用本方法合并成一个返回给调用方。

### core/src/main/java/org/apache/iceberg/util/ThreadPools.java

**修改目的**：更新文档注释，说明 delete worker 池的语义已从"计算索引"扩展为"读取 delete 文件"。

**工作逻辑**：仅注释变更，把 `getDeleteWorkerPool()` 的 Javadoc 从"限制用于为单个数据文件计算 PositionDeleteIndex 的线程数"改为"限制单个 JVM 内并发读取 delete 文件的任务数；如果有多线程同时加载 deletes，它们默认会共享这个 worker 池"。代码行为不变，但注释更准确地反映了新模型下该池承担的角色。

### data/src/main/java/org/apache/iceberg/data/DeleteLoader.java

**修改目的**：定义加载 delete 文件内容的统一 API。

**工作逻辑**：新增的接口，包含两个方法。`loadEqualityDeletes(Iterable<DeleteFile> deleteFiles, Schema projection)` 返回 `StructLikeSet`，加载等值删除文件内容到集合；`loadPositionDeletes(Iterable<DeleteFile> deleteFiles, CharSequence filePath)` 返回 `PositionDeleteIndex`，加载指定数据文件路径的位置删除索引。接口设计上把"加载"与"使用"分离，让 `DeleteFilter` 不再关心具体如何读文件、是否缓存，只调用接口拿结果。这为后续注入缓存实现留出空间。

### data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java

**修改目的**：提供 `DeleteLoader` 的默认实现，支持并行加载和可选的缓存钩子。

**工作逻辑**：新增的公开类，是本次提交的核心。构造时接受 `Function<DeleteFile, InputFile> loadInputFile`（用于按需打开 delete 文件）和 `ExecutorService workerPool`（默认使用 `ThreadPools.getDeleteWorkerPool()`）。提供两个 `protected` 钩子：`canCache(long size)` 默认返回 `false`，`getOrLoad(String key, Supplier<V>, long valueSize)` 默认抛 `UnsupportedOperationException`，子类（如 Spark 的 `CachingDeleteLoader`）通过覆盖这两个钩子来接入缓存。

`loadEqualityDeletes` 通过 `execute` 方法用 worker 池并行处理每个 delete 文件：对每个文件调用 `getOrReadEqDeletes`，其中先用 `estimateEqDeletesSize` 估算内存（`recordCount * 单条记录估算大小`，单条大小通过 `TypeUtil.estimateSize` 对 schema 各列求和），若 `canCache` 则用 delete 文件路径作为 key 调 `getOrLoad`，否则直接 `readEqDeletes`。读取流程为 `openDeletes` 打开文件（按 AVRO/PARQUET/ORC 分发到对应 reader），`Record::copy` 后用 `InternalRecordWrapper` 转成 `StructLike`，最后 `materialize` 成 `ImmutableList` 以便缓存。最终把所有文件的删除集合 `concat` 后装入 `StructLikeSet`。

`loadPositionDeletes` 类似并行加载每个 delete 文件，但每个文件返回的是 `CharSequenceMap<PositionDeleteIndex>`（缓存时）或单个 `PositionDeleteIndex`（不缓存时）。缓存路径下使用 `Deletes.toPositionIndexes` 全量加载并按路径分组，再用 `getOrDefault(filePath, PositionDeleteIndex.empty())` 取出当前路径对应的索引；不缓存路径下用 `readPosDeletes(deleteFile, filePath)`，通过 `Expressions.equal(DELETE_FILE_PATH, filePath)` 过滤只读当前路径的删除记录，调 `Deletes.toPositionIndex`。最后通过 `PositionDeleteIndexUtil.merge` 合并所有文件的结果。`estimatePosDeletesSize` 简单返回 `recordCount`，依据测试经验 Roaring 位图平均每值约 1 字节。

`execute` 方法使用 `Tasks.foreach(...).executeWith(workerPool).stopOnFailure().run(...)` 把多个 delete 文件的处理并发提交到 worker 池，结果收集到 `ConcurrentLinkedQueue` 后返回。这是实现"并行读取"的关键。

### data/src/main/java/org/apache/iceberg/data/DeleteFilter.java

**修改目的**：把 delete 文件加载逻辑从 `DeleteFilter` 中剥离，改为委托给 `DeleteLoader`。

**工作逻辑**：删除了大量原本内嵌的加载代码（包括 `POS_DELETE_SCHEMA` 常量、`openPosDeletes`、`openDeletes` 方法以及 AVRO/PARQUET/ORC 三个 reader 构造分支），相应地移除了 `FileContent`、`Avro`、`DataReader`、`GenericOrcReader`、`GenericParquetReaders`、`Expressions`、`Iterables` 等导入。新增了 `volatile DeleteLoader deleteLoader` 字段及 `deleteLoader()` 方法（双重检查锁定懒加载），并新增 `newDeleteLoader()` 工厂方法（默认 `new BaseDeleteLoader(this::loadInputFile)`，子类可覆盖）和 `loadInputFile(DeleteFile)`（默认委托给 `getInputFile(deleteFile.path().toString())`）。`applyEqDeletes` 中原本构造 `deleteRecords`、`records`、`deleteSet` 的整段逻辑被替换为一行 `deleteLoader().loadEqualityDeletes(deletes, deleteSchema)`。`deletedRowPositions()` 改为：若 `deleteRowPositions == null` 且 `posDeletes` 非空则调 `deleteLoader().loadPositionDeletes(posDeletes, filePath)`，并在 `posDeletes` 为空时返回 `null`（保持原行为）。`applyPosDeletes` 改为直接复用 `deletedRowPositions()` 而不再重复打开 pos delete 文件。这些改动让 `DeleteFilter` 只关心"如何用删除信息过滤记录"，不再关心"如何读 delete 文件"。

### spark/v3.5/build.gradle

**修改目的**：引入 Caffeine 缓存库作为 `SparkExecutorCache` 的底层实现依赖。

**工作逻辑**：在 `iceberg-spark` 模块的 `dependencies` 块中新增 `implementation libs.caffeine`。使用 `implementation` 配置（而非 `api`），因为 Caffeine 是实现细节，不需要暴露给下游消费者。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java

**修改目的**：支持解析时长类型的 Spark 配置项，供 `SparkExecutorCache` 读取超时配置。

**工作逻辑**：新增无参构造 `SparkConfParser()`，使用空 map 作为 properties 和 options，`SQLConf.get()` 作为 sessionConf——这允许在不持有 `SparkSession` 引用时（例如在 executor 上反序列化后）也能解析 SQL 配置。新增 `durationConf()` 工厂方法返回 `DurationConfParser`。新增内部类 `DurationConfParser extends ConfParser<DurationConfParser, Duration>`，提供 `defaultValue(Duration)`、`parse()`、`parseOptional()` 方法，并通过 `toDuration(String)` 调用 `JavaUtils.timeStringAsSec`（Spark 工具类）把形如 `"10m"`、`"30s"` 的时间字符串解析为 `Duration.ofSeconds`。这补齐了原有 `BooleanConfParser`、`LongConfParser`、`StringConfParser`、`IntConfParser` 之外缺少的时长解析能力。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkExecutorCache.java

**修改目的**：提供 executor 级别的单例缓存，用于在 task 之间复用已加载的 delete 文件内容。

**工作逻辑**：新增的公开类，使用单例模式（`volatile` + 双重检查锁），基于 Caffeine 实现。核心字段包括 `timeout`、`maxEntrySize`、`maxTotalSize`、`volatile Cache<String, CacheValue> state`。`getOrCreate()` 静态方法在首次调用时根据 `Conf` 决定是否启用缓存（默认启用），启用则创建单例；未启用则返回 `null`。`get()` 静态方法返回已有实例或 `null`。`maxEntrySize()` 返回单条目最大允许大小，供调用方判断是否值得缓存。

`getOrLoad(String group, String key, Supplier<V> valueSupplier, long valueSize)` 是核心 API：若 `valueSize > maxEntrySize` 则跳过缓存直接调用 supplier；否则用 `group + "_" + key` 作为内部 key 调用 Caffeine 的 `cache.get(internalKey, loadFunc(...))`。`loadFunc` 包装 supplier，记录加载耗时日志，返回 `CacheValue(value, valueSize)`。`CacheValue` 同时持有值和估算大小，`weight()` 返回 `Math.min(size, Integer.MAX_VALUE)` 供 Caffeine 的 `weigher` 使用。

`invalidate(String group)` 遍历缓存中所有以 group 开头的 key 并逐个失效，同时打印失效数量和缓存统计信息。`state()` 使用懒加载初始化 Caffeine `Cache`，配置 `expireAfterAccess(timeout)`、`maximumWeight(maxTotalSize)`、`weigher` 按 `CacheValue.weight()` 计重、`recordStats()` 开启统计、`removalListener` 打印驱逐日志。

内部类 `Conf` 通过 `SparkConfParser` 读取 4 个配置项：`cacheEnabled`、`timeout`、`maxEntrySize`、`maxTotalSize`，分别对应 `SparkSQLProperties` 中的常量。这些配置在初始化时读取一次后即固定，后续修改不生效（注释明确说明）。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java

**修改目的**：声明 executor 缓存相关的 SQL 配置项及其默认值。

**工作逻辑**：新增 4 组配置常量。`EXECUTOR_CACHE_ENABLED`（`spark.sql.iceberg.executor-cache.enabled`，默认 `true`）控制是否启用缓存；`EXECUTOR_CACHE_TIMEOUT`（`spark.sql.iceberg.executor-cache.timeout`，默认 `Duration.ofMinutes(10)`）控制访问后多久自动驱逐；`EXECUTOR_CACHE_MAX_ENTRY_SIZE`（`spark.sql.iceberg.executor-cache.max-entry-size`，默认 64MB）控制单个条目最大允许大小，超过则不缓存；`EXECUTOR_CACHE_MAX_TOTAL_SIZE`（`spark.sql.iceberg.executor-cache.max-total-size`，默认 128MB）控制缓存总重量上限。默认值偏保守，适合大多数生产场景，同时为调优留出空间。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java

**修改目的**：把 `SparkExecutorCache` 接入 `DeleteLoader`，让 Spark 读取路径上的 delete 加载走缓存。

**工作逻辑**：导入 `BaseDeleteLoader`、`DeleteLoader`、`SparkExecutorCache` 及 `Function`、`Supplier`。在内部的 task reader 类中覆盖 `newDeleteLoader()`，返回一个 `CachingDeleteLoader(this::loadInputFile)` 实例。`CachingDeleteLoader` 是 `BaseReader` 的内部类，继承 `BaseDeleteLoader`，构造时调用 `SparkExecutorCache.getOrCreate()` 拿到缓存（可能为 `null`）。覆盖 `canCache(long size)`：返回 `cache != null && size < cache.maxEntrySize()`。覆盖 `getOrLoad(String key, Supplier<V>, long valueSize)`：委托给 `cache.getOrLoad(table().name(), key, valueSupplier, valueSize)`，即用表名作为 group，delete 文件路径作为 key。这样同一个表的所有 delete 文件缓存归为一组，便于在表关闭时统一清理。注意 `CachingDeleteLoader` 是非静态内部类，可以访问外部 `BaseReader` 的 `table()` 方法。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SerializableTableWithSize.java

**修改目的**：在序列化表反序列化后关闭时，清理对应的 executor 缓存，避免跨任务泄漏。

**工作逻辑**：导入 `SparkExecutorCache`。在 `SerializableTableWithSize` 和 `SerializableMetadataTableWithSize` 的 `close()` 方法中，在原有的 `io().close()` 之后新增 `invalidateCache(name())` 调用。新增私有静态方法 `invalidateCache(String name)`：通过 `SparkExecutorCache.get()`（注意是 `get()` 不是 `getOrCreate()`，避免在不需要时意外创建缓存）拿到已有缓存，若非 null 则调用 `cache.invalidate(name)` 失效该表名对应的所有缓存条目。由于序列化表会在 executor 上被反序列化并最终关闭，这个清理点能确保表级别的缓存组在任务结束时被释放，配合 Caffeine 的 `expireAfterAccess` 作为兜底。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/Employee.java

**修改目的**：为 `TestSparkExecutorCache` 提供测试用的可序列化数据类。

**工作逻辑**：新增的测试辅助类，包含 `id`、`name`、`department` 等字段（典型的员工记录），用于在缓存测试中作为缓存值的载体。这是一个纯测试支持文件，不在主代码路径上。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java

**修改目的**：为 `SparkExecutorCache` 提供单元测试覆盖。

**工作逻辑**：新增 503 行的测试类，覆盖缓存的创建、getOrLoad 命中/未命中、maxEntrySize 跳过、group 失效、统计信息、并发访问等场景。鉴于这是测试代码，这里不逐方法展开，但其存在保证了缓存核心逻辑（特别是单例的双重检查锁、group 失效、大小判断）的正确性。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java

**修改目的**：补充对新增的 executor 缓存配置项的解析测试。

**工作逻辑**：新增 23 行测试，验证 `SparkWriteConf` 能正确解析 `EXECUTOR_CACHE_ENABLED`、`EXECUTOR_CACHE_TIMEOUT`、`EXECUTOR_CACHE_MAX_ENTRY_SIZE`、`EXECUTOR_CACHE_MAX_TOTAL_SIZE` 四个配置项及其默认值，确保配置链路端到端可用。

## 小结

这个提交是 Iceberg 在 Spark 3.5 上对 delete 读取路径的一次系统性重构与优化，其核心模式是"抽象 + 注入"：把原本散落在 `DeleteFilter` 中的加载逻辑抽成 `DeleteLoader` 接口，在核心层提供并行加载能力，在引擎层通过覆盖钩子注入缓存。这种设计既保持了核心模块的纯净（不引入 Caffeine 等引擎依赖），又让具体引擎可以按需增强。配套的 `TypeUtil.estimateSize` 提供了基于启发式的内存估算，使缓存准入决策有据可依；`SparkExecutorCache` 基于 Caffeine 实现单例缓存，支持 group 级别的失效和按重量驱逐，并通过 `SerializableTableWithSize` 的关闭钩子确保生命周期可控。同时把 delete worker 池默认线程数提升到 4 倍核数，匹配新的并行加载模型。整体影响是：在有多 delete 文件、delete 文件被多数据文件共享、或连续查询命中相同 delete 文件的场景下，能显著降低重复 I/O 和解码开销，提升读取吞吐。该模式后续也作为基础被其他引擎模块（如 Flink）借鉴复用。
