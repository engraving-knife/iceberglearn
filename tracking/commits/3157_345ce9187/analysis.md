# 提交 3157：Docs: Add C++ library to implementation status (#15107)

## 提交信息

- **序号**：3157 / 4088
- **哈希**：345ce9187c0d0847045f0022ed0eac1644e01581
- **短哈希**：345ce9187
- **日期**：2026-01-26 13:56:40 +0800
- **作者**：Feiyang Li
- **提交说明**：Docs: Add C++ library to implementation status
- **PR/Issue**：#15107

## 总体目的

Iceberg 官网 `site/docs/status.md` 是一张"实现状态矩阵"页面，逐项列出 Iceberg 规范在各语言库（Java、PyIceberg、Rust、Go）中的支持情况，供用户选型与跨语言能力对比。随着 Apache Iceberg C++ 库（`apache/iceberg-cpp`）日趋成熟，其 0.2.0 版本即将发布（参见紧随其后的 #15141 博客），社区需要将该新语言实现纳入官方状态矩阵，使 C++ 生态用户能直观了解当前已支持与未支持的能力边界。

本提交在整个 `status.md` 文档的所有能力表格中新增一列 `C++`，并依据 iceberg-cpp 当前的实际实现情况逐格填入 `Y`/`N`。这是一次纯文档改动，不涉及任何代码逻辑，但覆盖面广——文档中从"实现库列表"到"Data Types、Data File Formats、File IO、Table Maintenance/Update/Read/Write Operations、Catalogs（REST/SQL/Glue）及其 Table/View/Namespace 子项"的每一张表都新增了 C++ 列。

## 如何达成设计目的

作者在文档顶部的实现库列表中加入指向 `github.com/apache/iceberg-cpp/releases` 的 C++ 条目，随后对文档中每一张 Markdown 表格统一做"加列"操作：表头增加 `C++` 列，分隔行相应增加一列，并按 C++ 库现状填入支持标记。改动模式机械但需要准确反映 C++ 实际能力，因此本质是对 C++ 库能力的一次盘点与对外公示。

## 修改详情

### `site/docs/status.md` (+236/-235 lines)

**修改目的**：在实现状态矩阵的所有能力表格中新增 C++ 语言列，公示 iceberg-cpp 的能力支持情况。

**工作逻辑**：文档改动可分为以下几类，整体反映了 C++ 库当前的能力版图：

1. **实现库列表**：新增 `- [C++](https://github.com/apache/iceberg-cpp/releases)` 条目。

2. **Data Types**：绝大多数类型 C++ 标记为 `Y`（boolean/int/long/float/double/decimal/date/time/timestamp/timestamptz/string/fixed/binary/list/map/struct）；`timestamp_ns`、`timestamptz_ns`、`uuid`、`variant` 标记为 `N`，表明纳秒时间戳、UUID 与 Variant 类型尚未支持。

3. **Data File Formats**：`Parquet` 与 `Avro` 为 `Y`，`ORC` 与 `Puffin` 为 `N`。

4. **File IO**：仅 `Local Filesystem` 为 `Y`，Hadoop/S3/GCS/ADLS 等分布式与云存储均为 `N`，说明 C++ 库目前尚未集成云原生文件 IO。

5. **Table Maintenance Operations（V1/V2）**：`Update schema`、`Update partition spec`、`Update table properties`、`Replace sort order`、`Update table location`、`Update statistics` 均为 `Y`；`Update partition statistics`、`Expire snapshots`、`Manage snapshots` 为 `N`。

6. **Table Update Operations**：仅 `Append data files` 为 `Y`，`Rewrite files`、`Rewrite manifests`、`Overwrite files`、`Delete files`、`Row delta` 均为 `N`。

7. **Table Read Operations**：`Plan with data file` 与 `Read data file` 为 `Y`；`Plan with puffin statistics` 为 `N`；V2 中 `Plan with position deletes`、`Plan with equality deletes` 为 `Y`，但 `Read with position deletes`、`Read with equality deletes` 为 `N`（即能规划删除文件但读取时尚未应用删除语义）。

8. **Table Write Operations**：`Append data`、`Write position deletes`、`Write equality deletes` 均为 `N`，表明写入路径（相对扫描读取）尚不完整。

9. **Catalogs**：REST Catalog 的 Table 操作（V1/V2）全部 `Y`、Namespace 操作全部 `Y`，但 View 操作全部 `N`；SQL Catalog 与 Glue Catalog 的全部子项均为 `N`，说明 C++ 库目前仅实现了 REST Catalog 客户端。

## 总结

本提交通过在官方实现状态矩阵的每一张能力表格中新增 C++ 列，正式将 iceberg-cpp 纳入 Iceberg 多语言生态公示体系，清晰呈现了 C++ 库当前在数据类型、文件格式、IO、表维护/更新/读/写操作及各类 Catalog 上的支持边界，为 C++ 用户选型与社区后续开发优先级提供了权威参考，也呼应了即将发布的 C++ 0.2.0 版本。
