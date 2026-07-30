# 提交 3929：Kafka Connect: evolve table schema when record schema is updated but value is null (#16826)

## 提交信息

- **序号**：3929 / 4088
- **哈希**：ac874e80bd5f411b60578d51fcb9861e2f6d45ce
- **短哈希**：ac874e80b
- **日期**：2026-06-23 00:13:29 +0200
- **作者**：Thomas Thornton
- **提交说明**：Kafka Connect: evolve table schema when record schema is updated but value is null (#16826)
- **PR/Issue**：#16826

## 总体目的

这次提交修复了 Kafka Connect 集成中一个嵌套 schema 演进的缺陷。当 Kafka Connect 记录中某个结构化字段（如 struct/list/map）的值为 null，但该字段的 Connect schema 已经发生了变化（新增子字段、类型提升、变为 optional 等）时，原本的 `RecordConverter` 不会检测到这些 schema 变化，因为 schema 演进的检测发生在值转换的过程中——而值为 null 时直接跳过了嵌套遍历。

这意味着如果一条记录中嵌套字段值为 null，但 Connect schema 中该嵌套字段添加了新列（例如 `nested.b`），那么这个新增列不会被加入到 Iceberg 表的 schema 中，导致后续非 null 的记录写入时出现列不匹配错误。

该提交通过引入 `evolveSchemaFromConnectSchema` 方法，在值为 null 但 schema 可能已变化时，递归遍历 Connect schema 来发现并触发所有嵌套层级的 schema 演进事件（addColumn、updateType、makeOptional），确保即使值为 null 也能完成 schema 演进。

## 如何达成设计目的

设计上区分了两种场景的 schema 演进处理：
1. **有值时**：保持原有的 `convertValue` 流程，在值转换过程中检测 schema 变化；一旦检测到某层级的更新就停止该分支的递归（deferring 到重新转换）。
2. **值为 null 时**：新增独立的 `evolveSchemaFromConnectSchema` 方法，它无条件递归遍历整个 Connect schema，对所有嵌套层级（STRUCT/ARRAY/MAP 的值部分）检查并触发 addColumn、updateType、makeOptional 事件。

该方法只在值为 null 且当前层级没有其他 schema 更新时才被调用（`!hasSchemaUpdates`），避免在有值场景下重复触发演进。对于 MAP 类型，仅递归处理 value schema，不处理 key schema（key 类型变更不被支持）。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java` (+95/-1 lines)

**修改目的**：新增值为 null 时的嵌套 schema 演进能力。

**工作逻辑**：
1. 新增 `LOG` 和 `LoggerFactory` 用于记录类型不匹配的警告。
2. 在 `convertToStruct` 方法中，当字段值为 null 且 `schemaUpdateConsumer != null` 且当前无其他 schema 更新时，调用 `evolveSchemaFromConnectSchema(recordField.schema(), tableField.type(), tableField.fieldId(), schemaUpdateConsumer)` 进行递归 schema 演进检测。同时复用 `recordFieldValue` 变量避免重复 `struct.get()` 调用。
3. 新增 `evolveSchemaFromConnectSchema` 方法，根据 Connect schema 类型递归处理：
   - **STRUCT**：遍历字段，若字段在 Iceberg 表中不存在则触发 `addColumn`；若存在则检查 `needsDataTypeUpdate`（类型提升）和 `makeOptional`（变可选），并递归处理子字段。
   - **ARRAY**：递归处理 element schema 与 Iceberg list elementType 的对应。
   - **MAP**：递归处理 value schema 与 Iceberg map valueType 的对应（不处理 key）。
4. 新增 `logMismatchedType` 辅助方法，在 Connect schema 类型与 Iceberg table 类型不匹配时记录警告日志。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestRecordConverter.java` (+420/-0 lines)

**修改目的**：为值为 null 时的嵌套 schema 演进添加全面测试。

**工作逻辑**：新增 8 个测试用例覆盖各种场景：
1. `testNestedSchemaEvolutionStructWithNullValue`：嵌套 struct 值为 null 时新增列。
2. `testNoSchemaEvolutionStructWithNullValue`：嵌套 struct 值为 null 但 schema 无变化时不应触发演进。
3. `testNestedSchemaEvolutionListOfStructsWithNullValue`：list 中 struct 元素的嵌套字段为 null 时新增列。
4. `testNestedSchemaEvolutionMapOfStructsWithNullValue`：map value 为 struct 且嵌套字段为 null 时新增列。
5. `testNestedSchemaEvolutionStructInStructWithNullParent`：父 struct 为 null 时检测到深层子字段新增。
6. `testNestedSchemaEvolutionTypePromotionWithNullValue`：值为 null 时触发类型提升（int→long, float→double）。
7. `testNestedSchemaEvolutionMakeOptionalWithNullValue`：值为 null 时触发 makeOptional。
8. `testSchemaEvolutionForFieldAndNestedFieldsAcrossTwoRecords`：跨两条记录验证字段本身和嵌套字段的分阶段演进——第一条记录触发父字段 makeOptional，第二条记录在 schema 已更新后触发嵌套新增列。
9. `testNoNestedSchemaEvolutionMapKeyWithNullValue`：验证 map key 的 schema 变更不被演进（key 类型不支持变更）。

## 总结

这次提交显著增强了 Kafka Connect 的 schema 演进能力，确保即使嵌套字段值为 null，其 schema 变化（新增列、类型提升、变可选）也能被正确检测和应用。通过引入独立的递归 schema 遍历方法和全面的测试覆盖，修复了一个实际数据管道中常见的 schema 漂移问题，避免后续非 null 记录写入时出现列不匹配错误。
