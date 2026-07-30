# 提交 3303：Flink: SQL support for dynamic iceberg sink (#15279)

## 提交信息

- **序号**：3303 / 4088
- **哈希**：86defcebeeff70c0621adae86d2cb9252ab6f31f
- **短哈希**：86defcebe
- **日期**：2026-02-23
- **作者**：Swapna Marru
- **提交说明**：Flink: SQL support for dynamic iceberg sink (#15279)
- **PR/Issue**：#15279

## 总体目的

该提交为 Iceberg 的 Flink 集成（v2.1）新增了"动态 Iceberg Sink"的 SQL 支持，使得用户可以通过 Flink SQL 的 `CREATE TABLE ... WITH` 语法，使用单个 sink 将数据流动态路由到多个不同的 Iceberg 目标表。在传统（静态）sink 模式下，一个 Flink sink 只能写入一个预先确定的 Iceberg 表；而动态 sink 模式下，sink 根据每条记录的内容决定其应写入的目标表（数据库和表名），实现"一进多出"的路由能力。

这种动态路由能力在以下场景中非常有价值：例如 CDC 数据同步场景下，需要将一个上游数据流按业务规则分发到不同的下游表中；或者日志处理场景下，根据日志类型字段路由到不同的归档表。在没有动态 sink 时，用户需要为每个目标表创建独立的 sink 和独立的查询，导致作业拓扑复杂、资源浪费。动态 sink 通过用户提供的 `DynamicRecordGenerator` 实现类来定义路由逻辑，使单个 Flink 作业即可完成多表写入。

提交的核心设计是引入两个表选项：`use-dynamic-iceberg-sink`（布尔开关，默认 false）和 `dynamic-record-generator-impl`（用户实现的记录生成器类的全限定名）。当启用动态 sink 时，`FlinkDynamicTableFactory` 不再走传统的单表 `TableLoader` 路径，而是走基于 `CatalogLoader` 的动态路径，将 `CatalogLoader` 和生成器实现类名传递给 `IcebergTableSink`，后者在 `getSinkRuntimeProvider` 中通过反射实例化用户生成器并构建 `DynamicIcebergSink`。同时，该提交还对 `FlinkDynamicTableFactory` 和 `IcebergTableSink` 进行了重构优化，提取公共方法、统一校验逻辑并改善错误提示。

## 如何达成设计目的

整体设计分为四个方面：

1. **配置层**：在 `FlinkCreateTableOptions` 中新增两个 `ConfigOption`，作为用户通过 SQL 表属性开启动态 sink 的开关和指定路由逻辑实现类。

2. **工厂层**：`FlinkDynamicTableFactory.createDynamicTableSink` 增加分支判断——当 `use-dynamic-iceberg-sink` 为 true 时调用新方法 `getIcebergTableSinkWithDynamicSinkProps`，构造 `CatalogLoader` 并创建带动态参数的 `IcebergTableSink`；否则走原有静态 sink 路径。同时将 `requiredOptions` 注册新选项，重构 `createTableLoader` 提取 `createCatalogLoader` 公共方法，并将部分 `checkNotNull` 改为 `checkArgument` 提供更清晰的错误信息。

3. **Sink 层**：`IcebergTableSink` 新增第三个构造器（接收 `CatalogLoader`、生成器类名等），并在 `getSinkRuntimeProvider` 中根据 `useDynamicSink` 标志分发到 `createDynamicIcebergSink` 或原有的静态 sink 路径。原有的两条分支（resolvedSchema vs tableSchema、V2 sink vs legacy sink）被重构为 `createLegacySink` 和 `createIcebergSink` 两个私有方法，并统一了物理列过滤逻辑。动态路径通过反射实例化用户生成器、构造 `TableCreator` 并构建 `DynamicIcebergSink`。

4. **抽象层**：新增 `DynamicTableRecordGenerator` 抽象基类，封装 `RowType` 供用户实现类使用，降低用户扩展的样板代码。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCreateTableOptions.java` (+18/-0 lines)

**修改目的**：新增动态 sink 相关的表选项定义。

**工作逻辑**：
新增两个 `ConfigOption`：
- `USE_DYNAMIC_ICEBERG_SINK`：key 为 `use-dynamic-iceberg-sink`，布尔类型，默认 false。描述说明启用后单个 sink 可基于 `DynamicRecordGenerator` 实现动态路由记录到不同 Iceberg 表，需配合 `dynamic-record-generator-impl` 使用。
- `DYNAMIC_RECORD_GENERATOR_IMPL`：key 为 `dynamic-record-generator-impl`，字符串类型，无默认值。描述说明为动态 sink 启用时用户实现的 `DynamicTableRecordGenerator` 类。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableRecordGenerator.java` (+39/-0 lines)

**修改目的**：为 SQL 场景提供记录生成器的抽象基类。

**工作逻辑**：
新增抽象类 `DynamicTableRecordGenerator`，实现 `DynamicRecordGenerator<RowData>` 接口，持有 `RowType rowType` 字段并提供 `rowType()` 保护方法。用户通过继承该类并实现 `generate(RowData, Collector<DynamicRecord>)` 方法来定义路由逻辑——将输入的 `RowData` 转换为携带目标表标识（`TableIdentifier`）、schema、分区规格等信息的 `DynamicRecord`。封装 `RowType` 使子类无需自行处理类型信息获取。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkDynamicTableFactory.java` (+60/-39 lines)

**修改目的**：在工厂层增加动态 sink 分支并重构公共逻辑。

**工作逻辑**：
- `createDynamicTableSink` 方法重构：先从 `writeProps` 构造 `Configuration`，读取 `USE_DYNAMIC_ICEBERG_SINK` 标志。为 true 时调用 `getIcebergTableSinkWithDynamicSinkProps` 走动态路径；为 false 时走原静态路径。注意静态路径中 `resolvedSchema` 的物理列过滤逻辑从工厂移到了 `IcebergTableSink`（现在直接传入完整 `resolvedCatalogTable.getResolvedSchema()`）。
- 新增 `getIcebergTableSinkWithDynamicSinkProps`：校验 `dynamic-record-generator-impl` 非空，根据 `catalog` 是否存在选择 `catalog.getCatalogLoader()` 或新方法 `createCatalogLoader` 构造 `CatalogLoader`，最终创建带动态参数的 `IcebergTableSink`。
- 新增 `createCatalogLoader`：从 `FlinkCatalogFactory` 创建 `FlinkCatalog`，校验 catalog 名非空。该方法被动态路径和原 `createTableLoader(ResolvedCatalogTable, ...)` 复用，消除了重复的工厂创建代码。
- `requiredOptions` 注册两个新选项。
- 原 `createTableLoader` 中的 `checkNotNull` 改为 `checkArgument` 并附带更明确的提示信息（如 "Set ... create table option or specify fully qualified table name"），改善用户排错体验。移除了冗余的 null 检查（如 `createTableLoader(FlinkCatalog, ObjectPath)` 中的 catalog 非空检查）。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/IcebergTableSink.java` (+169/-64 lines)

**修改目的**：支持动态 sink 并重构静态 sink 的分支逻辑。

**工作逻辑**：
- 新增 `catalogLoader`、`dynamicRecordGeneratorImpl`、`useDynamicSink` 字段，并在拷贝构造器中同步。原有两个构造器补充 `catalogLoader = null`、`dynamicRecordGeneratorImpl = null` 初始化。新增第三个构造器接收 `CatalogLoader`、生成器类名、`ResolvedSchema` 等，设置 `useDynamicSink = true`、`tableLoader = null`。
- `getSinkRuntimeProvider` 大幅重构：原四路分支（resolvedSchema/tableSchema × V2/legacy）合并为统一的 `DataStreamSinkProvider` lambda，内部分发——`useDynamicSink` 为 true 调用 `createDynamicIcebergSink`，否则统一计算物理列 schema 和 equality columns 后调用 `createIcebergSink`（V2）或 `createLegacySink`（V1）。
- 新增 `createLegacySink`：构建 `FlinkSink.Builder`，根据 `physicalColumnsOnlySchema` 是否为 null 选择 `resolvedSchema` 或 `tableSchema`。
- 新增 `createIcebergSink`：同上但使用 `IcebergSink.Builder`（V2 sink）。
- 新增 `createDynamicIcebergSink`：校验 `catalogLoader` 和生成器类名非空，调用 `createTableCreator` 构造表创建器，调用 `createDynamicRecordGenerator` 反射实例化生成器，最终用 `DynamicIcebergSink.forInput` 构建 sink。
- 新增 `createTableCreator`：从 `writeProps` 提取 `table.props.` 前缀属性和 `location`，返回一个 lambda，在目标表不存在时通过 `catalog.buildTable` 创建表（带分区 spec、location 和属性）。
- 新增 `createDynamicRecordGenerator`：从 `resolvedSchema` 获取 `RowType`，使用 `DynConstructors` 反射加载用户类（构造器参数为 `RowType.class`）并实例化。捕获 `ClassCastException`（类未实现接口）和通用异常，转换为带清晰提示的 `IllegalArgumentException`/`RuntimeException`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestIcebergConnector.java` (+155/-1 lines)

**修改目的**：验证动态 sink 的端到端功能和错误处理。

**工作逻辑**：
- 参数化测试数据补充了 `catalog-database` 属性（`test_database`）。
- `testCreateDynamicIcebergSink`：创建带 `use-dynamic-iceberg-sink=true`、`dynamic-record-generator-impl` 指向自定义生成器和 `table.props.key1=val1` 的表，通过 SQL INSERT 三条带数据库/表名信息的记录，验证目标表被自动创建、表属性 `key1=val1` 正确写入、数据正确路由并可查询。
- `testMissingDynamicRecordGeneratorImpl`：验证启用动态 sink 但未指定生成器实现类时，INSERT 抛出 `IllegalArgumentException` 且消息提示需指定 `dynamic-record-generator-impl`。
- 内部类 `SimpleRowDataTableRecordGenerator`：继承 `DynamicTableRecordGenerator`，在 `open` 中根据字段名定位 `database_name` 和 `table_name` 列索引，在 `generate` 中提取这两个字段构造 `TableIdentifier`，并定义目标表的 schema（id、data），输出 `DynamicRecord`。

## 总结

本次提交为 Flink SQL 集成实现了动态多表路由 sink 能力，通过两个表选项开关和用户可扩展的 `DynamicTableRecordGenerator`，使单个 Flink 作业能根据记录内容动态写入多个 Iceberg 表。同时对工厂和 sink 类进行了重构，统一了校验逻辑和错误提示，并将静态 sink 的分支逻辑提取为独立方法提升可读性。配套测试覆盖了正常路由和缺失生成器的错误场景，验证了表自动创建和属性传递。这显著扩展了 Flink 集成在 CDC 同步、多目标写入等场景下的实用性。
