# 提交 0577：Site: Update release notes for 1.5.0

## 提交信息

- **序号**：0577 / 4088
- **哈希**：4e149ca4d1cb3da570d1c860608432379ac9296a
- **短哈希**：4e149ca4d
- **日期**：2024-03-11 16:28:07 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Site: Update release notes for 1.5.0 (#9835)
- **PR/Issue**：#9835

## 总体目的

Apache Iceberg 1.5.0 于 2024-03-11 正式发布，本提交同步更新官方网站文档，将发布说明、运行时 jar 下载链接以及多引擎支持矩阵对齐到 1.5.0 版本。这是一次纯文档（site/docs）提交，不涉及任何代码改动，属于发布流程的收尾工作。具体目标有三：

1. 在 `releases.md` 顶部补齐 1.5.0 的完整发布说明（changelog），并据引擎支持变化增删顶部的 runtime jar 下载入口；
2. 在 `multi-engine-support.md` 中调整 Spark / Flink 各版本的生命周期阶段与"最新支持的 Iceberg 版本"，反映 1.5.0 移除 Spark 3.2、移除 Flink 1.15、新增 Flink 1.18 的支持矩阵变化；
3. 把"当前发布（1.5.0）"与"历史发布（1.4.3 及更早）"在文档结构上区分开，新增 `## Past releases` 标题把旧版本归入历史区。

## 如何达成设计目的

整体设计是"文档同步"：跟随 1.5.0 的实际支持矩阵与变更内容，逐文件更新两张核心表格和发布说明段落，使官网展示与仓库实际状态一致。具体做法：

- 在 `releases.md` 顶部下载列表中删除已不再支持的 Spark 3.2 runtime jar 与 Flink 1.15 runtime jar 链接，并新增 Flink 1.18 runtime jar 链接；
- 在 Maven 依赖示例之后、原 1.4.3 段落之前，插入全新的 `### 1.5.0 release` 段落，按 API / Core / Spark / Flink / Parquet / Kafka-Connect / Spec / Vendor Integrations / Dependencies 分组罗列本次主要变更（每项附 PR 链接），并补充一条关于 JDBC catalog 启用 view 支持的配置说明；
- 把原先位于 1.4.3 与 1.4.2 之间的 `## Past releases` 标题移除，改在 1.5.0 段落之后重新添加 `## Past releases`，使 1.5.0 成为当前发布、1.4.3 及更早归入历史；
- 在 `multi-engine-support.md` 中将 Spark 3.2 由 "Deprecated / `{{ icebergVersion }}`" 改为 "End of Life / 1.4.3"（冻结最后支持版本），Flink 1.15 由 "`{{ icebergVersion }}`" 改为冻结到 "1.4.3"，并对各表格做列宽对齐。

## 修改详情

### `site/docs/multi-engine-support.md`

**修改目的**：把 Spark 与 Flink 的引擎支持矩阵对齐到 1.5.0，反映"移除 Spark 3.2、移除 Flink 1.15、新增 Flink 1.18"的支持变化。

**工作逻辑**：

Spark 表（约第 63-69 行）：

- Spark 3.2：生命周期由 `Deprecated` 改为 `End of Life`，"Latest Iceberg Support"由模板变量 `{{ icebergVersion }}`（动态跟随当前版本，即 1.5.0）冻结为固定值 `1.4.3`，runtime jar 链接也由模板版本改为固定 1.4.3 版本。这意味着 Spark 3.2 在 1.5.0 中已被移除（对应 changelog 中 "Remove support for Spark 3.2 (#9295)"），其最后受支持的 Iceberg 版本定格在 1.4.3。
- Spark 3.3 / 3.4 / 3.5：仍为 `Maintained`，"Latest Iceberg Support"保持 `{{ icebergVersion }}`（现解析为 1.5.0），表示这些 Spark 版本继续被当前 Iceberg 支持。
- Spark 3.1：仅做列对齐（去除多余空格），状态不变。
- 整张表做了 Markdown 列宽对齐（`|` 间补空格使各列对齐），纯格式调整。

Flink 表（约第 76-89 行）：

- Flink 1.15：生命周期保持 `End of Life`，但"Latest Iceberg Support"由 `{{ icebergVersion }}` 冻结为 `1.4.3`，runtime jar 链接也固定到 1.4.3。对应 changelog 中 "Remove Flink 1.15"。
- Flink 1.16：保持 `Deprecated`，仍跟随 `{{ icebergVersion }}`。
- Flink 1.17：保持 `Maintained`，仍跟随 `{{ icebergVersion }}`。
- Flink 1.18：保持 `Maintained`，"Initial Iceberg Support"为 `1.5.0`，即 1.5.0 起新增对 Flink 1.18 的支持（对应 changelog "Adds support for 1.18 version #9211"）。
- 同样做了表格列宽对齐。

### `site/docs/releases.md`

**修改目的**：补齐 1.5.0 的发布说明并调整 runtime jar 下载入口，使 1.5.0 成为文档中的当前发布版本。

**工作逻辑**：

1. 顶部 runtime jar 下载列表（约第 26-35 行）：
   - 删除 `Spark 3.2_2.12 / 3.2_2.13 runtime Jar` 链接（Spark 3.2 不再被 1.5.0 支持）；
   - 删除 `Flink 1.15 runtime Jar` 链接（Flink 1.15 不再被 1.5.0 支持）；
   - 新增 `Flink 1.18 runtime Jar` 链接，置于 Flink 1.17 之前，与多引擎表一致。

2. 新增 `### 1.5.0 release` 段落（约第 69-135 行）：开头说明 "Apache Iceberg 1.5.0 was released on March 11, 2024"，随后按模块分组列出主要变更，每组以 `* 模块名` 起头、`- 条目 (PR链接)` 列举。关键变更包括：
   - **API**：扩展 FileIO 并新增 EncryptingFileIO、TableMetadata 中追踪分区统计、views 新增 sqlFor API；
   - **Core**：REST/JDBC catalog 支持 view、Avro 文件 AES GCM 加密、StandardEncryptionManager、REST catalog table session 缓存、view 元数据压缩、列统计过滤在 planning 后启用等；
   - **Spark**：移除 Spark 3.2 支持、Spark 3.4/3.5 通过 SQL 支持 view、executor cache locality、delete manifest rewrites、加密输出文件、Spark UI metrics、并行 add_files、文件与分区级删除粒度等；
   - **Flink**：移除 Flink 1.15、新增 1.18 支持、IcebergSource 发射 watermark 及 watermark 读选项；
   - **Parquet**：row group filter 支持 INT96、unsafe Parquet ID fallback 系统配置；
   - **Kafka-Connect**：初始项目搭建与 event 数据结构、带 data writers 与 converters 的 sink connector（1.5.0 新引入的模块）；
   - **Spec**：分区统计 spec、纳秒时间戳类型、多参数 transform；
   - **Vendor Integrations**：AWS（Glue 描述、S3 Access Grants、DB URI 去尾斜杠）、Azure（ADLSv2 FileIO、DelegateFileIO）、Nessie（views、warehouse 去尾斜杠、URI 推断默认 API 版本）；
   - **Dependencies**：Nessie 0.77.1、ORC 1.9.2、Arrow 15.0.0、AWS SDK 2.24.5、Azure SDK 1.2.20、Google cloud 26.28.0。
   - 段末附一条 Note：启用 JDBC catalog 的 view 支持需将 catalog properties 中的 `jdbc.schema-version` 配置为 `V1`。

3. 调整 `## Past releases` 标题位置：原标题位于 1.4.3 与 1.4.2 段落之间（将 1.4.2 及更早归入历史），现删除该处标题，改在 1.5.0 段落之后新增 `## Past releases`，使 1.5.0 为当前发布、1.4.3 及更早全部归入历史区，结构更清晰。

## 小结

本提交是 1.5.0 发布的文档收尾，通过更新 `releases.md` 与 `multi-engine-support.md` 把官网的发布说明、runtime jar 入口、Spark/Flink 支持矩阵对齐到 1.5.0 实际状态，并据支持变化冻结已移除引擎（Spark 3.2、Flink 1.15）的最后版本为 1.4.3、新增 Flink 1.18。提交为纯文档、无代码与构建改动，回迁到 1.4.x 时通常无需 cherry-pick（1.4.x 是被 1.5.0 取代的历史分支，其文档应继续描述 1.4.x 自身的发布说明），但若需要在 1.4.x 文档中引用 1.5.0 的支持矩阵作为对照，可参考本提交的表格结构。
