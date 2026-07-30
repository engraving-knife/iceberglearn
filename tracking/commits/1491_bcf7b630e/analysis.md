# 提交 1491：Spark 3.5: Fix assertion mismatch in PartitionedWritesTestBase/TestRewritePositionDeleteFilesAction (#11748)

## 提交信息

- **序号**：1491 / 4088
- **哈希**：bcf7b630ef80c63d0836114576154f6fb0990ade
- **短哈希**：bcf7b630e
- **日期**：2024-12-15（Sun Dec 15 15:35:05 2024 +0800）
- **作者**：Liurnly <masterwangzx@gmail.com>
- **提交说明**：Spark 3.5: Fix assertion mismatch in PartitionedWritesTestBase/TestRewritePositionDeleteFilesAction (#11748)
- **PR/Issue**：#11748

## 总体目的

Iceberg 的 Spark 3.5 测试套件中，部分 AssertJ 断言的 `.as(...)` 描述文本与断言实际检查的内容不一致——通常是复制粘贴时遗留的错误描述。当这些断言失败时，开发者看到的失败信息会误导排查方向：描述说"应该有 5 行"但断言检查的是 3 行，描述说"插入后"但断言检查的是插入前状态，等等。

本提交修掉 4 处这类描述与实际不符的断言，使失败信息准确反映断言意图：

1. `PartitionedWritesTestBase.testInsertAppend` 第一个断言（插入前校验初始行数=3）的描述从 "Should have 5 rows after insert" 改为 "Rows before insert"。
2. `PartitionedWritesTestBase.testInsertOverwrite` 第一个断言（overwrite 前校验初始行数=3）的描述从 "Should have 5 rows after insert" 改为 "Rows before overwrite"。
3. `TestRewritePositionDeleteFilesAction` 一处断言（检查新 delete 文件数=2）的描述从 "Should have 4 delete files" 改为 "Delete files"。
4. `TestRewritePositionDeleteFilesAction` 另一处断言（检查新 delete 文件数=0）的描述从 "Should have 2 new delete files" 改为 "New delete files"。

这是纯测试可读性 / 可诊断性改进，不改变任何断言的实际检查值（`isEqualTo` / `hasSize` 的参数不变），也不改变被测产品代码。

## 如何达成设计目的

逐处把错误的 `.as("...")` 描述替换为准确或中性的描述。修改原则：
- 能准确描述时序 / 语义的，改成准确描述（如 "Rows before insert"）。
- 难以简短准确描述且核心信息已由 `hasSize(N)` / `isEqualTo(N)` 表达的，改成中性标签（如 "Delete files"），避免再写错数字。

不改动断言的实际期望值，仅改描述文本。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/PartitionedWritesTestBase.java`

**修改目的**：修正 `testInsertAppend` 与 `testInsertOverwrite` 中"插入前"断言的描述。

**工作逻辑**：

`testInsertAppend` 测试体结构：
```java
// 断言1：插入前，表有 3 行（初始数据 a/b/c）
assertThat(scalarSql("SELECT count(*) FROM %s", selectTarget()))
    .as("Rows before insert")    // 原为 "Should have 5 rows after insert"
    .isEqualTo(3L);

sql("INSERT INTO %s VALUES (4, 'd'), (5, 'e')", commitTarget());

// 断言2：插入后，表有 5 行（3 + 2）
assertThat(scalarSql("SELECT count(*) FROM %s", selectTarget()))
    .as("Should have 5 rows after insert")   // 这条本来就在，未改
    .isEqualTo(5L);
```

断言1 的旧描述 "Should have 5 rows after insert" 有三重错误：
- 时序错：它在 `INSERT` 之前执行，是"插入前"而非"插入后"；
- 数量错：期望值是 `3L` 而非 5；
- 复用错：明显是从断言2 复制过来时忘了改。

新描述 "Rows before insert" 准确反映"这是插入前的行数校验"，数量由 `isEqualTo(3L)` 自带。

`testInsertOverwrite` 同理：
```java
assertThat(scalarSql("SELECT count(*) FROM %s", selectTarget()))
    .as("Rows before overwrite")  // 原为 "Should have 5 rows after insert"
    .isEqualTo(3L);

sql("INSERT OVERWRITE %s VALUES (4, 'd'), (5, 'e')", commitTarget());
```

旧描述同样错（"after insert" 应为 "before overwrite"，5 应为 3）。新描述 "Rows before overwrite" 准确。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`

**修改目的**：修正两处 delete 文件数量断言的描述。

**工作逻辑**：

第一处（约 275 行）：
```java
List<DeleteFile> newDeleteFiles = except(deleteFiles(table), deleteFiles);
assertThat(newDeleteFiles).as("Delete files").hasSize(2);  // 原为 "Should have 4 delete files"
```
旧描述 "Should have 4 delete files" 与 `hasSize(2)` 矛盾（说 4 实际检查 2）。该测试场景：rewrite position deletes 后，过滤条件命中 c1=1 与 c1=2 两个分区，每分区重写产生 1 个新 delete 文件，共 2 个。新描述 "Delete files" 是中性标签，数量由 `hasSize(2)` 表达。

第二处（约 469 行）：
```java
List<DeleteFile> newDeleteFiles = except(deleteFiles(table), deleteFiles);
assertThat(newDeleteFiles).as("New delete files").hasSize(0);  // 原为 "Should have 2 new delete files"
```
旧描述 "Should have 2 new delete files" 与 `hasSize(0)` 矛盾（说 2 实际检查 0）。该测试场景：rewrite position deletes 过滤条件为 c1=0 或 c1=1，但这两个分区没有 position delete 文件（deletes 只在 c1=2、c1=3），故重写后不产生新 delete 文件，期望 0 个。新描述 "New delete files" 中性，数量由 `hasSize(0)` 表达。

## 小结

- **成效**：4 处 AssertJ 断言描述现与实际检查内容一致，断言失败时不再误导开发者（避免"描述说 5 行但断言检查 3 行"这类困惑）。测试可诊断性提升，维护成本降低。
- **影响范围**：2 个测试文件、4 行改动（每行只改字符串字面量）。零产品代码变更、零断言逻辑变更、零测试覆盖变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯测试描述修正，**可回迁可不回迁**：
    - 若 1.4.x 的这两个测试文件与 main 对应版本基本一致，cherry-pick 风险为零（只改字符串），**建议回迁**以保持测试描述准确。
    - 若 1.4.x 的测试文件已与 main 偏离较多（行号或上下文不同），可手工修改对应字符串，不一定要走 cherry-pick。
  - 不回迁也不影响 1.4.x 的任何功能或测试通过率——描述错误只影响失败时的诊断信息，不影响断言本身的正确性。
  - 注意只改 Spark 3.5 目录。若 1.4.x 还维护 Spark 3.3 / 3.4，且对应目录下有同样的描述错误，可同步修正（但本提交未涉及那些目录）。
