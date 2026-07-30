# 提交 2430：Flink: Add test for adjust the configuration precedence in the Dynamic Sink (#13662)

## 提交信息

- **序号**：2430 / 4088
- **哈希**：83da920b3abcb1206559034362e08127b6954dea
- **短哈希**：83da920b3
- **日期**：2025-07-29 17:39:56 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Add test for adjust the configuration precedence in the Dynamic Sink (#13662)
- **PR/Issue**：#13662

## 总体目的

本提交为 Flink Dynamic Sink 的 `DynamicWriter` 新增了两个测试用例，用于验证写入属性（properties）的配置优先级是否正确。具体而言，验证当用户在创建 `DynamicWriter` 时显式传入 properties 时，这些 properties 应当覆盖表本身配置的属性；而当用户未传入 properties 时，则应使用表上配置的属性作为默认值。

Dynamic Sink 是 Iceberg Flink 集成中支持多表动态写入的能力。`DynamicWriter` 在写入时会根据表配置和用户传入的属性构建 appender，这些属性（如 `write.parquet.compression-codec`）会直接影响数据文件的写入行为。配置优先级的正确性对于确保用户意图被准确执行至关重要——如果优先级处理有误，可能导致压缩编码等关键写入参数不符合预期。

本提交通过反射（`DynFields`）访问 `DynamicWriter` 内部的 `writers` 字段、`BaseTaskWriter` 的 `appenderFactory` 字段以及 `FlinkAppenderFactory` 的 `props` 字段，最终读取到实际生效的属性 map 进行断言。

## 如何达成设计目的

1. 重载 `createDynamicWriter` 方法，新增接受 `Map<String, String> properties` 的版本，原方法委托给新方法并传入 `Map.of()`。
2. 新增 `testDynamicWriterPropertiesDefault`：表配置 zstd 压缩，writer 不传 properties，断言最终生效的是 zstd。
3. 新增 `testDynamicWriterPropertiesPriority`：表配置 zstd 压缩，writer 传入 gzip，断言最终生效的是 gzip（writer properties 优先）。
4. 新增 `properties(DynamicWriter)` 私有方法，利用 `DynFields` 反射逐层获取最终生效的属性 map。
5. 三个 Flink 版本（v1.19、v1.20、v2.0）的测试文件做相同修改。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+75/-2 lines)

**修改目的**：为 v1.19 版本新增配置优先级测试。

**工作逻辑**：
- 新增 import：`RowData`、`DynFields`、`FlinkAppenderFactory`、`BaseTaskWriter`、`TaskWriter`、`ImmutableMap`。
- `testDynamicWriterPropertiesDefault`：创建表时设置 `write.parquet.compression-codec=zstd`，用不传 properties 的 `createDynamicWriter(catalog)` 创建 writer 并写入一条记录，反射读取最终 props，断言压缩编码为 zstd（表默认值生效）。
- `testDynamicWriterPropertiesPriority`：表同样配置 zstd，但 `createDynamicWriter(catalog, ImmutableMap.of("write.parquet.compression-codec", "gzip"))` 传入 gzip，写入记录后反射读取 props，断言为 gzip（用户传入覆盖表配置）。
- 将原 `createDynamicWriter(Catalog)` 改为接受 `properties` 参数的重载方法，并保留无参版本委托。
- 新增 `properties(DynamicWriter)` 方法：通过 `DynFields.builder().hiddenImpl(...)` 访问私有字段 `writers` → 取出任意一个 `TaskWriter` → 访问 `BaseTaskWriter.appenderFactory` → 访问 `FlinkAppenderFactory.props`，返回该 map。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+75/-2 lines)

**修改目的**：v1.20 版本同步相同的测试改动。

**工作逻辑**：与 v1.19 完全一致的内容，仅为适配 Flink 1.20 版本目录。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+75/-2 lines)

**修改目的**：v2.0 版本同步相同的测试改动。

**工作逻辑**：与 v1.19 完全一致的内容，仅为适配 Flink 2.0 版本目录。

## 总结

本提交是纯测试增强，通过两个新测试用例验证了 DynamicWriter 的配置优先级逻辑：用户显式传入的 properties 优先于表配置。测试使用反射机制深入 writer 内部读取实际生效的属性，保证了断言的准确性。由于 Iceberg 同时维护三个 Flink 版本（1.19、1.20、2.0），三个目录的测试文件做了相同的修改。这些测试为之前实现的配置优先级逻辑提供了回归保护。
