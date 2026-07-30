# 提交 3712：Flink: Backport Use native slot sharing group inheritance for maintenance tasks to 2.0 and 1.20 (#16337)

## 提交信息

- **序号**：3712 / 4088
- **哈希**：bf340affbf2d166395df35b8e982625134c315e6
- **短哈希**：bf340affb
- **日期**：2026-05-14 19:01:31 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport Use native slot sharing group inheritance for maintenance tasks to 2.0 and 1.20 (#16337)
- **PR/Issue**：#16337

## 总体目的

这个提交是 PR #16329（提交 3710）向 Flink 2.0 和 1.20 模块的回移植。它将这两个 Flink 版本的维护任务 slot sharing group 配置方式从显式设置默认值改为使用 Flink 原生的 slot sharing group 继承机制，与 Flink 2.1 的实现保持一致。

## 如何达成设计目的

通过与提交 3710 完全相同的修改，将 slot sharing group 的默认值从 `DEFAULT_SLOT_SHARING_GROUP` 改为 `null`，并使用条件设置方式。

## 修改详情

### Flink 2.0 模块 (12 files, +258/-243 lines)

**修改目的**：重构 slot sharing group 配置。

**工作逻辑**：与提交 3710 相同的修改——
- `TableMaintenance.java`：默认 slotSharingGroup 改为 null，使用 `setSlotSharingGroup` 包装
- `MaintenanceTaskBuilder.java`：新增 `setSlotSharingGroup` 辅助方法
- `IcebergSink.java`：仅当配置非 null 时设置
- `DeleteOrphanFiles.java`、`ExpireSnapshots.java`、`RewriteDataFiles.java`、`FlinkMaintenanceConfig.java`：相应适配
- 测试文件更新

### Flink 1.20 模块 (12 files, +258/-243 lines)

**修改目的**：与 Flink 2.0 完全相同的修改。

## 总结

这是提交 3710 向 Flink 2.0 和 1.20 的回移植，内容完全一致。至此，所有三个 Flink 版本（1.20、2.0、2.1）都完成了 slot sharing group 继承机制的重构，确保跨版本一致性。
