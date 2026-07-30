# 提交 2020：Spec: Clarify variant lower/upper bounds (#12658)

## 提交信息

- **序号**：2020 / 4088
- **哈希**：cc2e0ff9e9997c1ff473275cf270ce13d8f8b55d
- **短哈希**：cc2e0ff9e
- **日期**：2025-04-21 14:34:24 -0600
- **作者**：Aihua Xu
- **提交说明**：Spec: Clarify variant lower/upper bounds (#12658)
- **PR/Issue**：#12658

## 总体目的

这个提交在 Iceberg 规范中补充了 Variant 类型在数据文件 `lower_bounds` 和 `upper_bounds` 中的存储和使用说明。Variant 是 Iceberg 较新支持的数据类型，其内部结构是灵活的（类似 JSON），因此其上下界的语义与普通基本类型不同，需要明确规范。

规范中需要澄清的关键问题包括：
1. Variant 的上下界存储的是什么格式（序列化的 Variant 对象）。
2. 如何标识 Variant 对象中的特定字段（使用规范化的 JSON path 表达式）。
3. 上下界的准确性要求（必须覆盖所有非 null 值，数组中的值也需要覆盖）。
4. 何时不能写入边界（混合 Variant 类型时）。
5. 序列化方式和字段路径格式示例。

## 如何达成设计目的

在 `format/spec.md` 的数据文件章节中，紧接 deletion vector 相关说明之后，新增一段关于 Variant 上下界的详细说明。同时在附录的二进制单值序列化表中新增 variant 类型的行。

## 修改详情

### `format/spec.md` (修改, +17/-0 lines)

**修改目的**：补充 Variant 类型上下界的规范说明。

**工作逻辑**：

1. **Variant 上下界存储格式**：说明 `lower_bounds` 和 `upper_bounds` 映射中，Variant 类型的值存储的是序列化的 Variant 对象。对象的键是规范化 JSON path 表达式（唯一标识字段），值是该字段上下界的原始 Variant 表示。

2. **准确性要求**：字段的边界必须对数据文件中所有非 null 值准确。数组中的值也必须准确覆盖。不能为混合 Variant 类型的值写入边界（除非 null）。例如，一个包含 int64 和 null 的"measurement"字段可以有边界，但如果还包含字符串值如"n/a"则不能有边界。

3. **序列化方式**：Variant 边界对象通过拼接 Variant 编码的 metadata（包含规范化字段路径）和边界对象来序列化。

4. **字段路径格式**：使用 JSON path 格式，如 `$['location']['latitude']` 或 `$['user.name']`。特殊路径 `$` 表示 Variant 根的边界（当 Variant 数据由统一的基本类型组成时，如字符串）。

5. **路径示例**：提供了 6 个有效字段路径示例，涵盖根路径、对象字段、带特殊字符的字段名、嵌套对象、数组和数组内对象的字段。

6. **二进制单值序列化表**：在附录的序列化表中新增 `variant` 类型行，说明其序列化为"编码的 v1 metadata 拼接编码的 variant 对象，键为规范化 JSON path，值为上下界值"。

## 总结

本提交是规范文档的补充，明确了 Variant 类型在数据文件上下界中的存储格式、准确性要求、序列化方式和字段路径格式。这对于支持 Variant 类型的查询优化（如基于上下界的谓词下推）至关重要。
