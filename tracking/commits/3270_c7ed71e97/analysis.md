# 提交 3270：Spark 4.1: Align handling of branches in reads and writes (#15288)

## 提交信息

- **序号**：3270 / 4088
- **哈希**：c7ed71e970781ca6c7fa44a3900625600830e9d4
- **短哈希**：c7ed71e97
- **日期**：2026-02-16
- **作者**：Anton Okolnochyi
- **提交说明**：Spark 4.1: Align handling of branches in reads and writes (#15288)
- **PR/Issue**：#15288

## 总体目的

本提交对 Spark 4.1 适配器中"分支（branch）"的读取与写入处理进行统一对齐，并改变了一个关键行为：**当用户已显式指定分支时，会话级 WAP（Write-Audit-Publish）分支不再与之冲突报错，而是被显式分支优先覆盖**。

此前 Spark 4.1 中分支的解析逻辑分散在两个 conf 类中，且读路径与写路径的行为不一致：
- `SparkReadConf.branch()`：在 identifier 中传入的 `branch` 与读选项 `SparkReadOptions.BRANCH` 之间做一致性校验，再回退到 WAP 分支（仅当 `table.refs()` 中存在该 WAP 分支时才用）。校验失败抛 `ValidationException`。
- `SparkWriteConf.branch()`：先判断 `wapEnabled()`，若启用则取会话 `WAP_BRANCH`，再校验它不能与 identifier 的 `branch` 共存——一旦用户既设了显式分支又开了 WAP 分支，写直接抛 `ValidationException("Cannot write to both branch and WAP branch...")`。这里的行为与读路径不一致：读路径是"WAP 分支仅在显式分支未设且存在时才用"，写路径是"WAP 启用就强制校验不与显式分支冲突"。

更根本的痛点是：分支解析逻辑同时依赖会话 SQL 配置（`SparkSQLProperties.WAP_BRANCH`/`WAP_ID`）、表属性（`WRITE_AUDIT_PUBLISH_ENABLED`）、读/写选项、以及 identifier 中的 branch，这四类来源的优先级与校验规则散落在 `SparkReadConf.branch()` 与 `SparkWriteConf.branch()` 两个方法里，逻辑重复、容易走偏。此外，写路径此前根本没有 `SparkWriteOptions.BRANCH` 这个写选项常量——分支只能通过 SQL identifier（如 `tbl.branch_xxx`）指定，无法像读路径那样通过 `.option("branch", ...)` 指定。

本提交把两条路径的分支解析逻辑统一抽到 `SparkTableUtil.determineReadBranch(...)` / `determineWriteBranch(...)` 两个静态方法，二者采用一致的优先级规则：**显式选项分支 > identifier 分支 > 会话 WAP 分支**；当 identifier 与选项同时指定但值不同时才报冲突（`IllegalArgumentException`），而当显式分支存在时直接覆盖会话 WAP 分支——即"显式分支优先于 WAP 分支"，从而允许用户在开了 WAP 的表上仍能向某个具体分支写入。同时新增 `SparkWriteOptions.BRANCH` 写选项常量，使写路径也支持通过 `.option("branch", ...)` 指定分支，与读路径对齐。

## 如何达成设计目的

整体设计是"集中分支解析逻辑 + 统一选项类型 + 新增写分支选项"。涉及三类改动：(1) 在 `SparkTableUtil` 中新增 `determineReadBranch` / `determineWriteBranch` 两个公共静态方法并完善 Javadoc，把 WAP 校验抽到 `wapSessionBranch(spark)` 私有方法；(2) 改造 `SparkReadConf` / `SparkWriteConf`：把字段类型由 `Map<String, String>` 改为 `CaseInsensitiveStringMap`（与 Spark DataSource V2 API 的选项类型对齐），构造器新增无 options 的便捷重载，`branch()` 方法体改为转调 `SparkTableUtil`；(3) 新增 `SparkWriteOptions.BRANCH = "branch"` 常量；同步更新 `SparkTable` 中的 delete 调用、各类 benchmark 与测试以适配新构造器签名与新行为。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+83/-18 lines)

**修改目的**：集中分支解析逻辑为两个公共静态方法，统一读写路径行为。

**工作逻辑**：
- 新增 `determineWriteBranch(SparkSession, Table, String branch, CaseInsensitiveStringMap options)`：先从 options 取 `SparkWriteOptions.BRANCH` 作为 `optionBranch`；若存在则校验与 identifier 的 `branch` 不冲突（`Preconditions.checkArgument`，冲突信息为 `"Explicitly configured branch [%s] and write option [%s] are in conflict"`），返回 `optionBranch`；否则若 `branch == null && wapEnabled(table)`，返回 `wapSessionBranch(spark)`；否则返回 `branch`。这与旧行为的关键差异：显式 branch（identifier 或 option）会直接返回，不再因 WAP 分支存在而抛错。保留旧的无 options 重载 `determineWriteBranch(spark, table, branch)` 转调新方法。
- 新增 `determineReadBranch(SparkSession, Table, String branch, CaseInsensitiveStringMap options)`：与写路径对称，但从 `SparkReadOptions.BRANCH` 取 optionBranch；WAP 分支仅在 `table.refs().containsKey(wapBranch)` 时才返回（读路径保留旧约束——只读已存在的分支）。
- 抽出私有 `wapSessionBranch(SparkSession)`：从会话取 `WAP_ID` 与 `WAP_BRANCH`，校验二者不能同时设置（`Preconditions.checkArgument`，原 `ValidationException` 改为 `IllegalArgumentException`），返回 `wapBranch`。
- 异常类型由 `ValidationException` 改为 `IllegalArgumentException`（更通用，避免依赖 `iceberg-api` 的异常类）。Javadoc 详细说明优先级规则与"分支可在写时创建"等语义。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+18/-31 lines)

**修改目的**：把 `branch()` 委托给 `SparkTableUtil`，并把选项类型改为 `CaseInsensitiveStringMap`。

**工作逻辑**：字段 `Map<String, String> readOptions` 改为 `CaseInsensitiveStringMap options` 并保存到字段（原先不保存 options，直接传给 `SparkConfParser`）；构造器改为接收 `CaseInsensitiveStringMap`，新增无 options 便捷构造器 `SparkReadConf(spark, table)` 与 `SparkReadConf(spark, table, options)` 转调。`branch()` 方法体由原先 20 多行（解析 optionBranch、做 `ValidationException` 一致性校验、回退 WAP）压缩为一行 `return SparkTableUtil.determineReadBranch(spark, table, branch, options);`。移除 `java.util.Map`、`ValidationException`、`PropertyUtil` 等 import。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+23/-23 lines)

**修改目的**：与读路径对齐，把 `branch()` 委托给 `SparkTableUtil`，选项类型改为 `CaseInsensitiveStringMap`。

**工作逻辑**：字段 `Map<String, String> writeOptions` 改为 `CaseInsensitiveStringMap options`；构造器同样新增无 options 便捷重载。`overwriteMode()` 与 `extraSnapshotMetadata()` 中原先用 `writeOptions.get(...)` / `PropertyUtil.propertiesWithPrefix(writeOptions, ...)` 改为 `options.get(...)` / `PropertyUtil.propertiesWithPrefix(options, ...)`（`CaseInsensitiveStringMap` 也实现 `Map<String,String>`，兼容）。`branch()` 方法体压缩为 `return SparkTableUtil.determineWriteBranch(spark, table, branch, options);`，移除 `ValidationException` import。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+3/-0 lines)

**修改目的**：新增 `BRANCH` 写选项常量，使写路径也能通过 `.option("branch", ...)` 指定分支。

**工作逻辑**：新增 `public static final String BRANCH = "branch";`，注释说明"Overrides the target branch for write operations"。这与既有的 `SparkReadOptions.BRANCH` 形成对称，让 `determineWriteBranch` 能从 options 中取到该值。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+1/-1 lines)

**修改目的**：适配 `determineWriteBranch` 新签名。

**工作逻辑**：DELETE 操作中 `branch = SparkTableUtil.determineWriteBranch(sparkSession(), branch)` 改为 `SparkTableUtil.determineWriteBranch(sparkSession(), icebergTable, branch)`，传入表实例以供 `wapEnabled(table)` 判断。

### `spark/v4.1/spark-extensions/src/jmh/java/org/apache/iceberg/spark/PlanningBenchmark.java` (+1/-1 lines) 和 `spark/v4.1/spark-extensions/src/jmh/java/org/apache/iceberg/spark/TaskGroupPlanningBenchmark.java` (+2/-3 lines)

**修改目的**：适配新构造器签名。

**工作逻辑**：原本 `new SparkReadConf(spark, table, ImmutableMap.of())` 改为 `new SparkReadConf(spark, table)`（用新增的无 options 便捷构造器），并移除 `ImmutableMap` import（`TaskGroupPlanningBenchmark`）。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` (+8/-7 lines)、`TestMerge.java` (+8/-9 lines)、`TestUpdate.java` (+8/-7 lines)

**修改目的**：把"显式分支 + WAP 分支冲突报错"的测试改为"显式分支优先于 WAP 分支"的测试。

**工作逻辑**：三个测试原先在 `withSQLConf(ImmutableMap.of(SparkSQLProperties.WAP_BRANCH, "wap"), ...)` 下断言 `DELETE`/`MERGE`/`UPDATE` 抛 `ValidationException("Cannot write to both branch and WAP branch...")`；现在改为执行该 SQL 并断言数据按预期写入了显式分支（`commitTarget()`），注释统一加 "writing to explicit branch should succeed even with WAP branch set"。`TestUpdate` 把 SET 值由 `'a'` 改为 `'software'`、`WHERE dep='hr'` 以匹配 setup 数据。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestPartitionedWritesToWapBranch.java` (+14/-6 lines)

**修改目的**：把"branch 与 WAP branch 不能共存"的测试改为"显式 branch 优先"的测试，并修正一处异常类型断言。

**工作逻辑**：原 `testBranchAndWapBranchCannotBothBeSetForWrite` 重命名为 `testWriteToBranchWithWapBranchSet`：先创建 `test2` 分支，再 `INSERT INTO %s.branch_test2 VALUES (4, 'd')`，断言数据进入 `test2` 分支而当前分支不受影响。另一个测试中 `WAP_ID` 与 branch 冲突的异常类型由 `ValidationException` 改为 `IllegalArgumentException`，与 `SparkTableUtil` 中异常类型变更对齐。import 由 `ValidationException` 换为 `ImmutableList`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkWriteConf.java` (+13/-9 lines)

**修改目的**：适配 `SparkWriteConf` 新构造器签名。

**工作逻辑**：多处 `new SparkWriteConf(spark, table, ImmutableMap.of())` 改为 `new SparkWriteConf(spark, table)`；需要传 options 的地方由 `Map<String,String>` 改为 `new CaseInsensitiveStringMap(ImmutableMap.of(...))`。涉及 `testDeleteGranularityDefault`、`testDeleteGranularityWithTableProperty`、`testDeleteGranularityWithWriteOption`、`testDeleteGranularityInvalidConfig`、`testAdvisoryPartitionSize`、`testSparkWriteConfDistributionDefault`、`testSparkWriteConfDistributionModeWithWriteOption`、若干 `withSQLConf` 用例、`extraSnapshotMetadata` 用例、`testDVWriteConf`、`testWriteProperties` 等。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkDistributionAndOrderingUtil.java` (+2/-3 lines)

**修改目的**：适配新构造器签名。

**工作逻辑**：三个 `checkWriteDistributionAndOrdering` 辅助方法中 `new SparkWriteConf(spark, table, ImmutableMap.of())` 改为 `new SparkWriteConf(spark, table)`，移除 `ImmutableMap` import。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java` (+3/-3 lines) 和 `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+1/-1 lines)

**修改目的**：适配新构造器签名。

**工作逻辑**：`new SparkReadConf(spark, table, Collections.emptyMap())` 改为 `new SparkReadConf(spark, table)`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/SparkDistributedDataScanTestBase.java`、`TestSparkDistributedDataScanDeletes.java`、`TestSparkDistributedDataScanFilterFiles.java`、`TestSparkDistributedDataScanReporting.java`（各 +1/-2 lines）

**修改目的**：适配新构造器签名。

**工作逻辑**：四个测试基类/子类中 `new SparkReadConf(spark, table, ImmutableMap.of())` 改为 `new SparkReadConf(spark, table)`，移除 `ImmutableMap` import。这些测试用 `SparkDistributedDataScan` 做分布式扫描，仅需要一个无 options 的读 conf 即可。

## 总结

本提交通过把读写分支解析逻辑集中到 `SparkTableUtil.determineReadBranch`/`determineWriteBranch`，统一了 Spark 4.1 中分支的优先级规则（显式选项 > identifier > 会话 WAP），并改变了"显式分支与 WAP 分支冲突即报错"旧行为为"显式分支优先覆盖"。同时把 `SparkReadConf`/`SparkWriteConf` 的选项类型对齐为 `CaseInsensitiveStringMap`、新增 `SparkWriteOptions.BRANCH` 写选项，使读写路径在分支指定方式上完全对称。这是一次既改善一致性（读写规则统一）又改善可用性（WAP 表上也能向特定分支写、写支持 option 指定分支）的行为变更。
