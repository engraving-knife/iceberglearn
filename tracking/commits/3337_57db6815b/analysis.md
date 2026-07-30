# 提交 3337：Core, Spark: Adds a Table Property for Relying on Identifier Fields (#15372)

## 提交信息

- **序号**：3337 / 4088
- **哈希**：57db6815bc85a741a188f6d387d721dcb3153f2d
- **短哈希**：57db6815b
- **日期**：2026-03-02 12:23:46 -0600
- **作者**：Russell Spitzer
- **提交说明**：Core, Spark: Adds a Table Property for Relying on Identifier Fields (#15372)
- **PR/Issue**：#15372

## 总体目的

本提交为 Iceberg 表新增一个"可依赖标识符字段"的声明能力，让用户在确信表中 identifier fields（标识符字段）具备唯一性时，能够把这一事实告知查询引擎，从而使引擎可以据此做查询优化（如消除冗余连接、消除 distinct 等）。

背景是：Iceberg 早已支持在 schema 中声明 identifier fields（通过 `setIdentifierFields`），这些字段在语义上标识一行记录。但此前 Iceberg 没有任何机制告诉引擎"你可以信任这些字段的唯一性并据此优化"。换言之，identifier fields 的存在只是元数据声明，引擎无法判断它是否真的可当作主键来依赖。提交说明明确指出：Iceberg 既不强制校验 identifier fields 的唯一性，也不在写入时做验证；但有些用户清楚自己的数据中这些字段确实是唯一的（例如来自上游数据库的主键）。本 PR 就是给这类用户提供一个选项，让兼容的引擎知道它可以利用这种唯一性。

具体实现借助了 Spark 4.1 的 DataSource V2 约束 API（`org.apache.spark.sql.connector.catalog.constraints.Constraint`）。当该属性开启且表存在 identifier fields 时，`SparkTable.constraints()` 会返回一个 `PrimaryKey` 约束，其 `enforced=false`（不强制）、`validationStatus=UNVALIDATED`（未校验）、`rely=true`（可依赖）。`rely=true` 正是 SQL 标准中 `RELY` 约束的语义——约束未被强制，但优化器可以信任它。这样 Spark 的 Catalyst 优化器就能在逻辑计划层面利用该主键信息进行优化。该改动仅落在 `spark/v4.1/` 模块，因为 Constraint API 是 Spark 4.1 才引入的。

## 如何达成设计目的

设计分为三层：

1. **Core 层属性定义**：在 `TableProperties` 中新增表属性 `read.identifier-fields.rely` 及默认值 `false`，作为持久化的声明开关。

2. **Spark 配置层**：在 `SparkSQLProperties` 中新增会话级配置 `spark.sql.iceberg.identifier-fields-rely`，允许在不修改表属性的情况下临时启用/禁用；在 `SparkReadConf` 中新增 `identifierFieldsRely()` 方法，按"会话配置优先于表属性、默认 false"的优先级解析最终值。

3. **Spark 表暴露层**：在 `SparkTable` 中实现 `constraints()` 方法，当 rely 开启且 schema 存在 identifier fields 时，构造一个 `PrimaryKey` 约束（`enforced(false)`、`UNVALIDATED`、`rely(true)`）返回给 Spark；同时在 `Spark3Util` 中新增工具方法把字段名集合转换为 Spark 的 `NamedReference[]`。

涉及 6 个文件：1 个 Core 属性类、3 个 Spark 配置/工具类、1 个 Spark 表实现、1 个测试类。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+9 lines)

**修改目的**：定义可依赖标识符字段的表属性键名与默认值。

**工作逻辑**：
新增常量 `READ_IDENTIFIER_FIELDS_RELY = "read.identifier-fields.rely"` 与 `READ_IDENTIFIER_FIELDS_RELY_DEFAULT = false`。Javadoc 明确说明：当为 true 时，声明表的 identifier fields 可被查询引擎当作主键依赖以做优化（如消除冗余连接或 distinct）；此属性不在写入时强制，也不校验已有数据。默认 false 保证向后兼容——未显式声明的表不会向引擎暴露主键约束，行为与改动前一致。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java` (+4 lines)

**修改目的**：提供把字段名集合批量转换为 Spark `NamedReference[]` 的工具方法。

**工作逻辑**：
新增 `public static NamedReference[] toNamedReferences(Set<String> names)`，内部用流式调用既有的 `toNamedReference(String name)` 逐个转换并收为数组。该方法供 `SparkTable.constraints()` 构造主键列引用时使用，避免在调用处写重复的流式转换代码。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+9 lines)

**修改目的**：提供按优先级解析 rely 开关的读取方法。

**工作逻辑**：
新增 `public boolean identifierFieldsRely()`，通过 `confParser.booleanConf()` 链式配置三层数据源：`.sessionConf(SparkSQLProperties.IDENTIFIER_FIELDS_RELY)`（会话级 Spark 配置）、`.tableProperty(TableProperties.READ_IDENTIFIER_FIELDS_RELY)`（表属性）、`.defaultValue(TableProperties.READ_IDENTIFIER_FIELDS_RELY_DEFAULT)`（默认 false）。解析器按顺序取首个可用值，因此会话配置优先于表属性——这允许用户在会话级别临时覆盖表属性（测试中验证了 session conf `false` 覆盖表属性 `true` 的场景）。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+3 lines)

**修改目的**：定义会话级 rely 开关的配置键名。

**工作逻辑**：
新增常量 `IDENTIFIER_FIELDS_RELY = "spark.sql.iceberg.identifier-fields-rely"`，注释说明其用途为"将 identifier fields 作为主键约束供查询优化依赖（不强制）"。该键被 `SparkReadConf.identifierFieldsRely()` 用作会话级数据源，使用户可通过 `spark.sql.iceberg.identifier-fields-rely=true` 在不修改表属性的情况下为当前会话启用依赖声明。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+22 lines)

**修改目的**：向 Spark 暴露主键约束，使优化器可利用 identifier fields。

**工作逻辑**：
重写 `constraints()` 方法（Spark DataSource V2 的 `Table` 接口方法）。逻辑如下：

1. 创建空的 `List<Constraint>`。
2. 构造 `SparkReadConf`，调用 `identifierFieldsRely()` 获取 rely 开关。
3. 从 `icebergTable.schema().identifierFieldNames()` 获取表的 identifier 字段名集合。
4. 当且仅当 `rely == true` 且 identifier 字段集合非空时，构造一个 `PrimaryKey` 约束：名称固定为 `"iceberg_pk"`，列引用由 `Spark3Util.toNamedReferences(identifierFieldNames)` 生成，并设置 `enforced(false)`（不强制）、`validationStatus(ValidationStatus.UNVALIDATED)`（未校验）、`rely(true)`（可依赖）。
5. 返回约束数组。

关键设计点是三个约束属性的组合：`enforced=false` 表示引擎不需要在写入时维护该约束；`UNVALIDATED` 表示不校验已有数据是否满足；`rely=true` 是核心——它告诉优化器"尽管不强制，但你可以信任此主键的唯一性并据此做优化"。这正对应 SQL 标准中 `CONSTRAINT ... RELY` 的语义。当 rely 关闭或无 identifier 字段时返回空数组，不暴露任何约束。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java` (+118/-7 lines)

**修改目的**：验证 rely 约束在不同配置路径下的正确行为。

**工作逻辑**：
测试表的建表语句从两列改为三列（`id bigint NOT NULL, name string NOT NULL, data string`），以支持多列 identifier 字段测试。新增三个测试方法与两个辅助方法：

- `testNoIdentifierFieldsRelyByDefault()`：验证默认情况下（无表属性）`constraints()` 不返回主键；并验证即便设置了 `read.identifier-fields.rely=true`，若表没有 identifier fields，仍不返回主键。这覆盖了"rely 开启但无 identifier 字段"的边界。

- `testIdentifierFieldsRelyViaTableProperty()`：先用 `updateSchema().setIdentifierFields("id", "name")` 设置标识符字段，再通过 `ALTER TABLE SET TBLPROPERTIES` 设置 `read.identifier-fields.rely=true`。断言返回一个主键，名称为 `iceberg_pk`，`enforced=false`、`rely=true`、`validationStatus=UNVALIDATED`，且列名集合为 `{id, name}`。随后将属性改回 `false`，断言主键消失。这完整验证了表属性路径与约束属性。

- `testIdentifierFieldsRelyViaSessionConf()`：设置单列 identifier 字段 `id`，用会话配置 `spark.sql.iceberg.identifier-fields-rely=true` 启用，断言返回单列主键 `{id}`。接着设置表属性 `rely=true`，但用会话配置 `rely=false` 覆盖，断言主键消失。这验证了会话配置优先于表属性的优先级规则。

- 辅助方法 `primaryKeys(SparkTable)`：从 `constraints()` 中筛选 `PrimaryKey` 类型并收集为列表。
- 辅助方法 `loadSparkTable()`：封装通过 catalog 加载 `SparkTable` 的逻辑，消除重复代码（原 `testTableEquality` 也改用此方法）。

## 总结

本提交引入 `read.identifier-fields.rely` 表属性（及对应的 Spark 会话配置），让用户在确信 identifier fields 唯一时，能通过 Spark 4.1 的 Constraint API 以 `rely=true` 的非强制主键约束形式告知 Spark 优化器，使其可据此消除冗余连接/distinct 等优化。设计上严格区分"声明可依赖"与"强制校验"——约束始终 `enforced=false`/`UNVALIDATED`，仅靠 `rely=true` 传递信任，既不增加写入开销，又为有此需求的用户打开了优化通道。
