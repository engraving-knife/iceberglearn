# 提交 2626：Site: fix up 1.10.0 release notes (#14055)

## 提交信息

- **序号**：2626 / 4088
- **哈希**：b5ca14757f0d466e3cf06abb806754a50885d849
- **短哈希**：b5ca14757
- **日期**：2025-09-11 15:45:55 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Site: fix up 1.10.0 release notes
- **PR/Issue**：#14055

## 总体目的

这是对提交 2621（finalizing 1.10.0 release）的后续修复。2621 中写入的 1.10.0 发布说明存在若干问题需要修正：

1. **发布日期占位符未填**：2621 中写的是 "released on September ??, 2025"，日期未确定。本提交填入实际日期 "September 11, 2025"。
2. **runtime Jar 下载链接需更新**：1.10.0 新增了对 Spark 4.0 和 Flink 2.0 的支持，并移除了 Flink 1.18 支持。下载链接列表需要相应调整：新增 Spark 4.0 with Scala 2.13 和 Flink 2.0 的 runtime Jar 链接，移除 Flink 1.18 的链接。
3. **列表缩进不一致**：2621 中各分类下的条目使用 2 空格缩进（`  - `），与文档其他部分及 markdown 规范的 4 空格子项缩进不一致，导致渲染层级不正确。本提交统一改为 4 空格缩进（`    - `）。
4. **一处条目文本错误**：Spark 分类中 "Rewrite table path action: filter content files by snapshot id in incremental mode" 后误粘了 "REST: Add context aware response parsing"，本提交修正为只保留 Rewrite table path 的描述。

## 如何达成设计目的

逐项修改 `site/docs/releases.md`：
- 填入确定发布日期。
- 在 runtime Jar 下载列表顶部新增 Spark 4.0 Scala 2.13，在 Flink 区域新增 Flink 2.0、移除 Flink 1.18。
- 将 1.10.0 章节所有分类子条目的缩进从 2 空格改为 4 空格。
- 修正误粘贴的条目文本。

## 修改详情

### `site/docs/releases.md` (+111/-110 lines)

**修改目的**：修正 1.10.0 发布说明的日期、下载链接、缩进与文本错误。

**工作逻辑**：
- **日期**：`Apache Iceberg 1.10.0 was released on September ??, 2025.` 改为 `September 11, 2025`。
- **runtime Jar 下载列表**：
  - 新增 `{{ icebergVersion }} Spark 4.0\_with Scala 2.13 runtime Jar`（指向 `iceberg-spark-runtime-4.0_2.13`），置于 Spark 3.5 之前。
  - 新增 `{{ icebergVersion }} Flink 2.0 runtime Jar`（指向 `iceberg-flink-runtime-2.0`），置于 Flink 1.20 之前。
  - 移除 `{{ icebergVersion }} Flink 1.18 runtime Jar`（因 1.10.0 已弃用 Flink 1.18 支持）。
- **列表缩进**：将 1.10.0 章节下所有分类（Deprecation、Behavior change、Spec、API、Core、Arrow、Parquet、Spark、Flink、Hive、Kafka connect、Vendor integrations、Dependencies）的子条目从 `  - `（2 空格）改为 `    - `（4 空格），使 markdown 渲染层级正确。这是改动行数最多的部分（约 110 行重缩进）。
- **文本修正**：Spark 分类中 `Rewrite table path action: filter content files by snapshot id in incremental mode  REST: Add context aware response parsing` 改为 `Rewrite table path action: filter content files by snapshot id in incremental mode`，删除误粘贴的 "REST: Add context aware response parsing"（该内容在 Core 分类已有对应条目）。

## 总结

本提交是对 1.10.0 发布说明的修复，填补发布日期、更新 runtime Jar 下载链接以反映 Spark 4.0/Flink 2.0 支持与 Flink 1.18 弃用、统一列表缩进以正确渲染、修正一处误粘贴文本。这些修正使 1.10.0 的发布文档准确、规范，与实际发布内容一致。
