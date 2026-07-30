# 提交 0689：Core: Use 'delete' if RowDelta only has delete files

## 提交信息
- **序号**：0689 / 4088
- **哈希**：fc5b2b336c774b0b8b032f7d87a1fb21e76b3f20
- **短哈希**：fc5b2b336
- **日期**：2024-04-16 09:56:33 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use 'delete' if RowDelta only has delete files (#10123)
- **PR/Issue**：#10123

## 总体目的

本提交修复了 `BaseRowDelta` 在"只添加 delete 文件、不添加 data 文件"的提交场景下，快照操作类型被错误报告为 `overwrite` 的问题。

**Bug 成因**：

Iceberg 的 `Snapshot.operation()` 字段记录了产生该快照的数据操作类型，由 `DataOperations` 中定义的字符串常量表示：
- `append`：追加新数据，不删除任何数据（由 `AppendFiles` 实现）。
- `replace`：移除并替换文件，但不改变表中的数据内容（由 `RewriteFiles` 实现）。
- `overwrite`：新增数据并覆写已有数据（由 `OverwriteFiles`、`ReplacePartitions` 实现）。
- `delete`：从表中删除数据，不新增任何数据（由 `DeleteFiles` 实现）。

`RowDelta` 是一种合并快照操作，可以同时添加 data 文件和 delete 文件，常见于 MERGE-ON-READ 模式下的删除/更新场景。原来 `BaseRowDelta.operation()` 的实现是无条件返回 `DataOperations.OVERWRITE`：

```java
@Override
protected String operation() {
  return DataOperations.OVERWRITE;
}
```

问题在于：当一次 `RowDelta` 提交**只包含 delete 文件**（即仅添加位置删除/等值删除文件，没有新增 data 文件）时，操作语义上是一次纯删除，应该被分类为 `delete`，但实际被错误地报告为 `overwrite`。

**影响**：

1. **快照元数据语义错误**：`overwrite` 暗示"覆写已有数据"，但实际只是添加删除文件来标记要被过滤掉的行，没有任何数据被覆写。下游消费者（如增量读取器、监控告警、审计系统）基于 `operation` 字段判断操作类型时会误判。
2. **结构化流读取误判**：例如 `TestStructuredStreamingRead3` 中有测试明确依赖"RowDelta 写入 delete 文件后产生 OVERWRITE 快照"这一假设；这个假设本身就不正确，但用户也可能在自身代码中基于同样的假设做处理逻辑。
3. **审计与可观测性失真**：删除操作在审计上通常比 overwrite 更敏感，错误标记会降低可观测性工具的准确性。

**修复目标**：让 `RowDelta` 在"只添加 delete 文件、未添加 data 文件"时报告 `delete`，否则仍然报告 `overwrite`（因为同时添加 data 和 delete 文件确实更接近 overwrite 语义）。

## 如何达成设计目的

修复策略最小化侵入，仅在 `BaseRowDelta.operation()` 中添加一个条件分支：

```java
@Override
protected String operation() {
  if (addsDeleteFiles() && !addsDataFiles()) {
    return DataOperations.DELETE;
  }
  return DataOperations.OVERWRITE;
}
```

设计要点：

1. **复用父类已有的判定方法**：`addsDeleteFiles()` 和 `addsDataFiles()` 是 `MergingSnapshotProducer` 中已存在的方法，分别基于 `newDeleteFilesBySpec` 与 `newDataFiles` 是否非空来判断。无需在子类重复实现判定逻辑。
2. **保持向后兼容**：只有"只删不加"的纯删除场景才被重新分类为 `delete`；其它场景（同时加 data 和 delete、只加 data）仍然报告 `overwrite`，与原行为一致。
3. **覆盖测试矩阵**：
   - 在 `core` 层新增 `TestRowDelta.addOnlyDeleteFilesProducesDeleteOperation` 与 `TestCommitReporting` 修正断言；
   - 在 `spark/v3.3`、`v3.4`、`v3.5` 三个版本同步更新 `SparkRowLevelOperationsTestBase` 和 `TestDelete`，新增 `deleteSingleRecordProducesDeleteOperation` 端到端验证 SQL `DELETE FROM ... WHERE id = ?`（单条删除）在 MOR 模式下产生 `delete` 快照；
   - 修正 `TestStructuredStreamingRead3` 中依赖旧行为的测试：现在该测试在 `RowDelta` 上同时 `addRows` 和 `addDeletes`，使其仍然产生 `overwrite` 快照（保留原测试意图）。
4. **增强测试基础设施**：`validateProperty` 方法在 `expectedValue == null` 时改为断言"summary 中不包含该 key"，从而支持"某属性不应出现"的负向断言；同时 `validateSnapshot` 在 `addedDataFiles == null && addedDeleteFiles != null` 时选择 `DELETE` 作为期望操作类型，与生产代码逻辑保持一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java`

**修改目的**：让 `RowDelta` 在仅添加 delete 文件时报告 `delete` 操作类型。

**工作逻辑**：在 `operation()` 方法中新增条件判断：
- `addsDeleteFiles()` 返回 true（`newDeleteFilesBySpec` 非空）；
- `!addsDataFiles()` 返回 true（`newDataFiles` 为空）；
- 两者同时满足时返回 `DataOperations.DELETE`，否则保持 `OVERWRITE`。

这把"纯删除"与"覆写"两种语义在元数据层做了正确区分，使得下游基于 `operation` 字段做行为决策的组件能拿到更准确的信号。

### `core/src/test/java/org/apache/iceberg/TestCommitReporting.java`

**修改目的**：修正依赖旧行为的断言——纯删除提交的 `CommitReport.operation` 现在应该是 `delete` 而非 `overwrite`。

**工作逻辑**：把原本 `assertThat(report.operation()).isEqualTo("overwrite")` 改为 `isEqualTo("delete")`。该测试覆盖的正是"只 add delete 文件、不 add data 文件"的 RowDelta 提交场景，与新行为一致。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`

**修改目的**：新增针对 `BaseRowDelta` 行为变更的回归测试。

**工作逻辑**：新增 `addOnlyDeleteFilesProducesDeleteOperation`：
- 构造一个只添加 `FILE_A_DELETES` 和 `FILE_B_DELETES` 的 `RowDelta`；
- 提交后断言：
  - 快照序列号为 1；
  - 快照操作类型为 `DataOperations.DELETE`；
  - 快照中删除文件 manifest 数量为 1。

### `spark/v3.3|v3.4|v3.5/spark-extensions/.../SparkRowLevelOperationsTestBase.java`

**修改目的**：让验证测试基类自动根据"是否只添加 delete 文件"选择期望的 `operation`，并增强 `validateProperty` 支持负向断言。

**工作逻辑**：
1. `validateSnapshot(snapshot, changedPartitionCount, addedDeleteFiles, addedDataFiles)` 在调用底层 `validateSnapshot` 之前先动态决定 `operation`：
   ```java
   String operation = null == addedDataFiles && null != addedDeleteFiles ? DELETE : OVERWRITE;
   ```
   即"无 data 文件、有 delete 文件"时期望 `DELETE`，否则期望 `OVERWRITE`。
2. `validateProperty` 方法重写：
   - 原来：直接 `assertEquals(expectedValue, snapshot.summary().get(property))`，无法区分"值为 null"与"key 不存在"。
   - 修改后：当 `expectedValue == null` 时，断言 `summary` 中**不包含**该 key；否则断言包含该 key 且值匹配。这更符合 SQL 摘要的真实语义（某属性不存在 vs 显式 null）。

### `spark/v3.3|v3.4|v3.5/spark-extensions/.../TestDelete.java`

**修改目的**：新增端到端测试，验证 SQL 单条删除在 MOR 模式下产生 `delete` 操作类型的快照。

**工作逻辑**：新增 `deleteSingleRecordProducesDeleteOperation`：
1. 创建并初始化分区表；
2. append 三条记录 (id=1,2,3, dept=eng)；
3. 执行 `DELETE FROM %s WHERE id = 2`；
4. 断言：snapshots 大小为 2；
5. 分模式断言：
   - **COPY_ON_WRITE 模式**：删除通过 `OverwriteFiles` 实现（重写数据文件），仍然产生 `overwrite` 操作；调用 `validateCopyOnWrite(snapshot, "1", "1", "1")`。
   - **MERGE_ON_READ 模式**：删除通过 `RowDelta` 添加位置删除文件实现，新行为下产生 `delete` 操作；调用 `validateMergeOnRead(snapshot, "1", "1", null)` 并断言 `ADD_POS_DELETE_FILES_PROP == "1"`。
6. 最终验证数据只剩 (1, eng) 和 (3, eng)。

注意 v3.5 使用 `@TestTemplate` 而非 `@Test`（v3.5 测试基类使用 JUnit 5 参数化扩展）。

### `spark/v3.3|v3.4|v3.5/spark/.../TestStructuredStreamingRead3.java`

**修改目的**：修正原有依赖"RowDelta 只 addDeletes 会产生 OVERWRITE 快照"假设的测试，使其同时 add 一个 data 文件，从而仍然产生 OVERWRITE 快照（保留原测试意图）。

**工作逻辑**：原代码：
```java
table.newRowDelta().addDeletes(eqDeletes).commit();
```
修改后：
```java
DataFile dataFile = DataFiles.builder(table.spec())
    .withPath(...)  // 临时文件路径
    .withFileSizeInBytes(10)
    .withRecordCount(1)
    .withFormat(FileFormat.PARQUET)
    .build();
table.newRowDelta().addRows(dataFile).addDeletes(eqDeletes).commit();
```

这样该 RowDelta 同时添加了 data 和 delete 文件，会落入"非纯删除"分支，仍然返回 `OVERWRITE`，使得下游对该快照的 OVERWRITE 假设仍然成立。注释保留了"check pre-condition - that the above Delete file write - actually resulted in snapshot of type OVERWRITE"，强调了该测试的意图。

各 Spark 版本实现略有差异：
- v3.3、v3.4 用 `temp.newFile().toString()`；
- v3.5 用 `File.createTempFile("junit", null, temp.toFile()).getPath()`，与各自测试基础设施对齐。

## 小结
- **成效**：成功修正了 `BaseRowDelta` 在纯删除场景下操作类型的错误报告。改动仅 4 行生产代码，但配套的测试覆盖横跨 `core` 和 `spark/v3.3、v3.4、v3.5` 三套测试，确保该行为变更在多个 Spark 版本下都有回归保护。
- **影响范围**：
  - **生产代码**：仅 `core/src/main/java/org/apache/iceberg/BaseRowDelta.java`。
  - **下游影响**：所有基于 `Snapshot.operation()` 做决策的组件都会受到行为变更影响——结构化流读取、增量扫描、审计日志、监控告警等。对于依赖"RowDelta 总是产生 OVERWRITE"假设的下游代码，这是一个潜在的破坏性变更，需要相应适配。
  - **测试**：跨 3 个 Spark 版本同步更新，确保单条删除在 MOR 模式下产生 `delete` 快照。
- **回迁到 1.4.x 的注意事项**：
  1. **行为变更需要告知下游**：这是一个对快照元数据的语义性变更。回迁后，1.4.x 用户原有依赖"RowDelta → overwrite"假设的下游系统（如自研的增量同步、CDC 管道）可能需要适配。建议在 1.4.x release notes 中明确说明此变更。
  2. **测试同步回迁**：必须同步回迁 3 个 Spark 版本的测试更新，否则既有 `TestStructuredStreamingRead3` 等测试会因为期望 OVERWRITE 但实际拿到 DELETE 而失败。
  3. **依赖父类方法**：`addsDeleteFiles()` 和 `addsDataFiles()` 来自 `MergingSnapshotProducer`，需确认 1.4.x 中这两个方法已存在且语义一致；否则需要先回迁这两个父类方法。
  4. **`validateProperty` 增强同步**：测试基础设施层的 `validateProperty` 改动需要一并回迁，否则依赖"key 不存在"断言的新测试无法编译/运行。
  5. **CoW vs MoR 的差异**：CoW 模式下删除走的是 `OverwriteFiles` 路径，仍然产生 `overwrite`，与本次改动不冲突；测试中已分别覆盖两种模式，回迁时同样要保留这种区分。
