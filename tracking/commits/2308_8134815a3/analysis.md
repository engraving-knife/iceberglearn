# 提交 2308：API, Core, Spark: Ignore partition fields that are dropped from the current-schema (#11868)

## 提交信息

- **序号**：2308 / 4088
- **哈希**：8134815a3a04f3139fccf55a7e02c336e7a1a6e8
- **短哈希**：8134815a3
- **日期**：2025-07-02 09:02:34 -0600
- **作者**：Fokko Driesprong
- **提交说明**：API, Core, Spark: Ignore partition fields that are dropped from the current-schema (#11868)
- **PR/Issue**：#11868

## 总体目的

本提交解决了当分区字段的源列从当前 schema 中被删除后，导致的多种错误。在 Iceberg 中，分区规范（PartitionSpec）引用 schema 中的源字段。当用户删除一个列时，该列上的分区字段不会被自动删除（在 v2 表中，分区字段会被标记为 void transform），但分区规范仍然保留对该已删除字段的引用。

此前，当系统尝试构建分区类型或验证分区规范兼容性时，会尝试查找已删除字段的类型。由于字段已不存在，`schema.findType(field.sourceId())` 返回 `null`，导致后续操作失败（如 NPE 或类型检查异常）。

修复方案是在多个层面处理已删除的分区字段：
1. **构建分区类型时**：对已删除的源字段使用 `UnknownType` 代替 `null`
2. **验证兼容性时**：允许跳过已删除字段的检查
3. **解析分区规范时**：允许绑定到缺少字段的 schema
4. **计算表分区类型时**：过滤掉引用已删除字段的分区字段

## 如何达成设计目的

修改分为四个层面：

1. **`PartitionSpec`**：在 `checkCompatibility` 方法中添加 `allowMissingFields` 参数，当源字段不存在时跳过兼容性检查。在 `partitionType` 构建中，当源类型为 `null` 时使用 `UnknownType`。Builder 的 `build` 方法添加重载版本支持 `allowMissingFields` 参数。

2. **`UnboundPartitionSpec`**：添加 `bind(Schema, boolean)` 重载方法，将 `allowMissingFields` 传递到 Builder。

3. **`PartitionSpecParser`**：在 `fromJson(Schema, JsonNode)` 中调用 `bind(schema, true)`，允许从 JSON 解析时引用已删除的字段。

4. **`Partitioning`**：将 `allFieldIds` 方法改为 `allActiveFieldIds`，接收 schema 参数并过滤掉引用已删除字段的分区字段。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+16/-4 lines)

**修改目的**：处理已删除源字段的类型和兼容性检查。

**工作逻辑**：
- **`partitionType` 方法**：在构建分区类型的 `StructType` 时，如果 `sourceType == null`（字段已删除），则使用 `Types.UnknownType.get()` 作为 `resultType`，避免 NPE。
- **`Builder.build` 方法**：新增 `build(boolean allowMissingFields)` 重载方法，原 `build()` 调用 `build(false)` 保持向后兼容。
- **`checkCompatibility` 方法**：新增 `checkCompatibility(spec, schema, boolean allowMissingFields)` 重载方法。当 `allowMissingFields` 为 `true` 且 `sourceType == null` 时，跳过该字段的兼容性检查（`continue`），避免因找不到字段类型而失败。

### `api/src/main/java/org/apache/iceberg/UnboundPartitionSpec.java` (+4/-0 lines)

**修改目的**：支持带 `ignoreMissingFields` 参数的绑定。

**工作逻辑**：新增 `bind(Schema schema, boolean ignoreMissingFields)` 方法，调用 `copyToBuilder(schema).build(ignoreMissingFields)`。原 `bind(Schema)` 方法保持不变，调用 `build()`（即 `build(false)`）。

### `core/src/main/java/org/apache/iceberg/PartitionSpecParser.java` (+1/-1 lines)

**修改目的**：在从 JSON 解析分区规范时允许引用已删除的字段。

**工作逻辑**：`fromJson(Schema schema, JsonNode json)` 方法中，将 `fromJson(json).bind(schema)` 改为 `fromJson(json).bind(schema, true)`。这确保了从元数据 JSON 解析历史分区规范时，即使源字段已被删除也能正确解析。

### `core/src/main/java/org/apache/iceberg/Partitioning.java` (+4/-4 lines)

**修改目的**：计算表分区类型时过滤掉引用已删除字段的分区字段。

**工作逻辑**：
- `partitionType(Table table)` 方法中，将 `allFieldIds(specs)` 改为 `allActiveFieldIds(table.schema(), specs)`，传入当前 schema 用于过滤。
- `allFieldIds` 方法重命名为 `allActiveFieldIds`，新增 `Schema schema` 参数，添加 `.filter(field -> schema.findField(field.sourceId()) != null)` 过滤条件。只收集当前 schema 中仍然存在的分区字段 ID。

### `core/src/test/java/org/apache/iceberg/TestPartitioning.java` (+30/-2 lines)

**修改目的**：添加测试验证删除分区字段和源列后的行为。

**工作逻辑**：新增 `testPartitionTypeIgnoreInactiveFields` 测试：
1. 创建表，验证初始分区类型包含 `data` 和 `category_bucket` 字段
2. 删除 `category_bucket` 分区字段和 `category` 列，验证分区类型只剩 `data` 字段
3. 进一步删除 `data` 分区字段和 `data` 列，验证分区类型为空

还包含一处小重构：将 `((HasTableOperations) table).operations()` 简化为 `table.operations()`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java` (+46/-0 lines)

**修改目的**：添加端到端测试验证删除分区字段和源列后查询正常工作。

**工作逻辑**：新增 `runCreateAndDropPartitionField` 辅助方法和两个测试方法：
- `testDropPartitionAndSourceColumnLong`：测试 `col_ts` 列的各种分区变换（identity、year、month、day），删除分区字段和源列后查询
- `testDropPartitionAndSourceColumnTimestamp`：测试 `col_long` 列的各种分区变换（identity、truncate、bucket），删除分区字段和源列后查询

测试流程：创建表 → 插入数据 → 添加分区字段 → 插入数据 → 删除分区字段 → 插入数据 → 删除源列 → 查询验证结果正确。

## 总结

本提交系统性地解决了分区字段引用已删除源列导致的各种错误。修改覆盖了 API 层（PartitionSpec、UnboundPartitionSpec）、Core 层（PartitionSpecParser、Partitioning）和 Spark 层（测试），确保从元数据解析到查询执行的整个链路都能正确处理已删除的分区字段。这对于表的 schema 演进场景（特别是删除列操作）的稳定性具有重要意义。
