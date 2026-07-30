# 提交 2471：Core: Fix metrics column limit with nested columns (#13039)

## 提交信息

- **序号**：2471 / 4088
- **哈希**：1b0e4b3a5f6df535828e6c40d381777b1f456df3
- **短哈希**：1b0e4b3a5
- **日期**：2025-08-07 12:27:02 -0700
- **作者**：Joshua Kolash
- **提交说明**：Core: Fix metrics column limit with nested columns (#13039)
- **PR/Issue**：#13039

## 总体目的

该提交修复了 MetricsConfig 中列限制（100 列上限）在处理嵌套列（struct 等复合类型）时的缺陷。此前 struct 列没有遵循 100 列的限制，可能显著增大 manifest 文件中列统计信息的大小。

Iceberg 通过 `METRICS_MAX_INFERRED_COLUMN_DEFAULTS` 属性（默认值 100）限制默认收集 metrics 的列数，以避免 manifest 文件过大。然而旧实现仅统计 schema 顶层列的数量（`schema.columns().size()`），并通过 `subList(0, maxInferredDefaultColumns)` 截取顶层列。这意味着如果一个 struct 列包含大量嵌套子字段，这些子字段都会被纳入 metrics 收集范围，从而绕过列数限制，导致 manifest 文件中的列统计膨胀。该提交将限制改为对所有列（包括嵌套列）统一计数，且优先选取离顶层最近的列。

## 如何达成设计目的

核心设计点如下：

1. **新增 `limitFieldIds` 方法**：使用 `TypeUtil.CustomOrderSchemaVisitor` 按自定义顺序遍历 schema，收集不超过 limit 数量的字段 ID。遍历采用广度优先的策略，优先收集靠近顶层的字段。

2. **优先级策略**：在 struct 中，先按顺序收集当前层级的 eligible 字段（primitive 或 variant 类型），再递归访问子字段。当已收集字段数达到 limit 时停止。这保证了"precedence going to columns that are closest to the top level"。

3. **重构推断逻辑**：将原来基于顶层列 `subList` 的截取方式，改为基于 `limitFieldIds` 返回的字段 ID 集合来设置默认 metrics 模式。同时将 `schema.columns().size() <= maxInferredDefaultColumns` 的判断改为 `TypeUtil.getProjectedIds(schema).size() <= maxInferredDefaultColumns`，使判断也考虑嵌套字段总数。

4. **支持 variant 类型**：新增 `metricsEligible` 判断方法，将 variant 类型也视为可收集 metrics 的类型。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsConfig.java` (+107/-7 lines)

**修改目的**：实现新的列限制逻辑，正确处理嵌套列。

**工作逻辑**：

新增 `limitFieldIds(Schema schema, int limit)` 方法，使用 `CustomOrderSchemaVisitor` 遍历 schema。访问器维护一个 `idSet`，通过 `shouldContinue()` 判断是否已达 limit。在 `struct` 方法中，先收集当前层级的 eligible 字段 ID，再通过 `fieldResults` 迭代器访问子字段；`list` 和 `map` 方法类似处理元素/键值。这实现了广度优先、优先顶层的字段选择策略。

重构 `forTable` 相关的推断逻辑：原代码用 `schema.columns().subList(0, maxInferredDefaultColumns)` 截取顶层列，新代码改用 `limitFieldIds(schema, maxInferredDefaultColumns)` 获取应收集 metrics 的字段 ID 集合，并用 `TypeUtil.getProjectedIds(schema).size()` 判断是否超过限制。

### `core/src/test/java/org/apache/iceberg/TestMetricsConfig.java` (+73/-0 lines, 新文件)

**修改目的**：为 `limitFieldIds` 方法添加单元测试。

**工作逻辑**：包含三个测试用例：
- `testNestedStructsRespectedInLimit`：验证 struct 内字段与顶层字段竞争限制名额时，limit=1 选中顶层字段（id=4）。
- `testNestedMap`：验证 map 的 key 字段参与限制，limit=2 选中顶层字段和 map key。
- `testNestedListOfMaps`：验证 list 嵌套 map 的场景，limit=2 选中顶层字段和 map key。

### `core/src/test/java/org/apache/iceberg/TestMetricsModes.java` (+73/-0 lines)

**修改目的**：测试 variant 类型支持及嵌套 struct 的 metrics 配置。

**工作逻辑**：
- `testMetricsVariantSupported`：验证 V3 formatVersion 下 variant 类型可被收集 metrics。
- `testMetricsConfigNestedTypesStructs`：验证 struct 嵌套字段遵循列限制，limit=2 时选中 `col_struct.a` 和 `top`，而 `col_struct.b` 为 None。

### `data/src/test/java/org/apache/iceberg/io/TestWriterMetrics.java` (+37/-0 lines)

**修改目的**：验证写入时列限制被正确应用。

**工作逻辑**：`testMaxColumnsBounded` 测试设置 `METRICS_MAX_INFERRED_COLUMN_DEFAULTS=3`，写入包含 ID、DATA、STRUCT 字段的行，验证 `dataFile.upperBounds().keySet().size()` 等于 3。

## 总结

该提交修复了嵌套列场景下 metrics 列限制失效的 bug。通过引入 `limitFieldIds` 方法按广度优先策略统一计数所有字段（含嵌套），确保 100 列限制对 struct/list/map 等复合类型同样生效，优先保留靠近顶层的列。这不仅防止了 manifest 文件因嵌套列统计而膨胀，还顺带支持了 variant 类型的 metrics 收集。配套了充分的单元测试和集成测试覆盖各种嵌套场景。
