# 提交 3713：Flink: Backport PR #16324 to v2.0 and v1.20 (#16338)

## 提交信息

- **序号**：3713 / 4088
- **哈希**：22f866687a7cae6b1475781998583f7da089b9f4
- **短哈希**：22f866687
- **日期**：2026-05-14 12:10:55 -0700
- **作者**：Kevin Liu
- **提交说明**：Flink: Backport PR #16324 to v2.0 and v1.20 (#16338)
- **PR/Issue**：#16338

## 总体目的

这个提交是 PR #16324（提交 3708）向 Flink 2.0 和 1.20 模块的回移植。它修复了这两个 Flink 版本中 `ListMetadataFiles` 操作符未刷新 table 对象导致错误孤儿文件删除的 bug，与 Flink 2.1 的修复保持一致。

## 如何达成设计目的

通过与提交 3708 完全相同的修改，在 `ListMetadataFiles.processElement` 方法开头添加 `table.refresh()` 调用。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ListMetadataFiles.java` (+1 line)

**修改目的**：在处理触发器前刷新 table。

**工作逻辑**：

```java
public void processElement(Trigger trigger, Context ctx, Collector<String> collector)
    throws Exception {
  try {
+   table.refresh();
    table.snapshots().forEach(...)
```

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestListMetadataFiles.java` (+35 lines)

**修改目的**：添加测试验证修复。

**工作逻辑**：新增 `testMetadataFilesIncludesSnapshotsAddedAfterOpen` 测试，验证操作符启动后新增的快照也能被正确列入元数据文件。

### Flink 1.20 模块 (同样修改, +36 lines)

**修改目的**：与 Flink 2.0 完全相同的修改。

## 总结

这是提交 3708 向 Flink 2.0 和 1.20 的回移植，内容完全一致。至此，所有三个 Flink 版本（1.20、2.0、2.1）都完成了 `ListMetadataFiles` 的 table 刷新修复，确保跨版本一致性，避免错误的孤儿文件删除导致数据丢失。
