# 提交 3225：Site: Document v3 types (#14888)

## 提交信息

- **序号**：3225 / 4088
- **哈希**：e6593a00ff648f99d6db6c8fcf92acc9e1ab8c7d
- **短哈希**：e6593a00f
- **日期**：2026-02-09
- **作者**：Innocent Djiofack
- **提交说明**：Site: Document v3 types (#14888)
- **PR/Issue**：#14888

## 总体目的

Iceberg 的官方站点文档 `docs/docs/schemas.md` 以一张表格列举表所支持的数据类型及其说明/备注，供用户查阅建表可用类型。该表格此前只覆盖了 v2 规范下的类型集合（布尔、整数、浮点、decimal、日期/时间、string、fixed、binary 以及 struct/list/map 容器类型等），并未反映规范 v3 引入的一批新类型。随着 Iceberg 规范演进到 v3，新增了若干类型：纳秒精度时间戳（`timestamp_ns`/`timestamptz_ns`）、半结构化数据（`variant`）、地理空间类型（`geometry(C)`/`geography(C, A)`）、占位类型（`unknown`），以及通用唯一标识 `uuid`。这些类型在规范与实现层面已落地，但站点文档缺位，导致用户无法从官方类型表得知 v3 的类型能力与约束。

本提交在 `schemas.md` 的类型表中补齐这些类型行，并标注"added in v3"以区分版本来源、说明关键存储或语义约束（如纳秒时间戳以纳秒存储、`unknown` 必须为可选列、`geometry`/`geography` 的边插值与 CRS 参数差异等），使文档与规范 v3 保持一致，具备实际查阅指导价值。

## 如何达成设计目的

整体思路是文档层面的表格扩充：在 `schemas.md` 既有的类型表格中，按类型类别在合适位置插入 7 个新行，每行给出类型名、描述与备注（标注 v3 来源与关键约束），不改动既有行内容。改动仅涉及这一个 Markdown 文档，无代码逻辑变更。

## 修改详情

### `docs/docs/schemas.md` (+7/-0 lines)

**修改目的**：在类型表中补齐 v3 规范新增的数据类型行。

**工作逻辑**：
在表格中新增以下 7 行（位于 `timestamptz` 之后、`string` 之前插入两行纳秒时间戳；在 `string` 之后、`fixed(L)` 之前插入 `uuid`；在 `binary` 之后、`struct<...>` 之前插入 `variant`/`geometry`/`geography`/`unknown`）：

- `timestamp_ns`：无时区、纳秒精度时间戳，以纳秒存储；备注 "Stored as nanoseconds; added in v3"。
- `timestamptz_ns`：带时区、纳秒精度时间戳，以纳秒存储；备注 "Stored as nanoseconds; added in v3"。
- `uuid`：通用唯一标识符。
- `variant`：半结构化（类 JSON）数据；备注 "Added in v3"。
- `geometry(C)`：带 CRS 参数的地理空间特征，线性边插值；备注 "Linear edge-interpolation; added in v3"。
- `geography(C, A)`：带 CRS 与边算法参数的地理空间特征，非线性边插值；备注 "Non-linear edge-interpolation; added in v3"。
- `unknown`：用于未确定列的占位类型；备注 "Must be optional; added in v3"。

这些备注为用户提供了关键的版本与约束信息：纳秒时间戳的存储单位区别于微秒级的 `timestamp`/`timestamptz`；`unknown` 必须声明为可选列（不可为必填）这一重要约束被明确写出；`geometry` 与 `geography` 的差异（线性 vs 非线性边插值、参数集合不同）也被区分。需要留意的是，`uuid` 一行的 Markdown 表格行不完整（缺少第三列表头分隔与备注单元格，行尾未以 `|` 闭合），属本提交引入的一处格式瑕疵，在渲染时可能影响该行表格对齐，但类型名与描述信息本身可读。

## 总结

本提交在站点文档 `schemas.md` 的类型表中补齐了 Iceberg 规范 v3 新增的 7 种数据类型（`timestamp_ns`、`timestamptz_ns`、`uuid`、`variant`、`geometry(C)`、`geography(C, A)`、`unknown`），并标注 "added in v3" 及关键存储/语义约束，使官方文档与 v3 规范保持一致、具备查阅与建表指导价值；改动为纯文档表格扩充（其中 `uuid` 行存在一处表格格式瑕疵），不涉及代码。
