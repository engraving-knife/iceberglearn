# 提交 1171：Spark 3.3, 3.4, 3.5: Supplement test case for `RollbackToTimestampProcedure` (#11171)

## 提交信息

- **序号**：1171 / 4088
- **哈希**：b2b65df4c60102852798d782470176f6ab89c2f0
- **短哈希**：b2b65df4c
- **日期**：2024-09-20（Fri Sep 20 21:17:28 2024 +0800）
- **作者**：dongwang <mingwbd@gmail.com>
- **提交说明**：Spark 3.3, 3.4, 3.5: Supplement test case for `RollbackToTimestampProcedure` (#11171)
- **PR/Issue**：#11171

## 总体目的

Iceberg 提供 `system.rollback_to_timestamp` 存储过程，让 Spark 用户把表回滚到给定时间戳之前最近的快照。该过程的语义是「找到时间戳严格小于给定值的最新快照并回滚到它」——即要求给定时间戳必须严格晚于最早快照的时间戳，否则找不到任何「更老的」快照可回滚。

`RollbackToTimestampProcedure` 在找不到有效快照时会抛 `IllegalArgumentException`，消息为 `"Cannot roll back, no valid snapshot older than: %s"`。但 Iceberg 的 Spark 集成测试套件此前没有覆盖这一边界场景：

- 给定时间戳**早于**最早快照时间戳（理论上应报错，因为没有快照比这个时间还早）。
- 给定时间戳**等于**最早快照时间戳（按「严格小于」语义也应报错，因为找不到严格更老的快照）。

这两个边界若未覆盖，可能在后续重构（如改变时间戳比较策略、引入新快照保留机制）时悄悄引入回归——例如改成「小于等于」语义后，等于最早快照的回滚会成功而不是报错，与文档约定不符。

本提交在 Spark 3.3、3.4、3.5 三个版本的 `TestRollbackToTimestampProcedure` 中各新增一个测试用例 `testRollbackToTimestampBeforeOrEqualToOldestSnapshot`，覆盖上述两个边界场景，确保 `rollback_to_timestamp` 在时间戳早于或等于最早快照时抛出预期异常。

## 如何达成设计目的

在每个 Spark 版本分支的测试类中新增 `@Test`/`@TestTemplate` 方法：

1. 建表并插入一条数据，得到表的首个快照 `firstSnapshot`。
2. 取 `firstSnapshot.timestampMillis()`，构造两个时间戳：
   - `beforeFirstSnapshot` = 首快照时间戳 - 1ms（早于最早快照）。
   - `exactFirstSnapshot` = 首快照时间戳（等于最早快照）。
3. 分别调用 `CALL %s.system.rollback_to_timestamp(timestamp => TIMESTAMP '%s', table => '%s')`，断言抛出 `IllegalArgumentException`，且消息为 `"Cannot roll back, no valid snapshot older than: %s"`，其中 `%s` 是对应时间戳的 epoch 毫秒值。
4. 通过 `assumeThat` 等机制（在 3.5 中继承自 `ExtensionsTestBase`）确保测试仅在支持该 procedure 的 catalog 上运行。

由于 Iceberg 为 Spark 3.3/3.4/3.5 维护三套独立的源码目录（`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/`），同一测试需在三个目录各加一份。三份内容几乎一致，仅因基类不同（`SparkExtensionsTestBase` vs `ExtensionsTestBase`）与测试注解不同（`@Test` vs `@TestTemplate`）而略有差异。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRollbackToTimestampProcedure.java`

**修改目的**：为 Spark 3.5 测试套件补充时间戳早于/等于最早快照的边界用例。

**工作逻辑**：
- 新增 import：`java.sql.Timestamp`、`java.time.Instant`。
- 在已有 `testRollbackToTimestamp` 系列用例之后、`testInvalidRollbackToTimestampCases` 之前，新增 `@TestTemplate` 方法 `testRollbackToTimestampBeforeOrEqualToOldestSnapshot`：

  ```java
  @TestTemplate
  public void testRollbackToTimestampBeforeOrEqualToOldestSnapshot() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot firstSnapshot = table.currentSnapshot();
    Timestamp beforeFirstSnapshot =
        Timestamp.from(Instant.ofEpochMilli(firstSnapshot.timestampMillis() - 1));
    Timestamp exactFirstSnapshot =
        Timestamp.from(Instant.ofEpochMilli(firstSnapshot.timestampMillis()));

    assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rollback_to_timestamp(timestamp => TIMESTAMP '%s', table => '%s')",
                    catalogName, beforeFirstSnapshot, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot roll back, no valid snapshot older than: %s",
            beforeFirstSnapshot.toInstant().toEpochMilli());

    assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rollback_to_timestamp(timestamp => TIMESTAMP '%s', table => '%s')",
                    catalogName, exactFirstSnapshot, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot roll back, no valid snapshot older than: %s",
            exactFirstSnapshot.toInstant().toEpochMilli());
  }
  ```

- 关键点：
  - 用 `Timestamp.from(Instant.ofEpochMilli(...))` 构造 SQL `TIMESTAMP` 字面量，避免硬编码时间字符串带来的时区/格式问题。
  - 用 `assertThatThrownBy(...).hasMessage(...)` 严格校验异常消息（含 epoch 毫秒值），确保错误路径走的是「no valid snapshot older than」分支而非其他异常。
  - `beforeFirstSnapshot` 与 `exactFirstSnapshot` 两个用例分别覆盖「严格早于」与「等于」两种边界，验证 `RollbackToTimestampProcedure` 的「严格小于」语义。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRollbackToTimestampProcedure.java`

**修改目的**：为 Spark 3.4 测试套件补充同样用例。

**工作逻辑**：与 3.5 版本完全相同的测试方法逻辑，差异仅在：
- 测试注解为 `@Test`（3.4 仍用 JUnit 4 风格的 `@Test`，而非 3.5 的 `@TestTemplate`）。
- 基类为 `SparkExtensionsTestBase`（3.4 的基类名）。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRollbackToTimestampProcedure.java`

**修改目的**：为 Spark 3.3 测试套件补充同样用例。

**工作逻辑**：与 3.4 版本完全一致（`@Test` + `SparkExtensionsTestBase`）。

## 小结

- **成效**：Spark 3.3/3.4/3.5 三个版本的 `RollbackToTimestampProcedure` 测试套件现覆盖「时间戳早于最早快照」与「时间戳等于最早快照」两个边界场景，验证 `IllegalArgumentException` 与错误消息符合「严格小于」语义，避免后续重构引入回归。
- **影响范围**：3 个测试文件，每个新增 35 行（含 2 行 import + 33 行测试方法），共 105 行新增测试代码，无任何生产代码变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯测试补强，对运行时无任何影响，**可以安全回迁到 1.4.x**。
  - 回迁价值：1.4.x 通常支持 Spark 3.3/3.4/3.5（与 main 同期），若 1.4.x 的 `TestRollbackToTimestampProcedure` 也缺少此边界覆盖，回迁可提升 1.4.x 的测试质量，有助于在 1.4.x 上做相关重构时及时发现回归。
  - 回迁时需 cherry-pick 三个文件改动。注意 1.4.x 的 Spark 版本目录结构与 main 一致（`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/`），但基类名、注解风格可能与 main 略有差异（取决于 1.4.x 的测试基础设施版本），cherry-pick 时需手工核对。
  - 若 1.4.x 的 `RollbackToTimestampProcedure` 实现与 main 不同（如错误消息文案不同），断言 `hasMessage(...)` 可能失败，需根据 1.4.x 实际错误消息调整。
  - 测试用例使用的 `validationCatalog`、`tableIdent`、`catalogName` 等字段需在 1.4.x 测试基类中存在，回迁前需确认。
