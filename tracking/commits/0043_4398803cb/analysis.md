# 提交 0043：Add ASF DOAP rdf file (#8586)

## 提交信息

- **序号**：0043 / 4088
- **哈希**：4398803cb3630d4650863582e8b158e60f9ad59c
- **短哈希**：4398803cb
- **日期**：2023-10-12
- **作者**：JB Onofré
- **提交说明**：Add ASF DOAP rdf file (#8586)
- **PR/Issue**：#8586

## 总体目的

该提交为 Apache Iceberg 仓库新增了一个 `doap.rdf`（Description of a Project）元数据文件。DOAP 是 Apache 软件基金会（ASF）用于在 [projects.apache.org](https://projects.apache.org) 上呈现各孵化/顶级项目信息的标准 RDF 格式。ASF 通过扫描每个顶级项目仓库根目录下的 `doap.rdf` 文件来聚合项目的官方信息——项目名称、简介、主页、PMC 地址、邮件列表、Bug 数据库、下载页、支持语言、分类、最新发布版本、源代码仓库等。

在加入 `doap.rdf` 之前，Apache Iceberg 作为顶级项目在 ASF 元信息聚合中可能缺失或描述不完整，影响项目在 ASF 门户上的对外展示与下游工具的自动发现。本提交通过在仓库根目录新增该文件，完成 Iceberg 项目向 ASF 基础设施侧的元数据登记，属于项目治理与对外可见性方面的"行政性"补全，对代码运行时行为无任何影响。

## 如何达成设计目的

设计上直接复用 ASF 标准的 DOAP RDF 模板，填入 Iceberg 项目的实际元数据：项目主页 `https://iceberg.apache.org`、PMC 同址、Apache-2.0 许可证、bug 数据库指向 GitHub Issues、下载页指向 Iceberg 官网 releases 页、支持语言枚举 Java/Python/Go/Rust 四种、归属 Big-Data/Database/Data-Engineering 三个分类、最新发布版本为 1.4.0（2023-10-04 发布）、源代码仓库为 GitHub `apache/iceberg`。文件头加上 ASF 标准的 Apache License 2.0 许可声明，符合 ASF 仓库文件规范。

## 修改详情

### `doap.rdf`（新增）

**修改目的**：在仓库根目录创建 DOAP RDF 文件，向 ASF 注册 Iceberg 项目的标准元数据。

**工作逻辑**：文件采用 RDF/XML 格式，根元素 `<rdf:RDF>` 声明了 DOAP、RDF、ASF 扩展（asfext）、FOAF 四个命名空间。核心元素 `<Project rdf:about="https://iceberg.apache.org">` 包含：

- `created`：项目 DOAP 创建日期 2023-09-14
- `license`：Apache-2.0（SPDX 链接）
- `name`：Apache Iceberg
- `homepage` 与 `asfext:pmc`：均指向 `https://iceberg.apache.org`
- `shortdesc`：Iceberg 是面向海量分析表的高性能格式
- `description`：展开描述，强调 Iceberg 把 SQL 表的可靠性与简洁性带入大数据，并让 Spark、Trino、Flink、Presto、Hive、Impala 等引擎能并发安全地操作同一批表
- `bug-database`：GitHub Issues
- `mailing-list`：官网社区页
- `download-page`：官网 releases 页
- `programming-language`：Java、Python、Go、Rust（四个 icebf 仓库对应的主语言）
- `category`：big-data、database、data-engineering
- `release`：单个 `<Version>` 块，name=1.4.0、created=2023-10-04、revision=1.4.0
- `repository`：`<GitRepository>`，location 与 browse 均指向 GitHub 上的 `apache/iceberg`

文件最后保留一个空行。整份文件不含任何代码逻辑，仅作为 ASF 元数据登记凭证。

## 小结

该提交通过新增标准 `doap.rdf` 元数据文件，让 Apache Iceberg 顶级项目在 ASF 项目目录与基础设施侧获得正式登记，是项目治理与对外可见性的补全，与代码运行时行为无关。
