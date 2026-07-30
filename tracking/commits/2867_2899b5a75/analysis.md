# 提交 2867：API, Core: Introduce classes for content stats (#13933)

## 提交信息

- **序号**：2867 / 4088
- **哈希**：2899b5a75106c698ea8e59fe0b93c4857acaadee
- **短哈希**：2899b5a75
- **日期**：2025-11-12 07:47:53 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Introduce classes for content stats (#13933)
- **PR/Issue**：#13933

## 总体目的

这个提交引入了内容统计（content stats）的类层次结构，为 Iceberg 表的列级统计数据提供了类型化的 API。这是一个新功能的基础设施提交，为未来在清单文件（manifest）中存储更丰富的字段级统计信息奠定基础。

Iceberg 当前在数据文件级别存储了基本的指标（如记录数、文件大小、列级上下界等），但这些统计信息以 Map/ByteBuffer 形式存储，缺乏类型安全和结构化访问能力。这个提交引入了一套完整的接口和实现类，使字段统计信息可以通过类型安全的方式访问，并定义了字段 ID 的映射规则，将数据列的 field ID 映射到统计空间的 field ID。

这是 Iceberg 规范 v3 相关功能的基础工作，旨在改进统计信息的存储和查询效率。

## 如何达成设计目的

整体设计分为 API 层和 Core 层：

**API 层**定义了接口和工具类：
1. `ContentStats` 接口：表示内容统计的顶层接口，包含多个 FieldStats。
2. `FieldStats<T>` 接口：单个字段的统计信息接口，包含值计数、空值计数、NaN 计数、平均值大小、最大值大小、上下界等。
3. `FieldStatistic` 枚举：定义了支持的统计类型及其在结构中的偏移量。
4. `StatsUtil` 工具类：处理字段 ID 到统计字段 ID 的映射，以及从 Schema 生成统计 schema。

**Core 层**提供了实现类：
1. `BaseContentStats`：ContentStats 的基础实现。
2. `BaseFieldStats`：FieldStats 的基础实现，使用 Builder 模式。

字段 ID 映射是设计的核心：数据字段的 ID 从 10000 开始，每个字段预留 200 个统计 ID 空间；保留字段（metadata fields）的统计 ID 从 2147000000 开始。

## 修改详情

### `api/src/main/java/org/apache/iceberg/stats/ContentStats.java` (+41/-0 lines, new file)

**修改目的**：定义内容统计的顶层接口。

**工作逻辑**：`ContentStats` 继承 `StructLike`，提供三个方法：`fieldStats()` 返回所有字段统计列表，`statsFor(int fieldId)` 按字段 ID 获取特定字段的统计信息，`statsStruct()` 返回统计的结构类型定义。

### `api/src/main/java/org/apache/iceberg/stats/FieldStatistic.java` (+110/-0 lines, new file)

**修改目的**：定义字段统计类型枚举和统计 schema 生成方法。

**工作逻辑**：定义了 8 种统计类型枚举：VALUE_COUNT（值计数）、NULL_VALUE_COUNT（空值计数）、NAN_VALUE_COUNT（NaN 计数）、AVG_VALUE_SIZE（平均大小）、MAX_VALUE_SIZE（最大大小）、LOWER_BOUND（下界）、UPPER_BOUND（上界）、EXACT_BOUNDS（是否精确边界）。每个枚举值有偏移量和字段名。`fieldStatsFor(Type type, int fieldId)` 方法根据字段类型和起始 ID 生成包含所有统计字段的 StructType。

### `api/src/main/java/org/apache/iceberg/stats/FieldStats.java` (+54/-0 lines, new file)

**修改目的**：定义单字段统计信息接口。

**工作逻辑**：`FieldStats<T>` 继承 `StructLike`，泛型 T 表示字段值的类型。提供 fieldId()、type()、valueCount()、nullValueCount()、nanValueCount()、avgValueSize()、maxValueSize()、lowerBound()、upperBound()、hasExactBounds() 等方法。

### `api/src/main/java/org/apache/iceberg/stats/StatsUtil.java` (+195/-0 lines, new file)

**修改目的**：提供字段 ID 映射和统计 schema 生成工具。

**工作逻辑**：

1. **字段 ID 映射**：定义了字段 ID 空间映射规则：
   - 数据字段：ID 从 10000 开始，每个字段预留 200 个统计 ID（`STATS_SPACE_FIELD_ID_START_FOR_DATA_FIELDS = 10_000`，`NUM_SUPPORTED_STATS_PER_COLUMN = 200`）。
   - 保留字段（metadata fields）：ID 从 2147000000 开始。
   - `statsFieldIdForField(int fieldId)` 方法将数据字段 ID 映射到统计空间 ID。
   - `fieldIdForStatsField(int statsFieldId)` 方法执行反向映射。

2. **ContentStatsSchemaVisitor**：内部类，继承 `TypeUtil.SchemaVisitor`，遍历 Schema 生成统计 schema。对于简单类型字段生成对应的 FieldStats struct；对于嵌套类型（struct、list、map）递归处理叶子字段；跳过 variant 类型。最终生成 field ID 为 146 的 `content_stats` 嵌套字段。

### `api/src/test/java/org/apache/iceberg/stats/TestStatsUtil.java` (+214/-0 lines, new file)

**修改目的**：测试 StatsUtil 的字段 ID 映射和统计 schema 生成。

**工作逻辑**：包含 5 个测试：
- `statsIdsForTableColumns`：验证数据字段的 ID 映射正确性（0->10000, 1->10200, 2->10400 等）。
- `statsIdsOverflowForTableColumns`：验证超出范围的字段 ID 返回 -1。
- `statsIdsForReservedColumns`：验证保留字段的 ID 映射正确性。
- `contentStatsForSimpleSchema`：验证简单 schema 的统计 schema 生成。
- `contentStatsForComplexSchema`：验证包含 list、struct、map、variant 的复杂 schema 的统计 schema 生成。

### `core/src/main/java/org/apache/iceberg/stats/BaseContentStats.java` (+259/-0 lines, new file)

**修改目的**：提供 ContentStats 的基础实现。

**工作逻辑**：`BaseContentStats` 实现 `ContentStats` 接口并实现 `Serializable`。包含两个构造函数：一个接收 `Types.StructType projection` 用于 Avro 反射读取清单文件时实例化；另一个私有构造函数用于 Builder 创建。维护 `fieldStats` 列表和 `fieldStatsById` 映射，支持按字段 ID 查询统计信息。提供了 Builder 模式用于构建实例。

### `core/src/main/java/org/apache/iceberg/stats/BaseFieldStats.java` (+318/-0 lines, new file)

**修改目的**：提供 FieldStats 的基础实现。

**工作逻辑**：`BaseFieldStats` 实现 `FieldStats<T>` 接口并实现 `Serializable`。包含所有统计字段的 getter 方法和 StructLike 接口实现。提供 Builder 模式用于构建实例，支持从现有 FieldStats 复制和修改。

### `core/src/test/java/org/apache/iceberg/stats/TestContentStats.java` (+306/-0 lines, new file)

**修改目的**：测试 BaseContentStats 的功能。

**工作逻辑**：测试 BaseContentStats 的构建、字段查询、序列化等行为。

### `core/src/test/java/org/apache/iceberg/stats/TestFieldStats.java` (+214/-0 lines, new file)

**修改目的**：测试 BaseFieldStats 的功能。

**工作逻辑**：测试 BaseFieldStats 的构建、属性访问、StructLike 接口实现等行为。

## 总结

这个提交引入了 Iceberg 内容统计（content stats）的完整类层次结构，包括 API 层的接口定义（ContentStats、FieldStats、FieldStatistic）和工具类（StatsUtil），以及 Core 层的实现类（BaseContentStats、BaseFieldStats）。核心设计是字段 ID 映射机制，将数据列 ID 映射到统计空间 ID，每列预留 200 个统计 ID 空间。这是 Iceberg v3 规范相关功能的基础设施工作，为未来在清单文件中存储更丰富的字段级统计信息奠定基础。提交共新增 9 个文件、1711 行代码，包含完整的接口定义、实现和测试。
