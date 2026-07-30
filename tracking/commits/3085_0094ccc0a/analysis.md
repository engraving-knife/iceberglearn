# 提交 3085：Use SnapshotRef.MAIN_BRANCH instead of the 'main' string (#14999)

## 提交信息

- **序号**：3085 / 4088
- **哈希**：0094ccc0aeec33638f34ff5c4074b4cfe4bdf58c
- **短哈希**：0094ccc0a
- **日期**：2026-01-08
- **作者**：pvary
- **提交说明**：Use SnapshotRef.MAIN_BRANCH instead of the 'main' string (#14999)
- **PR/Issue**：#14999

## 总体目的

Iceberg 中主分支的名称 `"main"` 是一个在 API 层面有明确定义的常量 `SnapshotRef.MAIN_BRANCH`（值为 `"main"`，定义在 `api/src/main/java/org/apache/iceberg/SnapshotRef.java`）。然而在代码库的大量测试代码和部分生产代码中，主分支名称被硬编码为字符串字面量 `"main"`，而非使用该常量引用。

这种硬编码做法存在多个问题：首先，如果将来主分支的默认名称发生变化（虽然不太可能但并非不可能，或某些自定义 catalog 可能使用不同名称），所有硬编码 `"main"` 的地方都需要逐一修改，容易遗漏且维护成本高；其次，使用字符串字面量而非语义化常量降低了代码可读性，读者需要自行理解 `"main"` 的含义；第三，在涉及分支比较的测试逻辑中（如 `assumeThat(branch).isEqualTo("main")`），硬编码可能导致与实际分支名称不一致时产生隐蔽的 bug。

本提交的目的是将代码库中所有硬编码的 `"main"` 字符串字面量替换为 `SnapshotRef.MAIN_BRANCH` 常量引用，提升代码的一致性、可维护性和可读性。这是一个大面积的机械性重构（共 68 个文件），绝大多数改动在测试代码中，仅一处生产代码（Flink 动态 sink 的 `WriteTarget.java`）涉及。

## 如何达成设计目的

通过全局搜索替换，将所有表示主分支名称的 `"main"` 字面量替换为 `SnapshotRef.MAIN_BRANCH`，并添加对应的 `import org.apache.iceberg.SnapshotRef` 导入。改动覆盖 core 模块测试、Flink（v1.20/v2.0/v2.1）生产代码和测试、Nessie 测试、Spark（v3.4/v3.5/v4.0/v4.1）测试。由于改动是机械性的常量替换，不改变任何运行时行为——`SnapshotRef.MAIN_BRANCH` 的值就是 `"main"`，因此这是一次纯代码质量改进，无功能变化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotRef.java`（未修改）

说明：该文件定义了 `public static final String MAIN_BRANCH = "main"` 常量，本提交使用此常量，但未修改该文件本身。

### `core/src/test/java/org/apache/iceberg/TestDeleteFiles.java` (+4/-1 lines)

**修改目的**：将参数化测试中的 `"main"` 替换为 `SnapshotRef.MAIN_BRANCH`。

**工作逻辑**：
在 `@Parameters` 方法中，`Stream.of(new Object[] {v, "main"}, new Object[] {v, "testBranch"})` 改为 `Stream.of(new Object[] {v, SnapshotRef.MAIN_BRANCH}, new Object[] {v, "testBranch"})`。这些参数用于以不同 formatVersion 和分支名组合运行测试。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+4/-1 lines)

**修改目的**：同 `TestDeleteFiles`，替换参数化测试中的 `"main"`。逻辑一致。

### `core/src/test/java/org/apache/iceberg/TestOverwrite.java` (+4/-1 lines)

**修改目的**：同上。逻辑一致。

### `core/src/test/java/org/apache/iceberg/TestOverwriteWithValidation.java` (+4/-1 lines)

**修改目的**：同上。逻辑一致。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+3/-1 lines)

**修改目的**：将测试中引用主分支的 `"main"` 替换为常量。

### `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java` (+2/-1 lines)

**修改目的**：同上。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (+2/-1 lines)

**修改目的**：同上。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+6/-1 lines)

**修改目的**：替换参数化测试和分支假设断言中的 `"main"`。

**工作逻辑**：
`@Parameters` 中的 `"main"` 替换为常量；`testConcurrentManifestRewriteWithRemoveRowsRemoval` 方法中的 `assumeThat(branch).isEqualTo("main")` 改为 `assumeThat(branch).isEqualTo(SnapshotRef.MAIN_BRANCH)`，用于跳过非主分支的 manifest 重写测试。

### `core/src/test/java/org/apache/iceberg/TestRowLineageAssignment.java` (+3/-2 lines)

**修改目的**：替换 `fastForwardBranch` 调用和分支列表中的 `"main"`。

**工作逻辑**：
`table.manageSnapshots().fastForwardBranch("main", "branch")` 改为 `fastForwardBranch(SnapshotRef.MAIN_BRANCH, "branch")`；`for (String branch : List.of("main", "branch"))` 改为 `List.of(SnapshotRef.MAIN_BRANCH, "branch")`。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (+12/-4 lines)

**修改目的**：将构建 `SnapshotRef` Map 时的 `"main"` 键替换为常量。

**工作逻辑**：
多处 `ImmutableMap.of("main", SnapshotRef.branchBuilder(...).build(), ...)` 改为 `ImmutableMap.of(SnapshotRef.MAIN_BRANCH, ...)`，确保测试构造的 refs Map 使用与生产代码一致的主分支名称。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java` (+10/-2 lines)

**修改目的**：替换并发提交测试中的 `"main"` 分支引用。

**工作逻辑**：
多处 `String branch = "main"` 改为 `String branch = SnapshotRef.MAIN_BRANCH`；refs 断言中 `containsKey("main")` 和 `ref("main")` 改为使用常量。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/WriteTarget.java` (+2/-1 lines)（生产代码）

**修改目的**：将 `WriteTarget` 中主分支默认值的 `"main"` 替换为常量。

**工作逻辑**：
在构造函数中 `this.branch = branch != null ? branch : "main"` 改为 `this.branch = branch != null ? branch : SnapshotRef.MAIN_BRANCH`。这是本提交唯一的生产代码改动——当用户未指定分支时，默认使用主分支，改用常量引用提升了一致性。新增 `import org.apache.iceberg.SnapshotRef`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java`（及 v2.0、v2.1 同名文件，+248/-118 lines 合计）

**修改目的**：将动态 sink 测试中大量构造 `DynamicIcebergDataImpl` 时传入的 `"main"` 分支参数替换为常量。

**工作逻辑**：
测试中通过 `new DynamicIcebergDataImpl(schema, "t1", "main", spec)` 构造测试数据行，"main" 被替换为 `SnapshotRef.MAIN_BRANCH`。由于该构造是内联多参数调用，替换后为保持代码格式需调整换行，导致 diff 行数较大，但实际逻辑不变。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java`（及 v2.0、v2.1，+44/-12 lines 合计）

**修改目的**：将 `tableUpdater.update(...)` 调用中的 `"main"` 分支参数替换为常量。

**工作逻辑**：
多处 `tableUpdater.update(tableIdentifier, "main", SCHEMA, ...)` 改为 `tableUpdater.update(tableIdentifier, SnapshotRef.MAIN_BRANCH, SCHEMA, ...)`，新增 `SnapshotRef` 导入。

### Flink 其他测试文件（v1.20/v2.0/v2.1 各版本）

包括 `TestFlinkIcebergSinkBranch.java`、`TestFlinkIcebergSinkV2.java`、`TestFlinkIcebergSinkV2Branch.java`、`TestIcebergFilesCommitter.java`、`TestIcebergSinkBranch.java`、`TestIcebergSinkV2.java`、`TestDynamicIcebergSinkPerf.java`、`TestHashKeyGenerator.java`、`TestTableMetadataCache.java`、`TestFlinkMetaDataTable.java`、`DynamicRecordSerializerDeserializerBenchmark.java`。

**修改目的**：将上述测试和基准测试中所有引用主分支名称的 `"main"` 字面量替换为 `SnapshotRef.MAIN_BRANCH`。改动模式一致：替换字面量 + 新增导入。三个 Flink 版本的对应文件改动完全对称。

### `nessie/src/test/java/org/apache/iceberg/nessie/BaseTestIceberg.java` (+2/-1 lines)

**修改目的**：将 `createBranch` 默认源分支的 `"main"` 替换为常量。

**工作逻辑**：
`createBranch(name, hash, "main")` 改为 `createBranch(name, hash, SnapshotRef.MAIN_BRANCH)`。

### Nessie 其他测试文件

包括 `TestBranchVisibility.java`、`TestCustomNessieClient.java`、`TestNessieCatalog.java`、`TestNessieIcebergClient.java`、`TestNessieTable.java`、`TestNessieViewCatalog.java`。

**修改目的**：将 Nessie 测试中引用 Nessie 默认分支 `"main"` 的地方替换为 `SnapshotRef.MAIN_BRANCH`。注意 Nessie 的 "main" 分支与 Iceberg 的 main 分支语义对应，使用同一常量。`TestNessieIcebergClient` 中替换了 `new NessieIcebergClient(api, "main", ...)` 和 `api.getReference().refName("main")` 等调用。

### Spark 测试文件（v3.4/v3.5/v4.0/v4.1 各版本）

包括 `spark-extensions/.../TestMetadataTables.java` 和 `spark/.../TestSparkDataWrite.java`。

**修改目的**：将 Spark 元数据表测试和数据写入测试中引用 `"main"` 分支的地方替换为常量。四个 Spark 版本改动对称。

## 总结

本提交是一次大规模的代码质量重构，将代码库中 68 个文件里所有硬编码的主分支名称 `"main"` 字符串字面量替换为语义化常量 `SnapshotRef.MAIN_BRANCH`。改动覆盖 core、Flink（v1.20/v2.0/v2.1）、Nessie、Spark（v3.4/v3.5/v4.0/v4.1）模块，其中唯一的生产代码改动是 Flink 动态 sink 的 `WriteTarget.java` 默认分支赋值。由于常量值与原字面量相同，本次重构不改变任何运行时行为，但显著提升了代码的可维护性和一致性，消除了魔法字符串带来的维护风险。
