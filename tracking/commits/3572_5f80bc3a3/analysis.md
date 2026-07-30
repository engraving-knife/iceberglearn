# 提交 3572：Spark: fix delete from branch for canDeleteWhere where it does not resolve to the correct branch (#15512)

## 提交信息

- **序号**：3572 / 4088
- **哈希**：5f80bc3a38d582fe352969a9b2ae037de048c843
- **短哈希**：5f80bc3a3
- **日期**：2026-04-22 10:13:55 -0700
- **作者**：Yingjian Wu
- **提交说明**：Spark: fix delete from branch for canDeleteWhere where it does not resolve to the correct branch (#15512)
- **PR/Issue**：#15512

## 总体目的

该提交修复了 Spark 4.1 中 `SparkTable.canDeleteWhere` 方法在 WAP（Write-Audit-Publish）分支场景下未正确解析目标分支的 bug。当用户配置了 WAP 分支（通过 `SparkSQLProperties.WAP_BRANCH`）时，`DELETE` 操作应该作用于 WAP 分支而非 main 分支。但之前的实现中，`canDeleteUsingMetadata` 方法在构建扫描时直接使用 `snapshot`（即 main 分支的快照）而非 WAP 分支，导致元数据删除（metadata delete）扫描了错误的分支。同时，删除操作写入时也直接使用 `branch` 字段而非正确解析的写分支。

该修复通过 `SparkTableUtil.determineReadBranch` 和 `determineWriteBranch` 方法正确解析读分支和写分支，确保在 WAP 场景下删除操作读取和写入都指向正确的 WAP 分支。

## 如何达成设计目的

在 `canDeleteWhere` 方法中，使用 `SparkTableUtil.determineReadBranch` 解析读分支（考虑 WAP 配置），并将其传递给 `canDeleteUsingMetadata`。在 `canDeleteUsingMetadata` 中，优先使用 `scanBranch`（通过 `useRef`），其次才使用 `snapshot`。在执行删除时，使用 `SparkTableUtil.determineWriteBranch` 解析写分支，并通过 `deleteFiles.toBranch(writeBranch)` 指定写入分支。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+20/-5 lines)

**修改目的**：修复 canDeleteWhere 在 WAP 分支场景下的分支解析问题。

**工作逻辑**：
- 新增 `SparkTableUtil` 导入。
- `canDeleteWhere` 方法中，调用 `SparkTableUtil.determineReadBranch(spark(), table(), branch, CaseInsensitiveStringMap.empty())` 获取读分支，传递给 `canDeleteUsingMetadata`。
- `canDeleteUsingMetadata` 方法签名新增 `scanBranch` 参数。在构建 `TableScan` 时，优先检查 `scanBranch != null` 则 `scan.useRef(scanBranch)`，否则才检查 `snapshot != null` 使用 `scan.useSnapshot`。
- 执行删除时，使用 `SparkTableUtil.determineWriteBranch` 解析写分支，替代直接使用 `branch` 字段：

```java
String writeBranch =
    SparkTableUtil.determineWriteBranch(
        spark(), table(), branch, CaseInsensitiveStringMap.empty());

if (writeBranch != null) {
  deleteFiles.toBranch(writeBranch);
}
```

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java` (+56/-0 lines)

**修改目的**：添加 WAP 分支删除的测试用例。

**工作逻辑**：
新增两个测试：
- `testDeleteToWapBranchCanDeleteWhereScansWapBranch`：启用 WAP 分支 `wap`，追加数据后执行 `DELETE FROM %s WHERE id = 1`。验证 WAP 分支中删除了匹配行（row(0), row(2)），而 main 分支保持不变（row(1)）。测试行级删除（非元数据删除）路径。
- `testMetadataDeleteToWapBranchCommitsToWapBranch`：启用 WAP 分支，追加数据后执行 `DELETE FROM %s WHERE dep = 'hr'`（按分区删除，触发元数据删除路径）。验证 WAP 分支中删除了 hr 分区（row(2,eng), row(5,eng)），而 main 分支保持不变（row(1,hr), row(5,eng)）。

## 总结

该提交修复了 WAP（Write-Audit-Publish）场景下 Spark DELETE 操作未正确作用于 WAP 分支的 bug。修复涉及读分支和写分支的正确解析，确保元数据删除扫描和删除提交都指向 WAP 分支而非 main 分支。这是一个重要的正确性修复，防止了 WAP 场景下意外修改 main 分支数据的问题。
