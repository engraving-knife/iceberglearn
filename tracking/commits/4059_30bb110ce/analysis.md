# 提交 4059：Flink: Fix TableMaintenance operator uid instability that breaks savepoint restore (#17210)

## 提交信息

- **序号**：4059 / 4088
- **哈希**：30bb110ceacb9bf5f1937db5c023323eaa2a75de
- **短哈希**：30bb110ce
- **日期**：2026-07-17 18:51:26 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Fix TableMaintenance operator uid instability that breaks savepoint restore (#17210)
- **PR/Issue**：#17210

## 总体目的

这个提交修复了 Flink TableMaintenance 算子 uid（唯一标识符）不稳定导致 savepoint 无法恢复的严重问题。

在 Flink 中，算子的 uid 用于在 savepoint/checkpoint 中标识算子状态。当从 savepoint 恢复作业时，Flink 通过 uid 匹配算子并恢复其状态。如果 uid 不稳定（每次构建作业图时变化），则从旧 savepoint 恢复时会因 uid 不匹配而失败。

此前的实现中，`TableMaintenance` 的 `uidSuffix` 默认值为 `"TableMaintenance-" + UUID.randomUUID()`——每次创建 `TableMaintenance` builder 时都会生成一个随机 UUID，导致 uid 每次都不同。这意味着：
1. 用户如果不显式调用 `uidSuffix()` 设置固定 uid，则每次作业重启后 uid 都变化。
2. 从 savepoint 恢复时，Flink 无法将新作业的算子与 savepoint 中的状态匹配，导致恢复失败。

本提交将默认 uid 改为基于表名（`"TableMaintenance-" + tableName`）的稳定值，使同一表的维护作业在多次构建时 uid 保持一致，从而支持 savepoint 恢复。用户仍可显式提供自定义 uid suffix 覆盖默认值。

## 如何达成设计目的

设计上分三步：
1. 将 `uidSuffix` 字段默认值从随机 UUID 改为 `null`（表示「未设置，待自动生成」）。
2. 移除 `append()` 方法中 `Preconditions.checkNotNull(uidSuffix)` 的非空校验（因为现在 null 是合法的初始状态）。
3. 在 `append()` 方法中，当 `uidSuffix` 仍为 null 时，基于加载的表名生成稳定后缀 `"TableMaintenance-" + tableName`。这样只有在实际构建作业图时才确定 uid，且基于表名保证稳定性。

用户显式调用 `uidSuffix(value)` 时，`uidSuffix` 非 null，使用用户提供的值；未调用时，使用基于表名的默认值。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+6/-3 lines)

**修改目的**：将 uid 后缀从随机 UUID 改为基于表名的稳定默认值。

**工作逻辑**：
- 字段默认值改为 null：
  ```java
  private String uidSuffix = null;  // 原: "TableMaintenance-" + UUID.randomUUID()
  ```
- 移除 `append()` 中的非空校验：
  ```java
  // 删除: Preconditions.checkNotNull(uidSuffix, "Uid suffix should no be null");
  ```
- 在 `append()` 中基于表名生成稳定 uid（仅当用户未显式设置时）：
  ```java
  String tableName = loader.loadTable().name();
  if (uidSuffix == null) {
    this.uidSuffix = "TableMaintenance-" + tableName;
  }
  ```
  这样同一表的维护作业 uid 后缀始终为 `"TableMaintenance-" + tableName`，保证跨作业重启的稳定性。
- 移除不再需要的 `import java.util.UUID;`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (+42/-0 lines)

**修改目的**：验证用户提供的 uid 和默认 uid 的行为。

**工作逻辑**：
- `testUidSuffixUserProvidedIsUsedAsIs`：显式调用 `.uidSuffix(UID_SUFFIX)`，断言 trigger manager 算子的 uid 为 `TRIGGER_MANAGER_OPERATOR_NAME + UID_SUFFIX`，且不包含表名（证明用户值被原样使用）。
- `testUidSuffixDefaultContainsTableName`：不调用 `uidSuffix()`，断言算子 uid 为 `TRIGGER_MANAGER_OPERATOR_NAME + "TableMaintenance-" + tableName`（证明默认值基于表名生成）。

两个测试都通过 `env.getTransformations()` 查找名为 `TRIGGER_MANAGER_OPERATOR_NAME` 的 Transformation，检查其 `getUid()`。

## 总结

这个提交修复了一个影响生产可用性的严重问题：TableMaintenance 算子 uid 使用随机 UUID 导致 savepoint 恢复失败。修复将默认 uid 改为基于表名的稳定值，保证同一表的维护作业跨重启 uid 一致，使 savepoint/checkpoint 恢复正常工作。用户仍可显式覆盖 uid。这是一个对 Flink 维护作业可靠性至关重要的修复，特别影响使用了表维护功能（如快照过期、数据压缩）的生产作业的状态恢复能力。
