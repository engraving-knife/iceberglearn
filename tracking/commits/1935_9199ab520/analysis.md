# 提交 1935：Core: Remove redundant parameters() definition from subclasses of TestBase (#12666)

## 提交信息

- **序号**：1935 / 4088
- **哈希**：9199ab520b92084e058d7cf7101b15802eeefc7c
- **短哈希**：9199ab520
- **日期**：2025-03-28 19:08:39 +0100
- **作者**：sullis
- **提交说明**：Core: Remove redundant parameters() definition from subclasses of TestBase (#12666)
- **PR/Issue**：#12666

## 总体目的

这是一个测试代码清理重构。Iceberg 的 core 模块测试使用 JUnit5 的参数化测试扩展（`ParameterizedTestExtension`），通过 `@Parameters(name = "formatVersion = {0}")` 标注的静态方法 `parameters()` 提供参数列表（即 formatVersion 取值 `1, 2, 3`），每个参数会驱动一次测试执行。

基类 `TestBase` 本身已经定义了 `parameters()` 方法返回 `Arrays.asList(1, 2, 3)`，但其 34 个子类中却各自重复覆盖（override）了完全相同的 `parameters()` 方法，返回完全相同的 `Arrays.asList(1, 2, 3)`。这些子类的覆盖是冗余的——它们既没有改变参数列表，也没有提供不同的行为，纯属"防御性复制"式的历史遗留代码，可能是从模板复制粘贴而来。

冗余的覆盖会带来维护负担：未来若要调整 formatVersion 测试范围（例如新增 formatVersion 4），需要修改 35 处而非 1 处。本提交删除所有子类中冗余的 `parameters()` 覆盖，使其直接继承 `TestBase` 的实现，代码更简洁、更易维护，单一数据源（single source of truth）。同时清理因此变得不再使用的 `Arrays`/`List` 导入。

## 如何达成设计目的

设计思路是利用 Java 方法继承：删除子类中与父类签名与实现完全一致的 `parameters()` 覆盖，使子类自动继承 `TestBase.parameters()`。JUnit5 参数化扩展通过反射查找 `@Parameters` 标注的方法，无论该方法定义在当前类还是父类都能识别，因此删除覆盖不影响参数化测试的执行。每个子类删除的内容包括：`@Parameters` 注解、`parameters()` 方法体，以及仅为此方法导入的 `java.util.Arrays` 与/或 `java.util.List`。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestBase.java` (未修改)

**说明**：基类未修改，其 `parameters()` 定义如下（已存在于该提交之前）：

```java
@Parameters(name = "formatVersion = {0}")
protected static List<Object> parameters() {
  return Arrays.asList(1, 2, 3);
}
```

子类删除覆盖后将继承此实现。

### 34 个子类测试文件 (修改, 共 -206 lines)

**修改目的**：删除与 `TestBase` 完全一致的 `parameters()` 覆盖，并清理因此不再使用的导入。

**工作逻辑**：每个子类删除如下代码块（示例）：

```java
@Parameters(name = "formatVersion = {0}")
protected static List<Object> parameters() {
  return Arrays.asList(1, 2, 3);
}
```

并移除仅为此方法导入的 `import java.util.Arrays;` 与/或 `import java.util.List;`（仅当该导入在文件中已无其他引用时）。

涉及的 34 个文件包括：
- `MetadataTableScanTestBase.java`（抽象基类，也是 TestBase 子类）
- `TestBatchScans.java`、`TestCreateTransaction.java`、`TestEntriesMetadataTable.java`、`TestFastAppend.java`、`TestFindFiles.java`、`TestIncrementalDataTableScan.java`、`TestLocationProvider.java`、`TestManifestCleanup.java`、`TestManifestReaderStats.java`、`TestManifestWriter.java`、`TestMicroBatchBuilder.java`、`TestReplaceTransaction.java`、`TestRewriteManifests.java`、`TestScanSummary.java`、`TestSchemaAndMappingUpdate.java`、`TestSchemaID.java`、`TestSetPartitionStatistics.java`、`TestSetStatistics.java`、`TestSnapshot.java`、`TestSnapshotLoading.java`、`TestSnapshotManager.java`、`TestSnapshotSelection.java`、`TestSnapshotSummary.java`、`TestSplitPlanning.java`、`TestTableMetadataSerialization.java`、`TestTableUpdatePartitionSpec.java`、`TestTimestampPartitions.java`、`TestTransaction.java`、`TestUpdatePartitionSpec.java`、`TestWapWorkflow.java`
- `actions/TestSizeBasedRewriter.java`
- `io/TestOutputFileFactory.java`
- `mapping/TestMappingUpdates.java`

每个文件删除 4-8 行（含方法体与无用导入），合计删除 206 行，无新增。

## 总结

本次提交为测试代码清理，删除 34 个 `TestBase` 子类中与父类完全一致的冗余 `parameters()` 覆盖（返回 `Arrays.asList(1, 2, 3)`），使子类直接继承 `TestBase.parameters()`，并清理因此不再使用的 `Arrays`/`List` 导入。共删除 206 行，不涉及功能变更，但显著降低了未来调整参数化测试范围的维护成本，实现了单一数据源。
