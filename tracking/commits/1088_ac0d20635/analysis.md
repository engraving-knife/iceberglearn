# 提交 1088：Spec: Minor modifications for v3 (#10948)

## 提交信息

- **序号**：1088 / 4088
- **哈希**：ac0d2063599e25974165652fa2e23b4b39e29d94
- **短哈希**：ac0d20635
- **日期**：2024-08-22 13:16:05 -0700
- **作者**：Ryan Blue
- **提交说明**：Spec: Minor modifications for v3 (#10948)
- **PR/Issue**：#10948

## 总体目的

Iceberg 规范（`format/spec.md`）正在为 v3 做准备。本提交对规范文档做一系列"小修改"，目的是在正式定型 v3 前把文档结构调整到位、把措辞从"仅 v1/v2"泛化为"按版本"，并补充 v3 引入的关键能力说明与若干约束（未知 transform 的读写行为）。这些修改让规范能清晰地表述 v3 的新增内容，同时不破坏对 v1/v2 的既有描述。

主要动机包括：(1) 在版本简介区新增"Version 3: Extended Types and Capabilities"段落，列出 v3 带来的三类增强（纳秒时间戳、列默认值、多参数 transform）；(2) 把大量 Markdown 标题层级上移一级（`####`→`###`、`###`→`##`），使文档结构更合理、目录层级更清晰；(3) 把"v1 and v2 tables""v2 read behavior"等措辞改为"by version""v2+ read behavior"，使表述向前兼容 v3；(4) 在分区与写入章节明确"未知 transform"的读写规则，这是 v3 强制要求的读端行为。

## 如何达成设计目的

全部通过修改 `format/spec.md` 单文件完成，无代码改动。具体手段：

1. **新增 v3 概述段**：在版本简介区补一段，说明 v3 扩展数据类型（纳秒 timestamp(tz)）、列默认值、多参数 transform。
2. **标题层级调整**：把"Specification""Terms""Writer requirements""Schemas and Data Types""Partitioning""Sorting""Manifests"等大节从 `###`/`####` 提升为 `##`/`###`，使顶层结构更扁平。
3. **泛化版本措辞**：把"requirements for v1 and v2 tables"改为"requirements for tables by version"；"v2 read behavior"改为"v2+ read behavior"；"Required v2 fields"改为"Required fields that were not present in or were optional in prior versions"。
4. **未知 transform 规则**：在 Partitioning 与 Writing data files 章节新增"Writers are not allowed to commit files with a partition spec that contains a field with an unknown transform"，并说明读端应忽略未知 transform 的分区字段（v3 中为强制）。
5. **脚注重组**：把 primitive types 表的脚注重新编号，把"无时区/有时区时间戳"的语义说明合并到前两条脚注，删除冗余的 decimal scale 脚注（移入表格备注列）。

## 修改详情

### `format/spec.md`

**修改目的**：为 v3 调整规范文档结构与措辞，补充 v3 能力说明与未知 transform 规则。

**工作逻辑**（按区块说明）：

- **版本简介**：新增"Version 3: Extended Types and Capabilities"小节，列出 v3 三类增强：nanosecond timestamp(tz)、default value support、multi-argument transforms。
- **标题层级**：`## Specification`→`# Specification`；`#### Terms`/`#### Writer requirements`/`#### Writing data files`→`### ...`；`### Schemas and Data Types`/`### Partitioning`/`### Sorting`/`### Manifests`→`## ...`；其下子节同步上移一级。该调整贯穿全文，使目录层级一致。
- **Writer requirements 表**："for v1 and v2 tables"→"for tables by version"；"v2 read behavior"列名→"v2+ read behavior"；"v1 metadata files are allowed in v2 tables"→"in v2 tables (or later)"；"Required v2 fields that were not present in v1 or optional in v1"→"Required fields that were not present in or were optional in prior versions"。
- **Writing data files**：新增一句"Writers are not allowed to commit files with a partition spec that contains a field with an unknown transform."
- **Primitive Types 表与脚注**：`time` 描述微调；脚注重新编号，把"无时区时间戳"与"有时区时间戳"语义合并为脚注 1、2，删除原 decimal scale 脚注（其内容"Scale is fixed, precision must be 38 or less"已并入表格备注列，并去掉多余的 `[1]` 引用）。`timestamp`/`timestamp_ns` 的脚注引用从 `[2]` 改为 `[1]`。
- **Partitioning**：新增一段说明未知 transform 的读端行为——"Partition fields that use an unknown transform can be read by ignoring the partition field for the purpose of filtering data files during scan planning. In v1 and v2, readers should ignore fields with unknown transforms while reading; this behavior is required in v3. Writers are not allowed to commit data using a partition spec that contains a field with an unknown transform."；并把"source column ID"改为复数"source column IDs"以适配多参数 transform。

## 小结

- **成效**：完成 v3 规范的文档预备工作——新增 v3 能力概述、统一版本措辞、调整标题层级、明确未知 transform 读写规则，为后续 v3 规范定型奠定基础。
- **影响范围**：仅 `format/spec.md` 一个文件，66 行新增、53 行删除（多为标题与措辞调整），无任何代码、构建或测试变更。
- **回迁到 1.4.x 的注意事项**：这是规范文档的演进，1.4.x 实现的是 v1/v2 表格式。文档层面的 v3 描述回迁到 1.4.x 价值不大（1.4.x 不实现 v3），且可能造成"1.4.x 支持 v3"的误解。若 1.4.x 的 `spec.md` 也想同步文档结构，可选择性回迁标题层级与措辞泛化部分，但 v3 专属内容（纳秒时间戳、未知 transform 强制读端行为）不应回迁，因为 1.4.x 代码并不强制这些行为。
