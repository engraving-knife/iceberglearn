# 提交 0186：Spark 3.3, 3.4: Backport fix for metadata delete condition check for branches (#9115)

## 提交信息

- **序号**：0186 / 4088
- **哈希**：7320899deab780597c101beddc8561adde7938f4
- **短哈希**：7320899de
- **日期**：2023-11-20 14:06:54 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.3, 3.4: Backport fix for metadata delete condition check for branches (#9115)
- **PR/Issue**：#9115

## 总体目的

本提交是对 main 分支已合入修复（针对 Spark 在 branch 场景下元数据删除条件检查）的回溯（backport），同时打平 Spark 3.3 与 Spark 3.4 两个维护分支。问题根因是 Iceberg 的 scan API 采用不可变（immutable）构建者模式：`TableScan.useRef(String branch)` 不会就地修改原 scan，而是返回一个绑定了 ref 的新 `TableScan` 实例。在 Spark 中两处对 `scan.useRef(branch)` 的调用没有接收返回值，等价于 no-op，导致即便指定了 branch，扫描仍然落在主表（main）上而非目标 branch。

受影响的两处都是 metadata delete 条件检查路径：

1. [`SparkTable.canDeleteUsingMetadata(Expression)`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java)：Spark V2 源在执行 `DELETE` 时，先用此方法判断是否能用元数据级删除（直接删除匹配的整数据文件，无需行级读写）。判断逻辑是扫描满足 `deleteExpr` 的文件，逐一用 `Evaluator`（分区级）与 `StrictMetricsEvaluator`（文件级 metrics）确认"所有匹配行都落在能整体删除的文件内"。当带 branch 删除时，本应扫描 branch 的快照文件，但因 bug 实际扫描主表，可能给出错误的 true/false 结论——要么错误地对 branch 不存在的文件做了 metadata delete 判断，要么错过 branch 中实际可整体删除的文件。
2. [`TestHelpers.dataFiles(Table, String)`](../../../spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java)：测试辅助方法，用于列举某个 branch 上的数据文件。同样因丢弃返回值导致列举的是主表文件，使测试断言错位。

回溯意义在于：1.4.x 维护分支需保持与 main 一致的正确性，而 branch 是 Iceberg 1.4.x 推荐的 WAP（Write-Audit-Publish）与隔离写入核心能力，metadata delete 路径的正确性直接影响带 branch 的 `DELETE` 语义与性能（错判会退化到行级 MOR 路径或误删）。

## 如何达成设计目的

整体设计是"修一处 bug + 强一处测试"：

1. 修复两处 `scan.useRef(branch)` 调用，改为 `scan = scan.useRef(branch)`，正确接收返回的新 scan。
2. 强化测试：把 `SparkRowLevelOperationsTestBase` 中一组测试参数的 branch 字段从 `null` 改为 `"test"`，使该参数组合真正进入 branch 路径；并把 `TestDelete.testDeleteFileThenMetadataDelete` 改为先 `createBranchIfNeeded()` 再向 `commitTarget()`（branch）插入数据，从而让"先写入再 metadata delete"的序列发生在 branch 上，使上述 bug 在测试中能被触发并验证修复。

改动同时打平 Spark 3.3 与 Spark 3.4 两套并行维护的源码树，确保两条分支行为一致。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` 与 `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java`

**修改目的**：修复 `canDeleteUsingMetadata` 中 `scan.useRef(branch)` 返回值被丢弃导致 branch 不生效的 bug。

**工作逻辑**：在 `canDeleteUsingMetadata(Expression deleteExpr)` 方法中，构建 `TableScan` 后原本是：

```java
if (branch != null) {
  scan.useRef(branch);
}
```

改为：

```java
if (branch != null) {
  scan = scan.useRef(branch);
}
```

`TableScan.useRef` 是不可变 API，返回一个绑定了指定 ref（branch 或 tag）的新 scan；不接收返回值时原 `scan` 仍指向主表快照，后续 `scan.planFiles()` 与 `StrictMetricsEvaluator` / `Evaluator` 评估都基于主表数据，使 metadata delete 条件判断与 branch 实际文件状态脱节。修复后 scan 真正指向 branch 快照，判断结果与 branch 一致。`StrictMetricsEvaluator` 使用的 `SnapshotUtil.schemaFor(table(), branch)` 本就按 branch 取 schema，修复后整个判断路径在 branch 上自洽。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java` 与 `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

**修改目的**：修复测试辅助方法 `dataFiles(Table table, String branch)` 中同样的返回值丢弃 bug。

**工作逻辑**：

```java
public static List<DataFile> dataFiles(Table table, String branch) {
  TableScan scan = table.newScan();
  if (branch != null) {
    scan = scan.useRef(branch);   // 原: scan.useRef(branch);
  }
  CloseableIterable<FileScanTask> tasks = scan.includeColumnStats().planFiles();
  ...
}
```

该方法被多个 branch 相关测试用于列举期望数据文件并断言。修复前，传入非 null branch 时实际列举的是主表文件，使断言基于错误的期望集合；修复后正确列举 branch 文件，测试才有意义。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java` 与 `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：将测试参数表中一组参数的 branch 字段从 `null` 改为 `"test"`，使该参数组合真正走 branch 路径，覆盖此前被忽略的分支。

**工作逻辑**：在参数化测试的 `parameters()` 中，对应 `parquet` + `testhadoop` 的一组参数，把倒数第二列的 `null` 改为 `"test"`。该字段最终决定测试基类是否 `createBranchIfNeeded()` 并以 branch 作为 `commitTarget()`。改为 `"test"` 后，所有继承该基类的测试（`TestDelete`、`TestUpdate`、`TestMerge` 等）在这组参数下都会创建 `test` branch 并对 branch 执行操作，从而把上述 `SparkTable` 与 `TestHelpers` 的 bug 纳入持续覆盖。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` 与 `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：调整 `testDeleteFileThenMetadataDelete` 的执行顺序，使"先写入、再 metadata delete"的序列发生在 branch 上，精准复现并验证修复。

**工作逻辑**：

原顺序：

```java
createAndInitUnpartitionedTable();
sql("INSERT INTO TABLE %s VALUES (1, 'hr'), (2, 'hardware'), (null, 'hr')", tableName);
createBranchIfNeeded();
sql("DELETE FROM %s AS t WHERE t.id IS NULL", commitTarget());
```

新顺序：

```java
createAndInitUnpartitionedTable();
createBranchIfNeeded();
sql("INSERT INTO TABLE %s VALUES (1, 'hr'), (2, 'hardware'), (null, 'hr')", commitTarget());
sql("DELETE FROM %s AS t WHERE t.id IS NULL", commitTarget());
```

两处关键变化：

1. `createBranchIfNeeded()` 提前到 INSERT 之前，确保 branch 在写入时已存在。
2. INSERT 目标从 `tableName`（主表）改为 `commitTarget()`（branch），使数据真正写入 branch。

如此，后续 `DELETE WHERE t.id IS NULL` 在 branch 上执行时，`canDeleteUsingMetadata` 必须扫描 branch 的文件才能正确判断。修复前 `scan.useRef(branch)` 是 no-op，扫描的是空的主表（数据在 branch 里），可能错误地判定可整体删除或不可删除；修复后扫描 branch 文件，判断与实际一致。该测试通过 `null` 不能被 metadata delete（需 MOR 写 delete file）这一既有断言，间接验证了 metadata delete 条件检查在 branch 上的正确性。

## 小结

通过修复 Spark 3.3/3.4 中 `scan.useRef(branch)` 返回值丢弃的 bug 并强化 branch 路径测试，确保带 branch 的 metadata delete 条件判断真正基于 branch 快照，维护了 1.4.x 上 branch 写入/删除语义的正确性。
