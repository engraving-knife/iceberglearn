# 提交 0737：Flink: Apply DeleteGranularity for writes

## 提交信息
- **序号**：0737 / 4088
- **哈希**：1757577937d0e9c385ace059b8e8f7f1f75d5fc1
- **短哈希**：175757793
- **日期**：2024-04-30 22:52:48 +0200
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Apply DeleteGranularity for writes (#10200)
- **PR/Issue**：#10200

## 总体目的

本提交为 Flink 写入路径引入 `DeleteGranularity`（删除粒度）配置能力，使 Flink sink 在写入位置删除（position delete）时能够选择按"文件粒度"或"分区粒度"组织删除文件。同时修复了 `SortingPositionOnlyDeleteWriter` 在处理空路径集合时的边界缺陷。

### 背景与设计动机

在 Iceberg 中，UPSERT（更新插入）操作通过 equality delete（相等删除）+ position delete（位置删除）实现。当写入一条新记录覆盖已有记录时，需要先写入一条 position delete 标记旧记录的行位置，再写入新数据行。这些 position delete 会被收集到删除文件中。

`DeleteGranularity` 枚举定义了两种删除文件组织策略：

1. **PARTITION（分区粒度）**：将一个分区内所有数据文件的 position delete 合并写入同一个删除文件。优点是删除文件数量少；缺点是扫描单个数据文件时需要读取包含其他数据文件删除信息的删除文件，造成额外读取开销（虽然读取侧会丢弃无关删除，但仍有 I/O 开销）。

2. **FILE（文件粒度）**：为每个被引用的数据文件单独创建一个删除文件。优点是作业规划和读取都更精确，读取侧只加载必要的删除信息；缺点是删除文件数量增多，可能需要更激进的删除文件压缩（compaction）。

在此提交之前，`BaseTaskWriter` 中的 `BaseEqualityDeltaWriter` 使用的是旧的 `SortedPosDeleteWriter`，它只支持分区粒度（将所有删除合并到一个文件），无法选择文件粒度。本提交将其替换为功能更完善的 `SortingPositionOnlyDeleteWriter`，后者已内置对两种粒度的支持，从而使 Flink 等上层引擎可以选择最适合的粒度。

## 如何达成设计目的

### 核心思路

将 `BaseEqualityDeltaWriter` 中使用的位置删除写入器从 `SortedPosDeleteWriter`（仅支持分区粒度）切换为 `SortingPositionOnlyDeleteWriter`（支持 FILE 和 PARTITION 两种粒度），并通过构造函数参数 `DeleteGranularity` 让上层调用方决定粒度。

### 工作原理详解

**1. `SortingPositionOnlyDeleteWriter` 的两种粒度工作方式**

该写入器在内存中用 `Roaring64Bitmap`（高性能压缩位图）按数据文件路径维护被删除的行位置。在 `close()` 时根据粒度选择输出策略：

- **PARTITION 粒度**（`writePartitionDeletes()`）：将所有数据文件的删除位置一起排序后写入同一个底层 writer，产出一个删除文件。不同数据文件的删除记录混合在同一文件中，但按 (path, pos) 排序满足 Iceberg 规范要求。

- **FILE 粒度**（`writeFileDeletes()`）：遍历每个数据文件路径，分别为其调用 `writeDeletes(ImmutableList.of(path))`，每个数据文件产出独立的删除文件。这样每个删除文件只包含单一数据文件的删除信息，读取侧可精确定位。

**2. 为什么需要 Supplier 而非单个 writer**

原 `SortedPosDeleteWriter` 在构造时接收单个 appenderFactory/fileFactory，内部自行创建 writer。新的 `SortingPositionOnlyDeleteWriter` 接收一个 `Supplier<FileWriter>`，因为 FILE 粒度下需要为每个数据文件创建独立的底层 writer（每次调用 `writers.get()` 返回新实例），而 PARTITION 粒度只需一个 writer。Supplier 模式统一了两种粒度对 writer 的获取方式。

**3. Flink 选择 FILE 粒度的原因**

Flink sink 的 `RowDataDeltaWriter` 构造时传入 `DeleteGranularity.FILE`。Flink 流式写入场景下，每个 checkpoint 内同一 task 处理的数据通常对应少数数据文件，按文件粒度组织删除文件可以让 Flink 的下游读取（如下游任务读取该表）更高效地裁剪无关删除文件，避免读取放大。代价是删除文件数量可能增多，但 Flink 通常有定期的 small file compaction 机制来治理。

**4. 空路径边界修复**

`SortingPositionOnlyDeleteWriter.writeDeletes(Collection<CharSequence> paths)` 方法在 FILE 粒度下会被逐个路径调用。若在极端情况下 paths 为空（例如调用了 `writeFileDeletes()` 但 `positionsByPath` 为空），原代码会继续调用 `writers.get()` 创建一个空删除文件。本提交在方法入口加入空集合短路检查，直接返回空的 `DeleteWriteResult`，避免产出无意义的空删除文件。

### 实现步骤

1. 在 `BaseEqualityDeltaWriter` 中引入 `PositionDelete` 复用对象和 `DeleteGranularity` 参数。
2. 新增带 `DeleteGranularity` 的构造函数，保留原构造函数默认使用 `PARTITION` 粒度以保持向后兼容。
3. 将 `posDeleteWriter` 的类型从 `SortedPosDeleteWriter<T>` 改为 `FileWriter<PositionDelete<T>, DeleteWriteResult>`（`SortingPositionOnlyDeleteWriter` 实现的接口），并改用 `SortingPositionOnlyDeleteWriter` 构造。
4. 抽取 `writePosDelete(PathOffset)` 和 `newOutputFile(StructLike)` 辅助方法，简化调用并适配新 writer 的接口。
5. 修改 `close()` 逻辑，从旧的 `posDeleteWriter.complete()`/`referencedDataFiles()` 改为 `posDeleteWriter.close()` + `result()` 模式，匹配 `FileWriter` 接口契约。
6. Flink `BaseDeltaTaskWriter.RowDataDeltaWriter` 显式传入 `DeleteGranularity.FILE`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`
**修改目的**：修复空路径集合的边界缺陷，避免产出空删除文件。

**具体改动**：在 `writeDeletes(Collection<CharSequence> paths)` 方法入口增加空集合检查：
```java
if (paths.isEmpty()) {
  return new DeleteWriteResult(Lists.newArrayList(), CharSequenceSet.empty());
}
```
该方法在 FILE 粒度下被逐路径调用，也在 PARTITION 粒度下被整体调用。短路返回避免了对 `writers.get()` 的无谓调用和空文件产出。

### `core/src/main/java/org/apache/iceberg/io/BaseTaskWriter.java`
**修改目的**：将位置删除写入器切换为 `SortingPositionOnlyDeleteWriter`，引入 `DeleteGranularity` 配置能力。

**具体改动**：

1. **新增 import**：引入 `DeleteGranularity`、`PositionDelete`、`SortingPositionOnlyDeleteWriter`。

2. **`BaseEqualityDeltaWriter` 字段变更**：
   - 新增 `private final PositionDelete<T> positionDelete` 字段，作为可复用的位置删除记录对象，避免每次删除都创建新对象。
   - `posDeleteWriter` 类型从 `SortedPosDeleteWriter<T>` 改为 `FileWriter<PositionDelete<T>, DeleteWriteResult>`。

3. **构造函数重构**：
   - 保留原三参数构造函数 `BaseEqualityDeltaWriter(StructLike, Schema, Schema)`，内部委托给新构造函数并传入 `DeleteGranularity.PARTITION` 作为默认值，保持向后兼容。
   - 新增四参数构造函数 `BaseEqualityDeltaWriter(StructLike, Schema, Schema, DeleteGranularity)`，在其中用 `SortingPositionOnlyDeleteWriter` 替换 `SortedPosDeleteWriter`：
     ```java
     this.posDeleteWriter =
         new SortingPositionOnlyDeleteWriter<>(
             () -> appenderFactory.newPosDeleteWriter(newOutputFile(partition), format, partition),
             deleteGranularity);
     ```
     其中 `Supplier` 通过 lambda 延迟创建底层 writer，适配 FILE 粒度下需要多实例的场景。

4. **新增辅助方法**：
   - `newOutputFile(StructLike partition)`：根据是否分区选择 `fileFactory.newOutputFile()` 或 `fileFactory.newOutputFile(spec, partition)`，封装输出文件创建逻辑。
   - `writePosDelete(PathOffset pathOffset)`：封装 `positionDelete.set(path, offset, null)` + `posDeleteWriter.write(positionDelete)`，统一两处删除调用点（`delete` 方法和 `deleteKey` 方法）的逻辑。

5. **`close()` 方法适配**：从旧的 `posDeleteWriter.complete()` + `posDeleteWriter.referencedDataFiles()` 改为 `posDeleteWriter.close()` + `result().deleteFiles()` + `result().referencedDataFiles()`，匹配 `FileWriter` 接口（`SortingPositionOnlyDeleteWriter` 通过 `close()` 触发实际写入并通过 `result()` 返回结果）。

### `data/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java`
**修改目的**：适配新的 `DeleteGranularity` 参数并新增粒度测试。

**具体改动**：

1. **`TARGET_FILE_SIZE` 调整**：从 `128 * 1024 * 1024L`（128MB）改为 `128L`，目的是在测试中用极小的目标文件大小触发滚动写入（rolling write），从而产生多个数据文件，以验证 FILE 粒度下删除文件数量与数据文件数量一致的预期。

2. **所有 `createTaskWriter` 调用增加参数**：每个调用点增加 `DeleteGranularity.PARTITION` 参数，保持原有测试行为不变。

3. **`createTaskWriter` 方法签名扩展**：增加 `DeleteGranularity deleteGranularity` 参数，透传到 `GenericTaskDeltaWriter` 及 `GenericEqualityDeltaWriter`。

4. **新增 `testDeleteFileGranularity()` 和 `testDeletePartitionGranularity()` 测试**：通过 `withGranularity(DeleteGranularity)` 私有方法参数化测试两种粒度。测试写入 2000 条记录（触发 2 个数据文件），其中约 598 条被删除，验证：
   - 数据文件数为 2（因 `ROWS_DIVISOR=1000` 触发滚动）。
   - FILE 粒度下删除文件数为 2（每个数据文件一个），PARTITION 粒度下为 1（合并）。
   - 删除文件的总记录数等于预期删除数。
   - 提交后表数据正确。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/BaseDeltaTaskWriter.java`
**修改目的**：让 Flink sink 使用 FILE 粒度写入位置删除。

**具体改动**：
- 引入 `DeleteGranularity` import。
- `RowDataDeltaWriter` 构造函数调用从 `super(partition, schema, deleteSchema)` 改为 `super(partition, schema, deleteSchema, DeleteGranularity.FILE)`，显式选择文件粒度。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`
**修改目的**：验证 Flink sink 在 FILE 粒度下删除文件的统计信息正确性。

**具体改动**：
1. 新增 `globalTimeout = Timeout.seconds(60)` 规则，防止测试卡死。
2. 新增 `testDeleteStats()` 测试：写入 `+I(1,aaa)`、`-D(1,aaa)`、`+I(1,aaa)` 序列（先插入、再删除、再插入），验证：
   - 删除文件的 `lowerBounds` 中 `DELETE_FILE_PATH` 字段值等于对应数据文件路径，证明 FILE 粒度下删除文件正确关联到具体数据文件，统计信息（下界）可精确到文件级别。
   - 通过 `assumeThat(format).isNotEqualTo(FileFormat.AVRO)` 跳过 Avro 格式（Avro 不支持下界统计）。

## 小结

- **成效**：成功为写入路径引入 `DeleteGranularity` 能力。Flink sink 现使用 FILE 粒度，使每个删除文件精确对应单个数据文件，优化了读取侧的删除文件裁剪效率，减少无关删除信息的读取。Core 层的 `BaseEqualityDeltaWriter` 默认保持 PARTITION 粒度以维持向后兼容。同时修复了空路径边界问题，避免产出空删除文件。
- **影响范围**：
  - **core 模块**：`SortingPositionOnlyDeleteWriter`（边界修复）、`BaseTaskWriter`（写入器切换 + 接口适配）。影响所有继承 `BaseTaskWriter` 的写入器（包括 Spark、Flink、Core 通用写入器），但由于默认粒度为 PARTITION，非 Flink 调用方行为不变。
  - **data 模块**：测试适配与新增。
  - **flink v1.18 模块**：`BaseDeltaTaskWriter` 切换为 FILE 粒度，新增测试。
  - **行为变更**：Flink sink 的删除文件组织方式从分区级变为文件级，删除文件数量可能增多，但读取效率提升。需要关注下游是否有依赖删除文件数量的逻辑。
- **回迁到 1.4.x 的注意事项**：
  - 需确认 1.4.x 分支的 `SortingPositionOnlyDeleteWriter` 是否已具备 `DeleteGranularity` 构造函数和 FILE/PARTITION 分支逻辑。若 1.4.x 的该类较旧（仅有单参数构造函数），则需先回迁 `SortingPositionOnlyDeleteWriter` 的粒度支持能力。
  - 需确认 1.4.x 的 `BaseTaskWriter.BaseEqualityDeltaWriter` 是否仍使用 `SortedPosDeleteWriter`。若是，则本提交的写入器切换改动可直接应用；若已有其他变体，需相应调整。
  - 需确认 1.4.x 支持的 Flink 版本目录结构（本提交针对 v1.18，1.4.x 可能支持不同 Flink 版本如 v1.17/v1.18/v1.19/v1.20），需对每个 Flink 版本目录分别应用 `BaseDeltaTaskWriter` 的改动。
  - FILE 粒度会增加删除文件数量，回迁后需确认 1.4.x 的 Flink sink 是否有配套的 small file / delete file compaction 机制，避免小文件过多问题。
