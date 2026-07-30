# 提交 0462：Docs: Fix listing of catalog implementations

## 提交信息

- **序号**：0462
- **完整哈希**：a2c23a762233582d3779c6e5bc49d9bfbe1329a2
- **短哈希**：a2c23a762
- **日期**：2024-02-05 10:35:38 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Docs: Fix listing of catalog implementations
- **PR**：#9649

## 总体目的

本提交是纯文档修复，针对 `site/docs/catalog.md` 中「开箱即用的 catalog 实现列表」的渲染问题。原列表使用了 Markdown 的破折号无序列表语法（`- REST - ...`），但每一项中第二个破折号会被很多 Markdown 渲染器（包括 GitHub/Jekyll 等 Iceberg 文档站点所使用的渲染管线）误解为嵌套列表项或分隔符，导致渲染结果不是预期的项目符号列表，而是一段错乱的段落或嵌套结构，影响可读性。

具体而言，原文写成 `- REST - a server-side catalog...`，渲染器会把第一个 `-` 当作列表标记，但行内第二个 `-` 在某些解析器下会被当作列表项内的次级标记或弱强调，造成格式不一致。作者将行内分隔符从短横线 `-` 改为冒号 `:`，并将列表标记统一改为星号 `*`，使其成为标准的 Markdown 无序列表，渲染稳定一致。

这种修复虽然只是文档细节，但对于官方文档的专业性与用户阅读体验很重要，避免新用户在第一次接触 Iceberg catalog 时因渲染混乱而误解可选实现。

## 如何达成设计目的

将 `site/docs/catalog.md` 中四条 catalog 实现列表项重写：列表标记由 `-` 改为 `*`，名称与描述之间的分隔符由 `-` 改为 `:`。其余描述文字保持不变，仅调整标点与列表语法。

## 修改详情

### site/docs/catalog.md

**修改目的**：修复 catalog 实现列表的 Markdown 渲染，使其在文档站点上正确显示为无序列表。

**工作逻辑**：
- 原文（4 行）：
  - `- REST - a server-side catalog that's exposed through a REST API`
  - `- Hive Metastore - tracks namespaces and tables using a Hive metastore`
  - `- JDBC - tracks namespaces and tables in a simple JDBC database`
  - `- Nessie - a transactional catalog that tracks namespaces and tables in a database with git-like version control`
- 改后（4 行，使用 `*` 与 `:`）：
  - `* REST: a server-side catalog that's exposed through a REST API`
  - `* Hive Metastore: tracks namespaces and tables using a Hive metastore`
  - `* JDBC: tracks namespaces and tables in a simple JDBC database`
  - `* Nessie: a transactional catalog that tracks namespaces and tables in a database with git-like version control`
- 同时在列表前增加一个空行，使其与前一段「This includes:」之间形成清晰的列表起始分隔，避免某些渲染器把列表并入上一段。
- 紧随其后的「There are more catalog types...」说明文字保持不变。

## 小结

本提交通过最小化的标点与列表语法调整，修复了 catalog 实现列表在文档站点上的渲染问题。改动仅限 1 个文件、9 行（5 增 4 删），不影响任何代码逻辑，但对官方文档的可读性与专业呈现有直接价值。
