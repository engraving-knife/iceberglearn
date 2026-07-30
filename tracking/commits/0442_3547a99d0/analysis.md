# 提交 0442：Spark 3.4: Support file and partition delete granularity (#9602)

## 提交信息

- **序号**：0442
- **哈希**：3547a99d05d892c87a240475ef577abe632eff35
- **短哈希**：3547a99d0
- **日期**：2024-02-01 09:38:05 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Support file and partition delete granularity (#9602)
- **PR/Issue**：#9602（backport #9384）

## 总体目的

本提交将上游 #9384 的"删除粒度可配置"能力 backport 到 Spark 3.4 模块。Iceberg core 在 `DeleteGranularity` 枚举中定义了两种 position delete 的组织粒度：`PARTITION`（默认）让删除写入器把同一分区里多个数据文件的删除合并到一个删除文件中，整体删除文件数少但读时可能要加载与本扫描无关的删除信息；`FILE` 则为每个被引用的数据文件单独产出一个删除文件，读时只加载需要的删除信息，但删除文件总数会变多、需要更激进的 compaction。两种粒度各有取舍，应根据使用场景选择，甚至可以"写入用一种、维护用另一种"。

在 Spark 3.4 此前并没有把这一可选项暴露出来：`ClusteredPositionDeleteWriter` 和 `FanoutPositionOnlyDeleteWriter` 在 core 侧已经支持接收 `DeleteGranularity` 参数，但 Spark 写入路径（DELETE/MERGE/UPDATE 的 merge-on-read 模式）和 position deletes 重写路径（`SparkPositionDeletesRewrite`）都没有读取这个配置、也没有把它传给 writer，实际写入永远走默认的 `PARTITION`。本提交补齐了从 Spark 侧"读配置 → 透传到 writer"的完整链路，使用户既能用表属性 `delete-granularity` 静态配置，也能用 Spark session/write option `delete-granularity` 临时覆盖，并在 MoR 的 DELETE/UPDATE/MERGE 以及 `RewritePositionDeletes` action 中生效。

设计上的取舍是：粒度只对 position deletes 生效（equality deletes 不受影响），默认行为保持 `PARTITION` 与历史一致，避免破坏现有工作负载；option 优先级高于 table property，便于一次性任务覆盖。

## 如何达成设计目的

实现路径分为四步：(1) 在 `SparkWriteOptions` 中新增 `DELETE_GRANULARITY = "delete-granularity"` 这个 option key；(2) 在 `SparkWriteConf` 中新增 `deleteGranularity()` 方法，按"option → table property → 默认值 `DELETE_GRANULARITY_DEFAULT`"的优先级解析字符串并通过 `DeleteGranularity.fromString` 转为枚举；(3) 在 MoR 写入路径 `SparkPositionDeltaWrite` 的 `WriterFactory` 中读取该配置并透传给 `ClusteredPositionDeleteWriter` / `FanoutPositionOnlyDeleteWriter`，在 position deletes 重写路径 `SparkPositionDeletesRewrite` 中同样读取并透传；(4) 给 `WritersBenchmark` 中三个 position delete 写入基准各拆成 FILE/PARTITION 两个变体，并在四个测试类中加上针对 FILE/PARTITION 的端到端验证。

## 修改详情

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java

**修改目的**：新增 `delete-granularity` 这个 Spark write option key。

**工作逻辑**：在已有 compression 系列选项之后追加 `public static final String DELETE_GRANULARITY = "delete-granularity";`，附注释说明它用于覆盖删除粒度。这是一个纯常量定义，供 `SparkWriteConf` 解析 option 时引用。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java

**修改目的**：把"删除粒度"配置解析逻辑封装进 write conf。

**工作逻辑**：新增 import `DeleteGranularity`，并在类末尾新增方法：

```java
public DeleteGranularity deleteGranularity() {
  String valueAsString = confParser.stringConf()
      .option(SparkWriteOptions.DELETE_GRANULARITY)
      .tableProperty(TableProperties.DELETE_GRANULARITY)
      .defaultValue(TableProperties.DELETE_GRANULARITY_DEFAULT)
      .parse();
  return DeleteGranularity.fromString(valueAsString);
}
```

`confParser` 的链式 API 已经实现了"option > table property > default"的优先级，所以这里直接复用。解析得到的字符串再交给 `DeleteGranularity.fromString`，遇到非法值会抛 `IllegalArgumentException`（"Unknown delete granularity"），由 `TestSparkWriteConf.testDeleteGranularityInvalidValue` 覆盖。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java

**修改目的**：在 MoR 的 DELETE/UPDATE/MERGE 写入路径上把删除粒度透传到实际的 delete writer。

**工作逻辑**：

1. 新增 import `DeleteGranularity`。
2. 在内部 `WriterFactory`（约 432 行的 `createPositionDeleteWriter`）中，从 `context` 取出 `DeleteGranularity deleteGranularity = context.deleteGranularity();`，然后构造 writer 时把它传进去：`inputOrdered` 为 true 时用 `new ClusteredPositionDeleteWriter<>(writers, files, io, targetFileSize, deleteGranularity)`，否则用 `new FanoutPositionOnlyDeleteWriter<>(writers, files, io, targetFileSize, deleteGranularity)`。
3. `WriterContext`（持有写入上下文）新增字段 `private final DeleteGranularity deleteGranularity;`，构造时从 `writeConf.deleteGranularity()` 读取赋值，并新增 getter `DeleteGranularity deleteGranularity()`。这样 `WriterFactory` 通过 `context.deleteGranularity()` 即可拿到。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java

**修改目的**：在 `RewritePositionDeletes` action 的重写路径上也透传删除粒度。

**工作逻辑**：这条路径比 MoR 写入更复杂，因为涉及 `Write` 实现、`WriteFactory`、`ClusteredPositionDeleteWriter<InternalRow>` 的构造，所以改动点更多：

1. 顶层 `SparkPositionDeletesRewrite` 字段新增 `private final DeleteGranularity deleteGranularity;`，构造时从 `writeConf.deleteGranularity()` 读取。
2. 在构建 `WriteFactory` 时把 `deleteGranularity` 作为构造参数传入。
3. `WriteFactory` 内部新增对应字段与构造参数，并在 `createBatchWriter`/`createSingleBatchWriter` 等方法里把它继续传给 `SparkPositionDeletesRewriter`。
4. `SparkPositionDeletesRewriter` 新增 `deleteGranularity` 字段与构造参数，并在 `writerWithRow()` / `writerWithoutRow()` 懒加载构造 `ClusteredPositionDeleteWriter` 时把它作为最后一个构造参数传入。这样无论是带 row 还是不带 row 的 position delete 写入，都会按配置的粒度组织删除文件。

注意构造函数签名都新增了 `DeleteGranularity deleteGranularity` 形参，调用方需同步更新，文档注释里也补了 `@param deleteGranularity delete granularity`。

### spark/v3.4/spark/src/jmh/java/org/apache/iceberg/spark/source/WritersBenchmark.java

**修改目的**：让 JMH 基准能够分别度量 FILE 与 PARTITION 两种粒度下 position delete 写入的性能。

**工作逻辑**：原先三个 `@Benchmark` 方法——`writeUnpartitionedClusteredPositionDeleteWriter`、`writeUnpartitionedFanoutPositionDeleteWriter`、`writeUnpartitionedFanoutPositionDeleteWriterShuffled`——都隐式使用默认（PARTITION）粒度。改造方式统一：把每个公开的 `@Benchmark` 方法拆成两个，分别命名为 `...PartitionGranularity` 和 `...FileGranularity`，各自调用一个 `private` 重载方法并把 `DeleteGranularity.PARTITION` 或 `DeleteGranularity.FILE` 传入；私有方法签名新增 `DeleteGranularity deleteGranularity` 参数，构造 `ClusteredPositionDeleteWriter` / `FanoutPositionOnlyDeleteWriter` 时把它作为新构造参数传入。这样 JMH 会为每种粒度单独产出度量结果，便于横向对比。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java

**修改目的**：单测 `SparkWriteConf.deleteGranularity()` 的解析逻辑（默认值、表属性、write option、非法值）。

**工作逻辑**：新增四个测试：
- `testDeleteGranularityDefault`：不设任何配置，断言返回 `PARTITION`（默认）。
- `testDeleteGranularityTableProperty`：表属性设为 `FILE`，断言返回 `FILE`。
- `testDeleteGranularityWriteOption`：表属性设为 `PARTITION`，再用 write option 覆盖为 `FILE`，断言返回 `FILE`——验证 option 优先级高于 table property。
- `testDeleteGranularityInvalidValue`：表属性设为 `"invalid"`，断言调用抛 `IllegalArgumentException` 且消息包含 "Unknown delete granularity"。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadDelete.java

**修改目的**：端到端验证 MoR DELETE 在 FILE/PARTITION 两种粒度下的删除文件数。

**工作逻辑**：新增 `testDeleteFileGranularity` 和 `testDeletePartitionGranularity` 两个 `@Test`，都委托给私有方法 `checkDeleteFileGranularity(DeleteGranularity)`。该方法建一张按 `dep` 分区的表，设置 `delete-granularity` 表属性，写入 4 次（hr/hardware 各 2 次，共 8 行，id 1-4），执行 `DELETE FROM ... WHERE id = 1 OR id = 3`，断言快照总数为 5，再通过 `validateMergeOnRead` 校验：data files 2 个，delete files 在 `FILE` 粒度下为 4（每个数据文件一个），在 `PARTITION` 粒度下为 2（每个分区一个）。最后校验剩余行符合预期（id 为 2、4 的 hr/hardware 各一行）。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadMerge.java

**修改目的**：端到端验证 MoR MERGE 在两种粒度下的行为。

**工作逻辑**：新增 `testMergeDeleteFileGranularity` / `testMergeDeletePartitionGranularity`，委托给 `checkMergeDeleteGranularity(DeleteGranularity)`。建表后写 4 批数据（hr/it 各 2 批，id 1-4），构造 source view 为 `[1, 3, 5]`，执行 MERGE：匹配则 DELETE，不匹配则 INSERT `(-1, 'other')`。断言快照数为 5，data files 3 个，delete files FILE 下 4 个 / PARTITION 下 2 个，inserted data files 1 个，并校验最终行集合。这一测试同时覆盖了 delete 与 insert 两条路径。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadUpdate.java

**修改目的**：端到端验证 MoR UPDATE 在两种粒度下的行为。

**工作逻辑**：新增 `testUpdateFileGranularity` / `testUpdatePartitionGranularity`，委托给 `checkUpdateFileGranularity(DeleteGranularity)`。建表写 4 批数据后执行 `UPDATE ... SET id = id - 1 WHERE id = 1 OR id = 3`。MoR 的 UPDATE 等价于"删旧 + 插新"，所以校验时 data files 2、delete files FILE=4/PARTITION=2、inserted data files 2，并按 `dep ASC, id ASC` 校验最终 8 行（原 id 1/3 各变 0/2，原 2/4 不变，所以 hr 和 it 各有 0,2,2,4）。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java

**修改目的**：验证 `RewritePositionDeletes` action 在两种粒度下产出的删除文件数符合预期。

**工作逻辑**：新增 `testFileGranularity` / `testPartitionGranularity`，委托给 `checkDeleteGranularity(DeleteGranularity)`。建一张 2 分区无分区表（`createTableUnpartitioned(2, SCALE)`），设置 `delete-granularity` 表属性，取出 2 个数据文件并为之写 position deletes（`writePosDeletesForFiles(table, 2, DELETES_SCALE, dataFiles)`），断言初始 delete files 为 2；然后执行 `SparkActions.get(spark).rewritePositionDeletes(table).option(SizeBasedFileRewriter.REWRITE_ALL, "true").execute()`，断言重写后 `addedDeleteFilesCount` 在 `FILE` 粒度下为 2、在 `PARTITION` 粒度下为 1。这恰好体现了两种粒度的核心差异：FILE 模式重写后仍按数据文件拆分，PARTITION 模式重写后会把分区内删除合并成一个文件。

## 小结

这是一个把 core 已有能力（`DeleteGranularity` + writer 构造参数）暴露到 Spark 3.4 写入/重写链路的 backport。改动以"加字段、加构造参数、层层透传"为主，没有改变默认行为（仍为 PARTITION），但让用户能通过表属性或 write option 选择 FILE 粒度。测试覆盖了配置解析的四种情况、MoR 的 DELETE/MERGE/UPDATE 三种命令、以及 `RewritePositionDeletes` action，并配套拆分了 JMH 基准以便度量两种粒度的写入开销。
