# 提交 0318：Core, Data, Spark 3.5: Support file and partition delete granularity (#9384)

## 提交信息

- **序号**：0318 / 4088
- **哈希**：e7999a194dc0f32fba8fb515c9108764ed6ba6b5
- **短哈希**：e7999a194
- **日期**：2024-01-02 22:26:59 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Data, Spark 3.5: Support file and partition delete granularity (#9384)
- **PR/Issue**：#9384

## 总体目的

这个提交为 Iceberg 引入了“position delete 粒度（granularity）”这一可配置项，让用户能在两种删除文件组织策略之间选择：`PARTITION`（分区粒度，默认，旧行为）和 `FILE`（文件粒度，新行为）。

在 `PARTITION` 粒度下，position delete writer 会把同一个分区内、针对不同数据文件的删除记录合并写进一个（或少量）删除文件。这种策略的优点是删除文件总数少，缺点是：扫描某个数据文件时，reader 不得不读取覆盖了多个数据文件的删除文件，把与本次扫描无关的删除条目读进来再丢弃，造成 I/O 与 CPU 浪费（虽然可以通过 delete file cache 部分缓解）。

在 `FILE` 粒度下，writer 会为每个被引用的数据文件单独生成一个删除文件，确保 plan 阶段不会把无关删除分配给数据文件、reader 也只加载必要的删除信息。代价是删除文件总数上升，需要更激进的 delete compaction。两种策略各有取舍，应根据使用场景（写入吞吐 vs 读放大）以及“写入时”与“表维护时”分别选择，可以一阶段用 `FILE` 写入、另一阶段用 `PARTITION` 重写。

本提交只在 Core / Data / Spark 3.5 层落地该能力，且**仅对 position delete 生效**（equality delete 不受影响）。后续会再向其它 Spark 版本回移。

## 如何达成设计目的

整体设计分三层：
1. **Core 层**引入 `DeleteGranularity` 枚举和表属性 `write.delete.granularity`（默认 `PARTITION` 保持向后兼容），并新增一个 `FileScopedPositionDeleteWriter` 负责按数据文件切分删除输出；同时改造 `SortingPositionOnlyDeleteWriter`、`ClusteredPositionDeleteWriter`、`FanoutPositionOnlyDeleteWriter`，让它们接受一个 `DeleteGranularity` 参数，在 `FILE` 模式下走 file-scoped 路径、在 `PARTITION` 模式下走原有路径。
2. 为了避免按文件切分时引入 `String` 分配开销，新增 `CharSequenceUtil.unequalPaths` 直接对 `CharSequence` 做逐字符比较。
3. **Spark 3.5 层**通过 `SparkWriteOptions.DELETE_GRANULARITY` 写选项 + `SparkWriteConf.deleteGranularity()` 解析（覆盖顺序：写选项 > 表属性 > 默认值），并把解析结果一路透传到 `SparkPositionDeletesRewrite` 与 `SparkPositionDeltaWrite` 的 writer 构造路径。

下面按文件说明。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceUtil.java`（新增）

**修改目的**：提供无需转为 `String` 即可比较两个 `CharSequence` 是否不相等的工具方法，供 `FileScopedPositionDeleteWriter` 在按数据文件切分 writer 时判断当前记录的 `path` 是否变化。

**工作逻辑**：`unequalPaths(s1, s2)` 先做引用相等（`==`）短路，再比较长度，最后从尾到头逐字符比较，一旦发现不同立即返回 `true`。这种写法避免为每条 position delete 记录都生成一个 `String` 对象，对吞吐敏感的删除写入路径是关键优化。

### `core/src/main/java/org/apache/iceberg/TableProperties.java`

**修改目的**：注册新表属性 key 与默认值。

**工作逻辑**：新增常量 `DELETE_GRANULARITY = "write.delete.granularity"`，默认值 `DELETE_GRANULARITY_DEFAULT = DeleteGranularity.PARTITION.toString()`，即 `"partition"`，保持旧行为不变。同时 import 了新的 `DeleteGranularity`。

### `core/src/main/java/org/apache/iceberg/deletes/DeleteGranularity.java`（新增）

**修改目的**：定义两种删除粒度的枚举与字符串互转逻辑。

**工作逻辑**：
- 枚举值 `FILE` / `PARTITION`。
- `toString()` 返回小写 `"file"` / `"partition"`，用于属性值序列化。
- `fromString(String)` 大小写不敏感解析，未知值抛 `IllegalArgumentException`。
- 类的 JavaDoc 详尽解释了两种粒度的权衡：PARTITION 减少文件数但增加读放大；FILE 消除无关删除读取但增加文件数、需更频繁 compaction；目前仅对 position delete 生效；可写入与表维护阶段分别采用不同粒度。

### `core/src/main/java/org/apache/iceberg/deletes/FileScopedPositionDeleteWriter.java`（新增）

**修改目的**：在 `FILE` 粒度下，为每个被删除的数据文件单独产出一个删除文件。

**工作逻辑**：
- 构造时接收 `Supplier<FileWriter<PositionDelete<T>, DeleteWriteResult>>`（按需 new 一个底层 rolling writer）。
- 内部维护 `currentWriter` 和 `currentPath`。`write(PositionDelete)` 调用 `writer(path)` 取得对应 writer：若当前 writer 为空则 open 一个；若 `path` 变了（用 `CharSequenceUtil.unequalPaths` 判断）则先 close 当前 writer 并 open 新 writer。这要求输入按 (file, position) 排序，类注释明确声明了此前提。
- `close()` 关闭当前 writer 并置 `closed` 标志；`result()` 要求 writer 已关闭，返回累积的 `deleteFiles` 与 `referencedDataFiles`。`length()` 抛 `UnsupportedOperationException`（与 `SortingPositionOnlyDeleteWriter` 改造后一致，因为多文件场景下 length 无意义）。

### `core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`

**修改目的**：让这个用于 fanout 场景的排序删除 writer 也支持两种粒度。

**工作逻辑**：
- 字段从单个 `FileWriter` 改为 `Supplier<FileWriter>` + `DeleteGranularity`。保留旧构造器，新构造器接收 supplier 与 granularity，旧构造器内部委托给新构造器并默认 `PARTITION`。
- `close()` 改为 switch granularity：`FILE` 走 `writeFileDeletes()`——遍历每个 path 单独调用 `writeDeletes(ImmutableList.of(path))`，每个 path 产出一个删除文件并累积结果；`PARTITION` 走 `writePartitionDeletes()`——把所有 path 一起传给 `writeDeletes`，即旧行为。
- `writeDeletes` 接收 `Collection<CharSequence> paths`，每次从 `writers.get()` 取一个新底层 writer，按排序后的 path 顺序写入 position delete。
- `length()` 也改为抛 `UnsupportedOperationException`。
- `sort()` 增加短路：`paths.size() <= 1` 时直接返回，避免单元素时做无意义的排序与拷贝。

### `core/src/main/java/org/apache/iceberg/io/ClusteredPositionDeleteWriter.java`

**修改目的**：在 clustered 写入器中根据 granularity 选择底层 writer 类型。

**工作逻辑**：
- 新增 `DeleteGranularity` 字段；旧构造器委托给新构造器并默认 `PARTITION`。
- `newWriter(spec, partition)` 改为 switch：`FILE` 返回 `new FileScopedPositionDeleteWriter<>(() -> newRollingWriter(spec, partition))`；`PARTITION` 直接返回 `newRollingWriter(spec, partition)`。`newRollingWriter` 是从原 `newWriter` 抽出来的私有方法，返回 `RollingPositionDeleteWriter`，作为 `FileScopedPositionDeleteWriter` 的底层 supplier。

### `core/src/main/java/org/apache/iceberg/io/FanoutPositionOnlyDeleteWriter.java`

**修改目的**：在 fanout 写入器中根据 granularity 构造对应的 `SortingPositionOnlyDeleteWriter`。

**工作逻辑**：
- 新增 `DeleteGranularity` 字段；旧构造器委托给新构造器并默认 `PARTITION`。
- `newWriter` 不再直接 new 一个 delegate writer，而是 new 一个 `SortingPositionOnlyDeleteWriter`，其 supplier 是 `() -> new RollingPositionDeleteWriter<>(...)`，并把 `granularity` 透传进去。这样 `SortingPositionOnlyDeleteWriter` 内部就能按 granularity 决定写一份还是多份删除文件。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java`

**修改目的**：新增 Spark 写选项 key。

**工作逻辑**：新增常量 `DELETE_GRANULARITY = "delete-granularity"`，注释说明用于覆盖删除粒度。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java`

**修改目的**：解析 delete granularity，支持写选项 / 表属性 / 默认值三层覆盖。

**工作逻辑**：新增 `deleteGranularity()` 方法，使用 `confParser.stringConf()` 链式配置：option(`SparkWriteOptions.DELETE_GRANULARITY`) → tableProperty(`TableProperties.DELETE_GRANULARITY`) → defaultValue(`DELETE_GRANULARITY_DEFAULT`)，最后用 `DeleteGranularity.fromString` 转为枚举。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java`

**修改目的**：把 delete granularity 透传到 position delete 重写路径。

**工作逻辑**：从 `writeConf.deleteGranularity()` 读取并存入字段 `deleteGranularity`，并一路透传到 `WriteTask` / writer 构造器。最终在创建 `ClusteredPositionDeleteWriter`（`writerWithRow` / `writerWithoutRow`）时多传一个 `deleteGranularity` 参数，使重写后的删除文件也按所选粒度组织。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：把 delete granularity 透传到 MERGE/UPDATE 产生的 position delete 写入路径。

**工作逻辑**：在 writer context（`WriterContext`）中新增 `deleteGranularity` 字段，从 `writeConf.deleteGranularity()` 取值，并暴露 `deleteGranularity()` 方法。在创建删除 writer 时：`inputOrdered` 时用 `ClusteredPositionDeleteWriter`，否则用 `FanoutPositionOnlyDeleteWriter`，二者都把 `deleteGranularity` 传入。

### 测试文件

- `data/src/test/java/org/apache/iceberg/io/TestPartitioningWriters.java`：为 `ClusteredPositionDeleteWriter` 和 `FanoutPositionOnlyDeleteWriter` 增加 FILE/PARTITION 两种粒度的单元测试。
- `spark/v3.5/spark-extensions/.../TestMergeOnReadDelete.java` / `TestMergeOnReadMerge.java` / `TestMergeOnReadUpdate.java`：增加端到端测试，验证 DELETE/MERGE/UPDATE 在两种粒度下产生的删除文件数符合预期（FILE 粒度下每个被删数据文件一个删除文件，PARTITION 粒度下分区合并）。
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java`：覆盖默认值、表属性、写选项、非法值四种解析路径。
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`：验证 position delete 重写在两种粒度下产出的删除文件数（FILE=2，PARTITION=1）。
- `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/source/WritersBenchmark.java`：为两种粒度补充 benchmark。

## 小结

这个提交为 Iceberg 提供了 position delete 文件组织策略的可配置能力，是读写性能权衡上的一次重要灵活性提升。核心是新增 `DeleteGranularity` 枚举和 `FileScopedPositionDeleteWriter`，并通过 supplier 模式把粒度选择注入到 clustered/fanout/sorting 三类删除 writer 中，配合 Spark 端写选项与表属性两层配置，让用户可以按写入场景选择最优策略。改动覆盖 Core / Data / Spark 3.5 三层并附带完整测试，为后续向其它 Spark 版本回移打下了基础。
