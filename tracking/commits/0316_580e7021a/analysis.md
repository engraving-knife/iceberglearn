# 提交 0316：Spark 3.5: Fix clobbering of files across streaming epochs when query ID is reused (#9255)

## 提交信息

- **序号**：0316 / 4088
- **哈希**：580e7021ab4701764c3fcfd6c3e5e8f73f405992
- **短哈希**：580e7021a
- **日期**：2024-01-02 08:41:22 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.5: Fix clobbering of files across streaming epochs when query ID is reused (#9255)
- **PR/Issue**：#9255

## 总体目的

这个提交修复的是 Iceberg 与 Spark 结构化流（Structured Streaming）集成时一个严重的文件覆盖（clobbering）bug。问题根源在于 Spark 在某些情况下会在多个流式 epoch（micro-batch）之间复用同一个 query ID，而 Iceberg 在构建 `OutputFileFactory` 时把 `queryId` 直接当作 `operationId` 使用，用于生成数据文件路径。

`OutputFileFactory` 在为每个 task 生成输出文件路径时，会使用 `operationId`、`partitionId` 和 `taskId` 等信息拼接出唯一的文件路径。当 Spark 在跨 epoch 复用同一个 query ID 时，前一个 epoch 中已写入并提交的文件，可能与后一个 epoch 中新写入的文件落在完全相同的路径上，导致先写入的文件被覆盖（clobber）。对 append-only 流场景而言，这通常意味着数据丢失；对有 delete/update 的场景还可能引发更隐蔽的正确性问题。

需要强调的是，Spark 复用 query ID 并非 Spark 的 bug，而是其在某些流作业（如 watermark alignment、checkpoint 恢复、流表 join 等）下的合法行为。因此修复需要在 Iceberg 侧保证 `operationId` 在跨 epoch 维度上的唯一性——把 `epochId` 也纳入 `operationId` 的构成即可。

## 如何达成设计目的

提交的设计思路非常直接：将 `operationId` 从 `queryId` 改为 `queryId + "-" + epochId`，使得即便 Spark 复用了同一个 query ID，不同 epoch 之间生成的文件路径也不会冲突。这是最小化、最精准的修复，只在 `SparkWrite` 中构建 `OutputFileFactory` 的那一处进行修改，不影响其它逻辑。配合随后的 0317 提交把同样的修复移植到 Spark 3.3 / 3.4，从而覆盖所有维护中的 Spark 版本。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：让 `OutputFileFactory` 在跨 epoch 场景下生成唯一的文件路径，避免文件覆盖。

**工作逻辑**：
在 streaming writer 创建 `OutputFileFactory` 之前，新增一行：
```java
String operationId = queryId + "-" + epochId;
```
随后把传给 `OutputFileFactory.builderFor(...).operationId(...)` 的参数由原来的 `queryId` 改为这个新拼出的 `operationId`。`epochId` 是 Spark 流作业每个 micro-batch 的单调递增序号，把它追加到 `operationId` 后，文件名里就带上了 epoch 信息，从而彻底隔离不同 epoch 之间的文件路径。

## 小结

这个修复体量极小（仅一行新增加一行替换），但解决了流式作业下可能导致数据丢失的关键正确性问题，体现了 Iceberg 在与 Spark 流式集成中对于"唯一文件命名"这一不变量的坚守，也为后续 0317 把该修复回移到更老 Spark 版本奠定了基础。
