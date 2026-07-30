# 提交 3951：Flink: Add equality delete conversion API and integration tests (#16948)

## 提交信息

- **序号**：3951 / 4088
- **哈希**：6c5f9f242750fe2cade46e5dbd1a90d011741ea6
- **短哈希**：6c5f9f242
- **日期**：2026-06-25 13:43:41 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add equality delete conversion API and integration tests (#16948)
- **PR/Issue**：#16948

## 总体目的

这次提交为 Flink Iceberg 维护框架添加了 `ConvertEqualityDeletes` 维护任务的公共 API 和集成测试。这是 equality delete 转换管道的顶层入口，将 #16889（提交 3936）引入的 `EqualityConvertPlanner` 等算子组装成完整的、用户可调用的维护任务。

背景：Flink 的 upsert 模式写入 equality deletes，这种删除文件生产成本低但迫使每个 reader 在读取时将数据与删除文件做 merge-on-read 连接，影响读取性能。`ConvertEqualityDeletes` 是一个维护任务，将 equality deletes 重写为 deletion vectors（位置删除），使读取时可以通过位置应用删除，显著提升读取性能。写入器持续向 staging 分支追加 equality deletes，转换在后台并行运行。

使用示例：
```java
TableMaintenance.forTable(env, tableLoader)
    .add(
        ConvertEqualityDeletes.builder()
            .stagingBranch("staging")
            .targetBranch("main")
            .equalityFieldColumns(ImmutableList.of("id"))
            .scheduleOnEqDeleteFileCount(1)
            .append();
```

约束条件：
1. 表必须是 V3 格式（支持 deletion vectors）。
2. equality field columns 必须与写入器使用的匹配。
3. 每个分区列必须是 equality field（因为转换器仅按 equality 值 key 行，否则会在错误的分区解析删除）。

## 如何达成设计目的

`ConvertEqualityDeletes.Builder` 继承自 `MaintenanceTaskBuilder`，通过 `append()` 方法构建完整的 Flink DataStream 管道：

1. **Planner (p=1)**：`EqualityConvertPlanner` 扫描 staging 分支，发出文件级 ReadCommand 和阶段 watermark。
2. **Reader (p=N)**：`EqualityConvertReader` 读取文件，发出行级 IndexCommand。
3. **PKIndex (p=N)**：`EqualityConvertPKIndex` 维护 PK 索引分片，将 equality deletes 解析为 DV 位置。
4. **DVWriter (p=N, keyed by data file path)**：`EqualityConvertDVWriter` 按文件分组位置，内联写入 Puffin DV 文件。
5. **Committer (p=1)**：`EqualityConvertCommitter` 将数据文件和 DV 提交到目标分支。
6. **Aggregator (p=1)**：`TaskResultAggregator` 收集错误并发出 TaskResult。

管道还包括上游错误处理（reader 中止信号绕过 PKIndex 直接馈送 DVWriter）、广播流（planner 到 PKIndex 的清理信号、metadata 到 DVWriter）等机制。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ConvertEqualityDeletes.java` (+286 lines, 新文件)

**修改目的**：新增 ConvertEqualityDeletes 维护任务 API。

**工作逻辑**：
- `Builder` 类继承 `MaintenanceTaskBuilder`，提供 `stagingBranch`、`targetBranch`、`equalityFieldColumns` 配置。
- `resolveEqualityFieldIds()` 方法在构建时加载表，校验 V3 格式，将列名解析为字段 ID 集合。
- `append()` 方法组装完整管道：Planner → Reader → PKIndex → DVWriter → Committer → Aggregator，设置并行度、slot sharing group、uid 等。
- 包含上游错误处理：reader 错误通过 side output 发出 abort 信号，PKIndex 和 DVWriter 的错误也汇聚到 aggregator。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertCommitter.java` (+2/-1 line)

**修改目的**：适配 committer 的变更。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPlanner.java` (+20/-0 lines)

**修改目的**：为 planner 添加支持。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestConvertEqualityDeletes.java` (+1333 lines, 新文件)

**修改目的**：全面的单元测试。

**工作逻辑**：覆盖 API 构建、配置校验（V3 格式、字段存在性、分区列约束）、管道拓扑验证等。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestConvertEqualityDeletesE2E.java` (+170 lines, 新文件)

**修改目的**：端到端集成测试。

**工作逻辑**：构造实际表、写入 equality deletes、运行转换任务、验证 DV 生成和读取结果正确性。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestMaintenanceE2E.java` (+33/-0 lines)

**修改目的**：在维护框架 E2E 测试中添加 equality delete 转换场景。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertPlanner.java` (+40/-0 lines)

**修改目的**：补充 planner 测试。

## 总结

这次提交为 Flink Iceberg 维护框架添加了 `ConvertEqualityDeletes` 维护任务的公共 API，将先前引入的算子（planner、reader、PKIndex、DVWriter、committer）组装成完整的 equality delete 到 deletion vector 的转换管道。配有 1500+ 行的全面测试（单元测试和端到端集成测试）。该 API 随后在 #16969 中被 backport 到 Flink 1.20 和 2.0。
