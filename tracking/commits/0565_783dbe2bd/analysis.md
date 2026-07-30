# 提交 0565：文档——修复内部文件链接

## 提交信息

- **序号**：0565 / 4088
- **哈希**：783dbe2bd7546b729e79546adbe846c5e383651e
- **短哈希**：783dbe2bd
- **日期**：2024-03-07（AuthorDate 2024-03-07 16:22:32 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix links to internal files (#9819)
- **PR/Issue**：#9819。本提交是 PR #9591（提交 `ed288987c`，"Convert Hugo versioned docs to mkdocs format"，2024-02-01，Hugo→mkdocs 迁移）的后续修复——#9591 把文档从 Hugo 迁移到 mkdocs 并拆分/移动了文件，但未同步更新所有内部链接，导致迁移后大量链接指向错误路径或已不存在的锚点；本提交修复 `docs/docs/` 下 5 个文档文件中共 19 处内部链接。与 0563（PR #9861，修复 `format/` 下规范文件链接）属同一批迁移收尾工作。

## 总体目的

本提交要修复 Iceberg 文档站点从 Hugo 迁移到 mkdocs 后遗留的**内部链接失效**问题，使文档中的跨页链接与页内锚点链接在 mkdocs 渲染后能正确跳转到目标页面与章节。

**背景动机**：

- PR #9591 完成了 Hugo→mkdocs 迁移，其中一项重要变更是把原先的**单体 Flink 页面**拆分成多个独立文件：DDL 相关内容分到 `flink-ddl.md`、查询相关到 `flink-queries.md`、写入相关到 `flink-writes.md`、操作相关到 `flink-actions.md`，并在 `mkdocs.yml` 的 nav 中分组。同时所有文档从 `docs/` 移到了 `docs/docs/`。
- 但迁移时**未更新这些拆分/移动所影响的内部链接**，导致：
  1. **页内锚点链接失效**：`flink.md` 的功能支持表格原本用 `#create-table` 这样的页内锚点指向同页章节；拆分后这些章节搬到了 `flink-ddl.md` 等其他文件，锚点已不在本页，链接全部失效。
  2. **跨页路径错误**：部分链接仍按 Hugo 的目录结构书写（如 `../tables/maintenance#...`、`../../api#...`），与 mkdocs 的扁平目录结构不匹配。
  3. **缺 `.md` 扩展名**：Hugo 链接不带 `.md`（如 `metrics-reporting`、`spark-queries#snapshots`），mkdocs 需要带 `.md` 的相对文件链接才能正确解析。
  4. **锚点名变更未同步**：拆分时部分章节标题/锚点名调整（如 `querying-with-sql` → `reading-with-sql`、`write-properties` → `write-options`、`creating-catalogs-and-using-catalogs` → `create-catalog`），引用旧锚点名的链接需同步更新。
- 用户访问文档时这些失效链接会产生 404 或跳转到错误位置，影响文档可用性，需在 1.4.x 发布前修复。

## 如何达成设计目的

设计思路是逐文件、逐链接地把 Hugo 风格的链接改写为 mkdocs 风格的相对链接，遵循 mkdocs 的链接解析规则：

1. **mkdocs 默认 `use_directory_urls: true`**：每个 `foo.md` 页面被渲染为 `/foo/`（目录式 URL）。因此从页面 A 链接到同目录下的页面 B 时，相对链接需写成 `../B.md`——因为 A 的 URL 是 `/A/`（一个目录），要先 `..` 回到站点根，再进入 `B`。这解释了所有跨页链接前的 `../` 前缀。
2. **跨页锚点**格式为 `../<目标文件>.md#<锚点>`，mkdocs 构建时将其解析为 `/<目标文件>/#<锚点>`。
3. **拆分后锚点归属**：根据 #9591 的拆分，把原先指向 `flink.md` 页内锚点的链接改写为指向拆分后的目标文件（DDL→`flink-ddl.md`、查询→`flink-queries.md`、写入→`flink-writes.md`、操作→`flink-actions.md`），并同步更新变更过的锚点名。
4. **目录结构调整**：Hugo 时代的 `tables/` 子目录在 mkdocs 中已扁平化（`maintenance.md` 直接在根），`../../api` 这样的深层路径也随目录扁平化调整为 `../api.md`。

## 修改详情

### `docs/docs/configuration.md`

**修改目的**：修复指向 metrics-reporting 页面的跨页链接。

**工作逻辑**：把

```markdown
See the [Metrics reporting](metrics-reporting) section for additional details
```

改为

```markdown
See the [Metrics reporting](../metrics-reporting.md) section for additional details
```

`metrics-reporting` 是 Hugo 风格的同页/同节引用（无扩展名、无路径前缀）。`metrics-reporting.md` 与 `configuration.md` 同处 `docs/docs/`（mkdocs 站点根）。由于 mkdocs 默认 `use_directory_urls: true`，`configuration.md` 渲染为 `/configuration/`，需 `../metrics-reporting.md` 才能回到根再进入 `metrics-reporting`。

### `docs/docs/flink-writes.md`

**修改目的**：修复两处指向 maintenance 页面锚点的链接（过期快照、删除孤儿文件）。

**工作逻辑**：

```markdown
[expiring snapshots](../tables/maintenance#expire-snapshots)
[deleting orphan files](../tables/maintenance#delete-orphan-files)
```

改为

```markdown
[expiring snapshots](../maintenance.md#expire-snapshots)
[deleting orphan files](../maintenance.md#delete-orphan-files)
```

两处改动相同：移除 Hugo 时代的 `tables/` 子目录前缀（mkdocs 中 `maintenance.md` 已扁平化到站点根），并补上 `.md` 扩展名。`../` 前缀保留——`flink-writes.md` 渲染为 `/flink-writes/`，需上溯一级到根再进入 `maintenance`。锚点名 `#expire-snapshots`、`#delete-orphan-files` 未变。

### `docs/docs/flink.md`

**修改目的**：修复功能支持表格中 14 处页内锚点链接——这些锚点在 #9591 拆分后已分散到 `flink-ddl.md`/`flink-queries.md`/`flink-writes.md`/`flink-actions.md` 四个文件。

**工作逻辑**：把功能支持表格中每行的链接从 Hugo 时代的页内锚点 `#<anchor>` 改写为跨页链接 `../<目标文件>.md#<anchor>`，并同步 3 处锚点名变更。逐行对照如下：

| 功能 | 旧链接（Hugo 页内） | 新链接（mkdocs 跨页） | 说明 |
|------|---------------------|----------------------|------|
| SQL create catalog | `#creating-catalogs-and-using-catalogs` | `../flink-ddl.md#create-catalog` | 锚点名缩短 |
| SQL create database | `#create-database` | `../flink-ddl.md#create-database` | 仅加文件前缀 |
| SQL create table | `#create-table` | `../flink-ddl.md#create-table` | 仅加文件前缀 |
| SQL create table like | `#create-table-like` | `../flink-ddl.md#create-table-like` | 仅加文件前缀 |
| SQL alter table | `#alter-table` | `../flink-ddl.md#alter-table` | 仅加文件前缀 |
| SQL drop_table | `#drop-table` | `../flink-ddl.md#drop-table` | 仅加文件前缀 |
| SQL select | `#querying-with-sql` | `../flink-queries.md#reading-with-sql` | 锚点名变更 |
| SQL insert into | `#insert-into` | `../flink-writes.md#insert-into` | 仅加文件前缀 |
| SQL insert overwrite | `#insert-overwrite` | `../flink-writes.md#insert-overwrite` | 仅加文件前缀 |
| DataStream read | `#reading-with-datastream` | `../flink-queries.md#reading-with-datastream` | 归到 queries 文件 |
| DataStream append | `#appending-data` | `../flink-writes.md#appending-data` | 仅加文件前缀 |
| DataStream overwrite | `#overwrite-data` | `../flink-writes.md#overwrite-data` | 仅加文件前缀 |
| Metadata tables | `#inspecting-tables` | `../flink-queries.md#inspecting-tables` | 归到 queries 文件 |
| Rewrite files action | `#rewrite-files-action` | `../flink-actions.md#rewrite-files-action` | 归到 actions 文件 |

锚点归属遵循 #9591 的拆分逻辑：DDL 类（catalog/database/table/alter/drop）→ `flink-ddl.md`；查询类（select/DataStream read/metadata tables）→ `flink-queries.md`；写入类（insert/append/overwrite）→ `flink-writes.md`；操作类（rewrite files）→ `flink-actions.md`。3 处锚点名变更是因为拆分时对应章节标题调整导致 mkdocs 自动生成的锚点 slug 随之改变。

### `docs/docs/spark-configuration.md`

**修改目的**：修复一处同时含两个链接的表格单元格——指向 API 文档与 spark-queries 页面的锚点。

**工作逻辑**：把

```markdown
Can be obtained via [Table API](../../api#table-metadata) or [Snapshots table](../spark-queries#snapshots).
```

改为

```markdown
Can be obtained via [Table API](../api.md#table-metadata) or [Snapshots table](../spark-queries.md#snapshots).
```

两处改动：
- `../../api#table-metadata` → `../api.md#table-metadata`：Hugo 目录结构更深（`../../` 上溯两级），mkdocs 扁平化后只需 `../` 上溯一级；补 `.md`。
- `../spark-queries#snapshots` → `../spark-queries.md#snapshots`：补 `.md` 扩展名，`../` 前缀已正确（spark-configuration 渲染为 `/spark-configuration/`，上溯到根再进 spark-queries）。

### `docs/docs/spark-writes.md`

**修改目的**：修复一处页内锚点链接——目标锚点在 mkdocs 中位于另一个文件。

**工作逻辑**：把

```markdown
using the Spark [write fanout](#write-properties) property
```

改为

```markdown
using the Spark [write fanout](../spark-configuration.md#write-options) property
```

`#write-properties` 是 Hugo 时代的页内锚点，但在 mkdocs 迁移后，write fanout 相关的写入属性表位于 `spark-configuration.md` 中且锚点名变为 `write-options`（章节标题调整）。因此链接从页内锚点改为跨页链接 `../spark-configuration.md#write-options`，同时更新锚点名。

## 小结

本提交修复了 Hugo→mkdocs 迁移（#9591）后遗留的 5 个文档文件、19 处内部链接失效问题。改动纯粹是文档链接路径与锚点的修正，不涉及任何代码或文档内容语义变更。修复后，Flink 功能支持表格的 14 项跳转、Spark/Flink 写入与配置页的跨页引用、以及 metrics-reporting 与 maintenance 页的引用均能在 mkdocs 站点中正确跳转。

**影响范围**：仅影响文档站点的链接可用性，不影响代码与运行时行为。

**回迁到 1.4.x 的注意事项**：

- 本提交是 1.4.x 文档维护期的链接修复，回迁前提是 1.4.x 已合入 #9591 的 mkdocs 迁移（即文档已在 `docs/docs/` 下、Flink 页面已拆分）。若 1.4.x 仍在 Hugo，则不应回迁（这些 `../xxx.md` 链接在 Hugo 下反而会失效）。
- 锚点名的正确性依赖于目标文件中实际章节标题——若后续调整了 `flink-ddl.md`/`flink-queries.md`/`flink-writes.md`/`flink-actions.md`/`spark-configuration.md` 的章节标题，需同步复核这些锚点链接。
- 与 0563（PR #9861）配套：0563 修 `format/` 下规范文件链接，本提交修 `docs/docs/` 下主文档链接，二者共同完成迁移收尾。
