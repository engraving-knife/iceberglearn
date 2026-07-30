# 提交 3375：Core, Flink, Spark: Deprecate Manifest read methods which rely manifest Metadata (#15575)

## 提交信息

- **序号**：3375 / 4088
- **哈希**：ace2d0375069fec0c6284328de460ba4530af87e
- **短哈希**：ace2d0375
- **日期**：2026-03-11
- **作者**：Russell Spitzer
- **提交说明**：Core, Flink, Spark: Deprecate Manifest read methods which rely manifest Metadata (#15575)
- **PR/Issue**：#15575

## 总体目的

Iceberg 的 `ManifestFiles` 工具类提供了多个读取清单文件的方法，其中部分方法（如 `read(ManifestFile, FileIO)`、`readPaths(ManifestFile, FileIO)`、`open(ManifestFile, FileIO)`）不接收分区规范映射（`specsById`），而是在内部从清单文件的元数据中读取分区规范（通过 `readPartitionSpec(file)`）。这种从清单文件元数据中读取分区规范的方式存在问题：

Iceberg 正在推进支持非 Avro 格式的清单文件，而分区规范信息是 Avro 格式特有的元数据。如果继续依赖从清单元数据中读取分区规范，那么在非 Avro 格式下这些方法将无法工作。因此需要推动调用方显式传入 `specsById`，而不是隐式依赖清单元数据。

本次提交将这些不接收 `specsById` 的方法标记为 `@Deprecated`（自 1.11.0 起，1.12.0 移除），并在 `ManifestReader` 中当 `specsById` 为 null 时输出 WARN 级别日志，提示调用方迁移到传入 `specsById` 的方法。同时将 Core、Flink、Spark 测试中所有调用旧方法的地方改为调用新方法（传入 `table.specs()`）。

## 如何达成设计目的

改动分三步：第一步在 `ManifestFiles` 中为旧方法添加 `@Deprecated` 注解和 Javadoc 说明；第二步在 `ManifestReader` 中添加日志警告；第三步将所有测试和 benchmark 中调用 `ManifestFiles.read(manifest, io)` 的地方改为 `ManifestFiles.read(manifest, io, table.specs())`，将 `ManifestFiles.readDeleteManifest(manifest, io, null)` 改为传入 `table.specs()`。改动覆盖了 Core、Flink 1.20/2.0/2.1、Spark 3.4/3.5/4.0/4.1 多个模块的测试文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (+29/-2 lines)

**修改目的**：为依赖清单元数据的读取方法添加 `@Deprecated` 注解，引导调用方迁移。

**工作逻辑**：
1. `readPaths(ManifestFile, FileIO)` 方法改为新签名 `readPaths(ManifestFile, FileIO, Map<Integer, PartitionSpec> specsById)`，并在内部调用 `read(manifest, io, specsById)`。保留旧签名 `readPaths(ManifestFile, FileIO)` 并标记 `@Deprecated`，说明自 1.11.0 起废弃，1.12.0 移除，应使用新方法。
2. `read(ManifestFile, FileIO)` 方法标记 `@Deprecated`，Javadoc 说明"从清单文件元数据读取分区规范的方式将不支持非 Avro 清单格式"。
3. 包级可见的 `open(ManifestFile, FileIO)` 方法也标记 `@Deprecated`。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (+8/-0 lines)

**修改目的**：当 `specsById` 为 null 时输出警告日志。

**工作逻辑**：
新增 SLF4J Logger。在构造函数中，当 `specsById` 为 null 需要从文件元数据读取分区规范时，输出 WARN 日志：
```java
LOG.warn("Reading partition spec from manifest file metadata is deprecated and will be "
    + "removed in the 1.12.0 release. Pass specsById to avoid reading from file metadata: {}",
    file.location());
```
这样在实际运行中可以观察到哪些代码路径仍在使用旧方式。

### `core/src/jmh/java/org/apache/iceberg/RewriteDataFilesBenchmark.java` (+2/-1 lines)

**修改目的**：将 benchmark 中的旧方法调用迁移到新方法。

**工作逻辑**：
将 `ManifestFiles.read(dataManifest, table.io())` 改为 `ManifestFiles.read(dataManifest, table.io(), table.specs())`。

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+13/-8 lines)

**修改目的**：将核心测试基类中的旧方法调用迁移到新方法。

**工作逻辑**：
多处改动：将 `ManifestFiles.read(manifest, FILE_IO)` 改为 `ManifestFiles.read(manifest, FILE_IO, table.specs())`，将 `ManifestFiles.readDeleteManifest(manifest, FILE_IO, null)` 改为传入 `table.specs()`。同时将 `validateManifestEntries` 和 `files` 方法从 `static` 改为实例方法（因为需要访问 `table.specs()`）。

### `core/src/test/java/org/apache/iceberg/TestManifestReader.java` (+24/-11 lines)

**修改目的**：将测试方法中的旧方法调用迁移到新方法，并新增废弃方法测试。

**工作逻辑**：
多处将 `ManifestFiles.read(manifest, FILE_IO)` 改为 `ManifestFiles.read(manifest, FILE_IO, table.specs())`，将 `ManifestFiles.readDeleteManifest(manifest, FILE_IO, null)` 改为传入 `table.specs()`。新增 `testDeprecatedReadWithoutSpecsById` 测试方法（标注 `@SuppressWarnings("deprecation")`），验证旧方法仍能正常工作，确保向后兼容性。

### `core/src/test/java/org/apache/iceberg/TestManifestReaderStats.java` (+15/-8 lines)

**修改目的**：将统计相关测试中的旧方法调用迁移到新方法。

**工作逻辑**：
多处将 `ManifestFiles.read(manifest, FILE_IO)` 改为 `ManifestFiles.read(manifest, FILE_IO, table.specs())`，包括带 `filterRows`、`select`、`project` 链式调用的场景。

### `core/src/test/java/org/apache/iceberg/TestManifestWriter.java` (+2/-1 lines)

**修改目的**：将 writer 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
将 `ManifestFiles.read(manifest, table.io())` 改为 `ManifestFiles.read(manifest, table.io(), table.specs())`。

### `core/src/test/java/org/apache/iceberg/TestManifestWriterVersions.java` (+6/-2 lines)

**修改目的**：将 writer 版本测试中的旧方法调用迁移到新方法。

**工作逻辑**：
新增 `SPECS_BY_ID` 常量（`ImmutableMap.of(SPEC.specId(), SPEC)`），将 `readManifestAsList` 中的 `ManifestFiles.read(manifest, io)` 改为 `ManifestFiles.read(manifest, io, SPECS_BY_ID)`，将 `readDeleteManifest` 中的 `ManifestFiles.readDeleteManifest(manifest, io, null)` 改为传入 `SPECS_BY_ID`。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (+4/-2 lines)

**修改目的**：将 merge append 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
两处将 `ManifestFiles.read(..., FILE_IO)` 改为 `ManifestFiles.read(..., FILE_IO, table.specs())`。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (+1/-1 lines)

**修改目的**：将 rewrite files 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
将 `ManifestFiles.read(newManifest, FILE_IO)` 改为 `ManifestFiles.read(newManifest, FILE_IO, table.specs())`。

### `core/src/test/java/org/apache/iceberg/TestRewriteManifests.java` (+13/-8 lines)

**修改目的**：将 rewrite manifests 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
多处将 `ManifestFiles.read(manifests.get(0), table.io())` 改为 `ManifestFiles.read(manifests.get(0), table.io(), table.specs())`，包括在 `rewriteIf` lambda 内部的调用。

### `core/src/test/java/org/apache/iceberg/TestSequenceNumberForV2Table.java` (+2/-1 lines)

**修改目的**：将序列号测试中的旧方法调用迁移到新方法。

**工作逻辑**：
将 `ManifestFiles.read(newManifest, FILE_IO)` 改为 `ManifestFiles.read(newManifest, FILE_IO, table.specs())`。

### Flink 模块测试文件（`TestFlinkTableSinkCompaction.java` 和 `TestIcebergSinkCompact.java`，各 +2/-1 lines，覆盖 v1.20/v2.0/v2.1）

**修改目的**：将 Flink 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
各文件中 `getDataFiles` 方法内的 `ManifestFiles.read(dataManifest, table.io())` 改为 `ManifestFiles.read(dataManifest, table.io(), table.specs())`。

### Spark 模块测试文件（`ValidationHelpers.java`、`TestCompressionSettings.java`、`TestSparkDataFile.java`、`TestSparkDataWrite.java`，覆盖 v3.4/v3.5/v4.0/v4.1）

**修改目的**：将 Spark 测试中的旧方法调用迁移到新方法。

**工作逻辑**：
各文件中将 `ManifestFiles.read(manifest, table.io())` 改为 `ManifestFiles.read(manifest, table.io(), table.specs())`。`ValidationHelpers` 中的改动相同。各 Spark 版本的改动内容一致。

## 总结

本次提交通过标记废弃和添加日志警告，系统性推动 Iceberg 生态从"依赖清单元数据读取分区规范"迁移到"显式传入 specsById"的方式。这是为支持非 Avro 清单格式所做的前瞻性准备工作。改动覆盖面广（Core、Flink、Spark 多个版本模块的 34 个文件），但每处改动都是机械性的方法签名替换，风险低且向后兼容。新增的废弃方法测试确保了过渡期的平滑性。
