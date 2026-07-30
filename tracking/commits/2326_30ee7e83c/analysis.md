# 提交 2326：Spark 4.0: Migrate Iceberg Stored Procedures to Spark built-in implementations (#13106)

## 提交信息

- **序号**：2326 / 4088
- **哈希**：30ee7e83cfa3e5559d8d3ba4b6380156accb43f1
- **短哈希**：30ee7e83c
- **日期**：2025-07-07 15:07:49 -0700
- **作者**：Cheng Pan
- **提交说明**：Spark 4.0: Migrate Iceberg Stored Procedures to Spark built-in implementations (#13106)
- **PR/Issue**：#13106

## 总体目的

这是一个大型重构提交，将 Spark 4.0 中 Iceberg 的存储过程（Stored Procedures）从 Iceberg 自定义实现迁移到 Spark 4.0 内置的存储过程框架。

在 Spark 4.0 之前，Iceberg 使用自己实现的一套存储过程框架，包括：自定义的 `Procedure`/`ProcedureCatalog`/`ProcedureParameter` 接口（位于 `org.apache.spark.sql.connector.iceberg.catalog` 包）、自定义的 SQL 扩展解析器（用于解析 `CALL` 语句）、以及自定义的逻辑计划和执行层（`IcebergCall`、`CallExec`、`ResolveProcedures`、`ProcedureArgumentCoercion` 等 Scala 类）。

Spark 4.0 引入了内置的存储过程框架（`ProcedureCatalog`、`UnboundProcedure`、`BoundProcedure`、`ProcedureParameter` 等接口位于 `org.apache.spark.sql.connector.catalog.procedures` 包），因此 Iceberg 不再需要维护自己的一套实现。迁移到内置框架可以减少维护成本、确保与 Spark 4.0 生态的一致性，并移除大量自定义代码。

## 如何达成设计目的

迁移的核心思路是将所有自定义接口替换为 Spark 4.0 内置接口：

1. **接口替换**：Iceberg 的 `Procedure` → Spark 的 `UnboundProcedure` + `BoundProcedure`；`ProcedureParameter` → Spark 的 `ProcedureParameter`；`ProcedureCatalog` → Spark 的 `ProcedureCatalog`
2. **删除自定义解析层**：移除 IcebergCall、CallExec、ResolveProcedures、ProcedureArgumentCoercion、TestCallStatementParser 等自定义 SQL 扩展代码
3. **简化语法解析**：移除 IcebergSqlExtensions.g4 中 CALL 语句的解析规则，解析器不再需要拦截 CALL 语句（由 Spark 原生处理）
4. **适配返回类型**：从 `InternalRow[]` 改为 `Iterator<Scan>`，使用 `LocalScan` 实现结果返回
5. **各 Procedure 实现适配**：所有存储过程实现类修改参数构建方式和返回类型

## 修改详情

### 删除的文件 (11 个)

**修改目的**：移除 Iceberg 自定义的存储过程框架。

- `spark/v4.0/spark-extensions/src/main/scala/.../analysis/ProcedureArgumentCoercion.scala` — 自定义参数类型转换规则
- `spark/v4.0/spark-extensions/src/main/scala/.../analysis/ResolveProcedures.scala` — 自定义过程解析器（189 行）
- `spark/v4.0/spark-extensions/src/main/scala/.../plans/logical/IcebergCall.scala` — 自定义 CALL 逻辑计划
- `spark/v4.0/spark-extensions/src/main/scala/.../plans/logical/statements.scala` — CALL 语句 AST 定义
- `spark/v4.0/spark-extensions/src/main/scala/.../execution/datasources/v2/CallExec.scala` — 自定义 CALL 执行器
- `spark/v4.0/spark-extensions/src/test/.../TestCallStatementParser.java` — CALL 语句解析测试（219 行）
- `spark/v4.0/spark/src/main/java/.../NoSuchProcedureException.java` — 自定义异常
- `spark/v4.0/spark/src/main/java/.../connector/iceberg/catalog/Procedure.java` — Iceberg 自定义过程接口
- `spark/v4.0/spark/src/main/java/.../connector/iceberg/catalog/ProcedureCatalog.java` — Iceberg 自定义过程目录接口
- `spark/v4.0/spark/src/main/java/.../connector/iceberg/catalog/ProcedureParameter.java` — 自定义参数接口
- `spark/v4.0/spark/src/main/java/.../connector/iceberg/catalog/ProcedureParameterImpl.java` — 自定义参数实现

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/BaseCatalog.java` (+9/-10 lines)

**修改目的**：将 BaseCatalog 的 ProcedureCatalog 实现从 Iceberg 接口切换到 Spark 内置接口。

**工作逻辑**：import 从 `connector.iceberg.catalog.ProcedureCatalog` 改为 `connector.catalog.ProcedureCatalog`，`loadProcedure` 返回类型从 `Procedure` 改为 `UnboundProcedure`，异常从 `NoSuchProcedureException` 改为 `RuntimeException`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java` (+53/-6 lines)

**修改目的**：适配 Spark 4.0 内置的 BoundProcedure/UnboundProcedure 接口。

**工作逻辑**：
- 实现类改为 `implements BoundProcedure, UnboundProcedure`
- 新增 `requiredInParameter`/`optionalInParameter` 静态方法构建 Spark 内置 `ProcedureParameter`
- 新增 `isDeterministic()` 返回 false
- 新增内部 `Result` 类实现 `LocalScan` 接口，用于返回结果
- 新增 `asScanIterator` 方法将结果转换为 `Iterator<Scan>`

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` (+48/-35 lines)

**修改目的**：适配 Spark 内置过程接口。

**工作逻辑**：`ProcedureBuilder.build()` 返回类型从 `Procedure` 改为 `UnboundProcedure`，过程注册使用各 Procedure 类的 `NAME` 常量替代硬编码字符串，移除 `names()` 方法。

### 各 Procedure 实现类 (18 个文件)

**修改目的**：适配新的接口和返回类型。

**工作逻辑**：每个 Procedure 类进行类似修改：
- 新增 `NAME` 常量
- 参数构建从 `ProcedureParameter.required/optional` 改为 `requiredInParameter/optionalInParameter`
- 新增 `bind(StructType)` 方法返回 `this`
- `call` 方法返回类型从 `InternalRow[]` 改为 `Iterator<Scan>`
- 输出通过 `asScanIterator` 返回

### `spark/v4.0/spark-extensions/src/main/antlr/.../IcebergSqlExtensions.g4` (+11/-22 lines)

**修改目的**：移除 CALL 语句的语法定义。

### `spark/v4.0/spark-extensions/src/main/scala/.../IcebergSparkSqlExtensionsParser.scala` (+29/-30 lines)

**修改目的**：移除 CALL 语句的解析拦截逻辑。

**工作逻辑**：移除 `isIcebergProcedure` 方法，解析器不再需要判断 SQL 是否为 CALL 语句（Spark 原生处理）。

### `spark/v4.0/spark-extensions/src/main/scala/.../IcebergSparkSessionExtensions.scala` (+4/-8 lines)

**修改目的**：移除自定义解析规则注册。

### `spark/v4.0/spark-extensions/src/main/scala/.../ExtendedDataSourceV2Strategy.java` (+15/-30 lines)

**修改目的**：移除 IcebergCall 的执行策略。

### 测试文件 (13 个文件)

**修改目的**：适配新的过程调用方式。

**工作逻辑**：各测试文件主要调整过程调用的结果验证方式，适配新的返回类型。

## 总结

这是一个大型重构提交（54 文件，+642/-1236 行），将 Spark 4.0 中 Iceberg 的存储过程从自定义框架迁移到 Spark 内置框架。迁移删除了约 600 行自定义代码（包括 Scala 解析层和 Java 接口层），显著减少了维护负担。这是 Iceberg 跟随 Spark 4.0 API 演进的重要一步。
