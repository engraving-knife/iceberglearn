# 提交 2674：Flink: add _row_id and _last_updated_sequence_number readers (#14148)

## 提交信息

- **序号**：2674 / 4088
- **哈希**：6829c3e3db31c4881556998a2d87b129aa9a8654
- **短哈希**：6829c3e3d
- **日期**：2025-09-23 15:11:43 +0200
- **作者**：GuoYu
- **提交说明**：Flink: add _row_id and _last_updated_sequence_number readers (#14148)
- **PR/Issue**：#14148

## 总体目的

本提交为 Flink 2.0 的 Parquet 读取器添加对两个 Iceberg 元数据列——`_row_id`（行唯一标识）和 `_last_updated_sequence_number`（最后更新的序列号）的读取支持。这两个元数据列是 Iceberg v2/v3 格式中行级溯源（row lineage）特性的重要组成部分：`_row_id` 为每一行分配全局唯一标识，`_last_updated_sequence_number` 记录行最后一次被更新所属的快照序列号。

在此提交之前，Flink Parquet 读取器 `FlinkParquetReaders` 中对元数据列的处理是硬编码的，只覆盖了 `ROW_POSITION` 和 `IS_DELETED` 两种，没有对接 core 模块新增的 `ParquetValueReaders.replaceWithMetadataReader()` 统一机制。这意味着当用户在 Flink 中投影包含 `_row_id` 或 `_last_updated_sequence_number` 的 schema 时，读取器无法正确填充这些列的值。

本提交通过重构 `FlinkParquetReaders.struct()` 方法，将元数据列的 reader 创建委托给 core 的 `ParquetValueReaders.replaceWithMetadataReader()`，从而自动获得对 `_row_id` 和 `_last_updated_sequence_number` 的支持，同时简化了 Flink 侧的代码。

## 如何达成设计目的

整体思路是"收敛"——将 Flink 读取器中分散的元数据列处理逻辑替换为对 core 统一方法的调用，并提取普通字段处理的公共逻辑为独立方法。具体包括：
1. 在 `struct()` 方法中，对每个期望字段先调用 `ParquetValueReaders.replaceWithMetadataReader()` 将元数据列的 reader 替换为正确的实现（由 core 负责），再对非元数据字段调用新的 `defaultReader()` 辅助方法处理。
2. 移除不再需要的 `typesById`、`maxDefinitionLevelsById` 局部映射，改为一次性计算 `constantDefinitionLevel`。
3. 增强 `TestHelpers` 的断言逻辑以支持 `_row_id`（按行位置递增）和 `_last_updated_sequence_number`（常量）的期望值计算。
4. 测试改用 `InMemoryOutputFile` 并启用 `supportsRowLineage()`。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+60/-50 lines, 净 +10 但重写较多)

**修改目的**：重构 struct 读取器，委托元数据列处理给 core，新增对 `_row_id` 和 `_last_updated_sequence_number` 的支持。

**工作逻辑**：
- 移除了对 `MetadataColumns` 的 import（不再在 Flink 侧硬编码元数据列判断）。
- `struct()` 方法开头新增 null 检查：当 `expected` 为 null 时返回空的 `RowDataReader`，防御性处理。
- 移除了 `typesById` 和 `maxDefinitionLevelsById` 两个局部 Map，简化为只保留 `readersById`。常量定义级别改为一次性计算 `int constantDefinitionLevel = type.getMaxDefinitionLevel(currentPath())`。
- 字段遍历逻辑重构：对每个 `expectedFields` 中的字段，先通过 `ParquetValueReaders.replaceWithMetadataReader(id, readersById.get(id), idToConstant, constantDefinitionLevel)` 获取 reader——该方法会判断该 id 是否为元数据列（包括 `_row_id`、`_last_updated_sequence_number`、`ROW_POSITION`、`IS_DELETED` 等），若是则返回对应的专用 reader，若否则返回原 reader。然后调用 `defaultReader(field, reader, constantDefinitionLevel)` 处理 reader 为 null 的情况（初始默认值、optional null、或 required 缺失报错）。
- 新增 `defaultReader()` 私有方法：封装"reader 存在则返回 reader；否则用 initialDefault 创建常量 reader；否则若 optional 返回 nulls reader；否则抛出缺失必填字段异常"的逻辑。这取代了原先内联在 struct() 中的复杂 if-else 链。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` (+56/-14 lines)

**修改目的**：增强测试断言以支持 `_row_id` 和 `_last_updated_sequence_number` 元数据列。

**工作逻辑**：
- 新增 `assertRowData` 重载方法，接受 `Map<Integer, Object> idToConstant` 和 `int rowPosition` 参数，原有的两参数版本委托给新方法（传 null 和 -1）。
- 新增 `getExpectedValue()` 私有方法：根据字段 id 计算期望值。对于 `MetadataColumns.ROW_ID`（`_row_id`），若 expected record 中无值则用 `idToConstant` 中的基础值加上行位置 `pos` 计算（因为 _row_id 是按行递增的）；对于 `MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER`，若 expected record 中无值则直接取 `idToConstant` 中的常量值；其他字段取 expected record 的字段值。
- 将原先内联的 expectedField 判断逻辑（区分有值字段和 initialDefault 字段）统一为通过 `getExpectedValue` 获取期望值后再调用 `assertEquals`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+25/-16 lines)

**修改目的**：启用行溯源测试并适配新的读取接口。

**工作逻辑**：
- 新增 `supportsRowLineage()` 覆盖方法返回 `true`，使基类 `DataTestBase` 的行溯源相关测试用例在本测试中生效。
- `writeAndValidate` 方法改用 `InMemoryOutputFile` 替代临时文件，避免磁盘 IO 并简化测试。
- 读取时调用 `FlinkParquetReaders.buildReader(expectedSchema, type, ID_TO_CONSTANT)` 传入常量映射（`ID_TO_CONSTANT` 来自 `DataTestBase`，提供元数据列的测试常量值）。
- 断言调用改为 `TestHelpers.assertRowData(writeSchema.asStruct(), rowType, record, rows.next(), ID_TO_CONSTANT, pos++)`，传入常量映射和行位置以正确验证 `_row_id` 和 `_last_updated_sequence_number`。

## 总结

本提交为 Flink 2.0 Parquet 读取器补齐了 `_row_id` 和 `_last_updated_sequence_number` 两个行溯源元数据列的读取能力。通过将 Flink 侧的元数据列处理逻辑委托给 core 模块的统一方法 `ParquetValueReaders.replaceWithMetadataReader()`，既新增了功能又简化了 Flink 代码，避免了重复维护元数据列的映射逻辑。同时增强和重构了测试基础设施，使行溯源相关测试能在 Flink 读取器中运行。这是 Iceberg 行溯源特性在 Flink 引擎集成上的重要一环。
