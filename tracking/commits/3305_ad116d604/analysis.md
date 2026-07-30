# 提交 3305：Docs: Fix several nit issues in docs (#15419)

## 提交信息

- **序号**：3305 / 4088
- **哈希**：ad116d604dbeb2a4b4115ecba639d22c476ddcee
- **短哈希**：ad116d604
- **日期**：2026-02-23
- **作者**：Anshul Baliga
- **提交说明**：Docs: Fix several nit issues in docs (#15419)
- **PR/Issue**：#15419

## 总体目的

该提交修复了 Iceberg 文档中多处细微的语言（nit）问题，涉及拼写、语法和用词修正。文档质量直接影响用户对项目的理解和使用体验：错误的拼写和不当的语法会降低文档的专业性和可信度，甚至可能导致用户对技术细节的误解。此类"nit fix"提交虽然每处改动都很小，但累积起来能显著提升文档的整体严谨度。

本次修改覆盖五个文档文件，涉及 AWS 集成指南、Flink 配置与维护指南、Spark 存储过程指南以及 Iceberg 核心规范（spec），跨越项目文档的多个重要领域。这些文档是用户了解 Iceberg 的 AWS 目录选型、Flink 连接器配置、Spark 压缩存储过程以及表格式规范的第一手资料，准确性至关重要。

## 如何达成设计目的

通过逐一修正五个文档文件中的语法/拼写错误，每处改动均为单词级的用词修正，不改变文档的技术内容和结构。修改包括主谓一致（catalogs provide）、动词时态（provides）、拼写错误（planing → planning、partital → partial、object → objects）等。

## 修改详情

### `docs/docs/aws.md` (+2/-2 lines)

**修改目的**：修正 AWS 目录选型指南中的语法错误。

**工作逻辑**：
两处修改：
- 第 273 行："Glue and DynamoDB catalog provides" → "Glue and DynamoDB catalogs provide"。原句主语为复数（Glue 和 DynamoDB 两个 catalog），动词应为复数形式 `provide`，同时 `catalog` 也应为复数 `catalogs` 与主语一致。
- 第 275 行："provide efficient query performance" → "provides efficient query performance"。原句主语为单数 "DynamoDB catalog"，动词应为单数形式 `provides`。

### `docs/docs/flink-configuration.md` (+1/-1 lines)

**修改目的**：修正 Flink 配置文档中的拼写错误。

**工作逻辑**：
将 `max-allowed-planning-failures` 配置项描述中的 "scan planing failure" 改为 "scan planning failure"。`planing` 是 `planning` 的拼写错误（漏掉一个 n），这是文档中常见的拼写疏漏。

### `docs/docs/flink-maintenance.md` (+1/-1 lines)

**修改目的**：修正 Flink 维护文档中的语法错误。

**工作逻辑**：
将 `location(string)` 方法描述中的 "recursive listing the candidate files" 改为 "recursive listing of the candidate files"。原句 "listing the candidate files" 在此处缺少介词 `of`，改为 "listing of the candidate files" 使语法更通顺，表达"候选文件的递归列举"之意。

### `docs/docs/spark-procedures.md` (+1/-1 lines)

**修改目的**：修正 Spark 存储过程文档中的拼写错误。

**工作逻辑**：
将 `partial-progress.max-failed-commits` 配置项默认值说明中的 `partital-progress.max-commits` 改为 `partial-progress.max-commits`。`partital` 是 `partial` 的拼写错误（字母 t 和 i 位置颠倒），该默认值引用的是同一配置组中的另一个参数名，拼写错误会导致用户无法正确关联两个参数。

### `format/spec.md` (+1/-1 lines)

**修改目的**：修正 Iceberg 规范文档中的语法错误。

**工作逻辑**：
将 Sort Orders 段落中的 "a list of JSON object, each of which" 改为 "a list of JSON objects, each of which"。原句 "a list of JSON object" 中 object 应为复数 objects（与 "a list of" 搭配），这是 Iceberg 表格式规范中描述排序顺序序列化格式的关键说明，修正后语法正确。

## 总结

本次提交修复了分布在五个文档文件中的六处细微拼写和语法错误，涵盖 AWS、Flink、Spark 集成文档和核心规范。虽然每处改动都很小，但修正了主谓一致、介词缺失和字母拼写等问题，提升了文档的专业性和准确性，特别是 `spark-procedures.md` 中参数名拼写错误的修正避免了用户理解配置关联时的困惑。
