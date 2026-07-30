# 提交 3966：Spec: Add spec for expressions (#16652)

## 提交信息

- **序号**：3966 / 4088
- **哈希**：a2fb64bff8ea2c299d56f81d666778bf4365fdac
- **短哈希**：a2fb64bff
- **日期**：2026-06-29 14:24:00 -0700
- **作者**：Ryan Blue
- **提交说明**：Spec: Add spec for expressions (#16652)
- **PR/Issue**：#16652

## 总体目的

本提交为 Iceberg 项目新增了表达式（Expressions）规范文档。在此之前，Iceberg 的表达式仅存在于实现 API 中，没有正式的规范定义。本规范定义了一个通用的表达式结构，使得简单表达式可以被存储和交换。

存储表达式的用例包括数据验证（如 `CHECK` 约束）和默认值（如 `current_timestamp()`）。交换表达式的用例包括 catalog 协议中的服务端扫描规划。该规范有意保持简单，将复杂性推到函数调用层面，而非在表达式本身中引入复杂结构。

## 如何达成设计目的

规范采用分层设计：
1. **值表达式（Value expressions）**：产生类型化值，包括常量、字段引用和函数调用。
2. **谓词（Predicates）**：产生 true/false 的布尔表达式，包括测试（IS NULL、IN 等）、比较（=、<、> 等）和布尔逻辑（AND、OR、NOT）。

规范刻意约束表达式范围，复杂逻辑通过函数调用实现。函数引用使用 catalog 和函数标识符，支持保留名（`sql_functions`、`iceberg_functions`）。规范还定义了 null-safe 比较语义、2 值布尔逻辑（而非 SQL 的 3 值逻辑）以及与 REST catalog 旧表达式的向后兼容。

## 修改详情

### `format/expressions-spec.md` (+311/-0 lines)

**修改目的**：新增 Iceberg 表达式规范。

**工作逻辑**：文档结构包含以下主要部分：

1. **Overview**：阐述规范目标——定义简单表达式结构，避免复杂性。表达式被约束为值表达式和谓词两类。

2. **Structure**：
   - **Value expressions**：
     - 常量值（Constant values）：表示特定类型值
     - 字段引用（Field reference）：支持命名引用（unbound）和 ID 引用（bound）。ID 引用用于存储表达式（如 CHECK 约束绑定字段 ID，重命名列不影响），命名引用用于查询过滤器
     - 函数应用（Apply function）：通过 catalog 和函数标识符引用函数，支持保留名 `sql_functions` 和 `iceberg_functions`
   - **Predicates**：
     - 测试（Tests）：IS NULL、IS NOT NULL、IS NaN、STARTS WITH、IN 等
     - 比较（Comparisons）：=、!=、<、<=、>、>=，必须 null-safe
     - 布尔逻辑（Boolean logic）：AND、OR、NOT，使用 2 值逻辑

3. **比较语义**：详细定义了各类型的比较规则，包括 boolean（false < true）、binary/fixed/string/uuid（unsigned byte-wise）、decimal（signed, scale-independent）、float/double（IEEE 754 + NaN 特殊处理）。

4. **null-safe 比较**：`null = null` 为 true，`34 < null` 为 false 等。

5. **与 REST catalog 表达式的兼容性**：旧的 term-based 形式被标记为 deprecated，但需支持向后兼容。

## 总结

本提交是 Iceberg 规范层面的重要进展，首次正式定义了表达式结构规范。该规范为后续的 CHECK 约束、默认值、服务端扫描规划等功能奠定了基础。规范设计遵循 Iceberg 一贯的保守风格——刻意限制复杂度，通过函数调用处理复杂逻辑，保持核心表达式结构的简单和可移植性。
