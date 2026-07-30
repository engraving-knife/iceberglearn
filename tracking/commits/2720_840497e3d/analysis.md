# 提交 2720：Spark 3.4: Deprecate support

## 提交信息

- **序号**：2720 / 4088
- **哈希**：840497e3d73c45665610cb2e897d6f3eb53fd9f5
- **短哈希**：840497e3d
- **日期**：2025-10-07 07:43:59 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark 3.4: Deprecate support
- **PR/Issue**：#14099

## 总体目的

Apache Iceberg 对各引擎版本的支持遵循一个生命周期模型，包括以下阶段：Maintained（维护中）、Deprecated（已废弃）、End of Life（停止支持）。每个引擎版本从被 Iceberg 首次支持到最终停止支持，会经历这些阶段。

Spark 3.4 发布于 2023 年，Iceberg 从 1.3.0 开始支持。随着 Spark 3.5 和 Spark 4.0 的发布，Spark 3.4 的使用率逐渐下降。社区决定将 Spark 3.4 的支持状态从 "Maintained" 降级为 "Deprecated"，这意味着 Spark 3.4 将不再获得新功能开发，只接受关键的 bug 修复和安全补丁。

此次变更将 Spark 3.4 在支持矩阵中的最终版本固定为 1.10.0（之前使用 `{{ icebergVersion }}` 模板变量动态显示当前版本），这标志着 1.10.0 是最后一个支持 Spark 3.4 的 Iceberg 版本。后续的 Iceberg 版本（2.0.0 及以后）将不再支持 Spark 3.4。

## 如何达成设计目的

通过修改文档文件 `site/docs/multi-engine-support.md`，将 Spark 3.4 的支持状态从 "Maintained" 改为 "Deprecated"，并将其最终支持版本从动态变量 `{{ icebergVersion }}` 固定为 `1.10.0`。

## 修改详情

### `site/docs/multi-engine-support.md` (+1/-1 lines)

**修改目的**：将 Spark 3.4 的支持状态标记为 Deprecated，并固定最终支持版本为 1.10.0。

**工作逻辑**：在引擎支持矩阵表格中，Spark 3.4 行的三个字段被修改：
1. **状态**：从 `Maintained` 改为 `Deprecated`
2. **最终支持版本**：从 `{{ icebergVersion }}`（动态显示当前版本）改为 `1.10.0`（固定值）
3. **下载链接**：从动态版本链接改为指向 1.10.0 版本的固定链接

这样，当 Iceberg 发布 2.0.0 等新版本时，文档会明确显示 Spark 3.4 的最后支持版本是 1.10.0，而不是误导用户认为新版本仍然支持 Spark 3.4。

## 总结

此提交将 Spark 3.4 的支持状态从 Maintained 降级为 Deprecated，固定最终支持版本为 1.10.0。这是 Iceberg 引擎支持生命周期管理的正常流程，标志着 Spark 3.4 进入维护结束阶段。使用 Spark 3.4 的用户应计划升级到 Spark 3.5 或 4.0，以获得 Iceberg 的持续支持和新功能。
