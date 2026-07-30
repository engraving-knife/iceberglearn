# 提交 3406：Flink: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566)

## 提交信息

- **序号**：3406 / 4088
- **哈希**：22c788afb93a4cb86acb653b19670ec9cf02dc1b
- **短哈希**：22c788afb9
- **日期**：2026-03-17 13:21:56 +0100
- **作者**：Maximilian Michels
- **提交说明**：Flink: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566)
- **PR/Issue**：#15566

## 总体目的

扩展 Flink IcebergSink 的维护能力，允许用户通过 Builder API 配置任意的提交后维护任务。此前 IcebergSink 仅支持数据文件压缩（RewriteDataFiles）作为提交后维护任务，本提交新增了过期快照删除（ExpireSnapshots）和孤立文件删除（DeleteOrphanFiles）两种维护任务，并通过统一的 `MaintenanceTaskBuilder` 列表管理所有维护任务。同时重构了配置体系，为每种维护任务提供独立的配置类。

## 如何达成设计目的

1. 将 `compactMode` 布尔标志替换为 `maintenanceTasks` 列表，通过列表是否为空判断是否启用维护
2. 在 IcebergSink.Builder 中新增 `rewriteDataFiles()`、`expireSnapshots()`、`deleteOrphanFiles()` 等便捷方法及其带配置的重载
3. 新建 `ExpireSnapshotsConfig` 和 `DeleteOrphanFilesConfig` 配置类，定义各自的配置选项和默认值
4. 在 `FlinkWriteOptions` 中新增 `EXPIRE_SNAPSHOTS_ENABLE` 和 `DELETE_ORPHAN_FILES_ENABLE` 选项，并将 `COMPACTION_ENABLE` 的 key 改为统一前缀格式
5. 为 `DeleteOrphanFiles.Builder` 和 `ExpireSnapshots.Builder` 添加 `config()` 方法以接受配置对象
6. `TableMaintenance.Builder` 新增 `add(Collection)` 方法支持批量添加任务
7. 弃用旧的 `compaction(boolean)` 方法，用 `rewriteDataFiles()` 替代

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+147/-29 lines)

**修改目的**：重构 IcebergSink 以支持多种维护任务。

**工作逻辑**：
- 将 `compactMode` 字段替换为 `maintenanceEnabled`（由 maintenanceTasks 列表是否为空决定）和 `maintenanceTasks` 列表
- `addPostCommitTopology` 方法改为检查 `maintenanceTasks.isEmpty()` 而非 `compactMode`
- 后提交拓扑构建逻辑重构：不再硬编码 RewriteDataFiles，而是传入所有 maintenanceTasks
- Builder 新增 `rewriteDataFiles()`、`rewriteDataFiles(Map)`、`expireSnapshots()`、`expireSnapshots(Map)`、`deleteOrphanFiles()`、`deleteOrphanFiles(Map)` 方法
- 弃用 `compaction(boolean)` 方法，用 `rewriteDataFiles()` 替代
- `append()` 方法中根据 `flinkWriteConf` 的三个开关（compactMode、expireSnapshotsMode、deleteOrphanFilesMode）构建对应的 MaintenanceTaskBuilder 并添加到列表

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java` (+4/-4 lines)

**修改目的**：将 `compactMode` 重命名为 `tableMaintenanceEnabled`。

**工作逻辑**：
- 字段 `compactMode` 重命名为 `tableMaintenanceEnabled`
- 构造函数参数同步重命名
- `commit` 方法中 `!compactMode` 改为 `!tableMaintenanceEnabled`

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (+18 lines)

**修改目的**：新增 `expireSnapshotsMode()` 和 `deleteOrphanFilesMode()` 配置读取方法。

**工作逻辑**：
- 两个方法分别读取 `EXPIRE_SNAPSHOTS_ENABLE` 和 `DELETE_ORPHAN_FILES_ENABLE` 选项，默认为 false

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (+16/-1 lines)

**修改目的**：新增维护任务开关选项，统一配置 key 前缀。

**工作逻辑**：
- `COMPACTION_ENABLE` 的 key 从 `"compaction-enabled"` 改为 `RewriteDataFilesConfig.PREFIX + "enabled"`，旧的 key 作为 deprecated key 保留
- 新增 `EXPIRE_SNAPSHOTS_ENABLE` 选项，key 为 `ExpireSnapshotsConfig.PREFIX + "enabled"`
- 新增 `DELETE_ORPHAN_FILES_ENABLE` 选项，key 为 `DeleteOrphanFilesConfig.PREFIX + "enabled"`

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/DeleteOrphanFilesConfig.java` (+216 lines, 新文件)

**修改目的**：定义孤立文件删除任务的配置类。

**工作逻辑**：
- 定义前缀 `FlinkMaintenanceConfig.PREFIX + "delete-orphan-files."`
- 配置选项包括：调度间隔（默认1小时）、最小文件年龄（默认3天）、删除批大小（默认1000）、位置、是否使用前缀列表（默认true）、规划线程池大小、相等 scheme 映射、相等 authority 映射、前缀不匹配模式（默认ERROR）
- 使用 `FlinkConfParser` 从 table 属性、write options 和 Flink 配置中读取值

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshotsConfig.java` (+151 lines, 新文件)

**修改目的**：定义过期快照删除任务的配置类。

**工作逻辑**：
- 定义前缀 `FlinkMaintenanceConfig.PREFIX + "expire-snapshots."`
- 配置选项包括：提交计数调度（默认10）、间隔调度（默认1小时）、最大快照年龄、保留最近快照数、删除批大小（默认1000）、是否清理过期元数据（默认true）、规划线程池大小

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/DeleteOrphanFiles.java` (+26/-8 lines)

**修改目的**：为 Builder 添加 config 方法，提取默认相等 scheme 为常量，更改 usePrefixListing 默认值为 true。

**工作逻辑**：
- 新增 `DEFAULT_EQUAL_SCHEMES` 常量
- `usePrefixListing` 默认值从 false 改为 true
- Builder 新增 `config(DeleteOrphanFilesConfig)` 方法，从配置对象设置所有参数

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java` (+14 lines)

**修改目的**：为 Builder 添加 config 方法。

**工作逻辑**：
- Builder 新增 `config(ExpireSnapshotsConfig)` 方法，从配置对象设置调度、批大小、最大快照年龄、保留数等参数

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/FlinkMaintenanceConfig.java` (+8 lines)

**修改目的**：新增创建 ExpireSnapshotsConfig 和 DeleteOrphanFilesConfig 的工厂方法。

**工作逻辑**：
- `createExpireSnapshotsConfig()` 和 `createDeleteOrphanFilesConfig()` 方法

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+12 lines)

**修改目的**：新增批量添加维护任务的方法。

**工作逻辑**：
- `add(Collection<MaintenanceTaskBuilder<?>>)` 方法将一组任务添加到 taskBuilders 列表

### `docs/docs/flink-maintenance.md` (+126/-7 lines)

**修改目的**：文档化新增的维护任务和配置。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestDeleteOrphanFilesConfig.java` (+91 lines, 新文件)

**修改目的**：测试 DeleteOrphanFilesConfig 的配置解析。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TestExpireSnapshotsConfig.java` (+84 lines, 新文件)

**修改目的**：测试 ExpireSnapshotsConfig 的配置解析。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkTableMaintenance.java` (+167/-1 lines, 重命名)

**修改目的**：测试 IcebergSink 的多维护任务配置。

## 总结

本提交大幅扩展了 Flink IcebergSink 的提交后维护能力，从仅支持数据压缩扩展到支持过期快照删除和孤立文件删除。通过统一的 `MaintenanceTaskBuilder` 列表管理多种维护任务，并为每种任务提供独立的配置类。弃用了旧的 `compaction` API，引入更清晰的 `rewriteDataFiles()` 等方法。配置 key 统一使用前缀格式，向后兼容旧 key。
