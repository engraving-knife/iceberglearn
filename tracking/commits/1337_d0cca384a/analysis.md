# 提交 1337：Spark 3.5: Preserve data file reference during manifest rewrites (#11457)

## 提交信息

- **序号**：1337 / 4088
- **哈希**：d0cca384a01172b5133bf7e207d94e374ed0c2ed
- **短哈希**：d0cca384a
- **日期**：2024-11-04（Mon Nov 4 21:22:50 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Preserve data file reference during manifest rewrites (#11457)
- **PR/Issue**：#11457

## 总体目的

Iceberg 的 manifest 重写（`rewriteManifests`）动作会把表当前的 manifest 文件重新组织、合并生成新的 manifest。这本质上是一次"读取 manifest entry → 写入新 manifest"的转换。在该转换过程中，delete file 元数据中的 `referencedDataFile` 字段（即位置删除文件声明"我所删除的数据文件是哪一个"的字段）应当被完整保留——否则重写后会丢失这层引用关系，下游读取时无法根据 data file 反查关联的 delete file。

但 Spark 3.5 模块中的 `SparkContentFile`（把 Spark `Row` 包装成 `ContentFile` 的适配器）此前并未实现 `referencedDataFile()` 方法，导致 manifest 重写经过 Spark 这一侧时 `referencedDataFile` 信息丢失（始终读到 `null`）。本提交补上 `SparkContentFile.referencedDataFile()` 的字段位置解析与读取实现，并加入端到端的 manifest 重写回归测试验证该字段在重写后仍可读。

需要特别说明的是：这个 bug 只在 manifest rewrite 走 Spark 路径时出现（即 `SparkActions.get().rewriteManifests(...)`），其他模块的 `ContentFile` 实现已正确处理该字段。这也是改动只集中在 Spark 3.5 模块、而测试也在 Spark 模块的原因。

## 如何达成设计目的

1. 在 `SparkContentFile` 中新增 `referencedDataFilePosition` 字段、在构造时通过 `positions.get(DataFile.REFERENCED_DATA_FILE.name())` 取得该列在 Spark `StructType` 中的位置，并新增 `referencedDataFile()` 方法读取该列的 String 值（null safe）。
2. 在测试工具 `FileGenerationUtil` 中新增 `generatePositionDeleteFileWithRef` 方法，用于在测试中生成一个带 `referencedDataFile` 字段的位置删除文件。
3. 在 `TestRewriteManifestsAction` 中新增 `testRewriteManifestsPreservesOptionalFields` 测试：构建一张表、添加 3 个 data file 与 3 个对应的带 `referencedDataFile` 的 position delete file，执行 `rewriteManifests` 重写、`refresh()` 表，再 `planFiles()` 读取，校验每个 data file 关联的 delete file 的 `referencedDataFile()` 与原始值一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`

**修改目的**：让 Spark 行包装的 `ContentFile` 也能正确读取 `referencedDataFile` 字段。

**工作逻辑**：

新增字段：

```java
private final int referencedDataFilePosition;
```

在构造方法中初始化该位置（通过 Spark `StructType` 的字段索引查找）：

```java
this.referencedDataFilePosition = positions.get(DataFile.REFERENCED_DATA_FILE.name());
```

新增方法实现：

```java
public String referencedDataFile() {
    if (wrapped.isNullAt(referencedDataFilePosition)) {
        return null;
    }
    return wrapped.getString(referencedDataFilePosition);
}
```

这是本提交唯一的产品代码改动。其余改动均是为这个新列在 Spark 行中查找位置并 null safe 地读取。

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`

**修改目的**：提供测试用工具方法，生成带 `referencedDataFile` 引用的位置删除文件。

**工作逻辑**：新增方法：

```java
public static DeleteFile generatePositionDeleteFileWithRef(Table table, DataFile dataFile) {
    PartitionSpec spec = table.specs().get(dataFile.specId());
    StructLike partition = dataFile.partition();
    LocationProvider locations = table.locationProvider();
    String path = locations.newDataLocation(spec, partition, generateFileName());
    long fileSize = generateFileSize();
    return FileMetadata.deleteFileBuilder(spec)
        .ofPositionDeletes()
        .withPath(path)
        .withPartition(partition)
        .withFileSizeInBytes(fileSize)
        .withFormat(FileFormat.PARQUET)
        .withReferencedDataFile(dataFile.location())
        .withRecordCount(3)
        .build();
}
```

注意它也使用了上一提交 1334 修复后的 `table.specs().get(dataFile.specId())` 模式，确保分区演进场景下生成的删除文件与 data file 同 spec。这是为 1339 的 DV 测试做铺垫。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：端到端验证 manifest 重写后 `referencedDataFile` 不丢失。

**工作逻辑**：

新增测试 `testRewriteManifestsPreservesOptionalFields`（要求 `formatVersion >= 2`）：

1. 创建一张分区表，按 `c1=0` 分区，添加 3 个 data file（`dataFile1/2/3`）。
2. 用 `newDeleteFileWithRef(table, dataFileN)` 生成 3 个带 `referencedDataFile` 引用的 position delete file，断言生成时该字段值等于对应 data file 路径，并通过 `newRowDelta().addDeletes(...).commit()` 提交。
3. 调用 `SparkActions.get().rewriteManifests(table).rewriteIf(manifest -> true).option(...).execute()` 执行 manifest 重写。
4. `table.refresh()` 刷新后用 `table.newScan().planFiles()` 读取扫描任务，对每个 `FileScanTask` 取出 `file()`（data file）与 `Iterables.getOnlyElement(fileTask.deletes())`（delete file），根据 data file 路径匹配对应的原始 delete file，断言 `deleteFile.referencedDataFile()` 与原始 `deleteFileN.referencedDataFile()` 相等。

测试还增加了 `FileGenerationUtil` 与 `CloseableIterable`、`FileScanTask` 的 import。新增私有方法 `newDeleteFileWithRef(table, dataFile)` 转调 `FileGenerationUtil.generatePositionDeleteFileWithRef`。

## 小结

- **成效**：修复了 Spark 3.5 manifest 重写丢失 `referencedDataFile` 字段的缺陷。该字段是位置删除文件到数据文件的引用，丢失后会影响 Spark 引擎下基于 `referencedDataFile` 的优化路径（如 DV、过滤推送）。同时为后续 1339（DV 字段在 manifest 重写中保留）测试提供了基础。
- **影响范围**：1 个产品文件（`SparkContentFile.java`，+9 行）、1 个测试工具（`FileGenerationUtil.java`，+17 行）、1 个 Spark 测试（`TestRewriteManifestsAction.java`，+63 行），共 3 个文件、+89 行。
- **回迁到 1.4.x 的注意事项**：
  1. 这是 Spark 3.5 模块的功能性修复，**建议回迁**到 1.4.x（前提是 1.4.x 维护 Spark 3.5 模块且使用了 `rewriteManifests` 走 Spark 路径）。
  2. 回迁前应确认 1.4.x 的 `SparkContentFile` 是否已有该字段处理；若 1.4.x 已自行实现可跳过。
  3. 修复依赖 1334（`FileGenerationUtil` 中 `table.specs().get(specId)` 修复）作为前置，建议同时回迁 1334 以保证测试在分区演进下也正确。
  4. 回迁无 manifest 格式兼容性风险——`referencedDataFile` 是已有字段，本提交只是修 Spark 读取路径。回迁后建议跑 `TestRewriteManifestsAction#testRewriteManifestsPreservesOptionalFields` 等相关测试做验证。
