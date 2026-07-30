# 提交 1715：Docs: Add missing types to the spec v3 summary (#12219)

## 提交信息

- **序号**：1715 / 4088
- **哈希**：31e9dc768cef47df5eb007577f25ef3bbff86f61
- **短哈希**：31e9dc768
- **日期**：2025-02-11 10:46:06 +0100
- **作者**：Gang Wu
- **提交说明**：Docs: Add missing types to the spec v3 summary (#12219)
- **PR/Issue**：#12219

## 总体目的

修正 Iceberg 规范文档中 v3 新增数据类型摘要的遗漏。在之前的提交（如 1713 引入 geo 类型、variant 类型等）中，新增了多种数据类型，但规范文档中概述 v3 新增类型的摘要部分未同步更新，遗漏了 `variant`、`geometry`、`geography` 等类型。

规范文档的摘要部分是读者快速了解 v3 变更的重要入口，遗漏新增类型会导致读者对 v3 的能力产生不完整的认识。此提交确保摘要部分与实际规范内容保持一致。

## 如何达成设计目的

修改 `format/spec.md` 中两处关于 v3 新增类型的摘要描述，将遗漏的类型补充进去。

## 修改详情

### `format/spec.md`（修改, +2/-2 lines）

**修改目的**：补充 v3 新增数据类型摘要中遗漏的类型。

**工作逻辑**：

1. **第 48 行附近**：v3 新增数据类型列表从 "nanosecond timestamp(tz), unknown" 改为 "nanosecond timestamp(tz), unknown, variant, geometry, geography"，补充了 variant、geometry 和 geography 三种类型。

2. **第 1599 行附近**：v3 新增类型总结从 "Types `variant`, `unknown`, `timestamp_ns`, and `timestamptz_ns` are added in v3." 改为 "Types `variant`, `geometry`, `geography`, `unknown`, `timestamp_ns`, and `timestamptz_ns` are added in v3."，补充了 geometry 和 geography。

## 小结

- **成效**：规范文档的 v3 类型摘要与实际规范内容保持一致，读者可以准确了解 v3 引入的所有新数据类型。
- **影响范围**：仅文档变更，不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：取决于 1.4.x 分支的规范文档是否已包含 variant 和 geo 类型的定义。如果 1.4.x 已有这些类型定义，则应回迁此摘要更新以保持文档一致性；如果 1.4.x 尚未支持这些类型，则不应回迁。此提交依赖前置提交 1713（引入 geo 类型）。
