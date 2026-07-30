# 提交 2324：Core: Support DV for partition stats (#13425)

## 提交信息

- **序号**：2324 / 4088
- **哈希**：9f91295bf7453ce5c8d5b4f13f9dfa77db357920
- **短哈希**：9f91295bf
- **日期**：2025-07-07 14:40:21 -0700
- **作者**：Ajantha Bhat
- **提交说明**：Core: Support DV for partition stats (#13425)
- **PR/Issue**：#13425

## 总体目的

这个提交为 Iceberg 的分区统计功能添加了对删除向量（Deletion Vectors, DV）的支持。DV 是 Iceberg format version 3 引入的特性，用于高效标记行级删除。此前分区统计只能跟踪位置删除文件和等值删除文件的计数，无法区分 DV（以 Puffin 格式存储）。

DV 在文件格式上与传统的位置删除文件不同——传统位置删除文件以 Avro 格式存储，而 DV 以 Puffin 格式存储。在分区统计中，需要将 DV 单独计数（`dvCount`），以便用户了解各分区的 DV 分布情况。

此外，该提交还引入了版本化的 Schema 生成——v2 和 v3 格式使用不同的分区统计 Schema（v3 包含 `dv_count` 字段，v2 不包含），并通过字段默认值确保 v3 写入的文件可被 v2 读取（向后兼容）。

## 如何达成设计目的

1. **PartitionStats 扩展**：新增 `dvCount` 字段，在统计更新/减少/合并逻辑中根据文件格式（`FileFormat.PUFFIN`）区分 DV 和传统位置删除文件
2. **Schema 版本化**：`PartitionStatsHandler.schema()` 方法新增带 `formatVersion` 参数的重载，v2 Schema 不含 `dv_count`，v3 Schema 含 `dv_count`（字段 ID=13）
3. **默认值兼容**：`dv_count` 字段使用 `initialDefault(0)` 和 `writeDefault(0)`，确保 v2 读取 v3 写入的文件时该字段默认为 0
4. **测试参数化**：测试基类参数化为支持多格式版本（v2 及以上），验证 DV 统计在不同版本下的正确性

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (+29/-5 lines)

**修改目的**：在分区统计中添加 DV 计数支持。

**工作逻辑**：
- 新增 `dvCount` 字段和 `STATS_COUNT` 从 12 改为 13
- 在 `updateStats` 方法中，`POSITION_DELETES` 分支检查 `file.format() == FileFormat.PUFFIN`：如果是 Puffin 格式则增加 `dvCount`，否则增加 `positionDeleteFileCount`
- 在 `reduceStats` 方法中做对应的减少逻辑
- 在 `merge` 方法中将 `entry.dvCount` 累加
- 在 `StructLike` 接口的 `get`/`set` 方法中添加 position 12 的处理
- `positionDeleteRecordCount` 的注释更新为"also includes dv record count as per spec"

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+82/-12 lines)

**修改目的**：支持版本化的分区统计 Schema 和 DV 字段。

**工作逻辑**：
- 新增 `DV_COUNT` 字段定义（ID=13, `dv_count`, IntegerType, 带默认值 0）
- 原 `schema(StructType)` 方法标记为 `@Deprecated`，内部委托给 `v2Schema`
- 新增 `schema(StructType, int formatVersion)` 方法：formatVersion <= 2 返回 v2Schema（无 dv_count），> 2 返回 v3Schema（含 dv_count）
- `v3Schema` 将部分字段从 optional 改为 required
- 所有内部调用点改为使用带 formatVersion 的方法

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+152/-52 lines)

**修改目的**：参数化测试以支持多格式版本验证 DV 统计。

**工作逻辑**：使用 `@ExtendWith(ParameterizedTestExtension.class)` 参数化测试，支持 v2 及以上格式版本。测试方法从 `@Test` 改为 `@TestTemplate`，所有 Schema 创建调用改为带 formatVersion 参数，验证 `dvCount` 字段的正确性。

### `core/src/test/java/org/apache/iceberg/TestOrcPartitionStatsHandler.java` (+7/-0 lines)

**修改目的**：适配测试基类的参数化变更。

## 总结

这个提交为分区统计功能添加了 DV 支持，通过区分 Puffin 格式的 DV 文件和传统位置删除文件来单独统计 DV 计数。同时引入了版本化 Schema 设计，确保 v2/v3 格式的向后兼容性。这是 Iceberg 对 format version 3 DV 特性的持续完善。
