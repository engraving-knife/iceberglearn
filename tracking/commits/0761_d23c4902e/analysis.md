# 提交 0761：Spark: Backport tests for struct aggregation pushdown to 3.3/3.4, cleanup assertion (#10333)

## 提交信息

- **序号**：0761 / 4088
- **哈希**：d23c4902eb7ed319176bb7d74e04b6f2175e3593
- **短哈希**：d23c4902e
- **日期**：2024-05-14 01:10:43 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Backport tests for struct aggregation pushdown to 3.3/3.4, cleanup assertion (#10333)
- **PR/Issue**：#10333

## 总体目的

本提交是一次**测试回迁（test backport）**，将 Spark 3.5 模块中已有的"结构体（STRUCT）类型字段聚合下推"测试用例同步到 Spark 3.3 和 Spark 3.4 模块，并顺带修正 Spark 3.5 模块中一处 AssertJ 断言的调用顺序问题。其核心动机是保证 Iceberg 在多 Spark 版本（3.3/3.4/3.5）下对 STRUCT 字段聚合下推行为的测试覆盖一致——因为这三个 Spark 版本的 Iceberg 代码路径都支持 STRUCT 字段聚合下推，若仅在 3.5 中有测试，则 3.3/3.4 的回归风险无法被及时发现。

同时，提交标题中提到的"cleanup assertion"指 Spark 3.5 模块里 `assertExplainContains` 方法对 AssertJ 的 `Assertions.assertThat(...).isTrue().as(...)` 调用顺序做了修正——原写法先 `isTrue()` 再 `as()` 不符合 AssertJ 链式 API 约定（描述应在断言之前设置才有效），修正为 `Assertions.assertThat(explainString).as(...).contains(fragment)`，使失败时的描述信息能正确输出。

## 如何达成设计目的

### 测试回迁策略

Iceberg 为每个 Spark 版本维护独立模块（`spark/v3.3`、`spark/v3.4`、`spark/v3.5`），各模块下的 `TestAggregatePushDown.java` 在结构上一致但各自独立。Spark 3.5 模块此前已新增了 4 个针对 STRUCT 字段聚合下推的测试用例（结构体整数字段、嵌套结构体、结构体时间戳字段、分桶列上的聚合下推），以及两个辅助断言方法（`assertAggregates` 和 `assertExplainContains`）。本提交将这 4 个测试方法和 2 个辅助方法**原样复制**到 Spark 3.3 和 Spark 3.4 模块的 `TestAggregatePushDown.java` 中，使三个版本测试对齐。

### 测试覆盖的场景

回迁的 4 个测试用例覆盖了 STRUCT 字段聚合下推的关键路径：

1. **`testAggregationPushdownStructInteger`**：单层 STRUCT 包含 BIGINT 字段，验证 `COUNT/MAX/MIN` 聚合能下推到 STRUCT 内部字段 `struct_with_int.c1`，并包含 NULL 值处理。
2. **`testAggregationPushdownNestedStruct`**：四层嵌套结构体 `STRUCT<c1:STRUCT<c2:STRUCT<c3:STRUCT<c4:BIGINT>>>>`，验证对深层嵌套字段 `struct_with_int.c1.c2.c3.c4` 的聚合下推。
3. **`testAggregationPushdownStructTimestamp`**：STRUCT 包含 TIMESTAMP 字段，验证时间戳类型字段的聚合下推。
4. **`testAggregationPushdownOnBucketedColumn`**：表按 `bucket(8, id)` 分桶，验证在分桶列 `id` 上的聚合下推（含 NULL 值）。

每个测试都通过 `assertAggregates` 校验聚合结果正确性，并通过 `assertExplainContains` 校验 EXPLAIN 计划中包含 `count(...)`、`max(...)`、`min(...)` 下推片段。

### 断言清理

Spark 3.5 的 `assertExplainContains` 原实现为：

```java
Assertions.assertThat(explainString.contains(fragment))
    .isTrue()
    .as("Expected to find plan fragment in explain plan");
```

这种写法的问题是 `as(...)` 在 `isTrue()` 之后调用，AssertJ 中 `as` 仅对**其后**的断言生效，因此这里的描述实际不会应用到 `isTrue()` 上。修正为：

```java
Assertions.assertThat(explainString)
    .as("Expected to find plan fragment in explain plan")
    .contains(fragment);
```

这样描述先设置，`contains` 断言失败时能正确输出提示信息，且语义更清晰（直接断言"包含"而非"布尔为真"）。

## 修改详情

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：回迁 4 个 STRUCT 聚合下推测试和 2 个辅助方法到 Spark 3.3 模块。

**工作逻辑**：
- 新增 import `java.util.Arrays` 和 `org.assertj.core.api.Assertions`。
- 在 `testAggregationPushdownForComplexTypes`（原复杂类型不下推测试）之后新增 4 个 `@Test` 方法：`testAggregationPushdownStructInteger`、`testAggregationPushdownNestedStruct`、`testAggregationPushdownStructTimestamp`、`testAggregationPushdownOnBucketedColumn`。
- 新增 2 个 private 辅助方法：`assertAggregates(List<Object[]>, Object, Object, Object)` 用 AssertJ 校验 count/max/min 结果；`assertExplainContains(List<Object[]>, String...)` 将 EXPLAIN 结果转小写后逐个片段断言包含。
- 共新增 122 行，无删除。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：回迁同样的 4 个测试和 2 个辅助方法到 Spark 3.4 模块。

**工作逻辑**：与 Spark 3.3 改动**完全一致**（同样的 import、4 个测试方法、2 个辅助方法，122 行新增）。这是因为 3.3 和 3.4 的 `TestAggregatePushDown.java` 在此提交前内容相同（基于同一基线），且回迁的测试代码与 Spark 版本无关，可原样复制。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：清理 `assertExplainContains` 中的断言调用顺序。

**工作逻辑**：将 `Assertions.assertThat(explainString.contains(fragment)).isTrue().as(...)` 改为 `Assertions.assertThat(explainString).as(...).contains(fragment)`，共 3 行变更（6 减 3）。注意 Spark 3.5 版本 `TestAggregatePushDown` 继承自 `CatalogTestBase`（使用 JUnit 5 的 `@TestTemplate`），而 3.3/3.4 版本继承自 `SparkCatalogTestBase`（使用 JUnit 4 的 `@Test`），但回迁的测试方法体本身与框架无关，可跨版本复用。

## 小结

- **成效**：Spark 3.3 和 Spark 3.4 模块获得了与 3.5 一致的 STRUCT 字段聚合下推测试覆盖（4 个测试用例），同时修正了 3.5 中一处 AssertJ 断言描述顺序问题，使失败信息更准确。三个 Spark 版本的聚合下推测试基线对齐，便于后续同步维护。
- **影响范围**：仅影响测试代码（`src/test`），不改动任何生产代码，对运行时行为零影响。涉及 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 三个模块的 `TestAggregatePushDown.java`。
- **回迁注意事项**：
  1. 这是纯测试回迁，不依赖任何生产代码改动，cherry-pick 到 1.4.x 分支风险低，通常无冲突。
  2. 回迁前提是 1.4.x 分支的 Spark 3.3/3.4/3.5 模块已支持 STRUCT 字段聚合下推（即对应的 `SparkAggregatePushDown` / `SparkUtil` 等生产代码已具备该能力）。若 1.4.x 分支的聚合下推功能本身落后，这些测试可能失败（反映功能缺口，而非测试本身问题）。
  3. 回迁的测试依赖 AssertJ（`org.assertj.core.api.Assertions`），1.4.x 分支的 Spark 测试依赖中已包含 AssertJ，无需额外引入。
  4. Spark 3.5 模块的断言清理（3 行改动）与 3.3/3.4 的测试回迁是独立的两个改动，若仅需测试覆盖可只 cherry-pick 3.3/3.4 部分；但建议一并回迁以保持三版本一致。
  5. 注意 Spark 3.5 的 `TestAggregatePushDown` 继承 `CatalogTestBase`（JUnit 5），而 3.3/3.4 继承 `SparkCatalogTestBase`（JUnit 4），回迁的测试方法体不涉及框架特定 API，可原样复制。
