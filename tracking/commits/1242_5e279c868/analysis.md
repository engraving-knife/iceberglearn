# 提交 1242：Build, Spark, Flink: Bump junit from 5.10.1 to 5.11.1 (#11262)

## 提交信息

- **序号**：1242 / 4088
- **哈希**：5e279c868f8e2087f88ae779d8cc8474768bc5e2
- **短哈希**：5e279c868
- **日期**：2024-10-16（Wed Oct 16 21:32:51 2024 +0900）
- **作者**：Tom Tanaka <43331405+tomtongue@users.noreply.github.com>
- **提交说明**：Build, Spark, Flink: Bump junit from 5.10.1 to 5.11.1 (#11262)
- **PR/Issue**：#11262

## 总体目的

将 Iceberg 项目使用的 JUnit 5 测试框架从 5.10.1 升级到 5.11.1。版本升级后 JUnit 5 在 `ParameterizedTestExtension`（Iceberg 自定义的参数化测试扩展）以及 `@Parameter`/`@Parameters` 注解的字段查找行为上发生变化，导致部分继承体系下的测试在 5.11.x 上不再可用，因此提交同时调整自定义扩展和受影响的测试用例，使升级后的测试套件可以继续通过。

具体而言，JUnit 5.11 调整了 `AnnotationSupport.findAnnotatedFields` / `findAnnotatedMethods` 默认的层级遍历行为，使得子类与父类中同名字段或方法的解析顺序与 5.10 不一致；原扩展在检测到多个 `@Parameters` 方法时直接抛 `IllegalStateException`，在新版本下会误判失败。本提交把遍历模式显式改为 `BOTTOM_UP`、放宽多 provider 限制，并修正多处测试类以适配新的字段顺序与生命周期注解要求。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中将 `junit` 版本号由 `5.10.1` 改为 `5.11.1`。
2. 在 `ParameterizedTestExtension` 中将字段与方法的查找改为 `HierarchyTraversalMode.BOTTOM_UP`，并移除"多于一个 `@Parameters` 即报错"的限制，改为取列表中第一个。
3. 修正受影响的测试类，主要包括：
   - 将 `@Parameter(index = 0)` 调整到正确的索引位置，避免与父类字段冲突；
   - 把 `@Parameters` 名称模板对齐到新的参数顺序；
   - 把字符串形式的 `format` 改为 `FileFormat` 枚举，统一参数类型；
   - 在重写的 `before()`/`createTables()` 上补加 `@BeforeEach` 注解，符合 JUnit 5.11 对生命周期方法的要求；
   - 在 `TestAggregatePushDown` 与 `ExtensionsTestBase` 中先关闭旧的 `SparkSession` 再重建，避免 5.11 下重复创建 Spark session 报错。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 JUnit 5 版本。

**工作逻辑**：将依赖版本条目 `junit = "5.10.1"` 改为 `junit = "5.11.1"`。`junit-platform` 仍保持 `1.11.2` 不变。

### `api/src/test/java/org/apache/iceberg/ParameterizedTestExtension.java`

**修改目的**：适配 JUnit 5.11 的注解查找行为。

**工作逻辑**：
- `findAnnotatedMethods` 的遍历模式由 `TOP_DOWN` 改为 `BOTTOM_UP`，使子类的 `@Parameters` 方法优先于父类被命中。
- 删除 `if (parameterProviders.size() > 1) throw new IllegalStateException(...)` 的强校验，改为直接取 `parameterProviders.get(0)`。原因是在继承体系下，父子类可能各自声明 `@Parameters`，5.11 会同时收集到多个；按 BOTTOM_UP 顺序第一个就是最具体子类的版本。
- `findAnnotatedFields` 同样显式传入 `HierarchyTraversalMode.BOTTOM_UP`，确保子类重写的 `@Parameter` 字段优先被注入。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Branch.java`（以及 v1.19、v1.20 中的同名文件）

**修改目的**：适配新的参数顺序与可见性。

**工作逻辑**：
- 原本 `@Parameter(index = 0) private String branch;` 改为 `@Parameter(index = 4) protected String branch;`，因为父类 `TestFlinkIcebergSinkV2Base` 已经占用 index 0-3（FileFormat、Parallelism、Partitioned、WriteDistributionMode）。
- `@Parameters` 名称模板从 `"branch = {0}"` 改为 `"FileFormat={0}, Parallelism={1}, Partitioned={2}, WriteDistributionMode={3}, Branch={4}"`，便于调试时识别每个参数组合。
- `parameters()` 返回值从单元素数组改为 5 元素数组，与父类参数表对齐（FileFormat.AVRO, 1, false, WRITE_DISTRIBUTION_MODE_NONE, branch）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkV2Branch.java`（v1.20 同名文件同步修改）

**修改目的**：去除子类中重复的 `@Parameter`/`@Parameters` 声明，避免与父类冲突。

**工作逻辑**：删除子类自定义的 `branch` 字段与 `parameters()` 方法，让继承自 `TestFlinkIcebergSinkV2Branch`（来自 flink 共享模块）的参数配置生效；在 `before()` 上补 `@Override` 注解。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceSql.java` 与 `TestIcebergSourceSql.java`（v1.19、v1.20 同步修改）

**修改目的**：补全 JUnit 5.11 要求的生命周期注解。

**工作逻辑**：在重写的 `before()` 方法上添加 `@BeforeEach` 注解并导入 `org.junit.jupiter.api.BeforeEach`。JUnit 5.11 不再像旧版本那样隐式识别重写的 `@Before` 风格方法，必须显式标注才会被调用。

### `mr/src/test/java/org/apache/iceberg/mr/TestInputFormatReaderDeletes.java`

**修改目的**：修正参数顺序，使 `format` 字段在 index 0，`inputFormat` 字段在 index 1。

**工作逻辑**：
- 移除 `@Parameter private String inputFormat;`（默认 index 0），改为 `@Parameter(index = 1) private String inputFormat;`，并把 `fileFormat` 从 `@Parameter(index = 1)` 提升到默认 index 0。
- `@Parameters` 名称模板与二维数组都按"fileFormat 在前、inputFormat 在后"重新排列。
- `createTable` 等方法中引用的 `fileFormat` 字段名改为继承自父类的 `format` 字段。

### `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 下的 `TestSparkReaderDeletes.java`

**修改目的**：把 `format` 字段类型从 `String` 改为 `FileFormat` 枚举，统一类型并修复字段顺序。

**工作逻辑**：
- 删除子类自定义的 `@Parameter private String format;`，让继承自 `DeleteReadTests` 的 `format` 字段（`FileFormat` 类型）注入。
- 参数数组中 `"parquet"`/`"orc"`/`"avro"` 改为 `FileFormat.PARQUET`/`FileFormat.ORC`/`FileFormat.AVRO`。
- 设置表属性时用 `format.name()` 转字符串；判断分支用 `format.equals(FileFormat.PARQUET)` 等。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ExtensionsTestBase.java`

**修改目的**：避免重复创建 SparkSession 导致 5.11 下报错。

**工作逻辑**：在重建 `TestBase.spark` 之前先调用 `TestBase.spark.close()`，确保旧的 SparkSession 被释放。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：与 `ExtensionsTestBase` 一致，重建 SparkSession 前先关闭旧实例。

**工作逻辑**：在 `TestBase.spark = SparkSession.builder()...` 之前补一行 `TestBase.spark.close();`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestUnpartitionedWritesToBranch.java`

**修改目的**：补全 `@BeforeEach` 注解。

**工作逻辑**：在重写的 `createTables()` 方法上添加 `@BeforeEach` 注解，导入对应包。

## 小结

- **成效**：将 JUnit 5 升级到 5.11.1，使测试框架保持与新版本一致；同时修正自定义 `ParameterizedTestExtension` 以适配新版本对继承体系中注解查找的行为变化，并修复一系列测试类的字段索引、参数类型与生命周期注解。
- **影响范围**：仅影响测试代码与构建依赖版本，不改变产品运行时行为。涉及 `api`、`flink v1.18/v1.19/v1.20`、`mr`、`spark v3.3/v3.4/v3.5` 等多个模块的测试文件。
- **回迁到 1.4.x 的注意事项**：
  - 该提交本质是依赖升级加测试适配，1.4.x 若仍使用 JUnit 5.10.x 则无需回迁；如 1.4.x 也希望升级到 5.11.x，则需**整提交回迁**，并且必须同时回迁 `ParameterizedTestExtension` 的修改和所有受影响测试类的修改，否则会出现参数注入失败、`@BeforeEach` 不被调用、SparkSession 重复创建等问题。
  - 由于修改跨越多个 Spark/Flink 版本目录，回迁时要确保 1.4.x 分支对应版本目录都存在；若 1.4.x 不支持某个 Flink/Spark 版本，可只回迁存在的那部分。
  - 注意 `junit-platform` 版本（`1.11.2`）需与 `junit`（`5.11.1`）兼容。
