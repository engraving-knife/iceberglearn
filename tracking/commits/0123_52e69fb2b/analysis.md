# 提交 0123：Spec: Add partition stats spec (#7105)

## 提交信息

- **序号**：0123 / 4088
- **哈希**：52e69fb2b21669345a8d4dba495746fddbe0b376
- **短哈希**：52e69fb2b
- **日期**：2023-11-01
- **作者**：Ajantha Bhat
- **提交说明**：Spec: Add partition stats spec (#7105)
- **PR/Issue**：#7105

## 总体目的

Iceberg 此前在表元数据中已有 `statistics` 字段，用于关联基于 Puffin 文件的"表级统计"（如 NDV、直方图等列级统计 blob），用于查询优化。但分区粒度的统计一直没有标准化：引擎要回答"某分区有多少行、多少数据文件、删了多少行、最近何时更新"这类问题，往往需要扫描 manifest 文件并在客户端聚合，开销大且各引擎实现不统一。

本提交向 Iceberg 格式规范（`format/spec.md`）新增"分区统计（Partition Statistics）"规范，定义了：

1. 表元数据中的 `partition-statistics` 字段（一个可选列表），用于登记与某个 snapshot 关联的分区统计文件。
2. "分区统计文件（Partition Statistics File）"的格式与 schema：用表本身的任意数据文件格式（Parquet/ORC 等）存储，每行对应一个唯一的分区元组，并按 `partition` 字段升序（NULL FIRST）排序以便扫描时快速过滤。

这是 Iceberg 规范层面的一次重要新增能力，意在为后续引擎（Spark、Trino 等）做分区级裁剪、维护决策（如分区老化、compaction 触发）、可观测性提供统一、可持久化、可增量计算的数据源。规范本身不要求读取方必须使用分区统计（明确写明"readers may ignore them"），但其存在使得"按分区预聚合"的结果可以被持久化为表资产，避免每次重新计算。

需要强调的是，本提交只动规范文档，不引入任何 Java 实现；后续才会有读取/写入、Spark 集成等代码 PR 落地。这与 Iceberg 一贯的"先规范、后实现"演进方式一致。

## 如何达成设计目的

改动完全集中在 `format/spec.md` 一个文件，共 +53 行：

1. 在表元数据字段表中新增一行 `partition-statistics`（v1/v2 均 optional），类型指向新增的"Partition Statistics"小节。
2. 把原 `#### Table statistics` 标题改为 `#### Table Statistics`（首字母大写规范化），与新增小节的标题大小写风格保持一致，并使目录锚点统一。
3. 在"Table Statistics"小节之后、"Commit Conflict Resolution and Retry"小节之前，插入两个新小节：
   - `#### Partition Statistics`：描述分区统计的语义、与 snapshot 的关系，以及 `partition-statistics` 元数据列表的结构（`snapshot-id` / `statistics-path` / `file-size-in-bytes` 三个 required 字段）。
   - `#### Partition Statistics File`：定义分区统计文件本身的 schema（12 个字段）以及排序约束、`partition` 列所用的"统一分区类型（unified partition type）"。

整体设计上，分区统计文件刻意采用普通数据文件格式而非 Puffin，因为它的结构是规整的表格（一行一个分区），用 Parquet/ORC 既可复用现有读写栈，又天然支持按列裁剪与谓词下推；而 `partition` 列采用"统一分区类型"则解决了表分区 spec 演进下分区值结构不一致的问题。

## 修改详情

### `format/spec.md`

**修改目的**：在 Iceberg 格式规范中正式定义"分区统计"机制，包括元数据登记字段与分区统计文件的 schema、排序与分区类型规则。

**工作逻辑**：

**1) 表元数据字段表新增 `partition-statistics` 行**，紧跟已有的 `statistics` 行：

```
| _optional_ | _optional_ | **`partition-statistics`**  | A list (optional) of [partition statistics](#partition-statistics). |
```

这表明该字段在 v1/v2 均为可选，与 `statistics`（表级 Puffin 统计）并列存在，互不替代。

**2) 标题规范化**：`#### Table statistics` → `#### Table Statistics`，统一首字母大写，使两个统计小节标题风格一致。

**3) 新增 `#### Partition Statistics` 小节**，关键内容：

- 分区统计文件基于新的"partition statistics file spec"（见下小节）。
- "Partition statistics are not required for reading or planning and readers may ignore them." —— 明确语义为可选辅助信息，不破坏正确性。
- "Each table snapshot may be associated with at most one partition statistics file." —— 一个 snapshot 至多一个分区统计文件，与表级统计（一个 snapshot 可有多个 statistics 文件、每个含多个 blob）不同，体现了分区统计"全表一份"的简洁模型。
- "A writer can optionally write the partition statistics file during each write operation, or it can also be computed on demand." —— 既支持写时生成，也支持事后按需计算。
- "Partition statistics file must be registered in the table metadata file to be considered as a valid statistics file for the reader." —— 必须登记到 `partition-statistics` 列表才生效。

`partition-statistics` 元数据列表的 struct 字段：

| v1 | v2 | Field name | Type | Description |
|----|----|------------|------|-------------|
| _required_ | _required_ | **`snapshot-id`** | `long` | 该分区统计文件关联的 snapshot ID |
| _required_ | _required_ | **`statistics-path`** | `string` | 分区统计文件路径 |
| _required_ | _required_ | **`file-size-in-bytes`** | `long` | 分区统计文件大小（字节） |

与表级 `statistics` struct 相比，分区统计 struct 显著更简洁：去掉了 `file-footer-size-in-bytes`、`key-metadata`、`blob-metadata`（因为不是 Puffin、不需要加密元数据、没有多 blob 概念），只保留定位与大小三件套。

**4) 新增 `#### Partition Statistics File` 小节**，定义文件本身：

- 存储格式：表的任意数据文件格式（Parquet/ORC 等）。
- 行粒度：每个唯一分区元组一行。
- 排序约束：必须按 `partition` 字段升序、NULL FIRST 排列，以优化扫描时的行级过滤。

文件 schema（12 个字段，前 5 个 required、后 7 个 optional）：

| Field id, name | Type | Description |
|----------------|------|-------------|
| **`1 partition`** | `struct<..>` | 分区数据元组，schema 基于统一分区类型 |
| **`2 spec_id`** | `int` | 分区 spec id |
| **`3 data_record_count`** | `long` | 数据文件中的记录数 |
| **`4 data_file_count`** | `int` | 数据文件数 |
| **`5 total_data_file_size_in_bytes`** | `long` | 数据文件总字节数 |
| **`6 position_delete_record_count`** | `long` | 位置删除文件中的记录数 |
| **`7 position_delete_file_count`** | `int` | 位置删除文件数 |
| **`8 equality_delete_record_count`** | `long` | 等值删除文件中的记录数 |
| **`9 equality_delete_file_count`** | `int` | 等值删除文件数 |
| **`10 total_record_count`** | `long` | 应用删除文件后分区内的精确记录数 |
| **`11 last_updated_at`** | `long` | 该分区最近更新的时间戳（毫秒） |
| **`12 last_updated_snapshot_id`** | `long` | 最近更新该分区的 snapshot ID |

字段设计覆盖了"分区健康状况"所需的核心指标：数据规模（记录数/文件数/字节数）、删除规模（按 position/equality 分别拆分，因为二者语义和成本不同）、净记录数（`total_record_count` 已扣除删除）、时效性（`last_updated_at` / `last_updated_snapshot_id`）。后 7 个字段标记为 optional，允许首版只写必填的核心数据统计，删除统计与时效字段可逐步补齐，降低首版实现成本。

**5) 统一分区类型（unified partition type）的说明**：

规范明确：`partition` 列的 struct schema 基于"统一分区类型"——把表中所有曾经出现过的 spec 的分区字段取并集，按 field id 升序排列。文中给出两个例子：

- 例 1：spec#0 有 {field#1, field#2}，演进到 spec#1 有 {field#1, field#2, field#3}，统一类型为 `Struct<field#1, field#2, field#3>`。
- 例 2：spec#0 有 {field#1, field#2}，演进到 spec#1 只剩 {field#2}，统一类型仍为 `Struct<field#1, field#2>`（保留历史字段，避免 spec 演进后旧分区值丢失列）。

这一设计保证分区统计文件能在同一份 schema 下容纳所有 spec 的分区值，配合 `spec_id` 字段即可解释每行属于哪个 spec。`partition` struct 的字段 id 使用分区字段 id（而非 schema 字段 id），这是 Iceberg 分区类型的一贯做法，确保 spec 演进下字段身份稳定。

## 小结

本提交为 Iceberg 格式规范新增"分区统计"机制，定义了表元数据中的 `partition-statistics` 登记字段与分区统计文件的 schema、排序约束及统一分区类型规则，为后续分区级裁剪、维护决策与可观测性提供标准化、可持久化的数据基础，是 Iceberg 元数据能力扩展的重要一步。
