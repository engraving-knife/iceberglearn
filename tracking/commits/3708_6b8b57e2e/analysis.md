# 提交 3708：Flink: Refresh table in ListMetadataFiles to prevent incorrect orphan file deletion (#16324)

## 提交信息

- **序号**：3708 / 4088
- **哈希**：6b8b57e2e454ec64e0a6113c80d9cdfe09c6783a
- **短哈希**：6b8b57e2e
- **日期**：2026-05-14 14:44:16 +0200
- **作者**：Anupam Yadav
- **提交说明**：Flink: Refresh table in ListMetadataFiles to prevent incorrect orphan file deletion (#16324)
- **PR/Issue**：#16324

## 总体目的

这个提交修复了 Flink 2.1 模块中 `ListMetadataFiles` 操作符的一个数据正确性 bug，该 bug 可能导致错误的孤儿文件删除。

`ListMetadataFiles` 是 Flink 维护任务中的操作符，用于列出表的所有元数据文件（包括 manifest list 文件）。这些信息用于判断哪些文件是"孤儿"（不再被任何快照引用），以便在 `DeleteOrphanFiles` 维护任务中安全删除。

问题在于：`ListMetadataFiles` 操作符在 Flink 作业启动时获取 table 对象，但在 `processElement` 方法处理触发器时，没有刷新 table 对象。如果在操作符启动后有新的快照被提交（新数据写入），这些新快照的 manifest list 文件不会被列入元数据文件列表中。后果是：`DeleteOrphanFiles` 会将这些新快照引用的 manifest list 文件误判为孤儿文件并删除，导致数据丢失。

## 如何达成设计目的

通过在 `ListMetadataFiles.processElement` 方法的开头添加 `table.refresh()` 调用，确保每次处理触发器时都刷新 table 对象以获取最新的快照信息。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ListMetadataFiles.java` (+1 line)

**修改目的**：在处理触发器前刷新 table。

**工作逻辑**：

```java
public void processElement(Trigger trigger, Context ctx, Collector<String> collector)
    throws Exception {
  try {
+   table.refresh();
    table
        .snapshots()
        .forEach(...)
```

`table.refresh()` 会从元数据存储重新加载表的最新状态，包括所有新提交的快照。这样在列举元数据文件时，能包含所有快照（包括操作符启动后新提交的快照）引用的文件，避免误判孤儿文件。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestListMetadataFiles.java` (+35 lines)

**修改目的**：添加测试验证修复。

**工作逻辑**：

新增测试 `testMetadataFilesIncludesSnapshotsAddedAfterOpen`：
1. 创建表并插入第一批数据（快照 1）
2. 打开 `ListMetadataFiles` 操作符
3. 在操作符打开后，继续插入第二批和第三批数据（快照 2 和 3）
4. 触发操作符处理
5. 验证输出包含所有 3 个快照的 manifest list 文件

```java
// Verify that manifest lists from ALL 3 snapshots are present, not just the first one.
// Without table.refresh() in processElement, only snapshot 1's files would be emitted.
table.refresh();
List<Snapshot> snapshots = Lists.newArrayList(table.snapshots());
assertThat(snapshots).hasSize(3);
for (Snapshot snapshot : snapshots) {
  assertThat(tableMetadataFiles).contains(snapshot.manifestListLocation());
}
```

## 总结

这是一个重要的数据安全修复提交，通过在 `ListMetadataFiles` 处理触发器前添加 `table.refresh()` 调用，确保能获取到最新的快照信息。这避免了将新提交快照引用的文件误判为孤儿文件并删除，防止潜在的数据丢失。这是一个经典的"缓存陈旧"问题——操作符持有 table 对象的缓存视图，但没有在关键操作前刷新。
