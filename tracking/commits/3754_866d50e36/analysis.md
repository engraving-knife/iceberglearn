# 提交 3754：Docs: Add release notes for 1.11.0 (#16431)

## 提交信息

- **序号**：3754 / 4088
- **哈希**：866d50e368e8b6382d25a59457a6936aded39867
- **短哈希**：866d50e36
- **日期**：2026-05-20 11:53:19 -0700
- **作者**：Aihua Xu
- **提交说明**：Docs: Add release notes for 1.11.0 (#16431)
- **PR/Issue**：#16431

## 总体目的

本提交为 Iceberg 1.11.0 版本添加完整的发布说明，并同步更新文档站点的版本相关配置和多引擎支持表，使文档站点正确反映 1.11.0 为最新发布版本。

1.11.0 是一个包含 bug 修复和新功能的重要版本，于 2026-05-19 发布。本次发布包含多项重要变更：
- **弃用/停止支持**：弃用 Position delete files、移除 Java 11 支持、弃用 Spark 3.4、移除 Flink 1.19 支持、清理 1.11.0 前的弃用 API。
- **规范**：引入 SQL UDF 规范、恢复 snapshot 的 added-rows 字段、澄清 V3 geometry 类型限制。
- **新功能**：支持 Spark 4.1、Flink 2.1；新增 geospatial bounding box、V4 manifest 基础类型、content stats、partition stats scan、SQL UDF 等众多功能。
- **多模块 bug 修复**：Core、Arrow、Parquet、ORC、Spark、Flink、Hive、Kafka Connect、Open API 等模块的大量修复。

同时更新多引擎支持表，新增 Spark 4.1 和 Flink 2.1 条目，将 Flink 1.19 标记为 End of Life。

## 如何达成设计目的

通过四个文件的修改实现：
1. 在 `site/docs/releases.md` 中新增 1.11.0 发布说明段落（按模块分类列出所有重要变更），更新下载链接列表（新增 Spark 4.1 和 Flink 2.1，移除 Flink 1.19）。
2. 在 `site/docs/multi-engine-support.md` 的支持表中新增 Spark 4.1 和 Flink 2.1 条目，将 Flink 1.19 状态从 Maintained 改为 End of Life。
3. 在 `site/mkdocs.yml` 中更新版本变量和 flink 版本信息。
4. 在 `site/nav.yml` 中更新 Latest 标签为 1.11.0，并在 Previous 列表新增 1.10.2。

## 修改详情

### `site/docs/releases.md` (+173/-2 lines)

**修改目的**：新增 1.11.0 发布说明并更新下载链接。

**工作逻辑**：
- 下载链接新增 Spark 4.1 (Scala 2.13) 和 Flink 2.1 runtime Jar，移除 Flink 1.19 runtime Jar。
- 新增 1.11.0 发布说明段落，按模块分类列出重要变更（含 PR 链接）：
  - **Deprecation / End of Support**：弃用 Position delete files (#14045)、移除 Java 11 支持 (#14400)、弃用 Spark 3.4 (#14099)、移除 Flink 1.19 支持 (#13714)、清理 1.11.0 前弃用 API (#14059)。
  - **Spec**：SQL UDF 规范 (#14117)、恢复 added-rows 字段 (#14048)、澄清 V3 geometry 限制 (#14250)。
  - **API**：geospatial bounding box (#12667)、V4 manifest 基础类型 (#15049)、content stats 类 (#13933)、cleanupMode (#14287)、NOT IN/!= 优化 (#14593)、partition stats scan (#14640)、registerView (#14868)、overwrite-aware 注册 (#15525)、FileIO to Scan API (#15561) 等约 15 项。
  - **Core**：File Format API (#12774)、v4 structs builders (#16092)、TrackedFile (#16253/#15854)、unique table locations (#12892)、REST catalog freshness (#14398)、register view (#14870)、REST Scan Planning (#13400)、Avro timestamp-millis (#14401)、variant shredding 数据丢失修复 (#15087)、503 不清理 (#15051)、sequence number 冲突可重试 (#15126) 等约 20 项。
  - **Arrow**：TIMESTAMP_MILLIS 修复 (#14499)、unsigned int 对齐 (#16006)、ArrowFormatModel (#15258)、DELTA 编码向量化读取 (#15362/#15373) 等。
  - **Parquet**：variantShreddingFunc (#14153)、VARIANT 在 filter 中的处理 (#14279)、page-version (#15700)、ParquetFormatModel (#15253) 等。
  - **ORC**：_row_id 和 _last_updated_sequence_number reader (#15776)、连接泄漏修复 (#16086)、ORCFormatModel (#15255)。
  - **Spark**：Spark 4.1 支持 (#14155)、Limit pushdown (#14615)、远程 scan planning (#14963)、MERGE INTO schema evolution (#14970)、adaptive split sizing (#16088) 等约 30 项。
  - **Flink**：Flink 2.1 支持 (#13714)、uid-suffix (#14063)、_row_id reader (#14148)、DynamicCommitter 幂等 (#14182)、DV 支持 (#14414/#14197)、列删除 (#14728)、大小写不敏感 (#14729)、Variant 支持 (#15265)、纳秒精度 (#15475)、slot sharing group 等 约 35 项。
  - **Hive**：锁选择 (#14236)、加密表元数据完整性 (#14685)、view 更新 (#14831)、加密密钥丢失修复 (#14427)、Variant 快照修复 (#15964)。
  - **Kafka Connect**：GenericFileWriterFactory (#14328)、coordinator leader 选举 (#14395)、offset 校验 (#14510)、table UUID 校验 (#14979)、CVE 修复 (#14985/#15440)、VARIANT 支持 (#15283) 等。
  - **Open API**：移除 runtime Jar (#16163)。
  - **Build**：多处 CVE 修复和依赖升级。

### `site/docs/multi-engine-support.md` (+3/-1 lines)

**修改目的**：更新 Spark 和 Flink 版本支持表。

**工作逻辑**：
- Spark 支持表新增 4.1 行：状态 Maintained，首个支持版本 1.11.0，最新版本 {{ icebergVersion }}，附 runtime Jar 链接。
- Flink 支持表：
  - 1.19 状态从 Maintained 改为 End of Life，最后支持版本 1.10.2（链接固定为 1.10.2）。
  - 新增 2.1 行：状态 Maintained，首个支持版本 1.11.0，最新版本 {{ icebergVersion }}。

### `site/mkdocs.yml` (+5/-5 lines)

**修改目的**：更新文档站点的版本变量。

**工作逻辑**：
- `icebergVersion` 从 `1.10.2` 更新为 `1.11.0`。
- `flinkVersion` 和 `flinkVersionMajor` 更新为指向 Flink 2.1（从 2.0）。
- 其他相关版本变量同步调整。

### `site/nav.yml` (+2/-1 lines)

**修改目的**：更新导航版本标签。

**工作逻辑**：
- Latest 标签从 `Latest (1.10.2)` 更新为 `Latest (1.11.0)`。
- Previous 列表顶部新增 `1.10.2` 条目。

## 总结

本提交为 Iceberg 1.11.0 版本添加了完整的发布说明，按模块分类列出了大量新功能、bug 修复和弃用项（含 PR 链接），涵盖 Spec、API、Core、Arrow、Parquet、ORC、Spark、Flink、Hive、Kafka Connect、Open API、Build 等模块。同时更新了多引擎支持表（新增 Spark 4.1 和 Flink 2.1，Flink 1.19 标记为 EOL）、下载链接和站点版本变量。1.11.0 是一个重要版本，引入了 V4 基础设施、Spark 4.1/Flink 2.1 支持、SQL UDF 规范、geospatial 支持等众多新特性，同时弃用了 Position delete、Java 11、Spark 3.4、Flink 1.19 等。
