# 提交 3409：Flink: Backport: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566) (#15667)

## 提交信息

- **序号**：3409 / 4088
- **哈希**：652691c2ab06176f16f1db418d87d70cd94b176a
- **短哈希**：652691c2ab
- **日期**：2026-03-17 10:31:34 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566) (#15667)
- **PR/Issue**：#15667（backport of #15566）

## 总体目的

这是提交 3406（PR #15566）的 backport，将相同的改动应用到 Flink 的另一个版本分支。原始 PR 为 Flink IcebergSink 增加了通过 Builder 配置任意提交后维护任务的能力（ExpireSnapshots、DeleteOrphanFiles 等），本 backport 将相同功能应用到 Flink 的另一个版本目录。

## 如何达成设计目的

- 将 PR #15566 的所有改动复制到 Flink 的另一个版本分支目录
- 包含完全相同的文件和改动内容：IcebergSink 重构、新增配置类、Builder API 扩展等
- 修改的文件列表与原始 PR 完全对应，仅路径前缀不同

## 修改详情

本 backport 包含与提交 3406 完全相同的改动，涉及以下文件（每个 Flink 版本一份）：

### 主要源文件
- `FlinkWriteConf.java` (+18 lines)：新增 expireSnapshotsMode() 和 deleteOrphanFilesMode()
- `FlinkWriteOptions.java` (+16/-1 lines)：新增维护任务开关选项，统一配置 key 前缀
- `DeleteOrphanFiles.java` (+26/-8 lines)：Builder 新增 config 方法，提取默认常量
- `DeleteOrphanFilesConfig.java` (+216 lines, 新文件)：孤立文件删除配置类
- `ExpireSnapshots.java` (+14 lines)：Builder 新增 config 方法
- `ExpireSnapshotsConfig.java` (+151 lines, 新文件)：过期快照配置类
- `FlinkMaintenanceConfig.java` (+8 lines)：新增配置工厂方法
- `TableMaintenance.java` (+12 lines)：新增批量添加任务方法
- `IcebergCommitter.java` (+8/-4 lines)：compactMode 重命名为 tableMaintenanceEnabled
- `IcebergSink.java` (+147/-29 lines)：重构支持多维护任务

### 测试文件
- `TestDeleteOrphanFilesConfig.java` (+91 lines, 新文件)
- `TestExpireSnapshotsConfig.java` (+84 lines, 新文件)
- `TestIcebergSinkTableMaintenance.java` (+167/-1 lines, 重命名)

## 总结

本提交是 PR #15566 的 backport，将 Flink IcebergSink 多维护任务支持功能应用到 Flink 的另一个版本分支。改动内容与原始 PR 完全一致，详见提交 3406 的分析。
