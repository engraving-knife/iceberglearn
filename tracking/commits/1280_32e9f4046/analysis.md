# 提交 1280：Spark 3.5: Don't change table distribution when only altering local order (#10774)

## 提交信息

- **序号**：1280 / 4088
- **哈希**：32e9f40468756a60d2cc52e2b9e951209268e94b
- **短哈希**：32e9f4046
- **日期**：2024-10-25（Sat Oct 26 03:28:51 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Don't change table distribution when only altering local order (#10774)
- **PR/Issue**：#10774

## 总体目的

Iceberg 通过 Spark SQL 扩展语法 `ALTER TABLE ... WRITE ORDERED BY ...` / `WRITE LOCALLY ORDERED BY ...` / `WRITE UNORDERED` / `WRITE DISTRIBUTED BY PARTITION` 来设置表的写分布模式（`write.distribution-mode`：`none`/`hash`/`range`）与排序（`sort-order`）。其中：

- `WRITE ORDERED BY` → 全局有序，对应 `DistributionMode.RANGE`（写时按 sort key 全局 shuffle 后排序）；
- `WRITE LOCALLY ORDERED BY` → 局部有序，每个 task 内部排序，**不要求改变分布模式**（数据按现有分布打散到各 task，task 内排序）；
- `WRITE UNORDERED` → 不排序，对应 `DistributionMode.NONE`；
- `WRITE DISTRIBUTED BY PARTITION` → 按分区 hash 分布，对应 `DistributionMode.HASH`。

此前的实现把"局部有序"（`LOCALLY`）和"无序"（`UNORDERED`）一并处理为 `DistributionMode.NONE`，即 `ALTER TABLE ... WRITE LOCALLY ORDERED BY xxx` 会**强制把表的 `write.distribution-mode` 改成 `none`**。这带来问题：

1. **覆盖用户已有分布设置**：如果用户原本设置了 `hash`（按分区分布）或 `range`（全局有序），现在只想再加个"局部排序"以提升局部数据聚集度，结果分布模式被悄悄改成 `none`，写入分布行为完全变了（如分区表失去 hash 聚合，导致每个 task 写入所有分区的文件，小文件爆炸）；
2. **语义混淆**：`LOCALLY ORDERED` 在语义上只是"在每个 writer 内排序"，并不蕴含"取消分布"。

本提交修正：当用户只指定 `LOCALLY ORDERED` 时，**不修改** `write.distribution-mode` 属性（保留原值）；只有 `UNORDERED` / `ORDERED`（即 `RANGE`）/ 显式 `DISTRIBUTED BY PARTITION`（即 `HASH`）才设置分布模式。

## 如何达成设计目的

把 `distributionMode` 从"必然有值"改为"可能没有值"（`Option[DistributionMode]`），用 `None` 表示"不要修改分布模式"：

1. **AST Builder**：解析 `WRITE` 子句时，区分 `UNORDERED`（→ `Some(NONE)`）、`LOCALLY`（→ `None`）、`ORDERED`/默认（→ `Some(RANGE)`）、显式 `DISTRIBUTED BY PARTITION`（→ `Some(HASH)`）。
2. **逻辑计划节点 `SetWriteDistributionAndOrdering`** 与 **物理执行节点 `SetWriteDistributionAndOrderingExec`**：字段类型从 `DistributionMode` 改为 `Option[DistributionMode]`，在执行时用 `foreach` 仅在 `Some` 情况下调用 `txn.updateProperties().set(WRITE_DISTRIBUTION_MODE, ...).commit()`。
3. **测试**：新增/调整用例覆盖"`LOCALLY ORDERED` 不改分布模式"语义，并验证从已有分布模式出发再 `LOCALLY ORDERED` 时分布模式保持不变。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSqlExtensionsAstBuilder.scala`（修改）

**修改目的**：在解析 `WRITE` 子句时把 `LOCALLY ORDERED` 视为"不修改分布模式"（`None`），与 `UNORDERED`（`Some(NONE)`）区分开。

**工作逻辑**：

```scala
// 旧
val distributionMode = if (distributionSpec != null) {
  DistributionMode.HASH
} else if (orderingSpec.UNORDERED != null || orderingSpec.LOCALLY != null) {
  DistributionMode.NONE
} else {
  DistributionMode.RANGE
}

// 新
val distributionMode = if (distributionSpec != null) {
  Some(DistributionMode.HASH)
} else if (orderingSpec.UNORDERED != null) {
  Some(DistributionMode.NONE)
} else if (orderingSpec.LOCALLY() != null) {
  None
} else {
  Some(DistributionMode.RANGE)
}
```

- `distributionSpec != null`：显式 `DISTRIBUTED BY PARTITION` → `Some(HASH)`；
- `UNORDERED`：→ `Some(NONE)`（仍要主动把分布模式设为 none，表示"不要任何分布"）；
- `LOCALLY`：→ `None`（保留原分布模式不动）；
- 默认（`ORDERED BY` 全局有序）：→ `Some(RANGE)`。

注意 `orderingSpec.LOCALLY()` 改为带括号的调用形式（Scala 无参方法调用风格），与 `UNORDERED`（字段访问）做了风格区分。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/SetWriteDistributionAndOrderingExec.scala`（修改）

**修改目的**：物理节点在 `distributionMode` 为 `None` 时跳过 `updateProperties`。

**工作逻辑**：

- 字段类型从 `distributionMode: DistributionMode` 改为 `distributionMode: Option[DistributionMode]`；
- 执行块从无条件 `txn.updateProperties().set(WRITE_DISTRIBUTION_MODE, distributionMode.modeName()).commit()` 改为：
  ```scala
  distributionMode.foreach { mode =>
    txn.updateProperties()
      .set(WRITE_DISTRIBUTION_MODE, mode.modeName())
      .commit()
  }
  ```
  `None.foreach` 不执行任何操作，因此不会发起 `updateProperties` 事务，分布模式属性原样保留。

### `spark/v3.5/spark/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/SetWriteDistributionAndOrdering.scala`（修改）

**修改目的**：逻辑计划节点同步把字段类型改为 `Option[DistributionMode]`，与 AST Builder 和物理节点对齐。仅类型声明从 `DistributionMode` 改为 `Option[DistributionMode]`，无逻辑变化。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSetWriteDistributionAndOrdering.java`（修改，+29 -2 行）

**修改目的**：补充覆盖"LOCALLY ORDERED 不修改分布模式"语义的测试，并修正既有测试的断言。

**工作逻辑**：

1. **修正既有用例**（`testSetWriteLocallyOrdered` 类用例的断言）：原来断言 `distributionMode` 等于 `"none"`，现在改为断言 `table.properties()` **不含** `WRITE_DISTRIBUTION_MODE` 键：
   ```java
   assertThat(table.properties().containsKey(TableProperties.WRITE_DISTRIBUTION_MODE)).isFalse();
   ```
   因为新建表默认没有该属性，`LOCALLY ORDERED` 也不应主动写入该属性。

2. **新增 `testSetWriteLocallyOrderedToPartitionedTable`**：先创建一张分区表（默认无序、无分布模式属性），执行 `ALTER TABLE %s WRITE LOCALLY ORDERED BY category DESC`，断言：表属性仍不含 `WRITE_DISTRIBUTION_MODE`，且 `table.sortOrder()` 为按 `category DESC` 的局部排序（`withOrderId(1).desc("category")`）。

3. **扩展 `testSetWriteDistributedByWithSort`**：原用例验证 `DISTRIBUTED BY PARTITION` + `ORDERED BY id` 后分布模式为 `hash`、sort order 为 `id asc`。新增后续步骤：再执行 `ALTER TABLE %s WRITE LOCALLY ORDERED BY id`，断言分布模式 **保持** 为之前的 `distributionMode`（即 `hash`），不被 `LOCALLY` 改成 `none`。这一步直接锁定本提交修复的核心场景——已有 hash 分布的表加局部排序不应丢失 hash 分布。

## 小结

- **成效**：修正 Spark 3.5 的 Iceberg SQL 扩展中 `ALTER TABLE ... WRITE LOCALLY ORDERED BY ...` 会错误地把表分布模式改为 `none` 的 bug，使"局部排序"语义回归本意（仅在每个 writer 内排序，不改变数据分布）。这对分区表尤其重要——避免分区表失去 hash 分布导致小文件激增。修正方式是引入 `Option[DistributionMode]`，用 `None` 表示"不动分布模式"。
- **影响范围**：仅 Spark 3.5 模块（`spark-extensions` 的 AST builder、物理/逻辑节点 + 测试）。Spark 3.4 / 3.3 等其它版本未在本提交修改（可能存在相同 bug 但需单独回写/回迁）。对 `WRITE UNORDERED` / `WRITE ORDERED BY` / `WRITE DISTRIBUTED BY PARTITION` 行为不变。
- **回迁到 1.4.x 的注意事项**：bug 修复类变更，回迁安全且推荐。需注意：
  1. 1.4.x 上若 Spark 3.5 的同一文件结构一致可直接 cherry-pick；若 1.4.x 还支持 Spark 3.3/3.4，相同 bug 也存在，应同步修复（本提交未覆盖）；
  2. 回迁后测试 `testSetWriteLocallyOrderedToPartitionedTable` 与扩展后的 `testSetWriteDistributedByWithSort` 需要随同回迁以验证修复；
  3. 由于 `distributionMode` 字段类型从 `DistributionMode` 改为 `Option[DistributionMode]`，若 1.4.x 上有自定义规则或下游代码直接构造 `SetWriteDistributionAndOrdering` / `SetWriteDistributionAndOrderingExec`，需同步调整调用方传参（用 `Some(...)` 包装或传 `None`）。
