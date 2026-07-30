# 提交 3146：Flink: Test Parquet writer default handling via core's DataTestBase (#15123)

## 提交信息

- **序号**：3146 / 4088
- **哈希**：7493a158b38953a16672718bcb964d8b8b8ce34b
- **短哈希**：7493a158b
- **日期**：2026-01-23 17:13:36 +0100
- **作者**：Maximilian Michels
- **提交说明**：Flink: Test Parquet writer default handling via core's DataTestBase (#15123)
- **PR/Issue**：#15123

## 总体目的

Apache Iceberg 的核心模块 `core` 中提供了测试基类 `DataTestBase`，它定义了一组面向数据读写器的通用测试。其中包含一组与"字段默认值（default value）"相关的测试用例，例如 `testMissingRequiredWithoutDefault`、`testDefaultValues`、`testNullDefaultValue`、`testNestedDefaultValue` 等。这些测试通过 `assumeThat(supportsDefaultValues()).isTrue()` 来决定是否执行，默认实现 `supportsDefaultValues()` 返回 `false`，意味着各引擎读写器子类需要主动声明自己支持默认值，并实现 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 这个新的写入/读取不一致 schema 的入口，才能让默认值测试真正跑起来。

在此提交之前，Flink 的 `TestFlinkParquetWriter`（位于 `flink/v2.1/flink`）虽然继承了 `DataTestBase`，但没有覆盖 `supportsDefaultValues()`，也没有实现区分"写入 schema"与"期望（读取）schema"的写校验方法，因此默认值相关用例对 Flink Parquet 写入器是被跳过的，存在测试盲区。本提交的目标就是让 Flink Parquet 写入器接入核心的默认值测试体系，验证 Flink 写出的 Parquet 数据在读取端能够正确处理 schema 演进中新增的带默认值字段。

此外，本提交还顺手清理了原有的 `writeAndValidate` 实现：旧代码会借助 Flink 的 `RowDataSerializer` 将 `RowData` 转成 `BinaryRowData` 再写入，这一额外步骤与默认值测试无关，反而掩盖了真实写入路径的行为；新代码直接通过 `RandomRowData.convert` 由 `Record` 生成 `RowData` 后写入，使测试更贴近真实写入链路，并简化了导入。

## 如何达成设计目的

整体思路是：让 `TestFlinkParquetWriter` 声明 `supportsDefaultValues()` 返回 `true`，并将原私有方法 `writeAndValidate(Iterable<RowData>, Schema)` 重构为一个三参数的 `writeAndValidate(Schema writeSchema, Schema expectedSchema, List<Record> data)`，使"写入用 writeSchema、读取/校验用 expectedSchema"得以分离；再分别覆盖 `DataTestBase` 中的 `writeAndValidate(Schema)`、`writeAndValidate(Schema, List<Record>)` 和新增的 `writeAndValidate(Schema, Schema)` 三个入口，统一汇入新的三参数实现。改动只涉及单个测试文件 `TestFlinkParquetWriter.java`。

## 修改详情

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java` (+33/-31 lines)

**修改目的**：让 Flink Parquet 写入器测试接入核心默认值测试框架，并简化写入路径。

**工作逻辑**：

1. **启用默认值测试**：新增覆盖方法 `protected boolean supportsDefaultValues()` 返回 `true`。这使得 `DataTestBase` 中所有用 `assumeThat(supportsDefaultValues()).isTrue()` 守卫的默认值用例对 Flink 真正生效。

2. **重构核心写校验方法**：将原来的私有 `writeAndValidate(Iterable<RowData> iterable, Schema schema)` 改为 `writeAndValidate(Schema writeSchema, Schema expectedSchema, List<Record> data)`。关键变化在于写入与读取采用不同的 schema：
   - 写入端使用 `writeSchema`：`Parquet.write(outputFile).schema(writeSchema)` 并以 `FlinkSchemaUtil.convert(writeSchema)` 构建写入函数；数据通过 `RandomRowData.convert(writeSchema, data)` 从 `Record` 转换得到。
   - 读取端使用 `expectedSchema`：`Parquet.read(...).project(expectedSchema)` 并用 `GenericParquetReaders.buildReader(expectedSchema, fileSchema)` 构建读取器。
   - 校验时把读出的 `Record` 经 `RowDataConverter.convert(expectedSchema, actual.next())` 转成 `RowData`，再用 `TestHelpers.assertRowData(expectedSchema.asStruct(), rowType, expected, actualRowData)` 与原始 `Record` 比对。
   这种 writeSchema/expectedSchema 分离正是默认值测试的核心：写入时 schema 缺少某些字段，读取时 expectedSchema 包含带默认值的新字段，从而验证写入器/读取器对默认值的处理。

3. **统一三个入口**：
   - `writeAndValidate(Schema schema)` 仍生成随机数据、字典可编码数据、降级数据三组用例，但数据源统一改用 `RandomGenericData.generate(...)` 生成 `Record`，再传入新三参数方法。
   - `writeAndValidate(Schema schema, List<Record> data)` 简化为直接委托 `writeAndValidate(schema, schema, data)`，删除了原先用 `RowDataSerializer.toBinaryRow` 把数据转成 `BinaryRowData` 的逻辑。
   - 新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 入口，生成随机数据后委托三参数方法。

4. **清理导入**：移除不再使用的 `BinaryRowData` 和 `RowDataSerializer` 导入，新增 `RowType`（用于替换原来的 `LogicalType` 局部变量类型，使代码更精确）。

## 总结

本提交通过让 Flink Parquet 写入器测试声明支持默认值并实现 schema 分离的写校验方法，补齐了 Flink 在字段默认值场景下的测试覆盖，使核心 `DataTestBase` 中的默认值用例对 Flink 2.1 模块生效；同时移除了 `BinaryRowData`/`RowDataSerializer` 的中转转换，让测试更直接地覆盖真实写入路径，提升了测试的准确性与可维护性。
