# 提交 3554：Docs: Sync Go implementation status with iceberg-go (#16021)

## 提交信息

- **序号**：3554 / 4088
- **哈希**：69d7bfacebc044ad9b36e21f5cee2bd12d2da204
- **短哈希**：69d7bface
- **日期**：2026-04-18 15:44:46 -0400
- **作者**：Tanmay Rauth
- **提交说明**：Docs: Sync Go implementation status with iceberg-go (#16021)
- **PR/Issue**：#16021

## 总体目的

Iceberg 官方网站的 `status.md` 文档跟踪各语言实现对 Iceberg 规范的支持情况。随着 `iceberg-go` 项目的进展，Go 实现已支持更多操作，但网站表格中 Go 列的多项还是旧的 "N"（不支持）状态。

本提交通过对 `iceberg-go` 源代码的实际验证，更新 `status.md` 中 Go 列的 42 项状态，反映 Go 实现的真实能力。提交说明中列出了每项改动对应的源代码文件和行号作为证据，例如 `transaction.go:177`（update schema）、`metadata.go:532`（replace sort order）、`rewrite_data_files.go:83`（rewrite files）、`row_delta.go:63`（row delta）、`equality_delete_writer.go:78`（write equality deletes）等。这些引用经过 reviewer（zeroshade、laskoviymishka）确认。

## 如何达成设计目的

逐项更新 `status.md` 中多个表格的 Go 列，把已支持的操作从 "N" 改为 "Y"。更新覆盖多个类别：表元数据操作（v1/v2）、表更新操作（v1/v2）、读取规划、表写操作、View 操作（多个 catalog）、Hive Metastore 命名空间操作。

## 修改详情

### `site/docs/status.md` (+42/-42 lines)

**修改目的**：同步 Go 实现的支持状态。

**工作逻辑**：以下操作的 Go 列从 N 改为 Y：

**表元数据操作**（v1 和 v2 两个表格）：
- Update schema (`transaction.go:177`)
- Update partition spec (`transaction.go:160`)
- Replace sort order (`metadata.go:532`)
- Update table location (`updates.go:376`)
- Update statistics
- Expire snapshots (`transaction.go:212`)
- Manage snapshots (`metadata.go:753`)

**表更新操作**（v1 和 v2）：
- Rewrite files (`rewrite_data_files.go:83`)
- Overwrite files
- Delete files
- Row delta (仅 v2, `row_delta.go:63`)

**读取规划**：
- Plan with equality deletes
- Read with equality deletes

**表写操作**：
- Write position deletes
- Write equality deletes (v2, `equality_delete_writer.go:78`)

**View 操作**（REST Catalog、SQL/JDBC Catalog、Glue Catalog 三处）：
- createView
- dropView
- listView
- viewExists

**Hive Metastore Catalog 命名空间操作**：
- listNamespaces
- createNamespace
- dropNamespace
- namespaceExists
- loadNamespaceMetadata

每处改动都是表格单元格中 Go 列从 `N` 改为 `Y`，共 42 处。

## 总结

本提交将 Iceberg 官方网站 `status.md` 中 Go 实现的 42 项操作支持状态从 "N" 更新为 "Y"，覆盖表元数据操作、表更新操作（含 row delta）、读取规划（equality deletes）、写操作（position/equality deletes）、View 操作（REST/SQL/Glue 三个 catalog）、Hive Metastore 命名空间操作。所有更新均通过 iceberg-go 源代码验证并附源码引用，经 reviewer 确认。属于文档维护，反映 iceberg-go 项目的进展。
