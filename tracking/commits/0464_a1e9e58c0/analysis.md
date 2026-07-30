# 提交 0464：Core: Add catalog type for glue, jdbc, nessie

## 提交信息

- **序号**：0464
- **完整哈希**：a1e9e58c0c98bb7f001d70c2cbf5bdc493dce25f
- **短哈希**：a1e9e58c0
- **日期**：2024-02-05 19:26:07 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Add catalog type for glue, jdbc, nessie
- **PR**：#9647
- **共同作者**：zhaomin <zhaomin1423@163.com>

## 总体目的

本提交为 Iceberg 的 `CatalogUtil` 增加对 `glue`、`jdbc`、`nessie` 三种 catalog 类型的内建识别，使得用户在配置 Spark/Flink/Hive 等引擎时可以直接通过简短的 `type=glue`、`type=jdbc`、`type=nessie` 来加载对应的 catalog，而不必再写完整类名（如 `catalog-impl=org.apache.iceberg.aws.glue.GlueCatalog`）。

在此提交之前，`CatalogUtil.buildIcebergCatalog` 的 `switch(catalogType)` 只识别 `hadoop`、`hive`、`rest` 三种内建类型。对于 Glue、JDBC、Nessie 这三种同样随 Iceberg 一起发布、开箱即用的 catalog 实现，用户只能通过 `catalog-impl` 指定完整类名，配置体验不一致，也容易因类名拼写错误导致初始化失败。这种不一致在文档中也体现为：aws.md、jdbc.md、nessie.md 等页面大量示例都不得不写完整类名。

本提交在 `CatalogUtil` 中为这三种 catalog 新增类型常量与 switch 分支，使它们与 `hive`/`hadoop`/`rest` 平级，统一了配置入口。同时更新多个文档页面，把示例从 `catalog-impl=...` 改为更简洁的 `type=glue|jdbc|nessie`，并在 Flink 与 Spark 配置文档的 `catalog-type` 取值表中补充这三个新值。测试侧则调整 `TestJdbcCatalog` 与 `TestNessieCatalog`，让它们走 `CatalogUtil.buildIcebergCatalog` 这条统一入口来初始化 catalog，从而对新增的类型识别逻辑形成回归保护。

## 如何达成设计目的

核心改动集中在 `CatalogUtil.java`：新增 6 个常量（3 个类型字符串常量 + 3 个实现类全限定名常量），并在 `buildIcebergCatalog` 的 switch 中增加 3 个 case，把类型字符串映射到对应的实现类。文档侧把各引擎示例与配置表统一改为使用 `type=` 简写。测试侧把原先直接 `new JdbcCatalog()` / `new NessieCatalog()` 然后 `initialize()` 的写法，改为通过 `CatalogUtil.buildIcebergCatalog(...)` 构造，并在 properties 中带上 `type` 字段，确保新映射路径被实际执行覆盖。

## 修改详情

### core/src/main/java/org/apache/iceberg/CatalogUtil.java

**修改目的**：在核心 Catalog 工具类中正式注册 glue、jdbc、nessie 三种 catalog 类型，使其可通过 `type` 简写加载。

**工作逻辑**：
- 新增三个类型字符串常量：`ICEBERG_CATALOG_TYPE_GLUE = "glue"`、`ICEBERG_CATALOG_TYPE_NESSIE = "nessie"`、`ICEBERG_CATALOG_TYPE_JDBC = "jdbc"`，与既有的 `hadoop`/`hive`/`rest` 常量并列。
- 新增三个实现类全限定名常量：`ICEBERG_CATALOG_GLUE = "org.apache.iceberg.aws.glue.GlueCatalog"`、`ICEBERG_CATALOG_NESSIE = "org.apache.iceberg.nessie.NessieCatalog"`、`ICEBERG_CATALOG_JDBC = "org.apache.iceberg.jdbc.JdbcCatalog"`。
- 在 `buildIcebergCatalog` 的 `switch(catalogType)` 中，紧跟 `case ICEBERG_CATALOG_TYPE_REST` 之后新增三个 case，分别把 `catalogImpl` 赋为对应的实现类常量，随后 `break`。`default` 分支仍抛出 `Unknown catalog type` 异常，保持未知类型的安全性。

### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java

**修改目的**：让 JDBC catalog 测试通过 `CatalogUtil.buildIcebergCatalog` 入口初始化，覆盖新增的 `type=jdbc` 映射路径。

**工作逻辑**：
- 新增 import `org.apache.iceberg.CatalogUtil`。
- 在构造 catalog 时，向 `properties` 中加入 `properties.put("type", "jdbc")`。
- 删除原先的 `new JdbcCatalog()` + `setConf()` + `initialize()` 三行手动构造代码，改为 `return (JdbcCatalog) CatalogUtil.buildIcebergCatalog(catalogName, properties, conf)`。
- 这样测试既验证了新映射能正确产出 `JdbcCatalog` 实例，也保证了 `buildIcebergCatalog` 这条统一入口对 JDBC 的端到端可用性。

### docs/docs/aws.md

**修改目的**：把 AWS（Glue）相关示例从写完整类名改为使用 `type=glue` 简写，统一配置风格。

**工作逻辑**：
- 多处 Spark/Flink/Hive 启动示例中，把 `--conf spark.sql.catalog.my_catalog.catalog-impl=org.apache.iceberg.aws.glue.GlueCatalog`（或 Hive 的 `SET iceberg.catalog.glue.catalog-impl=...`、Flink 的 `'catalog-impl'='...'`）替换为 `--conf spark.sql.catalog.my_catalog.type=glue`（对应 `SET iceberg.catalog.glue.type=glue`、`'type'='glue'`）。
- 涉及 S3 tagging、access-point、acceleration、dual-stack、AssumeRole 等多个示例段落，全部统一改为 `type=glue`。
- 在「Glue Catalog」说明段，把「You can start using Glue catalog by specifying the `catalog-impl` as `org.apache.iceberg.aws.glue.GlueCatalog`,」改为「…by specifying the `catalog-impl` as `org.apache.iceberg.aws.glue.GlueCatalog` **or by setting `type` as `glue`**,」，明确两种写法都受支持。

### docs/docs/flink-configuration.md

**修改目的**：在 Flink 全局配置属性表中扩展 `catalog-type` 的取值范围。

**工作逻辑**：
- `catalog-type` 行的「Values」列从 `` `hive`, `hadoop` or `rest` `` 改为 `` `hive`, `hadoop`, `rest`, `glue`, `jdbc` or `nessie` ``。
- 「Description」列同步补充：列出 `GlueCatalog`、`JdbcCatalog`、`NessieCatalog` 作为可选的底层实现，并保留「left unset if using a custom catalog implementation via catalog-impl」说明。

### docs/docs/flink.md

**修改目的**：在 Flink 创建 catalog 的属性说明列表中同步新增三种 catalog-type。

**工作逻辑**：
- 将 `catalog-type` 一项的描述从 `` `hive`, `hadoop` or `rest` for built-in catalogs... `` 改为 `` `hive`, `hadoop`, `rest`, `glue`, `jdbc` or `nessie` for built-in catalogs... ``，其余说明保持不变。

### docs/docs/hive.md

**修改目的**：将 Hive 注册 Glue catalog 的示例改为使用 `type=glue`。

**工作逻辑**：
- 把 `SET iceberg.catalog.glue.catalog-impl=org.apache.iceberg.aws.glue.GlueCatalog;` 改为 `SET iceberg.catalog.glue.type=glue;`，后续 `warehouse`、`lock.table` 等配置保持不变。

### docs/docs/jdbc.md

**修改目的**：将 JDBC catalog 的 Spark 启动示例改为使用 `type=jdbc`。

**工作逻辑**：
- 把 `--conf spark.sql.catalog.my_catalog.catalog-impl=org.apache.iceberg.jdbc.JdbcCatalog \` 改为 `--conf spark.sql.catalog.my_catalog.type=jdbc \`，其余 `uri`、`jdbc.verifyServerCertificate` 等配置不变。

### docs/docs/nessie.md

**修改目的**：将 Nessie catalog 的 Spark 与 Flink 示例改为使用 `type=nessie`，并补全说明文字。

**工作逻辑**：
- Spark 示例：`conf.set("spark.sql.catalog.nessie.catalog-impl", "org.apache.iceberg.nessie.NessieCatalog")` 改为 `conf.set("spark.sql.catalog.nessie.type", "nessie")`。
- Flink 示例：`'catalog-impl'='org.apache.iceberg.nessie.NessieCatalog'` 改为 `'type'='nessie'`。
- 说明文字「the important parts are the settings for the `catalog-impl` and the required config...」改为「the important parts are the settings for the `type` or `catalog-impl` and the required config...」，明确两种写法均可。

### docs/docs/spark-configuration.md

**修改目的**：在 Spark 配置属性表中扩展 `spark.sql.catalog._catalog-name_.type` 的取值范围。

**工作逻辑**：
- `type` 行的「Values」列从 `` `hive`, `hadoop` or `rest` `` 改为 `` `hive`, `hadoop`, `rest`, `glue`, `jdbc` or `nessie` ``。
- 「Description」列同步列出 `GlueCatalog`、`JdbcCatalog`、`NessieCatalog`，并保留「or left unset if using a custom catalog」说明。
- 同时调整了该行所在表格的列宽分隔符以适配更长的内容。

### nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java

**修改目的**：让 Nessie catalog 测试通过 `CatalogUtil.buildIcebergCatalog` 入口初始化，覆盖新增的 `type=nessie` 映射路径。

**工作逻辑**：
- 新增 import `java.util.Map` 与 `org.apache.iceberg.CatalogUtil`。
- `initNessieCatalog(String ref)` 方法原先：`new NessieCatalog()` → `setConf()` → 构造 `ImmutableMap` → `initialize("nessie", options)` → 返回。
- 改后：构造 `Map<String, String> options` 时在 `ImmutableMap.of(...)` 中加入 `"type", "nessie"`（与 `ref`、`uri`、`warehouse` 并列），然后 `return (NessieCatalog) CatalogUtil.buildIcebergCatalog("nessie", options, hadoopConfig)`。
- 这样测试通过统一入口加载 NessieCatalog，验证 `type=nessie` 映射的正确性，同时简化了手动构造步骤。

## 小结

本提交把 Iceberg 内建 catalog 的「类型简写」从原来的 3 种（hive/hadoop/rest）扩展到 6 种（新增 glue/jdbc/nessie），核心改动是 `CatalogUtil` 中 6 个常量与 3 个 switch 分支。配套地，多个文档页面把示例统一为 `type=...` 简写，两个 catalog 测试改走 `buildIcebergCatalog` 统一入口以形成回归保护。整体提升了配置的一致性与易用性，降低了用户因拼写完整类名而出错的风险。
