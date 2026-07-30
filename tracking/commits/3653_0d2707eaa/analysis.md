# 提交 3653：Docs: Add missing v3 data types to status page (#16228)

## 提交信息

- **序号**：3653 / 4088
- **哈希**：0d2707eaab3f1629bdcb55fdb5100fa256c83d19
- **短哈希**：0d2707eaa
- **日期**：2026-05-06 09:41:29 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Add missing v3 data types to status page (#16228)
- **PR/Issue**：#16228

## 总体目的

这个提交在 Iceberg 网站的状态页面（status.md）中补充缺失的 v3 数据类型支持情况。

状态页面以表格形式展示各数据类型在不同计算引擎（Spark、Flink、Trino、Presto、DuckDB 等）中的支持情况。此前表格中遗漏了三个 v3 数据类型：`unknown`、`geometry`、`geography`。这些类型已在 Iceberg 规范中定义，但未在状态页中列出，导致用户无法了解各引擎对它们的支持状态。本提交补全了这三个类型在各引擎的支持矩阵。

## 如何达成设计目的

在 `site/docs/status.md` 的数据类型支持表格中，按类型分类位置插入三行：
- `unknown`：Spark=Y, Flink=Y, Trino=N, Presto=Y, DuckDB=N（放在 timestamp 类之后、string 之前）
- `geometry`：Spark=Y, Flink=N, Trino=N, Presto=N, DuckDB=N（放在 variant 之后）
- `geography`：Spark=Y, Flink=N, Trino=N, Presto=N, DuckDB=N（同上）

## 修改详情

### `site/docs/status.md` (+3 lines)

**修改目的**：补全 v3 数据类型支持矩阵。

**工作逻辑**：在表格中新增三行：
```
| unknown        | Y    | Y         | N    | Y  | N   |
| geometry       | Y    | N         | N    | N  | N   |
| geography      | Y    | N         | N    | N  | N   |
```
其中 `unknown` 类型仅 Spark、Flink、Presto 支持；`geometry` 和 `geography` 仅 Spark 支持（其他引擎均不支持）。

## 总结

这是一个纯文档提交，在 Iceberg 状态页面的数据类型支持矩阵中补全了 `unknown`、`geometry`、`geography` 三个 v3 数据类型的支持情况。这使文档与实际规范保持一致，帮助用户了解各引擎对这些新类型的支持程度。从矩阵看，`geometry` 和 `geography` 目前仅 Spark 支持，其他引擎支持尚待完善。
