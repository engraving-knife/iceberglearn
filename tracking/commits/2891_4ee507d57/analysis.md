# 提交 2891：Flink: DynamicSink support dvs (#14414)

## 提交信息

- **序号**：2891 / 4088
- **哈希**：4ee507d5788e31c74d5ef77204ef126ae0105981
- **短哈希**：4ee507d57
- **日期**：2025-11-19 09:44:00 +0100
- **作者**：GuoYu
- **提交说明**：Flink: DynamicSink support dvs (#14414)
- **PR/Issue**：#14414

## 总体目的

Iceberg 表格式版本 3（V3）引入了 Deletion Vectors（DV，删除向量）的特性。DV 是一种更高效的行级删除机制，将位置删除信息以紧凑的向量化形式存储。在 V3 表中，position delete 文件应当是 DV 格式而非传统的位置删除文件。

此前 Flink 的 `DynamicIcebergSink`（动态 sink，支持向多个表写入）存在两个限制：

1. `DynamicWriter` 中明确禁止在 V3+ 表上使用 upsert 模式，这意味着动态 sink 无法利用 V3 的 DV 能力。
2. `DynamicWriteResultAggregator` 在写入 DeltaManifests 时，将 manifest 的格式版本硬编码为 2，无法正确处理 V3 表的 delete 文件。
3. `DynamicCommitter` 没有对 position delete 文件是否为 DV 进行校验，无法防止并发表升级导致的格式不一致问题。

本提交的目标是让动态 sink 支持 DV（V3 格式），使 upsert 模式能在 V3 表上工作，并增加校验逻辑防止在表升级过程中出现不兼容的 delete 文件。

## 如何达成设计目的

整体设计分三个层面：

1. **Writer 层**：移除 `DynamicWriter` 中对 V3+ 表 upsert 模式的禁止检查，允许在 V3 表上使用 upsert（从而生成 DV）。

2. **Aggregator 层**：`DynamicWriteResultAggregator` 之前将 manifest 格式版本硬编码为 2，现在改为根据表的实际 format version 动态获取。通过将 `outputFileFactories` 缓存改为缓存 `Tuple2<ManifestOutputFileFactory, Integer>`（工厂+格式版本），在写入 DeltaManifests 时使用正确的格式版本。

3. **Committer 层**：`DynamicCommitter` 在读取 deltaManifests 后，如果表格式版本 > 2，则校验所有 position delete 文件必须是 DV（通过 `ContentFileUtil.isDV`），否则抛出异常，防止在表升级到 V3 的过程中提交非 DV 的 position delete 文件。

## 修改详情

### `docs/docs/flink-writes.md` (+1/-0 lines)

**修改目的**：在文档中补充说明动态 sink 不支持在表格式升级过程中运行。

**工作逻辑**：新增一条说明，告知用户动态 sink 不支持升级带有动态记录的表，V2 到 V3 的升级过程中不应运行作业。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (+18/-2 lines)

**修改目的**：在 commit 阶段校验 V3+ 表的 position delete 文件是否为 DV，防止并发表升级导致格式不一致。

**工作逻辑**：在 `apply` 方法（处理 commitRequests）中，读取 DeltaManifests 后，如果 `TableUtil.formatVersion(table) > 2`，遍历所有 delete 文件，对于 `POSITION_DELETES` 类型的文件，使用 `ContentFileUtil.isDV(deleteFile)` 校验是否为 DV。如果不是 DV，抛出 `IllegalArgumentException`，提示"不支持并发表升级到 V3"。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriteResultAggregator.java` (+22/-14 lines)

**修改目的**：将 manifest 格式版本从硬编码的 2 改为根据表实际 format version 动态获取，以支持 V3 表的 DV。

**工作逻辑**：
- 将成员变量 `outputFileFactories`（`Map<String, ManifestOutputFileFactory>`）改为 `outputFileFactoriesAndFormatVersions`（`Map<String, Tuple2<ManifestOutputFileFactory, Integer>>`），同时缓存工厂和格式版本。
- 方法 `outputFileFactory` 改名为 `outputFileFactoryAndFormatVersion`，返回 `Tuple2`，在创建工厂时同时记录 `TableUtil.formatVersion(table)`。
- 在 `writeToManifest` 中，使用 `outputFileFactoryAndVersion.f0` 获取工厂、`outputFileFactoryAndVersion.f1` 获取格式版本，传给 `FlinkManifestUtil.writeCompletedFiles`（之前硬编码为 2）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+0/-5 lines)

**修改目的**：移除对 V3+ 表 upsert 模式的禁止检查，允许动态 sink 在 V3 表上使用 upsert。

**工作逻辑**：删除了 `Preconditions.checkArgument(!(TableUtil.formatVersion(table) > 2), "Dynamic Sink writer does not support upsert mode in tables (V3+)")` 这段代码及其相关的 `TableUtil` import。这意味着 V3 表现在可以使用 upsert 模式，生成的 position delete 将是 DV 格式。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicCommitter.java` (+137/-0 lines)

**修改目的**：测试在不同格式版本下提交 delete 文件的行为。

**工作逻辑**：新增两个测试：
1. `testCommitDeleteInDifferentFormatVersion`：在 V2 表上写入包含 delete 文件的 manifest，然后将表升级到 V3，再执行 commit，验证抛出 `IllegalArgumentException`（因为 V3 表不接受非 DV 的 position delete）。
2. `testCommitOnlyDataInDifferentFormatVersion`：在 V2 表上写入仅含 data 文件的 manifest，升级到 V3 后 commit 成功，验证只含数据文件的提交不受格式版本影响。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+205/-0 lines)

**修改目的**：集成测试验证 V3 表的 upsert 功能和多格式版本混合写入。

**工作逻辑**：新增两个测试：
1. `testUpsertV3`：创建 V3 表，执行 upsert（插入一条记录后跟三条重复记录），验证最终表中只有一条记录（去重生效，DV 工作）。
2. `testMultiFormatVersion`：同时创建 V3 表和 V2 表，向两个表写入 upsert 数据，验证两个表都能正确去重到一条记录。这证明了动态 sink 可以在同一作业中同时处理不同格式版本的表。

## 总结

本提交为 Flink `DynamicIcebergSink` 添加了对 Iceberg V3 表 DV（删除向量）的支持。通过移除 upsert 限制、动态获取格式版本、增加 commit 校验三个层面的修改，使动态 sink 能够在 V3 表上使用高效的 DV 删除机制。同时通过校验逻辑防止了并发表升级时的格式不一致问题。此修改针对 Flink 2.1 版本，后续提交 2891 将其 backport 到 Flink 2.0 和 1.20。
