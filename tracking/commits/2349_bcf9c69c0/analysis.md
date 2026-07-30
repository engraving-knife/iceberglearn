# 提交 2349：Docs: Document clean_expired_metadata parameter in expire_snapshots Spark procedure (#13516)

## 提交信息

- **序号**：2349 / 4088
- **哈希**：bcf9c69c098b54d31cbd803d62a2609d3814c3df
- **短哈希**：bcf9c69c0
- **日期**：2025-07-14 09:26:26 +0200
- **作者**：gaborkaszab
- **提交说明**：Docs: Document clean_expired_metadata parameter in expire_snapshots Spark procedure (#13516)
- **PR/Issue**：#13516

## 总体目的

这个提交是 `expire_snapshots` Spark 过程文档化系列的第一步。`expire_snapshots` 过程在 Iceberg 中用于过期不再需要的快照，以释放存储空间。在此提交之前，该过程的文档中缺少 `clean_expired_metadata` 参数的说明。

`clean_expired_metadata` 是一个布尔参数，当设为 `true` 时，会清理不再被快照引用的元数据，例如分区规范（partition specs）和模式（schemas）。这对于在长期运行的表上控制元数据膨胀很有价值——随着表经历多次 schema 演进和分区规范变更，旧的 schemas 和 specs 会累积在元数据中，即使它们已不再被任何有效快照引用。

本提交是纯文档变更，仅为该参数补充用户可见的文档描述，与紧随其后的提交 2350（在 Spark 3.4/3.5 中实际暴露此参数）配套使用。

## 如何达成设计目的

在 `expire_snapshots` 过程的参数表中新增一行，描述 `clean_expired_metadata` 参数的类型（boolean）和行为：当为 true 时清理不再被快照引用的元数据（如分区规范和 schemas）。改动极小，仅在表格中追加一个参数行。

## 修改详情

### `docs/docs/spark-procedures.md` (+1/-0 lines)

**修改目的**：在 `expire_snapshots` 过程的参数文档表中补充 `clean_expired_metadata` 参数说明。

**工作逻辑**：在 `snapshot_ids` 参数行之后新增一行 `clean_expired_metadata`，类型为 boolean，描述为"When true, cleans up metadata such as partition specs and schemas that are no longer referenced by snapshots."。该行被插入到参数表格的正确位置，使读者能在查阅 expire_snapshots 过程时看到此可选参数的完整说明。

## 总结

该提交为 `expire_snapshots` Spark 过程补充了 `clean_expired_metadata` 参数的文档说明，使用户了解可以通过该参数清理不再被快照引用的分区规范和 schemas 等元数据。这是一个纯文档变更，为后续提交 2350 中实际暴露该参数做好了文档准备。
