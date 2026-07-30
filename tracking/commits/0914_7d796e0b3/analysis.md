# 提交 0914：Flink 1.17, 1.18: Migrate tests to JUnit5 (#10663)

## 提交信息

- **序号**：0914 / 4088
- **哈希**：7d796e0b38ad7f2d80e76b3a250e3f8705c1a62f
- **短哈希**：7d796e0b3
- **日期**：2024-07-10（Wed Jul 10 15:13:43 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Flink 1.17, 1.18: Migrate tests to JUnit5 (#10663)
- **PR/Issue**：#10663

## 总体目的

本提交是 Iceberg 项目"JUnit 4 → JUnit 5 + AssertJ"测试栈统一迁移工作的延续，专门针对 `flink/v1.17` 和 `flink/v1.18` 两个 Flink 集成模块的 source/reader/enumerator/assigner 相关测试。在它之前，`data` 模块以及 `flink/v1.19` 已先行迁移；本提交把 1.17/1.18 模块下仍使用 JUnit 4（`org.junit.Assert`、`@Rule`、`@ClassRule`、`TemporaryFolder`、`@Before`、`@Test`、`TestName` 等）的测试类统一改为 JUnit 5（`org.junit.jupiter.api.*`、`@TempDir`、`@BeforeEach`、`@RegisterExtension`）并采用 AssertJ 流式断言。

由于 `flink/v1.19` 已经迁移过，本提交还顺带对 v1.19 中几处与本次迁移相关的小问题做了清理（例如 `TestArrayPoolDataIteratorBatcherRowData` 把 `@BeforeAll` 静态配置改为字段初始化、`ReaderFunctionTestBase` 删除多余的 `@Parameter(index = 1)`、`TestContinuousSplitPlannerImplStartStrategy` 移除冗余注释、`TestContinuousIcebergEnumerator` 合并两行 `assertThat` 链式调用）。同时引入了 `HadoopTableExtension`（JUnit 5 Extension 风格）替代 `HadoopTableResource`（JUnit 4 `@Rule` 风格），使 1.17/1.18 与 1.19 的测试基础设施保持一致。

## 如何达成设计目的

整体采用机械替换 + 适配式重构：
1. 注解迁移：`@Rule`/`@ClassRule` + `TemporaryFolder` → `@TempDir Path temporaryFolder`；`@Before` → `@BeforeEach`；`@Test` 保持不变（仍是 `org.junit.jupiter.api.Test`）；`@Rule TestName` 在不需要时直接删除。
2. 表资源迁移：把 `@Rule public final HadoopTableResource tableResource = new HadoopTableResource(TEMPORARY_FOLDER, ...)` 改为 `@RegisterExtension private static final HadoopTableExtension TABLE_RESOURCE = new HadoopTableExtension(...)`，调用处 `tableResource.table()` → `TABLE_RESOURCE.table()`。
3. 断言迁移：`Assert.assertEquals/NotNull/Null/Same/True/False` → `assertThat(...).isEqualTo/isNotNull/isNull/isSameAs/isTrue/isFalse`；`Assert.fail(msg)` → `org.assertj.core.api.Assertions.fail(...)`。
4. 辅助类适配：`SplitHelpers.createSplitsFromTransientHadoopTable` 与 `equipSplitsWithMockDeleteFiles` 的入参从 `TemporaryFolder` 改为 `java.nio.file.Path`，内部用 `File.createTempFile("junit", null, temporaryFolder.toFile())` 替代 `temporaryFolder.newFile()`。
5. 清理 v1.19 中遗留的小问题（见修改详情）。

## 修改详情

### `flink/v1.17/.../source/SplitHelpers.java` 与 `flink/v1.18/.../source/SplitHelpers.java`（相同改动）

**修改目的**：把 `SplitHelpers` 工具类的入参从 JUnit 4 的 `TemporaryFolder` 改为 JUnit 5 风格的 `java.nio.file.Path`，使其可在 JUnit 5 `@TempDir` 注入的临时目录上工作。

**工作逻辑**：
- `createSplitsFromTransientHadoopTable(TemporaryFolder, int, int)` 与 `(TemporaryFolder, int, int, String)` 改签名为 `(Path, int, int)` / `(Path, int, int, String)`。
- 内部 `temporaryFolder.newFolder()` → `File.createTempFile("junit", null, temporaryFolder.toFile())` + `assertThat(warehouseFile.delete()).isTrue()`。
- `equipSplitsWithMockDeleteFiles(...)` 同样把 `TemporaryFolder` 改为 `Path`，`temporaryFolder.newFile().getPath()` → `File.createTempFile("junit", null, temporaryFolder.toFile()).getPath()`。
- 删除 `import org.junit.Assert` 与 `import org.junit.rules.TemporaryFolder`，新增 `import static org.assertj.core.api.Assertions.assertThat` 与 `import java.nio.file.Path`。

### `flink/v1.17/.../source/assigner/SplitAssignerTestBase.java` 与 `flink/v1.18` 同名类

**修改目的**：把抽象测试基类从 JUnit 4 迁移到 JUnit 5，并把临时目录改为实例字段 `@TempDir Path`。

**工作逻辑**：
- `@ClassRule public static final TemporaryFolder TEMPORARY_FOLDER` → `@TempDir protected Path temporaryFolder`（从静态共享改为实例级，符合 JUnit 5 默认生命周期）。
- `Assert.assertSame(future, assigner.isAvailable())` → `assertThat(assigner.isAvailable()).isSameAs(future)`。
- `Assert.assertEquals(true, futureCompleted.get())` → `assertThat(futureCompleted.get()).isTrue()`。
- `assertGetNext` 中 `assertEquals/notNull/null/fail` → `isEqualTo/isNotNull/isNull/Assertions.fail`。
- `assertSnapshot` 中 `Assert.assertEquals(splitCount, stateBeforeGet.size())` → `assertThat(stateBeforeGet).hasSize(splitCount)`。
- `createSplits(...)` 调用 `SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, ...)` → `(...temporaryFolder, ...)`。

### `flink/v1.17/.../assigner/TestDefaultSplitAssigner.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，4 行改动。模式同上。

### `flink/v1.17/.../assigner/TestFileSequenceNumberBasedSplitAssigner.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，8 行改动。模式同上。

### `flink/v1.17/.../assigner/TestWatermarkBasedSplitAssigner.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，10 行改动。模式同上。

### `flink/v1.17/.../source/enumerator/TestContinuousIcebergEnumerator.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，96 行改动。把 `@Rule`/`@ClassRule`/`TemporaryFolder`/`Assert.*` 等替换为 JUnit 5 + AssertJ 风格。`flink/v1.19` 中对应文件也有 3 行改动，主要是把 `assertThat(pendingSplitIds).hasSameSizeAs(splits); assertThat(pendingSplitIds).first().isEqualTo(...)` 合并为链式 `assertThat(pendingSplitIds).hasSameSizeAs(splits).first().isEqualTo(...)`。

### `flink/v1.17/.../enumerator/TestContinuousSplitPlannerImpl.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，351 行改动，是本提交改动量最大的文件之一。

**工作逻辑**：
- `@ClassRule TemporaryFolder` → `@TempDir Path temporaryFolder`。
- `@Rule HadoopTableResource tableResource` → `@RegisterExtension static HadoopTableExtension TABLE_RESOURCE`，所有 `tableResource.table()` → `TABLE_RESOURCE.table()`。
- `@Rule TestName testName` 字段被删除（迁移后不再使用）。
- `@Before` → `@BeforeEach`，`@Test` import 改为 `org.junit.jupiter.api.Test`。
- 所有 `Assert.*` → AssertJ `assertThat`。

### `flink/v1.17/.../enumerator/TestContinuousSplitPlannerImplStartStrategy.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，80 行改动。模式同上。v1.19 中对应文件还移除了一些冗余的 `// empty table` 注释（5 行删除）。

### `flink/v1.17/.../enumerator/TestEnumerationHistory.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，23 行改动。模式同上。

### `flink/v1.17/.../enumerator/TestIcebergEnumeratorStateSerializer.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，67 行改动。模式同上。

### `flink/v1.17/.../source/reader/ReaderFunctionTestBase.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，65 行改动。v1.19 中同名文件还删除了一行多余的 `@Parameter(index = 1)`（保留 `appenderFactory` 字段但不再作为参数化参数注入）。

### `flink/v1.17/.../source/reader/ReaderUtil.java` 与 `flink/v1.18` 同名类

**修改目的**：删除仅为 JUnit 4 测试保留的 `createCombinedScanTask(..., TemporaryFolder, ...)` 重载方法。

**工作逻辑**：

```diff
-  // Only for JUnit4 tests. Keep this method for test migration from JUnit4 to JUnit5
-  public static CombinedScanTask createCombinedScanTask(
-      List<List<Record>> recordBatchList,
-      TemporaryFolder temporaryFolder,
-      FileFormat fileFormat,
-      GenericAppenderFactory appenderFactory)
-      throws IOException {
-    ...
-  }
```

迁移完成后，所有调用方都改用 `Path` 版本，因此可以清理这个临时保留的兼容方法（19 行删除）。

### `flink/v1.17/.../source/reader/TestArrayBatchRecords.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，19 行改动。模式同上。

### `flink/v1.17/.../source/reader/TestArrayPoolDataIteratorBatcherRowData.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，286 行改动。v1.19 中同名文件也有 68 行改动，主要是把 `@BeforeAll` 静态初始化配置改为字段初始化：

```diff
-  private static final Configuration config = new Configuration();
+  private final Configuration config =
+      new Configuration()
+          .set(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY, 1)
+          .set(FlinkConfigOptions.SOURCE_READER_FETCH_BATCH_RECORD_COUNT, 2);
...
-  @BeforeAll
-  public static void setConfig() {
-    // set array pool size to 1
-    config.set(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY, 1);
-    // set batch array size to 2
-    config.set(FlinkConfigOptions.SOURCE_READER_FETCH_BATCH_RECORD_COUNT, 2);
-  }
```

同时把若干注释移到 `assertThat(...).as(...)` 中作为断言描述。

### `flink/v1.17/.../source/reader/TestIcebergSourceReader.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，分别 35 行 / 33 行改动。模式同上。

### `flink/v1.17/.../source/reader/TestRowDataReaderFunction.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，13 行改动。模式同上。

### `flink/v1.17/.../source/split/TestIcebergSourceSplitSerializer.java` 与 `flink/v1.18` 同名类

**修改目的**：迁移到 JUnit 5，47 行改动。模式同上。

## 小结

- **成效**：完成 `flink/v1.17` 与 `flink/v1.18` 模块下 source/reader/enumerator/assigner/split 共 17 个测试类（每模块 17 个，共 34 个文件，部分文件 1.17 与 1.18 内容相同）从 JUnit 4 到 JUnit 5 + AssertJ 的迁移，并顺带清理 `flink/v1.19` 中 4 个相关测试文件的遗留问题。共修改 38 个文件，1169 行新增 / 1250 行删除。
- **影响范围**：仅测试代码，无生产代码变更。涉及 `flink/v1.17`、`flink/v1.18`、`flink/v1.19` 三个模块的 source/reader/enumerator/assigner/split 测试目录。引入了 `HadoopTableExtension`（JUnit 5）替代 `HadoopTableResource`（JUnit 4）。
- **回迁到 1.4.x 的注意事项**：测试基础设施迁移，不影响生产功能。
  - 1.4.x 默认仍使用 JUnit 4，单独回迁本提交会让 `flink/v1.17|1.18` 测试栈与分支其余部分不一致，且依赖 `HadoopTableExtension`（JUnit 5 Extension）在 1.4.x 中的可用性。需确认 1.4.x 已有该扩展类以及 AssertJ 依赖。
  - 由于本提交改动量大（38 文件），且仅是测试栈统一，回迁收益低、风险高，建议不主动回迁。
  - 若 1.4.x 整体已决定切换到 JUnit 5，则应作为一组连续的 JUnit 5 迁移提交（含 `data`、`flink/v1.19`、本提交等）一起回迁，确保 `HadoopTableExtension`、`ParameterizedTestExtension` 等基础扩展类同步存在。
