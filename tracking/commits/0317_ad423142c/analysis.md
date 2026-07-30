# 提交 0317：Spark 3.3, 3.4: Fix file clobbering when Spark reuses query IDs (#9255) (#9399)

## 提交信息

- **序号**：0317 / 4088
- **哈希**：ad423142c4d3d41b9423026cd99f8ecf1156a24b
- **短哈希**：ad423142c
- **日期**：2024-01-02 12:52:59 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.3, 3.4: Fix file clobbering when Spark reuses query IDs (#9255) (#9399)
- **PR/Issue**：#9255（原始修复 PR），#9399（本次 backport PR）

## 总体目的

这是 0316 提交（PR #9255，针对 Spark 3.5）的版本回移（backport）。Iceberg 同时维护 spark/v3.3、spark/v3.4、spark/v3.5 三套代码分支，三套分支中的 `SparkWrite.java` 结构几乎相同但互相独立。0316 修了 v3.5 的文件覆盖 bug 后，必须把同样的修复同步到 v3.3 和 v3.4，否则这两个版本上运行的流式作业仍会遭遇跨 epoch 文件覆盖、数据丢失的问题。

原始 bug 的根因已经在 0316 的分析中说明：Spark 在跨 epoch 复用 query ID 时，Iceberg 用 `queryId` 作为 `operationId`，导致不同 epoch 写出的文件落到同一物理路径而被覆盖。本次 backport 把同一个修复（`operationId = queryId + "-" + epochId`）应用到 v3.3 和 v3.4 两个分支的 `SparkWrite.java` 中。从 commit 信息 `( #9255) (#9399)` 可以看出：#9255 是最初主分支上的修复 PR 编号，#9399 是把该修复回移到老 Spark 版本时单独开的 PR。

## 如何达成设计目的

backport 的策略是逐分支、逐行复制：在 `spark/v3.3` 和 `spark/v3.4` 各自的 `SparkWrite.java` 中找到创建 `OutputFileFactory` 的同一处代码，做与 0316 完全一致的两行修改——新增 `operationId = queryId + "-" + epochId`，并把 `operationId(queryId)` 改为 `operationId(operationId)`。由于三套分支的 `SparkWrite.java` 在该处代码完全一致，backport 不需要任何额外的语义调整，只是把修复的覆盖面扩展到仍在维护的更老 Spark 版本。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：将 v3.5 的文件覆盖修复同步到 Spark 3.3 分支。

**工作逻辑**：
在 streaming writer 中创建 `OutputFileFactory` 之前新增 `String operationId = queryId + "-" + epochId;`，并将 `OutputFileFactory.builderFor(table, partitionId, taskId).format(format).operationId(queryId)` 中的 `queryId` 替换为 `operationId`，使每个 epoch 的输出文件路径带 epoch 后缀而彼此互斥。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：将同样的修复同步到 Spark 3.4 分支。

**工作逻辑**：与 v3.3 完全一致，新增 `operationId = queryId + "-" + epochId` 并替换 `operationId(...)` 的参数。两处修改分别位于各自的 `SparkWrite` 抽象类中创建 `OutputFileFactory` 的位置（v3.3 在第 643 行附近，v3.4 在第 657 行附近）。

## 小结

这是一个典型的"修复一致性维护"backport：把 0316 中针对 Spark 3.5 的关键修复同步到 Spark 3.3 / 3.4 两个仍在维护的分支。改动机械但不可省略——它是 Iceberg 多 Spark 版本并行维护策略下的必要步骤，确保所有维护中的版本都不会受同一正确性 bug 影响。
