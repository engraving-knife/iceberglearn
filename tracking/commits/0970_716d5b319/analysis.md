# 提交 0970：API: Update StatisticsFile javadoc (#10769)

## 提交信息

- **序号**：0970 / 4088
- **哈希**：716d5b319c485c85b42e1fdd9a0f9d79877e4054
- **短哈希**：716d5b319
- **日期**：2024-07-24 16:40:30 +0200
- **作者**：rice
- **提交说明**：API: Update StatisticsFile javadoc (#10769)
- **PR/Issue**：#10769

## 总体目的

Iceberg 的 `StatisticsFile` 接口表示一份与某个 snapshot 关联的统计文件（包含文件级统计用于优化查询计划）。接口中 `snapshotId()` 方法返回该统计文件关联的快照 ID。原 Javadoc 写为：

> ID of the Iceberg table's snapshot the statistics were computed from.

字面意思是"统计是基于哪个 snapshot 计算得到的"。这个表述在严格语义下容易引起误解：它暗示 `snapshotId()` 返回的是"统计的来源 snapshot"——但实际语义上，`StatisticsFile` 的 `snapshotId` 表示的是**这份统计文件关联到哪个 snapshot**，即"统计文件归属的 snapshot"，并不一定意味着统计的输入数据来自该 snapshot（也可能统计文件就是为该 snapshot 而计算并随其提交的）。

为了避免读者把 `snapshotId` 误解为"统计计算所基于的源 snapshot"（与某些实现的内部细节混淆），本提交把 Javadoc 改写为更中性、更准确的"the statistics file is associated with"（统计文件所关联的 snapshot），强调"关联"关系而非"计算来源"。

## 如何达成设计目的

直接修改 `api/src/main/java/org/apache/iceberg/StatisticsFile.java` 中 `snapshotId()` 方法的 Javadoc 文本，把 "the statistics were computed from" 改为 "the statistics file is associated with"，仅此一处文本替换。无任何代码、签名或行为变更。

## 修改详情

### `api/src/main/java/org/apache/iceberg/StatisticsFile.java`

**修改目的**：把 `snapshotId()` 方法的 Javadoc 注释从"the statistics were computed from"改为"the statistics file is associated with"。

**工作逻辑**：原注释 `ID of the Iceberg table's snapshot the statistics were computed from.` 改为 `ID of the Iceberg table's snapshot the statistics file is associated with.`。新表述强调 `snapshotId` 是"统计文件与 snapshot 之间的关联关系"而非"统计计算所基于的源 snapshot"，更贴合实际语义。方法签名与返回值不变。

## 小结

- **成效**：`StatisticsFile.snapshotId()` 的 Javadoc 表述更准确，避免读者误以为该字段表示"统计的来源 snapshot"，实际语义为"统计文件所关联的 snapshot"。
- **影响范围**：仅 `api/src/main/java/org/apache/iceberg/StatisticsFile.java` 一个文件，1 行 Javadoc 文本替换，无任何代码或行为变更。
- **回迁到 1.4.x 的注意事项**：纯 Javadoc 修正，**完全可以也推荐回迁到 1.4.x 分支**，无任何风险，有助于分支间文档一致性。cherry-pick 时几乎不会有冲突。
