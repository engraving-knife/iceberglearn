# 提交 3093：Spark: Add location overlap validation for SnapshotTableAction (#14933)

## 提交信息

- **序号**：3093 / 4088
- **哈希**：2c92500e974eb5a58cdcf75126a85d7cf16d2995
- **短哈希**：2c92500e9
- **日期**：2026-01-10
- **作者**：Varun Lakhyani
- **提交说明**：Spark: Add location overlap validation for SnapshotTableAction (#14933)
- **PR/Issue**：#14933

## 总体目的

该提交为 Spark 的 `SnapshotTableSparkAction` 添加了源表与目标快照表位置重叠校验。`SnapshotTableAction` 是 Iceberg Spark 模块提供的一个迁移工具，用于将现有的 Spark 表（如 Parquet/ORC 表）快照为 Iceberg 表——即创建一个新的 Iceberg 表，其数据文件指向源表的数据文件位置，而不复制数据。

此前代码中仅有一行注释 `// TODO: Check the dest table location does not overlap with the source table location`，表明开发者已知悉需要校验但尚未实现。如果目标快照表的位置与源表位置重叠（例如目标位置是源位置的子目录，或源位置是目标位置的子目录，或二者完全相同），会导致严重的数据管理问题：快照表和源表的元数据文件、数据文件会混在同一目录树下，可能导致文件被误删、快照表引用到源表管理范围外的文件，或在清理操作时损坏另一张表的数据。

完全相同的位置是最极端的情况——快照表和源表共享完全相同的存储路径，任何一方的写入或清理都会影响另一方。子目录关系（目标位置是源位置的子目录，或反之）同样危险，因为文件路径前缀匹配会导致一方的文件列表包含另一方的文件。通过添加前置校验，在快照操作执行前就拒绝重叠的位置配置，避免后续的数据损坏风险。

该提交还新增了两个测试方法，分别验证重叠位置被拒绝（包括完全相同、子目录、父目录三种情况）和非重叠位置正常执行的场景。

## 如何达成设计目的

在 `SnapshotTableSparkAction.execute` 方法中，将原来的 TODO 注释替换为实际的 `Preconditions.checkArgument` 校验，通过字符串比较检测三种重叠情况：完全相同、目标位置是源位置的子目录、源位置是目标位置的子目录。子目录检测使用 `startsWith(sourceTableLocation + "/")` 确保是路径级别的包含关系而非字符串前缀巧合（例如 `/data/abc` 不应匹配 `/data/ab`）。测试中通过 `DESCRIBE EXTENDED` 获取源表实际位置，并构造各种重叠和非重叠的目标位置进行验证。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java` (+10/-1 lines)

**修改目的**：实现源表与目标快照表位置重叠校验。

**工作逻辑**：
在 `stageDestTable()` 之后、实际快照操作之前，获取源表位置 `sourceTableLocation = sourceTableLocation()` 和目标表位置 `stagedTableLocation = icebergTable.location()`。使用 `Preconditions.checkArgument` 校验三个条件同时不成立：`!sourceTableLocation.equals(stagedTableLocation)`（不完全相同）、`!stagedTableLocation.startsWith(sourceTableLocation + "/")`（目标不是源的子目录）、`!sourceTableLocation.startsWith(stagedTableLocation + "/")`（源不是目标的子目录）。校验失败时抛出 `IllegalArgumentException`，消息为 "Cannot create a snapshot at location %s because it would overlap with source table location %s. Overlapping snapshot and source would mix table files."，包含目标位置和源位置信息便于排查。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java` (+92/-1 lines)

**修改目的**：验证位置重叠校验的正确性和非重叠场景的正常执行。

**工作逻辑**：
新增 `SOURCE` 常量字符串 "source"。新增两个 `@TestTemplate` 测试方法：

`testSnapshotWithOverlappingLocation`：使用 `assumeThat(catalogType).isNotEqualTo(ICEBERG_CATALOG_TYPE_HADOOP)` 跳过不支持自定义表位置的 Hadoop Catalog。创建带自定义 LOCATION 的源表并插入两条数据，通过 `DESCRIBE EXTENDED` 获取实际源表位置。然后验证三种重叠场景均抛出 `IllegalArgumentException`：(1) 目标位置与源位置完全相同——断言消息以 "The snapshot table location cannot be same as the source table location." 开头（该消息来自上游 `BaseTableCreationSparkAction` 的已有校验）；(2) 目标位置是源位置的子目录（`actualSourceLocation + "/nested"`）——断言消息以 "Cannot create a snapshot at location" 开头（来自本提交新增的校验）；(3) 目标位置是源位置的父目录（截取掉 `/source` 后缀）——同样断言新校验的消息。

`testSnapshotWithNonOverlappingLocation`：同样跳过 Hadoop Catalog，创建源表并获取实际位置。构造一个非重叠的目标位置（将源路径末尾的 "source" 替换为 "newDestination"），执行快照操作并验证结果表有 2 条数据，确认非重叠场景正常工作。

## 总结

该提交实现了 `SnapshotTableSparkAction` 中长期存在的 TODO 项——源表与目标快照表位置重叠校验，防止因位置重叠导致的数据文件混合和管理混乱。校验覆盖完全相同、子目录、父目录三种重叠模式，通过路径前缀加 `/` 的方式确保路径级别的精确匹配。测试全面覆盖了各种重叠和非重叠场景，并正确处理了 Hadoop Catalog 不支持自定义位置的兼容性。这是一个重要的数据安全性增强，防止用户误配置导致的数据损坏。
