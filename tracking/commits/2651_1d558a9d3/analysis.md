# 提交 2651：Spec: bring back added-rows in snapshot fields (#14048)

## 提交信息

- **序号**：2651 / 4088
- **哈希**：1d558a9d3afcfef80a7e4632dfad71b091e453f1
- **短哈希**：1d558a9d3
- **日期**：2025-09-17 20:43:32 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spec: bring back added-rows in snapshot fields (#14048)
- **PR/Issue**：#14048
- **共同作者**：Yuya Ebihara

## 总体目的

本提交恢复了 Iceberg 规范中快照（snapshot）的 `added-rows` 字段，并重新定义了其语义。此前该字段被移除或语义被弱化，但在 row lineage（行谱系）特性的实现过程中，发现该字段对于在提交时安全地递增表的 `next-row-id` 是必需的，因此需要将其"带回来"。

关键背景是 Iceberg 的 Row Lineage 特性：它为表中的每一行分配全局唯一的行 ID（`_row_id`）。表的元数据中维护一个 `next-row-id`，每次提交需要基于本次快照中分配了行 ID 的行数来递增该值。原先 `added-rows` 被理解为"本快照新增的行数"（即所有新增 manifest 的 ADDED_ROWS_COUNT 之和），但这种理解不够准确——因为某些已存在行的行 ID 也可能被重新分配，所以实际需要一个"上界"值来安全地推进 `next-row-id`。

本提交将 `added-rows` 的语义从"新增行总数"重新定义为"本快照中分配了行 ID 的行数的上界"，使得该字段可以安全地用于提交时递增 `next-row-id`，避免 ID 冲突。

## 如何达成设计目的

通过两个文件的协同修改达成目标：

1. 在规范文档 `format/spec.md` 中，将 `added-rows` 字段重新加入快照字段表，并标注为 row lineage 版本下 _required_，同时在 Row Lineage 章节补充其语义说明。
2. 在 Java API 的 `Snapshot.java` 接口中，更新 `addedRows()` 方法的 Javadoc，使其语义与规范保持一致——从"新增行总数"改为"分配了行 ID 的行数的上界"。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Snapshot.java` (+5/-3 lines)

**修改目的**：更新 `addedRows()` 方法的文档注释，使其反映新的语义定义。

**工作逻辑**：原注释说明 `addedRows()` 返回"本快照中新增行的总数，应为所有新增 manifest 的 `ManifestFile.ADDED_ROWS_COUNT` 之和"。新注释将其改为"本快照中分配了行 ID 的行数的上界，可安全用于提交时递增表的 `next-row-id`，它可能大于本快照实际新增的行数并包含一些已存在的行"。方法签名 `default Long addedRows()` 本身未变，仍返回 `null` 作为默认实现。

### `format/spec.md` (+6/-1 lines)

**修改目的**：在规范中恢复 `added-rows` 字段并补充语义说明。

**工作逻辑**：
- 在快照字段表中，于 `first-row-id` 之后、`key-id` 之前，新增一行 `added-rows`，标注为 row lineage 版本下 _required_，描述为"分配了行 ID 的行数的上界"，并指向 Row Lineage 章节。
- 删除了一个多余的空行。
- 在 Row Lineage 章节末尾新增一段说明：`added-rows` 捕获分配了行 ID 的行数上界，可安全用于提交时递增 `next-row-id`，它可能大于本快照新增的行数并包含一些已存在的行，并指向 Row Lineage Example。

## 总结

本提交通过恢复并重新定义 `added-rows` 字段的语义，解决了 Row Lineage 特性中 `next-row-id` 安全递增的问题。将语义从"新增行总数"改为"分配行 ID 的行数上界"，确保了在涉及已有行重新分配 ID 的场景下不会产生 ID 冲突。这是规范层面的重要修正，为 row lineage 的正确实现奠定了基础。
