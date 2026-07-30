# 提交 2594：[SPEC] Add implementation note about schema evolution (#13936)

## 提交信息

- **序号**：2594 / 4088
- **哈希**：c64c89fbb5390c7c796e022b939118539c7195e1
- **短哈希**：c64c89fbb
- **日期**：2025-09-04 09:31:18 -0700
- **作者**：emkornfield
- **提交说明**：[SPEC] Add implementation note about schema evolution (#13936)
- **PR/Issue**：#13936

## 总体目的

本次提交向 Iceberg 规范文档（format/spec.md）中添加了一段关于"Schema 演进与使用旧 schema 写入"的实现说明，指导写入器应如何正确处理 schema 变更。

Iceberg 支持 schema 演进（如添加列、重命名列、修改列类型等），表的元数据中会记录每个字段的各种默认值（如 `initial-default` 和 `write-default`）。然而，如果写入器使用过时的 schema（非最新 schema）写入数据，或者不写入所有列，可能会导致数据语义不一致。这段说明旨在明确这些风险，指导实现者正确使用最新 schema 进行写入。

需要注意的是，此提交后来在提交 2599（#14002）中被 revert，说明该说明的内容可能存在争议或需要进一步修订。

## 如何达成设计目的

在 `format/spec.md` 文档中，在"Appendix F"（关于实现注意事项）区域添加一个新的小节"Schema evolution and writing with old schemas"，列出使用旧 schema 写入可能导致的三类不一致问题，并说明列投影规则的设计保证了基本的可读性。

## 修改详情

### `format/spec.md` (+10/-0 lines)

**修改目的**：补充规范文档中关于 schema 演进的实现指导。

**工作逻辑**：在已有的实现说明段落之后，新增 `### Schema evolution and writing with old schemas` 小节，内容包括：

1. **核心要求**：写入器必须按照表元数据中 schema 指定的类型写出所有字段，且应使用最新 schema 进行写入。不写出所有列或不使用最新 schema 可能改变写入数据的语义。

2. **三类潜在不一致**：
   - **全 null 列问题**：对于全为 null 的列，如果不写出该列，读取时会应用 `initial-default` 值而非 null。
   - **write-default 变更问题**：如果 `write-default` 已被修改，使用过时 schema 会导致填入错误的默认值。
   - **部分行更新问题**：如果写入是部分行更新的结果（如 `update table set col_y = 'xyz'`），使用过时 schema 会静默丢弃值。

3. **可读性保证**：列投影规则的设计确保即使写入器使用了过时 schema，表仍然保持可读。

## 总结

本次提交是对 Iceberg 规范文档的补充，明确了写入器在 schema 演进场景下的正确行为和潜在风险。这类规范说明有助于各引擎实现者遵循一致的行为。不过该说明后来被 revert（见提交 2599），表明其措辞或内容可能需要进一步讨论完善。
