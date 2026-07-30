# 提交 3090：Core: Support case-insensitive field lookups in SchemaUpdate (#14734)

## 提交信息

- **序号**：3090 / 4088
- **哈希**：a1c1c1b5a855a0ce63785f62660cf2490765d4e0
- **短哈希**：a1c1c1b5a
- **日期**：2026-01-09
- **作者**：Maximilian Michels
- **提交说明**：Core: Support case-insensitive field lookups in SchemaUpdate (#14734)
- **PR/Issue**：#14734

## 总体目的

该提交修复了 `SchemaUpdate` 在大小写不敏感（case-insensitive）模式下对新增字段进行移动（move）操作时的 bug。Iceberg 的 `UpdateSchema` API 支持大小写敏感和不敏感两种模式，通过 `caseSensitive(boolean)` 方法设置。在大小写不敏感模式下，字段查找（如 `findField`）会忽略大小写进行匹配。

然而，`SchemaUpdate` 内部维护了一个 `addedNameToId` 映射，用于追踪新增字段的名称到 ID 的对应关系。这个映射在添加字段时使用原始字段名（`fullName`）作为键存储，但在查找时（如 `isAdded`、`findForUpdate`、`findForMove`）也使用原始名称查找。当用户在大小写不敏感模式下添加字段后，用不同大小写的名称来引用该字段进行移动操作时（例如添加 `"data"` 字段后用 `"dAtA"` 来移动），查找会失败，因为 `addedNameToId` 中的键是原始大小写。

具体来说，已有字段（schema 中已存在的字段）的查找通过 `findField` 方法已经正确处理了大小写敏感性（`caseSensitive ? schema.findField(fieldName) : schema.caseInsensitiveFindField(fieldName)`），但新增字段的查找直接使用 `addedNameToId.get(name)` 进行精确匹配，未考虑大小写不敏感场景。这导致在大小写不敏感模式下，对新增字段的 `moveFirst`、`moveBefore`、`moveAfter`、`updateColumn` 等操作无法正确找到刚添加的字段。

该提交通过引入 `caseSensitivityAwareName` 方法，在大小写不敏感模式下将名称统一转为小写后再作为 `addedNameToId` 的键，使得添加和查找都使用规范化后的小写名称，从而实现大小写不敏感的匹配。

## 如何达成设计目的

在 `SchemaUpdate` 中新增私有方法 `caseSensitivityAwareName(String name)`，根据 `caseSensitive` 标志决定是否将名称转为小写（使用 `Locale.ROOT` 避免土耳其语等 locale 问题）。然后在所有涉及 `addedNameToId` 的 put/get/containsKey 操作中统一调用该方法进行名称规范化，共四处：`add` 方法中的 put、`isAdded` 中的 containsKey、`findForUpdate` 中的 get、`findForMove` 中的 get。同时新增三个测试用例覆盖大小写不敏感模式下的添加+移动场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java` (+9/-4 lines)

**修改目的**：使新增字段的名称查找支持大小写不敏感模式。

**工作逻辑**：
新增 `import java.util.Locale`。新增私有方法 `caseSensitivityAwareName(String name)`，当 `caseSensitive` 为 true 时返回原始名称，为 false 时返回 `name.toLowerCase(Locale.ROOT)`。然后修改四处对 `addedNameToId` 的操作：

1. `add` 方法中 `addedNameToId.put(fullName, newId)` 改为 `addedNameToId.put(caseSensitivityAwareName(fullName), newId)`，确保新增字段以规范化名称存储。
2. `isAdded` 方法中 `addedNameToId.containsKey(name)` 改为 `addedNameToId.containsKey(caseSensitivityAwareName(name))`。
3. `findForUpdate` 方法中 `addedNameToId.get(name)` 改为 `addedNameToId.get(caseSensitivityAwareName(name))`，使更新操作能找到新增字段。
4. `findForMove` 方法中 `addedNameToId.get(name)` 改为 `addedNameToId.get(caseSensitivityAwareName(name))`，使移动操作能找到新增字段。

这四处修改确保了无论在添加时还是后续查找时，名称都经过相同的大小写规范化处理，从而在大小写不敏感模式下实现一致的字段匹配。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java` (+76/-0 lines)

**修改目的**：验证大小写不敏感模式下新增字段的移动操作正确性。

**工作逻辑**：
新增三个测试方法：

`testCaseInsensitiveAddTopLevelAndMove`：在仅有 `id` 字段的 schema 上，以大小写不敏感模式添加 `data` 字段，然后用 `"dAtA"`（大小写混合）调用 `moveFirst` 将其移到首位。验证结果 schema 中 `data` 在 `id` 之前。这直接测试了 bug 场景——移动一个刚添加的字段时使用不同大小写的名称。

`testCaseInsensitiveAddNestedAndMove`：在包含嵌套 `struct.field1` 的 schema 上，以大小写不敏感模式用大写 `"STRUCT"` 添加嵌套字段 `field2`，然后用 `"STRUCT.FIELD2"` 调用 `moveFirst`。验证 `field2` 在 `field1` 之前。测试嵌套字段的大小写不敏感添加和移动。

`testCaseInsensitiveMoveAfterNewlyAddedField`：在包含 `struct.field1` 的 schema 上，先用小写 `"struct"` 添加 `field2`，再用大写 `"STRUCT"` 添加 `field3`，然后用 `"STRUCT.FIELD3"` 和 `"struct.FIELD2"` 执行 `moveAfter`（将 field3 移到 field2 之后）。验证三个字段的顺序为 field1、field2、field3。测试混合大小写下对两个新增字段的相对移动操作。

## 总结

该提交修复了 `SchemaUpdate` 在大小写不敏感模式下无法正确查找和移动新增字段的缺陷。通过引入统一的名称规范化方法，确保 `addedNameToId` 映射的存取在大小写不敏感模式下使用小写键，与已有字段的 `caseInsensitiveFindField` 行为保持一致。三个新测试用例全面覆盖了顶层字段、嵌套字段和混合大小写的移动场景，验证了修复的正确性。这是一个影响 schema 演进功能正确性的重要 bug 修复。
