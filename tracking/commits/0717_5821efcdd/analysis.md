# 提交 0717：Spec: Clarify missing fields when writing

## 提交信息
- **序号**：0717 / 4088
- **哈希**：5821efcdd521fa4d0f244500d3edb5e1c9e06311
- **短哈希**：5821efcdd
- **日期**：2024-04-26 08:50:30 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Spec: Clarify missing fields when writing (#8672)
- **PR/Issue**：#8672

## 总体目的

本提交澄清（clarify）Iceberg 规范（spec）中关于"写入时缺失字段"（missing fields when writing）的语义，解决社区中关于 `optional` 字段写入行为产生的歧义。

问题的起因是 Jan Finis（JFinis）在 Iceberg Slack 频道上提出的一个语义疑问：当一个字段被标注为 `optional` 时，写入方（writer）到底应该如何处理？存在两种可能的理解：

1. **字段不属于 schema，完全从文件中省略**（field is not part of the schema, and omitted from the file）
2. **字段属于 schema，但值不写入**（field is part of the schema, but the value is not written，即 nullable）

这两种理解在实践中会导致不同的写入行为，进而影响读取方的兼容性。提交作者 Fokko Driesprong 的观点是：写入 Avro 文件时应使用静态 schema（static schema），即所有 optional 或 required 字段都应该出现在 schema 中。这与 Iceberg 一贯遵循的"写严格、读宽容"（write strict, read permissive）原则一致——鼓励写入方写出规范中定义的所有字段，即使某些字段的值全为 null。

这一澄清虽小（仅修改 2 行），但对规范的可解释性和不同实现之间的互操作性有重要意义。它明确了 optional 字段的双向语义：既可以写、也可以省略，从而为写入方提供了灵活性，同时不破坏读取方的兼容性保证。

## 如何达成设计目的

提交者通过两处精细的措辞修改来达成澄清：

1. **扩展"writer requirements"段落的范围说明**：原文只说"adding metadata files"，修改后明确为"adding metadata files (including manifests files and manifest lists)"，显式将 manifest 文件和 manifest list 纳入写入要求的适用范围，避免歧义。

2. **修改 `optional` 要求的写行为描述**：原文为"The field can be written"（字段可以被写入），修改后为"The field can be written or omitted"（字段可以被写入或省略）。这一改动明确允许 optional 字段在写入时被省略，从而回答了 Jan 提出的问题——optional 字段既可以存在于 schema 中但不写值，也可以完全从文件中省略。

这两处修改配合规范的"read permissive"原则（v1 metadata 文件允许在 v2 表中使用，因此读取方必须比写入方更宽容），形成了一个完整的语义闭环：写入方对 optional 字段有写或不写的自由，读取方则必须能处理这两种情况。

## 修改详情

### `format/spec.md`
**修改目的**：澄清 Iceberg 规范中关于写入时 optional 字段的行为，以及写入要求的适用范围。

**工作逻辑**：在 `#### Writer requirements` 小节中做了两处措辞修改：

1. 第一处（第 129 行）：
   - 修改前：`Some tables in this spec have columns that specify requirements for v1 and v2 tables. These requirements are intended for writers when adding metadata files to a table with the given version.`
   - 修改后：`Some tables in this spec have columns that specify requirements for v1 and v2 tables. These requirements are intended for writers when adding metadata files (including manifests files and manifest lists) to a table with the given version.`
   - **意义**：原文只说"metadata files"，虽然 manifest 文件和 manifest list 在技术上属于 metadata 文件，但显式列出可以避免实现者遗漏。提交说明中也提到"Add manifest-list explicitly"，表明这是讨论中提出的补充要求。

2. 第二处（第 132 行，Writer requirements 表格中 `optional` 行）：
   - 修改前：`| _optional_  | The field can be written |`
   - 修改后：`| _optional_  | The field can be written or omitted |`
   - **意义**：这是本次澄清的核心。原文"The field can be written"只说了"可以写"，但没有说"可以不写"，导致语义模糊。修改后明确 optional 字段既可以写入（即使值为 null），也可以完全省略。这与 `(blank)`（必须省略）和 `required`（必须写入）形成清晰的三态：
     - `(blank)` → 必须省略（should be omitted）
     - `optional` → 可以写入或省略（can be written or omitted）
     - `required` → 必须写入（must be written）

## 小结

- **成效**：成功达成目的。修改后规范对 optional 字段的写入行为有了明确的、无歧义的描述，消除了"可以写"是否意味着"必须写"的混淆。
- **影响范围**：仅影响 Iceberg 规范文档 `format/spec.md`，不修改任何代码。但这一规范澄清对所有 Iceberg 实现（Java、Python、Go、Rust 等）的写入方都有指导意义，特别是实现 manifest 文件和 manifest list 写入的开发者。
- **回迁到 1.4.x 的注意事项**：规范文档的澄清是向后兼容的，不改变任何运行时行为，理论上可以安全回迁到 1.4.x 分支。不过需要注意：1.4.x 分支的 `format/spec.md` 可能与 main 分支有差异，回迁时需要确认上下文一致。由于这是纯文档澄清，不涉及代码变更，优先级较低，可按需回迁。
