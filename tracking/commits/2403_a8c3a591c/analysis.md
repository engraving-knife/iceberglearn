# 提交 2403：Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13640)

## 提交信息

- **序号**：2403 / 4088
- **哈希**：a8c3a591c597a94a461481a6918bb63c4fa7c961
- **短哈希**：a8c3a591c
- **日期**：2025-07-24 10:56:21 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13640)
- **PR/Issue**：#13640

## 总体目的

此提交是对 `TestEvolveSchemaVisitor` 测试类的代码清理（cleanup）。原先 `loadUpdateApi` 方法需要两个参数：`Schema schema` 和 `int lastColumnId`，调用方需要手动传入正确的 `lastColumnId` 值（如 0、1、2、3、5、9 等），这些值需要与 schema 中实际的最大字段 ID 匹配。手动维护这些值容易出错且增加了测试的维护成本。

清理后，`loadUpdateApi` 方法简化为只接受 `Schema schema` 参数，内部通过 `schema.highestFieldId()` 自动计算 `lastColumnId`，消除了手动传入可能不一致的风险。所有调用点也相应简化。

## 如何达成设计目的

关键设计点：

1. **简化方法签名**：`loadUpdateApi(Schema schema, int lastColumnId)` 改为 `loadUpdateApi(Schema schema)`。
2. **自动计算 lastColumnId**：在方法内部使用 `schema.highestFieldId()` 替代手动传入的 `lastColumnId` 参数。
3. **统一更新所有调用点**：将测试中所有 `loadUpdateApi(schema, N)` 调用改为 `loadUpdateApi(schema)`。

## 修改详情

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+29/-28 lines)

**修改目的**：简化 `loadUpdateApi` 方法签名并更新所有调用点。

**工作逻辑**：
- 方法签名从 `private static UpdateSchema loadUpdateApi(Schema schema, int lastColumnId)` 改为 `private static UpdateSchema loadUpdateApi(Schema schema)`。
- 方法体内 `constructor.newInstance(schema, lastColumnId)` 改为 `constructor.newInstance(schema, schema.highestFieldId())`，自动从 schema 获取最大字段 ID。
- 所有约 20 处调用点从 `loadUpdateApi(schema, N)` 更新为 `loadUpdateApi(schema)`，移除了手动维护的 `lastColumnId` 参数（如 0、1、2、3、4、5、9 等）。
- 个别调用处还清理了多余的空行。

## 总结

此提交是纯测试代码清理，通过 `schema.highestFieldId()` 自动计算替代手动传入 `lastColumnId` 参数，简化了 `loadUpdateApi` 方法签名和所有调用点。消除了手动维护字段 ID 的出错风险，降低了测试维护成本，不改变任何测试逻辑或覆盖范围。
