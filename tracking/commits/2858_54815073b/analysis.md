# 提交 2858：Doc: Remove Spark 3 specific wordings in docs (#14357)

## 提交信息

- **序号**：2858 / 4088
- **哈希**：54815073b1bf0335b9747d9cb85015ab6a586d90
- **短哈希**：54815073b
- **日期**：2025-11-10 10:11:43 -0800
- **作者**：jackylee
- **提交说明**：Doc: Remove Spark 3 specific wordings in docs (#14357)
- **PR/Issue**：#14357

## 总体目的

这个提交更新了 Spark 相关文档，移除了文档中针对 Spark 3 的特定措辞，使文档适用于更广泛的 Spark 版本（包括 Spark 4.0）。随着 Iceberg 项目对 Spark 4.0 的支持日益成熟，文档中大量"Spark 3"的措辞已经过时且具有误导性，需要改为更通用的表述。

具体改动包括：
1. 将硬编码的 `iceberg-spark-runtime-3.5_2.12` 包名替换为模板变量 `{{ sparkVersionMajor }}`，使文档能根据配置自动适配不同 Spark 版本。
2. 将"Spark 3"措辞改为"Spark"或更准确的版本描述。
3. 移除针对 Spark 3.0 及更早版本的过时注意事项。
4. 在 `site/mkdocs.yml` 中添加 `sparkVersionMajor` 变量定义。

## 如何达成设计目的

通过修改 7 个文档和配置文件，系统性地替换 Spark 3 特定措辞：

1. 使用 MkDocs 模板变量 `{{ sparkVersionMajor }}` 替换硬编码的 Spark 版本号，实现文档的版本自适应。
2. 移除或更新针对旧版 Spark 的注意事项和限制说明。
3. 在 Spark 4.0 中，存储过程（stored procedures）原生支持无需 SQL extensions，文档对此进行了说明。

## 修改详情

### `docs/docs/spark-getting-started.md` (+4/-4 lines)

**修改目的**：移除 Spark 3 特定措辞，使用版本模板变量。

**工作逻辑**：将标题"Using Iceberg in Spark 3"改为"Using Iceberg in Spark"；将硬编码的 `iceberg-spark-runtime-3.5_2.12` 替换为 `iceberg-spark-runtime-{{ sparkVersionMajor }}`，在 spark-shell 和 spark-sql 命令示例中都做了相应替换。

### `docs/docs/spark-procedures.md` (+3/-1 lines)

**修改目的**：更新存储过程的 Spark 版本说明。

**工作逻辑**：将"Stored procedures are only available when using Iceberg SQL extensions in Spark 3"改为更详细的说明：Spark 3.x 仍需 SQL extensions，但 Spark 4.0 原生支持存储过程（无需 extensions），但注意 Spark 4.0 中存储过程是大小写敏感的。

### `docs/docs/spark-queries.md` (+2/-6 lines)

**修改目的**：移除 Spark 3 特定措辞和过时注意事项。

**工作逻辑**：将"In Spark 3, tables use identifiers..."改为"In Spark, tables use identifiers..."；将"Spark 3.3 and later supports time travel"改为"Spark supports time travel"；移除关于 Spark 3.0 及更早版本不支持 `option` with `table` 的注意事项（因为该版本已过时）。

### `docs/docs/spark-structured-streaming.md` (+1/-3 lines)

**修改目的**：移除针对 Spark 3.0 的过时说明。

**工作逻辑**：移除"If you're using Spark 3.0 or earlier, you need to use `.option("path", ...)` instead of `.toTable(...)`"的注意事项；修正一处关于分区排序的句子断行问题。

### `docs/docs/spark-writes.md` (+18/-16 lines)

**修改目的**：将功能支持表格中的"Spark 3"改为"Spark"，更新相关措辞。

**工作逻辑**：将功能支持表头从"Spark 3"改为"Spark"；移除多处"Spark 3 added support for..."中的版本特定措辞，改为"Spark supports..."。功能支持表格的内容和注释保持不变，仅更新表头表述。

### `site/docs/spark-quickstart.md` (+6/-6 lines)

**修改目的**：将快速入门指南中的硬编码 Spark 版本替换为模板变量。

**工作逻辑**：将所有 `iceberg-spark-runtime-3.5_2.12` 替换为 `iceberg-spark-runtime-{{ sparkVersionMajor }}`，涵盖 CLI、spark-defaults.conf、SparkSQL、Spark-Shell、PySpark 等多个示例场景，以及 Maven 下载链接。

### `site/mkdocs.yml` (+1/-0 lines)

**修改目的**：定义 sparkVersionMajor 模板变量。

**工作逻辑**：在 `extra` 配置中添加 `sparkVersionMajor: '4.0_2.13'`，使文档模板变量 `{{ sparkVersionMajor }}` 能被正确解析为 `4.0_2.13`。注意这里默认值为 Spark 4.0，反映了 Iceberg 对 Spark 4.0 的重点支持方向。

## 总结

这个提交系统性地清理了 Spark 文档中针对 Spark 3 的特定措辞，使文档适用于包括 Spark 4.0 在内的更广泛版本。通过引入 `sparkVersionMajor` 模板变量实现版本号自适应，移除过时注意事项，并补充了 Spark 4.0 存储过程原生支持的说明。这反映了 Iceberg 项目从以 Spark 3 为主向同时支持 Spark 3.x 和 4.0 的文档策略转变。
