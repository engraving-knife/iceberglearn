# 提交 1746：Core: Fix CI: Update tests with UnknownType from required to optional (#12316)

## 提交信息

- **序号**：1746 / 4088
- **哈希**：e79295b53e7b1d4143401f863f5d9181111c3bf4
- **短哈希**：e79295b53
- **日期**：2025-02-18 13:09:53 -0800
- **作者**：Fokko Driesprong
- **提交说明**：Core: Fix CI: Update tests with UnknownType from required to optional (#12316)
- **PR/Issue**：#12316

## 总体目的

在提交 1743（#12302）中，`NestedField` 构建器新增了校验：必填字段（required）不能使用 `UnknownType`，因为 `UnknownType` 表示字段类型尚未确定，无法为必填字段提供有效的非 null 值。该校验导致 CI 中已有的测试代码失败，因为这些测试在参数化测试中使用 `UnknownType` 作为必填字段的类型。

本提交是提交 1743 的后续修复，目标是将 CI 中失败的测试代码里使用 `UnknownType` 的字段从必填（`required`）改为可选（`optional`），使测试符合新的校验规则，恢复 CI 的正常运行。

## 如何达成设计目的

提交修改了两个测试文件：

1. **`TestTypeUtil.java`**：在 8 个参数化测试方法中，将使用 `testType`（包括 `UnknownType`）的字段从 `required` 改为 `optional`，包括源 schema 和期望 schema 中的对应字段。

2. **`TestSchemaUnionByFieldName.java`**：在 `testAddTopLevelMapOfPrimitives` 测试方法中，添加对 `UnknownType` 的特殊处理——跳过该类型的测试，因为 `UnknownType` 必须是可选的，而 map 的 key 必须是必填的，两者冲突。

## 修改详情

### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java`（修改, +13/-13 lines）

**修改目的**：将参数化测试中 `UnknownType` 字段从必填改为可选。

**工作逻辑**：在以下 8 个参数化测试方法中，将 `required(1, "data", testType)` 改为 `optional(1, "data", testType)`，同时将期望结果中对应的 `required` 也改为 `optional`：
1. `testAssignIdsWithType`：源类型和期望类型的 data 字段改为 optional。
2. `testAssignFreshIdsWithType`：源 schema 和期望 schema 的 data 字段改为 optional。
3. `testReassignIdsWithType`：源 schema 和源参考 schema 的 data 字段改为 optional。
4. `testIndexByIdWithType`：源 schema 的 data 字段改为 optional。
5. `testIndexNameByIdWithType`：源 schema 的 data 字段改为 optional。
6. `testProjectWithType`：源 schema 和期望 schema 的 data 字段改为 optional。
7. `testGetProjectedIdsWithType`：源 schema 的 data 字段改为 optional。
8. `testReassignDocWithType`：源 schema 和文档源 schema 的 data 字段改为 optional。

这些测试使用 `testTypes` 参数源，其中包含 `UnknownType`。由于 `UnknownType` 不能用于必填字段，所有使用 `testType` 的字段都改为可选，确保测试在 `testType` 为 `UnknownType` 时不会触发校验异常。

### `core/src/test/java/org/apache/iceberg/TestSchemaUnionByFieldName.java`（修改, +6 lines）

**修改目的**：跳过 `UnknownType` 在 map key 场景下的测试。

**工作逻辑**：在 `testAddTopLevelMapOfPrimitives` 方法的循环中，新增对 `UnknownType` 的跳过逻辑：
```java
if (primitiveType.equals(UnknownType.get())) {
    // The UnknownType has to be optional, and this conflicts with the map key that must be required
    continue;
}
```
这是因为 map 的 key 必须是必填的（`MapType.ofOptional` 的 key 仍为 required），而 `UnknownType` 不能用于必填字段，两者冲突。因此跳过 `UnknownType` 在该测试中的验证。

## 小结

- **成效**：成功修复了提交 1743 引入的校验导致的 CI 测试失败，将 8 个参数化测试中 `UnknownType` 字段从必填改为可选，并跳过了 map key 场景下不兼容的 `UnknownType` 测试。
- **影响范围**：仅涉及 api 和 core 模块的测试文件，不影响任何生产代码或功能。
- **回迁到 1.4.x 的注意事项**：此提交是提交 1743 的后续修复，必须与 1743 一起回迁。如果单独回迁 1743 而不回迁此提交，CI 中的测试会失败。建议与 1743 一起回迁。
