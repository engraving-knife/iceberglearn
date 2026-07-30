# 提交 3041：Site: Updates for 1.10.1 Release (#14907)

## 提交信息

- **序号**：3041 / 4088
- **哈希**：f4005868942a929436576389ac764be9841ec128
- **短哈希**：f40058689
- **日期**：2025-12-22
- **作者**：Huaxin Gao
- **提交说明**：Site: Updates for 1.10.1 Release (#14907)
- **PR/Issue**：#14907

## 总体目的

本提交为 Apache Iceberg 1.10.1 维护版本发布更新官方文档站点（基于 MkDocs Material 的 `site/` 目录）。1.10.1 是 1.10.x 系列的补丁版本，于 2025 年 12 月发布，仅包含 bug 修复。本次文档更新的核心任务有四项：一是在发布说明页面 `releases.md` 中新增 1.10.1 的完整变更清单（按 API/Core/Parquet/Flink/Spark/Kafka Connect/Vendor integrations 等模块分类列出所有修复 PR）；二是把站点全局展示的"最新版本"从 1.10.0 切换到 1.10.1；三是在导航配置中把 1.10.0 移入"Previous（历史版本）"分组，使 1.10.1 成为默认的"Latest"；四是把 `## Past releases` 标题位置下移到 1.10.1 与 1.10.0 之间，使最新版本与历史版本的层级结构保持一致。

这类发布文档更新的动机是版本发布流程的标准化动作：每次发布新版本后，需同步更新站点以反映新版本号、提供变更明细并归档旧版本。对用户而言，`releases.md` 是了解 1.10.1 修复了哪些问题的关键入口，导航与版本号切换则确保用户默认看到最新文档。提交说明中的子提交记录（"add missing space"、"remove duplicate items"）表明作者在初次写入后又修正了格式瑕疵与重复条目，体现了发布文档的精校过程。

## 如何达成设计目的

改动涉及 `site/docs/releases.md`、`site/mkdocs.yml`、`site/nav.yml` 三个站点配置/内容文件。`releases.md` 负责内容（变更清单），`mkdocs.yml` 负责站点变量（`icebergVersion`），`nav.yml` 负责导航结构（版本分组）。三者协同完成"新增版本说明 + 切换最新版本 + 归档旧版本"的发布动作。

## 修改详情

### `site/docs/releases.md` (+51/-4 lines)

**修改目的**：新增 1.10.1 发布说明并调整历史版本标题层级。

**工作逻辑**：
在 1.10.0 发布说明之前插入完整的 1.10.1 章节，结构为：开篇一句 `Apache Iceberg 1.10.1 was released on Dec 21, 2025.`，说明该版本仅含 bug 修复，并给出 GitHub Release 页面链接；随后按模块分类列出修复项，每项附 PR 链接。具体分类包括：

- **API**：可选结构内必需嵌套字段能否产生 null 的检测（#13804、#14270）。
- **Core**：含禁止删除含视图的命名空间（#14456）、timestamp nanos 默认值溢出修复（#14359）、replace partitions 的非分区检查（#14186）、`NAN_VALUE_COUNTS` 序列化修复（#14721）、未知类型 deletes 处理（#14356），以及一组 REST 相关修复（PlanTableScanRequest/Response 校验 #14561/#14562、空 delete file references 列表处理 #14568、多 delete 序列化 #14573、filter 与 OpenAPI 对齐 #14658、plan status 一致性 #14643）等。
- **Parquet**：NameMapping 丢失修复（#14617）、variant 类型支持（#14588、#14081、#14261）、UUID ClassCastException 修复（#14027）。
- **Flink**：`DynamicCommitter` 幂等性（#14182/#14213）、`ManifestOutputFileFactory` 重建修复（#14358/#14385）、`DynamicIcebergSink` 缓存刷新（#14406/#14765）。
- **Spark**：3.4/3.5/4.0 各自传递 format-version 创建快照（#14170/#14169/#14163）、Z-order UDF 处理 `DateType` 修复（#14108）。
- **Kafka Connect**：合并 control topic 与 last persisted offsets（#14525）。
- **Vendor integrations**：AWS 的 HTTP 连接池复用（#14161）、多 catalog 凭证泄露修复（#14178）、bundle 排除日志依赖（#14225）。

同时将原位于 1.9.2 之前的 `## Past releases` 二级标题上移到 1.10.1 与 1.10.0 之间，使 1.10.0 及更早版本归入"历史发布"区段，结构更清晰。

### `site/mkdocs.yml` (+1/-1 lines)

**修改目的**：切换站点展示的最新 Iceberg 版本号。

**工作逻辑**：
将 `extra` 段中的 `icebergVersion: '1.10.0'` 修改为 `icebergVersion: '1.10.1'`。该变量被站点模板引用，用于在页面页脚/版本提示等位置展示当前推荐的 Iceberg 版本，并在部分文档片段中插值。修改后站点全局展示 1.10.1 为当前版本。

### `site/nav.yml` (+3/-1 lines)

**修改目的**：更新文档导航的版本分组，将 1.10.1 设为 Latest、1.10.0 移入 Previous。

**工作逻辑**：
将 `Latest (1.10.0)` 改为 `Latest (1.10.1)`（仍指向 `docs/docs/latest/mkdocs.yml`），并在 `Previous` 列表顶部新增 `1.10.0: '!include docs/docs/1.10.0/mkdocs.yml'`。这样导航栏的"Latest"直接呈现 1.10.1 文档，而 1.10.0 作为历史版本可被访问，保证了多版本文档的有序归档。

## 总结

本提交是 Iceberg 1.10.1 维护版本发布的配套站点更新，新增了覆盖 API/Core/Parquet/Flink/Spark/Kafka Connect/AWS 等模块的完整 1.10.1 修复清单，并将站点最新版本切换为 1.10.1、把 1.10.0 归入历史版本导航。它是版本发布流程的标准动作，为用户提供了了解 1.10.1 修复内容与查阅对应版本文档的入口，具有明确的实际指导价值。
