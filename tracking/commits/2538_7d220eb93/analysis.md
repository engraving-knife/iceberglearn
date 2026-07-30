# 提交 2538：Flink: Supports delete orphan files in TableMaintenance (#13302)

## 提交信息

- **序号**：2538 / 4088
- **哈希**：7d220eb93815c8e5a0947571005a251f3deadd49
- **短哈希**：7d220eb93
- **日期**：2025-08-21 16:52:54 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Supports delete orphan files in TableMaintenance (#13302)
- **PR/Issue**：#13302

## 总体目的

Iceberg 的 `RemoveOrphanFiles` action 用于清理文件系统中存在、但表元数据不再引用的孤儿文件（或phan files），防止存储浪费。之前 Spark 有对应 action，但 Flink 的 maintenance 流式任务体系中没有等价能力，无法在 Flink 流式维护管道里自动清理孤儿文件。

本提交在 Flink v2.0 maintenance 框架中新增 `DeleteOrphanFiles` 任务，构建一条流式 pipeline：
- 用触发器（Trigger）驱动；
- 并行走两条路径：一条列举表元数据引用的所有文件（数据文件 + metadata 文件），另一条递归列举文件系统目录下所有候选文件；
- 通过对两路文件 URI 做反连接（anti-join），找出文件系统中存在但表未引用的文件；
- 在确认无错误后批量删除孤儿文件，并通过 `TaskResultAggregator` 输出任务结果。

同时考虑了 URI 标准化（s3/s3a/s3n 等价 scheme、不同 authority 等价）、prefix mismatch 处理策略、最小文件年龄保护（避免删除正在写入的文件）、错误隔离（任意上游出错时停止删除）、prefix listing 优化等工程细节。

这是一个 2000+ 行的大型功能提交，包含 8 个新算子 + 1 个 builder + 6 个测试类。

## 如何达成设计目的

整个 pipeline 在 `DeleteOrphanFiles.Builder.append(DataStream<Trigger>)` 中编排：

1. **元数据文件路径采集**（两条子流）：
   - `MetadataTablePlanner`：把 Trigger 转成对 `ALL_FILES` 元数据表的 scan split，单并行。
   - `FileNameReader`（继承 `TableReader`）：rebalance 后并行读取 split，提取每条记录的 `file_path`，输出数据文件路径流。
   - `ListMetadataFiles`：单独列举表自身引用的 metadata 文件（metadata.json、manifest list、manifest、snapshot 等），输出 metadata 文件路径流。
   - 两路路径通过 `union` 合并为"表引用文件"流。
2. **文件系统候选文件采集**：
   - `ListFileSystemFiles`：基于 `FileSystemWalker` 递归列举 `location` 目录，过滤隐藏文件和年龄小于 `minAge`（默认 3 天）的文件，支持 `usePrefixListing` 走 `SupportsPrefixOperations` 加速。输出候选文件流。
3. **反连接找孤儿**：
   - `FileUriKeySelector`：把文件 URI 按等价 scheme/authority 标准化为统一 key（如 `s3a://host/path` 与 `s3://host/path` 视为相同）。
   - "表引用文件"流与"候选文件"流分别 `keyBy` 后 `connect` 进入 `OrphanFilesDetector`（`KeyedCoProcessFunction`）：
     - 用 `MapState` 记录表引用过的 URI；
     - 当候选文件到达时检查是否在表引用集合中，不在则视为孤儿输出；
     - 处理 URI 解析错误并按 `PrefixMismatchMode`（ERROR/IGNORE/DELETE）决策。
4. **错误隔离**：
   - 所有算子通过 side output `ERROR_STREAM` 上报异常。
   - 汇总错误流与待删除文件流 `connect` 进入 `SkipOnError`：一旦有错误则置位 `hasErrorFlag`，丢弃后续待删文件，保证不会在元数据列举失败时误删有效文件。
5. **删除**：
   - `DeleteFilesProcessor`（已有算子）按 `deleteBatchSize`（默认 1000）批量删除文件。
6. **结果聚合**：
   - `TaskResultAggregator` 把 Trigger 与错误流 connect，输出 `TaskResult`，标识本次触发是否成功。

Builder 提供 `location`、`minAge`、`usePrefixListing`、`prefixMismatchMode`、`equalSchemes`、`equalAuthorities`、`planningWorkerPoolSize`、`deleteBatchSize` 等可配置项。`equalSchemes`/`equalAuthorities` 支持逗号分隔多 key（如 `"s3a,s3,s3n" -> "s3"`），通过 `flattenMap` 展开。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/DeleteOrphanFiles.java` (+324)

**修改目的**：维护任务入口与 pipeline 编排。

**工作逻辑**：见上文 pipeline 描述。`Builder` 继承 `MaintenanceTaskBuilder`，在 `append` 中依次构建 planner、reader、metadata files lister、fs files lister、orphan detector、skip-on-error、delete processor、aggregator，并通过 `uid`/`name`/`slotSharingGroup`/`parallelism` 配置算子拓扑。定义了 `ERROR_STREAM` OutputTag 供所有算子上报异常。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/MetadataTablePlanner.java` (+133)

**修改目的**：将 Trigger 转成元数据表 scan split。

**工作逻辑**：`ProcessFunction<Trigger, SplitInfo>`，使用 `FlinkSplitPlanner` 规划 `ALL_FILES` 元数据表的 splits，序列化后包装为 `SplitInfo`（含 split 序列化字节与分片数），输出供下游 reader 消费。支持 worker pool 并行规划。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableReader.java` (+120)

**修改目的**：读取元数据表 split 并输出记录的抽象基类。

**工作逻辑**：`ProcessFunction<SplitInfo, R>`，反序列化 split，用 `MetaDataReaderFunction` 创建 `DataIterator`，逐条调用子类 `extract(RowData, Collector<R>)` 抽取目标字段。处理 checkpoint 与异常上报。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/FileNameReader.java` (+49)

**修改目的**：从 `ALL_FILES` 元数据表读取数据文件的 `file_path`。

**工作逻辑**：继承 `TableReader<String>`，`extract` 取 RowData 第 0 列（`file_path`）输出。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ListMetadataFiles.java` (+93)

**修改目的**：列举表自身引用的 metadata 文件。

**工作逻辑**：`ProcessFunction<Trigger, String>`，加载 Table 后遍历 `metadataLocation`、所有 snapshot 的 manifest-list、manifest、data files 路径以及 metadata.json 本身，输出供反连接使用。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ListFileSystemFiles.java` (+133)

**修改目的**：递归列举文件系统候选文件。

**工作逻辑**：用 `FileSystemWalker` 遍历 `location`，跳过隐藏文件和年龄 < `minAgeMs` 的文件。若 `usePrefixListing` 且 `FileIO` 支持 `SupportsPrefixOperations`，则用 prefix listing 加速；否则递归列举。输出候选文件 URI。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/FileUriKeySelector.java` (+60)

**修改目的**：URI 标准化作为 key。

**工作逻辑**：`KeySelector<String, String>`，把 URI 的 scheme 与 authority 按 `equalSchemes`/`equalAuthorities` 映射为规范形式，返回规范化的 path key，使不同 scheme/authority 的同路径文件能 join 到一起。解析失败的 URI 返回 `__INVALID_URI__`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/OrphanFilesDetector.java` (+191)

**修改目的**：反连接找孤儿文件。

**工作逻辑**：`KeyedCoProcessFunction<String, String, String, String>`：
- `processElement1`（表引用侧）：把 URI 存入 `MapState foundInTable`。
- `processElement2`（文件系统侧）：检查 URI 是否在 `foundInTable`，不在则输出为孤儿。同时用 `foundInFileSystem` 状态暂存当前 key 的 fs 文件，便于后续校验。
- 处理 URI 解析错误，按 `PrefixMismatchMode` 决定 ERROR（抛 ValidationException）、IGNORE（跳过）、DELETE（删除）。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/SkipOnError.java` (+90)

**修改目的**：错误隔离，出错时停止删除。

**工作逻辑**：`TwoInputStreamOperator<String, Exception, String>`，用 `ListState` 缓存待删文件、`hasError` 状态记录是否出错。一旦错误流到达，置 `hasErrorFlag=true`，后续待删文件直接丢弃；checkpoint 时清空缓存。

### 测试文件（+1037）

- `MaintenanceTaskTestBase.java`：扩展基类支持新任务。
- `TestDeleteOrphanFiles.java`（+340）：端到端验证 orphan 清理、minAge 保护、错误隔离、URI 等价等场景。
- `OperatorTestBase.java`、`TestListFileSystemFiles.java`、`TestListMetadataFiles.java`、`TestOrphanFilesDetector.java`、`TestSkipOnError.java`、`TestTablePlanerAndReader.java`：分别覆盖各算子单元行为。

## 总结

在 Flink v2.0 maintenance 框架中实现了流式 `DeleteOrphanFiles` 任务，通过"表引用文件 + 文件系统候选文件 → URI 标准化 keyBy → 反连接检测孤儿 → 错误隔离 → 批量删除"的 pipeline，让 Flink 用户能在流式维护管道中自动清理孤儿文件。设计上充分考虑了 URI 等价、prefix mismatch 策略、minAge 保护、错误隔离与 checkpoint 一致性，并通过 8 个算子与 6 个测试类提供完整实现与覆盖。后续 PR #13887 会将该能力 backport 到 Flink 1.19/1.20。
