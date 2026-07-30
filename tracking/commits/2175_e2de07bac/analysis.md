# 提交 2175：Spark 3.4: streaming-skip-overwrite-snapshots fix (#13168)

## 提交信息

- **序号**：2175 / 4088
- **哈希**：e2de07bac35e9890613bfa2bb22c935f348faa32
- **短哈希**：e2de07bac
- **日期**：2025-05-28 09:35:14 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 3.4: streaming-skip-overwrite-snapshots fix (#13168)
- **PR/Issue**：#13168（backport #8980）

## 总体目的

此提交是 PR #8980 的反向移植，修复 Spark 3.4 结构化流式读取中的一个 bug。在 Spark 微批处理流式读取 Iceberg 表时，当遇到 rewrite 或 delete 类型的快照时应该跳过它们，但原来的实现只跳过了单个快照，如果连续出现多个需要跳过的快照，处理会出错。具体来说，原来使用 `SnapshotUtil.snapshotAfter` 获取下一个快照后没有检查该快照是否也需要跳过。此提交通过引入 `nextValidSnapshot` 方法，循环跳过所有连续的 rewrite/delete 快照，直到找到一个可处理的快照或到达最新快照。

## 如何达成设计目的

- 新增 `nextValidSnapshot` 方法，循环跳过 rewrite 和 delete 类型的快照
- 在 `shouldContinueReading` 为 true 时，使用 `nextValidSnapshot` 替代直接调用 `SnapshotUtil.snapshotAfter`
- 如果 `nextValidSnapshot` 返回 null（表示所有剩余快照都应跳过），则跳出循环
- 添加多个测试用例验证修复效果，包括单次 rewrite、连续两次 rewrite、rewrite 后跟 append 等场景

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (修改, +25/-2 lines)

**修改目的**：修复流式读取中跳过 overwrite 快照的逻辑。

**工作逻辑**：
- 在 `shouldContinueReading` 为 true 时，原来直接 `curSnapshot = SnapshotUtil.snapshotAfter(table, curSnapshot.snapshotId())` 获取下一个快照
- 现在改为先调用 `nextValidSnapshot(curSnapshot)` 获取下一个有效快照
- 如果返回 null（所有剩余快照都应跳过），则 break 跳出循环
- 否则将 `curSnapshot` 设置为返回的有效快照
- `nextValidSnapshot` 方法的逻辑：先获取当前快照的下一个快照，然后通过 `while (!shouldProcess(nextSnapshot))` 循环跳过所有不需要处理的快照（rewrite/delete 类型）。如果已经到达表的当前快照（最新快照），则返回 null 表示没有更多有效快照

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (修改, +108/-2 lines)

**修改目的**：添加测试用例验证 rewrite 快照跳过逻辑。

**工作逻辑**：
- 修改表创建语句，添加 `commit.manifest.min-count-to-merge` 和 `commit.manifest-merge.enabled` 属性
- 添加 `makeRewriteDataFiles` 辅助方法，通过 `table.newRewrite()` 创建 rewrite 快照（使用已有文件进行 add/delete 操作）
- 添加 5 个新测试用例：
  - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplace`：验证单次 rewrite 后的微批计数
  - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceMaxRows`：验证按最大行数限制的微批计数
  - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceMaxFilesAndRows`：验证同时限制文件数和行数的微批计数
  - `testReadStreamWithSnapshotType2RewriteDataFilesIgnoresReplace`：验证连续两次 rewrite 的处理
  - `testReadStreamWithSnapshotTypeRewriteDataFilesIgnoresReplaceFollowedByAppend`：验证 rewrite 后跟 append 的处理

## 总结

此提交修复了 Spark 3.4 结构化流式读取中跳过 rewrite/delete 快照的 bug。原来只跳过单个快照，现在通过 `nextValidSnapshot` 方法循环跳过所有连续的不可处理快照。修复确保了在有数据压缩（compaction）等 rewrite 操作的表上，流式读取能正确跳过这些快照而不产生错误数据。添加了全面的测试用例覆盖各种场景。
