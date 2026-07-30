# 提交 3463：Spark: test cleanup - eliminate unnecessary table refreshes (#15765)

## 提交信息

- **序号**：3463 / 4088
- **哈希**：f575d97755d6f01c40dc5cb894534c88562accae
- **短哈希**：f575d97755
- **日期**：2026-03-26 14:06:34 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark: test cleanup - eliminate unnecessary table refreshes (#15765)
- **PR/Issue**：#15765

## 总体目的

清理 Spark 4.1 测试代码中不必要的 `table.refresh()` 调用。在测试中，许多 `table.refresh()` 调用是多余的，因为 Iceberg 的 `Table` 对象在 commit 操作后会自动更新元数据。这些不必要的 refresh 调用增加了测试时间并降低了代码可读性。

## 如何达成设计目的

- 移除 `TestRewriteDataFilesAction` 中不必要的 `table.refresh()` 调用
- 在确实需要刷新的地方保留（如在写入数据后需要刷新才能看到新文件时）
- 同样清理 `TestRewritePositionDeleteFilesAction` 中的不必要 refresh

## 修改详情

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+6/-62 lines)

**修改目的**：移除不必要的 table.refresh() 调用。

**工作逻辑**：
- 在 `basicRewrite()` 方法中移除 `table.refresh()`
- 在多个测试方法中移除重写操作后的 `table.refresh()` 调用
  - 包括 `testRewriteDataFilesWithPositionDeletes`、`testRewriteDataFilesWithPositionDeletes2` 等方法
- 在需要刷新的位置保留调用（如 `writeRecords()` 后需要刷新才能 `shouldHaveFiles()`）
- 移除了约 60 个不必要的 refresh 调用

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (+0/-5 lines)

**修改目的**：移除不必要的位置删除文件重写测试中的 refresh 调用。

## 总结

该提交清理了 Spark 4.1 测试代码中约 60 个不必要的 `table.refresh()` 调用。这些调用在 commit 操作后是多余的，因为 Table 对象会自动更新。移除这些调用提高了测试性能和代码可读性。
