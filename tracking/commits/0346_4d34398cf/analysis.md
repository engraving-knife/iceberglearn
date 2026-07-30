# 提交分析：Flink: Watermark read options (#9346)

## 提交信息

- 哈希：4d34398cfd32465222f55df522fcd5a2db59c92c
- 短哈希：4d34398cf
- 日期：2024-01-09 18:22:29 +0100
- 作者：Rodrigo (Rodrigo <rmenesespinillos@apple.com>)
- 说明：Flink: Watermark read options (#9346)

## 总体目的

本提交的核心目的是将 Flink Iceberg Source 中原本仅能通过 Java Builder API 配置的 watermark（水位线）相关参数 `watermarkColumn` 与 `watermarkTimeUnit` 暴露为标准的"读选项"（read options），使其能够通过 SQL Hints、SQL SET 语法、Flink Table Config 等基于字符串配置的方式被用户使用。这一改动呼应了 Iceberg Flink 集成向"声明式 / SQL-first"演进的整体方向——即把 Builder 上一切有意义的可调参数都下沉到 `FlinkReadOptions` / `FlinkReadConf` 体系中，让 SQL 用户与 Java API 用户享有对等的能力。

在改造之前，watermark 列只能通过 `IcebergSource.Builder#watermarkColumn(String)` 这种编程式接口设置，使用 SQL 的下游用户无法触及该能力。由于 watermark 列与 OrderedSplitAssignerFactory 紧密相关（用于按列统计值对 split 排序，进而影响 watermark 生成与对齐），缺少 SQL 入口意味着 SQL 用户无法驱动这一关键的流式读取顺序优化。本提交通过把这两个参数纳入 `FlinkReadOptions`（声明 `ConfigOption`）、`FlinkReadConf`（提供解析入口）以及 `ScanContext`（携带到运行时），并让 `IcebergSource.Builder` 把这些值写入 `readOptions` map，实现了"统一配置源"。

更深层的目的是统一配置优先级与一致性。原本 Builder 中存在私有字段 `watermarkColumn` / `watermarkTimeUnit`，它们与 `readOptions` map 中其他选项走的是两条不同的代码路径，容易产生"Builder 字段与 readOptions 不一致"的问题（例如 `assignerFactory(...)` 方法中曾硬编码检查 `watermarkColumn == null`，但该字段在 SQL 路径下永远为 null，导致检查失效）。本次改造消除了 Builder 中的私有字段，改为通过 `FlinkReadConf` 统一从 `readOptions` + `flinkConfig` 解析，使校验逻辑（"watermark 列与自定义 SplitAssigner 不能同时设置"）真正在所有路径上都生效。

附带地，提交还完善了测试基础设施：在 `TestHelpers` 中抽出 `convertRecordToRow` 私有方法并新增 `assertRecordsWithOrder` / `assertRowsWithOrder` 两个工具方法，区分"有序断言"与"无序断言"；并在 `TestIcebergSourceSql` 中新增两个端到端 SQL 测试用例，覆盖 watermark 列为 timestamp（升序）与 long（降序，带 time-unit）两种典型场景。

## 如何达成设计目的

设计上采取"三段式"贯通：1) 在 `FlinkReadOptions` 中用 `ConfigOptions.key(...)` 声明两个新的 `ConfigOption`，使其自动获得 Flink 配置体系的支持（包括 SQL hint、table config、flink-conf 等）；2) 在 `FlinkReadConf` 中新增 `watermarkColumn()` / `watermarkColumnTimeUnit()` 两个解析方法，复用 `confParser` 链式 DSL，其中 watermark-column 用 `parseOptional()`（默认 null 表示不启用），time-unit 用 `enumConfParser(TimeUnit.class)` 限定合法取值；3) 在 `ScanContext` 中新增两个字段并在 `copyWithAppendsBetween` / `copyWithSnapshot` 等"派生 ScanContext"的方法中传递它们，保证流式增量扫描时 watermark 配置不丢失。同时 `IcebergSource.Builder` 在 `ifControls`（即 `if (watermarkColumn != null)`）处改为先构造 `FlinkReadConf` 解析读选项，再据此决定是否收集 column stats 与覆盖 `OrderedSplitAssignerFactory`，从而把 Builder 内部存储迁移到统一的 readOptions map 上。

## 修改详情

### docs/flink-configuration.md
**修改目的**：在 Flink 读取选项文档表中补充 `watermark-column` 与 `watermark-column-time-unit` 两行说明。
**工作逻辑**：明确写出两个选项的 connector key（`connector.iceberg.watermark-column` / `connector.iceberg.watermark-column-time-unit`）、默认值（`null` / `TimeUnit.MICROSECONDS`）以及语义——当 watermark-column 存在时会用 `OrderedSplitAssignerFactory` 覆盖默认的 split assigner；time-unit 接受 DAYS/HOURS/MINUTES/SECONDS/MILLISECONDS/MICROSECONDS/NANOSECONDS 七个枚举值。同时移除文档中多余的空行。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkReadOptions.java
**修改目的**：声明两个新的读选项常量与 `ConfigOption`。
**工作逻辑**：
- `WATERMARK_COLUMN = "watermark-column"`，对应 `ConfigOption<String>` 使用 `stringType().noDefaultValue()`，即默认无值（表示不启用 watermark 列）。
- `WATERMARK_COLUMN_TIME_UNIT = "watermark-column-time-unit"`，对应 `ConfigOption<TimeUnit>` 使用 `enumType(TimeUnit.class).defaultValue(TimeUnit.MICROSECONDS)`，默认微秒（与 Iceberg 内部 timestamp 的存储精度一致）。
- 引入 `import java.util.concurrent.TimeUnit;`。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkReadConf.java
**修改目的**：为两个新选项提供解析方法，作为 `readOptions` / `flinkConfig` 与 `ScanContext` 之间的桥梁。
**工作逻辑**：新增 `watermarkColumn()` 与 `watermarkColumnTimeUnit()`。前者用 `stringConf().option(...).flinkConfig(...).defaultValue(...).parseOptional()`——`parseOptional` 表示未配置时返回 null；后者用 `enumConfParser(TimeUnit.class)` 解析，自动校验用户输入的字符串是否为合法的 TimeUnit 枚举名，错误时抛出明确异常。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java
**修改目的**：把 Builder 上的 watermark 设置迁移到 readOptions map，并修正对应的校验逻辑。
**工作逻辑**：
- 移除 Builder 的私有字段 `watermarkColumn` / `watermarkTimeUnit`，并新增 `import FlinkReadConf`。
- `watermarkColumn(String)` 方法不再赋值给字段，而是 `readOptions.put(FlinkReadOptions.WATERMARK_COLUMN, columnName)`；同理 `watermarkColumnTimeUnit(TimeUnit)` 改为 `readOptions.put(FlinkReadOptions.WATERMARK_COLUMN_TIME_UNIT, timeUnit.name())`（注意写入的是枚举的 `name()` 字符串）。
- `assignerFactory(SplitAssignerFactory)` 方法移除了原先对 `watermarkColumn` 字段的检查（因为该字段已不存在，且这种顺序检查在 Builder 链式调用下并不可靠）。
- 在 `build()` 方法末尾，新增 `FlinkReadConf flinkReadConf = new FlinkReadConf(table, readOptions, flinkConfig);` 并通过 `flinkReadConf.watermarkColumn()` / `watermarkColumnTimeUnit()` 取得解析后的值，再保留原有的 `if (watermarkColumn != null)` 逻辑（收集 column stats、覆盖 assigner factory）。这样无论 watermark 列是通过 Builder 方法还是 SQL 选项设置的，都能进入同一段处理逻辑。
- 同时把方法名从 `watermarkTimeUnit` 改为 `watermarkColumnTimeUnit`，命名更精确（区分"watermark 列的 time unit"与未来可能的其他 time unit），并修正 Javadoc 引用。

### flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java
**修改目的**：把 watermark 列与 time-unit 纳入 ScanContext，作为运行期扫描上下文的一部分在 Source / Enumerator / Split 之间传递。
**工作逻辑**：
- 新增字段 `watermarkColumn` 与 `watermarkColumnTimeUnit`，并在私有构造器中赋值。
- 提供公开 getter `watermarkColumn()` / `watermarkColumnTimeUnit()`。
- 在 `copyWithAppendsBetween` 与 `copyWithSnapshot`（流式增量扫描场景下派生新 ScanContext 的两个方法）中通过 Builder 透传这两个字段，避免在增量扫描时丢失 watermark 配置。
- 在 Builder 中新增 `watermarkColumn(String)` / `watermarkColumnTimeUnit(TimeUnit)` 设置方法，默认值取自 `FlinkReadOptions.*_OPTION.defaultValue()`（即 null 与 MICROSECONDS）。
- 在 `resolveConfig(Table, readOptions, readableConfig)` 中调用 `flinkReadConf.watermarkColumn()` / `flinkReadConf.watermarkColumnTimeUnit()` 写入 Builder，使从 readOptions 解析出的值最终进入 ScanContext。
- 在 `build()` 中把这两个值传给私有构造器。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java
**修改目的**：抽取公共转换逻辑并新增"有序断言"工具，支持 watermark 排序场景的测试。
**工作逻辑**：
- 把原 `assertRecords` 中"Record → Row"的转换逻辑抽到私有方法 `convertRecordToRow(List<Record>, Schema)`，返回 `List<Row>`，被两个 assert 方法复用。
- `assertRecords` 改为调用 `convertRecordToRow` 后调用 `assertRows`（后者使用 `containsExactlyInAnyOrder`，无序比对）。
- 新增 `assertRecordsWithOrder` 调用 `convertRecordToRow` 后调用 `assertRowsWithOrder`。
- 新增 `assertRowsWithOrder` 使用 AssertJ 的 `containsExactlyElementsOf`，要求顺序与数量完全一致——这是 watermark 排序测试的关键，因为只有顺序对了才能证明 OrderedSplitAssignerFactory 生效。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java
**修改目的**：通过端到端 SQL 测试验证 watermark 读选项的生效。
**工作逻辑**：
- 定义 schema `SCHEMA_TS`：字段 `t1` 为 `TimestampType.withoutZone()`、`t2` 为 `LongType.get()`，分别用于测试 timestamp 列与 long 列两种 watermark 来源。
- `before()` 中除了开启 FLIP-27 source 与 catalog，还把 `table.exec.resource.default-parallelism` 设为 1，避免多并行度下 split 分配顺序不确定干扰断言。
- `generateRecord(Instant, long)` 构造一条记录；`generateExpectedRecords(boolean ascending)` 构造两个数据文件，每个文件 2 条记录，并刻意让两个文件的 t1/t2 的最值分布在不同文件上（file1 的 t1 较早，file2 的 t2 较小）。`ascending=true` 时返回 file1 + file2（按 t1 升序排），`ascending=false` 时返回 file2 + file1（按 t2 升序排，但因 t2 是 long，需配合 time-unit=MILLISECONDS）。
- `testWatermarkOptionsAscending` 通过 SQL hint `watermark-column=t1` + `split-file-open-cost=128000000`（大值避免小文件合并）运行查询，用 `assertRecordsWithOrder` 校验顺序。
- `testWatermarkOptionsDescending` 同时设置 `watermark-column=t2` 与 `watermark-column-time-unit=MILLISECONDS`，验证 long 列配合 time-unit 也能驱动排序。

## 小结

本提交是 Flink Iceberg Source 配置体系标准化的一步：把 watermark 列与时间单位从 Builder 私有字段迁移到统一的 `FlinkReadOptions` / `FlinkReadConf` / `ScanContext` 三层结构，使 SQL 用户与 Java API 用户获得对等能力，并修复了原先 Builder 字段导致校验逻辑失效的隐患。配套的有序断言工具与端到端 SQL 测试覆盖了 timestamp 升序与 long+time-unit 两种典型场景，是后续 watermark alignment / split ordering 等流式优化的基础。
