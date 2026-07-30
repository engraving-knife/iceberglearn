# 提交 2024：Spec: Allow the use of `source-id` in V3 (#12644)

## 提交信息

- **序号**：2024 / 4088
- **哈希**：12b1f52ea46b423f80c5bd83f7cf1d6b1db519f4
- **短哈希**：12b1f52ea
- **日期**：2025-04-22 10:29:44 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spec: Allow the use of `source-id` in V3 (#12644)
- **PR/Issue**：#12644

## 总体目的

这个提交修改了 Iceberg 规范中关于 V3 分区字段和排序字段的 `source-id` 和 `source-ids` 的使用规则，允许在 V3 中继续使用 `source-id`（单参数变换），而非强制只使用 `source-ids`（多参数列表）。

此前规范规定在 V3 元数据中，分区字段和排序字段必须使用 `source-ids`（JSON int 列表），而 `source-id`（JSON int）被标记为"omitted"（省略）。这意味着即使变换只有一个参数（如 `bucket[16](id)`），也必须写成 `source-ids: [1]` 而非 `source-id: 1`。这增加了元数据的冗余，且对于大量使用单参数变换的表来说不够简洁。

本提交将 V3 中 `source-id` 从"omitted"改为"optional"，允许根据变换参数数量选择使用 `source-id`（单参数）或 `source-ids`（多参数），使规范更灵活、更简洁。

## 如何达成设计目的

修改 `format/spec.md` 中三处关于分区字段和排序字段的规范描述：
1. 分区字段表中 `source-id` 和 `source-ids` 的 V3 列从"omitted/required"改为"optional/optional"。
2. 排序字段表中同样的变更。
3. 新增 Notes 说明何时使用 `source-id` vs `source-ids`。
4. 更新 V3 元数据写入说明和 V1/V2 读取兼容说明。

## 修改详情

### `format/spec.md` (修改, +13/-12 lines)

**修改目的**：允许 V3 中使用 `source-id`。

**工作逻辑**：

1. **分区字段表**：`source-id` 的 V3 列从"omitted"改为"optional"，`source-ids` 的 V3 列从"required"改为"optional"。新增 Note："For partition fields with a transform with a single argument, only `source-id` is written. In case of a multi-argument transform, only `source-ids` is written."

2. **排序字段表**：与分区字段表相同的变更。移除了原来的"In v3 metadata, writers must use only `source-ids` because v3 requires reader support for multi-arg transforms."说明，替换为相同的 Note。

3. **V3 元数据写入说明**：将原来的"`source-ids` was added and is required / `source-id` is no longer required and should be omitted; always use `source-ids` instead"改为"`source-ids` was added and must be written in the case of a multi-argument transform / `source-id` must be written in the case of single-argument transforms"。

4. **移除 V1/V2 读取兼容说明**：移除了"Reading v1 or v2 metadata for v3: `source-ids` should default to a single-value list of the value of `source-id`"的说明，因为现在 V3 可以直接使用 `source-id`，不需要转换。

## 总结

本提交修改了规范，允许 V3 中根据变换参数数量灵活使用 `source-id`（单参数）或 `source-ids`（多参数），而非强制只使用 `source-ids`。这简化了单参数变换的元数据表示，减少了冗余，同时保持了向后兼容性。
