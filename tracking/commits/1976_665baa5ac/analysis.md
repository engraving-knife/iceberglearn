# 提交 1976：Doc: Remove Hive 2.x/3.x related docs in hive.md (#12700)

## 提交信息

- **序号**：1976 / 4088
- **哈希**：665baa5ac23f7743642ff51d6b7594952e60198e
- **短哈希**：665baa5ac
- **日期**：2025-04-09 09:29:24 +0200
- **作者**：jackylee
- **提交说明**：Doc: Remove Hive 2.x/3.x related docs in hive.md (#12700)
- **PR/Issue**：#12700

## 总体目的

本提交清理 Hive 集成文档中与 Hive 2.x / 3.x 相关的过时内容，使文档聚焦于 Hive 4.0.0 及以上版本的支持。由于从 Iceberg 1.8.0 起不再发布 Hive runtime connector，Hive 2.x/3.x 的集成路径已不再推荐，相关文档保留会造成误导。

具体动机：
- Iceberg 1.8.0 起不再发布 `iceberg-hive-runtime` jar，Hive 2.x/3.x 用户需改用 Iceberg 1.6.1 提供的 runtime connector，或升级到内置 Iceberg 集成的 Hive 4.0.0+。
- 文档原先的"Feature support"矩阵区分 Hive 2/3 与 Hive 4，并包含 Hive 2.3.x/3.1.x 的加载 jar、Tez 配置、MapReduce 引擎等说明，这些内容已过时。
- multi-engine-support 表格中 Hive 2/3 的"Latest Iceberg Support"需从 1.7.2 下调为 1.6.1，以反映不再发布 runtime connector 的事实。

## 如何达成设计目的

通过编辑 `hive.md` 与 `multi-engine-support.md` 两个文档文件完成内容清理与版本号修正。

## 修改详情

### `docs/docs/hive.md` (修改, +20/-57 lines)

**修改目的**：移除 Hive 2.x/3.x 专属说明，统一为 Hive 4.0.0+ 的支持描述。

**工作逻辑**：
- 删除"Hive 2/3 vs Hive 4"的功能支持矩阵表格，改为直接列出 Hive 4.0.0+ 支持的功能清单（建表、CTAS、删除、改表、迁移、读取、时间旅行、INSERT、CRUD、分支/标签等），并对清单项补全句号。
- "Enabling Iceberg support in Hive"段落改写：说明 1.8.0 起不再发布 Hive runtime connector，Hive 2.x/3.x 应使用 1.6.1 的 connector 或升级到 Hive 4.0.0+。
- 删除"Hive 2.3.x, Hive 3.1.x"小节（加载 runtime jar、`add jar` 示例、Tez 升级 TEZ-4248、tez-site.xml 配置、MapReduce 引擎说明、vectorization 警告等）。
- 删除 DDL Commands 段开头关于 Hive 2.3.x/3.1.x 限制与 `STORED BY ICEBERG` 差异的说明。
- "Hive query engines"由"2.3.x/3.1.x 支持 MapReduce 和 Tez，4.x 支持 Tez"简化为"4.x 支持 Tez"。
- 在 Global Hive catalog 段补充 HiveCatalog 支持 Hive 2.3.10 或 3.1.3 及以上。

### `site/docs/multi-engine-support.md` (修改, +4/-4 lines)

**修改目的**：修正 Hive 2/3 的最新支持版本与 runtime jar 链接。

**工作逻辑**：将 Hive 版本 2 与 3 的"Latest Iceberg Support"由 `1.7.2` 改为 `1.6.1`，runtime jar 下载链接相应改为指向 `1.6.1` 版本的 `iceberg-hive-runtime-1.6.1.jar`，对齐"1.8.0 起不再发布 runtime connector"的策略。

## 总结

文档清理提交，移除 `hive.md` 中 Hive 2.x/3.x 的过时集成说明（功能矩阵、runtime jar 加载、Tez/MapReduce 配置等），统一聚焦 Hive 4.0.0+；并在 `multi-engine-support.md` 中将 Hive 2/3 的最新 Iceberg 支持版本下调为 1.6.1、更新 runtime jar 链接，以反映 1.8.0 起不再发布 Hive runtime connector 的事实。
