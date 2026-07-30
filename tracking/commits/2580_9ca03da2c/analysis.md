# 提交 2580：Doc: Flink Maintenance add Delete OrphansFiles part (#13923)

## 提交信息

- **序号**：2580 / 4088
- **哈希**：9ca03da2c8d807a2a0215b92d403fb11c91aaa39
- **短哈希**：9ca03da2c
- **日期**：2025-08-29 22:37:32 +0300
- **作者**：GuoYu
- **提交说明**：Doc: Flink Maintenance add Delete OrphansFiles part (#13923)
- **PR/Issue**：#13923

## 总体目的

此次提交为 Flink 表维护文档补充 `DeleteOrphanFiles`（删除孤儿文件）章节。Iceberg 的 Flink 维护模块（`flink-maintenance`）支持通过 `TableMaintenanceJob` 编排多种维护动作，如 `RewriteDataFiles`、`RewriteManifests`、`ExpireSnapshots` 等。此前文档中缺少 `DeleteOrphanFiles` 这一维护动作的说明和配置参考，用户无法从文档了解如何在 Flink 维护作业中配置孤儿文件清理。

孤儿文件（orphan files）是指表目录下不被任何元数据文件引用的文件，可能由失败的事务、压缩中断等产生，长期累积会浪费存储空间。`DeleteOrphanFiles` 维护动作负责扫描表目录并删除这些孤儿文件，支持按最小存活时间（minAge）过滤以避免删除正在写入的文件。

提交在文档的维护动作列表中新增 `DeleteOrphanFiles` 的简介与代码示例、配置参数表格，以及在完整示例中追加该动作的使用。

## 如何达成设计目的

- 在 `RewriteDataFiles` 章节后新增 `DeleteOrphanFiles` 小节，包含功能简介和 builder 代码示例（`minAge`、`deleteBatchSize`）。
- 在配置参考部分新增 `DeleteOrphanFiles Configuration` 表格，列出全部可配置方法、描述、默认值和类型。
- 在文档末尾的 Complete Example 中追加 `DeleteOrphanFiles` 调用示例。

## 修改详情

### `docs/docs/flink-maintenance.md` (+26)

**修改目的**：补充 DeleteOrphanFiles 维护动作文档。

**工作逻辑**：
- **功能简介与示例**：新增 `#### DeleteOrphanFiles` 小节，说明该动作用于删除不被任何元数据文件引用的孤儿文件，会检查表 location。代码示例展示 `DeleteOrphanFiles.builder().minAge(Duration.ofDays(3)).deleteBatchSize(1000)`。
- **配置表格**：新增 `DeleteOrphanFiles Configuration` 表格，包含：
  - `location(String)`：候选文件递归列出的起始位置，默认表 location。
  - `usePrefixListing(boolean)`：是否使用前缀列表（需 FileIO 支持 `SupportsPrefixOperations`），默认 false（递归列表）。
  - `prefixMismatchMode(PrefixMismatchMode)`：location 前缀（scheme/authority）不匹配时的行为（ERROR/IGNORE/DELETE），默认 ERROR。
  - `equalSchemes(Map)`：视为等价的文件系统 scheme 映射，默认 s3n->s3, s3a->s3。
  - `equalAuthorities(Map)`：视为等价的 authority 映射，默认空。
  - `minAge(Duration)`：仅删除此时间之前创建的孤儿文件，默认 3 天前。
  - `planningWorkerPoolSize(int)`：规划线程池大小，默认共享线程池。
- **完整示例**：在 Complete Example 的维护作业构建中追加 `DeleteOrphanFiles.builder().minAge(Duration.ofDays(5))`，注释说明删除 5 天前的孤儿文件。

## 总结

一次纯文档补充提交，为 Flink 表维护文档新增 `DeleteOrphanFiles` 动作的功能简介、代码示例、完整配置参数表格和完整示例中的使用。使用户能够从文档了解如何在 Flink 维护作业中配置和执行孤儿文件清理。无代码逻辑变更。
