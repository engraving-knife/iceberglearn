# 提交 1455：Spark 3.5: Make where clause case sensitive in rewrite data files (#11439)

## 提交信息

- **序号**：1455 / 4088
- **哈希**：d8326d876574241b4811017cd489f099d4b74f56
- **短哈希**：d8326d876
- **日期**：2024-12-02（Mon Dec 2 13:58:57 2024 -0800）
- **作者**：AGW <ludlows@users.noreply.github.com>
- **提交说明**：Spark 3.5: Make where clause case sensitive in rewrite data files (#11439)
- **PR/Issue**：#11439

## 总体目的

Iceberg 的 Spark 过程 `rewrite_data_files` 支持通过 `where` 参数传入过滤条件（如 `where => 'C1 > 0'`），用来限定只重写满足条件的文件。该 `where` 字符串会经过 `SparkExpressionConverter.collectResolvedSparkExpression` 解析为 Spark 已解析表达式，再转换为 Iceberg `Expression`，最终交给 `RewriteDataFilesSparkAction` 中的 `table.newScan().filter(filter)` 执行文件扫描。

问题在于：Spark 在 `spark.sql.caseSensitive = false`（默认）时，会以大小写不敏感方式解析 `C1` 到表中实际的 `c1` 列，Spark 侧的解析与绑定能通过。但生成的 Iceberg `Expression` 中引用的列名可能是用户书写的大小写（`C1`），而 `RewriteDataFilesSparkAction` 在构造 `TableScan` 时**没有把 Spark 的大小写敏感设置透传给 Iceberg 的扫描**——Iceberg `TableScan` 默认 `caseSensitive = true`。于是当 Iceberg 在扫描阶段把这个表达式绑定（`Binder.bind`）到表 schema 时，按大小写敏感模式查找名为 `C1` 的列，找不到（实际列名为 `c1`），导致过滤失效或绑定异常。

本提交让 `RewriteDataFilesSparkAction` 在初始化时读取 Spark 的 `spark.sql.caseSensitive` 配置，并在 `table.newScan()` 上显式调用 `.caseSensitive(caseSensitive)`，使 Iceberg 扫描的列名匹配行为与 Spark 解析阶段保持一致。

## 如何达成设计目的

通过两处改动：
1. 在 `RewriteDataFilesSparkAction` 构造函数中调用 `SparkUtil.caseSensitive(spark)` 读取配置并保存到实例字段 `caseSensitive`；
2. 在 `planFileGroups` 中构造 `TableScan` 时链式调用 `.caseSensitive(caseSensitive)`，使 Iceberg 扫描按 Spark 的大小写策略绑定 filter 表达式中的列名。

并新增测试 `testFilterCaseSensitivity`：在 `caseSensitive = false` 下用大写 `C1` 作为 where 条件，验证能正常完成重写（10 个文件被重写为 1 个），且数据不变。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（修改，+3 行 import/字段/赋值，+1 行 scan 调用）

**修改目的**：让 rewrite data files 的 Iceberg 扫描遵循 Spark 的大小写敏感配置。

**工作逻辑**：

1. 新增 import：
```java
import org.apache.iceberg.spark.SparkUtil;
```

2. 新增实例字段：
```java
private boolean caseSensitive;
```

3. 在构造函数中读取配置（紧跟在禁用 AQE 之后）：
```java
RewriteDataFilesSparkAction(SparkSession spark, Table table) {
  super(spark.cloneSession());
  spark().conf().set(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), false);
  this.caseSensitive = SparkUtil.caseSensitive(spark);  // 新增
  this.table = table;
}
```

`SparkUtil.caseSensitive(spark)` 的实现是 `Boolean.parseBoolean(spark.conf().get("spark.sql.caseSensitive"))`，直接读取 Spark 配置项。

4. 在 `planFileGroups` 的扫描构造链上增加 `.caseSensitive(caseSensitive)`：
```java
CloseableIterable<FileScanTask> fileScanTasks =
    table
        .newScan()
        .useSnapshot(startingSnapshotId)
        .caseSensitive(caseSensitive)   // 新增
        .filter(filter)
        .ignoreResiduals()
        .planFiles();
```

这样当 `filter` 表达式（如 `greaterThan("C1", 0)`）被绑定到表 schema（列名为 `c1`）时，`Binder.bind` 会按 `caseSensitive = false` 做大小写不敏感匹配，正确解析到 `c1` 列。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`（修改，+24 行）

**修改目的**：新增回归测试，验证在 `caseSensitive = false` 下用大写列名作为 where 条件能正常工作。

**工作逻辑**：新增 `testFilterCaseSensitivity` 测试方法：

```java
@TestTemplate
public void testFilterCaseSensitivity() {
  createTable();                          // 建表：c1 int, c2 string, c3 string（列名小写）
  insertData(10);                         // 插入 10 个文件
  sql("set %s = false", SQLConf.CASE_SENSITIVE().key());  // 关闭大小写敏感
  List<Object[]> expectedRecords = currentData();
  List<Object[]> output =
      sql(
          "CALL %s.system.rewrite_data_files(table=>'%s', where=>'C1 > 0')",  // 大写 C1
          catalogName, tableIdent);
  assertEquals(
      "Action should rewrite 10 data files and add 1 data files",
      row(10, 1),
      Arrays.copyOf(output.get(0), 2));
  // verify rewritten bytes separately
  assertThat(output.get(0)).hasSize(4);
  assertThat(output.get(0)[2])
      .isInstanceOf(Long.class)
      .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));
  List<Object[]> actualRecords = currentData();
  assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
}
```

关键点：
- 表的列名为小写 `c1`，而 where 子句用大写 `C1`；
- 显式 `set spark.sql.caseSensitive = false`；
- 断言重写了 10 个文件、新增 1 个文件（即所有数据文件都满足 `C1 > 0` 被重写合并），且重写前后数据一致；
- 额外校验返回结果第 3 列（rewritten bytes）与快照 summary 中的 `REMOVED_FILE_SIZE_PROP` 一致。

若没有本提交的修复，此测试会在 Iceberg 扫描绑定 filter 时因找不到 `C1` 列而失败。

## 小结

- **成效**：修复了 `rewrite_data_files` 过程在 Spark 大小写不敏感模式下、where 子句中列名大小写与表 schema 不一致时过滤失效/异常的问题。改动很小但精准——把 Spark 的 `caseSensitive` 配置透传给 Iceberg 的 `TableScan`。
- **影响范围**：仅 Spark 3.5 模块的 `RewriteDataFilesSparkAction`（生产代码）与对应测试。不影响其他 Spark 版本（3.3/3.4 由后续提交 1460 单独修复）、不影响其他 action。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。需确认 1.4.x 分支的 `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java` 中存在 `caseSensitive(SparkSession)` 静态方法（该方法在 1.4.x 中已存在）。注意本提交仅修 Spark 3.5，若 1.4.x 同时维护 3.3/3.4，应一并回迁提交 1460（#11696）以保持各版本行为一致。测试中用到的 `snapshotSummary()`、`currentData()` 等辅助方法在 1.4.x 测试基类中应已存在，可直接复用。
