# 提交 1906：Spec: Geo spec simplifications (#12533)

## 提交信息

- **序号**：1906 / 4088
- **哈希**：1a94bc0346cb1cdc2cb60d1a666564693e83aed5
- **短哈希**：1a94bc034
- **日期**：2025-03-23 17:55:31 -0700
- **作者**：Szehon Ho
- **提交说明**：Spec: Geo spec simplifications (#12533)
- **PR/Issue**：#12533

## 总体目的

这个提交简化了 Iceberg 规范中地理类型（geometry 和 geography）的 JSON 序列化格式定义。

Iceberg 正在引入地理空间数据类型（geometry 和 geography），此前的规范定义中，这两种类型使用 JSON 对象格式进行序列化（包含 type、crs、algorithm 等字段）。本提交将其简化为 JSON 字符串格式，类似于 variant 类型的序列化方式。

简化后的格式更紧凑、更易解析：
- `geometry(C)`：从 JSON 对象 `{"type": "geometry", "crs": "srid:4326"}` 简化为 JSON 字符串 `"geometry(srid:4326)"`
- `geography(C, A)`：从 JSON 对象 `{"type": "geography", "crs": "srid:4326", "algorithm": "spherical"}` 简化为 JSON 字符串 `"geography(srid:4326,spherical)"`

同时更新了默认值约束，将 geometry 和 geography 类型与 unknown 类型一样，要求默认值为 null。

## 如何达成设计目的

通过修改 `format/spec.md` 规范文档中的类型序列化表格和默认值约束段落来完成。

## 修改详情

### `format/spec.md` (修改, +3/-3 lines)

**修改目的**：简化地理类型的 JSON 序列化格式定义。

**工作逻辑**：

1. 默认值约束：将 "All columns of `unknown` type must default to null" 扩展为 "All columns of `unknown`, `geometry`, and `geography` types must default to null"，明确地理类型的默认值也必须为 null。

2. 类型序列化表格：
   - `geometry(C)`：从 JSON 对象格式改为 JSON 字符串格式 `"geometry(<C>)"`
   - `geography(C, A)`：从 JSON 对象格式改为 JSON 字符串格式 `"geography(<C>,<E>)"`
   - 示例从嵌套 JSON 对象改为简洁的字符串表示

## 总结

本提交简化了 Iceberg 规范中 geometry 和 geography 类型的 JSON 序列化格式，从复杂的嵌套 JSON 对象改为紧凑的字符串格式。同时明确了这两种类型的默认值必须为 null。这是地理类型规范设计阶段的简化改进。
