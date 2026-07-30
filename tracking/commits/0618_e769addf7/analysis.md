# 提交 0618：Kafka Connect 引入 Record 转换器

## 提交信息

- **序号**：0618 / 4088
- **哈希**：e769addf75400e9dffb72e7e417bc752f804622a
- **短哈希**：e769addf7
- **日期**：2024-03-21 14:08:16 -0700
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Record converters (#9641)
- **PR/Issue**：#9641

## 总体目的

本提交为 Kafka Connect sink 引入核心的"记录转换器" `RecordConverter`，把 Kafka Connect 的 `SinkRecord` 数据真正转换为 Iceberg 的 `Record`，并打通"按需演进表 schema"的链路。这是 Kafka Connect 集成从"骨架可用"到"端到端可写入数据"的关键一步。

背景动机：

- 此前 `IcebergWriter.convertToRow(SinkRecord)` 直接返回 `null`，并留有 `// FIXME: update this when the record converter is added` 与 `// FIXME: add CDC operation support` 的占位注释——即 sink 仅搭好了 writer 与 schema 工具的骨架，但还没有把 Kafka 数据真正写入 Iceberg。
- Kafka Connect 上游可能送来两种形式的数据：
  1. 带 schema 的 `Struct`（典型来自 JDBC/Debezium 等带 Connect Schema 的 source）
  2. 不带 schema 的 `Map`（典型来自 JsonConverter 关闭 schema 的场景，或 JSON 字符串已被反序列化为 map）
- Iceberg 表的字段名可能与上游不同（被 rename 过），需要通过 `NameMapping`（表属性 `TableProperties.DEFAULT_NAME_MAPPING`）来回填；同时字段大小写匹配规则也应可配置。
- 当上游 schema 与 Iceberg 表 schema 不一致时（新列、类型扩宽、required→optional），sink 在开启 `iceberg.tables.evolve-schema-enabled` 时应能自动加列、改类型、放宽 nullable。

## 如何达成设计目的

整体设计围绕一个新类 `RecordConverter`（包 `org.apache.iceberg.connect.data`，包级可见）展开，配合对 `IcebergWriter` 的接线、对 `SchemaUpdate` / `SchemaUtils` / `NoOpWriter` / `PartitionedAppendWriter` 的可见性收窄，以及一个 936 行的测试类。设计要点：

1. **双入口分发**：`convert(Object data)` 与 `convert(Object data, SchemaUpdate.Consumer schemaUpdateConsumer)`。前者用于"不需要 schema 演进"的场景；后者在转换过程中收集 schema 变更，由 `IcebergWriter` 决定是否落盘并刷新表。
2. **类型分派**：核心 `convertValue(Object, Type, int fieldId, SchemaUpdate.Consumer)` 按 Iceberg `Type.TypeId` 走 switch——复合类型（STRUCT/LIST/MAP）递归调用，基本类型走专用 `convertXxx` 方法。每个基本类型转换器都接受多种输入形态（`Number`、`String`、原始包装类型、`java.util.Date`、`java.time.*` 等），兼容"带 schema 的 Struct 字段值"与"无 schema 的 Map 字段值"两种来源。
3. **字段查找三策略**：`lookupStructField(name, schema, structFieldId)` 依次尝试：
   - 有 `NameMapping`：基于 struct 的字段 id 查映射的备选名（一个字段可对应多个历史名），按备选名匹配输入字段名；缓存到 `structNameMap`，避免重复扫描。
   - 无 `NameMapping` + 大小写不敏感（`config.schemaCaseInsensitive()`）：用 `StructType.caseInsensitiveField(name)`。
   - 无 `NameMapping` + 大小写敏感：用 `StructType.field(name)`。
4. **两套 convertToStruct 实现**：
   - `convertToStruct(Map<?,?>, StructType, int, Consumer)`：用于无 schema 的 Map 输入。遍历每个 map 键值对，按名查表字段；查不到则用 `SchemaUtils.inferIcebergType(value, config)` 从值推断 Iceberg 类型并报"加列"；查到则递归 `convertValue`。
   - `convertToStruct(Struct, StructType, int, Consumer)`：用于带 schema 的 Struct 输入。遍历 `struct.schema().fields()`，按名查表字段；查不到则用 `SchemaUtils.toIcebergType(recordField.schema(), config)` 从 Connect Schema 转 Iceberg 类型并报"加列"；查到则做两步演进检查——`SchemaUtils.needsDataTypeUpdate(tableField.type(), recordField.schema())`（目前仅 FLOAT→FLOAT64 升 DOUBLE、INT32→INT64 升 LONG）和 `tableField.isRequired() && recordField.schema().isOptional()` 报"放宽为 optional"；只有"无演进需求"时才真正写值，避免写到一半再被刷新。
5. **演进回调接口 `SchemaUpdate.Consumer`**：一个轻量收集器，记录三类变更——`AddColumn(parentName, name, type)`、`UpdateType(name, PrimitiveType)`、`MakeOptional(name)`。`IcebergWriter.convertToRow` 在转换后检查 `updates.empty()`，非空则：① `flush()` 完成当前文件 → ② `SchemaUtils.applySchemaUpdates(table, updates)`（commit schema update，刷新 `Table` 引用）→ ③ `initNewWriter()` 重建 writer 与 RecordConverter（拿到新 schema）→ ④ 用新 schema 重新 `convert` 一次本条记录。这种"先收集→必要时回滚再重转"的策略，既保证不在 dirty schema 上写文件，又保证单条记录语义不丢失。
6. **类型转换细节**：
   - 数值（int/long/float/double）：接受 `Number` 与 `String`，`String` 走对应 `parseXxx`。
   - decimal：接受 `BigDecimal` / `Number` / `String`，对 `Number` 区分整数与浮点（`Math.floor` 判断）选 `BigDecimal.valueOf(long)` 或 `BigDecimal.valueOf(double)`，最后 `setScale(scale, HALF_UP)`。
   - boolean：接受 `Boolean` / `String`（`Boolean.parseBoolean`）。
   - string：接受 `String`、`Number`、`Boolean` 走 `toString`；`Map`/`List` 用 Jackson `ObjectMapper` 写 JSON；`Struct` 用 `config.jsonConverter().fromConnectData(null, struct.schema(), struct)` 转成 JSON 字符串——这样 Connect 的复杂对象在 Iceberg string 字段里也能落地。
   - uuid：接受 `String`（`UUID.fromString`）与 `UUID`。
   - binary/fixed：接受 `String`（Base64 解码）、`byte[]`、`ByteBuffer`，统一返回 `ByteBuffer`。
   - date：接受 `Number`（epoch day）、`String`（ISO `LocalDate.parse`）、`LocalDate`、`java.util.Date`（按毫秒数除以一天的毫秒换算成天数，再走 `DateTimeUtil.dateFromDays`）。
   - time：接受 `Number`（毫秒）、`String`（ISO `LocalTime.parse`）、`LocalTime`、`java.util.Date`（取毫秒），统一通过 `DateTimeUtil.timeFromMicros(millis * 1000)` 转成 `LocalTime`。
   - timestamp：按 `TimestampType.shouldAdjustToUTC()` 分流到 `convertOffsetDateTime`（带时区，返回 `OffsetDateTime`）或 `convertLocalDateTime`（不带时区，返回 `LocalDateTime`）；接受 `Number`（毫秒，乘 1000 走 `DateTimeUtil.timestamptzFromMicros` / `timestampFromMicros`）、`String`（ISO，由 `parseOffsetDateTime` / `parseLocalDateTime` 处理，`ensureTimestampFormat` 把 `'T'` 与 `' '` 互换、把 `+HH:mm` 偏移规范化为 `+HHmm` 以兼容各种 ISO 8601 写法）、`LocalDateTime`/`OffsetDateTime`（互转，UTC 作为缺省偏移）、`java.util.Date`（按毫秒换算）。
7. **可见性收窄**：本提交把 `SchemaUtils`、`SchemaUpdate`（含其内部三个子类 `AddColumn`/`UpdateType`/`MakeOptional` 与 `Consumer`）、`NoOpWriter`、`PartitionedAppendWriter` 从 `public` 改为包级（`class`/`static class`/方法去 `public`），表示这些是 Kafka Connect 内部实现细节，外部不应直接依赖；同时配套调整 `IcebergSinkConfig`、`IcebergSinkConnector`、`events/TableReference` 中无关紧要的 `static import`（`toList`/`toSet`）改回 `Collectors.toList()` / `Collectors.toSet()`，把 import 拉到包外以规避与新增 import 的冲突或提升可读性。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java`（新增，517 行）

**修改目的**：新增整个转换器实现。

**工作逻辑**：

- 构造时持有 `tableSchema`（来自 `table.schema()`）、`nameMapping`（解析 `table.properties().get(TableProperties.DEFAULT_NAME_MAPPING)`，可能为 null）、`config`，并预建一个 `Map<Integer, Map<String, NestedField>> structNameMap` 缓存。
- 入口 `convert(data)` 转发到 `convert(data, null)`；`convert(data, consumer)` 仅接受 `Struct` 或 `Map`，否则抛 `UnsupportedOperationException`，调用 `convertStructValue(data, tableSchema.asStruct(), -1, consumer)`，根 struct 的 `parentFieldId = -1` 表示"无父字段"。
- `convertValue(value, type, fieldId, consumer)` 是核心分派器：null 直返；按 `type.typeId()` 分派 STRUCT/LIST/MAP 走复合，其它走基本转换器；末尾 default 抛 `UnsupportedOperationException`。
- `convertStructValue` 选择 Map 路径或 Struct 路径；这两条路径分别对应无 schema 与有 schema 输入，行为差异在 schema 演进推断方式（值推断 vs Schema 转换）和是否做类型扩宽检查上。
- `lookupStructField` + `createStructNameMap` 实现 NameMapping 缓存：对每个 struct 字段，若 `nameMapping.find(fieldId)` 命中且 `names()` 非空，则把所有备选名都映射到该字段；否则用字段原名。`computeIfAbsent` 保证一个 struct 只构建一次。
- `convertListValue` 遍历 List，对每个元素调用 `convertValue`，元素类型为 `type.elementType()`，元素 fieldId 为 `type.fields().get(0).fieldId()`（Iceberg list element 字段的内部 id）。
- `convertMapValue` 类似，对 key/value 分别走 `convertValue`，分别用 `type.fields().get(0)` / `.get(1)` 的 fieldId。
- 各 `convertXxx` 方法均 `protected`，便于测试通过同包子类直接调用做单测（`RecordConverterTest` 即同包）。
- 时间戳相关的两个 `parseXxx` + `ensureTimestampFormat` 实现了一个宽容的 ISO 8601 解析器：先把日期与时间之间的空格替换为 `T`，再把形如 `+08:00` 的偏移冒号去掉变成 `+0800`，先按 `ISO_LOCAL_DATE_TIME + offset` 试解析，失败则按 local 解析后再加 UTC offset 兜底（或反之），从而同时容忍"带偏移字符串"和"无偏移字符串"作为输入。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriter.java`

**修改目的**：把 RecordConverter 接入 writer 主流程，实现端到端可写入与按需 schema 演进。

**工作逻辑**：

- `initNewWriter()` 中在创建 `TaskWriter<Record>` 的同时创建 `new RecordConverter(table, config)`（替换原 `// FIXME: update this when the record converter is added` 注释占位）。
- `write(SinkRecord)` 中删除 `// TODO: config to handle tombstones...` 改为简洁的 `// ignore tombstones...`——当 `record.value() == null` 时跳过写入，逻辑保持一致。
- `convertToRow(SinkRecord)` 由原"返回 null"替换为完整流程：
  - 若未开启 `config.evolveSchemaEnabled()`：直接 `recordConverter.convert(record.value())`。
  - 若开启：先建 `SchemaUpdate.Consumer`，调用 `recordConverter.convert(record.value(), updates)`；若 `!updates.empty()`，则 `flush()` 完成当前文件 → `SchemaUtils.applySchemaUpdates(table, updates)` 刷新表 schema → `initNewWriter()` 重建 writer+converter → 用新 schema 再 `recordConverter.convert(record.value(), null)` 重新转换本条记录。注意重转时 consumer 传 null，避免在同一条记录上反复触发演进。
- 这种"先试转→必要时刷新→重转"的策略保证不会把基于旧 schema 的脏数据写入新 schema 的文件，也不会因为 schema 变更而丢失当前记录。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUpdate.java`

**修改目的**：把 `SchemaUpdate` 及其内部 `Consumer`/`AddColumn`/`UpdateType`/`MakeOptional` 从 `public` 收窄为包级，方法同步去 `public`，配合 RecordConverter 作为统一入口。

**工作逻辑**：仅可见性变更，无逻辑变化。`AddColumn.key()` 仍按 `parentName == null ? name : parentName + "." + name` 拼接，作为 map 去重 key（保证同名同路径的列只报一次）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUtils.java`

**修改目的**：把 `SchemaUtils` 与其关键方法（`needsDataTypeUpdate`、`applySchemaUpdates`、`createPartitionSpec`、`toIcebergType`、`inferIcebergType` 以及内部 `SchemaGenerator.inferIcebergType`）从 `public` 收窄为包级，使 RecordConverter 成为对外唯一的转换入口。

**工作逻辑**：可见性变更为主。`needsDataTypeUpdate` 的语义保留：当前仅识别两种升级——Iceberg FLOAT 对应 Connect FLOAT64 升 DOUBLE、Iceberg INT32 对应 Connect INT64 升 LONG；其它情况返回 null 表示"无需演进"。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/Utilities.java`

**修改目的**：清理 `static import` 与一处 `// FIXME: add delta writers` 注释。

**工作逻辑**：把 `import static ... Collectors.toSet` 与 `TableProperties.DEFAULT_FILE_FORMAT` 等 4 个静态导入改成 `import org.apache.iceberg.TableProperties;` + `Collectors.toSet()` 的常规调用，统一风格；删除 `// FIXME: add delta writers` 注释（不影响代码行为）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/NoOpWriter.java` 与 `PartitionedAppendWriter.java`

**修改目的**：将 writer 实现类的可见性从 `public` 收窄为包级。

**工作逻辑**：仅 `public class` → `class`，构造器 `public` → 包级。表示这两个 writer 是包内实现细节，外部不直接构造。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`

**修改目的**：清理一处无关 `// FIXME: add config for CDC and upsert mode` 注释，并改一处 `static import toList` 为 `Collectors.toList()`。

**工作逻辑**：删除 `// FIXME: add config for CDC and upsert mode` 注释（CDC/upsert 配置未在本 PR 实现，留待后续 PR）；`splitValue` 中的 `collect(toList())` 改为 `collect(Collectors.toList())` 以与新增 import 风格保持一致。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConnector.java`

**修改目的**：与 `IcebergSinkConfig` 同步清理 `static import toList`。

**工作逻辑**：`taskConfigs` 中的 `collect(toList())` 改为 `collect(Collectors.toList())`，`import static ... Collectors.toList` 替换为 `import java.util.stream.Collectors`。

### `kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/TableReference.java`

**修改目的**：与上述清理同步，把 `static import toList` 改为 `Collectors.toList()`。

**工作逻辑**：`NAMESPACE` 字段解析时 `((List<Utf8>) v).stream().map(Utf8::toString).collect(toList())` 改为 `collect(Collectors.toList())`；顺手把三目表达式格式化为多行以提高可读性。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/RecordConverterTest.java`（新增，936 行）

**修改目的**：为 RecordConverter 提供全面单测覆盖。

**工作逻辑**：使用 JUnit 5（`org.junit.jupiter.api.Test` / `@ParameterizedTest` + `@ValueSource`）与 Mockito 模拟 `Table`/`IcebergSinkConfig`，使用 Kafka Connect `JsonConverter`（关闭 schema）作为 `config.jsonConverter()` 返回值。共 30 个测试方法，按主题分组：

- **基础转换**：`testMapConvert` / `testNestedMapConvert` / `testStructConvert` / `testNestedStructConvert` 验证 16 种 Iceberg 类型字段（int/long/date/time/timestamp/timestampz/float/double/decimal/string/boolean/uuid/fixed/binary/list/map）的 round-trip 转换。`assertRecordValues` 与 `assertNestedRecordValues` 是断言核心，覆盖所有支持的 Iceberg 类型。
- **Struct/Map 互转**：`testMapValueInListConvert` / `testMapValueInMapConvert` / `testStructValueInListConvert` / `testStructValueInMapConvert` 验证当 Iceberg 字段是 list/map 但值是相反类型时的容错（如 Iceberg list-of-struct 接收 list-of-map，逐元素转 struct）。
- **String 序列化**：`testMapToString` / `testStructToString` 验证当 Iceberg 字段是 string 但值是 Struct/Map 时，会通过 Jackson/JsonConverter 序列化为 JSON 字符串。
- **NameMapping 与大小写**：`testNameMapping` 验证通过 `TableProperties.DEFAULT_NAME_MAPPING` 配置别名后，输入 `renamed_ii` 能映射到 Iceberg 字段 `ii`；`testCaseSensitivity`（参数化，true/false）验证 `config.schemaCaseInsensitive()` 开关对 map/struct 输入的影响。
- **基本类型转换**：`testIntConversion` / `testLongConversion` / `testFloatConversion` / `testDoubleConversion` / `testDecimalConversion` / `testDateConversion` / `testTimeConversion` / `testTimestampWithZoneConversion` / `testTimestampWithoutZoneConversion` 用 `ImmutableList.of("123", 123.0f, 123.0d, 123L, 123)` 等多形态输入验证每个 `convertXxx` 都能正确处理字符串与多种数值类型。
- **Schema 演进检测**：
  - `testMissingColumnDetectionMap` / `testMissingColumnDetectionMapNested` / `testMissingColumnDetectionMapListValue` / `testMissingColumnDetectionStruct` / `testMissingColumnDetectionStructNested` / `testMissingColumnDetectionStructListValue` / `testMissingColumnDetectionStructMapValue`：验证当输入有 Iceberg 表中不存在的列时，会触发 `SchemaUpdate.Consumer.addColumn(parentName, name, type)`，且 `parentName` 能正确表达嵌套位置（如 `stli.element`、`stma.value`）。
  - `testEvolveTypeDetectionStruct` / `testEvolveTypeDetectionStructNested`：验证 FLOAT→DOUBLE、INT→LONG 的类型升级会被 `SchemaUpdate.Consumer.updateType(name, type)` 报告，嵌套字段用点分路径（如 `st.ii`）。
- **辅助方法**：`createMapData` / `createNestedMapData` / `createStructData` / `createNestedStructData` 构造标准测试数据；`assertRecordValues` / `assertNestedRecordValues` 统一断言；`assertTypesAddedFromStruct` 验证从无 schema 推断出的 16 种 Iceberg 类型正确。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/IcebergSinkConnectorTest.java`

**修改目的**：随 `static import` 清理同步调整测试断言写法。

**工作逻辑**：将 `assertThat(map).containsKey(INTERNAL_TRANSACTIONAL_SUFFIX_PROP)` 改为 `assertThat(map).containsKey(IcebergSinkConfig.INTERNAL_TRANSACTIONAL_SUFFIX_PROP)`，去掉 `static import`。

## 小结

本提交是 Kafka Connect sink 端到端写入能力的奠基性改动。在引入 `RecordConverter` 之前，sink 的 `IcebergWriter.convertToRow` 只是占位返回 null；本提交之后，sink 能真正把 Kafka 的 Struct/Map 转成 Iceberg Record 并写入 Parquet/ORC/Avro 数据文件，且在开启 schema evolution 时能自动加列、改类型、放宽 optional，并保证"先关闭当前文件→更新表 schema→重建 writer→重转记录"的顺序，避免脏写。

**影响范围**：

- 主要影响 Kafka Connect 模块（`kafka-connect/kafka-connect` 与 `kafka-connect/kafka-connect-events`），其它模块不受影响。
- 改动包含一个新生产类（`RecordConverter`，517 行）、一个新测试类（936 行）、对 writer 的接线、以及多个类的可见性收窄。
- 由于把 `SchemaUtils`、`SchemaUpdate`、`NoOpWriter`、`PartitionedAppendWriter` 从 public 改为包级，**对调用方有 ABI 影响**：任何外部直接 `new SchemaUtils()` 或调用其静态方法的代码会编译失败。但 Iceberg 的 Kafka Connect 模块对外仅暴露 `IcebergSinkConnector` 与 `IcebergSinkConfig`，所以实际外部影响为零。
- 类型演进目前仅支持 FLOAT→DOUBLE、INT→LONG 两种"安全扩宽"，其它类型变更会被忽略——这是合理的保守策略，避免误升 varchar 长度或转 decimal 精度。
- 时间戳解析的 `ensureTimestampFormat` 实现了一个相对宽容的 ISO 8601 容忍器，能处理 `'T'` 与空格、`+08:00` 与 `+0800` 等多种写法，但极端非标准格式仍会抛 `DateTimeParseException`。

**回迁到 1.4.x 的注意事项**：

1. **依赖前置**：本提交依赖 `SchemaUtils`（含 `SchemaGenerator`、`inferIcebergType`、`toIcebergType`、`needsDataTypeUpdate`、`applySchemaUpdates`）、`SchemaUpdate`、`Utilities.createTableWriter`、`IcebergSinkConfig.jsonConverter()` 与 `evolveSchemaEnabled()`、`schemaCaseInsensitive()` 等。1.4.x 上若尚未合入对应 PR（包括此前的 Kafka Connect 骨架 PR 与 schema 工具 PR），必须先回迁它们。
2. **可见性变更需整体回迁**：如果只回迁 `RecordConverter` 而不把 `SchemaUtils` 等改为包级，会出现"RecordConverter 调用包外可见的 SchemaUtils"——技术上能编译，但与上游不一致；建议整批回迁。
3. **CDC 与 upsert 未实现**：本提交删除了 `// FIXME: add config for CDC and upsert mode` 与 `// FIXME: add CDC operation support` 注释，但并未实现 CDC 操作支持；回迁后若 1.4.x 需要 CDC，需另行回迁后续相关 PR。
4. **测试框架**：`RecordConverterTest` 用 JUnit 5 原生 `@Test` / `@ParameterizedTest`，与 Iceberg 自研的 `ParameterizedTestExtension`（提交 0617 提到的）不同——因为这是非参数化 + 简单 `@ValueSource` 参数化，无需 Iceberg 的类级别参数化扩展。回迁时需确保 JUnit5 依赖已就位。
5. **行为风险**：转换器对多种输入格式做了宽容处理（String 解析、java.util.Date 兼容、ISO 时间戳归一化），这是好的用户体验但也是潜在的"隐式转换"风险点——回迁后建议在 1.4.x 上跑一遍回归测试，特别是对 timestamp/date 的边界值（如负 epoch、闰秒、LocalDateTime 与 OffsetDateTime 互转）。
6. **维护分支价值高**：1.4.x 作为维护分支，若使用者已经在用 Iceberg Kafka Connect sink，回迁本提交能让 sink 真正可用，价值显著；但若 1.4.x 上的 Kafka Connect 仍处于骨架阶段，回迁成本（依赖链 + 测试）需评估。
