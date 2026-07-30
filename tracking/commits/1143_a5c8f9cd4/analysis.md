# 提交 1143：Build: Remove unused variables, fields and parameters (#11101)

## 提交信息

- **序号**：1143
- **哈希**：a5c8f9cd4557639d39eed716d499e9837d13b88e
- **短哈希**：a5c8f9cd4
- **日期**：2024-09-10（Tue Sep 10 11:59:26 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Remove unused variables, fields and parameters (#11101)
- **PR/Issue**：#11101

## 总体目的

Iceberg 仓库使用 Error Prone 进行静态代码分析以保障代码质量，此前已经在 `baseline.gradle` 中开启了 `UnusedMethod:ERROR`，但 `UnusedVariable` 检查尚未开启。为了进一步清理代码中遗留的"死代码"——包括从未被引用的局部变量、字段、方法参数以及方法签名中冗余的参数——本提交先一次性清理掉仓库中所有已存在的未使用变量/字段/参数，再在 `baseline.gradle` 中将 `UnusedVariable` 检查升级为 `ERROR`，从而在 CI 上强制禁止后续提交再次引入此类代码。

这是一次典型的"清理 + 加固 lint 规则"配套操作：先消解存量违规，再让规则生效，避免规则一开启就报错满屏。

## 如何达成设计目的

1. **存量清理**：在 28 个文件中删除从未被读取或赋值后从未使用的变量、字段与参数。其中部分参数由于属于接口/方法签名的一部分无法直接删除（如 `UpdateRequirements.update(MetadataUpdate.AddSchema update)` 中参数 `update` 在方法体内未使用，但方法签名需保持以体现重载语义），则将参数名改为 `unused` 或 `unused2` 以表达"已知不用"的意图，并通过 Error Prone 的命名豁免规则。
2. **方法签名简化**：对私有方法 `getCatalogProperties` 和 `spec` 等移除真正多余的方法参数（如从未读取的 `catalogType`、`properties`），同时更新所有调用点。
3. **保留带说明的"未来使用"字段**：`OrcFileAppender.conf` 字段在主代码路径中未使用，但测试代码引用了它，因此保留字段并加上 `@SuppressWarnings("unused")` 注解和注释 `// Currently used in tests TODO remove this redundant field`，说明保留原因。
4. **加固 lint 规则**：在 `baseline.gradle` 的 Error Prone 配置数组中追加 `'-Xep:UnusedVariable:ERROR'`，使后续提交若再引入未使用变量将直接构建失败。

## 修改详情

### `baseline.gradle`

**修改目的**：开启 Error Prone 的 `UnusedVariable` 检查并设为 ERROR 级别。

**工作逻辑**：在 `errorprone` 配置的选项数组中，于 `UnusedMethod:ERROR` 之后追加一行 `'-Xep:UnusedVariable:ERROR'`。Error Prone 在编译期会扫描所有局部变量、字段与参数，若发现从未被读取（且非命名豁免）将报错。

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`

**修改目的**：删除常量 `FIVE_MINUTES_IN_NANOS`。

**工作逻辑**：该常量定义为 `TimeUnit.MINUTES.toNanos(5)`，但全文未被引用，属于历史遗留代码，直接删除整行。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`

**修改目的**：删除未被使用的局部变量 `byName`。

**工作逻辑**：原代码 `Map<String, Integer> byName = indexer.byName();` 调用了 `indexer.byName()` 但赋值后从未读取 `byName`。删除该行后，`IndexByName` 实例仍被 `indexer` 引用并参与后续 `indexByName(struct)` 调用，行为不变。

### `aws/src/integration/java/org/apache/iceberg/aws/TestAssumeRoleAwsClientFactory.java`

**修改目的**：删除未被使用的 `LOG` 字段及对应 import。

**工作逻辑**：测试类中声明了 `private static final Logger LOG = LoggerFactory.getLogger(TestAssumeRoleAwsClientFactory.class);`，但全文未出现 `LOG.xxx()` 调用。删除字段声明与 `slf4j` 的 `Logger`、`LoggerFactory` 两条 import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`

**修改目的**：同上，删除未被使用的 `LOG` 字段及 import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`

**修改目的**：删除方法 `testRenameTableFailsToDeleteOldTable` 内未使用的局部变量。

**工作逻辑**：原代码在方法开头执行 `TableIdentifier id = TableIdentifier.of(namespace, tableName); Table table = glueCatalog.loadTable(id);`，但 `id` 与 `table` 后续均未使用，属于调试残留或重构遗留。删除这两行，避免无意义的 `loadTable` 远程调用，同时让测试更聚焦于"删除旧表失败"这一行为本身。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/LakeFormationTestBase.java`

**修改目的**：删除未使用的 import 与局部变量。

**工作逻辑**：
- 删除 `import ...PutDataLakeSettingsResponse;`。
- 原代码 `PutDataLakeSettingsResponse putDataLakeSettingsResponse = lakeformation.putDataLakeSettings(...)` 将返回值赋给局部变量但未读取，改为直接调用 `lakeformation.putDataLakeSettings(...)` 不接收返回值。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationAwsClientFactory.java`

**修改目的**：删除未使用的常量 `IAM_PROPAGATION_DELAY`（值为 10000）。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`

**修改目的**：删除未使用的静态字段 `multiRegionS3Control`。

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java`

**修改目的**：删除未使用的 `LOG` 字段（含 import）与未使用的常量 `HTTP_CLIENT_PREFIX`。

**工作逻辑**：`AwsProperties` 是核心运行时类，但 `LOG` 从未被引用，说明日志输出需求已被其他位置覆盖。`HTTP_CLIENT_PREFIX = "http-client."` 也未被引用。

### `core/src/jmh/java/org/apache/iceberg/ManifestReadBenchmark.java`

**修改目的**：删除未使用的累加变量 `recordCount`。

**工作逻辑**：基准测试原本在遍历 manifest 文件时累加 `recordCount += it.next().recordCount();`，但 `recordCount` 在循环结束后从未被读取（仅用于触发迭代），属于无意义计算。改为 `it.next().recordCount();` 直接丢弃返回值，保留迭代语义。这是性能基准测试中的合理清理——避免无效累加影响 benchmark 度量。

### `core/src/main/java/org/apache/iceberg/UpdateRequirements.java`

**修改目的**：将 5 个 `update(MetadataUpdate.Xxx update)` 方法中未使用的参数 `update` 重命名为 `unused`。

**工作逻辑**：这 5 个重载方法分别处理 `AddSchema`、`SetCurrentSchema`、`AddPartitionSpec`、`SetDefaultPartitionSpec`、`SetDefaultSortOrder` 五类元数据更新事件。方法签名接收 update 参数，但方法体内只读取 `base`、`addedSchema`、`setSchemaId` 等状态字段，不使用 update 本身。由于这些方法属于内部状态机分发逻辑，参数本身有"语义占位"作用（标识处理哪类事件），不能删除，故改名为 `unused` 以满足 Error Prone 豁免规则。

### `core/src/main/java/org/apache/iceberg/actions/RewriteFileGroup.java` 与 `RewritePositionDeletesGroup.java`

**修改目的**：将 `Comparator` 默认分支中未使用的 lambda 参数重命名。

**工作逻辑**：原代码 `return (fileGroupOne, fileGroupTwo) -> 0;` 返回恒等于 0 的比较器（即"不排序"），两个参数均未使用。改为 `return (unused, unused2) -> 0;`，通过命名表达"已知不用"。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：删除两个未使用的字符串常量 `CATALOG` 与 `REFRESH_TOKEN`。

**工作逻辑**：这两个常量原可能用于 OAuth2 token 交换/刷新流程的请求体字段名，但当前代码并未引用，可能由其他重构遗留。

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：删除未使用的字段 `maxQueueSize`，并将对应的参数校验前移到构造方法开头。

**工作逻辑**：`ParallelIterator` 构造方法接收 `maxQueueSize`，原代码将其赋值给字段 `this.maxQueueSize = maxQueueSize;`，但该字段后续从未被读取——`maxQueueSize` 仅在构造 `Task` 时作为参数传入。故：
- 删除字段声明 `private final int maxQueueSize;` 与赋值语句。
- 将 `Preconditions.checkArgument(maxQueueSize > 0, ...)` 校验移到构造方法最开头（在 `tasks` 创建之前），让参数校验先发生，逻辑更合理。

### `flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java`（3 个版本同步修改）

**修改目的**：移除 `getRow` → `getStructRowData` 调用链中未使用的参数 `numFields`。

**工作逻辑**：`RowData.getRow(int pos, int numFields)` 是 Flink RowData 接口的实现，`numFields` 是接口要求必须保留的参数（不能删）。原实现将 `numFields` 透传给私有方法 `getStructRowData(int pos, int numFields)`，但私有方法内部并未使用 `numFields`（结构类型直接从 `type.fields().get(pos).type()` 获取）。故将私有方法签名简化为 `getStructRowData(int pos)`，调用处不再透传 `numFields`。

### `flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/source/RowDataRewriter.java`（3 个版本同步修改）

**修改目的**：删除内部类中 3 个未使用的字段及其赋值。

**工作逻辑**：`RowDataRewriter` 内部类原本在构造方法中接收并存储 `schema`、`nameMapping`、`caseSensitive` 三个参数到字段，但这些字段后续从未被读取（实际逻辑通过 `rowDataReader` 间接使用）。删除字段声明与构造方法中的赋值语句。注意构造方法签名本身未变（仍接收这些参数），可能是为了保持调用方兼容性。

### `mr/src/main/java/org/apache/iceberg/mr/Catalogs.java`

**修改目的**：移除私有方法 `getCatalogProperties` 中未使用的参数 `catalogType`。

**工作逻辑**：`getCatalogProperties(conf, catalogName, catalogType)` 中 `catalogType` 从未被方法体引用。将其从签名删除，并同步更新两处调用点：
- `isHiveCatalog` 方法中：`getCatalogProperties(conf, catalogName, catalogType)` → `getCatalogProperties(conf, catalogName)`。
- `getCatalog` 方法中：`getCatalogProperties(conf, name, catalogType)` → `getCatalogProperties(conf, name)`。
- 同时更新方法 Javadoc，删除 `@param catalogType` 行。

### `mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergMetaHook.java`

**修改目的**：移除私有方法 `spec` 中未使用的参数 `properties`。

**工作逻辑**：`spec(Schema schema, Properties properties, Table hmsTable)` 中 `properties` 从未被使用。删除该参数，同步更新调用点 `PartitionSpec spec = spec(schema, catalogProperties, hmsTable);` → `spec(schema, hmsTable);`。

### `orc/src/main/java/org/apache/iceberg/orc/OrcFileAppender.java`

**修改目的**：为"当前未在主代码使用、但测试在用"的字段 `conf` 添加显式说明与抑制注解。

**工作逻辑**：保留 `private final Configuration conf;` 字段，但在其上方添加 `@SuppressWarnings("unused")` 注解和注释 `// Currently used in tests TODO remove this redundant field`，并前后加空行让说明更醒目。这是面对"暂时不能删但有计划删"场景的规范做法，既让 Error Prone 不报错，也保留 TODO 提醒后续清理。

### `spark/v3.3|v3.4|v3.5/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`（3 个版本同步修改）

**修改目的**：删除 smoke test 中无意义的 `getTable()` 调用。

**工作逻辑**：原代码 `Table table = getTable();` 在表创建后立即获取表对象，但后续从未读取 `table`（后续通过 SQL 修改表结构，再重新 `getTable()` 获取最新状态）。改为 `Table table;` 仅声明变量，等待后续赋值。这避免了在表结构未定型时无意义地加载一次表元数据。

## 小结

- **成效**：清理了仓库 28 个文件中累积的未使用变量/字段/参数，并开启 `UnusedVariable:ERROR` 静态检查，使后续提交无法再引入此类问题；同时整理了部分冗余的方法签名与无意义的远程/元数据调用（如 `loadTable`、`getTable`）。
- **影响范围**：涉及 `api`、`aws`（含集成测试）、`core`、`flink`（v1.18/v1.19/v1.20）、`mr`、`orc`、`spark`（v3.3/v3.4/v3.5）多个模块；总变更 28 文件、+30/-74 行。无对外 API 变更（仅私有方法签名调整与字段删除），运行时行为不变。
- **回迁到 1.4.x 的注意事项**：
  - 这是一次纯代码卫生（hygiene）改进，**不修复任何 bug、不引入任何功能**。1.4.x 作为维护分支，原则上不强制回迁此类清理。
  - 若 1.4.x 也希望启用 `UnusedVariable:ERROR` 以保持代码卫生一致性，则需要先回迁本提交中的全部清理改动（或对 1.4.x 单独跑一次清理），否则直接开启检查会导致大量已存在的"未使用变量"报错。
  - 由于本提交修改了 `flink/v1.18|v1.19|v1.20` 三个版本的同一文件，回迁时需确认 1.4.x 当时维护的 Flink 版本范围（1.4.x 早期可能仅支持 v1.17/v1.18/v1.19，需以 1.4.x 分支实际目录为准），按需挑选对应版本回迁。
  - `OrcFileAppender.conf` 字段的 `@SuppressWarnings` 注解是 harmless 的微调，可单独回迁也可不回迁，不影响功能。
