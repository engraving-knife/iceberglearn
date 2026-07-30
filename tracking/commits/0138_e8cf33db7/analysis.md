# 提交 0138：Docs: Add note that snapshot expiration and cleanup orphan files could corrupt Flink job state (#9002)

## 提交信息

- **序号**：0138 / 4088
- **哈希**：e8cf33db7d3fc637504a51a801c055dce54474b7
- **短哈希**：e8cf33db7
- **日期**：2023-11-08 12:40:02 +0100
- **作者**：Rui Li
- **提交说明**：Docs: Add note that snapshot expiration and cleanup orphan files could corrupt Flink job state (#9002)
- **PR/Issue**：#9002

## 总体目的

这个提交向 Flink 写入文档追加了一节 "Notes"，明确提醒用户：`Expire snapshots`（快照过期）和 `Delete orphan files`（清理孤儿文件）这两个表维护操作，如果不加约束地执行，可能会破坏正在运行的 Flink 流式写入作业的状态，从而造成作业失败或数据丢失。这是一处纯文档改动，不涉及任何代码逻辑。

背景与动机：Flink 的 Iceberg 流式写入依赖快照摘要（snapshot summary）来记录"最后一次提交的 checkpoint ID"，并把尚未提交到 Iceberg 的中间数据以临时文件形式留在数据目录下。这意味着 Iceberg 表的快照链和临时文件实际上承载了 Flink 作业的恢复状态。如果运维侧在 Flink 作业运行期间或作业恢复窗口内调用了 `Expire snapshots` 把尚未被 Flink 确认提交的快照过期掉，或者调用了 `Delete orphan files` 把 Flink 还需要的临时文件当成孤儿文件删掉，就会导致 Flink 作业在下次 checkpoint 恢复时找不到必要的快照或临时文件，状态被破坏，作业无法继续。

此前文档里没有显式提示这一风险，用户很容易把 Iceberg 的常规维护操作（快照过期、孤儿文件清理）与 Flink 流式写入混用而不自知。这个提交通过在 `docs/flink-writes.md` 末尾追加 "Notes" 小节，把这条隐性约束显式化：必须保留 Flink 作业产生的最后一个快照（可通过快照 summary 中的 `flink.job-id` 属性识别），且清理孤儿文件时必须设置足够大的"年龄"阈值，只删除足够老的文件。这对 Iceberg 演进的意义在于完善了 Flink 集成层的运维文档，让表的维护操作与流式作业的状态管理之间的边界更清晰，降低用户在生产环境中踩坑的概率。

## 如何达成设计目的

设计思路很简单：在 Flink 写入文档的末尾增加一个独立的 "Notes" 小节，用一段说明把"快照摘要承载 checkpoint ID、临时文件承载未提交数据"这一机制，以及由此推出的两条运维注意事项（保留 Flink 最后一个快照、只删足够老的孤儿文件）讲清楚，并在文字中嵌入指向 `expire-snapshots` 与 `delete-orphan-files` 维护操作文档的相对链接，方便用户直接跳转。

## 修改详情

### `docs/flink-writes.md`

**修改目的**：在 Flink 写入文档末尾追加 "Notes" 小节，提示快照过期与孤儿文件清理可能破坏 Flink 作业状态，并给出规避做法。

**工作逻辑**：原文件末尾是 `Check out all the options here: [write-options](/flink-configuration#write-options)` 一行（且文件没有结尾换行）。改动在该行之后追加：

```markdown
## Notes

Flink streaming write jobs rely on snapshot summary to keep the last committed checkpoint ID, and
store uncommitted data as temporary files. Therefore, [expiring snapshots](../tables/maintenance#expire-snapshots)
and [deleting orphan files](../tables/maintenance#delete-orphan-files) could possibly corrupt
the state of the Flink job. To avoid that, make sure to keep the last snapshot created by the Flink
job (which can be identified by the `flink.job-id` property in the summary), and only delete
orphan files that are old enough.
```

这段说明包含三层信息：一是机制解释（Flink 用快照摘要保存最后提交的 checkpoint ID、用临时文件保存未提交数据）；二是风险点（`Expire snapshots` 和 `Delete orphan files` 可能破坏 Flink 作业状态）；三是规避做法（保留 Flink 作业产生的最后一个快照，可用 summary 中的 `flink.job-id` 识别；清理孤儿文件时只删足够老的）。两处维护操作以相对链接形式指向 `../tables/maintenance#expire-snapshots` 与 `../tables/maintenance#delete-orphan-files`，方便跳转。

## 小结

在 Flink 写入文档中显式提示快照过期与孤儿文件清理可能破坏流式作业状态，并给出保留最后一个 Flink 快照、只删足够老孤儿文件的运维建议，完善了 Flink 集成的运维边界说明。
