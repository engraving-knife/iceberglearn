# 提交 0183：Spark: Fix metadata delete check with branches (#9102)

## 提交信息

- **序号**：0183 / 4088
- **哈希**：c193de9e8f6ef8195506eeebe4a3721cf9884bd9
- **短哈希**：c193de9e8
- **日期**：2023-11-19 15:56:55 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Fix metadata delete check with branches (#9102)
- **PR/Issue**：#9102

## 总体目的

本提交修复了 Spark 3.5 集成中一个与分支（branch）相关的正确性缺陷：当对 Iceberg 表的某个分支执行 `DELETE FROM ... WHERE ...` 操作时，Spark 在判断该删除是否可以通过"元数据删除"（metadata delete）方式执行时，会错误地扫描主分支（main branch）的数据文件而非目标分支的数据文件，导致判断结果可能不正确。

在 Iceberg 中，"metadata delete"（元数据删除）是一种优化路径：当 `DELETE WHERE` 的谓词能够完全覆盖某些数据文件中的所有行时（即整个文件都可以被删除），引擎可以直接通过操作 manifest 来移除这些文件，而无需读取文件内容进行逐行删除。这种路径比逐行删除（copy-on-write 或 merge-on-read）高效得多。Spark 通过 `SparkTable.canDeleteWhere()` → `canDeleteUsingMetadata()` 这一路径来判断是否可以走元数据删除。

Iceberg 的分支（branch）机制允许在同一个表上创建独立的、可写的快照引用线（类似 Git 分支），不同分支可以拥有不同的快照和文件集合。当用户在分支上执行删除操作时，元数据删除的可行性判断必须基于该分支当前快照中的数据文件。然而，由于 `TableScan.useRef(branch)` 的返回值未被重新赋值（这是 builder 模式的典型陷阱），扫描实际上一直针对的是主分支，而非用户指定的分支。这会导致两种潜在的错误结果：
1. **误判可以元数据删除**：主分支的文件可以被谓词完全覆盖，但目标分支的文件不能——此时会错误地执行元数据删除，可能导致分支上不该被删除的数据被删除。
2. **误判不能元数据删除**：目标分支的文件可以被完全覆盖，但主分支的不能——此时会错误地降级为逐行删除，虽然结果正确但性能下降。

此外，测试辅助类 `TestHelpers.dataFiles(Table, String)` 中存在同样的 bug，导致测试中获取分支数据文件列表时实际获取的是主分支的文件，使测试无法有效暴露上述生产代码中的缺陷。本提交同时修复了生产代码和测试辅助代码，并调整了测试用例使其真正覆盖分支场景。

## 如何达成设计目的

整体设计分三个层面：
1. **修复生产代码**：在 `SparkTable.canDeleteUsingMetadata` 中，将 `scan.useRef(branch)` 改为 `scan = scan.useRef(branch)`，确保扫描器切换到目标分支。
2. **修复测试辅助代码**：在 `TestHelpers.dataFiles(Table, String)` 中做同样的修复，使测试中获取的数据文件确实来自目标分支。
3. **强化测试覆盖**：调整 `TestDelete.testDeleteFileThenMetadataDelete` 测试用例的数据准备顺序与目标，并将参数化测试矩阵中一个原本 `branch=null` 的用例改为 `branch="test"`，使该测试路径真正在分支上下文下运行，从而有效回归验证此修复。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java`

**修改目的**：修复 `canDeleteUsingMetadata` 方法在分支场景下扫描错误分支数据文件的缺陷。

**工作逻辑**：`canDeleteUsingMetadata(Expression deleteExpr)` 方法（[SparkTable.java:326](../../../spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java#L326)）负责判断一个删除谓词是否可以通过元数据删除执行。其逻辑是：先用 `ExpressionUtil.selectsPartitions` 检查谓词是否直接选择了分区（若是则可直接返回 `true`）；否则构建一个 `TableScan`，加上过滤条件、列统计信息，然后对每个 `FileScanTask` 使用 `StrictMetricsEvaluator` 评估该文件是否可以被整体删除。

关键问题在于分支扫描的设置。修复前的代码（[SparkTable.java:341-343](../../../spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java#L341)）为：
```java
if (branch != null) {
  scan.useRef(branch);
}
```
Iceberg 的 `TableScan` 遵循不可变 builder 模式，`useRef(branch)` 不会修改调用对象的内部状态，而是返回一个新的、绑定了指定分支引用的 `TableScan` 实例。原代码丢弃了返回值，因此 `scan` 变量仍指向未设置分支的扫描器，后续 `scan.planFiles()` 实际扫描的是表的主分支（main）快照，而非目标分支的快照。

修复后改为：
```java
if (branch != null) {
  scan = scan.useRef(branch);
}
```
将返回值重新赋给 `scan`，使后续的文件规划和元数据评估都在正确的分支上下文中进行。同时，方法后面使用的 `SnapshotUtil.schemaFor(table(), branch)` 已经正确传入了 branch 参数来获取分支对应的 schema，这也印证了 branch 信息本应贯穿整个判断流程，只是 scan 这一环遗漏了。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

**修改目的**：修复测试辅助方法 `dataFiles(Table, String)` 中相同的 `useRef` 返回值未赋值缺陷。

**工作逻辑**：`dataFiles(Table table, String branch)` 方法（[TestHelpers.java:841](../../../spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java#L841)）用于获取指定分支上的数据文件列表，在多个测试中被调用以验证删除操作后的文件状态。修复前的代码（[TestHelpers.java:843-845](../../../spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java#L843)）为：
```java
TableScan scan = table.newScan();
if (branch != null) {
  scan.useRef(branch);
}
```
与生产代码完全相同的 bug：`useRef` 返回值被丢弃。修复为 `scan = scan.useRef(branch);`。这意味着此前所有通过此方法获取分支数据文件的测试，实际获取的都是主分支的文件，使得测试在分支场景下无法正确验证生产代码的行为。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：调整 `testDeleteFileThenMetadataDelete` 测试用例，使数据真正写入分支而非主表，从而正确覆盖分支场景下的元数据删除路径。

**工作逻辑**：该测试验证"先写入删除文件（MOR 模式），再执行元数据删除"的复合场景。修复前的代码（[TestDelete.java:345-346](../../../spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java#L345)）为：
```java
sql("INSERT INTO TABLE %s VALUES (1, 'hr'), (2, 'hardware'), (null, 'hr')", tableName);
createBranchIfNeeded();
```
问题在于：先向主表（`tableName`）插入数据，再创建分支。此时分支继承主表当前快照，虽然能看到数据，但数据实际提交在主表上。后续 `DELETE` 操作通过 `commitTarget()` 定向到分支，但初始数据并不在分支的提交历史中。

修复后调整为：
```java
createBranchIfNeeded();
sql("INSERT INTO TABLE %s VALUES (1, 'hr'), (2, 'hardware'), (null, 'hr')", commitTarget());
```
先创建分支（此时分支与主表快照一致，为空表），再通过 `commitTarget()` 将数据直接插入到分支上。`commitTarget()` 在设置了 branch 时返回分支限定的表名（如 `table.branch_test`），使提交发生在分支上下文中。这样整个测试的数据写入和删除都在分支上进行，真正覆盖了 `canDeleteUsingMetadata` 在分支场景下的行为——这正是此前被 `useRef` bug 掩盖的场景。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：将参数化测试矩阵中一个原本不使用分支（`branch=null`）的用例改为使用分支（`branch="test"`），使更多测试路径覆盖分支场景。

**工作逻辑**：该类的 `parameters()` 方法返回一个二维数组，定义了测试矩阵的参数组合（catalog 名、实现类、配置、文件格式、是否向量化、分布模式、是否 fanout、分支名、规划模式）。修复前，第二个参数组合中 branch 参数为 `null`（[SparkRowLevelOperationsTestBase.java:147](../../../spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java#L147)），即该组测试不涉及分支。修复后改为 `"test"`，使该参数组合下所有继承 `SparkRowLevelOperationsTestBase` 的测试类（包括 `TestDelete`、`TestUpdate`、`TestMerge` 等）都会在分支上下文中运行，从而扩大了分支场景的回归覆盖面，有效防止类似 `useRef` 返回值遗漏的缺陷再次引入而不被发现。

## 小结

本提交修复了一个影响分支场景下元数据删除判断正确性的关键缺陷——`TableScan.useRef()` 返回值未赋值导致扫描错误分支，同时修复了测试辅助类中相同的 bug 并强化了分支场景的测试覆盖，确保 Iceberg 分支功能在 Spark 行级操作中的正确性。
