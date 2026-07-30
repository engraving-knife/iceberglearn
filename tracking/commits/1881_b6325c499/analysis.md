# 提交 1881：Docs: update go impl status (#12578)

## 提交信息

- **序号**：1881 / 4088
- **哈希**：b6325c499355d90b3a20b18f2ad9611d8185d440
- **短哈希**：b6325c499
- **日期**：2025-03-19 16:49:16 +0100
- **作者**：Matt Topol
- **提交说明**：Docs: update go impl status (#12578)
- **PR/Issue**：#12578

## 总体目的

本提交更新 Iceberg 各语言实现状态文档（`site/docs/status.md`），反映 Go 实现近期新增的功能支持。

背景：iceberg-go 项目持续推进，已实现多个此前标记为 N（不支持）的功能。文档需同步更新以准确反映 Go 实现的当前能力，避免用户误认为某些功能不可用。

## 如何达成设计目的

直接编辑 `site/docs/status.md` 中多个状态表格的 Go 列，将 N 改为 Y。

## 修改详情

### `site/docs/status.md` (修改, +14/-14 lines)

**修改目的**：更新 Go 实现的功能支持状态。

**工作逻辑**：将以下功能的 Go 列从 N 改为 Y（共 14 处）：
- 表/视图更新操作：Update table properties（Table Spec V1 和 V2 两处）
- 表写入操作：Append data files（V1 和 V2 两处）
- 表读取操作（V1）：Read data file
- 表读取操作（V2）：Plan with position deletes、Read data file、Read with position deletes
- SQL Catalog 数据库支持：Postgres、MySQL、SQLite（三处）
- Namespace 操作（SQL Catalog）：listNamespaces、createNamespace、namespaceExists（三处）

## 总结

本提交是文档状态同步，将 iceberg-go 已实现的 14 项功能在状态表中从 N 更新为 Y，涵盖表属性更新、数据追加、数据读取（含 position deletes）、SQL Catalog 数据库支持及 namespace 操作。
