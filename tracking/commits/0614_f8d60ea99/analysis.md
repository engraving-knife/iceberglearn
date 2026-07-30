# 提交 0614：Docs: Add Daft into Iceberg documentation

## 提交信息

- **序号**：0614 / 4088
- **哈希**：f8d60ea993c5e87d377dbc07e32cc6d0f28914d4
- **短哈希**：f8d60ea99
- **日期**：2024-03-20（Wed Mar 20 04:01:21 2024 -0700）
- **作者**：Jay Chia <17691182+jaychia@users.noreply.github.com>
- **提交说明**：Docs: Add Daft into Iceberg documentation (#9836)
- **PR/Issue**：#9836

## 总体目的

本提交是一个纯文档改动，目标是在 Iceberg 官方文档站点中新增一个介绍 Daft 查询引擎的页面，让用户了解如何使用 Daft 读取 Iceberg 表。

背景动机如下：

1. **Daft 的定位**：Daft 是一个用 Python 与 Rust 编写的分布式查询引擎，专注于机器学习/AI 训练、批量推理、特征工程与数据分析场景，提供 Python DataFrame API。它与 Iceberg 的紧密集成能让用户直接在 Iceberg 数据目录上跑 Pythonic 的 ML/分析工作负载。
2. **PyIceberg 作为桥梁**：Daft 通过 Apache Iceberg 的 Python 客户端 PyIceberg 来加载 Iceberg 表。在本次提交之前，Iceberg 文档中已收录了 Spark、Flink、Hive、Trino 等引擎的集成文档，Daft 作为新兴的 Python/Rust 生态查询引擎缺少一个对等的官方介绍页面。
3. **生态收录**：Iceberg 文档站点的导航栏维护着一个"支持 Iceberg 的引擎/集成"列表（Trino、Clickhouse、Presto、Dremio、StarRocks、Athena、Snowflake 等），多数只挂外链。本次为 Daft 提供一个内嵌的、详尽的入门文档（而非仅外链），表明 Iceberg 社区认可 Daft 作为一等公民集成，并希望向 ML/Python 用户群体推广 Iceberg 在该场景的可用性。

## 如何达成设计目的

作者通过新增一个 Markdown 文件 `docs/docs/daft.md` 并在 `docs/mkdocs.yml` 导航中加入该条目完成目标。整体设计思路：

1. **新建独立文档页**：在 `docs/docs/` 下新建 `daft.md`，包含 YAML front matter（`title: "Daft"`）、Apache 许可证头、以及结构化的正文，覆盖从安装到类型映射的完整使用链路。
2. **挂入文档导航**：在 `mkdocs.yml` 的 `nav` 列表中，把 `Daft: daft.md` 插入到 `Trino` 与 `Clickhouse` 之间，使其出现在"引擎集成"分组里。注意多数相邻条目（Trino/Clickhouse/Presto/Dremio/StarRocks/Athena/Snowflake 等）都是指向外部文档的外链，而 Daft 与 Hive 一样使用 Iceberg 仓库内的本地 `.md`，说明这是受 Iceberg 项目维护的官方集成文档。
3. **以可运行示例驱动**：正文不是干巴巴的特性罗列，而是用 Spark Quickstart 教程中现成的 `demo.nyc.taxis` 表作为示例数据，给出从配置 PyIceberg catalog、设置 Daft 的 S3 IO config、`daft.read_iceberg(table)` 读取、到 `df.where(...)` 过滤的完整可复现代码，并附带真实的查询输出表格。这种"跟着抄就能跑"的写法降低了用户上手门槛。
4. **强调 Iceberg 核心能力在 Daft 中的体现**：文档专门演示了"未加过滤会触发全表扫描警告"与"在分区列上加过滤会下推并跳过无关文件"两种情形，并向性能/分区/统计等已有文档页交叉链接，突出 Daft 正确利用了 Iceberg 的谓词下推、隐藏分区、表统计等优化。

## 修改详情

### `docs/docs/daft.md`（新文件，145 行）

**修改目的**：创建 Daft 查询引擎的官方集成文档。

**工作逻辑**：

文档按以下结构组织：

1. **YAML front matter 与许可证头**：`title: "Daft"` 用于在导航与页面标题中显示；Apache 2.0 许可证头与其他文档文件保持一致。

2. **项目简介**：
   ```
   [Daft](www.getdaft.io) is a distributed query engine written in Python and Rust ...
   ```
   介绍 Daft 的语言栈（Python+Rust）、目标生态（数据工程与 ML）、提供 Python DataFrame API、与 Iceberg 的紧密集成可用于 ML/AI 训练、批量推理、特征工程与分析。

3. **"Enabling Iceberg support in Daft"**：说明 Daft 通过 PyIceberg 读取 Iceberg 表，给出安装命令 `pip install getdaft pyiceberg`。这里的设计是把 PyIceberg 作为独立依赖显式安装，避免在 Daft 内部硬编码 Iceberg 客户端版本，便于用户随 PyIceberg 升级。

4. **"Querying Iceberg using Daft" → "Reading Iceberg tables"**：
   - 引导用户先按 Spark Quickstart 建表；
   - 给出 `~/.pyiceberg.yaml` 配置示例（指向本地 Docker 的 REST Catalog 与 MinIO S3 端点）；
   - 给出 Python 代码：
     ```python
     import daft
     from pyiceberg.catalog import load_catalog
     daft.set_planning_config(default_io_config=daft.io.IOConfig(s3=daft.io.S3Config(endpoint_url="http://localhost:9000")))
     table = load_catalog("default").load_table("nyc.taxis")
     df = daft.read_iceberg(table)
     df.show()
     ```
     其中 `daft.set_planning_config` 让 Daft 知道如何访问 MinIO（S3 端点），`load_catalog` 来自 PyIceberg 加载表元数据，`daft.read_iceberg(table)` 把 PyIceberg 表对象转成 Daft DataFrame。
   - 附带一个 ASCII 表格展示查询输出（vendor_id/trip_id/trip_distance/fare_amount/store_and_fwd_flag 5 列 4 行），让读者直观看到结果形态。

5. **谓词下推演示**：文档紧接着解释——上面的查询会触发 PyIceberg 的 "no partition filter was specified" 警告（全表扫描）。随后给出带过滤的版本：
   ```python
   df = df.where(df["vendor_id"] > 1)
   df.show()
   ```
   并展示输出从 4 行缩减为 2 行。文档说明：对 `vendor_id`（分区列）的过滤会被 Daft 下推，正确利用 Iceberg 的[数据过滤](https://iceberg.apache.org/docs/latest/performance/#data-filtering)、[隐藏分区](https://iceberg.apache.org/docs/latest/partitioning/)与[表统计辅助查询规划](https://iceberg.apache.org/docs/latest/performance/#scan-planning)，避免全表扫描。这段是文档的核心卖点——证明 Daft 不是简单把 Iceberg 数据全量拉到客户端，而是真正参与了 Iceberg 的查询规划优化。

6. **"Type compatibility" 类型映射表**：给出 Iceberg 与 Daft 类型对照表，覆盖：
   - **原始类型**：`boolean→bool()`、`int→int32()`、`long→int64()`、`float→float32()`、`double→float64()`、`decimal(p,s)→decimal128(p,s)`、`date→date()`、`time→time(timeunit="us")`、`timestamp→timestamp(timeunit="us", timezone=None)`、`timestampz→timestamp(timeunit="us", timezone="UTC")`、`string→string()`、`uuid→binary()`、`fixed(L)→binary()`、`binary→binary()`。
   - **嵌套类型**：`struct(**fields)→struct(**fields)`、`list(child_type)→list(child_type)`、`map(K,V)→map(K,V)`。
   - 每个映射都带指向 Daft 官方 DataType 文档的链接。值得注意的细节：`uuid` 与 `fixed(L)` 都映射到 Daft 的 `binary()`（Daft 没有独立的 UUID/Fixed 类型，用变长二进制承载）；`time` 映射虽然注释写 `time(timeunit="us")` 但链接指向 `int64`，提示 Daft 内部用 int64 表示 time。

### `docs/mkdocs.yml`（1 行新增）

**修改目的**：把新建的 Daft 页面加入文档站点导航。

**工作逻辑**：

在 `nav` 列表中，紧接 `Trino` 外链之后插入一行：
```yaml
  - Daft: daft.md
```
该条目位于 `Trino`（外链）与 `Clickhouse`（外链）之间，与 Hive、Trino、Clickhouse、Presto、Dremio、StarRocks、Athena、EMR、Snowflake、Impala、Doris 等并列，构成"支持 Iceberg 的查询引擎/集成"分组。区别在于：Daft 与 Hive 用本地 `.md`（Iceberg 项目维护），其余用外链——这表明 Daft 文档由 Iceberg 社区审阅维护，质量与一致性更有保障。

## 小结

本提交是一个零风险的纯文档新增，为 Iceberg 官方文档站点引入了 Daft 查询引擎的集成指南。文档以可运行的 PyIceberg + Daft 示例为主线，演示了表读取、谓词下推、隐藏分区利用等 Iceberg 核心能力，并附完整的 Iceberg↔Daft 类型映射表。

- **影响范围**：仅文档站点（新增 `daft.md` + 导航加一行），无任何代码、构建或测试改动。
- **作者背景**：提交者 Jay Chia 是 Daft 项目的核心成员，文档对 Daft API 与 PyIceberg 集成细节的描述准确专业。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick，无冲突风险（除非 1.4.x 的 `mkdocs.yml` 在同一行已有其他改动）。回迁后建议核对：1.4.x 的 Spark Quickstart 教程中 `demo.nyc.taxis` 表的 schema 是否与本示例输出一致；若 1.4.x 文档站点的 `nav` 结构与 main 不同，需手工把 `Daft: daft.md` 放到合适位置。由于是纯文档，回迁的唯一价值是让 1.4.x 用户也能看到 Daft 集成指南，优先级可由维护者自行评估。
