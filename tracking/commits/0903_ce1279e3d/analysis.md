# 提交 0903：Flink 1.19: Migrate source package to JUnit5 (#10632)

## 提交信息

- **序号**：0903 / 4088
- **哈希**：ce1279e3d001d2757b61db42e6d460ba649a5e29
- **短哈希**：ce1279e3d
- **日期**：2024-07-05 12:10:02 +0200
- **作者**：Tom Tanaka
- **提交说明**：Flink 1.19: Migrate source package to JUnit5 (#10632)
- **PR/Issue**：#10632

## 总体目的

Iceberg 的 Flink 1.19 模块（`flink/v1.19/flink`）此前仍混用 JUnit4 写测试：`@org.junit.Test`、`@Rule`/`@ClassRule` + `TemporaryFolder`、`org.junit.Assert.*`、`HadoopTableResource`（JUnit4 Rule）等。社区正逐步把各 Flink 子模块的测试基础设施迁移到 JUnit5（Jupiter），以与新版本 Flink 测试基类和 AssertJ 风格断言对齐，并最终淘汰 JUnit4 依赖。

本提交把 Flink 1.19 模块下 `org.apache.iceberg.flink.source` 包（含 `assigner`、`enumerator`、`reader`、`split` 子包）以及共享辅助类 `SplitHelpers`、`ReaderUtil` 的测试代码统一迁移到 JUnit5 + AssertJ，作为该模块整体 JUnit5 化的一步。

## 如何达成设计目的

迁移工作按 JUnit4 → JUnit5 的标准对照表逐文件机械替换：

- `org.junit.Test` → `org.junit.jupiter.api.Test`；`org.junit.Before` → `org.junit.jupiter.api.BeforeEach`；抽象基类中需被参数化子类驱动的测试方法改为 `@TestTemplate`。
- `@ClassRule public static final TemporaryFolder` / `@Rule TemporaryFolder` → `@TempDir Path temporaryFolder`（JUnit5 内建扩展），临时目录由 `java.nio.file.Path` 表示。
- `@Rule HadoopTableResource` / `RuleChain` → `@RegisterExtension HadoopTableExtension`（Jupiter 扩展），消除对 `RuleChain` 编排的需要。
- `org.junit.Assert.*`（`assertEquals`/`assertTrue`/`assertNull`/`assertSame`/`assertArrayEquals`/`fail`）→ AssertJ 的 `assertThat(...).isEqualTo/isTrue/isNull/isSameAs/containsExactly/fail(...)`，可读性更好。
- `TestName` Rule 移除（本提交范围内不再使用测试名）。
- 涉及 `TemporaryFolder.newFile()`/`newFolder()` 的辅助方法改用 `java.io.File.createTempFile("junit", null, temporaryFolder.toFile())` 或直接接收 `Path`。
- 删除 `ReaderUtil` 中专为 JUnit4 残留测试保留的 `createCombinedScanTask(TemporaryFolder, ...)` 重载，统一使用 `Path` 重载。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/SplitHelpers.java`

**修改目的**：把两个 `createSplitsFromTransientHadoopTable` 重载和 `equipSplitsWithMockDeleteFiles` 的入参由 `TemporaryFolder` 改为 `java.nio.file.Path`，与 JUnit5 的 `@TempDir Path` 对接。

**工作逻辑**：内部把 `temporaryFolder.newFolder()` 改为 `File.createTempFile("junit", null, temporaryFolder.toFile())`，再用 `assertThat(warehouseFile.delete()).isTrue()` 断言删除成功；mock delete file 路径同样改用 `File.createTempFile`。原 `org.junit.Assert` import 换成 AssertJ 的 `assertThat`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java`

**修改目的**：移除仅供 JUnit4 测试残留使用的 `createCombinedScanTask(List, TemporaryFolder, FileFormat, GenericAppenderFactory)` 重载。

**工作逻辑**：保留接收 `Path` 的同名重载作为唯一入口，调用方（已迁移到 JUnit5）统一传 `Path`，从而消除 `TemporaryFolder` 依赖与对应 import。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/assigner/SplitAssignerTestBase.java`

**修改目的**：抽象测试基类迁移到 JUnit5。

**工作逻辑**：`@ClassRule TemporaryFolder` → `@TempDir Path temporaryFolder`；`Assert.assertSame/assertEquals/assertNotNull/assertNull/assertArrayEquals/fail` → AssertJ 等价写法；`createSplits` 把 `TEMPORARY_FOLDER` 改传 `temporaryFolder`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestDefaultSplitAssigner.java`、`TestFileSequenceNumberBasedSplitAssigner.java`、`TestWatermarkBasedSplitAssigner.java`

**修改目的**：三个具体 assigner 测试类去除 JUnit4 import（`Assert` 等），改用 AssertJ 静态导入，与基类迁移保持一致。改动量很小（4/8/10 行），主要是 import 替换与少量断言改写。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousIcebergEnumerator.java`

**修改目的**：把 `@ClassRule TemporaryFolder` 改为 `@TempDir Path`，所有 `Assert.*` 替换为 AssertJ。改动以机械替换为主（97 行 diff），无逻辑变化。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java`

**修改目的**：迁移到 JUnit5 + AssertJ，并把 `HadoopTableResource`（JUnit4 Rule）替换为 `HadoopTableExtension`（Jupiter `@RegisterExtension`）。

**工作逻辑**：`@ClassRule TemporaryFolder` → `@TempDir Path temporaryFolder`；`@Rule HadoopTableResource tableResource` → `@RegisterExtension static final HadoopTableExtension TABLE_RESOURCE`；`@Before` → `@BeforeEach`；所有 `Assert.assertEquals/assertTrue/assertNull` 改为 AssertJ 链式断言，例如 `assertThat(result.splits()).hasSize(1)`、`assertThat(discoveredFiles).containsExactlyInAnyOrderElementsOf(expectedFiles)`，部分断言改用 `.satisfies(...)` 做更精确的字段校验。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImplStartStrategy.java`

**修改目的**：同上，迁移到 JUnit5。

**工作逻辑**：去掉 `RuleChain.outerRule(temporaryFolder).around(tableResource)` 编排，改为 `@TempDir Path temporaryFolder` + `@RegisterExtension HadoopTableExtension`；`@Before` → `@BeforeEach`；`Assert.*` → AssertJ。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestEnumerationHistory.java`

**修改目的**：纯断言库迁移，不涉及临时目录或 Rule。把 `Assert.assertTrue/assertFalse/assertArrayEquals` 替换为 `assertThat(...).isTrue()/isFalse()/containsExactly(...)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestIcebergEnumeratorStateSerializer.java`

**修改目的**：迁移到 JUnit5：`@ClassRule TemporaryFolder` → `@TempDir Path`，`Assert.*` → AssertJ，67 行 diff 以机械替换为主。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderFunctionTestBase.java`

**修改目的**：抽象测试基类迁移到 JUnit5。

**工作逻辑**：`@ClassRule TemporaryFolder` → `@TempDir Path`；关键点是将其中的 `@Test` 方法改为 `@TestTemplate`——因为该基类被子类以参数化方式驱动（结合 `@ParameterizedTest`/自定义扩展），需要 `@TestTemplate` 才能在每次参数化调用时各执行一次，这是 JUnit5 下抽象基类的标准做法。其余 `Assert.*` 改 AssertJ。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestArrayBatchRecords.java`

**修改目的**：小范围迁移，`Assert.*` → AssertJ，按需引入 `@TempDir`/`Path`（19 行 diff）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestArrayPoolDataIteratorBatcherRowData.java`

**修改目的**：迁移到 JUnit5。

**工作逻辑**：`@ClassRule TemporaryFolder` → `@TempDir Path`；原构造函数里给 `Configuration` 设值并构造 `batcher`/`appenderFactory` 的逻辑改为字段直接初始化 + `@BeforeAll static void setConfig()` 配置 `SourceReaderOptions` 与 `SOURCE_READER_FETCH_BATCH_RECORD_COUNT`；常量 `fileFormat` 改为 `FILE_FORMAT`（大写常量风格）；`TEMPORARY_FOLDER.newFile()` → `File.createTempFile("junit", null, temporaryFolder.toFile())`；`Assert.*` 全面替换为 AssertJ（如 `assertThat(recordBatchIterator).isExhausted()`）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestIcebergSourceReader.java`

**修改目的**：迁移到 JUnit5。

**工作逻辑**：`@ClassRule TemporaryFolder` → `@TempDir Path`；构造函数初始化 `appenderFactory` 改为字段直接初始化；调用 `ReaderUtil.createCombinedScanTask` 时传 `temporaryFolder`（`Path`）；两行 `Assert.assertEquals(...)` 合并为 `assertThat(rowDataList1).containsExactlyElementsOf(rowDataList2)`；`Assert.assertEquals(expected, size)` 改为 `assertThat(readerOutput.getEmittedRecords()).hasSize((int) expected)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestRowDataReaderFunction.java`

**修改目的**：小范围迁移（13 行），主要是 import 与少量断言改写。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/split/TestIcebergSourceSplitSerializer.java`

**修改目的**：迁移到 JUnit5：`@ClassRule TemporaryFolder` → `@TempDir Path`，`Assert.*` → AssertJ（47 行 diff，纯机械替换）。

## 小结

- **成效**：完成 Flink 1.19 模块 `flink.source` 包全部测试从 JUnit4 到 JUnit5（Jupiter）+ AssertJ 的迁移，移除了仅供 JUnit4 使用的 `ReaderUtil` 重载与 `TemporaryFolder` 依赖，测试逻辑等价、断言可读性提升。
- **影响范围**：仅 `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/` 下 17 个测试文件，无生产代码、无跨模块影响。
- **回迁到 1.4.x 的注意事项**：通常**不需要回迁**。这是 Flink 1.19 专属模块的测试基础设施现代化，1.4.x 维护分支若无 Flink 1.19 模块或未启动 JUnit5 迁移则与该分支无关。若 1.4.x 也维护了 flink 1.19 子模块且需要该模块测试稳定运行，可按文件 cherry-pick；风险点在于依赖 `HadoopTableExtension`（Jupiter 扩展）与 AssertJ 的存在——需确认 1.4.x 分支已引入相应测试依赖（`org.junit.jupiter`、`org.assertj`）以及 `HadoopTableExtension` 类已在该分支落地，否则会出现编译错误。建议回迁前先确认这些前置依赖在 1.4.x 是否就绪。
