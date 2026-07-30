# 提交 0714：OverwriteFiles 仅删除/仅追加时使用 'delete' / 'append' 操作类型

## 提交信息
- **序号**：0714 / 4088
- **哈希**：f460964e78dbbfbe81fd093d436dca80c72e7124
- **短哈希**：f460964e7
- **日期**：2024-04-25
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use 'delete' / 'append' if OverwriteFiles only deletes/appends data files (#10150)
- **PR/Issue**：#10150

## 总体目的

本提交优化 `OverwriteFiles` 操作在快照（Snapshot）中记录的 `operation` 类型。在修改前，无论 `OverwriteFiles` 是只删数据文件、只加数据文件、还是既删又加，提交后生成的新快照的 `operation` 字段一律记为 `OVERWRITE`。修改后，会根据实际的数据文件增删情况，更精确地记录为 `DELETE`、`APPEND` 或 `OVERWRITE` 三者之一。

Iceberg 的每个快照都带有一个 `operation` 字段（取值定义在 `DataOperations` 中：`APPEND`、`OVERWRITE`、`DELETE`、`REPLACE`），用于描述该快照对表数据做了什么类型的变更。这个字段非常重要，下游消费者会据此优化行为，其中最典型的就是 Spark Structured Streaming 的读流逻辑：

- Spark Structured Streaming 在读取 Iceberg 表时，会根据快照的 `operation` 类型决定如何处理该快照产生的数据变更。常见配置项 `streaming.skip-overwrite-snapshots` 与 `streaming.skip-delete-snapshots` 允许流作业跳过 OVERWRITE 或 DELETE 类型的快照。当用户只想消费增量追加数据（APPEND）时，可同时跳过 OVERWRITE 和 DELETE。

问题在于：当一个 `OverwriteFiles` 操作实际上**只删除了数据文件**（例如通过 `overwriteByRowFilter` 删掉一批匹配的文件，而未追加任何新文件），它本质等价于一个 `DeleteFiles` 操作，但快照的 `operation` 却被记成了 `OVERWRITE`。这会导致：
- 设置了 `streaming.skip-delete-snapshots=true` 但 `streaming.skip-overwrite-snapshots=false` 的流作业，本意是"跳过删除类快照、消费覆写类快照"，却会错误地处理这个"名为覆写、实为删除"的快照，把删除的数据当成需要消费的增量，造成语义错误。
- 反之，设置了 `streaming.skip-overwrite-snapshots=true` 的流作业，会跳过这个实际只是删除操作的快照，可能错过本应处理的内容。

类似地，当一个 `OverwriteFiles` 操作**只追加数据文件**（调用 `addFile` 而不调用任何 `deleteFile` 或 `overwriteByRowFilter`），它本质是一个 `AppendFiles` 操作，但被记成 `OVERWRITE`，导致流作业无法用 `skip-overwrite-snapshots` 正确过滤。

本提交通过让 `operation` 更精确地反映实际增删语义，使下游消费者（尤其是 Structured Streaming）能够基于快照类型做出正确的增量处理决策。

## 如何达成设计目的

### 核心思路

修改 `BaseOverwriteFiles.operation()` 方法，在返回 `OVERWRITE` 之前先判断两种"退化"情形：

1. **只删不加**：若本次操作删除了数据文件但没有追加任何数据文件 → 返回 `DataOperations.DELETE`
2. **只加不删**：若本次操作追加了数据文件但没有删除任何数据文件 → 返回 `DataOperations.APPEND`
3. **既删又加**：保持返回 `DataOperations.OVERWRITE`（真正的覆写语义）

### 关键方法依赖

判断所用的两个方法来自父类 `MergingSnapshotProducer`：

- `deletesDataFiles()`：返回 `filterManager.containsDeletes()`。`filterManager` 是 `DataFileFilterManager`（`ManifestFilterManager<DataFile>` 的子类），负责管理数据文件的删除。当通过 `deleteFile(file)`（直接删除指定文件）或 `deleteByRowFilter(expr)`（按行过滤表达式删除匹配文件）调用时，`filterManager` 会被置为"含有删除"状态。因此 `deletesDataFiles()` 对两种删除方式都返回 `true`。
- `addsDataFiles()`：返回 `newDataFiles.size() > 0`。`newDataFiles` 列表在调用 `add(file)`（被 `addFile(file)` 调用）时追加元素。因此只要有 `addFile` 调用就返回 `true`。

注意：这里的判断只考虑**数据文件**（DataFile），不涉及**删除文件**（DeleteFile，即 equality delete / position delete 文件）。`MergingSnapshotProducer` 另有 `deletesDeleteFiles()` 和 `addsDeleteFiles()` 方法针对删除文件，但本优化不涉及。

### 边界情形分析

- `overwriteByRowFilter(alwaysTrue)` 删掉全部数据文件、不追加 → `deletesDataFiles()=true, addsDataFiles()=false` → `DELETE`。语义正确：这就是删除操作。
- `addFile(f1).addFile(f2)` 只追加、不删除 → `deletesDataFiles()=false, addsDataFiles()=true` → `APPEND`。语义正确：这就是追加操作。
- `addFile(f1).deleteFile(f2)` 既加又删 → `deletesDataFiles()=true, addsDataFiles()=true` → `OVERWRITE`。语义正确：真正的覆写。
- `overwriteByRowFilter(expr).addFile(f1)` 按过滤删 + 追加 → 两者都 true → `OVERWRITE`。语义正确。
- 空操作（既不删也不加）：两者都 false → 落到 `OVERWRITE`。理论上空 OverwriteFiles 提交不常见，保持原行为不引入回归。

### 测试调整逻辑

由于操作类型的语义变化，多个原有测试的断言需要相应更新。调整分三类：

1. **新增专门验证 operation 类型的单测**（`TestOverwrite.java`）：新增 6 个测试用例，分别覆盖"只删→DELETE"、"只加→APPEND"、"既删又加→OVERWRITE"、以及 `overwriteByRowFilter` 场景下的对应类型。这是对核心逻辑的直接验证。

2. **修正原有测试的预期值**（`TestOverwrite.java`）：原有几个测试在调用 `newOverwrite().deleteFile(...)` 或 `overwriteByRowFilter(...)` 后，之前未断言 operation 类型；现在补上断言，且预期值为 `DELETE`（只删场景）或 `OVERWRITE`（既删又加场景）。其中一处 lower/upper bounds 的预期值从 5/9 改为 10/14，是因为该测试的数据集语义从"FILE_5_TO_9 被保留"改为"FILE_10_TO_14 被追加"，需要与新的测试数据范围一致。

3. **Spark 引擎层测试适配**（`SparkRowLevelOperationsTestBase`、`TestDelete`、`TestStructuredStreamingRead3`，覆盖 v3.3/v3.4/v3.5）：

   - `SparkRowLevelOperationsTestBase.validateCopyOnWrite`：原固定断言 `OVERWRITE`，现改为 `addedDataFiles == null && deletedDataFiles != null ? DELETE : OVERWRITE`，即"只有删除、没有新增数据文件时断言 DELETE，否则断言 OVERWRITE"。这是因为 COW 模式下的 DELETE 操作（如 `DELETE FROM t WHERE ...`）在无法走 metadata-only 路径时会走 `OverwriteFiles`，且只删不增——现在 operation 变为 DELETE。
   
   - `TestDelete`：在断言"应该有 4 个快照"之后，新增 `assertThat(currentSnapshot.operation()).isEqualTo(DELETE)`，并更新注释说明"COW 下移除了数据文件 / MOR 下添加了删除文件，因此是 delete 而非 overwrite"。
   
   - `TestStructuredStreamingRead3.testReadStreamWithSnapshotTypeDeleteAndSkipOverwriteOption`：这个测试原本调用 `table.newOverwrite().overwriteByRowFilter(greaterThan("id", 4))` 来制造一个 OVERWRITE 快照，并断言其类型为 OVERWRITE 以验证"skip-overwrite"逻辑。修改后，由于该调用只删不增，operation 变成了 DELETE，不再满足测试前提。因此测试被改为先 `addFile(dataFile)` 再 `overwriteByRowFilter(...)`，使其变成"既删又加"的真 OVERWRITE，从而保留测试原意。新增的 `dataFile` 是一个构造好的占位 `DataFile`（路径为临时文件，大小 10，1 条记录，PARQUET 格式）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseOverwriteFiles.java`
**修改目的**：让 `OverwriteFiles` 操作的快照 `operation` 类型精确反映实际的数据文件增删语义。
**工作逻辑**：

原 `operation()` 实现：
```java
@Override
protected String operation() {
  return DataOperations.OVERWRITE;
}
```

修改后：
```java
@Override
protected String operation() {
  if (deletesDataFiles() && !addsDataFiles()) {
    return DataOperations.DELETE;
  }

  if (addsDataFiles() && !deletesDataFiles()) {
    return DataOperations.APPEND;
  }

  return DataOperations.OVERWRITE;
}
```

判断顺序的设计：先判断"只删不加"返回 DELETE，再判断"只加不删"返回 APPEND，最后兜底返回 OVERWRITE。两个 if 条件互斥（一个要求 `!addsDataFiles()`，另一个要求 `!deletesDataFiles()`），当两者都为 true 时（既删又加）两个 if 都不命中，落到最后的 OVERWRITE，语义正确。

`deletesDataFiles()` 和 `addsDataFiles()` 都是 `protected` 方法，由父类 `MergingSnapshotProducer` 提供，基于本次操作累积的状态（`filterManager` 的删除标记、`newDataFiles` 列表的大小）判断，是提交时即时计算的真实状态而非估算，因此判断准确。

### `core/src/test/java/org/apache/iceberg/TestOverwrite.java`
**修改目的**：新增 operation 类型验证测试，并修正原有测试的断言。
**工作逻辑**：

- 新增 6 个 `@TestTemplate` 方法：
  - `deleteDataFilesProducesDeleteOperation`：`newOverwrite().deleteFile(FILE_A).deleteFile(FILE_B)` → 断言 operation == `DELETE`
  - `addAndDeleteDataFilesProducesOverwriteOperation`：`newOverwrite().addFile(FILE_10_TO_14).deleteFile(FILE_B)` → 断言 operation == `OVERWRITE`
  - `overwriteByRowFilterProducesDeleteOperation`：`newOverwrite().overwriteByRowFilter(equal("date", "2018-06-08"))` → 断言 operation == `DELETE`（按行过滤删除匹配文件，未追加）
  - `addAndOverwriteByRowFilterProducesOverwriteOperation`：`newOverwrite().addFile(FILE_10_TO_14).overwriteByRowFilter(equal("date", "2018-06-08"))` → 断言 operation == `OVERWRITE`
  - `addFilesProducesAppendOperation`：`newOverwrite().addFile(FILE_10_TO_14).addFile(FILE_5_TO_9)` → 断言 operation == `APPEND`

- 修正一处 lower/upper bounds 预期：从 `longToBuffer(5L)`/`longToBuffer(9L)` 改为 `longToBuffer(10L)`/`longToBuffer(14L)`，配合新增的 `FILE_10_TO_14` 数据文件断言。

- 在多个原有测试（`testOverwriteWithoutAppend`、`testOverwriteWithoutAppendWithRewriteManifests` 等）中补加 `assertThat(latestSnapshot(table, branch).operation()).isEqualTo(...)` 断言，分别预期 `DELETE` 或 `OVERWRITE`。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`
（v3.4、v3.5 同名文件做相同修改）
**修改目的**：让 COW 模式下 DELETE 操作的快照类型断言适配新的 operation 语义。
**工作逻辑**：

`validateCopyOnWrite` 方法原固定传入 `OVERWRITE`：
```java
validateSnapshot(snapshot, OVERWRITE, changedPartitionCount, deletedDataFiles, null, addedDataFiles);
```
改为按"是否只有删除"动态选择：
```java
String operation = null == addedDataFiles && null != deletedDataFiles ? DELETE : OVERWRITE;
validateSnapshot(snapshot, operation, changedPartitionCount, deletedDataFiles, null, addedDataFiles);
```
当 `addedDataFiles == null`（无新增数据文件）且 `deletedDataFiles != null`（有删除数据文件）时，预期 operation 为 `DELETE`；否则为 `OVERWRITE`。这对应 COW DELETE 在无法走 metadata-only 路径时退化为 `OverwriteFiles` 只删不加的场景。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`
（v3.4、v3.5 同名文件做相同修改）
**修改目的**：在 DELETE 测试中补加 operation 类型断言。
**工作逻辑**：

- 新增 `import static org.apache.iceberg.DataOperations.DELETE;`
- 在断言"应该有 4 个快照"后，新增 `assertThat(currentSnapshot.operation()).isEqualTo(DELETE);`
- 更新注释：从 "should be an overwrite since cannot be executed using a metadata operation" 改为 "should be a 'delete' instead of an 'overwrite' as only data files have been removed (COW) / delete files have been added (MOR)"。注释明确区分了 COW（移除数据文件）和 MOR（添加删除文件）两种模式下都是 DELETE 操作。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java`
（v3.4、v3.5 同名文件做相同修改）
**修改目的**：修复因 operation 语义变化而失效的流式读取测试。
**工作逻辑**：

`testReadStreamWithSnapshotTypeDeleteAndSkipOverwriteOption` 测试原意是制造一个 OVERWRITE 快照来验证 `STREAMING_SKIP_OVERWRITE_SNAPSHOTS` 选项。原代码：
```java
table.newOverwrite().overwriteByRowFilter(Expressions.greaterThan("id", 4)).commit();
```
修改后此调用只删不增，operation 变为 DELETE，不再满足"制造 OVERWRITE 快照"的测试前提。因此改为：
```java
DataFile dataFile = DataFiles.builder(table.spec())
    .withPath(temp.newFile().toString())
    .withFileSizeInBytes(10)
    .withRecordCount(1)
    .withFormat(FileFormat.PARQUET)
    .build();

table.newOverwrite()
    .addFile(dataFile)
    .overwriteByRowFilter(Expressions.greaterThan("id", 4))
    .commit();
```
通过先 `addFile(dataFile)` 再 `overwriteByRowFilter(...)`，使操作变为"既删又加"，operation 保持为 `OVERWRITE`，从而保留测试对 skip-overwrite 行为的验证意图。新增的 `dataFile` 是占位文件（路径指向临时文件，实际不会被读取，仅用于让 `addsDataFiles()` 返回 true）。

注意 v3.5 的实现略有不同：`withPath` 使用 `File.createTempFile("junit", null, temp.toFile()).getPath()` 而非 `temp.newFile().toString()`，这是因为 JUnit5 的 `@TempDir` API 与 JUnit4 不同，但语义一致。

## 小结
- **成效**：成功让 `OverwriteFiles` 的快照 operation 类型精确反映实际增删语义（只删→DELETE、只增→APPEND、既删又增→OVERWRITE），使下游消费者（尤其是 Spark Structured Streaming 的 skip-overwrite / skip-delete 选项）能基于准确的快照类型做出正确的增量处理决策。
- **影响范围**：
  - **核心逻辑**：`core` 模块的 `BaseOverwriteFiles.operation()`，影响所有走 `OverwriteFiles` 路径的提交（包括用户直接调用 `newOverwrite()`，以及引擎层 COW DELETE / REPLACE PARTITIONS 等内部退化为 OverwriteFiles 的场景）。
  - **测试**：`core` 模块 `TestOverwrite`，`spark` 模块 v3.3/v3.4/v3.5 的 `SparkRowLevelOperationsTestBase`、`TestDelete`、`TestStructuredStreamingRead3`。
  - **下游影响**：任何依赖快照 `operation` 字段做行为决策的消费者（Spark Structured Streaming、Flink connector、维护作业、增量同步工具等）都会受到此语义变化的影响——这是预期的正向影响。
- **回迁到 1.4.x 的注意事项**：
  - 这是对快照元数据语义的变更，属于行为改变而非纯 bug 修复。回迁前需评估 1.4.x 是否已有下游消费者依赖"OverwriteFiles 一律产生 OVERWRITE"这一旧行为。如果有用户在 1.4.x 上基于 `operation == OVERWRITE` 做了特定逻辑（如流作业的 skip 配置），回迁后这些逻辑的行为会变化（原本被识别为 OVERWRITE 的只删/只增快照现在会被识别为 DELETE/APPEND）。
  - 该变更与 #10150 PR 配套的测试改动较多（涉及 core 与 spark 三个版本的测试），回迁时需一并 cherry-pick 全部相关测试文件，否则测试会失败。
  - 建议先确认 1.4.x 的 `BaseOverwriteFiles.operation()` 实现与 main 一致（直接返回 `OVERWRITE`），且 `MergingSnapshotProducer` 已提供 `deletesDataFiles()` / `addsDataFiles()` 两个 protected 方法（这两个方法在较早版本就存在，1.4.x 应已具备）。
  - 回迁后应在 1.4.x 上跑一遍 `TestOverwrite` 和 Spark v3.3/v3.4（1.4.x 支持的 Spark 版本）的相关测试套件，确认无回归。
