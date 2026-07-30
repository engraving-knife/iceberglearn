# 提交 2455：Spark: Use default cleanExpiredMetadata from Java API (#13731)

## 提交信息

- **序号**：2455 / 4088
- **哈希**：ded11105fb1d038ce03793e2a5c1144e408da260
- **短哈希**：ded11105f
- **日期**：2025-08-05 11:07:16 +0200
- **作者**：gaborkaszab
- **提交说明**：Spark: Use default cleanExpiredMetadata from Java API (#13731)
- **PR/Issue**：#13731

## 总体目的

该提交修改了 Spark 的 `ExpireSnapshotsSparkAction`，使其不再硬编码 `cleanExpiredMetadata` 的默认值为 `false`，而是改为使用 Java API 中的默认值。

此前，Spark 的 `ExpireSnapshotsSparkAction` 将 `cleanExpiredMetadata` 字段初始化为 `false`（基本类型 boolean），并在执行过期快照操作时总是调用 `expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata)`。这意味着即使用户没有显式设置该参数，Spark action 也会强制将 `cleanExpiredMetadata` 设为 `false`，覆盖了 Java Core API 中的默认值。

这种行为存在问题：如果 Java API 的 `ExpireSnapshots` 实现的默认值发生变化（例如改为 `true`），Spark action 不会遵循这个变化，因为 Spark 端总是传递 `false`。该提交通过将字段类型改为 `Boolean`（包装类型，可为 null），并在值为 null 时不调用 `cleanExpiredMetadata()` 方法，从而让 Java API 使用其自身的默认值。

## 如何达成设计目的

设计思路如下：

1. **字段类型从 `boolean` 改为 `Boolean`**：将 `cleanExpiredMetadata` 从基本类型 `boolean`（默认值 `false`）改为包装类型 `Boolean`（默认值 `null`），用 `null` 表示"未设置"状态。

2. **条件性调用**：在执行过期操作时，只有当 `cleanExpiredMetadata != null` 时才调用 `expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata)`，否则不调用，让 Java API 使用其默认行为。

3. **条件性输出**：在 `toString()` 方法的选项列表中，同样只在 `cleanExpiredMetadata != null` 时才添加该选项，避免在未设置时显示误导性的 `clean_expired_metadata=false`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+6/-2 lines)

**修改目的**：让 Spark 3.4 的过期快照操作使用 Java API 的默认 cleanExpiredMetadata 值。

**工作逻辑**：

1. 字段声明修改：
```java
// 修改前
private boolean cleanExpiredMetadata = false;
// 修改后
private Boolean cleanExpiredMetadata = null;
```

2. 执行逻辑修改：
```java
// 修改前
expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata).cleanExpiredFiles(false).commit();
// 修改后
if (cleanExpiredMetadata != null) {
    expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata);
}
expireSnapshots.cleanExpiredFiles(false).commit();
```

3. toString 选项修改：
```java
// 修改前
options.add("clean_expired_metadata=" + cleanExpiredMetadata);
// 修改后
if (cleanExpiredMetadata != null) {
    options.add("clean_expired_metadata=" + cleanExpiredMetadata);
}
```

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+6/-2 lines)

**修改目的**：对 Spark 3.5 应用相同的修改。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+6/-2 lines)

**修改目的**：对 Spark 4.0 应用相同的修改。

## 总结

该提交修复了 Spark `ExpireSnapshotsSparkAction` 中 `cleanExpiredMetadata` 参数的默认行为问题。通过将字段从 `boolean` 改为 `Boolean`（nullable），并在未设置时不调用设置方法，让 Java Core API 的默认值生效。这确保了 Spark action 与 Java API 的行为一致性，避免 Spark 端硬编码覆盖 Java API 的默认值。修改覆盖了 Spark 3.4、3.5 和 4.0 三个版本。
