# 提交 0568：Docs: Add DDL docs for Views (#9878)

## 提交信息

- **序号**：0568 / 4088
- **哈希**：d90ac0019b89b03cf2ba34c3896490af22a5575f
- **短哈希**：d90ac0019
- **日期**：2024-03-07 14:04:38 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Docs: Add DDL docs for Views (#9878)
- **PR/Issue**：#9878

## 总体目的

本提交为 Iceberg 的 Spark DDL 文档（`docs/docs/spark-ddl.md`）补充"视图（Views）"相关章节。在此之前，`spark-ddl.md` 已经完整覆盖了表的 DDL 操作（`CREATE TABLE`、`DROP TABLE`、`ALTER TABLE`、分区字段管理、分支与标签等），但完全没有涉及视图的 DDL。随着 Iceberg 1.4 引入视图能力（View spec），Spark 3.4+ 已经支持通过 Iceberg catalog 创建和管理视图，文档却缺位，用户无法从官方文档了解如何在 Spark 中使用 Iceberg 视图。

本提交的目标是在 `spark-ddl.md` 末尾追加一个新章节 `### Iceberg views in Spark`，系统介绍如何在 Spark 中使用标准 Spark SQL DDL 语句（`CREATE VIEW`、`ALTER VIEW`、`DROP VIEW`、`SHOW VIEWS`、`SHOW TBLPROPERTIES`、`SHOW CREATE TABLE`、`DESCRIBE`）来操作 Iceberg 视图，并明确这些语句遵循 Spark 官方 SQL 语法、最低要求 Spark 3.4。

## 如何达成设计目的

整体设计思路是"按操作类型分小节，每节给出最小可运行 SQL 示例"：

1. **章节定位**：新章节以 `### Iceberg views in Spark` 作为标题，追加在文档末尾（即 `#### ALTER TABLE ... DROP TAG` 之后）。这与文档既有的"按 DDL 操作类别组织"的结构一致——前文已用 `## CREATE TABLE`、`## ALTER TABLE`、`## ALTER TABLE SQL extensions` 等二级/三级标题分类，视图作为新的 DDL 主题以三级标题出现，避免破坏既有目录层级。

2. **强调通用性**：开篇即指出 Iceberg 视图是跨查询引擎的"通用表示"，并链接到 `view-spec.md`（视图规范文档），让读者理解视图的设计目标不是 Spark 专属，而是可在多引擎间互通。

3. **明确版本要求**：注明需要 Spark 3.4 及以上，更早版本不支持。这是一个关键约束，避免用户在旧版 Spark 上尝试而困惑。

4. **引用官方语法**：用 `!!! note` 提示块列出 Spark 官方 6 个 DDL 语句的语法文档链接（CREATE VIEW、ALTER VIEW、DROP VIEW、SHOW VIEWS、SHOW TBLPROPERTIES、SHOW CREATE TABLE）。这样文档不重复 Spark 语法细节，只关注 Iceberg 视角下的用法示例，避免与 Spark 文档重复维护。

5. **按操作组织小节**：用 `####` 四级标题把视图操作分成 8 个子主题，每个子主题给出 1-3 个 SQL 示例：
   - Creating a view（创建视图：基础、`IF NOT EXISTS`、带列注释）
   - Creating a view with properties（带 `TBLPROPERTIES` 创建 + `SHOW TBLPROPERTIES` 查看）
   - Dropping a view（`DROP VIEW`、`DROP VIEW IF EXISTS`）
   - Replacing a view（`CREATE OR REPLACE`，可同时改 schema、属性、SQL）
   - Setting and removing view properties（`ALTER VIEW ... SET/UNSET TBLPROPERTIES`）
   - Showing available views（`SHOW VIEWS`、`SHOW VIEWS IN <catalog>`、`SHOW VIEWS IN <namespace>`、`SHOW VIEWS IN <catalog>.<namespace>`）
   - Showing the CREATE statement of a view（`SHOW CREATE TABLE <viewName>`）
   - Displaying view details（`DESCRIBE [EXTENDED] <viewName>`）

6. **覆盖常见用法变体**：对每个操作给出基础形式与带保护子句的形式（`IF NOT EXISTS`、`IF EXISTS`），让用户能直接复制示例上手。

## 修改详情

### `docs/docs/spark-ddl.md`

**修改目的**：在 Spark DDL 文档末尾追加 Iceberg 视图章节，共 116 行新增。

**工作逻辑**：

- **章节引言**：`### Iceberg views in Spark` 下首段说明 Iceberg 视图是跨引擎通用表示，引用 `../../view-spec.md`；明确支持 Spark 3.4+。

- **`!!! note` 提示块**：列出 Spark 官方 6 个 DDL 语句语法链接。这是 mkdocs Material 主题的 admonition 语法（`!!! note` 后缩进内容），让提示块在渲染时突出显示。

- **Creating a view**：3 个示例——最简 `CREATE VIEW`、`CREATE VIEW IF NOT EXISTS`、带列注释与 `COMMENT` 的视图。第三个示例展示了视图列名与源表列名不同（`ID` vs `id`）的情况，体现视图列别名能力。

- **Creating a view with properties**：`TBLPROPERTIES ('key1' = 'val1', 'key2' = 'val2')` 与 `SHOW TBLPROPERTIES <viewName>`。属性以键值对形式设置，与表属性语法一致。

- **Dropping a view**：`DROP VIEW` 与 `DROP VIEW IF EXISTS`，后者避免视图不存在时报错。

- **Replacing a view**：`CREATE OR REPLACE` 同时更新视图的列定义（带新注释）、属性、SQL 语句。这是视图演进的常用模式。

- **Setting and removing view properties**：`ALTER VIEW ... SET TBLPROPERTIES` 与 `ALTER VIEW ... UNSET TBLPROPERTIES`，对应已有视图属性的增量修改。

- **Showing available views**：`SHOW VIEWS` 列出当前 namespace（由 `USE <namespace>` 设置）下的视图；3 个变体支持按 catalog、namespace、`catalog.namespace` 限定范围。

- **Showing the CREATE statement of a view**：`SHOW CREATE TABLE <viewName>`。注意这里用的是 `SHOW CREATE TABLE` 而非 `SHOW CREATE VIEW`——这是 Spark 的语法复用，`SHOW CREATE TABLE` 对视图同样有效，文档没有掩饰这一Spark 行为，直接呈现。

- **Displaying view details**：`DESCRIBE [EXTENDED] <viewName>`。`DESCRIBE` 同样是表/视图通用语法，`EXTENDED` 关键字展示更详细的元信息（包括属性、存储信息等）。

## 小结

本提交为 Iceberg 的 Spark DDL 文档补齐视图章节，使文档从"仅覆盖表 DDL"扩展到"覆盖表与视图 DDL"。新增内容严格遵循 Spark 官方 SQL 语法，通过引用官方文档避免重复维护，通过 8 个分类小节与示例覆盖视图的创建、删除、替换、属性管理、列举、元信息查看等常用操作。

回迁到 1.4.x 的注意事项：
1. 本提交仅修改 Markdown 文档，无任何代码变更，回迁零风险。
2. 文档中引用的 `../../view-spec.md`（视图规范）需确认 1.4.x 分支中该文件已存在；若视图规范文档尚未回迁，链接会失效。
3. 文档要求 Spark 3.4+——若 1.4.x 的 Spark 集成版本更低（不太可能，因 1.4 已支持视图），需相应调整版本说明。
4. 文档章节用 `###` 三级标题而非 `##` 二级，与既有 `## CREATE TABLE` 等略有层级不一致，回迁时可保持原样（与上游一致）或视本地文档风格调整。
