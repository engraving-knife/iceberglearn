# 提交 3464：Spark 3.4, 3.5, 4.0: test cleanup - eliminate unnecessary table refreshes (#15787)

## 提交信息

- **序号**：3464 / 4088
- **哈希**：9f2bcf582e5267a860be616ad31c51d67327b792
- **短哈希**：9f2bcf582e
- **日期**：2026-03-26 19:12:55 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 3.4, 3.5, 4.0: test cleanup - eliminate unnecessary table refreshes (#15787)
- **PR/Issue**：#15787

## 总体目的

将 PR #15765（提交 3463）中针对 Spark 4.1 的测试清理（移除不必要的 `table.refresh()` 调用）移植到 Spark 3.4、3.5 和 4.0 三个版本。

## 如何达成设计目的

- 在 Spark 3.4、3.5、4.0 三个模块中复制与 Spark 4.1 完全相同的测试清理
- 移除 `TestRewriteDataFilesAction` 和 `TestRewritePositionDeleteFilesAction` 中不必要的 `table.refresh()` 调用

## 修改详情

### Spark 3.4 模块

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+6/-62 lines)
**修改目的**：移除不必要的 table.refresh() 调用。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (+0/-5 lines)
**修改目的**：移除位置删除重写测试中的不必要 refresh。

### Spark 3.5 模块

与 Spark 3.4 完全相同的修改。

### Spark 4.0 模块

与 Spark 3.4 完全相同的修改。

## 总结

该提交是 PR #15765 的 backport，将 Spark 4.1 中移除不必要 `table.refresh()` 调用的测试清理移植到 Spark 3.4、3.5 和 4.0 三个版本。每个版本都移除了约 60 个不必要的 refresh 调用，提高了测试性能和代码可读性。
