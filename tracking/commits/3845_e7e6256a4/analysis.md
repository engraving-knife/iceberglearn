# 提交 3845：Spark: Fix first row ID carry over for manifest rewrite (#16699)

## 提交信息

- **序号**：3845 / 4088
- **哈希**：e7e6256a4cb58ccba6afdc6a44e62b7b11e61938
- **短哈希**：e7e6256a4
- **日期**：2026-06-08 22:42:18 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Fix first row ID carry over for manifest rewrite (#16699)
- **PR/Issue**：#16699

## 总体目的

本提交修复了 Spark 引擎在重写清单（manifest rewrite）时 first row ID 丢失的问题。Iceberg V3 规范引入了行级溯源（row-level lineage）功能，其中 `first_row_id` 是数据文件清单中的一个重要字段，用于标识数据文件中第一行的全局唯一行 ID，是行 ID 计算的基础。

当使用 Spark 的 `rewriteManifests` action 重写清单文件时（例如合并多个小清单为一个），`SparkContentFile` 类没有正确实现 `firstRowId()` 方法，导致重写后的清单中 `first_row_id` 信息丢失。这会破坏行级溯源功能，使得重写清单后行的 ID 不再正确，影响基于行 ID 的查询和数据管理。

本提交通过在 `SparkContentFile` 中添加 `firstRowId()` 方法的实现，确保清单重写时 first row ID 被正确传递。

## 如何达成设计目的

在 `SparkContentFile` 类中新增 `firstRowIdPosition` 字段和 `firstRowId()` 方法实现。`SparkContentFile` 是一个适配器，将 Spark 的 `InternalRow` 包装为 Iceberg 的 `ContentFile` 接口。每个字段通过其在 Spark `StructType` 中的位置（position）来访问。新增的 `firstRowId()` 方法从对应位置读取 Long 值，处理 null 情况。

同时添加了全面的测试覆盖，包括 V3 清单重写保留 first row ID、分区表场景、以及 V2 升级到 V3 后重写清单的场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java` (+11/-0 lines)

**修改目的**：实现 `firstRowId()` 方法。

**工作逻辑**：

1. 新增 `firstRowIdPosition` 字段，在构造器中从 positions map 获取位置：
```java
private final int firstRowIdPosition;
// ...
this.firstRowIdPosition = positions.get(DataFile.FIRST_ROW_ID.name());
```

2. 实现 `firstRowId()` 方法，处理 null 值：
```java
@Override
public Long firstRowId() {
  if (wrapped.isNullAt(firstRowIdPosition)) {
    return null;
  }
  return wrapped.getLong(firstRowIdPosition);
}
```

### `spark/v3.5/spark/src/test/java/.../TestRewriteManifestsAction.java` (+125/-0 lines)

**修改目的**：添加清单重写保留 first row ID 的测试。

**工作逻辑**：
新增三个测试和辅助方法：

1. `testRewriteV3ManifestsPreservesFirstRowId`：验证非分区 V3 表重写清单后行 ID 不变。先写入两条记录（两个 manifest），读取带 lineage 的行 ID，执行重写，再读取行 ID，断言完全一致。

2. `testRewriteV3PartitionedManifestsPreservesFirstRowId`：验证分区 V3 表重写清单后行 ID 不变。

3. `testRewriteManifestsAfterV2ToV3Upgrade`：验证 V2 表升级到 V3 后重写清单，行 ID 不为 null 且不重复。V2 表没有行 ID，升级到 V3 后重写清单会生成新的行 ID。

4. `recordsWithLineage()` 辅助方法：读取表数据并包含 `ROW_ID` 和 `LAST_UPDATED_SEQUENCE_NUMBER` 元数据列。

### `spark/v4.0/spark/src/main/java/.../SparkContentFile.java` (+11/-0 lines)

**修改目的**：同 3.5，为 Spark 4.0 实现 `firstRowId()`。

### `spark/v4.0/spark/src/test/java/.../TestRewriteManifestsAction.java` (+125/-0 lines)

**修改目的**：同 3.5，为 Spark 4.0 添加测试。

### `spark/v4.1/spark/src/main/java/.../SparkContentFile.java` (+11/-0 lines)

**修改目的**：同 3.5，为 Spark 4.1 实现 `firstRowId()`。

### `spark/v4.1/spark/src/test/java/.../TestRewriteManifestsAction.java` (+125/-0 lines)

**修改目的**：同 3.5，为 Spark 4.1 添加测试。

## 总结

本提交修复了一个影响 Iceberg V3 行级溯源功能的重要 bug：清单重写时 `first_row_id` 丢失。修复方式是在 `SparkContentFile` 中正确实现 `firstRowId()` 方法。测试覆盖了非分区表、分区表和 V2 升级 V3 三种场景。修复同时应用于 Spark 3.5、4.0 和 4.1 三个版本，确保一致性。这对于使用行级溯源功能的用户来说是关键的正确性修复。
