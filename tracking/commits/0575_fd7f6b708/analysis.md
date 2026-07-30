# 提交 0575：增强 Flink 文档页面

## 提交信息

- **序号**：0575 / 4088
- **哈希**：fd7f6b708c12ce8ca7cb72e89fa65c04a686e73b
- **短哈希**：fd7f6b708
- **日期**：2024-03-11 18:32:27 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Enhance Flink pages (#9919)
- **PR/Issue**：#9919

提交正文：
1. Fix internal links
2. Remove period in title
3. Fix numbered list with code blocks
4. Add identifier fields as an approach for upsert mode

## 总体目的

对 Iceberg Flink 文档做一轮系统性的可读性与正确性修复，涵盖四类问题：站内相对链接路径错误、标题多余句号、有序列表与代码块缩进不当导致渲染断裂，以及 upsert 模式缺少「identifier fields」这一可选路径说明。整体目标是让 Flink 系列文档在 mkdocs 渲染后链接可达、排版连续、内容更完整，提升用户阅读体验与上手效率。

背景动机：
- Iceberg 文档使用 mkdocs 渲染。mkdocs 对 Markdown 中相对链接的解析基于「渲染后 URL」，而非源文件目录。`docs/docs/flink-*.md` 渲染后的 URL 为 `/flink-xxx/`，如果其中相对链接写为 `maintenance.md`、`flink.md`、`branching.md` 等，会被解析为 `/flink-xxx/maintenance.md` 这种错误路径，导致点进去 404。需要写 `../maintenance.md` 让其解析到 `/maintenance/`。
- 文档标题（`## ...`、`### ...`）在多处带有英文句号结尾，不符合主流 markdown 风格指南（如 Microsoft、Google 写作风格），且渲染后侧边栏标题也带句号显得突兀。
- `flink-writes.md` 中有一段「1. ... 2. ...」的有序列表，每项之间嵌入 ` ```sql ` 代码块，但代码块没有按 4 空格缩进进列表项，渲染器会把代码块踢出列表、打乱编号连续性。
- upsert 模式的文档此前只提「主键（PRIMARY KEY）」一种定义等价字段的方式，但 Iceberg spec 还支持 identifier fields 作为 upsert 的等价字段来源，文档未提及这一替代方案，导致用户不知道还有这条路径。

## 如何达成设计目的

提交采用「逐文件逐链接扫描」的纯文档修复方式，不涉及代码改动，四类问题分别用以下手段解决：

1. **修复相对链接**：把 `flink-*.md` 中所有形如 `xxx.md`、`flink.md`、`branching.md`、`configuration.md`、`flink-configuration.md`、`maintenance.md` 的站内链接统一改为 `../xxx.md`，使其在 mkdocs 渲染后正确解析到 `/xxx/`。
2. **删除标题末尾句号**：`## Rewrite files action.` → `## Rewrite files action`；`### Appending data.` → `### Appending data`。
3. **修复有序列表与代码块缩进**：把代码块整体缩进 4 空格、SQL 内容相应缩进，使其归入对应列表项；同时调整列表项正文文案。
4. **补充 identifier fields 说明**：在 upsert 模式说明中追加「[primary key](../flink-ddl.md/#primary-key) or [identifier fields](../../spec.md#identifier-field-ids)」并列，并在「Appends / Branch Writes」段落不动原文案只调链接。
5. **配套链接修复**：把同一文件里其它的站内链接也按 `../` 规则统一改正（如 `branching.md` → `../branching.md`、`flink-configuration.md#write-options` → `../flink-configuration.md#write-options`、`configuration.md` → `../configuration.md`、`flink-configuration.md#read-options` → `../flink-configuration.md#read-options`）。

## 修改详情

### `docs/docs/flink-actions.md`

**修改目的**：修复标题句号与跨文档链接。

**工作逻辑**：
- 把标题 `## Rewrite files action.` 改为 `## Rewrite files action`（去掉句号）。
- 把指向 Spark rewriteDataFiles 的链接 `[rewriteDataFiles](maintenance.md#compact-data-files)` 改为 `[rewriteDataFiles](../maintenance.md#compact-data-files)`，使其在 mkdocs 渲染后能正确跳到 maintenance 页面的 compact-data-files 锚点。

### `docs/docs/flink-connector.md`

**修改目的**：修复 3 处指向 `flink.md` 的相对链接。

**工作逻辑**：
- `[custom catalog](flink.md#adding-catalogs)` → `[custom catalog](../flink.md#adding-catalogs)`（catalog-impl 配置说明处）。
- `[quick start documentation](flink.md)` → `[quick start documentation](../flink.md)`（Hive catalog 示例前）。
- `[Flink documentation](flink.md)` → `[Flink documentation](../flink.md)`（文末）。

3 处全部从 `flink.md` 改为 `../flink.md`，对应 mkdocs 渲染后的 `/flink/` 路径。

### `docs/docs/flink-ddl.md`

**修改目的**：修复指向 configuration 文档的链接。

**工作逻辑**：把 `[table configuration](configuration.md)` 改为 `[table configuration](../configuration.md)`，让 `WITH ('key'='value', ...)` 段落的链接能正确跳到 `/configuration/`。

### `docs/docs/flink-queries.md`

**修改目的**：修复指向 Flink Configuration 文档的链接。

**工作逻辑**：把 `[Flink Configuration](flink-configuration.md#read-options)` 改为 `[Flink Configuration](../flink-configuration.md#read-options)`，让「Reading branches and tags with SQL」段落的链接能正确跳到 `/flink-configuration/#read-options`。

### `docs/docs/flink-writes.md`

**修改目的**：综合修复——列表与代码块缩进、identifier fields 说明、3 处链接、标题句号。

**工作逻辑**：
- **标题**：`### Appending data.` → `### Appending data`。
- **UPSERT 模式第 1、2 步的代码块缩进**：原 1. 后紧跟无缩进的 ` ```sql ` 代码块（被 markdown 视为列表外内容），改为缩进 4 空格（`    ```sql`）使代码块归属于第 1 步列表项；同样 2. 后的 ` ```sql ` 也缩进 4 空格归属于第 2 步列表项。两段 SQL 内容相应缩进。
- **identifier fields 说明**：在第 2 步正文「you still need to use v2 table format and specify the primary key when creating the table」中，把 `the primary key` 改为 `the [primary key](../flink-ddl.md/#primary-key) or [identifier fields](../../spec.md#identifier-field-ids)`，把单一路径扩成两条并列路径，并分别加上指向 `flink-ddl.md#primary-key` 与 `spec.md#identifier-field-ids` 的链接。注意 `spec.md` 位于 `docs/docs/spec.md`，从 `docs/docs/flink-writes.md` 出发的相对路径是 `../../spec.md`——这里多一层 `../` 是因为 `flink-writes.md` 在 `docs/docs/` 目录下，按 mkdocs 渲染后 URL `/flink-writes/` 计算，要往上两层（先到 `/`，再到 `spec`，spec.md 渲染为 `/spec/`），所以 `../../spec.md` 是合理的。
- **Branch Writes 链接**：`[branches](branching.md)` → `[branches](../branching.md)`。
- **文末 write-options 链接**：`[write-options](flink-configuration.md#write-options)` → `[write-options](../flink-configuration.md#write-options)`。

### `docs/docs/flink.md`

**修改目的**：修复 Branch Writes 段落中的 branching 链接。

**工作逻辑**：`[branches](branching.md)` → `[branches](../branching.md)`，与 `flink-writes.md` 同段落的修复一致。

## 小结

- **成效**：本轮修复全部围绕「文档可用性」展开，没有功能性代码改动。修好后 Flink 系列页面在 mkdocs 渲染下：站内跳转链接全部可达、标题不带冗余句号、有序列表与代码块嵌套渲染正确、upsert 模式补全 identifier fields 这一替代路径说明。整体提升了 Flink 文档的浏览体验与信息完整性。
- **影响范围**：纯文档改动，不影响运行时行为、API、构建产物（除站点 HTML 外）。所有变更集中在 `docs/docs/flink-*.md` 与 `docs/docs/flink.md`。
- **回迁到 1.4.x 注意事项**：
  1. 文档改动零风险，回迁非常安全；但回迁前应确认 1.4.x 分支上对应文档文件结构（`docs/docs/flink-*.md` 是否存在、是否已分裂为子目录）与本提交基于的版本一致。如果 1.4.x 上 Flink 文档已经移到 `docs/docs/flink/` 子目录（一些版本会做这种重组），那么 `../` 的层数需要重新计算——例如从 `docs/docs/flink/writes.md` 跳到 `docs/docs/spec.md` 应该用 `../spec.md`，回迁时需逐链接重新核对相对路径。
  2. identifier fields 的链接指向 `../../spec.md#identifier-field-ids`，回迁时需确认 1.4.x 上 `spec.md` 中 `Identifier Field IDs` 这一节标题锚点仍为 `identifier-field-ids`（mkdocs 默认把标题转成 kebab-case 锚点），否则链接需要调整。
  3. 此 PR 不带代码改动，不需要编译验证；回迁后建议本地 `mkdocs serve` 跑一次，肉眼检查链接与列表渲染。
