# 提交 4003：Docs: Add doc for flink table maintenance ConvertEqualityDeletes (#17113)

## 提交信息

- **序号**：4003 / 4088
- **哈希**：a71f95023184b39f41e643dc5eeb006c554b5428
- **短哈希**：a71f95023
- **日期**：2026-07-09 09:04:46 +0200
- **作者**：GuoYu
- **提交说明**：Docs: Add doc for flink table maintenance ConvertEqualityDeletes (#17113)
- **PR/Issue**：#17113

## 总体目的

本提交为 Flink 表维护（table maintenance）文档新增 `ConvertEqualityDeletes` 任务的详细说明。`ConvertEqualityDeletes` 是一个将源分支（staging branch）上的 equality delete 文件转换为目标分支（target branch）上 deletion vector（DV）的维护任务，通常与 Flink `IcebergSink` 配合使用——`IcebergSink` 将新数据文件和 equality delete 写入 staging 分支，转换器再将 equality delete 解析为基于行位置的 DV，并把数据文件连同 DV 一起提交到目标分支。

这是 Iceberg 表格式 V3（引入 deletion vector）相关功能文档化的一部分，让用户了解如何配置和使用这一维护任务，以及其前置条件、分支语义、状态管理和并发提交注意事项。

## 如何达成设计目的

在 `docs/docs/flink-maintenance.md` 中：
1. 在已有的 `RemoveOrphanFiles` 章节后新增 `#### ConvertEqualityDeletes` 小节，包含任务概述、代码示例和详细的 Notes（前置条件、分支语义、状态与重启、并发提交）。
2. 在配置表格区域新增 `#### ConvertEqualityDeletes Configuration` 配置表，列出 `stagingBranch`、`targetBranch`、`equalityFieldColumns` 三个核心配置项。

## 修改详情

### `docs/docs/flink-maintenance.md` (+55/-0 lines)

**修改目的**：新增 `ConvertEqualityDeletes` 任务文档。

**工作逻辑**：
文档内容涵盖：

**任务概述**：将 staging 分支的 equality delete 转换为 target 分支的 DV，要求表格式版本 >= 3（引入 DV）。pipeline 每次触发运行一个转换周期，内部分为 planner → reader → PK index → DV writer → committer 并行阶段，使用与其它维护任务相同的锁保证互斥。

**代码示例**：
```java
.add(ConvertEqualityDeletes.builder()
    .stagingBranch("staging")
    .equalityFieldColumns(ImmutableList.of("id"))
    .scheduleOnEqDeleteFileCount(10)
    .parallelism(4))
```

**Notes 详细说明**：
- **前置条件与 staging 分支布局**：表格式版本 >= 3（启动时校验）；不支持 row lineage；staging 分支可含新数据文件、equality delete、DV，但禁止 V2 positional delete；staging 分支不得删除数据文件（不能在其上跑 compaction）；`equalityFieldColumns` 必填且须与 writer 一致。
- **分支语义**：staging != target 时只读 staging 不修改；相等时原地作为 eq-delete-to-DV compaction；每次触发只处理最旧的未处理 staging 快照。
- **状态与重启**：PK-index worker 在 Flink state 中维护目标分支的键控行位置索引，跨 checkpoint 增量维护，推荐 RocksDB state backend；`equalityFieldColumns` 不可跨 savepoint 重配置；committer 无状态，通过 marker 属性 `equality-convert-staging-snapshot` 恢复位置。
- **并发提交**：外部提交通过 `RowDelta.validateFromSnapshot` 检测，冲突时下次触发重建索引重试；建议避免目标分支持续并发写入以防 livelock；列出可接受的两种模式。

**配置表**：
| Method | Description | Default |
|---|---|---|
| `stagingBranch(String)` | 源分支（必填） | None |
| `targetBranch(String)` | 目标分支 | `main` |
| `equalityFieldColumns(List<String>)` | 等值字段列（必填，非空） | None |

并附 note 提示要求表格式版本 >= 3。

## 总结

本提交是一次纯文档新增，为 Flink 表维护的 `ConvertEqualityDeletes` 任务提供了完整的用户指南，包括配置、前置条件、分支语义、状态管理、并发注意事项等。这有助于用户正确使用该功能，避免常见陷阱（如在 staging 分支跑 compaction、目标分支持续并发写入导致 livelock 等）。文档质量较高，覆盖了故障恢复和并发场景的关键约束。
