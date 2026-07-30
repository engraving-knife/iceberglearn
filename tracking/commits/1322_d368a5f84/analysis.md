# 提交 1322：Spec: Add deletion vectors to the table spec (#11240)

## 提交信息

- **序号**：1322 / 4088
- **哈希**：d368a5f84448b2e5698f9cab668a80eda6e3f96d
- **短哈希**：d368a5f84
- **日期**：2024-11-02（Sat Nov 2 03:18:04 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spec: Add deletion vectors to the table spec (#11240)
- **PR/Issue**：#11240
- **共同作者**：Anton Okolnychyi <aokolnychyi@apache.org>、emkornfield <emkornfield@gmail.com>

## 总体目的

本提交是 Iceberg 规格层面的一次重大演进：在 v3 表规格中正式引入"删除向量"（Deletion Vectors，简称 DV）。删除向量是一种按位图（bitmap）记录单个数据文件中被删除行位置的二进制表示，存储在 Puffin 文件中，相比传统的 position delete 文件在读取时更高效，且能保证每个数据文件在一个 snapshot 中至多只有一个 DV，从而简化读取端的删除合并逻辑。

具体目标包括：
1. 在 v3 规格中新增"二进制删除向量"能力，作为 v3 列出的扩展特性之一。
2. 在 manifest 的 `data_file` 结构中新增三个字段：`referenced_data_file`（143）、`content_offset`（144）、`content_size_in_bytes`（145），用于追踪 DV 所在的 Puffin 文件位置以及引用的数据文件。
3. 明确 DV 与 position delete 文件、equality delete 文件之间的作用域规则，特别是 DV 存在时读者可安全忽略对应数据文件的 position delete 文件。
4. 在 v3 中标记 position delete 文件为**已废弃**（仍允许从 v2 升级而来的表保留既有 position delete 文件，但在生成新 DV 时必须将其合并进来）。
5. 同步更新 REST Catalog OpenAPI 描述：`FileFormat` 枚举新增 `puffin`，`PositionDeleteFile` 新增 `content-offset` 与 `content-size-in-bytes` 两个可选字段。

## 如何达成设计目的

提交以纯规格/接口文档方式落地，不涉及 Java 实现代码：
- 修改 `format/spec.md`：在 v3 特性列表、`data_file` manifest 字段表、扫描与删除作用域规则、删除格式章节、版本升级附录等多处补充 DV 相关定义。
- 修改 `open-api/rest-catalog-open-api.py` 与 `open-api/rest-catalog-open-api.yaml`：扩展 `FileFormat` 枚举与 `PositionDeleteFile` schema，使 REST Catalog 接口能正确表达 DV 所需的 Puffin 文件格式与位置元数据。

设计上让 DV 复用既有 manifest schema（删除文件与数据文件共用同一 manifest schema），通过 `referenced_data_file` 强约束一个 DV 只能引用一个数据文件，并通过 `content_offset`/`content_size_in_bytes` 精确定位 Puffin 文件内的 blob，从而允许多个 DV 共存于同一个 Puffin 文件中。

## 修改详情

### `format/spec.md`

**修改目的**：在规格文档中全面引入删除向量。

**工作逻辑**：

1. **v3 特性列表**（顶部）：在 v3 扩展列表中追加"Binary deletion vectors"，并在版本说明之后补一段空行。

2. **版本兼容性说明**：在 `data_file` 字段表之前的版本兼容段落新增一句说明："If a later version is not shown, the requirement for a version is not changed from the most recent version shown. For example, v3 uses the same requirements as v2 if a table shows only v1 and v2 requirements." —— 明确字段表中"未列出版本即沿用最近版本"的规则，避免读者误以为 v3 缺省即为 optional/不可用。

3. **`data_file` manifest 字段表**：
   - `101 file_format` 描述由 "avro, orc or parquet" 扩展为 "`avro`, `orc`, `parquet`, or `puffin`"。
   - `103 record_count` 描述补充："or the cardinality of a deletion vector"（DV 时表示位图中被删除行的基数）。
   - 新增三行字段：
     - `143 referenced_data_file`（v2 可选、v3 可选，string）：所有删除所引用的数据文件全限定 URI；对 DV 为必需。
     - `144 content_offset`（v3 可选，long）：内容在文件中的起始偏移。
     - `145 content_size_in_bytes`（v3 可选，long）：被引用内容的长度；当 `content_offset` 存在时为必需。

4. **脚注**：原脚注 4（保留字段 ID 141）顺延为脚注 6；新增脚注 4 说明 position delete 元数据可使用 `referenced_data_file`，且对 DV 为必需；新增脚注 5 说明 `content_offset` 与 `content_size_in_bytes` 必须与 Puffin footer 中 DV blob 的 `offset` 与 `length` 完全一致。

5. **扫描规划段落**：将"partition 谓词用于选择数据与删除文件"改写为"用于选择相关数据文件、删除文件以及删除向量元数据"，并强调转换使用写入 manifest 时所用的分区规格（而非当前分区规格）。

6. **删除作用域规则**：重写为统一描述"删除文件与删除向量元数据"，并新增 DV 的应用规则：
   - DV 应用于数据文件需同时满足：数据文件 `file_path` 等于 DV 的 `referenced_data_file`；数据文件 data sequence number ≤ DV 的 data sequence number；分区（spec 与值）相等。
   - position delete 文件规则补充：当 `referenced_data_file` 非空时需 `file_path` 相等；且"当存在必须应用于该数据文件的 DV 时，不再应用 position delete 文件"（因为 DV 已包含所有既有 position delete 的内容）。
   - 同 commit 内应用删除的特殊情况由"Position delete files"扩展为"Position deletes (vectors and files)"。

7. **Delete Formats 章节**：完全重写开头，定义三类行级删除（DV、position delete 文件、equality delete 文件），并新增"### Deletion Vectors"小节：
   - DV 用位图标识被删除行位置，置位表示该行被删除。
   - 使用 Puffin 规格的 `deletion-vector-v1` blob 定义。
   - 支持正 64 位位置，但优化为大部分位置在 32 位内的情况：将 64 位位置拆为高 32 位 key 与低 32 位 sub-position，每个 key 维护一个 32 位 Roaring bitmap。
   - 测试某位置是否置位：用高位 4 字节查找对应 32 位 bitmap，再用低位 4 字节测试是否在该 bitmap 中；找不到则未置位。
   - manifest 通过 `file_path`、`content_offset`、`content_size_in_bytes` 单独追踪每个 DV，多个 DV 可存于同一文件。
   - 每个 snapshot 中每个数据文件至多一个 DV；写入 DV 时必须替换所有先前 position delete 文件，使读者在存在 DV 时可安全忽略匹配的 position delete 文件。

8. **Position Delete Files 章节**：新增一段说明："Position delete files are **deprecated** in v3. Existing position deletes must be written to delete vectors when updating the position deletes for a data file."

9. **附录"Version 3"的写入约束**：新增"Row-level delete changes"段落，列出 8 条要点：DV 在 v3 引入并使用 Puffin `deletion-vector-v1` blob；manifest 新增三字段及其语义；DV 同步维护（写入者必须合并 DV 与旧 position delete 文件以确保至多一个 DV）；读者可安全忽略被 DV 覆盖的 position delete 文件；v3 表不允许新增 position delete 文件；从 v2 升级的表保留的 position delete 文件在生成 DV 时必须合并；针对跨多个数据文件的 position delete 文件需保留至所有删除被 DV 替换。

### `open-api/rest-catalog-open-api.py`

**修改目的**：让 REST Catalog 的 Pydantic 模型反映 DV 所需的格式与字段。

**工作逻辑**：
- `FileFormat.__root__` 的 `Literal` 由 `('avro', 'orc', 'parquet')` 扩展为 `('avro', 'orc', 'parquet', 'puffin')`。
- `PositionDeleteFile` 新增两个可选字段：
  - `content_offset: Optional[int]`，alias `content-offset`，描述"Offset within the delete file of delete content"。
  - `content_size_in_bytes: Optional[int]`，alias `content-size-in-bytes`，描述"Length, in bytes, of the delete content; required if content-offset is present"。

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：与 `.py` 文件保持一致，更新 OpenAPI YAML schema。

**工作逻辑**：
- `FileFormat` 枚举 `enum` 列表追加 `puffin`。
- `PositionDeleteFile` schema 新增 `content-offset`（integer/int64）与 `content-size-in-bytes`（integer/int64）两个属性，描述与 `.py` 一致。

## 小结

- **成效**：v3 表规格正式定义了删除向量这一高效行级删除机制，明确了 DV 在 manifest 中的元数据字段、Puffin 存储格式、与 position/equality delete 的作用域关系，以及 v3 中 position delete 文件的废弃策略。REST Catalog OpenAPI 同步更新，使接口层可表达 DV。
- **影响范围**：仅规格与接口描述文件（`format/spec.md`、`open-api/rest-catalog-open-api.py`、`open-api/rest-catalog-open-api.yaml`），无 Java 实现、构建或测试代码改动。本提交是后续 Core/API 实现 DV（如提交 1323）的规格基础。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。删除向量是 v3 规格新增能力，涉及 manifest schema 字段（143/144/145）与 Puffin blob 类型的新约定，属于功能演进而非缺陷修复。1.4.x 作为维护分支已冻结规格，回迁会破坏 1.4.x 与既定 v2 规格的一致性，且 1.4.x 并无 DV 的 Java 实现，仅回迁规格文档无实际意义。若 1.4.x 需要支持 DV，应整体升级到包含完整 DV 实现的版本，而非单独回迁本提交。
