# 提交 0547：API: Fix typo in method comment of SortOrder and SortOrderBuilder

## 提交信息

- **序号**：0547 / 4088
- **哈希**：8a16a417492eb6ccb1895b4b6db3536ff2a8b67d
- **短哈希**：8a16a4174
- **日期**：2024-02-27 23:20:08 +0800
- **作者**：dongwang <mingwbd@gmail.com>
- **提交说明**：API: Fix typo in method comment of SortOrder and SortOrderBuilder (#9816)
- **PR/Issue**：#9816

## 总体目的

本提交修复 `api` 模块中 `SortOrder` 与 `SortOrderBuilder` 两个公共接口/类的 Javadoc 注释笔误：所有 `desc` 系列方法的注释被错误地写成了 "ascending"（升序），实际方法语义是 "descending"（降序）。这种文档与代码语义相反的笔误会误导使用者在阅读 IDE 提示或生成的 Javadoc 时对排序方向产生错误判断，需要纠正。

## 如何达成设计目的

设计思路非常直接：纯文档修正，不改动任何方法签名或实现逻辑。提交者逐一定位 `desc*` 方法的 Javadoc，把 "ascending" 一词替换为 "descending"，使注释与方法名（`desc`）和方法体（`SortDirection.DESC` / `NullOrder.NULLS_LAST`）保持一致。

具体涉及 5 处注释（SortOrder.java 1 处、SortOrderBuilder.java 4 处）：

- `SortOrder.Builder.desc(Term, NullOrder)` 的 Javadoc
- `SortOrderBuilder.desc(String name)` 的 Javadoc
- `SortOrderBuilder.desc(String name, NullOrder)` 的 Javadoc
- `SortOrderBuilder.desc(Term term)` 的 Javadoc
- `SortOrderBuilder.desc(Term term, NullOrder nullOrder)` 的 Javadoc

所有修改都是把 "ascending" 替换为 "descending"，其他文字保持不变。

## 修改详情

### `api/src/main/java/org/apache/iceberg/SortOrder.java`

**修改目的**：修复 `SortOrder.Builder` 内 `desc(Term term, NullOrder nullOrder)` 方法的 Javadoc 描述方向错误。

**工作逻辑**：该方法实现为 `return addSortField(term, SortDirection.DESC, nullOrder);`，明确使用 `SortDirection.DESC` 做降序排序。原 Javadoc 却写 "Add an expression term to the sort, ascending with the given null order."，与 `asc(Term, NullOrder)` 方法的注释完全相同（复制粘贴遗留）。本次将 "ascending" 改为 "descending"，使注释与方法语义一致。注意同一文件中 `asc(Term, NullOrder)` 的注释保留 "ascending" 不变。

### `api/src/main/java/org/apache/iceberg/SortOrderBuilder.java`

**修改目的**：修复 `SortOrderBuilder` 接口中 4 个 `desc*` 默认/抽象方法的 Javadoc 描述方向错误。

**工作逻辑**：4 处修改全部是把 "ascending" 替换为 "descending"：

1. `desc(String name)` 方法（默认实现 `desc(Expressions.ref(name), NullOrder.NULLS_LAST)`）：Javadoc 由 "Add a field to the sort by field name, ascending with nulls first." 改为 "descending with nulls first."。
2. `desc(String name, NullOrder nullOrder)` 方法（默认实现 `desc(Expressions.ref(name), nullOrder)`）：Javadoc 由 "...ascending with the given null order." 改为 "descending with the given null order."。
3. `desc(Term term)` 方法（默认实现 `desc(term, NullOrder.NULLS_LAST)`）：Javadoc 由 "Add an expression term to the sort, ascending with nulls first." 改为 "descending with nulls first."。
4. `desc(Term term, NullOrder nullOrder)` 抽象方法：Javadoc 由 "...ascending with the given null order." 改为 "descending with the given null order."。

**备注（未在本提交中处理的问题）**：上述 `desc(String name)` 和 `desc(Term term)` 两个无参 NullOrder 的默认方法实际使用 `NullOrder.NULLS_LAST`，但其 Javadoc 仍写 "with nulls first"。本提交仅修复 ascending/descending 这一词，未触及 "nulls first/nulls last" 的描述。严格来说 "nulls first" 也与实现不一致，但 PR 作者限定只修复标题所述的 "ascending/descending" 笔误，未一并处理 null 顺序描述，属于可改进的遗留点。

## 小结

本提交是纯文档修复，不涉及任何代码逻辑、方法签名或运行时行为变更，对功能零影响，回迁到 1.4.x 风险极低。改进点在于：

- 消除公共 API Javadoc 中方向描述与实际行为相反的误导性表述，提升 IDE 悬浮提示、Javadoc 站点使用体验。
- 5 处修改全部为单词替换，无副作用，可直接 cherry-pick 到任何分支。

**回迁到 1.4.x 的注意事项**：
- 文档修复对任何分支都安全，可直接回迁。
- 回迁时若 1.4.x 中这两个文件的 `desc*` 方法注释仍是 "ascending"，则直接应用即可；若 1.4.x 已有其他改动，需确认上下文一致。
- 顺带可考虑把 "nulls first" → "nulls last" 的描述也一并修正（针对 `desc(String name)` 和 `desc(Term term)` 两个默认方法），但严格来说这超出本提交范围，应作为单独的小修提交。
