# 提交 2561：Docs: Add docs for Table Maintenance in Flink (#13853)

## 提交信息

- **序号**：2561 / 4088
- **哈希**：2df7dc760a63622c0ecd884155e471b27989191d
- **短哈希**：2df7dc760
- **日期**：2025-08-25 16:28:23 +0200
- **作者**：JeonDaehong
- **提交说明**：Docs: Add docs for Table Maintenance in Flink (#13853)
- **PR/Issue**：#13853

## 总体目的

该提交为 Iceberg 的 Flink 模块新增了完整的 Table Maintenance（表维护）文档。此前 Flink 的表维护文档以 `flink-actions.md` 的形式存在，但内容有限（仅覆盖批处理模式下的 rewrite files action）。随着 Flink 维护 API 的扩展（包括流式模式下的快照过期、小文件合并、孤儿文件清理等），需要一份完整的文档来指导用户如何在 Flink 环境中进行表维护。

新文档 `flink-maintenance.md`（401 行）覆盖了以下内容：
1. **批处理模式（BatchMode）**：Rewrite files action，将小文件合并为大文件。
2. **流式模式（StreamingMode）**：TableMaintenance API，支持在 Flink 流式作业中嵌入表维护任务，包括：
   - ExpireSnapshots（快照过期）
   - RewriteDataFiles（数据文件重写/合并）
   - RemoveOrphanFiles（孤儿文件清理）
   - 锁机制（ZkLockFactory / JdbcLockFactory）
   - 触发器配置
   - 监控指标

这消除了用户必须依赖 Spark 集群执行表维护的架构复杂性和运维开销，使 Flink 用户能够在原生环境中完成表维护。

## 如何达成设计目的

- 新建 `docs/docs/flink-maintenance.md` 文档，包含完整的 Flink 表维护 API 使用说明和代码示例。
- 删除旧的 `docs/docs/flink-actions.md`（内容已被新文档覆盖/替代）。
- 更新 `docs/mkdocs.yml` 导航，将 `flink-actions.md` 替换为 `flink-maintenance.md`。

## 修改详情

### `docs/docs/flink-maintenance.md` (+401/-0)

**修改目的**：新增 Flink 表维护完整文档。

**工作逻辑**：文档分为两大部分：
- **BatchMode**：介绍 `RewriteDataFilesAction` 的 API 和代码示例。
- **StreamingMode**：介绍 `TableMaintenance` API，包含 ExpireSnapshots、RewriteDataFiles、RemoveOrphanFiles 三个维护操作的使用方法，锁机制（ZkLockFactory/JdbcLockFactory）的配置，触发器（Trigger）的配置方式，以及监控指标。每个部分都提供了详细的 Java 代码示例和配置说明。

### `docs/docs/flink-actions.md` (+0/-35)

**修改目的**：删除旧的 Flink actions 文档。

**工作逻辑**：旧文档内容已被新的 `flink-maintenance.md` 完整覆盖，不再需要单独的 actions 文档。

### `docs/mkdocs.yml` (+1/-1)

**修改目的**：更新文档导航。

**工作逻辑**：将导航菜单中的 `flink-actions.md` 替换为 `flink-maintenance.md`。

## 总结

该提交新增了完整的 Flink Table Maintenance 文档（401 行），覆盖批处理和流式两种模式下的表维护操作，包括快照过期、文件合并、孤儿文件清理、锁机制和触发器配置等内容，同时删除了旧的 actions 文档并更新了导航。
