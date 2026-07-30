# 提交 2648：Core: Allow reading metadata table when scanning table with dropped partition source field (#14089)

## 提交信息

- **序号**：2648 / 4088
- **哈希**：87fce5fbe92de69ac3dfd5ca122535417ef6b072
- **短哈希**：87fce5fbe
- **日期**：2025-09-17 09:59:15 -0700
- **作者**：Gabriel Igliozzi
- **提交说明**：Core: Allow reading metadata table when scanning table with dropped partition source field (#14089)
- **PR/Issue**：#14089

## 总体目的

Iceberg 支持分区演化（partition evolution），可以添加或移除分区字段。当移除一个分区字段后，其对应的源字段（source field）也可能被从 schema 中删除（drop column）。此时，旧快照中的 manifest 仍可能引用该已删除的分区字段。

在扫描元数据表（metadata table，如 files 表）时，`BaseMetadataTable` 会基于表的分区规范构建一个投影 schema。当分区源字段已被删除时，构建分区规范会因找不到源字段而抛出异常，导致无法读取元数据表。

本提交通过在构建分区规范时启用 `allowMissingFields`（`builder.build(true)`）来允许分区规范中存在缺失的源字段（即 void partition fields），从而修复了在分区源字段被删除后无法扫描元数据表的问题。

## 如何达成设计目的

在 `BaseMetadataTable` 的 `transformedPartitionSpec` 方法中，将 `PartitionSpec.Builder.build()` 改为 `build(true)`，即启用 `allowMissingFields` 选项。这样当分区规范中引用的源字段已从 schema 中删除时，不会抛出异常，而是将其视为 void 分区字段，允许扫描继续进行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java` (+4/-1 lines)

**修改目的**：允许分区规范中存在缺失的源字段。

**工作逻辑**：在 `transformedPartitionSpec` 方法中，将 `return builder.build()` 改为 `return builder.build(true)`。添加注释说明启用 `allowMissingFields` 以允许分区规范中存在缺失的源字段（void partition fields）。`build(true)` 的 `true` 参数表示允许规范中引用的源字段在 schema 中不存在。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScansWithPartitionEvolution.java` (+16/-0 lines)

**修改目的**：验证分区源字段被删除后仍能扫描元数据表。

**工作逻辑**：新增测试 `testPartitionSpecEvolutionSourceFieldMissing`：
1. 移除分区字段 `id`。
2. 删除源列 `id`。
3. 创建 `AllFilesTable` 并执行扫描。
4. 断言扫描能返回 2 个文件任务（不抛异常）。

## 总结

本提交修复了在分区源字段被删除后无法扫描元数据表的 bug，通过在构建分区规范时启用 `allowMissingFields`，将缺失的源字段视为 void 分区字段。修改简洁精准，配套测试覆盖了"移除分区字段 + 删除源列"的场景。这是一个健壮性改进，使分区演化后的元数据表扫描更加可靠。
