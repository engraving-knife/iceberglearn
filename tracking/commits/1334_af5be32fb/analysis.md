# 提交 1334：Core: Fix generated position delete file spec (#11458)

## 提交信息

- **序号**：1334 / 4088
- **哈希**：af5be32fbef36690d32b5e53c4153b709d8db188
- **短哈希**：af5be32fb
- **日期**：2024-11-04（Mon Nov 4 15:40:21 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Fix generated position delete file spec (#11458)
- **PR/Issue**：#11458

## 总体目的

`core/src/test/java/org/apache/iceberg/FileGenerationUtil.java` 是测试用工具类，用于在测试中合成 `DataFile`/`DeleteFile` 元数据对象。其 `generatePositionDeleteFile(Table table, DataFile dataFile)` 方法用于根据已有的 `dataFile` 生成一个"位置删除文件"（position delete file）元数据，模拟对该数据文件的删除操作。

旧实现中调用 `table.spec()` 取分区规范，但当表经历了分区演进（partition evolution）后，`dataFile` 关联的 specId 与当前表默认 specId 可能不一致——`table.spec()` 总是返回表当前的活跃 spec，而非数据文件生成时所属的旧 spec。这会导致生成的位置删除文件被错误地"挂"到错误的分区规范上，路径生成、partition 数据等也可能不匹配，测试结果偏离实际行为。本提交将该测试工具按 `dataFile.specId()` 反查正确的分区规范。

这是为先前的提交 1335（PR #11446，引入 DV 与 specId 关联）等相关测试做铺垫的小修复，使 `FileGenerationUtil` 在分区演进场景下也能生成正确的 delete file。

## 如何达成设计目的

将 `generatePositionDeleteFile` 方法内部对 `table.spec()` 的两次调用改为 `table.specs().get(dataFile.specId())`，即通过 `dataFile` 自带的 specId 反查 `PartitionSpec`。同一个 spec 变量随后被传给 `locations.newDataLocation(spec, partition, ...)` 和 `FileMetadata.deleteFileBuilder(spec)`，确保路径生成与 builder 都使用与数据文件一致的分区规范。这是一种最小化的"修正引用源"改动，逻辑不变。

注意：同文件中的 `generateEqualityDeleteFile(Table table, StructLike partition)` 方法未做改动，因为该方法只接收 `partition` 参数、不携带 `specId`，沿用 `table.spec()` 是该方法语义下的合理选择。

## 修改详情

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`

**修改目的**：让 `generatePositionDeleteFile` 在分区演进场景下使用与 `dataFile` 一致的 `PartitionSpec`，避免使用表当前的活跃 spec。

**工作逻辑**：

原代码：

```java
public static DeleteFile generatePositionDeleteFile(Table table, DataFile dataFile) {
    PartitionSpec spec = table.spec();
    StructLike partition = dataFile.partition();
    LocationProvider locations = table.locationProvider();
    String path = locations.newDataLocation(spec, partition, generateFileName());
    long fileSize = generateFileSize();
    Metrics metrics = generatePositionDeleteMetrics(dataFile);
    return FileMetadata.deleteFileBuilder(table.spec())
        .ofPositionDeletes()
        .withPath(path)
        .withPartition(partition)
        .withFileSizeInBytes(fileSize)
        .withFormat(FileFormat.PARQUET)
        .withMetrics(metrics)
        .build();
}
```

新代码：

```java
public static DeleteFile generatePositionDeleteFile(Table table, DataFile dataFile) {
    PartitionSpec spec = table.specs().get(dataFile.specId());
    StructLike partition = dataFile.partition();
    LocationProvider locations = table.locationProvider();
    String path = locations.newDataLocation(spec, partition, generateFileName());
    long fileSize = generateFileSize();
    Metrics metrics = generatePositionDeleteMetrics(dataFile);
    return FileMetadata.deleteFileBuilder(spec)
        .ofPositionDeletes()
        .withPath(path)
        .withPartition(partition)
        .withFileSizeInBytes(fileSize)
        .withFormat(FileFormat.PARQUET)
        .withMetrics(metrics)
        .build();
}
```

差异：第一行从 `table.spec()` 改为 `table.specs().get(dataFile.specId())`；builder 入参由 `table.spec()` 改为复用上面的 `spec` 变量。这是该提交唯一一处源代码改动（2 行变更）。

## 小结

- **成效**：测试工具 `FileGenerationUtil.generatePositionDeleteFile` 在分区演进场景下也能生成与数据文件 specId 一致的位置删除文件，使后续相关测试（如 DV、manifest rewrite 等场景）在多 spec 表上也可靠。
- **影响范围**：仅 `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java` 一个测试工具类、2 行变更，无产品代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是测试工具修复，本身对 1.4.x 运行时无任何影响。**是否回迁取决于 1.4.x 是否需要相关测试基础设施**——如果 1.4.x 上要引入 1335（DV 支持）等测试，则需要此修复作为前置；若仅就本提交单独看，可回迁可不回迁，无运行时风险。回迁无冲突，可直接 cherry-pick。
