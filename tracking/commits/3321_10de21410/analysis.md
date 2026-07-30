# 提交 3321：Flink: Backport SQL support for dynamic iceberg sink (#15444)

## 提交信息

- **序号**：3321 / 4088
- **哈希**：10de21410833f379197e3fe125e7bc73ccd546e4
- **短哈希**：10de21410
- **日期**：2026-02-27
- **作者**：Swapna Marru
- **提交说明**：Flink: Backport SQL support for dynamic iceberg sink (#15444)
- **PR/Issue**：#15444（回移自 #15279）

## 总体目的

本提交是把"动态 Iceberg Sink"特性从 main 分支（已落地于 Flink 2.1）回移（backport）到仍在维护的 Flink 1.20 与 Flink 2.0 两个版本模块。该特性解决的核心问题是：原有 Flink Iceberg sink 是"静态"的——一个 sink 实例在编译期绑定到一张固定的 Iceberg 表（通过 `TableLoader`），无法在运行时把流入的记录动态分发到不同的目标表。而在数据路由、多表写入等场景下，用户希望由单个 SQL 表（带 `use-dynamic-iceberg-sink=true`）作为一个"动态 sink"，根据每条记录的内容决定它应写入哪张 Iceberg 表，甚至按需创建目标表。

为此该特性引入了一个用户可扩展的扩展点 `DynamicTableRecordGenerator`：用户实现该类，把每条 `RowData` 转换为一个 `DynamicRecord`（携带目标表标识、schema、分区、要写入的记录等信息）；sink 在运行时调用它完成路由与记录生成，并通过 `DynamicIcebergSink` 把记录写到对应的目标表。回移到 1.20 与 2.0 的目的，是让这两个尚未升级到 2.1 的 Flink 用户群也能使用该能力，保持各 Flink 版本间的功能一致性。

## 如何达成设计目的

设计上在 SQL 表选项层面新增两个开关：`use-dynamic-iceberg-sink`（布尔，默认 false）与 `dynamic-record-generator-impl`（字符串，生成器实现类全限定名）。`FlinkDynamicTableFactory.createDynamicTableSink` 据此分流：开启时走新的动态 sink 构建路径，构造 `CatalogLoader`（而非单一 `TableLoader`）与生成器类名，传入新的 `IcebergTableSink` 构造器；否则走原有静态 sink 路径。`IcebergTableSink.consumeDataStream` 中据 `useDynamicSink` 标志分流：动态路径用反射（`DynConstructors`）实例化用户提供的生成器类，构造 `TableCreator`（按 `table.props.` 前缀属性与 `location` 创建目标表），再通过 `DynamicIcebergSink.Builder` 组装并 append；静态路径则把原先内联的 v1/v2 sink 构建逻辑抽取为 `createLegacySink`/`createIcebergSink` 辅助方法，并修正了 `resolvedSchema` 过滤物理列的位置（原先在 factory 中过滤，现移至 sink 内）。新增 `DynamicTableRecordGenerator` 抽象基类供用户继承并持有 `RowType`。1.20 与 2.0 两套代码改动一致。

## 修改详情

### `flink/v1.20/...` 与 `flink/v2.0/...` 下 `FlinkCreateTableOptions.java` (+18/-0 lines，各版本)

**修改目的**：新增动态 sink 的两个 SQL 表选项。

**工作逻辑**：
新增 `USE_DYNAMIC_ICEBERG_SINK`（key `use-dynamic-iceberg-sink`，boolean，默认 false）与 `DYNAMIC_RECORD_GENERATOR_IMPL`（key `dynamic-record-generator-impl`，string，无默认值）。前者描述是否启用动态 sink 路由到多表、需配合后者使用；后者描述启用的 `DynamicTableRecordGenerator` 实现类名。

### `flink/v1.20/...` 与 `flink/v2.0/...` 下 `FlinkDynamicTableFactory.java` (+72/-39 lines，各版本)

**修改目的**：在 factory 中按选项分流静态/动态 sink 构建，并重构校验与 catalog 加载。

**工作逻辑**：
`createDynamicTableSink` 中先把表选项装入 `Configuration`，读取 `USE_DYNAMIC_ICEBERG_SINK`。若为 true，调用新增的 `getIcebergTableSinkWithDynamicSinkProps`：校验 `dynamic-record-generator-impl` 非空，构造 `CatalogLoader`（catalog 存在时直接取 `catalog.getCatalogLoader()`，否则通过新增的 `createCatalogLoader(...)` 用 `FlinkCatalogFactory` 创建），再以 `CatalogLoader` + 生成器类名 + `resolvedCatalogTable.getResolvedSchema()` 构造 `IcebergTableSink`。若为 false，走原静态路径，但把传入 sink 的 schema 由原先"过滤物理列后的 `ResolvedSchema`"改为完整 `resolvedCatalogTable.getResolvedSchema()`（物理列过滤移到 sink 内部统一处理）。`optionalOptions()` 新增两个选项。`createCatalogLoader` 抽取为静态方法供两处复用，并把原先 `Preconditions.checkNotNull` 的消息改为 `checkArgument` 形式并附选项名提示。`createTableLoader(ResolvedCatalogTable,...)` 内部改为调用 `createCatalogLoader`，删除冗余的 null 校验。

### `flink/v1.20/...` 与 `flink/v2.0/...` 下 `IcebergTableSink.java` (各版本，+约200/-约40 lines)

**修改目的**：支持动态 sink 路径，并重构静态 sink 构建逻辑。

**工作逻辑**：
新增字段 `catalogLoader`、`dynamicRecordGeneratorImpl`、`useDynamicSink`，并在拷贝构造器中复制。新增构造器 `(CatalogLoader, String dynamicRecordGeneratorImpl, ResolvedSchema, ReadableConfig, Map)`，置 `useDynamicSink=true`、`tableLoader=null`；原有构造器补 `catalogLoader=null`/`dynamicRecordGeneratorImpl=null`。`consumeDataStream` 重写为统一的 `DataStreamSinkProvider` lambda：先判断 `useDynamicSink` 走 `createDynamicIcebergSink`；否则构造 `physicalColumnsOnlySchema`（仅当 `resolvedSchema!=null` 时过滤 `Column::isPhysical`），按 `TABLE_EXEC_ICEBERG_USE_V2_SINK` 分派到 `createIcebergSink`（IcebergSink v2）或 `createLegacySink`（FlinkSink）。
- `createLegacySink`/`createIcebergSink`：用 builder 模式组装，按是否有 `physicalColumnsOnlySchema` 选择 `resolvedSchema(...)` 或 `tableSchema(...)`，统一 `equalityFieldColumns`、`overwrite`、`setAll(writeProps)`、`flinkConf(readableConfig)`。
- `createDynamicIcebergSink`：校验 `catalogLoader` 与 `dynamicRecordGeneratorImpl` 非空，调用 `createTableCreator()` 与 `createDynamicRecordGenerator(...)`，再用 `DynamicIcebergSink.forInput(dataStream).generator(...).catalogLoader(...).setAll(writeProps).tableCreator(...).flinkConf(readableConfig).append()` 构建动态 sink。
- `createTableCreator`：从 `writeProps` 取 `table.props.` 前缀属性与 `location`，返回一个按 `catalog/identifier/schema/spec` 创建目标表（`buildTable(...).withPartitionSpec(spec).withLocation(location).withProperties(tableProperties).create()`）的 lambda。
- `createDynamicRecordGenerator`：由 `resolvedSchema.toSourceRowDataType().getLogicalType()` 取 `RowType`，用 `DynConstructors.builder(DynamicTableRecordGenerator.class).loader(...).impl(generatorImpl, RowType.class).buildChecked()` 反射查找并实例化用户类（接受 `RowType` 构造参数），并对 `ClassCastException`/其他异常给出明确报错。

### `flink/v1.20/...` 与 `flink/v2.0/...` 下 `sink/dynamic/DynamicTableRecordGenerator.java` (新增, +39 lines，各版本)

**修改目的**：提供用户可继承的 SQL 动态记录生成器抽象基类。

**工作逻辑**：
`DynamicTableRecordGenerator` 继承 `DynamicRecordGenerator<RowData>`，在构造时持有 `RowType` 并通过 `rowType()` 暴露给子类。用户继承它并实现 `generate`（来自接口）以把 `RowData` 转成 `DynamicRecord`。把它放在 `sink.dynamic` 包下，与 `DynamicIcebergSink`、`DynamicRecord` 等同模块。

### `flink/v1.20/...` 与 `flink/v2.0/...` 下 `TestIcebergConnector.java` (+155/-15 lines，各版本)

**修改目的**：覆盖动态 sink 的端到端写入与缺生成器时的校验报错。

**工作逻辑**：
新增 `testCreateDynamicIcebergSink`：建表时设置 `use-dynamic-iceberg-sink=true`、`dynamic-record-generator-impl=SimpleRowDataTableRecordGenerator`、并带 `table.props.key1=val1`；通过 SQL 插入三行（每行携带 `database_name`/`table_name` 列指向同一目标库表），验证目标表被自动创建、属性 `key1` 存在，并随后读取目标表内容断言三行正确写入。新增 `testMissingDynamicRecordGeneratorImpl`：开启动态 sink 但不指定生成器实现，断言 insert 抛出 `IllegalArgumentException` 且消息提示 `dynamic-record-generator-impl must be specified`。内嵌测试类 `SimpleRowDataTableRecordGenerator` 继承 `DynamicTableRecordGenerator`，在 `open` 中按字段名定位 `database_name`/`table_name` 索引，在 `collect` 中据行内容构造指向目标表的 `DynamicRecord`。参数化测试配置中补充 `catalog-database` 等选项以适配新路径。

## 总结

本提交把动态 Iceberg Sink 特性从 main 回移到 Flink 1.20 与 2.0 两个版本模块，使这两个版本的 Flink 用户也能通过 `use-dynamic-iceberg-sink` 表选项启用单 sink 多表动态路由能力。改动覆盖表选项声明、factory 分流、sink 构建重构（含静态路径的整理）以及用户扩展点 `DynamicTableRecordGenerator`，并附带端到端与异常路径测试。核心价值在于扩展了 Flink Iceberg 的写入灵活性，并在多个 Flink 版本间保持功能对齐。
