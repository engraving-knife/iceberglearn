# 提交 1839：Core: Apply correct metric configs in GenericAppenderFactory (#12366)

## 提交信息

- **序号**：1839 / 4088
- **哈希**：f61f97112618c35e5647de6d703809c3cfeba425
- **短哈希**：f61f97112
- **日期**：2025-03-11 15:39:28 +0100
- **作者**：Xu Bai
- **提交说明**：Core: Apply correct metric configs in GenericAppenderFactory (#12366)
- **PR/Issue**：#12366

## 总体目的

本提交修复了 `GenericAppenderFactory` 中 metrics 配置不正确的问题。在此之前，`GenericAppenderFactory` 只接受一个 `Map<String, String> config` 作为配置来源，从中读取 `MetricsConfig`（通过 `MetricsConfig.fromProperties(config)`）。这种方式存在两个问题：一是当 factory 关联到一个真实的 `Table` 时，应该使用表的属性（table properties）来计算 `MetricsConfig`，而不是单独传入的 config map，因为表属性是 metrics 配置的权威来源；二是对 position delete writer，应该使用 `MetricsConfig.forPositionDelete(table)` 而非通用的 `MetricsConfig.forTable(table)`，因为 position delete 文件有不同的 metrics 需求（不需要行级 metrics）。

此外，原实现允许在已提供 table 的情况下通过 `set()`/`setAll()` 方法覆盖 metrics 配置，这会导致表属性与 writer 配置不一致的矛盾状态。本提交通过引入 `validateMetricsConfig` 方法，在 table 不为 null 时禁止设置 `write.metadata.metrics.` 开头的属性，强制调用方使用表属性来配置 metrics。

## 如何达成设计目的

整体设计是给 `GenericAppenderFactory` 增加一个可选的 `Table` 引用。新增一个包含 `Table` 参数的构造函数，当 table 不为 null 时：从表属性而非 config map 计算 `MetricsConfig`（通过 `MetricsConfig.forTable(table)` 或 `MetricsConfig.forPositionDelete(table)`）；在 `set()`/`setAll()` 时校验不允许设置 metrics 相关属性。当 table 为 null 时（向后兼容），仍使用原有的 `MetricsConfig.fromProperties(config)` 路径。原有的五参数构造函数委托给新的七参数构造函数（table 传 null），保持向后兼容。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/GenericAppenderFactory.java` (修改)

**修改目的**：引入 Table 引用，按表属性计算 MetricsConfig，并校验 metrics 配置冲突。

**工作逻辑**：

1. 新增 `private final Table table` 字段和 `import org.apache.iceberg.Table`。`config` 字段从 `Maps.newHashMap()` 初始化改为在构造函数中赋值。

2. 新增七参数构造函数 `GenericAppenderFactory(Table table, Schema schema, PartitionSpec spec, Map<String, String> config, int[] equalityFieldIds, Schema eqDeleteRowSchema, Schema posDeleteRowSchema)`：
   - 赋值 `this.table = table`，`this.config = config == null ? Maps.newHashMap() : config`。
   - 如果 table 不为 null：从 table 派生 schema/spec（若未提供），并调用 `validateMetricsConfig(this.config)` 校验。
   - 如果 table 为 null：直接使用传入的 schema/spec。

3. 原五参数构造函数改为委托：`this(null, schema, spec, null, equalityFieldIds, eqDeleteRowSchema, posDeleteRowSchema)`。

4. `set(String property, String value)` 方法：在 `config.put` 前调用 `validateMetricsConfig(ImmutableMap.of(property, value))`。

5. `setAll(Map<String, String> properties)` 方法：在 `config.putAll` 前调用 `validateMetricsConfig(properties)`。

6. `newAppender()` 方法：`MetricsConfig` 从 `MetricsConfig.fromProperties(config)` 改为 `table != null ? MetricsConfig.forTable(table) : MetricsConfig.fromProperties(config)`。

7. `newEqDeleteWriter()` 方法：同上改动。

8. `newPosDeleteWriter()` 方法：改为 `table != null ? MetricsConfig.forPositionDelete(table) : MetricsConfig.fromProperties(config)`，使用专用的 position delete metrics 配置。

9. 新增 `validateMetricsConfig(Map<String, String> writeConfig)` 私有方法：如果 table 为 null 则直接返回；如果 writeConfig 中有任何以 `write.metadata.metrics.` 开头的 key，抛出 `IllegalArgumentException("Cannot set metrics properties when the table is provided, use table properties instead")`。

### `data/src/test/java/org/apache/iceberg/TestGenericAppenderFactory.java` (修改)

**修改目的**：验证新的 metrics 配置校验逻辑和 table-aware 行为。

**工作逻辑**：

1. `createAppenderFactory` 方法：改为使用新的七参数构造函数，传入 `table`、`table.schema()`、`table.spec()`、`Maps.newHashMap()` 等。

2. 新增 `illegalSetConfig` 测试：在 table 存在时，调用 `set(TableProperties.METRICS_MAX_INFERRED_COLUMN_DEFAULTS, ...)` 应抛出 `IllegalArgumentException`。

3. 新增 `illegalSetAllConfigs` 测试：在 table 存在时，调用 `setAll(...)` 传入包含 metrics 属性的 map 应抛出异常。

4. 新增 `setConfigExcludeMetrics` 测试：在 table 存在时，设置非 metrics 属性（如 "key1"）不抛异常。

5. 新增 `setConfigWithoutTable` 测试：在不传 table 的构造函数（`new GenericAppenderFactory(SCHEMA)`）下，设置 metrics 属性不抛异常（向后兼容路径）。

6. 新增 `createFactoryWithConflictConfig` 测试：先在表属性中设置 `DEFAULT_WRITE_METRICS_MODE=Full`，然后用包含冲突 metrics 配置的 config 构造 factory，应抛出 `IllegalArgumentException`。

## 小结

本提交通过给 `GenericAppenderFactory` 引入 Table 引用，使 metrics 配置从表属性正确派生，并禁止在 table 存在时通过 config map 覆盖 metrics 配置，修复了 metrics 配置不一致的 bug。同时为 position delete writer 使用专用的 `MetricsConfig.forPositionDelete`。改动涉及 2 个文件，是 API 兼容的增强（新增构造函数，旧构造函数保持兼容）。回迁到 1.4.x 时需注意：1.4.x 中 `MetricsConfig.forPositionDelete` 方法需存在；调用方若使用了旧的五参数构造函数则行为不变，但若想获取正确的 metrics 需迁移到新的七参数构造函数。
