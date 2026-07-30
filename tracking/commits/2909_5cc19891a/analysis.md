# 提交 2909：Core, API: Support cleanupMode in snapshot expiration (#14287)

## 提交信息

- **序号**：2909 / 4088
- **哈希**：5cc19891a25f314971bcbc3ee9d6d4fa52d942de
- **短哈希**：5cc19891a
- **日期**：2025-11-21 14:04:05 -0800
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core, API: Support cleanupMode in snapshot expiration
- **PR/Issue**：#14287

## 总体目的

在 Iceberg 的快照过期（snapshot expiration）机制中，原有的 API 只提供了一个布尔开关 `cleanExpiredFiles(boolean)`，要么完全清理数据文件和元数据文件，要么完全不清理。这种二元控制方式在实际使用中过于粗粒度，无法满足一些需要更细粒度控制的场景。

例如，当数据文件被多个表共享时（如通过 add-files 操作），删除数据文件可能会破坏其他表的数据完整性，此时用户希望只清理元数据文件（manifest、manifest list、统计文件），而保留数据文件。又或者当用户希望使用分布式框架通过 actions API 更高效地删除文件时，可能希望跳过所有文件清理，仅移除快照元数据。

本提交引入了 `CleanupLevel` 枚举，提供三个级别：`NONE`（跳过所有文件清理，仅移除快照元数据）、`METADATA_ONLY`（仅清理元数据文件，保留数据文件）、`ALL`（清理元数据和数据文件，默认行为），让用户对快照过期时的文件清理拥有细粒度控制。

## 如何达成设计目的

设计上在 `ExpireSnapshots` 接口中新增了 `CleanupLevel` 枚举和 `cleanupLevel(CleanupLevel)` 方法，同时将旧的 `cleanExpiredFiles(boolean)` 标记为 `@Deprecated`（自 1.11.0 起，2.0.0 移除），保持向后兼容。

为了处理新旧两种 API 的互斥与覆盖关系，`RemoveSnapshots` 实现类中维护了 `cleanExpiredFiles` 布尔字段和 `cleanupLevel` 字段，并通过 `Preconditions.checkArgument` 校验逻辑防止用户混用产生冲突：当 `cleanExpiredFiles(false)` 被显式设置后，不能再设置非 NONE 的 cleanupLevel；当 cleanupLevel 被设置为非 ALL 时，不能再调用 `cleanExpiredFiles`。但 `cleanExpiredFiles(true)` 之后可以用 `cleanupLevel` 覆盖，因为前者不产生冲突意图。

在清理策略层面，`FileCleanupStrategy.cleanFiles` 方法签名增加了 `cleanupLevel` 参数，两个具体实现 `IncrementalFileCleanup` 和 `ReachableFileCleanup` 根据级别决定是否删除数据文件，而元数据文件（manifest、manifest list、统计文件）在非 NONE 级别下都会被清理。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ExpireSnapshots.java` (+35/-2 lines)

**修改目的**：在 API 层定义新的 `CleanupLevel` 枚举和 `cleanupLevel` 方法，并弃用旧的 `cleanExpiredFiles`。

**工作逻辑**：
新增枚举 `CleanupLevel` 包含 `NONE`、`METADATA_ONLY`、`ALL` 三个值，各自带有 Javadoc 说明适用场景。`cleanupLevel(CleanupLevel)` 作为 default 方法抛出 `UnsupportedOperationException`，要求实现类自行实现。`cleanExpiredFiles(boolean)` 被加上 `@Deprecated` 注解并补充说明引导用户使用新 API。

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java` (+17/-1 lines)

**修改目的**：修改抽象清理策略基类的方法签名，传递 cleanupLevel 给具体实现。

**工作逻辑**：
`cleanFiles` 方法签名从 `(TableMetadata beforeExpiration, TableMetadata afterExpiration)` 改为增加第三个参数 `ExpireSnapshots.CleanupLevel cleanupLevel`，并补充详细 Javadoc 说明 NONE 级别在到达此方法之前已被处理。

### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java` (+21/-4 lines)

**修改目的**：在增量清理策略中实现按 cleanupLevel 分级清理。

**工作逻辑**：
方法开头判断 `cleanupLevel == NONE` 则直接返回。在删除数据文件的部分，用 `if (CleanupLevel.ALL == cleanupLevel)` 包裹 `findFilesToDelete` 和 `deleteFiles(filesToDelete, "data")`，使得 METADATA_ONLY 级别跳过数据文件删除。manifest、manifest list、统计文件的删除在非 NONE 级别下照常执行。同时增加了若干 debug 日志记录删除的文件数量。

### `core/src/main/java/org/apache/iceberg/ReachableFileCleanup.java` (+24/-2 lines)

**修改目的**：在可达文件清理策略中实现按 cleanupLevel 分级清理，逻辑与 IncrementalFileCleanup 对应。

**工作逻辑**：
开头判断 NONE 直接返回。在 `manifestsToDelete` 非空时，用 `if (CleanupLevel.ALL == cleanupLevel)` 包裹数据文件查找与删除逻辑，manifest 路径删除和 manifest list、统计文件删除照常执行。

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (+27/-4 lines)

**修改目的**：在 ExpireSnapshots 的实现类中实现新 API，处理新旧 API 互斥逻辑，并根据级别决定是否执行清理。

**工作逻辑**：
新增 `cleanupLevel` 字段默认 `ALL`。`cleanExpiredFiles` 方法增加校验：若 cleanupLevel 已被设为非 ALL 则报错，并同步将 `cleanExpiredFiles` 布尔映射到 cleanupLevel。`cleanupLevel` 方法校验非 null 且不与 `cleanExpiredFiles(false)` 冲突。在 `commit()` 中将判断条件从 `cleanExpiredFiles` 改为 `CleanupLevel.NONE != cleanupLevel`，并在 `cleanExpiredSnapshots()` 中将 cleanupLevel 传递给清理策略。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+174/-0 lines)

**修改目的**：为新的 cleanupLevel 功能添加全面测试覆盖。

**工作逻辑**：
新增 7 个测试用例：`testCleanupLevelAll` 验证 ALL 级别删除数据和元数据文件；`testCleanupLevelMetadataOnly` 验证只删元数据；`testCleanupLevelNone` 验证不删任何文件但移除快照元数据；`testCleanExpiredFilesAPI` 验证旧 API `cleanExpiredFiles(false)` 等价于 NONE；`testCannotSetCleanExpiredFilesAndCleanupLevelTogether` 验证混用抛异常；`testCanOverrideCleanExpiredFilesWithCleanupLevel` 验证 `cleanExpiredFiles(true)` 后可被 cleanupLevel 覆盖；`testCleanupLevelNullValidation` 验证 null 校验。

## 总结

本提交为 Iceberg 快照过期引入了细粒度的文件清理控制能力，解决了原有布尔开关过于粗粒度的问题，特别适用于数据文件共享场景和分布式清理场景。通过 `CleanupLevel` 枚举提供了清晰的三级控制，同时保持了对旧 API 的向后兼容并最终计划弃用旧 API。实现中仔细处理了新旧 API 的互斥与覆盖关系，并通过充分的测试覆盖确保了正确性。这是一个对生产环境有实际价值的 API 增强。
