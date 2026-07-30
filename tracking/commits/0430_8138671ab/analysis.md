# 提交 0430：Spark 3.4: Add support for describing/showing views (#9595)

## 提交信息

- **序号**：0430
- **哈希**：8138671ab1e5998b70c4b26806380c5b39740d63
- **短哈希**：8138671ab
- **日期**：2024 年 1 月 31 日（周三）12:52:37 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Add support for describing/showing views (#9595)
- **PR/Issue**：#9595

## 总体目的

本提交为 Iceberg 在 Spark 3.4 引擎中引入了对视图（View）的"描述"与"列举/展示"能力，使 Iceberg 视图在功能完整度上对齐 Spark 原生视图。具体新增了对以下四类 SQL 命令的支持：

1. `DESCRIBE [EXTENDED] view` —— 查看视图的列 schema，`EXTENDED` 模式下额外展示视图的元信息（注释、所属 catalog 与 namespace、查询输出列、属性、创建引擎版本等）。
2. `SHOW TBLPROPERTIES view [(key)]` —— 查看视图的自定义属性，可按 key 精确查询。
3. `SHOW VIEWS [IN namespace] [LIKE pattern]` —— 列出某个命名空间下的视图，支持模式匹配，并能同时列出全局临时视图与本地临时视图。
4. `SHOW CREATE TABLE view` —— 反向重建视图的 `CREATE VIEW` DDL 语句，便于导出与迁移。

在本次提交之前，Iceberg 的 Spark 3.4 集成已支持创建视图（`CREATE VIEW`）和删除视图（`DROP VIEW`），但缺少对这些"只读"型查看命令的支持，用户无法用标准 SQL 检视已创建的 Iceberg 视图。此外，`SparkCatalog.listViews()` 此前直接抛出 `UnsupportedOperationException`，从根上阻断了 `SHOW VIEWS` 通路。本提交补齐了这一缺口，使 Iceberg 视图在 Spark 3.4 中成为与表（Table）地位相当的一等公民，提升了用户体验与可观测性，也使得依赖这些命令的上层工具（如 BI、元数据管理工具）能正常工作。

设计上严格遵循 Spark Catalyst 的分层架构（分析 -> 逻辑计划 -> 物理策略 -> 执行节点），并将 Iceberg 特有逻辑以可复用的方式注入到各层，与 Iceberg 既有的视图扩展（CreateIcebergView / DropIcebergView）保持风格一致。

## 如何达成设计目的

整体实现遵循 Spark Catalyst 的扩展模式，分四层落地：

第一层（分析）：在 `RewriteViewCommands` 分析规则中，把 Spark 标准的 `ShowViews` 逻辑计划改写为 Iceberg 专用的 `ShowIcebergViews`（仅当目标 catalog 是 Iceberg 视图 catalog 时触发）。对于 `DESCRIBE`、`SHOW TBLPROPERTIES`、`SHOW CREATE TABLE`，复用 Spark 已有的逻辑计划节点，仅在物理策略层做视图特化处理。

第二层（逻辑计划）：新增 `ShowIcebergViews` 逻辑节点，承载列举视图的语义；其余命令复用 Spark 内置的 `DescribeRelation`、`ShowTableProperties`、`ShowCreateTable` 节点。

第三层（物理策略）：在 `ExtendedDataSourceV2Strategy` 中新增四条匹配规则，把上述逻辑节点映射到对应的 Iceberg 视图执行节点。

第四层（执行节点）：新增四个 `V2CommandExec` 子类分别实现各命令的数据组装逻辑。

同时修改 `SparkCatalog.listViews()`，由"抛异常"改为"委托给 asViewCatalog"，打通列举视图的底层数据来源。

## 修改详情

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/analysis/RewriteViewCommands.scala

**修改目的**：在分析阶段将 Spark 标准的 `ShowViews` 命令改写为 Iceberg 专用的 `ShowIcebergViews`，使列举视图走 Iceberg 路径。

**工作逻辑**：在已有的 `CreateView`/`DropView` 改写规则之后，新增两个 case 分支处理 `ShowViews`：
- 第一个分支匹配当前 catalog 为 Iceberg 视图 catalog、且 namespace 为空的情况（即用户执行 `SHOW VIEWS` 未显式指定 namespace），将其改写为 `ShowIcebergViews(ResolvedNamespace(currentCatalog, Seq.empty), pattern, output)`，用当前 catalog 与空 namespace 解析。
- 第二个分支匹配显式指定了 catalog 与 namespace 的情况（通过 `CatalogAndNamespace` 提取），当该 catalog 是 Iceberg 视图 catalog 时改写为 `ShowIcebergViews(ResolvedNamespace(catalog, ns), pattern, output)`。

两分支都通过 `isViewCatalog` 判断目标 catalog 是否实现了视图能力，确保只在 Iceberg 视图 catalog 上触发改写，不影响其它 catalog。`pattern` 与 `output` 透传以保留原始查询的模式匹配与输出列定义。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/views/ShowIcebergViews.scala

**修改目的**：新增 Iceberg 专用的"列举视图"逻辑计划节点，作为分析改写的目标、并作为物理策略的匹配源。

**工作逻辑**：定义 `ShowIcebergViews` case class，继承 `UnaryCommand`。持有三个字段：`namespace: LogicalPlan`（解析后的命名空间）、`pattern: Option[String]`（可选的模式匹配串）、`output: Seq[Attribute]`（输出列，默认取 `ShowViews.getOutputAttrs` 以保持与 Spark 原生 SHOW VIEWS 输出结构一致）。`child` 指向 namespace，`withNewChildInternal` 用于树形改写时生成新副本。该节点本身不含执行逻辑，仅承载语义，等待策略层将其转换为执行节点。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/DescribeV2ViewExec.scala

**修改目的**：新增执行节点，实现 `DESCRIBE [EXTENDED] view` 的数据组装。

**工作逻辑**：定义 `DescribeV2ViewExec(output, view, isExtended)`，继承 `V2CommandExec` 与 `LeafExecNode`。`run()` 方法依据 `isExtended` 决定返回内容：
- 非 EXTENDED 模式：仅返回 `describeSchema`，即遍历 `view.schema()` 的每一列，输出 `(列名, 类型 simpleString, 列注释)` 三元组。
- EXTENDED 模式：返回 `describeSchema :+ emptyRow`（一个空分隔行）再拼接 `describeExtended`。`describeExtended` 组装"详细视图信息"区段，包含：标题行 `# Detailed View Information`、`Comment`（取 `PROP_COMMENT`）、`View Catalog and Namespace`（当前 catalog + namespace 的 quoted 形式）、`View Query Output Columns`（`view.queryColumnNames` 拼成 `[a, b]`）、`View Properties`（剔除 `RESERVED_PROPERTIES` 后按 key 排序并单引号转义）、`Created By`（取 `PROP_CREATE_ENGINE_VERSION`）。

属性展示时通过 `ViewCatalog.RESERVED_PROPERTIES` 排除系统保留属性，只展示用户自定义部分；并使用 `escapeSingleQuotedString` 对 key/value 做转义，避免注入与格式破坏。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ExtendedDataSourceV2Strategy.scala

**修改目的**：在物理策略层把视图相关的逻辑节点映射到对应的执行节点。

**工作逻辑**：新增导入 `ResolvedNamespace`、`DescribeRelation`、`ShowCreateTable`、`ShowTableProperties`、`ShowIcebergViews`，并在 `apply` 方法中追加四条 case 规则：
- `DescribeRelation(ResolvedV2View(catalog, ident), _, isExtended, output)` -> `DescribeV2ViewExec(output, catalog.loadView(ident), isExtended)`：当描述对象是已解析的视图时，加载该视图并交给 `DescribeV2ViewExec`。
- `ShowTableProperties(ResolvedV2View(catalog, ident), propertyKey, output)` -> `ShowV2ViewPropertiesExec(output, catalog.loadView(ident), propertyKey)`：查看视图属性时加载视图并委托执行。
- `ShowIcebergViews(ResolvedNamespace(catalog: ViewCatalog, namespace), pattern, output)` -> `ShowV2ViewsExec(output, catalog, namespace, pattern)`：列举视图时要求 catalog 是 `ViewCatalog`，把 catalog、namespace、pattern 透传给执行节点。
- `ShowCreateTable(ResolvedV2View(catalog, ident), _, output)` -> `ShowCreateV2ViewExec(output, catalog.loadView(ident))`：对视图执行 SHOW CREATE TABLE 时加载视图并重建 DDL。

四条规则共同覆盖了描述、属性、列举、建表 DDL 反向生成四类命令，且都依赖 `ResolvedV2View`（视图已解析）作为前置条件，确保只在视图对象上生效。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowCreateV2ViewExec.scala

**修改目的**：新增执行节点，实现 `SHOW CREATE TABLE view`，反向重建视图的 CREATE DDL。

**工作逻辑**：定义 `ShowCreateV2ViewExec(output, view)`，`run()` 用 `StringBuilder` 拼接 DDL：
1. 起始 `CREATE VIEW <view.name> `；
2. `showColumns`：遍历 `view.schema().fields`，对每个字段输出 `name` 及其可选 `COMMENT '...'`，用 `concatByMultiLines` 拼成多行括号块；
3. `showComment`：若视图含 `PROP_COMMENT`，追加 `COMMENT '...'`；
4. `showProperties`：剔除保留属性后，若仍有用户自定义属性，则用 `conf.redactOptions` 做敏感信息脱敏，按 key 排序后拼成 `TBLPROPERTIES (...)`；
5. 末尾追加 `AS\n<view.query>\n`。

`concatByMultiLines` 工具方法把一组字符串拼成 `(\n  a,\n  b,\n)\n` 的多行格式，保证输出 DDL 可读、可重放。脱敏处理体现了对敏感配置（如含密钥的属性）的安全考量。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowV2ViewPropertiesExec.scala

**修改目的**：新增执行节点，实现 `SHOW TBLPROPERTIES view [(key)]`。

**工作逻辑**：定义 `ShowV2ViewPropertiesExec(output, view, propertyKey)`。私有 `properties` 方法将 `view.properties` 转为 Scala Map 并剔除 `ViewCatalog.RESERVED_PROPERTIES`。`run()` 分两种情况：
- 指定了 `propertyKey`：从 properties 中取值，若不存在则返回提示信息 `View <name> does not have property: <key>`，输出单行 `(key, value)`。
- 未指定：遍历所有 properties，每对 `(k, v)` 输出一行。

该节点与 `ShowV2ViewPropertiesExec`（表版）行为对齐，但作用对象是视图，且统一排除保留属性，避免泄露系统内部元数据。

### spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/ShowV2ViewsExec.scala

**修改目的**：新增执行节点，实现 `SHOW VIEWS [IN namespace] [LIKE pattern]`，列出视图。

**工作逻辑**：定义 `ShowV2ViewsExec(output, catalog, namespace, pattern)`，`run()` 用 `ArrayBuffer[InternalRow]` 累积结果，逻辑分三段：
1. 全局临时视图：若 namespace 非空且首段等于全局临时视图数据库名（`session.sessionState.catalog.globalTempViewManager.database`），则从 `globalTempViewManager.listViewNames` 取视图名（按 pattern 过滤），每行输出 `(globalTemp, name, isTemporary=true)`。
2. 命名空间下的持久视图：调用 `catalog.listViews(namespace: _*)` 列举，对每个视图用 `StringUtils.filterPattern` 做 pattern 匹配，命中则输出 `(namespace.quoted, name, isTemporary=false)`。
3. 本地临时视图：无论 namespace 如何，都通过 `session.sessionState.catalog.listLocalTempViews` 列出本地临时视图，每行输出 `(database.quoted, name, isTemporary=true)`。

输出三列含义为（命名空间、视图名、是否临时视图）。三段合一保证 `SHOW VIEWS` 能完整呈现持久视图、全局临时视图、本地临时视图，与 Spark 原生 SHOW VIEWS 语义一致。pattern 为空时使用通配 `*` 全量列出。

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java

**修改目的**：为新增的四类命令补充测试覆盖，并修正既有测试中对命名空间的硬编码。

**工作逻辑**：新增七个测试方法：
- `describeView`：建普通视图后 `DESCRIBE` 校验列名/类型/注释。
- `describeExtendedView`：`DESCRIBE EXTENDED` 校验列、详细信息区段（Comment、View Catalog and Namespace、View Query Output Columns `[id, data]`、View Properties 含 format-version/location/provider）。
- `showViewProperties`：建带 `key1/key2` 属性的视图，`SHOW TBLPROPERTIES` 校验返回值。
- `showViewPropertiesByKey`：校验按 key 查询（含 provider 这种保留属性、以及不存在的 key 返回提示）。
- `showViews`：建多个视图（v1、prefixV2、prefixV3）+ 全局临时视图 + 本地临时视图，校验 `SHOW VIEWS`、`SHOW VIEWS IN catalog`、`SHOW VIEWS IN catalog.namespace`、`SHOW VIEWS LIKE 'pref*'`、`SHOW VIEWS LIKE 'non-existing'`（空）、`SHOW VIEWS IN spark_catalog.default`（仅临时）、`SHOW VIEWS IN global_temp`（全局临时）等多种场景，覆盖 namespace 解析、pattern 匹配与 isTemporary 标志。
- `showCreateSimpleView`：校验简单视图的 `SHOW CREATE TABLE` 输出 DDL 与期望格式完全一致（含列块、TBLPROPERTIES 含 format-version/location/provider、AS 查询）。
- `showCreateComplexView`：校验带列别名+COMMENT、视图 COMMENT、自定义 key1/key2 属性的复杂视图 DDL 重建，验证属性按 key 排序、列注释正确拼接。

同时将两处既有断言中硬编码的 `"default"` 替换为 `NAMESPACE` 常量，使测试与测试基类的命名空间配置保持一致，避免在非默认命名空间环境下误判。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java

**修改目的**：打通 `listViews` 底层数据来源，使 `SHOW VIEWS` 能真正从 Iceberg 视图 catalog 取到视图列表。

**工作逻辑**：原 `listViews(String... namespace)` 直接抛 `UnsupportedOperationException`，表示"本 catalog 不支持列举视图"。本提交改为：先判断 `asViewCatalog` 是否非空（即底层 catalog 是否实现了 `ViewCatalog` 接口），若实现则委托 `asViewCatalog.listViews(Namespace.of(namespace))` 取 Iceberg 标识符，再逐个映射为 Spark 的 `Identifier`（合并 namespace levels 与 name）返回数组；若未实现则返回空数组。这是从"拒绝服务"到"按能力提供服务"的语义转变，是 `SHOW VIEWS` 链路能跑通的关键一环。

## 小结

本提交是 Iceberg 视图能力在 Spark 3.4 上的重要补全，使视图具备与表一致的只读检视能力（DESCRIBE / SHOW TBLPROPERTIES / SHOW VIEWS / SHOW CREATE TABLE）。其设计严格遵循 Spark Catalyst 分层架构，通过"分析改写 + 新增逻辑节点 + 物理策略映射 + 执行节点实现"四层协作落地，并修正了 `SparkCatalog.listViews` 的能力缺口。新增的四个执行节点各司其职，且在属性展示、DDL 重建等环节统一处理保留属性过滤、排序、转义与敏感信息脱敏，体现了对安全与一致性的考量。配套的七个测试方法覆盖了正常、扩展、模式匹配、属性按 key 查询、简单/复杂 DDL 重建等关键路径，质量较高。整体上使 Iceberg 视图在 Spark 3.4 中更趋近于一等公民，提升了可用性与生态兼容性。
