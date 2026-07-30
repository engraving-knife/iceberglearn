# 提交 0661：OpenAPI: Fix additionalProperties for SnapshotSummary

## 提交信息
- **序号**：0661 / 4088
- **哈希**：ba1cd36fbbd92e35bf0ad1667daa1c18be723521
- **短哈希**：ba1cd36fb
- **日期**：2024-04-04
- **作者**：Haizhou Zhao
- **提交说明**：OpenAPI: Fix additionalProperties for SnapshotSummary (#9838)
- **PR/Issue**：PR #9838，对应 ISSUE #9837

## 总体目的

本提交修复 Iceberg REST Catalog OpenAPI 规范中 `SnapshotSummary`（在代码中对应 `Summary` 模型）的 `additionalProperties` 定义错误。

在 OpenAPI 规范中，`additionalProperties` 用于描述一个 schema 允许出现的"额外字段"（即未在 `properties` 中显式声明的字段）。对于 `SnapshotSummary` 而言，快照摘要除了 `operation` 字段外，还会携带很多运行时统计信息（如 `added-data-files`、`deleted-data-files`、`total-records` 等），这些字段都是字符串类型的键值对，因此 `additionalProperties` 应当作为 `SnapshotSummary` schema 的一个直接属性，与 `operation` 同级，表示"该对象可以包含任意字符串类型的额外字段"。

然而在修复前，YAML 中 `additionalProperties` 的缩进错误导致它被错误地嵌套在 `operation` 字段定义之下（作为 `operation` 的子属性）。从 OpenAPI 规范角度，这没有意义——`operation` 是一个字符串枚举类型，不可能再有"额外属性"。这会导致 OpenAPI 工具链（代码生成器、校验器等）对 `SnapshotSummary` 模型的理解与实际运行时不一致：客户端无法正确感知快照摘要允许携带的额外统计字段。

此外，Python 生成的模型文件 `rest-catalog-open-api.py` 中 `Summary` 类错误地把 `additionalProperties` 当作一个普通字段（`additionalProperties: Optional[str] = None`）来声明。Pydantic 中 `additionalProperties` 并非标准字段声明方式，正确的做法是让 `BaseModel` 通过类配置来允许额外字段，而不是声明一个名为 `additionalProperties` 的属性。因此本提交同时移除了 Python 模型中这个多余的字段。

## 如何达成设计目的

提交采用了"最小化改动"策略（提交说明中明确提到 "Include minimal change to generated python files"）：

1. **YAML 层面**：通过调整 `additionalProperties` 的缩进，将其从 `operation` 字段的子节点提升为 `SnapshotSummary` schema properties 的同级节点，使其语义从"operation 字段的额外属性"修正为"整个 SnapshotSummary 对象的额外属性"。
2. **Python 模型层面**：移除 `Summary` 类中错误声明的 `additionalProperties` 字段，使其不再误导用户认为这是一个普通属性。

这种修复方式不改变任何业务逻辑或运行时行为，只是让 OpenAPI 规范文档与实际数据结构保持一致，属于纯规范层面的修正。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`
**修改目的**：修正 `SnapshotSummary` 模型中 `additionalProperties` 的缩进层级。
**工作逻辑**：原代码中 `additionalProperties` 缩进较深，与 `operation` 的 `enum` 同级，意味着它被当作 `operation` 字段的子属性。修复后 `additionalProperties` 缩进减少一级，与 `operation` 字段同级（作为 `schema-id` 的兄弟节点也处于同一层级），成为整个 `SnapshotSummary` schema 的属性。这样 OpenAPI 解析器会正确地将 `additionalProperties: type: string` 理解为"该对象允许任意字符串类型的额外字段"，与快照摘要实际携带的统计字段（如 `added-data-files` 等）保持一致。

### `open-api/rest-catalog-open-api.py`
**修改目的**：移除 Python 生成模型 `Summary` 类中错误声明的 `additionalProperties` 字段。
**工作逻辑**：原代码 `additionalProperties: Optional[str] = None` 将其当作一个名为 `additionalProperties` 的普通可选字段，这与 OpenAPI 规范中 `additionalProperties` 作为 schema 元数据的含义不符。移除该字段后，Pydantic 模型不再误声明该字段，避免了客户端误用。

## 小结
- **成效**：成功修正了 OpenAPI 规范中 `SnapshotSummary` 的 `additionalProperties` 定义错误，使其语义与实际数据结构一致。
- **影响范围**：仅影响 OpenAPI 规范文档（`rest-catalog-open-api.yaml`）及生成的 Python 模型（`rest-catalog-open-api.py`）。属于规范层修正，不影响 Java/服务端运行时逻辑。
- **回迁到 1.4.x 的注意事项**：可直接回迁，无依赖性风险。需确认 1.4.x 分支的 OpenAPI 文件结构与 main 一致；若 1.4.x 分支有其他改动同时影响该文件，需注意合并冲突。该修复纯文档性质，回迁安全。
