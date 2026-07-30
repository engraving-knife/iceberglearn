# 提交 2407：Flink: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13659)

## 提交信息

- **序号**：2407 / 4088
- **哈希**：be4db9023e1828c805f33509060ca755f5c2df39
- **短哈希**：be4db9023
- **日期**：2025-07-24 16:14:05 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13659)
- **PR/Issue**：#13659

## 总体目的

此提交修复了 Flink 动态 Sink（DynamicSink）在 schema 演进中的一个边界场景：当表中存在 required（非空）字段，但传入的数据 schema（data schema）中不包含该字段时，需要自动将表中的 required 字段转换为 optional 字段。

原先的 `CompareSchemasVisitor` 在比较数据 schema 和表 schema 时，如果表中有 required 字段在数据 schema 中不存在，不会触发 `SCHEMA_UPDATE_NEEDED`，而是直接进入 `DATA_CONVERSION_NEEDED` 路径。这导致 required 字段未被转换为 optional，后续写入时会因为 required 字段缺失而失败。

修复后，在比较逻辑中新增检查：遍历表 schema 的所有字段，如果某个 required 字段在数据 schema 的 struct 中不存在，则返回 `SCHEMA_UPDATE_NEEDED`，触发将该字段从 required 转换为 optional 的 schema 演进操作。这使得动态 Sink 能自动适配数据 schema 中缺失的字段，将表 schema 中的 required 字段降级为 optional，保证写入能继续进行。

## 如何达成设计目的

关键设计点：

1. **CompareSchemasVisitor 新增检查**：在 struct 比较中，遍历表 schema 的所有字段，检查 required 字段是否在数据 schema 中存在。如果不存在，返回 `SCHEMA_UPDATE_NEEDED`。
2. **检查位置**：新增的检查放在 `SCHEMA_UPDATE_NEEDED` 已有条件之后、`DATA_CONVERSION_NEEDED` 判断之前，确保优先触发 schema 更新。
3. **测试覆盖**：新增两个测试用例分别验证 required 字段缺失时触发 schema 更新，以及 optional 字段缺失时不触发 required 变更（仅 DATA_CONVERSION_NEEDED）。
4. **端到端测试**：新增 `testRowEvolutionMakeMissingRequiredFieldOptional` 验证完整流程，重构原有非向后兼容测试。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (+9/-0 lines)

**修改目的**：检测表 schema 中 required 字段在数据 schema 中缺失的场景。

**工作逻辑**：在 struct 比较方法中，新增循环遍历 `tableSchemaType.asStructType().fields()`。对于每个 `tableField`，如果 `tableField.isRequired()` 且 `struct.field(tableField.name()) == null`（数据 schema 中不存在该字段），则返回 `Result.SCHEMA_UPDATE_NEEDED`。这确保后续 schema 演进会将该 required 字段转换为 optional。注释说明了原因：如果字段在输入 schema 中不存在，就不会被访问到，因此无法通过常规的 required/optional 兼容性检查来处理，唯一选择是将表字段改为 optional。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestCompareSchemasVisitor.java` (+22/-1 lines)

**修改目的**：新增 required 字段缺失场景的单元测试。

**工作逻辑**：
- 原 `testWithRequiredChange` 重命名为 `testRequiredChangeForMatchingField`（更准确描述测试意图——字段名匹配时的 required 变更）。
- 新增 `testRequiredChangeForNonMatchingField`：数据 schema 仅有 `id` 字段，表 schema 有 `id`（optional）和 `extra`（required），验证双向访问都返回 `SCHEMA_UPDATE_NEEDED`。
- 新增 `testNoRequiredChangeForNonMatchingField`：数据 schema 有 `id`（required），表 schema 有 `id`（required）和 `extra`（optional），验证返回 `DATA_CONVERSION_NEEDED`（因 extra 是 optional，不需要 schema 更新）。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+33/-8 lines)

**修改目的**：新增端到端测试并重构原有非向后兼容测试。

**工作逻辑**：
- 新增 `testRowEvolutionMakeMissingRequiredFieldOptional`：创建包含 `id`（optional）和 `data`（required）字段的表，然后使用仅包含 `id` 字段的 schema 写入数据，验证表中的 `data` 字段被自动从 required 转换为 optional，写入成功。
- 重构 `testSchemaEvolutionNonBackwardsCompatible`：原测试场景是 required 字段缺失导致错误，现在改为类型不兼容（int → string）导致错误。错误消息也从 "Field 2... is non-nullable but does not exist" 改为 "Cannot change column type: id: int -> string"。`DynamicIcebergDataImpl` 构造函数调用也增加了 existingSchema 参数。

## 总结

此提交修复了 Flink 动态 Sink 在数据 schema 缺失表 required 字段时的处理逻辑，从直接失败改为自动将 required 字段降级为 optional，提升了 schema 演进的灵活性。修改涉及比较逻辑增强、单元测试和端到端测试三个层面，覆盖完整。
