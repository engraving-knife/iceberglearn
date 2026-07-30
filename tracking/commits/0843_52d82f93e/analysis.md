# 提交 0843：API, Spark 3.3: Remove all usages of deprecated AssertHelpers (#10500)

## 提交信息
- **序号**：0843 / 4088
- **哈希**：52d82f93efe77a33ff6c898126292aadcce65916
- **短哈希**：52d82f93e
- **日期**：2024-06-17
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：API, Spark 3.3: Remove all usages of deprecated AssertHelpers (#10500)
- **PR/Issue**：#10500

## 总体目的

本提交是 Iceberg 测试基础设施的一次大规模清理，目的是彻底移除已被标记为 `@Deprecated` 的测试辅助类 `AssertHelpers` 及其在 Spark 3.3 模块测试中的所有使用点，改为直接使用 AssertJ 原生的 `Assertions.assertThatThrownBy(...)` 流式断言 API。

`AssertHelpers` 是 Iceberg 早期为简化异常断言而封装的工具类，提供了 `assertThrows`、`assertThrowsCause`、`assertThrowsRootCause`、`assertThrowsWithCause`、`assertEmptyAvroField` 等静态方法。这些方法接收消息、预期异常类、预期消息片段、可调用对象等参数，内部委托 AssertJ 完成断言。随着 AssertJ 自身 API 的成熟，`Assertions.assertThatThrownBy(...)` 已经提供了更流畅的链式断言能力（`.isInstanceOf(...).hasMessageContaining(...).cause().isInstanceOf(...)` 等），`AssertHelpers` 的封装反而成为多余且不够灵活的中间层。该类已被标记 `@Deprecated`，类注释明确推荐"Use `Assertions#assertThatThrownBy(ThrowableAssert.ThrowingCallable)` directly"。

本提交执行了该废弃指引：删除 `AssertHelpers` 类本身，并将 Spark 3.3 模块下所有测试文件中对 `AssertHelpers` 的调用机械式改写为等价的 AssertJ 链式断言。这是继之前提交将 `AssertHelpers` 标记为 deprecated 之后的"最终清除"步骤。

## 如何达成设计目的

提交通过"机械式等价改写"达成目的，针对 `AssertHelpers` 的每种方法采用对应的 AssertJ 链式断言模式：

1. **`AssertHelpers.assertThrows(message, ExpectedException.class, containedInMessage, callable)` → AssertJ 流式断言**：
   改写为
   ```java
   Assertions.assertThatThrownBy(callable)
       .as(message)
       .isInstanceOf(ExpectedException.class)
       .hasMessageContaining(containedInMessage);
   ```
   其中 `.as(message)` 设置断言描述（对应原 `message` 参数），`.isInstanceOf(...)` 对应预期异常类，`.hasMessageContaining(...)` 对应消息片段。若原调用未传 `containedInMessage`（null），改写时省略 `.hasMessageContaining` 行。

2. **`AssertHelpers.assertThrowsCause(message, CauseClass.class, containedInMessage, runnable)` → AssertJ `.cause()` 链**：
   改写为
   ```java
   Assertions.assertThatThrownBy(runnable)
       .as(message)
       .cause()
       .isInstanceOf(CauseClass.class)
       .hasMessageContaining(containedInMessage);
   ```
   关键差异：原 `assertThrowsCause` 内部使用 `.getCause()`，AssertJ 对应方法为 `.cause()`，语义等价。

3. **`AssertHelpers.assertThrowsWithCause(message, ExpectedClass.class, expectedMsg, CauseClass.class, causeMsg, runnable)` → AssertJ 链式 cause 断言**：
   改写为
   ```java
   Assertions.assertThatThrownBy(runnable)
       .as(message)
       .isInstanceOf(ExpectedClass.class)
       .hasMessageContaining(expectedMsg)
       .cause()
       .isInstanceOf(CauseClass.class)
       .hasMessageContaining(causeMsg);
   ```
   原方法内部对 null 消息片段做了条件跳过，改写时若原参数为 null 则省略对应 `.hasMessageContaining` 行。

4. **`AssertHelpers.assertThrowsRootCause(...)` → AssertJ `.rootCause()` 链**（本提交中该方法仅出现在 `AssertHelpers` 类定义内部，调用点改写为多次 `.cause()` 链式调用以导航到根因，或直接使用 AssertJ 的 `.rootCause()`，具体取决于上下文）。

5. **import 调整**：每个测试文件移除 `import org.apache.iceberg.AssertHelpers;`，新增 `import org.assertj.core.api.Assertions;`（若文件原先未导入 AssertJ）。

6. **删除 `AssertHelpers` 类**：删除 `api/src/test/java/org/apache/iceberg/AssertHelpers.java` 整个文件（213 行），并从 `LICENSE` 中移除对该文件来自 Apache Parquet 的引用条目 `* AssertHelpers.java`（因为该文件本身被删除，相关衍生代码声明不再需要）。

提交涉及 60 个文件、+2317 / -2509 行，其中绝大部分是测试文件中的机械式改写，`TestMerge.java`、`TestRewriteDataFilesProcedure.java`、`TestConflictValidation.java`、`TestSparkBucketFunction.java`、`TestSparkTruncateFunction.java` 等文件改动量较大（因为这些测试大量使用异常断言）。

## 修改详情

### `api/src/test/java/org/apache/iceberg/AssertHelpers.java`
**修改目的**：删除整个已废弃的 `AssertHelpers` 测试辅助类。
**工作逻辑**：该文件包含 213 行，定义了 `AssertHelpers` 工具类及其全部 `@Deprecated` 静态方法（`assertThrows` 多个重载、`assertThrowsCause`、`assertThrowsWithCause`、`assertThrowsRootCause`、`assertEmptyAvroField`）。所有方法内部都委托 `Assertions.assertThatThrownBy(...)` 完成断言，只是参数风格不同。删除该文件后，所有调用点必须改用 AssertJ 原生 API。

### `LICENSE`
**修改目的**：移除对已删除的 `AssertHelpers.java` 的衍生代码引用。
**工作逻辑**：`LICENSE` 文件原先在 "This product includes code from Apache Parquet" 段落下列出了 `* AssertHelpers.java`（表明该文件衍生自 Apache Parquet），删除该文件后相应移除该行。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`
**修改目的**：将 `AssertHelpers.assertThrows` 调用改写为 AssertJ 流式断言。
**工作逻辑**：例如原
```java
AssertHelpers.assertThrows(
    "Should forbid adding of partitioned data to unpartitioned table",
    IllegalArgumentException.class,
    "Cannot use partition filter with an unpartitioned table",
    () -> scalarSql(...));
```
改写为
```java
Assertions.assertThatThrownBy(() -> scalarSql(...))
    .as("Should forbid adding of partitioned data to unpartitioned table")
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Cannot use partition filter with an unpartitioned table");
```
保留全部断言语义（描述、异常类型、消息片段），仅改变调用风格。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`
**修改目的**：将 `AssertHelpers.assertThrowsCause` 调用改写为 AssertJ `.cause()` 链式断言。
**工作逻辑**：例如原
```java
AssertHelpers.assertThrowsCause(
    "Should complain about multiple matches",
    SparkException.class,
    errorMsg,
    () -> { sql("MERGE INTO ..."); });
```
改写为
```java
Assertions.assertThatThrownBy(() -> { sql("MERGE INTO ..."); })
    .as("Should complain about multiple matches")
    .cause()
    .isInstanceOf(SparkException.class)
    .hasMessageContaining(errorMsg);
```
关键差异是 `.cause()` 替代原方法内部的 `.getCause()`，语义等价。`TestMerge.java` 改动量大（+/- 约 958 行）是因为该测试大量使用 `assertThrowsCause` 验证 MERGE 语句的多匹配错误场景。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestConflictValidation.java`
**修改目的**：将 `assertThrowsCause` 改写为 AssertJ `.cause()` 链。
**工作逻辑**：与 `TestMerge.java` 同模式，验证冲突检测抛出的 `ValidationException` 作为 cause 包装在 `RuntimeException` 中。改动量较大（+/- 约 291 行）因多个隔离级别场景都使用此断言模式。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestWriteAborts.java`
**修改目的**：将 `assertThrowsWithCause` 改写为 AssertJ `.cause()` 链。
**工作逻辑**：原
```java
AssertHelpers.assertThrowsWithCause(
    "Should throw a Commit State Unknown Exception",
    SparkException.class, "Writing job aborted",
    CommitStateUnknownException.class, "Datacenter on Fire",
    () -> sql("DELETE FROM %s WHERE id = 2", "dummy_catalog.default.table"));
```
改写为
```java
Assertions.assertThatThrownBy(() -> sql(...))
    .as("Should throw a Commit State Unknown Exception")
    .isInstanceOf(SparkException.class)
    .hasMessageContaining("Writing job aborted")
    .cause()
    .isInstanceOf(CommitStateUnknownException.class)
    .hasMessageContaining("Datacenter on Fire");
```
原方法对 null 消息片段的条件跳过逻辑，改写时通过省略对应 `.hasMessageContaining` 行实现等价。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`
**修改目的**：将 `assertThrows` 改写为 AssertJ 流式断言，部分场景改用 `.hasMessage(...)` 精确匹配。
**工作逻辑**：例如原 `AssertHelpers.assertThrows("Should fail entire rewrite if part fails", RuntimeException.class, () -> spyRewrite.execute())` 改写为 `Assertions.assertThatThrownBy(() -> spyRewrite.execute()).as("Should fail entire rewrite if part fails").isInstanceOf(RuntimeException.class).hasMessage("Rewrite Failed")`。注意这里改用 `.hasMessage(...)` 精确匹配而非 `.hasMessageContaining(...)`，属于改写时对断言强度的细微调整。

### 其余 53 个测试文件
**修改目的**：统一将 `AssertHelpers.*` 调用改写为等价的 AssertJ 流式断言。
**工作逻辑**：遵循上述 4 种改写模式（`assertThrows` → `.isInstanceOf().hasMessageContaining()`；`assertThrowsCause` → `.cause().isInstanceOf().hasMessageContaining()`；`assertThrowsWithCause` → 链式 `.cause()`；`assertThrowsRootCause` → `.rootCause()` 或多次 `.cause()` 链）。每个文件同步移除 `import org.apache.iceberg.AssertHelpers;`、新增 `import org.assertj.core.api.Assertions;`。涉及文件包括 `TestAlterTableSchema`、`TestAncestorsOfProcedure`、`TestBranchDDL`、`TestCallStatementParser`、`TestChangelogTable`、`TestCherrypickSnapshotProcedure`、`TestDelete`、`TestExpireSnapshotsProcedure`、`TestMigrateTableProcedure`、`TestPublishChangesProcedure`、`TestRemoveOrphanFilesProcedure`、`TestReplaceBranch`、`TestRequiredDistributionAndOrdering`、`TestRewriteDataFilesProcedure`、`TestRewriteManifestsProcedure`、`TestRollbackToSnapshotProcedure`、`TestRollbackToTimestampProcedure`、`TestSetCurrentSnapshotProcedure`、`TestSnapshotTableProcedure`、`TestTagDDL`、`TestUpdate`、`TestFunctionCatalog`、`TestDeleteReachableFilesAction`、`TestExpireSnapshotsAction`、`TestRemoveOrphanFilesAction`、`TestRewriteManifestsAction`、`TestParquetVectorizedReads`、`TestDataFrameWriterV2`、`TestDataSourceOptions`、`TestForwardCompatibility`、`TestIcebergSourceTablesBase`、`TestMetadataTablesWithPartitionEvolution`、`TestRequiredDistributionAndOrdering`（source 包）、`TestSparkDataWrite`、`TestSparkMetadataColumns`、`TestStructuredStreamingRead3`、`TestTimestampWithoutZone`、`TestWriteMetricsConfig`、`TestAlterTable`、`TestCreateTable`、`TestDeleteFrom`、`TestDropTable`、`TestNamespaceSQL`、`TestSelect`、`TestSparkBucketFunction`、`TestSparkDaysFunction`、`TestSparkHoursFunction`、`TestSparkMonthsFunction`、`TestSparkTruncateFunction`、`TestSparkYearsFunction`、`TestTimestampWithoutZone`（sql 包）、`UnpartitionedWritesTestBase` 等。

## 小结
- **成效**：彻底移除已废弃的 `AssertHelpers` 测试辅助类及其在 Spark 3.3 模块全部 59 个测试文件中的使用点，统一为 AssertJ 原生流式断言 API，消除了 deprecated API 的技术债，简化了测试基础设施。改写为等价变换，断言语义（异常类型、消息片段、cause 链）保持一致，仅在个别场景（如 `TestRewriteDataFilesAction`）将 `hasMessageContaining` 调整为 `hasMessage` 精确匹配。
- **影响范围**：仅影响测试代码和 `LICENSE` 文件，不触及任何产品代码。范围限于 `api` 模块（删除 `AssertHelpers` 类）和 `spark/v3.3` 模块（59 个测试文件改写）。注意：本提交只清理 Spark 3.3 模块；Spark 3.4 / 3.5 及其它模块（flink、core、aws 等）若仍引用 `AssertHelpers`，需在各自的对应提交中清理（`AssertHelpers` 类被删除后，其它模块若仍引用将编译失败，因此 main 分支上应有配套提交清理所有模块）。
- **回迁注意事项**：回迁到 1.4.x 时需注意：(1) 1.4.x 分支若仍保留 `AssertHelpers` 类且其它模块（flink、core、aws、spark 3.4/3.5）仍引用它，则不能仅 cherry-pick 本提交（删除 `AssertHelpers` 类）否则会破坏其它模块编译；需确认 1.4.x 上是否有配套的"全模块清理"提交集合。(2) 本提交涉及 60 个文件的大规模机械改写，cherry-pick 时若 1.4.x 上任一测试文件有本地修改可能产生冲突，需逐文件解决。(3) 改写时部分场景由 `hasMessageContaining` 改为 `hasMessage`，若 1.4.x 上错误消息文本有细微差异（如包含动态内容），可能需要回退为 `hasMessageContaining` 以避免测试脆弱性。(4) 由于改动量大，建议优先评估 1.4.x 是否真的需要此清理提交，若仅为测试基础设施清理且无功能影响，可考虑跳过以降低回迁成本。
