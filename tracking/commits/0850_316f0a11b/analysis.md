# 提交 0850：Spark: Import Assertions statically (#10531)

## 提交信息
- **序号**：0850 / 4088
- **哈希**：316f0a11bf5aa3d24cf7cb0c649960e42c312b36
- **短哈希**：316f0a11b
- **日期**：2024-06-18
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Spark: Import Assertions statically (#10531)
- **PR/Issue**：#10531

## 总体目的

本提交对 Iceberg Spark 模块的全部测试源码（及少量辅助测试基类）进行代码风格统一：将 AssertJ 的 `Assertions` 类从普通 `import` 改为静态导入（`import static org.assertj.core.api.Assertions.assertThat;` 等），并把方法调用处的 `Assertions.assertThat(...)` / `Assertions.assertThatThrownBy(...)` 简化为 `assertThat(...)` / `assertThatThrownBy(...)`。

这是与提交 0849（Flink: Import Assertions statically）配对的姊妹提交，针对 Spark 模块做完全相同的重构。设计目的一致：

1. **简化测试代码**：去掉重复的 `Assertions.` 前缀，断言语句更接近自然语言。
2. **与社区主流风格对齐**：AssertJ 官方与 Iceberg core 模块早已采用静态导入风格，本次把 Spark 测试代码统一过来。
3. **便于后续使用更多 AssertJ 静态方法**：如 `assertThatThrownBy`、`assertThatExceptionOfType`、`assertThatCode` 等。

由于 Spark 模块同时维护 v3.3、v3.4、v3.5 多个版本子目录，且 Spark 测试类数量远多于 Flink（涉及 extensions、source、sql、actions、data 等多个子包），本次改动文件数高达 223 个，但每个文件的修改模式完全一致。除测试类外，还包含少量被测试代码引用的辅助类（如 `ValidationHelpers`、`GenericsHelpers`、`TestHelpers`、`SmokeTest`、`SparkTestHelperBase`）。

## 如何达成设计目的

每个文件的修改遵循固定模式：

1. 在 `package` 声明后新增静态导入：
   ```java
   import static org.assertj.core.api.Assertions.assertThat;
   ```
   若用到 `assertThatThrownBy`，则再新增：
   ```java
   import static org.assertj.core.api.Assertions.assertThatThrownBy;
   ```

2. 删除原来的非静态导入：
   ```java
   // 删除：import org.assertj.core.api.Assertions;
   ```

3. 把方法体内所有 `Assertions.assertThat(...)` → `assertThat(...)`；`Assertions.assertThatThrownBy(...)` → `assertThatThrownBy(...)`。

对于 `TestHelpers`、`GenericsHelpers`、`SparkTestHelperBase` 这类包含大量断言的工具类，去掉前缀后链式调用可压缩到更少的行数，可读性显著提升。

由于 Spark 各版本（v3.3 / v3.4 / v3.5）下的同名测试类是独立副本，每个版本都被同步修改，保证风格一致，避免后续 cherry-pick 或合并时产生冲突。同时 v3.5 较 v3.3/v3.4 多了一些新增测试类（如 `TestSpark3Util`、`TestSparkV2Filters`、`TestSparkReadMetrics`、`TestSparkScan`、`TestCompressionSettings`、`TestSparkFunctions`、`TestCreateTableAsSelect`、`TestStoragePartitionedJoinsInRowLevelOperations`、`TestSystemFunctionPushDownDQL`、`SmokeTest` 等），也都被纳入本次重构。

## 修改详情

下面列出有代表性的文件级修改。其余文件遵循完全相同的模式。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`
**修改目的**：引入 `assertThatThrownBy` 静态导入，替换 4 处 `Assertions.assertThatThrownBy(...)` 调用。

**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assertions.assertThatThrownBy;`（`assertThat` 静态导入已存在）。
- 删除 `import org.assertj.core.api.Assertions;`。
- 4 处调用形如：
  ```java
  Assertions.assertThatThrownBy(
          () -> scalarSql("CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))", ...))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageStartingWith("Cannot use partition filter with an unpartitioned table");
  ```
  改为：
  ```java
  assertThatThrownBy(
          () -> scalarSql("CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))", ...))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageStartingWith("Cannot use partition filter with an unpartitioned table");
  ```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkTestHelperBase.java`
**修改目的**：把测试基类中的 `Assertions.assertThat(...)` 改为静态导入的 `assertThat(...)`，并利用简化后的写法压缩链式调用。

**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`，删除 `import org.assertj.core.api.Assertions;`。
- `assertEquals(String, List<Object[]>, List<Object[]>)` 与 `assertEquals(String, Object[], Object[])` 两个方法内的多处 `Assertions.assertThat(...)` 全部改为 `assertThat(...)`。
- 由于去掉了前缀，原来因行长被拆成多行的链式调用被合并：
  ```java
  // 原
  Assertions.assertThat(actualRow)
      .as("Number of columns should match")
      .hasSameSizeAs(expectedRow);
  // 新
  assertThat(actualRow).as("Number of columns should match").hasSameSizeAs(expectedRow);
  ```
  ```java
  // 原
  Assertions.assertThat(actualValue)
      .as(context + " contents should match")
      .isEqualTo(expectedValue);
  // 新
  assertThat(actualValue).as(context + " contents should match").isEqualTo(expectedValue);
  ```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`、`GenericsHelpers.java`
**修改目的**：把 Spark 数据测试辅助类中大量的 `Assertions.assertThat(...)` 调用改为静态导入。

**工作逻辑**：这两个文件包含大量字段值比较、行比较的断言逻辑，改动后行数显著减少（`TestHelpers.java` 从约 114 行删减到更少，`GenericsHelpers.java` 从约 104 行删减）。所有断言行为不变。

### 其余 220 个测试文件
**修改目的**：与上述文件完全一致——引入 `assertThat`（必要时引入 `assertThatThrownBy`）静态导入，删除 `Assertions` 普通导入，并把方法调用处的 `Assertions.` 前缀去掉。

**工作逻辑**：覆盖 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 下的测试类与少量辅助类，按子包归类包括：
- **extensions**：`TestAddFilesProcedure`、`TestAlterTablePartitionFields`、`TestAlterTableSchema`、`TestAncestorsOfProcedure`、`TestBranchDDL`、`TestCallStatementParser`、`TestChangelogTable`、`TestCherrypickSnapshotProcedure`、`TestConflictValidation`、`TestCopyOnWriteDelete/Merge/Update`、`TestDelete`、`TestExpireSnapshotsProcedure`、`TestMerge`、`TestMergeOnReadDelete`、`TestMetaColumnProjectionWithStageScan`、`TestMigrateTableProcedure`、`TestPublishChangesProcedure`、`TestRemoveOrphanFilesProcedure`、`TestReplaceBranch`、`TestRequiredDistributionAndOrdering`、`TestRewriteDataFilesProcedure`、`TestRewriteManifestsProcedure`、`TestRewritePositionDeleteFilesProcedure`、`TestRollbackToSnapshotProcedure`、`TestRollbackToTimestampProcedure`、`TestSetCurrentSnapshotProcedure`、`TestSetWriteDistributionAndOrdering`、`TestSnapshotTableProcedure`、`TestStoragePartitionedJoinsInRowLevelOperations`（v3.5）、`TestSystemFunctionPushDownDQL`（v3.5）、`TestTagDDL`、`TestUpdate`、`TestWriteAborts`。
- **actions**：`TestDeleteReachableFilesAction`、`TestExpireSnapshotsAction`、`TestRemoveOrphanFilesAction`、`TestRewriteDataFilesAction`、`TestRewriteManifestsAction`、`TestSparkFileRewriter`。
- **source**：`TestDataFrameWriterV2`、`TestDataFrameWrites`、`TestDataSourceOptions`、`TestFilteredScan`、`TestForwardCompatibility`、`TestIcebergSourceTablesBase`、`TestIcebergSpark`、`TestMetadataTablesWithPartitionEvolution`、`TestPathIdentifier`、`TestReadProjection`、`TestRequiredDistributionAndOrdering`、`TestSnapshotSelection`、`TestSparkCatalogCacheExpiration`、`TestSparkDataWrite`、`TestSparkMetadataColumns`、`TestStructuredStreaming`、`TestStructuredStreamingRead3`、`TestTimestampWithoutZone`、`TestWriteMetricsConfig`、`TestCompressionSettings`（v3.5）、`TestSparkReadMetrics`（v3.5）、`TestSparkScan`（v3.5）。
- **sql**：`TestAggregatePushDown`、`TestAlterTable`、`TestCreateTable`、`TestCreateTableAsSelect`（v3.5）、`TestDeleteFrom`、`TestDropTable`、`TestFilterPushDown`、`TestNamespaceSQL`、`TestPartitionedWritesToWapBranch`、`TestSelect`、`TestSparkBucketFunction`、`TestSparkDaysFunction`、`TestSparkHoursFunction`、`TestSparkMonthsFunction`、`TestSparkTruncateFunction`、`TestSparkYearsFunction`、`TestTimestampWithoutZone`、`TestUnpartitionedWritesToBranch`、`UnpartitionedWritesTestBase`。
- **data**：`GenericsHelpers`、`TestHelpers`、`TestSparkParquetReader`、`vectorized/TestParquetVectorizedReads`。
- **其他**：`TestDataFileSerialization`、`TestManifestFileSerialization`、`TestScanTaskSerialization`、`ValidationHelpers`、`TestFunctionCatalog`、`TestSpark3Util`（v3.5）、`TestSparkTableUtil`、`TestSparkV2Filters`（v3.5）、`SmokeTest`（v3.5）、`SparkTestHelperBase`（v3.5）、`TestSparkFunctions`（v3.5）。

每个文件的修改都是机械替换，无任何逻辑变化。

## 小结
- **成效**：将 Spark 模块全部 223 个测试文件（含少量辅助类）的 AssertJ 调用统一为静态导入风格，代码更简洁，与 Iceberg core 模块及提交 0849（Flink）保持一致；同时利用简化后的写法压缩了 `SparkTestHelperBase`、`TestHelpers`、`GenericsHelpers` 等高密度断言文件的行数。
- **影响范围**：仅影响 Spark 测试源码（`spark/v3.3`~`spark/v3.5` 下的 `src/test/java` 及少量被测试引用的辅助类如 `ValidationHelpers`、`SmokeTest`、`SparkTestHelperBase`），不涉及任何主代码、构建脚本或运行时行为；测试逻辑零变化。
- **回迁注意事项**：可直接回迁到 1.4.x，但需注意 1.4.x 支持的 Spark 版本目录与 main 分支可能不同（1.4.x 可能只支持 Spark 3.3/3.4/3.5 中的子集，或尚有 v3.2）。回迁时应只对 1.4.x 实际存在的 Spark 版本目录做同样修改；若 1.4.x 已有部分文件采用静态导入风格，只需补齐剩余文件。建议借助 IDE 的 "Organize Imports" 批量处理，并运行一次完整 Spark 测试套件确认编译与断言行为无回归。注意 v3.5 下部分文件（如 `TestSpark3Util`、`TestSparkV2Filters`、`SmokeTest`、`TestSparkReadMetrics`、`TestSparkScan` 等）在 1.4.x 中可能尚未存在，无需处理。
