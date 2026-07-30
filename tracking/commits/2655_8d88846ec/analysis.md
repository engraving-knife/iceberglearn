# 提交 2655：Docs: Add Spark 4.0 to lifecycle status (#14116)

## 提交信息

- **序号**：2655 / 4088
- **哈希**：8d88846ec46bfca60d73d239e8bb34a740f8f0ce
- **短哈希**：8d88846ec
- **日期**：2025-09-19 11:11:02 +0200
- **作者**：Ron Kapoor
- **提交说明**：Docs: Add Spark 4.0 to lifecycle status (#14116)
- **PR/Issue**：#14116

## 总体目的

本提交将 Spark 4.0 添加到 Iceberg 多引擎支持文档的生命周期状态表中。Apache Iceberg 的 `multi-engine-support.md` 文档维护了一个表格，列出各 Spark 版本的生命周期阶段（如 Maintained、End of Life 等）、支持的 Iceberg 起始版本和最新版本，以及对应的 Maven artifact 链接。

Spark 4.0 从 Iceberg 1.10.0 开始获得支持，随着其逐渐成熟和稳定，需要将其正式列入生命周期状态表，标记为 "Maintained"（维护中）状态，使用户能够清晰了解 Spark 4.0 的支持情况。

## 如何达成设计目的

在 `site/docs/multi-engine-support.md` 的 Spark 版本生命周期表中，于 Spark 3.5 行之后新增一行 Spark 4.0 的条目，包含生命周期状态、起始支持版本、最新版本和 Maven artifact 链接。

## 修改详情

### `site/docs/multi-engine-support.md` (+1/-0 lines)

**修改目的**：在生命周期表中添加 Spark 4.0 条目。

**工作逻辑**：在 Spark 3.5 行之后新增一行：
- 版本：4.0
- 生命周期阶段：Maintained（维护中）
- 起始支持版本：1.10.0
- 最新版本：`{{ icebergVersion }}`（使用文档变量自动填充当前版本）
- Maven artifact 链接：`iceberg-spark-runtime-4.0_2.13`（注意使用 Scala 2.13，而 3.4/3.5 使用 Scala 2.12）

## 总结

这是一次简单的文档更新，将 Spark 4.0 正式纳入 Iceberg 的多引擎支持生命周期表，标记为维护中状态，起始支持版本为 1.10.0。这为用户提供了 Spark 4.0 支持状态的官方参考信息。不涉及任何代码变更。
