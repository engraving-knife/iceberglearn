# 提交 3973：Spec: Add optional specific-name to UDF definition model (#16727)

## 提交信息

- **序号**：3973 / 4088
- **哈希**：d6aa0bf5c244cf6934eaa1820f1a88d099610f7b
- **短哈希**：d6aa0bf5c
- **日期**：2026-07-01 15:22:22 -0700
- **作者**：Szehon Ho
- **提交说明**：Spec: Add optional specific-name to UDF definition model (#16727)
- **PR/Issue**：#16727

## 总体目的

本提交为 Iceberg 的 UDF（用户定义函数）规范模型新增了一个可选的 `specific-name` 字段。该字段类似于 SQL 标准中的 routine *specific name*，为单个函数定义提供一个稳定的、人类可读的名称句柄，独立于函数签名（参数类型元组派生的 `definition-id`）。

此前，UDF 定义仅通过 `definition-id`（基于规范参数类型元组的标识符）标识。当需要通过名称而非签名来引用特定定义时（例如 SQL 的 `DROP SPECIFIC FUNCTION` 语句），缺少稳定的人类可读名称。`specific-name` 填补了这一空白。

## 如何达成设计目的

在 UDF spec 的定义模型表中新增 `specific-name` 行，标记为可选字段，并新增 "Specific Name" 章节解释其语义和约束（存在时必须在 UDF 元数据的所有定义中唯一）。

## 修改详情

### `format/udf-spec.md` (+8/-0 lines)

**修改目的**：在 UDF 定义模型中新增 specific-name 字段。

**工作逻辑**：
1. 在定义字段表格中新增行：
```markdown
| *optional*  | `specific-name`  | `string`  | A user-assignable name for this definition; must be unique (see [Specific Name](#specific-name)).  |
```
2. 新增 "Specific Name" 章节：
```markdown
The `specific-name` is an optional, user-assignable name for a single definition,
analogous to the SQL standard's routine *specific name*. It provides a stable,
human-readable handle for a definition that is independent of its signature
(e.g., for SQL statements such as `DROP SPECIFIC FUNCTION`).
When present, `specific-name` **must** be unique among all definitions within the UDF metadata.
```

## 总结

本提交为 UDF 规范增加了 `specific-name` 可选字段，提供了独立于签名的稳定名称标识，支持如 `DROP SPECIFIC FUNCTION` 等 SQL 语句场景。这是一个规范层面的增量改进，增强了 UDF 元数据的可管理性。
