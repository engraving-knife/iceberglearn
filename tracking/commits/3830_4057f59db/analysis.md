# 提交 3830：Spark: Fix time-travel filter on renamed columns in distributed planning mode (#16523)

## 提交信息

- **序号**：3830 / 4088
- **哈希**：4057f59dbee6878b3986f7cc39b1f30a59ee9ee3
- **短哈希**：4057f59db
- **日期**：2026-06-05 16:56:12 -0700
- **作者**：sanshi <43472713+lilei1128@users.noreply.github.com>
- **提交说明**：Spark: Fix time-travel filter on renamed columns in distributed planning mode (#16523)
- **PR/Issue**：#16523

## 总体目的

本提交修复 Iceberg Spark 分布式扫描规划模式（distributed planning mode）下，对"已被重命名的列"做时间旅行（time-travel）过滤查询时失败的问题。分布式规划模式是 Iceberg 的一种扫描规划方式，它把 manifest 读取与文件过滤分发到 Spark executor 上并行执行（而非在 driver 端串行执行），用于加速大表的扫描规划。

问题场景：用户有一个表，列名为 `col`。在某个快照 S1 时插入数据，之后把列 `col` 重命名为 `value` 并继续写入（产生快照 S2）。当用户执行时间旅行查询 `SELECT * FROM t VERSION AS OF S1 WHERE col > 0` 时，过滤条件引用的是旧列名 `col`。在分布式规划模式下，`SparkDistributedDataScan` 在 executor 上读取 manifest 时，使用的是 `table.value().specs()`——即表的"当前"specs（反映重命名后的 schema），而非快照 S1 时的 specs。由于重命名后当前 schema 中已不存在 `col` 列，过滤表达式 `col > 0` 无法解析到对应字段，导致查询失败或过滤失效。

修复方式是在分布式规划的 manifest 读取路径上，使用 `specs()`（即 `BaseDistributedDataScan.specs()`，返回与所查询快照对应的 specs 集合）替代 `table().specs()`（当前表 specs）。`specs()` 已经考虑了时间旅行的快照上下文，返回该快照生效时的 specs，从而使过滤条件能正确按旧列名解析。

## 如何达成设计目的

设计上把"specs 来源"从表对象改为扫描上下文：
- `BaseDistributedDataScan.specCache` 中 `table().specs()` 改为 `specs()`，让 spec 缓存基于快照上下文的 specs。
- `SparkDistributedDataScan` 中构造 `ReadDataManifest` 与 `ReadDeleteManifest` 时，把 `specs()` 显式传入并保存为字段，executor 端读取 manifest 时使用传入的 specs 而非 `table.value().specs()`。
- `DeleteFileIndex.builderFor(...).specsById(...)` 也从 `table().specs()` 改为 `specs()`。
这样在时间旅行到旧快照时，manifest 读取与过滤都基于该快照的 specs，重命名前的列名能正确解析。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseDistributedDataScan.java` (+1/-1 lines)

**修改目的**：让 spec 缓存基于快照上下文的 specs 而非当前表 specs。

**工作逻辑**：
```java
-    table().specs().forEach((specId, spec) -> cache.put(specId, load.apply(spec)));
+    specs().forEach((specId, spec) -> cache.put(specId, load.apply(spec)));
```
`specs()` 返回与所查询快照对应的 specs，时间旅行时与旧快照的 schema 一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/SparkDistributedDataScan.java` (+12/-5 lines)

**修改目的**：在分布式 manifest 读取路径使用快照上下文的 specs。

**工作逻辑**：
- 构造 `ReadDataManifest` 与 `ReadDeleteManifest` 时传入 `specs()`：
```java
.flatMap(new ReadDataManifest(tableBroadcast(), specs(), context(), withColumnStats));
...
.flatMap(new ReadDeleteManifest(tableBroadcast(), specs(), context()));
```
- 两个 FlatMapFunction 内部新增 `specs` 字段并在构造时赋值，`call` 方法中改用传入的 `specs` 而非 `table.value().specs()`：
```java
// 旧: Map<Integer, PartitionSpec> specs = table.value().specs();
// 新: 直接使用 this.specs 字段
return new ClosingIterator<>(
    ManifestFiles.read(manifest, io, specs)...
```
- `DeleteFileIndex.builderFor(deleteFiles).specsById(table().specs())` 改为 `.specsById(specs())`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/SparkDistributedDataScan.java` (+12/-5 lines)

**修改目的**：同 v3.5，对 Spark 4.0 应用相同修复。改动完全一致。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/SparkDistributedDataScan.java` (+12/-5 lines)

**修改目的**：同上，对 Spark 4.1 应用相同修复。改动完全一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+75/-0 lines)

**修改目的**：覆盖分布式规划模式下时间旅行过滤重命名列的场景。

**工作逻辑**：
新增两个测试：
- `testTimeTravelFilterOnRenamedColumn`：建表 `(id, col)`，启用分布式规划模式，插入 3 行（含 1 行 col=0），记录快照 S1；重命名 `col` 为 `value`，插入新行；用 `VERSION AS OF S1` 时间旅行并 `WHERE col > 0` 过滤，验证只返回 S1 中 col>0 的两行。覆盖 DataFrame API 与 SQL 两种方式。
- `testTimeTravelFilterOnRenamedColumnWithDeleteFiles`：建分区表 v2，启用 merge-on-read 删除与分布式规划，插入数据后删除一行，记录快照，重命名列，时间旅行并过滤，验证删除文件索引也基于快照 specs 正确工作，排除已删除行。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+75/-0 lines)

**修改目的**：Spark 4.0 同步测试。改动与 v3.5 一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+75/-0 lines)

**修改目的**：Spark 4.1 同步测试。改动与 v3.5 一致。

## 总结

本提交修复了分布式扫描规划模式下时间旅行查询对重命名列过滤失败的缺陷：manifest 读取误用了当前表 specs 而非快照上下文 specs。修复把 specs 来源统一改为 `specs()`，确保时间旅行时按旧快照的 schema 解析过滤条件。改动覆盖三个 Spark 版本，测试包含数据文件与删除文件两种场景。这是时间旅行与分布式规划交互正确性的重要修复，对生产中频繁的 schema 演进 + 时间旅行查询场景意义重大。
