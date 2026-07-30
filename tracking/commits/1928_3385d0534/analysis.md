# 提交 1928：Update PyIceberg status page (#12645)

## 提交信息

- **序号**：1928 / 4088
- **哈希**：3385d05343ce183e0d598f2a5be438ec72d4032d
- **短哈希**：3385d0534
- **日期**：2025-03-27 19:36:47 +0200
- **作者**：Kevin Liu
- **提交说明**：Update PyIceberg status page (#12645)
- **PR/Issue**：#12645

## 总体目的

`site/docs/status.md` 是 Iceberg 各语言实现（Java、PyIceberg、Rust、Go）对规范特性支持情况的状态矩阵页。随着 PyIceberg 不断实现新功能，该页面中 PyIceberg 列的多个条目已过时（仍标记为 `N`，但实际已支持）。本提交把这些已实现的功能从 `N` 更新为 `Y`，使状态页与 PyIceberg 当前实际能力保持一致。

本次更新覆盖表操作、视图操作、以及多种 Catalog（In-Memory/REST、SQL、Glue、Hive Metastore）的命名空间操作中 PyIceberg 已支持的能力。

## 如何达成设计目的

直接编辑 `status.md` 中各表格的 PyIceberg 列单元格，把已实现操作从 `N` 改为 `Y`。涉及以下分组：

1. **表操作（Spec V1 与 V2）**：Update schema、Update partition spec、Update table location、Update statistics 由 N→Y（V1 与 V2 两张表都改）。
2. **表操作 - 删除**：Delete files 由 N→Y。
3. **视图操作**：dropView、listView、viewExists 由 N→Y。
4. **In-Memory / REST Catalog 命名空间操作**：namespaceExists、loadNamespaceMetadata 由 N→Y。
5. **SQL Catalog 命名空间操作**：listNamespaces、createNamespace、loadNamespaceMetadata 由 N→Y。
6. **Glue Catalog 命名空间操作**：listNamespaces、createNamespace、dropNamespace、loadNamespaceMetadata 由 N→Y。
7. **Hive Metastore Catalog 命名空间操作**：listNamespaces、createNamespace、dropNamespace、loadNamespaceMetadata 由 N→Y。

## 修改详情

### `site/docs/status.md` (修改, +23/-23 lines)

**修改目的**：把 PyIceberg 已支持的操作状态从 N 更新为 Y。

**工作逻辑**：在多张支持矩阵表中把对应行的 PyIceberg 单元格由 `N` 改为 `Y`，共 23 处。例如：

- Spec V1/V2 表操作：
  - `Update schema` PyIceberg：N→Y
  - `Update partition spec` PyIceberg：N→Y
  - `Update table location` PyIceberg：N→Y
  - `Update statistics` PyIceberg：N→Y
  - `Delete files` PyIceberg：N→Y
- 视图操作：
  - `dropView` / `listView` / `viewExists` PyIceberg：N→Y
- 各 Catalog 命名空间操作（In-Memory/REST、SQL、Glue、Hive Metastore）：
  - `listNamespaces` / `createNamespace` / `dropNamespace` / `loadNamespaceMetadata` / `namespaceExists` 视 catalog 由 N→Y。

文件末尾保持原"无换行结尾"的状态（`\ No newline at end of file`）。

## 总结

本提交更新 Iceberg 状态页 `status.md`，把 PyIceberg 已实现但页面仍标记为 `N` 的 23 项能力（表 schema/分区/位置/统计更新、删除文件、视图 drop/list/exists、多种 Catalog 的命名空间 list/create/drop/exists/loadMetadata）更正为 `Y`，使文档与 PyIceberg 当前实际支持情况一致。
