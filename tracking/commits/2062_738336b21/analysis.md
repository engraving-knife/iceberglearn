# 提交 2062：Spark 3.4: Migrate Partition Transform tests (#12941)

## 提交信息

- **序号**：2062 / 4088
- **哈希**：738336b21c268b95811d9ae6b0eeb0bf76728fa5
- **短哈希**：738336b21
- **日期**：2025-04-30 16:02:30 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.4: Migrate Partition Transform tests (#12941)
- **PR/Issue**：#12941

## 总体目的

延续 2059 提交（#12934）的测试框架迁移工作，本次针对 Spark 3.4 模块下 `spark/sql` 包中与分区变换（partition transform）相关的 8 个测试类完成 JUnit 4 → JUnit 5 与新基类迁移，并对 Spark 3.5 中已完成部分迁移的同类测试补加 `@ExtendWith(ParameterizedTestExtension.class)` 注解使其完全对齐。这些测试覆盖 `days`/`hours`/`months`/`years`/`bucket`/`truncate` 等分区变换函数、storage partitioned joins、以及无时区时间戳等场景。

迁移目的与 #12934 一致：统一测试基础设施，最终移除 JUnit 4 与老的 `SparkTestBaseWithCatalog` 基类。

## 如何达成设计目的

迁移模式与 #12934 一致：
1. **基类替换**：`extends SparkTestBaseWithCatalog` → `extends TestBaseWithCatalog`，并加 `@ExtendWith(ParameterizedTestExtension.class)`。
2. **生命周期注解**：`@Before` → `@BeforeEach`；`@Test` → `@TestTemplate`。
3. **断言工具**：`org.junit.Assert.assertEquals`/`assertNull` 等替换为 AssertJ 的 `assertThat(...).as(...).isEqualTo(...)` / `.isNull()`；异常断言用 `assertThatThrownBy`。
4. **Spark 3.5 对齐**：为 3.5 中已完成大部分迁移的同类测试补加 `@ExtendWith(ParameterizedTestExtension.class)` 注解（每文件约 +3 行）。

业务逻辑（被测的 SQL 与期望值）保持不变。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkBucketFunction.java` (修改, 大量行)

**修改目的**：迁移 bucket 函数测试到 JUnit 5 + 新基类。

**工作逻辑**：
基类替换为 `TestBaseWithCatalog`，加 `@ExtendWith(ParameterizedTestExtension.class)`；`@Before` → `@BeforeEach`，`@Test` → `@TestTemplate`；所有 `Assert.assertEquals`/`assertNull` 改写为 AssertJ `assertThat` 链式断言。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkDaysFunction.java` (修改)

**修改目的**：迁移 days 函数测试。

**工作逻辑**：
基类与注解迁移；`testDates`/`testTimestamps`/`testTimestampNtz` 中对 `system.days(...)` 的断言改写为 `assertThat(scalarSql(...)).as("...").isEqualTo(Date.valueOf(...))`，null 断言改写为 `.isNull()`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkHoursFunction.java` (修改)

**修改目的**：迁移 hours 函数测试。

**工作逻辑**：
同上模式迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkMonthsFunction.java` (修改)

**修改目的**：迁移 months 函数测试。

**工作逻辑**：
同上模式迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkTruncateFunction.java` (修改, 396 行)

**修改目的**：迁移 truncate 函数测试；改动量最大。

**工作逻辑**：
基类与注解迁移；针对 `system.truncate(width, value)` 在不同类型（int/long/string/binary/decimal）上的断言全部改写为 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkYearsFunction.java` (修改)

**修改目的**：迁移 years 函数测试。

**工作逻辑**：
同上模式迁移。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestStoragePartitionedJoins.java` (修改, 125 行)

**修改目的**：迁移 storage partitioned joins 测试。

**工作逻辑**：
基类与注解迁移；断言改写为 AssertJ。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestTimestampWithoutZone.java` (修改)

**修改目的**：迁移无时区时间戳测试。

**工作逻辑**：
同上模式迁移。

### Spark 3.5 对应 7 个文件 (修改, 各 +3 行)

**修改目的**：为 Spark 3.5 中已迁移的同类测试补加 `@ExtendWith(ParameterizedTestExtension.class)`。

**工作逻辑**：
在 `TestSparkBucketFunction`、`TestSparkDaysFunction`、`TestSparkHoursFunction`、`TestSparkMonthsFunction`、`TestSparkTruncateFunction`、`TestSparkYearsFunction`、`TestTimestampWithoutZone` 顶部添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解及对应 import。

## 总结

本提交将 Spark 3.4（并对齐 Spark 3.5）`spark/sql` 包下 8 个分区变换相关测试类从 JUnit 4 + `SparkTestBaseWithCatalog` 迁移到 JUnit 5 + `TestBaseWithCatalog` + `ParameterizedTestExtension`，断言改写为 AssertJ。是 #12934 之后测试基础设施现代化的继续，业务逻辑不变。
