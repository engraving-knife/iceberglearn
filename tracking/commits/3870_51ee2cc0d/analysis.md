# 提交分析：3870 - Core: Disallow setting main branch ref to a tag

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3870 |
| 短哈希 | 51ee2cc0d |
| 完整哈希 | 51ee2cc0d993fe58de21b76613f350da97e9d3ef |
| 日期 | 2026-06-12 20:29:33 -0700 |
| 作者 | Rahul Shivu Mahadev |
| 提交说明 | Core: Disallow setting main branch ref to a tag (#16753) |

## 总体目的

在 `TableMetadata` 的 `setRef` 操作中添加校验，禁止将 `main` 分支引用设置为 tag 类型。`main` 分支在 Iceberg 中有特殊语义（它代表表的当前状态），将其设置为 tag 会导致表状态管理异常。

### 背景

`SnapshotRef.MAIN_BRANCH`（即 `"main"`）是 Iceberg 中的特殊引用，它指向表的当前快照。当通过 `setRef` 将 `main` 设置为一个 tag 时，虽然代码中会继续设置 `currentSnapshotId`，但 tag 的语义（不可变、不追踪当前状态）与 `main` 分支的语义（可变、追踪当前状态）冲突，可能导致后续操作行为异常。

## 修改详情

### 1. 在 `TableMetadata.java` 中添加校验

**文件路径**: `core/src/main/java/org/apache/iceberg/TableMetadata.java`

在 `setRef` 方法的 `build` 阶段，紧跟在快照存在性校验之后添加了一条 `ValidationException` 校验：

```java
Snapshot snapshot = snapshotsById.get(snapshotId);
ValidationException.check(
    snapshot != null, "Cannot set %s to unknown snapshot: %s", name, snapshotId);
ValidationException.check(
    !SnapshotRef.MAIN_BRANCH.equals(name) || ref.isBranch(),
    "Cannot set %s to a tag, it must be a branch",
    SnapshotRef.MAIN_BRANCH);
```

校验逻辑：如果引用名称是 `main`，则该引用必须是 branch 类型，不能是 tag 类型。如果违反则抛出 `ValidationException`，消息为 `"Cannot set main to a tag, it must be a branch"`。

### 2. 新增测试 `TestSnapshotManager.java`

**文件路径**: `core/src/test/java/org/apache/iceberg/TestSnapshotManager.java`

新增测试 `testCreateTagNamedMainFails`：
- 先 stage 一个快照（使用 `stageOnly()`），使表有快照但没有 main 分支引用
- 尝试创建名为 `main` 的 tag
- 验证抛出 `ValidationException`，消息为 `"Cannot set main to a tag, it must be a branch"`
- 验证失败的提交没有创建 main 引用（`ref(SnapshotRef.MAIN_BRANCH)` 返回 `null`）

### 3. 新增测试 `TestTableMetadata.java`

**文件路径**: `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

新增测试 `testSetRefRejectsTagForMainBranch`：
- 构建一个带快照的 `TableMetadata`
- 尝试通过 `setRef` 将 `main` 设置为 tag，验证抛出 `ValidationException`
- 验证其他引用名（如 `tag1`）仍然可以设置为 tag 类型

## 总结

此提交在 `TableMetadata.buildFrom().setRef()` 中添加了一条校验规则：`main` 分支引用必须是 branch 类型，不能是 tag。校验在构建阶段执行，确保违反规则的元数据变更无法提交。测试覆盖了通过 `SnapshotManager` 和直接通过 `TableMetadata` 两种路径的校验，并验证了其他引用名不受影响。
