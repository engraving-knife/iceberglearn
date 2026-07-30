# 提交 4033：Spark: Backport split-size session conf to 3.5 and 4.0 (#17199)

## 提交信息

- **序号**：4033 / 4088
- **哈希**：f2875fdccecfdc0e8ca6b09b248deb1b66bccb08
- **短哈希**：f2875fdcc
- **日期**：2026-07-14 14:11:46 -0700
- **作者**：Gera Shegalov
- **提交说明**：Spark: Backport split-size session conf to 3.5 and 4.0 (#17199)
- **PR/Issue**：#17199（backport of #16154）

## 总体目的

本提交是将提交 4031（#16154，"Spark: Add session-level split size override"）backport 到 Spark 3.5 和 4.0 两个版本。原提交只在 Spark 4.1 中新增了会话级 split size 覆盖配置 `spark.sql.iceberg.read.split-size`，本次将相同改动应用到 `spark/v3.5` 和 `spark/v4.0`。

## 如何达成设计目的

将 4031 在 `spark/v4.1` 下的 4 个文件改动（不含文档，文档是版本无关的已在 4031 更新）原样复制到 `spark/v3.5` 和 `spark/v4.0` 对应路径，共 8 个文件。代码逻辑与 4031 完全一致，仅包路径前缀不同。

## 修改详情

### `spark/v3.5/spark/...` (4 files)

**修改目的**：backport 到 Spark 3.5。

**工作逻辑**：与 4031 相同的改动：
- `SparkSQLProperties.java`：新增 `READ_SPLIT_SIZE = "spark.sql.iceberg.read.split-size"`。
- `SparkReadConf.java`：`splitSizeOption()` 和 `splitSize()` 加入 `.sessionConf(SparkSQLProperties.READ_SPLIT_SIZE)`。
- `TestSparkReadConf.java`、`TestSparkScan.java`：测试覆盖。

### `spark/v4.0/spark/...` (4 files)

**修改目的**：backport 到 Spark 4.0。

**工作逻辑**：与 Spark 3.5 完全相同的改动。

## 总结

本提交是 4031 的跨版本 backport，将会话级 split size 覆盖配置推广到 Spark 3.5 和 4.0，确保三个支持的 Spark 版本（3.5、4.0、4.1）功能一致。代码逻辑无差异，仅是并行维护多个 Spark 版本分支的常规操作。文档已在 4031 中统一更新，本次 backport 不含文档改动。
