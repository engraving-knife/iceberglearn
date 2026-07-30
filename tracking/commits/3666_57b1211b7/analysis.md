# 提交 3666：Spark: backport PR #15512 to v3.4, v3.5, v4.0 for WAP branch delete fix (#16245)

## 提交信息

- **序号**：3666 / 4088
- **哈希**：57b1211b7477f9f4a5e79a4cf6f6505d63ded4e8
- **短哈希**：57b1211b7
- **日期**：2026-05-07 17:42:57 -0600
- **作者**：Steven Zhen Wu
- **提交说明**：Spark: backport PR #15512 to v3.4, v3.5, v4.0 for WAP branch delete fix (#16245)
- **PR/Issue**：#16245（backport #15512）

## 总体目的

这个提交将 PR #15512（WAP 分支删除修复）backport 到 Spark 3.4、3.5 和 4.0。

WAP（Write-Audit-Publish）是 Iceberg 的分支写入机制，通过 `spark.wap.branch` 配置启用。当 WAP 启用时，写入操作（包括 delete）会提交到 WAP 分支而非主分支。

此前的 bug 是：`canDeleteWhere()` 方法在判断是否可以进行元数据删除（metadata-only delete）时，扫描的是主分支的数据，而 `deleteWhere()` 实际提交到 WAP 分支。这导致 `canDeleteWhere()` 可能基于主分支中存在但 WAP 分支中不存在的数据，错误地批准一个元数据删除。当提交到 WAP 分支时，由于 WAP 分支的数据与主分支不同，会抛出 "Cannot delete file where some, but not all, rows match filter" 错误。

本修复使 `canDeleteWhere()` 的扫描分支与 `deleteWhere()` 的写入分支保持一致：当 WAP 启用且 WAP 分支已存在时，扫描 WAP 分支；当 WAP 分支尚未创建时，回退到主分支（因为是读扫描）。

## 如何达成设计目的

1. 新增 `scanBranchForDelete()` 方法，解析删除扫描应使用的分支：优先使用显式 branch，其次当 WAP 启用且 WAP 分支存在时使用 WAP 分支，否则返回 null（主分支）。
2. 将 `canDeleteUsingMetadata` 方法新增 `scanBranch` 参数，在扫描文件和获取 schema 时使用该分支而非硬编码的 `branch`。
3. 在 Spark 3.4、3.5、4.0 三个版本中应用相同修复，并新增测试。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+27/-4 lines)

**修改目的**：修复 WAP 分支删除的扫描分支不一致问题。

**工作逻辑**：
1. 新增 `scanBranchForDelete()` 方法：
```java
private String scanBranchForDelete() {
  if (branch != null) {
    return branch;
  }
  if (!SparkTableUtil.wapEnabled(table())) {
    return null;
  }
  String wapBranch = sparkSession().conf().get(SparkSQLProperties.WAP_BRANCH, null);
  if (wapBranch != null && table().refs().containsKey(wapBranch)) {
    return wapBranch;
  }
  return null;
}
```
2. `canDeleteUsingMetadata` 新增 `scanBranch` 参数，扫描和 schema 获取使用 `scanBranch` 而非 `branch`：
```java
if (scanBranch != null) {
  scan = scan.useRef(scanBranch);
}
// ...
new StrictMetricsEvaluator(SnapshotUtil.schemaFor(table(), scanBranch), deleteExpr);
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+27/-4 lines)

**修改目的**：同上，为 Spark 3.5 应用相同修复。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+27/-4 lines)

**修改目的**：同上，为 Spark 4.0 应用相同修复。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` (+56 lines)

**修改目的**：新增 WAP 分支删除测试。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` (+56 lines)

**修改目的**：同上，为 Spark 3.5 添加测试。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` (+56 lines)

**修改目的**：同上，为 Spark 4.0 添加测试。

## 总结

这个提交将 WAP 分支删除修复 backport 到 Spark 3.4、3.5 和 4.0。修复的核心问题是 `canDeleteWhere()` 扫描主分支而 `deleteWhere()` 提交到 WAP 分支的不一致，导致元数据删除误判。通过新增 `scanBranchForDelete()` 方法统一解析扫描分支，确保删除检查和实际删除操作基于同一分支的数据。注意此修复仅 backport 到 3.4/3.5/4.0，说明 4.1 已在原 PR #15512 中修复。
