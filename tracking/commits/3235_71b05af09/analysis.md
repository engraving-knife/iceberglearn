# 提交 3235：Spark 4.1: Simplify description and toString in scans (#15281)

## 提交信息

- **序号**：3235 / 4088
- **哈希**：71b05af09e1012c38eadd2e9393a517dfaf6ab21
- **短哈希**：71b05af09
- **日期**：2026-02-10
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Simplify description and toString in scans (#15281)
- **PR/Issue**：#15281

## 总体目的

本提交对 Spark 4.1 中各 Iceberg 扫描（scan）类的 `description()` 和 `toString()` 方法进行统一化和简化。在 Spark 的数据源 API 中，`Scan.description()` 用于 EXPLAIN 输出和查询计划展示，`toString()` 用于日志和调试。此前各扫描类对这两个方法的实现不一致：有的类同时实现了两个方法但内容差异较大（`toString` 包含 schema 类型、大小写敏感等冗余信息，`description` 格式各异），有的类仅实现其中一个。此外，`equals()` 和 `hashCode()` 方法中有的使用 `filterExpressions().toString()` 进行比较，有的使用 `Spark3Util.describe(filterExpressions)`，两者可能产生不同结果导致语义不一致。

具体问题包括：格式字符串不统一（有的以类名开头，有的以表名开头，有的使用 `[filters=...]` 括号语法，有的使用 `filters=...`）；`SparkStagedScan.hashCode()` 存在 bug（`splitSize, splitSize` 重复，应为 `splitSize, splitLookback`）；过滤器描述方式不统一（有的直接 `toString()`，有的用 `Spark3Util.describe()`）。

本提交统一了所有扫描类的描述格式为 `IcebergXxxScan(table=..., filters=..., groupedBy=...)`，使 `toString()` 委托给 `description()`，统一使用 `Spark3Util.describe()` 进行过滤器描述，并将过滤器描述方法集中到基类 `SparkScan.filtersDesc()` 中。

## 如何达成设计目的

在基类 `SparkScan` 中新增 `filtersDesc()` 方法统一过滤器描述，在 `SparkPartitioningAwareScan` 中新增 `groupingKeyDesc()` 方法描述分组键。各子类将 `toString()` 改为委托 `description()`，简化格式字符串移除冗余字段。`equals()` 和 `hashCode()` 统一使用 `filtersDesc()` 替代 `filterExpressions().toString()`。同时修复 `SparkStagedScan.hashCode()` 的重复字段 bug，并更新相关测试断言以匹配新格式。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (+15/-8 lines)

**修改目的**：在基类提供统一的过滤器描述方法和 toString 委托。

**工作逻辑**：
新增 `filtersDesc()` 方法返回 `Spark3Util.describe(filterExpressions)`，供子类的 `equals`、`hashCode` 和 `description` 统一使用。将原有的 `description()` 方法（包含分组键拼接逻辑）移除，改为 `toString()` 方法委托 `description()`——即 `toString()` 调用 `description()`，确保两者一致。子类需自行实现 `description()`。`groupingKeyType()` 保留为基类方法。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPartitioningAwareScan.java` (+7/-0 lines)

**修改目的**：提供分组键描述方法。

**工作逻辑**：
新增 `groupingKeyDesc()` 方法，将 `groupingKeyType()` 的字段名通过 `NestedField::name` 提取并用逗号连接，返回如 `"data"` 的分组键描述字符串，供子类的 `description()` 使用。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java` (+23/-14 lines)

**修改目的**：统一 description/toString 格式和 equals/hashCode 中的过滤器比较。

**工作逻辑**：
`equals()` 和 `hashCode()` 中将 `filterExpressions().toString()` 和 `runtimeFilterExpressions.toString()` 替换为 `filtersDesc()` 和 `runtimeFiltersDesc()`，确保与 `description()` 使用相同的描述方式。将 `toString()` 改为 `description()`，格式简化为 `"IcebergScan(table=%s, branch=%s, filters=%s, runtimeFilters=%s, groupedBy=%s)"`——移除了 `type`（schema 类型）和 `caseSensitive`，新增 `groupedBy`。新增 `runtimeFiltersDesc()` 私有方法返回 `Spark3Util.describe(runtimeFilterExpressions)`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogScan.java` (+25/-14 lines)

**修改目的**：合并 description 和 toString，统一格式。

**工作逻辑**：
`description()` 格式改为 `"IcebergChangelogScan(table=%s, fromSnapshotId=%d, toSnapshotId=%d, filters=%s)"`，移除了原来的前缀式格式和 `Spark3Util.describe(filters)` 内联调用，改用 `filtersDesc()`。删除了独立的 `toString()` 方法（继承基类的 `toString()` → `description()` 委托）。`equals()` 和 `hashCode()` 中 `filters.toString()` 替换为 `filtersDesc()`。新增 `filtersDesc()` 私有方法。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+14/-10 lines)

**修改目的**：统一 description/toString 格式和过滤器比较。

**工作逻辑**：
`equals()` 和 `hashCode()` 中 `filterExpressions().toString()` 替换为 `filtersDesc()`。`toString()` 改为 `description()`，格式简化为 `"IcebergCopyOnWriteScan(table=%s, filters=%s, groupedBy=%s)"`——移除 `type` 和 `caseSensitive`，新增 `groupedBy`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkLocalScan.java` (+8/-5 lines)

**修改目的**：统一格式，toString 委托 description。

**工作逻辑**：
`description()` 格式改为 `"IcebergLocalScan(table=%s, filters=%s)"`（原先为 `"%s [filters=%s]"` 前缀式）。`toString()` 简化为直接返回 `description()`，移除了包含 schema 类型和原始过滤器列表的旧实现。移除了不再需要的 `SparkSchemaUtil` 导入。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java` (+8/-6 lines)

**修改目的**：简化 description 并修复 hashCode bug。

**工作逻辑**：
`hashCode()` 中修复了 `splitSize, splitSize` 重复为 `splitSize, splitLookback`。`toString()` 改为 `description()`，格式简化为 `"IcebergStagedScan(table=%s, taskSetID=%s)"`——移除 `type` 和 `caseSensitive`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+45/-0 lines)

**修改目的**：验证新的 description 格式。

**工作逻辑**：
新增 `testBatchQueryScanDescription` 和 `testCopyOnWriteScanDescription` 两个测试。前者推送过滤器 `id = 1, id > 0` 并启用数据分组，验证 description 包含 `"IcebergScan"`、表名、`"filters=id = 1, id > 0"` 和 `"groupedBy=data"`。后者类似地验证 CoW 扫描的 description 包含 `"IcebergCopyOnWriteScan"`、`"filters=id = 2, id < 10"` 和 `"groupedBy=data"`。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java` (+1/-1 lines) / `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestFilterPushDown.java` (+1/-1 lines)

**修改目的**：适配新的 description 格式。

**工作逻辑**：
两个测试中验证推送过滤器的断言从 `.contains("[filters=" + icebergFilters + ",")` 改为 `.contains(", filters=" + icebergFilters + ",")`，匹配新格式中 `filters=` 前为逗号而非方括号。

## 总结

本提交统一了 Spark 4.1 中所有 Iceberg 扫描类的 `description()` 和 `toString()` 实现，消除了格式不一致和冗余信息，使 `toString()` 一致委托 `description()`。通过将过滤器描述统一到 `Spark3Util.describe()` 并提取到基类方法，确保了 `equals()`/`hashCode()` 与 `description()` 之间的语义一致性。同时修复了 `SparkStagedScan.hashCode()` 中 `splitSize` 重复的 bug。这一重构提升了 EXPLAIN 输出的可读性和一致性，降低了维护成本。
