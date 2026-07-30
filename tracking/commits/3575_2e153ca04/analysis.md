# 提交 3575：Spark 3.4, 3.5, 4.0: Include snapshotId and branch in SparkTable equals and hashCode (#15840)

## 提交信息

- **序号**：3575 / 4088
- **哈希**：2e153ca04f2ed6cfbb329e6b9af055fa6e27bb73
- **短哈希**：2e153ca04
- **日期**：2026-04-23 11:44:38 -0500
- **作者**：Bharath Krishna
- **提交说明**：Spark 3.4, 3.5, 4.0: Include snapshotId and branch in SparkTable equals and hashCode (#15840)
- **PR/Issue**：#15840

## 总体目的

该提交修复了 Spark `SparkTable` 类的 `equals` 和 `hashCode` 方法未考虑 `snapshotId` 和 `branch` 的问题。之前，`SparkTable` 的 `equals` 和 `hashCode` 仅基于 `icebergTable.name()`（表名），这意味着同一表的不同快照（time travel）或不同分支的 `SparkTable` 实例会被认为相等。这可能导致 Spark 在缓存、去重或比较表时将不同快照/分支的表错误地视为相同，产生数据正确性问题。

例如，当用户通过 time travel 查询不同快照的数据时，Spark 可能因为 `equals` 返回 true 而复用了错误快照的缓存结果。同样，不同分支的表也可能被混淆。该提交将 `table().uuid()`、`snapshotId` 和 `branch` 纳入 `equals` 和 `hashCode` 的计算，确保不同快照和分支的表实例被正确区分。同时修复了 `canDeleteWhere` 方法中 WAP 分支变量赋值修改了 `branch` 字段的问题。

## 如何达成设计目的

在 `equals` 方法中增加 `uuid`、`snapshotId` 和 `branch` 的比较；在 `hashCode` 中使用 `Objects.hash` 计算这四个字段的哈希值。同时在 `canDeleteWhere` 中引入局部变量 `writeBranch` 替代直接修改 `branch` 字段，避免副作用。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+17/-7 lines)

**修改目的**：修复 equals/hashCode 和 canDeleteWhere 的 branch 副作用问题。

**工作逻辑**：
- `canDeleteWhere` 中引入局部变量 `writeBranch`，不再直接修改 `branch` 字段：
```java
String writeBranch = branch;
if (SparkTableUtil.wapEnabled(table())) {
  writeBranch = SparkTableUtil.determineWriteBranch(sparkSession(), branch);
}
if (writeBranch != null) {
  deleteFiles.toBranch(writeBranch);
}
```
- `equals` 方法从仅比较 `icebergTable.name()` 改为同时比较 name、uuid、snapshotId、branch：
```java
return icebergTable.name().equals(that.icebergTable.name())
    && Objects.equals(table().uuid(), that.table().uuid())
    && Objects.equals(snapshotId, that.snapshotId)
    && Objects.equals(branch, that.branch);
```
- `hashCode` 方法从 `icebergTable.name().hashCode()` 改为 `Objects.hash(icebergTable.name(), table().uuid(), snapshotId, branch)`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java` (+56/-0 lines)

**修改目的**：测试不同快照和分支的表不相等。

**工作逻辑**：
- `testTableInequalityWithDifferentSnapshots`：插入两条数据产生两个快照，通过 `copyWithSnapshotId` 创建不同快照的 SparkTable，验证它们不相等且 hashCode 不同。
- `testTableInequalityWithDifferentBranches`：创建分支 `testBranch`，通过 `copyWithBranch` 创建 main 和 testBranch 的 SparkTable，验证它们不相等且 hashCode 不同。

### `spark/v3.5/...` 和 `spark/v4.0/...` 对应文件 (+17/-7, +56/-0 each)

**修改目的**：将相同修复应用到 Spark 3.5 和 4.0 版本。

**工作逻辑**：与 v3.4 版本的改动完全一致。

## 总结

该提交修复了 SparkTable 的 equals/hashCode 未区分不同快照和分支的 bug，这是一个潜在的数据正确性问题。修复后，Spark 能够正确区分同一表的不同 time travel 快照和不同分支的实例，避免缓存复用导致的错误结果。同时修复了 canDeleteWhere 中修改 branch 字段的副作用问题。该修复覆盖 Spark 3.4、3.5、4.0 三个版本。
