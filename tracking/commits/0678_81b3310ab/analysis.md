# 提交 0678：Spark 3.5: Support preserving schema nullability in CTAS and RTAS

## 提交信息
- **序号**：0678 / 4088
- **哈希**：81b3310ab469408022cc14af51257b7e8b36614f
- **短哈希**：81b3310ab
- **日期**：2024-04-13 05:12:28 +0800
- **作者**：Yujiang Zhong
- **提交说明**：Spark 3.5: Support preserving schema nullability in CTAS and RTAS (#10074)
- **PR/Issue**：#10074

## 总体目的

本提交为 Iceberg 的 Spark 3.5 catalog 增加了一项可配置能力：在执行 CTAS（`CREATE TABLE AS SELECT`）与 RTAS（`REPLACE TABLE AS SELECT`）时，是否保留查询 schema 中字段的 nullability（可空性）。在此之前，Iceberg Spark catalog 在 CTAS/RTAS 场景下会将结果表的所有字段一律标记为可空（nullable），即使源查询的字段是 `NOT NULL` 的。这是因为 Spark 默认的查询输出 schema 会把所有字段当作可空处理——查询可能产生 null（例如外连接、聚合等），Spark 从保守角度统一标记为可空。Iceberg catalog 此前未提供覆盖这一行为的开关，导致用户无法在 CTAS/RTAS 建出的 Iceberg 表上保留 `NOT NULL` 约束。

这一限制带来的实际问题：Iceberg 的 schema 中字段有 `required`（非空）与 `optional`（可空）之分，这是数据质量约束的一部分。当用户通过 CTAS 基于一张有 `NOT NULL` 约束的源表创建新表时，新表丢失了这一约束，所有字段变成 `optional`，削弱了数据的完整性保证。对于希望严格保留 nullability 语义的用户，缺少配置手段是一个功能缺口。

Spark 3.5 在其 `TableCatalog` 接口中新增了 `useNullableQuerySchema()` 方法，允许 catalog 声明在 CTAS/RTAS 中是否使用全可空的查询 schema。本提交正是利用这一 Spark 3.5 新接口，在 Iceberg 的 `BaseCatalog` 中实现该方法并暴露配置项 `use-nullable-query-schema`，让用户可以按 catalog 粒度选择行为：默认值 `true` 保持向后兼容（全可空），设为 `false` 则保留字段原有的 nullability。这也是该功能仅适用于 Spark 3.5（及更高版本）的原因——`useNullableQuerySchema()` 是 Spark 3.5 才引入的接口方法。

## 如何达成设计目的

整体设计遵循"配置读取下沉到基类 + 子类委托 + 测试覆盖两种取值"的思路：

1. **基类承载配置**：在 `BaseCatalog`（`SparkCatalog` 与 `SparkSessionCatalog` 的共同抽象基类）中新增 `useNullableQuerySchema` 字段、配置键常量 `USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS = "use-nullable-query-schema"`、默认值常量 `USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS_DEFAULT = true`，并覆写 `initialize(String name, CaseInsensitiveStringMap options)` 从 catalog options 中读取该配置项。再实现 `useNullableQuerySchema()` getter 返回该字段值。这样配置读取逻辑只写一处，两个子类共享。

2. **子类委托初始化**：`SparkCatalog` 与 `SparkSessionCatalog` 各自的 `initialize` 方法中新增 `super.initialize(name, options);` 调用，确保基类有机会读取 `use-nullable-query-schema` 配置。这两处 `initialize` 原本已存在（负责读取 cache-enabled 等其他配置），只需在方法开头先调用 `super.initialize`。注意必须先调 `super.initialize` 再读子类自己的配置，顺序上基类先初始化公共字段。

3. **Spark 侧的实际应用**：本提交并未在 Iceberg 代码中显式调用 `useNullableQuerySchema()` 的消费方——因为该方法的消费方在 Spark 引擎内部。Spark 3.5 在执行 CTAS/RTAS 构建 V2 表的 schema 时，会调用 catalog 的 `useNullableQuerySchema()` 决定是否将查询 schema 的所有字段强制设为可空。当返回 `true` 时，Spark 按既有行为把字段都设为可空（对应 Iceberg 的 `optional`）；当返回 `false` 时，Spark 保留查询 schema 中字段原本的 nullability，`NOT NULL` 字段保持 `required`。Iceberg 侧只需正确实现接口方法、返回用户配置的值即可，无需自己改写 schema。

4. **测试随机化覆盖两种路径**：测试类 `TestSparkCatalogOperations` 通过 `ThreadLocalRandom.current().nextBoolean()` 随机生成 `useNullableQuerySchema` 的取值，并在 `parameters()` 中把该布尔值以 `"use-nullable-query-schema"` 配置项注入三种 catalog（HIVE/HADOOP/SPARK）的配置。这样每次测试运行会随机测试 `true` 或 `false` 路径，长期来看两条路径都会被覆盖。两个测试用例 `testCTASUseNullableQuerySchema` 与 `testRTASUseNullableQuerySchema` 分别验证 CTAS 与 RTAS 场景：先向源表插入含 null 的数据，再执行 CTAS/RTAS，然后加载结果 Iceberg 表的 schema，断言 `id` 字段在 `useNullableQuerySchema=true` 时为 `optional`、在 `false` 时为 `required`（`data` 字段因源表定义为可空，两种情况下都是 `optional`）。这直接验证了配置项对结果表 schema nullability 的影响。

## 修改详情

### `docs/docs/spark-configuration.md`
**修改目的**：文档化新增的 catalog 配置项 `use-nullable-query-schema`。
**工作逻辑**：在 Spark catalog 配置属性表中新增一行，说明配置项名 `spark.sql.catalog._catalog-name_.use-nullable-query-schema`、取值 `true` 或 `false`、语义（控制 CTAS/RTAS 是否保留字段 nullability，`true` 全可空，`false` 保留原 nullability，默认 `true`），并注明仅在 Spark 3.5 及以上可用。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/BaseCatalog.java`
**修改目的**：在 catalog 基类中实现配置读取与 `useNullableQuerySchema()` 接口方法，作为整个功能的承载点。
**工作逻辑**：
- 新增 import：`org.apache.iceberg.util.PropertyUtil`（用于安全的属性读取）与 `org.apache.spark.sql.util.CaseInsensitiveStringMap`（Spark catalog options 类型）。
- 新增两个常量：`USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS = "use-nullable-query-schema"`（配置键）、`USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS_DEFAULT = true`（默认值，保持向后兼容）。
- 新增实例字段 `private boolean useNullableQuerySchema = USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS_DEFAULT;`。
- 覆写 `initialize(String name, CaseInsensitiveStringMap options)`：通过 `PropertyUtil.propertyAsBoolean(options, USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS, USE_NULLABLE_QUERY_SCHEMA_CTAS_RTAS_DEFAULT)` 读取配置项，`PropertyUtil` 在键缺失或值非法时返回默认值，赋给 `useNullableQuerySchema` 字段。
- 实现 `useNullableQuerySchema()`：直接返回 `useNullableQuerySchema` 字段。该方法对应 Spark 3.5 `TableCatalog` 接口的新增方法，由 Spark 引擎在 CTAS/RTAS schema 构建时回调。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`
**修改目的**：让 `SparkCatalog` 的初始化流程调用基类 `initialize`，使 `use-nullable-query-schema` 配置被读取。
**工作逻辑**：在该类已有的 `initialize(String name, CaseInsensitiveStringMap options)` 方法体开头加入 `super.initialize(name, options);`。该方法后续仍继续读取 `cache-enabled`、`cache.expiration-interval-ms` 等配置，基类初始化先执行不会干扰子类逻辑。调用 `super.initialize` 是 Java 方法覆写中常见的"模板方法"模式，确保基类公共初始化不遗漏。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java`
**修改目的**：同 `SparkCatalog`，让 `SparkSessionCatalog` 初始化时也调用基类 `initialize`。
**工作逻辑**：在该类已有的 `initialize(String name, CaseInsensitiveStringMap options)` 方法体开头加入 `super.initialize(name, options);`。`SparkSessionCatalog` 是 Iceberg 用于接管 Spark 内置 session catalog 的包装 catalog，同样需要支持该配置项，使通过 session catalog 执行的 CTAS/RTAS 也能保留 nullability。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java`
**修改目的**：为新增配置项添加测试覆盖，验证 CTAS 与 RTAS 在两种取值下的 schema nullability 行为。
**工作逻辑**：
- 新增静态字段 `useNullableQuerySchema = ThreadLocalRandom.current().nextBoolean()`，测试类加载时随机决定本次运行的配置取值。
- 覆写 `parameters()` 方法（用 `@Parameters` 注解，供 `ParameterizedTestExtension` 使用），为三种 catalog 配置（HIVE/HADOOP/SPARK）都注入 `"use-nullable-query-schema"` = `Boolean.toString(useNullableQuerySchema)`，使所有参数化用例都在同一随机取值下运行。
- 新增 `testCTASUseNullableQuerySchema`：向源表插入 `(1,'abc'),(2,null)`，执行 `CREATE TABLE ctas_table USING iceberg AS SELECT * FROM <源表>`，加载 `default.ctas_table` 的 Iceberg schema，构造期望 schema——`id` 字段在 `useNullableQuerySchema` 为 true 时用 `Types.NestedField.optional(...)`（可空），为 false 时用 `Types.NestedField.required(...)`（非空）；`data` 字段恒为 optional。用 `assertThat(ctasTable.schema().asStruct()).isEqualTo(expectedSchema.asStruct())` 断言。
- 新增 `testRTASUseNullableQuerySchema`：先 `CREATE TABLE rtas_table (id bigint NOT NULL, data string) USING iceberg`（显式声明 id 为 NOT NULL），再 `REPLACE TABLE rtas_table USING iceberg AS SELECT * FROM <源表>`，加载结果 schema 做同样的断言，并额外校验替换后的数据与源表一致。
- 两个测试用例最后都 `DROP TABLE IF EXISTS` 清理，避免污染后续用例。

## 小结
- **成效**：成功达成目的。用户现在可通过 `spark.sql.catalog.<name>.use-nullable-query-schema=false` 在 Spark 3.5 的 CTAS/RTAS 中保留字段 nullability，默认行为（全可空）保持不变，向后兼容。
- **影响范围**：Spark 3.5 模块的 `BaseCatalog`、`SparkCatalog`、`SparkSessionCatalog`，以及对应的测试。不涉及 Spark 3.3/3.4（这些版本的 Spark `TableCatalog` 接口尚无 `useNullableQuerySchema()` 方法）。不影响 Flink 或其他引擎。
- **回迁到 1.4.x 的注意事项**：
  - 该功能依赖 Spark 3.5 的 `TableCatalog.useNullableQuerySchema()` 接口方法。回迁前需确认 1.4.x 分支所用的 Spark 3.5 版本该接口方法存在（Spark 3.5.0+ 均有）。
  - `BaseCatalog` 在 1.4.x 上的结构可能与 main 有差异，需确认 `initialize` 覆写与 `super.initialize` 调用链不与 1.4.x 的本地修改冲突。
  - 测试中 `parameters()` 覆写需确保不与 1.4.x 已有的参数化配置冲突；若 1.4.x 的 `TestSparkCatalogOperations` 已有自定义 `parameters()`，需合并而非替换。
  - 默认值为 `true`（向后兼容），回迁后即使不配置也不会改变现有用户行为，风险低。
