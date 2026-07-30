# 提交 1272：Spec: Adds Row Lineage (#11130)

## 提交信息

- **序号**：1272 / 4088
- **哈希**：02a988b09ab5b6e9aaa47c79e5e131313cf983cc
- **短哈希**：02a988b09
- **日期**：2024-10-24（Thu Oct 24 05:59:18 2024 +0800）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Spec: Adds Row Lineage (#11130)
- **PR/Issue**：#11130
- **共同作者**：Ryan Blue <blue@apache.org>

## 总体目的

Iceberg v3 规范此前已经引入了纳秒时间戳、unknown 类型、列默认值、多参数变换等能力，但缺乏对"行级别血缘"（Row Lineage）的支持。所谓行血缘，就是给表中每一行分配一个全局唯一的 `_row_id`，并跟踪每行最近一次被更新的 `_last_updated_sequence_number`，从而支持增量同步、CDC 下游消费、行级更新合并等高级场景。

本提交在 `format/spec.md` 中正式加入 Row Lineage 章节，定义了：
1. 两个新的元数据列 `_row_id`（field id `2147483543`）和 `_last_updated_sequence_number`（field id `2147483542`）。
2. 表元数据中新增 `row-lineage`（开关）与 `next-row-id`（下一个可用 row id）两个 v3-only 字段。
3. 快照（snapshot）新增 `first-row-id`、清单（manifest）新增 `first_row_id`（field id `520`）、数据文件（data_file）新增 `first_row_id`（field id `142`）三层"继承式"分配机制。
4. 通过"继承（inheritance）"模式，让 writer 在 commit 之前就能写出数据/清单文件，commit 重试时不需要重写文件，从而保持与 Iceberg 现有乐观并发模型兼容。
5. 明确行血缘启用后不再支持 equality deletes（因为 equality deletes 不读取已有数据，无法维护血缘）。

这是一次纯规范（spec）变更，不涉及 Java 实现，但为后续 v3 实现（如 `TableMetadata`、`Snapshot`、`ManifestFile`、`DataFile` 等结构的扩展）打下了规范基础。

## 如何达成设计目的

整体设计采用 Iceberg 规范中已成熟的"继承"思路（与 sequence number 继承一致）：写入时把未知字段写为 `null`，读取时按层级（snapshot → manifest → data file → row）逐层计算填充实际值。这样在 commit 真正成功之前不需要回填文件，重试成本低。

具体实现思路：
1. 在 spec.md 顶部 v3 特性列表中加入 "Row Lineage tracking"。
2. 重写"Metadata columns"表格，补全已有元数据列说明，并新增 `_row_id`、`_last_updated_sequence_number` 两个元数据列。
3. 新增独立的 "Row Lineage" 章节，详细说明启用方式、写入规则、读取规则、与已有行的拷贝规则。
4. 在 "data_file" 结构表新增 v3 列与 `142 first_row_id` 字段。
5. 在 "Snapshots" 表新增 `first-row-id` 字段，并新增 "Snapshot Row IDs" 子章节说明 commit 重试时如何重新分配。
6. 在 "Manifest Lists" 表新增 `520 first_row_id` 字段，并新增 "First Row ID Assignment" 子章节。
7. 在 "Sequence Number Inheritance" 后新增 "First Row ID Inheritance" 子章节，描述数据文件层的继承规则。
8. 在 Table metadata 表中新增 `row-lineage` 与 `next-row-id` 两个 v3 字段，并补充 next-row-id 在 commit 时的更新公式。
9. 调整原有 manifest_entry/data_file/manifest_file/snapshot/table-metadata 表格列宽以容纳 v3 列。

## 修改详情

### `format/spec.md`

**修改目的**：在 Iceberg v3 规范中正式引入 Row Lineage 能力。

**主要改动**：

1. **v3 特性列表（顶部）**：将原本空行替换为 `* Row Lineage tracking`，把行血缘列入 v3 新能力清单。

2. **Metadata columns 表格**：
   - 给原有元数据列（`_file`、`_pos`、`_deleted`、`_spec_id`、`_partition`、`file_path`、`pos`、`row`）重新调整列宽与说明（如 `_pos` 明确"starting at `0`"）。
   - 新增两行：
     - `2147483543  _row_id`（`long`）：当行血缘启用时分配的唯一 long 标识。
     - `2147483542  _last_updated_sequence_number`（`long`）：行血缘启用时，最近一次更新该行的 commit 的 sequence number。

3. **新增 "Row Lineage" 章节**：
   - 说明 v3 表通过把表元数据字段 `row-lineage` 设为 true 启用；启用后 writer 必须维护 `next-row-id` 表字段以及 `_row_id`、`_last_updated_sequence_number` 数据列。
   - 明确两个字段的"继承"语义：`_row_id` 在新增行时为 `null`，读取时填充；`_last_updated_sequence_number` 在新增或修改时为 `null`，读取时填充为该文件 manifest entry 的 sequence number。
   - 明确启用行血缘的快照不能再包含 equality deletes。
   - 给出 "Row lineage assignment" 子小节，详细规定：
     - 仅含新行的数据文件可省略这两列（视为全 null）；
     - 读取时若 `_last_updated_sequence_number` 为 null 则用 manifest entry 的 sequence number 填充；
     - 若 `_row_id` 为 null 则用 `data_file.first_row_id + _pos` 填充；
     - 当行被搬运到新文件时必须显式拷贝 `_row_id`；如果搬运时还修改了该行，`_last_updated_sequence_number` 必须写为 null（让 commit sequence 接管）；否则必须显式拷贝原值。
   - 给出 "Row lineage example" 子小节：以 `next-row-id = 1000` 为例，演示 snapshot 的 `first-row-id`、manifest 的 `first_row_id`、data file 的 `first_row_id` 三层如何按 `added_rows_count` / `record_count` 累加分配，最终 `next-row-id` 更新为 `1000 + 225 = 1225`。

4. **新增 "Enabling Row Lineage for Non-empty Tables" 子小节**：规定在启用之前已存在的文件必须把 `_row_id` / `_last_updated_sequence_number` 视为 null，并在被复制时显式写为 null；启用之后新增的行按新行处理。

5. **`data_file` 表格**：
   - 新增 `v3` 列，并把所有字段按 v1/v2/v3 三栏标注是否 required/optional。
   - 新增 `142  first_row_id`（`long`，v3-only，optional）：数据文件中第一行的 `_row_id`，引用 "First Row ID Inheritance"。
   - 旧 deprecated 字段（`block_size_in_bytes`、`file_ordinal`、`sort_columns`）的说明同步更新为 "Do not write in v2 or v3."。

6. **新增 "First Row ID Inheritance" 子小节**（紧跟 "Sequence Number Inheritance" 之后）：
   - 未启用时 `first_row_id` 必须为 null；
   - 启用后新增数据文件写 null，读取时按 `manifest.first_row_id + 该文件之前所有 ADDED 数据文件 record_count 之和` 填充；
   - 该继承值仅对 ADDED 文件生效；EXISTING/DELETED 条目必须显式写出已分配的值；delete 文件始终为 null。

7. **Snapshots 表格**：
   - 新增 v3 列；新增 `first-row-id` 字段（v3-only，optional）：第一个 manifest 中第一个数据文件的第一行所分配的 `_row_id`。

8. **新增 "Snapshot Row IDs" 子小节**：
   - 未启用时必须省略 `first-row-id`；
   - 启用后每次 commit 尝试都把 `first-row-id` 设为表当前的 `next-row-id`；commit 重试时必须重新分配；如果本次 commit 没有新增行则应省略。

9. **Manifest Lists 表格（`manifest_file`）**：
   - 新增 v3 列；新增 `520  first_row_id` 字段（v3-only，optional）：分配给该 manifest 中 ADDED 数据文件行的起始 `_row_id`。

10. **新增 "First Row ID Assignment" 子小节**：
    - 未启用时 manifest 的 `first_row_id` 必须为 null；启用后不可再关闭（不可逆）；
    - 新增 data manifest 的 `first_row_id` = snapshot 的 `first-row-id` + 该 manifest 之前所有 data manifest 的 `added_rows_count` 之和；
    - 已存在 manifest 的原值必须保留；delete manifest 的 `first_row_id` 始终为 null。

11. **Table metadata 表格**：
    - 新增 v3 列；新增两个字段：
      - `row-lineage`（boolean，默认 false）：是否启用行血缘；
      - `next-row-id`（long）：高于所有已分配 row id 的值，作为下一次 snapshot 的 `first-row-id`。
    - 表格后新增段落：每次新增 snapshot 时，`next-row-id` 必须更新为 `原值 + 该 snapshot 中所有新增数据文件 record_count 之和`（也等于所有新增 manifest 的 `added_rows_count` 之和）。

## 小结

- **成效**：本次提交把 Row Lineage 正式纳入 Iceberg v3 规范，完整定义了从表元数据、快照、清单到数据文件四层结构的字段与继承/分配规则，并通过示例说明分配流程；为后续 v3 实现（table metadata parser、manifest read/write、reader 端 `_row_id`/`_last_updated_sequence_number` 填充）提供了规范依据。
- **影响范围**：仅修改 `format/spec.md` 一个文件（+203 / -86），无代码、构建或测试改动；属于规范层变更，需要后续 PR 在 Java/Spark/Flink 等模块落地实现。
- **回迁到 1.4.x 的注意事项**：
  - 这是 v3 规范的扩展，1.4.x 仍是 v2 的稳定维护分支，不支持 v3 表格式，**无需也无法回迁**。
  - 即使强行 port 此 spec 文档改动，对 1.4.x 运行时行为也没有影响（1.4.x 的代码不会读取这些新字段），但会让 1.4.x 的 spec.md 与 main 出现不必要的差异，反而增加后续合并冲突的成本。
  - 行血缘相关的 Java 实现（`TableMetadata`、`Snapshot`、`ManifestFile`、`DataFile`、reader 端填充逻辑等）都还没有进入 main，1.4.x 更不具备回迁基础。
  - 结论：1.4.x 维护分支保持现状，不回迁。
