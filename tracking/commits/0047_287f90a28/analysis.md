# 提交 0047：Spark: Replace .size() > 0 with isEmpty() (#8814)

## 提交信息

- **序号**：0047 / 4088
- **哈希**：287f90a2839ffafd7d5e785e0f56b255e5517015
- **短哈希**：287f90a28
- **日期**：2023-10-13 14:52:39 +0200（作者时区为 -0700，提交行显示 05:52:39 -0700）
- **作者**：Kirill Saied
- **提交说明**：Spark: Replace .size() > 0 with isEmpty() (#8814)
- **PR/Issue**：#8814（关联 ISSUE #8810）

## 总体目的

这是 ISSUE #8810 系列代码清理在 Spark 模块的专项提交，与 0046（跨多个外围模块）、0050（Core 模块）属同一清理批次，按模块拆分以便审阅。提交将 Iceberg Spark 适配层（覆盖 Spark 3.2、3.3、3.4、3.5 四个并行维护的版本分支）中所有形如 `coll.size() > 0` 的非空判断替换为 `!coll.isEmpty()`，并将 `Assert.assertTrue(coll.size() > 0)` 形式的断言改写为更直接的 `Assert.assertFalse(coll.isEmpty())`。

这种替换的动机有二：一是可读性，`!isEmpty()` 比 `size() > 0` 更直接表达"非空"语义；二是性能，对某些集合实现（如基于链表或惰性求值的集合），`size()` 可能是 O(n) 操作，而 `isEmpty()` 通常只需 O(1) 检查首个元素。虽然本提交涉及的大多是 `ArrayList`/不可变 `List`，性能差异可忽略，但统一风格对长期可维护性有益。

本提交共改动 27 个文件、72 处，是三个清理提交中规模最大的。之所以改动量大，是因为 Iceberg 为 Spark 3.2/3.3/3.4/3.5 各维护一份近乎相同的源码副本（在 `spark/v3.2/`、`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/` 目录下），同一处清理需要在四个分支同步执行。这反映了 Iceberg 在多 Spark 版本兼容维护上的代价。

## 如何达成设计目的

整体思路是机械式、跨版本同步替换。改动分两类：生产代码（`FileScanTaskSetManager`、`ScanTaskSetManager`、`RewriteDataFilesSparkAction`、`SparkBatch`、`SparkBinPackPositionDeletesRewriter`、`SparkZOrderDataRewriter`、`SparkPositionDeletesRewriteBuilder`）和测试代码（`TestScanTaskSerialization`、`TestCreateActions`、`TestRewritePositionDeleteFilesAction`）。生产代码中 `Preconditions.checkArgument(coll.size() > 0, ...)` 改为 `!coll.isEmpty()`；测试中 `Assert.assertTrue(coll.size() > 0)` 改为 `Assert.assertFalse(coll.isEmpty())`，少数保留 `Assert.assertTrue(...)` 外壳但内部改为 `!coll.isEmpty()`（当外层 `assertTrue` 还承担其他断言语义时）。

## 修改详情

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/FileScanTaskSetManager.java` 及 v3.3/v3.4/v3.5 对应的 `ScanTaskSetManager.java`

**修改目的**：将"暂存扫描任务时校验任务列表非空"的判断改用 `isEmpty`。

**工作逻辑**：`stageTasks(Table table, String setID, List<...> tasks)` 中 `Preconditions.checkArgument(tasks != null && tasks.size() > 0, "Cannot stage null or empty tasks")` 改为 `tasks != null && !tasks.isEmpty()`。注意 v3.2 的类名是 `FileScanTaskSetManager`，v3.3+ 改名为 `ScanTaskSetManager`，本提交在四个版本同步修改。这是 Spark 多版本维护的典型例证。

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`

**修改目的**：数据文件重写动作中"按分区聚合文件组后判断是否有文件组"改用 `isEmpty`。

**工作逻辑**：`fileGroups.size() > 0` 改为 `!fileGroups.isEmpty()`，控制是否把该分区的文件组放入 `fileGroupsByPartition` 映射。仅 v3.2 路径有此改动（v3.3+ 该段逻辑或写法略有不同）。

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` 及 v3.3 对应文件

**修改目的**：判定是否启用 Parquet 向量化批量读时"投影列非空"判断改用 `isEmpty`。

**工作逻辑**：`expectedSchema.columns().size() > 0` 改为 `!expectedSchema.columns().isEmpty()`，作为 `parquetBatchReadsEnabled()`/`useParquetBatchReads()` 短路条件之一（要求至少有一列被投影且全部为基本类型）。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/SparkBinPackPositionDeletesRewriter.java` 及 v3.4/v3.5 对应文件

**修改目的**：位置删除文件 bin-pack 重写器中"文件组非空"校验改用 `isEmpty`。

**工作逻辑**：`doRewrite(String groupId, List<PositionDeletesScanTask> group)` 中 `Preconditions.checkArgument(group.size() > 0, "Empty group")` 改为 `!group.isEmpty()`，确保进入重写逻辑的文件组不为空。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderDataRewriter.java` 及 v3.4/v3.5 对应文件

**修改目的**：ZOrder 数据重写器中"有效 ZOrder 列非空"校验改用 `isEmpty`。

**工作逻辑**：`Preconditions.checkArgument(validZOrderColNames.size() > 0, "Cannot ZOrder, all columns provided were identity partition columns and cannot be used")` 改为 `!validZOrderColNames.isEmpty()`。这是 ZOrder 重写前置校验：若所有候选列都是恒等分区列（不能用于 ZOrder），则报错。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewriteBuilder.java` 及 v3.4/v3.5 对应文件

**修改目的**：位置删除重写构建器中"扫描任务列表非空"校验改用 `isEmpty`。

**工作逻辑**：`Preconditions.checkArgument(tasks != null && tasks.size() > 0, "No scan tasks found for %s", fileSetId)` 改为 `tasks != null && !tasks.isEmpty()`。

### `spark/v3.2/spark/src/test/java/org/apache/iceberg/TestScanTaskSerialization.java` 及 v3.3/v3.4/v3.5 对应文件

**修改目的**：扫描任务序列化测试中断言任务组非空改用 `isEmpty`。

**工作逻辑**：两处（Kryo 序列化与 Java 序列化测试方法）`Assert.assertTrue("Task group can't be empty", taskGroup.tasks().size() > 0)` 改为 `Assert.assertTrue("Task group can't be empty", !taskGroup.tasks().isEmpty())`。这里保留外层 `assertTrue` 是因为带消息字符串的断言形式，仅替换内部条件。

### `spark/v3.2/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java` 及 v3.3/v3.4/v3.5 对应文件

**修改目的**：表创建/迁移动作测试中"查询结果非空"断言改用 `isEmpty`。

**工作逻辑**：这是本提交改动密度最高的一组文件，每个版本约 10 处。`Assert.assertTrue(results.size() > 0)` 改为 `Assert.assertFalse(results.isEmpty())`。这些断言出现在迁移、追加、覆盖、列变更等多种场景，用于校验迁移后的表能查出数据。v3.5 额外有一处 `sourceTable.partitionColumnNames().size() == 0` 改为 `isEmpty()`，用于判断源表是否为分区表。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` 及 v3.5 对应文件

**修改目的**：位置删除文件重写测试中"删除文件行非空"断言改用 `isEmpty`。

**工作逻辑**：`Assert.assertTrue("Empty delete file found", rows.size() > 0)` 改为 `Assert.assertFalse("Empty delete file found", rows.isEmpty())`，校验读取位置删除表时确实拿到了对应删除文件的行。

## 小结

本提交将 Iceberg Spark 适配层在 3.2/3.3/3.4/3.5 四个版本分支中散落的 72 处 `.size() > 0` 统一替换为 `!isEmpty()` 或 `isEmpty()` 形式，作为 ISSUE #8810 清理批次的 Spark 专项，在不改变运行时行为的前提下统一了多版本分支的代码风格，也侧面体现了 Iceberg 维护多 Spark 版本的代码同步成本。
