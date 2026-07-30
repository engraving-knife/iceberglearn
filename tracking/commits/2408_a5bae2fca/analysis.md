# 提交 2408：Backport: Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13658)

## 提交信息

- **序号**：2408 / 4088
- **哈希**：a5bae2fcad2ef8deb81f3b1e8f0722137a406dd4
- **短哈希**：a5bae2fca
- **日期**：2025-07-24 16:14:45 +0200
- **作者**：Maximilian Michels
- **提交说明**：Backport: Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13658)
- **PR/Issue**：#13658（backports #13640）

## 总体目的

此提交将 PR #13640（提交 2403）backport 到 Flink 1.19 和 1.20 分支。核心清理是**简化 `TestEvolveSchemaVisitor` 中的 `loadUpdateApi` 方法，从需要两个参数（schema + lastColumnId）简化为只需 schema 参数，内部通过 `schema.highestFieldId()` 自动计算 lastColumnId**。

原先调用方需要手动传入正确的 `lastColumnId` 值（如 0、1、2、3 等），这些值需要与 schema 中实际的最大字段 ID 匹配，手动维护容易出错且增加测试维护成本。清理后消除了手动传入可能不一致的风险。

此提交的代码变更与 2403（flink/v2.0）完全一致，同时应用到 flink/v1.19 和 flink/v1.20 两个版本。

## 如何达成设计目的

与 2403 完全一致的设计：

1. **简化方法签名**：`loadUpdateApi(Schema schema, int lastColumnId)` 改为 `loadUpdateApi(Schema schema)`。
2. **自动计算 lastColumnId**：方法内部使用 `schema.highestFieldId()` 替代手动传入参数。
3. **统一更新所有调用点**：将所有 `loadUpdateApi(schema, N)` 调用改为 `loadUpdateApi(schema)`。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+29/-28 lines)

**修改目的**：简化 Flink 1.19 的 loadUpdateApi 方法并更新所有调用点。

**工作逻辑**：
- 方法签名从 `loadUpdateApi(Schema schema, int lastColumnId)` 改为 `loadUpdateApi(Schema schema)`。
- 方法体内 `constructor.newInstance(schema, lastColumnId)` 改为 `constructor.newInstance(schema, schema.highestFieldId())`。
- 所有约 20 处调用点从 `loadUpdateApi(schema, N)` 更新为 `loadUpdateApi(schema)`，移除手动维护的 lastColumnId 参数。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+29/-28 lines)

**修改目的**：Flink 1.20 的相同清理。

**工作逻辑**：与 1.19 完全相同的修改。

## 总结

此提交是 2403 在 Flink 1.19 和 1.20 分支上的对应 backport，代码变更完全一致。通过 `schema.highestFieldId()` 自动计算替代手动传入 `lastColumnId` 参数，简化了测试代码并消除了手动维护字段 ID 的出错风险。与 2403（Flink 2.0）一起，覆盖了所有维护中的 Flink 版本。
