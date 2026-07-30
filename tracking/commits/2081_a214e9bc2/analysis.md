# 提交 2081：Spec: Update partition stats for V3

## 提交信息

- **序号**：2081 / 4088
- **哈希**：a214e9bc23a525f8ddaa53f4a77aa574ff7dd939
- **短哈希**：a214e9bc2
- **日期**：2025-05-05 14:21:58 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spec: Update partition stats for V3 (#12098)
- **PR/Issue**：#12098

## 总体目的

Iceberg V3 规范引入了二进制删除向量（Deletion Vectors，简称 DV）作为行级删除的新机制，替代或补充原有的位置删除文件（position delete files）。分区统计文件（Partition Statistics File）此前在 V1/V2 中已定义，用于汇总每个分区的数据文件记录数、文件数、删除文件数等统计信息。

本提交更新分区统计文件的定义以适配 V3 规范，主要解决以下问题：

1. **版本列扩展**：分区统计的字段表此前只有 v1、v2 两列，需要扩展为 v1、v2、v3 三列，以区分不同版本下字段的必要性。

2. **删除向量统计**：V3 引入 DV 后，分区统计需要新增 `dv_count` 字段（字段 ID 13）来统计删除向量数量。同时，`position_delete_record_count` 的语义需要更新——在 V3 中它应统计所有位置删除（包括 DV 和剩余的 v2 位置删除文件），而 `position_delete_file_count` 则只统计位置删除文件（不含 DV）。

3. **必填性调整**：在 V3 中，多个此前为 optional 的删除统计字段（如 `position_delete_record_count`、`equality_delete_record_count` 等）变为 required，因为 V3 期望分区统计更完整。

4. **total_record_count 语义说明**：新增说明文字，澄清当表存在等值删除或 v2 位置删除文件时计算 `total_record_count` 需要读取数据，实现可以写 NULL 表示未知；只有无删除或仅有 DV 时才鼓励使用 manifest 元数据填充。

## 如何达成设计目的

通过修改规范文档 `format/spec.md` 中分区统计相关的两个字段表和补充说明段落，完成 V3 适配：

- **表元数据中 `partition-statistics` 字段表**：扩展版本列为 v1/v2/v3，所有字段在 v3 中保持 required。
- **分区统计文件 schema 字段表**：扩展版本列，调整字段必填性，新增 `dv_count` 字段，更新 `position_delete_record_count` 和 `position_delete_file_count` 的描述。
- **新增说明段落**：澄清 v2→v3 升级时的统计语义和 `total_record_count` 的计算规则。

## 修改详情

### `format/spec.md` (修改, +25/-19 lines)

**修改目的**：更新分区统计定义以适配 V3 规范的删除向量和必填性要求。

**工作逻辑**：
共修改 3 处：

1. **`partition-statistics` 表元数据字段表**（第 989 行附近）：
   - 将表头从 `| v1 | v2 | Field name | Type | Description |` 扩展为 `| v1 | v2 | v3 | Field name | Type | Description |`。
   - `snapshot-id`、`statistics-path`、`file-size-in-bytes` 三个字段在 v3 中保持 required。

2. **分区统计文件 schema 字段表**（第 1002 行附近）：
   - 表头扩展为 v1/v2/v3 三列。
   - 字段 1-5（partition、spec_id、data_record_count、data_file_count、total_data_file_size_in_bytes）：v3 保持 required。
   - 字段 6 `position_delete_record_count`：v3 从 optional 改为 required，描述更新为"Count of position deletes across position delete files and deletion vectors"（统计位置删除文件和删除向量中的所有位置删除）。
   - 字段 7 `position_delete_file_count`：v3 从 optional 改为 required，描述更新为"Count of position delete files ignoring deletion vectors"（仅统计位置删除文件，不含 DV）。
   - **新增字段 13 `dv_count`**（int, v3 required）："Count of deletion vectors"——统计删除向量数量。插入在字段 7 和 8 之间。
   - 字段 8-9（equality_delete 相关）：v3 从 optional 改为 required。
   - 字段 10 `total_record_count`：v3 保持 optional，描述从"after applying the delete files"改为"after applying deletes if any"。
   - 字段 11-12（last_updated 相关）：v3 保持 optional。

3. **新增说明段落**（第 1033 行后）：
   - 说明 v2 表升级到 v3 时，`position_delete_record_count` 必须包含所有位置删除（剩余 v2 位置删除文件 + 升级后新增的 DV）。
   - 说明当表存在等值删除或 v2 位置删除文件时，计算 `total_record_count` 需要读取数据，实现可以省略并写 NULL 表示未知；只有无删除或仅有 DV 时才鼓励使用 manifest 元数据填充该字段。

## 总结

本提交更新 Iceberg 规范中分区统计的定义以适配 V3 规范。主要改动包括：扩展字段表版本列为 v1/v2/v3；新增 `dv_count` 字段统计删除向量数量；更新 `position_delete_record_count` 语义为包含 DV 的所有位置删除；将多个删除统计字段在 V3 中改为 required；新增 v2→v3 升级语义说明和 `total_record_count` 计算规则说明。属于 V3 规范演进的一部分。
