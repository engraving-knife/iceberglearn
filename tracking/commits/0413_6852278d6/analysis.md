# 提交 0413：Core: `streaming-skip-overwrite-snapshots` only skips (#8980)

## 提交信息

- **序号**：0413
- **哈希**：6852278d6cf01bec2998be954d7bd6f6cc37cc94
- **短哈希**：6852278d6
- **日期**：2024-01-26 15:30:27 -0500
- **作者**：cccs-jc <56140112+cccs-jc@users.noreply.github.com>
- **提交说明**：Core: `streaming-skip-overwrite-snapshots` only skips (#8980)
- **PR/Issue**：#8980

## 总体目的

本提交修复了 Spark Structured Streaming 在读取 Iceberg 表时的一个边界缺陷：当配置了 `streaming-skip-overwrite-snapshots`（或 `streaming-skip-delete-snapshots`）选项时，原本的实现对 rewrite 类快照（`DataOperations.REPLACE`，例如 `RewriteDataFiles` 操作产生的快照）的处理是不正确的。

背景：Iceberg 的 `shouldProcess` 方法对 `REPLACE` 操作返回 `false`（即不应处理），目的是让流式读取跳过 rewrite 类快照。然而在 `getLatestOffset` 方法推进游标到"下一个快照"时，旧代码直接调用 `SnapshotUtil.snapshotAfter(table, curSnapshot.snapshotId())` 获取紧邻的下一个快照，并不感知下一个快照是否也是需要跳过的 replace/delete 快照。这导致在连续多个 rewrite 快照、或 rewrite 快照后面紧跟另一个 rewrite 快照的场景下，`getLatestOffset` 会把游标停在一个不该被处理的快照上，进而让后续的 `planMicroBatches` 因为 `shouldProcess` 校验失败或读到空数据而出现行为异常（与 `STREAMING_MAX_FILES_PER_MICRO_BATCH` / `STREAMING_MAX_ROWS_PER_MICRO_BATCH` 等限制配合时表现尤其明显）。

修复思路是引入一个 `nextValidSnapshot` 辅助方法，在推进游标时持续跳过所有 `shouldProcess` 返回 `false` 的快照，直到找到一个合法快照（APPEND）或者已经到达表当前快照（说明后续全部应被跳过，返回 null 让外层停止读取）。这样 `streaming-skip-overwrite-snapshots` / `streaming-skip-delete-snapshots` 的"跳过"语义才真正在 offset 推进逻辑里生效，而不是仅在批数据生成阶段生效。

上下游影响：上游影响所有使用 Structured Streaming 读取 Iceberg 且表上会执行 `RewriteDataFiles`、`DELETE`、`OVERWRITE` 等产生 REPLACE/DELETE/OVERWRITE 快照的作业；下游影响 `MicroBatchStream` 的 offset 推进与 micro-batch 切分正确性。修复后流式读取能稳定跳过整段 rewrite/delete 快照链，不再误入不可处理的快照。

## 如何达成设计目的

核心实现是在 `SparkMicroBatchStream.getLatestOffset` 推进快照游标的位置，将原来"无条件取下一个快照"替换为"循环跳过不可处理快照直至找到合法快照或抵达表尾"。新增的私有方法 `nextValidSnapshot` 封装了这个循环逻辑，复用已有的 `shouldProcess` 判定函数，保持单一职责。当循环到达 `table.currentSnapshot()` 仍不可处理时返回 `null`，由调用方据此将 `shouldContinueReading` 置为 `false` 并退出主循环，从而正确地结束本轮 offset 计算。测试侧通过 `makeRewriteDataFiles` 辅助方法构造 replace 快照，并新增 5 个测试用例覆盖单次 rewrite、连续两次 rewrite、rewrite 后接 append、以及与 max-files/max-rows 限制组合的场景。

## 修改详情

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java

**修改目的**：修复 `getLatestOffset` 在推进快照游标时未跳过 rewrite/delete/overwrite 快照的缺陷，使 `streaming-skip-overwrite-snapshots` / `streaming-skip-delete-snapshots` 配置在 offset 计算阶段真正生效。

**工作逻辑**：原代码在主循环末尾（即成功消费完当前快照、需要推进到下一个快照时）直接执行 `curSnapshot = SnapshotUtil.snapshotAfter(table, curSnapshot.snapshotId())`，无论下一个快照是否可处理都把游标移过去。修改后改为调用新方法 `nextValidSnapshot(curSnapshot)`：

- `nextValidSnapshot` 先取下一个快照 `nextSnapshot`；
- 进入 `while (!shouldProcess(nextSnapshot))` 循环，逐个跳过不可处理的快照（REPLACE/DELETE/OVERWRITE 等），并在 DEBUG 日志中记录被跳过的快照；
- 在循环内检查：若 `nextSnapshot` 已是 `table.currentSnapshot()`，说明剩余所有快照都应跳过，返回 `null`；否则继续调用 `SnapshotUtil.snapshotAfter` 推进；
- 循环退出（找到一个 `shouldProcess` 为 true 的快照）后返回该合法快照。

调用处据此处理 `null` 返回值：将 `shouldContinueReading` 置为 `false` 并 `break` 退出主循环，避免在不可处理的快照上继续生成 micro-batch。注意方法签名上的 Javadoc 还有一处笔误（`nextValide`、`skiping`），但不影响逻辑。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java

**修改目的**：为修复新增覆盖测试，验证流式读取在遇到 `RewriteDataFiles` 产生的 REPLACE 快照时能正确跳过，并与 micro-batch 大小限制组合时行为正确。

**工作逻辑**：
1. 表创建 DDL 新增 `TBLPROPERTIES ('commit.manifest.min-count-to-merge'='3', 'commit.manifest-merge.enabled'='true')`，确保 manifest 合并行为可控，使测试场景更稳定。
2. 新增辅助方法 `makeRewriteDataFiles()`：遍历表中所有 `DataOperations.APPEND` 快照，取出每个 append 快照新增的 `DataFile`，对每个文件同时执行 `rewrite.addFile(datafile)` 和 `rewrite.deleteFile(datafile)`，最后 `commit()` 产生一个 REPLACE 操作的 rewrite 快照（数据内容不变但快照类型为 replace，用于验证流式读取会跳过它）。
3. 新增 5 个测试用例：
   - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplace`：append 数据后做一次 rewrite，设置 `STREAMING_MAX_FILES_PER_MICRO_BATCH=1`，断言 micro-batch 数为 6（即只来自 append 快照，rewrite 被跳过）。
   - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceMaxRows`：同样场景但用 `STREAMING_MAX_ROWS_PER_MICRO_BATCH=4`，断言 micro-batch 数为 2。
   - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceMaxFilesAndRows`：同时设置两个限制，断言 micro-batch 数为 6（max-files 限制更严格，主导切分）。
   - `testReadStreamWithSnapshotType2RewriteDataFilesIgnoresReplace`：连续两次 rewrite，断言 micro-batch 数仍为 6（验证 `nextValidSnapshot` 循环跳过多个 rewrite 快照）。
   - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceFollowedByAppend`：rewrite 后再 append 数据，断言 micro-batch 数为 12（rewrite 被跳过，前后两段 append 都被正确处理）。

这些测试覆盖了修复的核心场景：单次 rewrite、连续 rewrite、rewrite 后接 append，以及与流式读取大小限制的组合，充分验证 `nextValidSnapshot` 的循环跳过逻辑。

## 小结

这个提交是 Structured Streaming 读取 Iceberg 表正确性方面的一个重要修复。它揭示了一个设计上的不对称：`shouldProcess` 已经定义了"哪些快照应被跳过"，但 offset 推进逻辑却没有遵循这一定义，导致跳过行为只在批数据生成阶段生效、在 offset 计算阶段失效。修复通过引入 `nextValidSnapshot` 让两处逻辑对齐，体现了"跳过语义应贯穿整个读取链路"的设计原则。模式上属于"将散落的判定逻辑收敛为单一辅助方法并在关键路径复用"，是典型的可维护性改进。影响范围集中在流式读取的 rewrite/delete/overwrite 跳过场景，对普通 append-only 流无影响。
