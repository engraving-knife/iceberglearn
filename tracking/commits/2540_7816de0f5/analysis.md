# 提交 2540：Flink: Backport supports delete orphan files in TableMaintenance to 1.19 and 1.20 (#13887)

## 提交信息

- **序号**：2540 / 4088
- **哈希**：7816de0f50d29923492158f4622eb5f85e81ec33
- **短哈希**：7816de0f5
- **日期**：2025-08-21 22:03:53 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport supports delete orphan files in TableMaintenance to 1.19 and 1.20 (#13887)
- **PR/Issue**：#13887（backport of #13302）

## 总体目的

#13302 为 Flink v2.0 maintenance 框架实现了 `DeleteOrphanFiles` 任务（流式清理孤儿文件）。但 Iceberg 的 Flink 集成同时维护 1.19 与 1.20 两个老版本分支，这两个版本同样需要该维护能力。本提交把 #13302 的全部代码（8 个算子 + 1 个 builder + 6 个测试类）原样 backport 到 `flink/v1.19` 与 `flink/v1.20` 两个模块，让 1.19/1.20 用户也能在流式维护管道中清理孤儿文件。

由于 1.19/1.20 仍使用 Flink 旧版本（状态 API 为 v1），backport 直接采用 v1 状态 API（`org.apache.flink.api.common.state.ListState`），避免了 #13302 在 v2.0 上遇到的 v2/v1 混用问题（参见 #13888）。也就是说，本 backport 的 `SkipOnError` 一开始就用了正确的 v1 import。

## 如何达成设计目的

- 在 `flink/v1.19/flink` 与 `flink/v1.20/flink` 下分别复制 v2.0 的对应文件，保持包路径与类名一致：
  - `maintenance/api/DeleteOrphanFiles.java`
  - `maintenance/operator/` 下 8 个算子（`MetadataTablePlanner`、`TableReader`、`FileNameReader`、`ListMetadataFiles`、`ListFileSystemFiles`、`FileUriKeySelector`、`OrphanFilesDetector`、`SkipOnError`）
- 测试类同步 backport：`MaintenanceTaskTestBase`、`TestDeleteOrphanFiles`、`OperatorTestBase`、`TestListFileSystemFiles`、`TestListMetadataFiles`、`TestOrphanFilesDetector`、`TestSkipOnError`、`TestTablePlanerAndReader`。
- pipeline 设计、Builder 配置项、错误隔离、URI 标准化等逻辑与 #13302 完全一致，详见提交 2538 的分析。

## 修改详情

### `flink/v1.19/...` 与 `flink/v1.20/...` 下新增 17 个文件 ×2 模块（+4454/-10）

**修改目的**：把 `DeleteOrphanFiles` 维护任务及其算子、测试 backport 到 1.19 与 1.20。

**工作逻辑**：与 #13302（v2.0）实现一致：
- `DeleteOrphanFiles.Builder.append` 编排 pipeline：Trigger → MetadataTablePlanner → FileNameReader（数据文件路径）+ ListMetadataFiles（metadata 文件路径）合并为"表引用文件"流；同时 ListFileSystemFiles 递归列举文件系统候选文件；两路按 `FileUriKeySelector` 标准化 key 后 connect 进入 `OrphanFilesDetector` 做反连接输出孤儿文件；经 `SkipOnError` 错误隔离后由 `DeleteFilesProcessor` 批量删除；`TaskResultAggregator` 汇总结果。
- `SkipOnError` 使用 v1 状态 API（`org.apache.flink.api.common.state.ListState`），与 v1 算子基类一致，避免 v2.0 上的状态 API 混用问题。

## 总结

将 #13302 在 Flink v2.0 实现的 `DeleteOrphanFiles` 流式孤儿文件清理任务完整 backport 到 Flink 1.19 与 1.20 模块，复制 8 个算子 + 1 个 builder + 6 个测试类到两个版本目录。1.19/1.20 直接采用 v1 状态 API，规避了 v2.0 上后续 #13888 修复的 v2/v1 混用问题，让老版本用户也能使用流式 orphan file 清理能力。
