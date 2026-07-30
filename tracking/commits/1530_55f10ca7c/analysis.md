# 提交 1530 55f10ca7c 分析

## 提交信息
- 哈希：55f10ca7cd460cb9ce4a34ef72e69ed2e843b935
- 日期：2024-12-23（Mon Dec 23 10:46:01 2024 +0800）
- 作者：Renjie Liu <liurenjie2008@gmail.com>
- 消息：Doc: Add status page for different implementations. (#11772)
- 合作者：Fokko Driesprong <fokko@apache.org>（多次 review 提交反馈）

## 总体目的

Apache Iceberg 的表格式规范（Table Spec）是一个语言无关的开放规范，社区在多个语言中分别实现了该规范：Java（参考实现，最完整）、PyIceberg（Python）、Rust、Go。各实现的成熟度与覆盖能力差异很大：Java 实现最全面，支持所有 spec 特性；PyIceberg 持续追赶；Rust 与 Go 实现相对早期，覆盖核心读写但许多维护操作尚未实现。

在此之前，社区缺乏一个统一的、可对照的"实现能力矩阵"页面，用户与开发者难以快速了解：
- 某个实现是否支持某个数据类型、文件格式、Catalog 操作？
- 某个实现是否支持 V2 spec 的 row-level delete（position delete / equality delete）？
- 某个 Catalog（REST / SQL / Glue / Hive）在不同实现中的表/视图/命名空间操作覆盖情况？

本提交新增 `site/docs/status.md` 文档，以表格形式系统化地展示 Java / PyIceberg / Rust / Go 四个实现的能力矩阵，覆盖数据类型、数据文件格式、文件 IO、表维护操作、表更新操作、表读操作、表写操作，以及四种 Catalog（REST、SQL、Glue、Hive Metastore）的表操作、视图操作、命名空间操作。同时将该页面注册到站点导航 `site/nav.yml`，使其在文档站点的"Specs & APIs"区块可访问。

这一页面对社区有多重价值：
1. **用户选型**：帮助用户根据自身技术栈与所需特性选择合适的实现；
2. **贡献者导向**：清晰标识各实现的缺口，引导贡献者补齐未实现的能力；
3. **跨实现协作**：为各实现团队提供对齐基准，促进规范的一致性实现。

从提交消息可见，本 PR 经历多轮 review（Fokko Driesprong 多次提交反馈），最终调整了页面位置、命名（去掉 "implementations" 的 's'）、补充了 Avro 数据文件格式与 Puffin 统计信息等条目。

## 如何达成设计目的

通过两个文件修改达成：
1. 新建 `site/docs/status.md`：Markdown 文档，使用 YAML front matter（title）+ 多个 Markdown 表格，按能力维度逐项列出四个实现的 Y/N 状态。
2. 修改 `site/nav.yml`：在导航的"Specs & APIs"区块插入"Implementation status"条目，指向 status.md。

文档结构按 Iceberg 规范的能力域组织，每个域内按 Table Spec V1 / V2 分组（V2 引入 row-level delete、branch/branching 等），表格列固定为 Java / PyIceberg / Rust / Go，单元格用 `Y`（已实现）或 `N`（未实现）标记。这种结构便于横向对比实现、纵向对比能力，且易于后续维护（新增能力只需加行，新增实现只需加列）。

### 修改详情

#### `site/docs/status.md`（新增文件，369 行）

**修改目的**：创建实现能力矩阵页面。

**工作逻辑**：
- **YAML front matter**：`title: "Implementation Status"`，供 MkDocs 渲染页面标题。
- **License 头部**：标准 Apache 2.0 许可声明，符合 Apache 项目文件规范。
- **Libraries 区块**：列出四个实现的制品链接（Maven Central、PyPI、crates.io、pkg.go.dev），方便用户获取。
- **Data Types 表**：列出 spec 定义的全部数据类型（boolean、int、long、float、double、decimal、date、time、timestamp、timestamptz、timestamp_ns、timestamptz_ns、string、uuid、fixed、binary、variant、list、map、struct）。四个实现全部标记为 Y，表明基础数据类型在各实现中均已覆盖。其中 `variant` 是较新的数据类型（用于半结构化数据），`timestamp_ns`/`timestamptz_ns` 是纳秒精度时间戳，这些较新类型也已被四者支持。
- **Data File Formats 表**：Parquet（四者全 Y）、ORC（仅 Java Y）、Puffin（仅 Java Y）、Avro（仅 Java Y）。反映出 PyIceberg/Rust/Go 目前仅支持 Parquet，其他格式尚未实现。这正是 PR review 中"Add avro data file formats"反馈的补充内容。
- **File IO 表**：Local Filesystem、Hadoop Filesystem、S3 Compatible、GCS Compatible 四种存储，四者全 Y。
- **Table Maintenance Operations**（按 V1/V2 分组）：包括 Update schema、Update partition spec、Update table properties、Replace sort order、Update table location、Update statistics、Update partition statistics、Expire snapshots、Manage snapshots。Java 全 Y；PyIceberg 仅支持部分（properties、V2 的 schema/partition）；Rust 仅支持 schema/partition/properties；Go 全 N。可见维护操作是各非 Java 实现的主要缺口。
- **Table Update Operations**（按 V1/V2 分组）：Append data files、Rewrite files、Rewrite manifests、Overwrite files、Delete files（V1）；增加 Row delta（V2）。Java 全 Y；PyIceberg 大部分 Y；Rust 仅 V1 Append；Go 全 N。
- **Table Read Operations**（按 V1/V2 分组）：Plan with data file、Plan with puffin statistics、Read data file（V1）；增加 Plan with position deletes、Plan with equality deletes、Read with position deletes、Read with equality deletes（V2）。这一组体现了 V2 row-level delete 的读取能力，Java 全 Y，其他实现覆盖参差。其中 "Plan with puffin statistics" 在 V2 仅 Java Y，对应 PR review 中"Add puffin statistics"反馈。
- **Table Write Operations**（按 V1/V2 分组）：Append data（V1）；增加 Write position deletes、Write equality deletes（V2）。Java 全 Y；PyIceberg 仅 Append；Rust 仅 Append；Go 全 N。
- **Catalogs 区块**：分别列出 REST Catalog、SQL Catalog、Hive Metastore Catalog、Glue Catalog 四种，每种下再按 Table Spec V1/V2、View Spec V1、Namespace Operations 分组。
  - REST Catalog：表操作四者全 Y；视图操作仅 Java Y（PyIceberg/Rust/Go 均未实现视图）；命名空间操作基本全 Y，个别 `namespaceExists`/`loadNamespaceMetadata` 在 PyIceberg 为 N。
  - SQL Catalog：先列出支持的后端数据库（Postgres、MySQL、SQLite），Java/PyIceberg/Rust 支持，Go 不支持。表操作四者全 Y（仅限支持 SQL Catalog 的实现）；视图操作仅 Java；命名空间操作 Java 全 Y，PyIceberg/Rust/Go 仅部分（dropNamespace、updateNamespaceProperties）。
  - Glue Catalog 与 Hive Metastore Catalog 结构类似：表操作四者全 Y；视图操作仅 Java；命名空间操作仅 `updateNamespaceProperties` 在四者全 Y，其余仅 Java Y。
- 文件末尾缺少换行符（`No newline at end of file`），这是一个小瑕疵，但不影响 MkDocs 渲染。

#### `site/nav.yml`

**修改目的**：将新页面注册到站点导航。

**工作逻辑**：在 `nav` 的"Specs & APIs"区块（含 View spec、Puffin spec、AES GCM Stream spec 等）中，在"AES GCM Stream spec"之后、"Multi-engine support"之前插入一行：

```yaml
    - Implementation status: status.md
```

使该页面在文档站点导航中可见。diff 中还显示 `ASF:` 行被改动（前后都带 `:`，看似无实质变化），这通常是编辑器自动格式化（行尾空格调整）导致的，无功能影响。

## 小结

- **成效**：新增实现能力矩阵页面，系统化展示 Java / PyIceberg / Rust / Go 四个实现的能力覆盖情况，覆盖数据类型、文件格式、IO、维护操作、更新操作、读写操作，以及 REST/SQL/Glue/Hive 四种 Catalog 的表/视图/命名空间操作；并在站点导航中注册，便于用户与贡献者查阅。这是社区跨实现协作的重要基础设施文档。
- **影响范围**：新增 `site/docs/status.md`（369 行）+ 修改 `site/nav.yml`（1 行插入），纯文档变更，无代码、构建或运行时影响。该页面需社区持续维护，随各实现能力演进更新 Y/N 状态。
- **回迁到 1.4.x 的注意事项**：这是文档增强，与产品版本功能无关，对 1.4.x 维护分支的发布产物无任何影响。文档由 main 分支统一构建发布，1.4.x 无需回迁。即便回迁，该页面描述的是各实现的当前状态，与 1.4.x 发布时刻的状态可能不符，反而造成误导，**不应回迁**。
