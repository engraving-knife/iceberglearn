# 提交 0453：Spark 3.4: Read deletes in parallel and cache them on executors (#9603)

## 提交信息

- **序号**：0453
- **完整哈希**：65a076dedae2e62008ce64c6094dd519d29ce500
- **短哈希**：65a076ded
- **日期**：2024-02-02 15:24:55 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Read deletes in parallel and cache them on executors (#9603)，本提交将 PR #8755 与 PR #9583 回移植到 Spark 3.4
- **关联 PR**：#9603（回移植 #8755 与 #9583）
- **修改文件**：11 个（4 个新文件，7 个修改），共 1301 行新增

## 总体目的

本提交为 Spark 3.4 引入执行器（executor）侧的删除文件（delete files）缓存能力，以减少在行级操作（DELETE/UPDATE/MERGE）中重复读取同一批 delete 文件的 IO 与计算开销。背景是：在 Iceberg 的 COPY_ON_WRITE 与 MERGE_ON_READ 模式下，Spark 执行行级操作时往往会对目标表进行多次扫描（主查询 + runtime filter 等），而每个数据文件可能关联相同的 delete 文件（位置删除 + 等值删除）。如果每次扫描任务都重新打开并解析这些 delete 文件，就会在同一个 executor 上造成重复 IO 与 CPU 解码开销，尤其在 CoW 模式下被多次扫描时更为明显。

社区在主干（main 分支）通过 PR #8755 引入了 `SparkExecutorCache` 这一基于 Caffeine 的执行器内单例缓存，并通过 PR #9583 完善了与 delete 加载的集成。本提交把这两个 PR 的成果回移植到 Spark 3.4 模块，使 3.4 用户也能受益于该优化。

设计上，`SparkExecutorCache` 采用 JVM 单例（`volatile` + 双重检查锁），缓存键由"表名 + delete 文件标识"组合而成，值封装实际对象与估算字节数。缓存通过 Spark SQL 属性配置：开关 `spark.sql.iceberg.executor-cache.enabled`（默认开）、超时 `spark.sql.iceberg.executor-cache.timeout`（默认 10 分钟，空闲后驱逐）、单条目上限 `spark.sql.iceberg.executor-cache.max-entry-size`（默认 64MB）、总容量上限 `spark.sql.iceberg.executor-cache.max-total-size`（默认 128MB）。超过单条上限的值不缓存直接走 supplier；总容量通过 Caffeine 的 `weigher` 按字节数加权驱逐。缓存以表名为 group，在 `SerializableTableWithSize` 关闭（executor 任务结束时反序列化对象的 `close`）时主动 `invalidate(name)`，避免跨任务残留。

为了支持 duration 类配置解析，本提交还向 `SparkConfParser` 增加了 `DurationConfParser`，并新增 `JavaUtils`（从 Spark 内部类复制，因为 Spark 3.4 不对外暴露）来把 "10s"/"2m" 等时间字符串解析为秒。集成点在 `BaseReader`：内部 `DeleteFilter` 子类化 `BaseDeleteLoader`，覆写 `newDeleteLoader()` 返回 `CachingDeleteLoader`，由后者在 `canCache` 与 `getOrLoad` 上桥接到 `SparkExecutorCache`，从而让 delete 文件的读取结果在 executor 内被复用。

## 如何达成设计目的

实现路径分为四部分：基础设施（缓存与配置）、配置解析（DurationConfParser + JavaUtils）、读取层集成（BaseReader 的 CachingDeleteLoader）、生命周期管理（SerializableTableWithSize 关闭时 invalidate）。基础设施部分新建 `SparkExecutorCache` 类作为 Caffeine 缓存的门面与单例持有者，新建 `SparkSQLProperties` 中四个缓存相关常量；配置解析部分在 `SparkConfParser` 中加 `DurationConfParser` 内部类并新增无参构造器（供 executor 侧无 SparkSession/Table 上下文时使用 `SQLConf.get()`），新建 `JavaUtils` 提供时间字符串解析；读取层集成部分在 `BaseReader` 的 `DeleteFilter` 中覆写 `newDeleteLoader()` 返回 `CachingDeleteLoader`，后者把 `canCache` 委托给 `cache != null && size < cache.maxEntrySize()`，把 `getOrLoad` 委托给 `cache.getOrLoad(table().name(), key, supplier, size)`；生命周期部分在 `SerializableTableWithSize` 与 `SerializableMetadataTableWithSize` 的 `close()` 末尾调用 `invalidateCache(name)`，由它通过 `SparkExecutorCache.get()`（非创建式）取出已存在的缓存并按表名失效。测试侧新增 `TestSparkExecutorCache`（spark 模块与 spark-extensions 模块各一份）覆盖配置解析、并发访问、CoW/MoR 下 delete/UPDATE/MERGE 场景的缓存命中（通过自定义 `CustomFileIO`/`CustomInputFile` 统计 `newStream` 调用次数验证），并新增 `Employee` 测试模型类与 `TestSparkWriteConf.testDurationConf` 验证 duration 解析。

## 修改详情

### spark/v3.4/build.gradle

**修改目的**：引入 Caffeine 缓存库依赖，并为扩展测试引入 iceberg-data 的测试产物。

**工作逻辑**：在 `:iceberg-spark:iceberg-spark-3.4_2.12` 项目的 dependencies 中新增 `implementation libs.caffeine`，作为 `SparkExecutorCache` 的底层缓存实现。同时在 `iceberg-spark-extensions-3.4_2.12` 项目中新增 `testImplementation project(path: ':iceberg-data', configuration: 'testArtifacts')`，使扩展测试（`TestSparkExecutorCache`）能复用 `iceberg-data` 模块的测试工具类（如 `FileHelpers`）。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSparkExecutorCache.java（新文件，366 行）

**修改目的**：在 spark-extensions 测试模块中验证 CoW/MoR 下 DELETE/UPDATE/MERGE 操作的执行器缓存命中行为。

**工作逻辑**：继承 `SparkExtensionsTestBase`，使用参数化 catalog（`testhive` + 自定义 `CustomFileIO`）。`CustomFileIO` 把每个 path 映射到 `CustomInputFile`，后者在 `newStream()` 时 `AtomicInteger.incrementAndGet()`，从而统计 delete 文件被打开的次数。`createAndInitTable` 建表后追加 2 个数据文件（每个 3 行），再写 1 个位置删除文件（覆盖两个数据文件的第 0 行）+ 1 个等值删除文件（id=2,5），通过 `newRowDelta` 提交；之后 `REFRESH TABLE` 并清空 Spark `MemoryStore` 以销毁已有广播变量，确保后续读取重新走 executor。`checkDelete/checkUpdate/checkMerge` 在 CoW 下允许 delete 文件被打开 ≤3 次（主查询 + runtime filter，且 filter 可能使缓存失效），MoR 下允许 ≤1 次（仅扫描一次，缓存应保证每个 delete 文件只打开一次）。验证最终结果集正确。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/JavaUtils.java（新文件，76 行）

**修改目的**：提供时间字符串到秒的解析能力，供 `DurationConfParser` 使用。

**工作逻辑**：注释说明"copied from internal JavaUtils in Spark, not accessible in 3.4"。类为包级私有 `JavaUtils`，包含 `TIME_SUFFIXES` 映射（us/ms/s/m/min/h/d → TimeUnit）与两个方法 `timeStringAsSec(String)`、`timeStringAs(String, TimeUnit)`。`timeStringAs` 用正则 `(-?[0-9]+)([a-z]+)?` 拆分数字与后缀，若后缀无效抛 `NumberFormatException`，否则用 `unit.convert(val, timeUnit)` 转换。失败时抛出带提示的 `NumberFormatException`。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkConfParser.java

**修改目的**：新增 `DurationConfParser` 与无参构造器，支持在 executor 侧解析 duration 类配置。

**工作逻辑**：
- 新增无参构造器 `SparkConfParser()`，把 `properties` 设为空 map，`sessionConf` 设为 `new RuntimeConfig(SQLConf.get())`，`options` 设为空 map。这使 executor 侧（无 SparkSession/Table）也能用当前线程的 `SQLConf` 构造解析器。
- 新增 `durationConf()` 工厂方法返回 `DurationConfParser`。
- 新增内部类 `DurationConfParser extends ConfParser<DurationConfParser, Duration>`，含 `defaultValue(Duration)`、`parse()`（要求默认值非空）、`parseOptional()`（允许返回 null）。`toDuration(String)` 通过 `Duration.ofSeconds(JavaUtils.timeStringAsSec(time))` 把字符串转 `java.time.Duration`。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkExecutorCache.java（新文件，228 行）

**修改目的**：执行器侧单例缓存的核心实现，基于 Caffeine，按表名分组管理 delete 文件读取结果。

**工作逻辑**：
- 单例：`private static volatile SparkExecutorCache instance`，`getOrCreate()` 双重检查锁，仅在 `Conf.cacheEnabled()` 为 true 时创建；`get()` 返回已存在的或 null。
- 字段：`timeout`、`maxEntrySize`、`maxTotalSize`（来自 `Conf`），`volatile Cache<String, CacheValue> state`（懒初始化）。
- `getOrLoad(group, key, supplier, valueSize)`：若 `valueSize > maxEntrySize` 则直接 `supplier.get()` 不缓存；否则用 `internalKey = group + "_" + key` 调 `state().get(internalKey, loadFunc(...))`。`loadFunc` 包装 supplier，记录加载耗时并返回 `CacheValue(value, size)`。
- `invalidate(group)`：遍历 `state.asMap().keySet()` 过滤出以 group 开头的 internalKey，逐个 `state.invalidate`，并打印统计。
- `state()`：懒初始化，双重检查锁；`initState()` 用 Caffeine `expireAfterAccess(timeout)`、`maximumWeight(maxTotalSize)`、`weigher` 按 `CacheValue.weight()`（int 截断到 `Integer.MAX_VALUE`）、`recordStats()`、`removalListener` 日志。
- `CacheValue`：内部静态类，存 `Object value` 与 `long size`，`get()` 泛型强转，`weight()` 把 long 截断为 int。
- `Conf`：内部静态类，用 `new SparkConfParser()`（无参构造器）解析四个属性：`cacheEnabled`、`timeout`（Duration）、`maxEntrySize`、`maxTotalSize`（long）。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java

**修改目的**：声明执行器缓存相关的 Spark SQL 属性键与默认值。

**工作逻辑**：新增 `import java.time.Duration`，并在类末尾追加四个常量：
- `EXECUTOR_CACHE_ENABLED = "spark.sql.iceberg.executor-cache.enabled"`，默认 `true`。
- `EXECUTOR_CACHE_TIMEOUT = "spark.sql.iceberg.executor-cache.timeout"`，默认 `Duration.ofMinutes(10)`。
- `EXECUTOR_CACHE_MAX_ENTRY_SIZE = "spark.sql.iceberg.executor-cache.max-entry-size"`，默认 `64 * 1024 * 1024`（64MB）。
- `EXECUTOR_CACHE_MAX_TOTAL_SIZE = "spark.sql.iceberg.executor-cache.max-total-size"`，默认 `128 * 1024 * 1024`（128MB）。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java

**修改目的**：把 delete 加载桥接到执行器缓存，使 delete 文件读取结果在 executor 内复用。

**工作逻辑**：导入 `BaseDeleteLoader`、`DeleteLoader`、`Function`、`Supplier`、`SparkExecutorCache`。在 `DeleteFilter`（`BaseReader` 的内部抽象类）中：
- 覆写 `protected DeleteLoader newDeleteLoader()`，返回 `new CachingDeleteLoader(this::loadInputFile)`。
- 新增内部类 `CachingDeleteLoader extends BaseDeleteLoader`：构造时调 `super(loadInputFile)` 并 `this.cache = SparkExecutorCache.getOrCreate()`；覆写 `canCache(long size)` 返回 `cache != null && size < cache.maxEntrySize()`；覆写 `getOrLoad(key, supplier, size)` 返回 `cache.getOrLoad(table().name(), key, supplier, size)`。这样 `BaseDeleteLoader` 在加载 delete 文件时会先问 `canCache`，若可缓存则用 `getOrLoad` 把加载结果交给 `SparkExecutorCache`，相同表名 + delete 文件键的后续请求直接命中缓存。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SerializableTableWithSize.java

**修改目的**：在执行器任务结束时失效该表对应的缓存条目，避免跨任务残留。

**工作逻辑**：导入 `SparkExecutorCache`。在 `SerializableTableWithSize.close()` 末尾调用 `invalidateCache(name())`；在 `SerializableMetadataTableWithSize.close()` 末尾同样调用。新增私有静态方法 `invalidateCache(String name)`：`SparkExecutorCache cache = SparkExecutorCache.get();`（注意用 `get()` 而非 `getOrCreate()`，避免在关闭时意外创建缓存），若非 null 则 `cache.invalidate(name)`。这样 executor 任务结束时，反序列化的 table 对象关闭会触发按表名清理，使下一任务不读到过期 delete 数据。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/Employee.java（新文件，66 行）

**修改目的**：提供测试用的 Employee POJO，供 `createDataFrame` 创建数据集。

**工作逻辑**：简单 POJO，含 `Integer id` 与 `String dep` 字段，无参构造器与全参构造器，getter/setter，`equals/hashCode` 基于 id 与 dep。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java（新文件，444 行）

**修改目的**：在 spark 测试模块中覆盖缓存本身的配置解析、并发安全、以及 CoW/MoR delete 场景。

**工作逻辑**：继承 `SparkTestBaseWithCatalog`，使用 `@RunWith(Parameterized.class)`。测试包括：
- `testCacheValueWeightOverflow`：验证 `CacheValue` 在 size 超过 `Integer.MAX_VALUE` 时 weight 截断为 `Integer.MAX_VALUE`。
- `testCacheEnabledConfig`/`testTimeoutConfig`/`testMaxEntrySizeConfig`/`testMaxTotalSizeConfig`：用 `withSQLConf` 设置属性后通过 `Conf` 验证解析结果。
- `testConcurrentAccess`：10 线程并发 `getOrLoad` 两组两键，验证每个 internalKey 仅加载一次（用 `loadedInternalKeys` 集合 + 同步断言），之后 `invalidate` 两组并反射取 `state` 验证无残留键。
- `checkDelete`（与 extensions 版本类似）：通过 `CustomFileIO`/`CustomInputFile` 统计 delete 文件 `newStream` 次数，CoW ≤3、MoR ≤1。
- `fetchInternalCacheState`：反射访问 `SparkExecutorCache.state` 私有字段供测试断言。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java

**修改目的**：补充对 `SparkConfParser.DurationConfParser` 的解析测试。

**工作逻辑**：新增 `import java.time.Duration` 与 `testDurationConf()` 测试。该测试加载表后用 `withSQLConf` 分别设置 `spark.sql.iceberg.some-duration-conf` 为 `10s` 与 `2m`，构造 `SparkConfParser(spark, table, ImmutableMap.of())`，调 `durationConf().sessionConf(confName).parseOptional()`，断言结果分别为 10 秒与 2 分钟，验证 `DurationConfParser` 与底层 `JavaUtils.timeStringAsSec` 的正确性。

## 小结

本提交把主干上 PR #8755（`SparkExecutorCache` 引入）与 PR #9583（delete 加载集成）回移植到 Spark 3.4 模块，新增执行器侧基于 Caffeine 的单例缓存，按表名分组缓存 delete 文件读取结果，显著减少 CoW/MoR 行级操作中重复扫描导致的 delete 文件重复 IO 与解码。配套新增 `DurationConfParser` 与 `JavaUtils`（从 Spark 内部复制）支持 duration 配置解析，在 `SparkSQLProperties` 声明四个缓存属性（开关/超时/单条上限/总上限，默认 10 分钟、64MB、128MB），在 `BaseReader` 通过 `CachingDeleteLoader` 桥接 `BaseDeleteLoader` 与缓存，在 `SerializableTableWithSize` 关闭时按表名主动失效。测试侧新增两份 `TestSparkExecutorCache`（spark 模块覆盖缓存本身，spark-extensions 模块覆盖端到端行级操作）与 `Employee` 模型类，并通过 `TestSparkWriteConf.testDurationConf` 验证 duration 解析。修改性质为性能优化，默认开启但可通过 SQL 配置关闭，不改变行级操作对外语义。
