# 提交 1970：Flink: Backport using ExternalTypeInfo in Rowconverter code instead of deprecated TableSchema.getFieldTypes (#12739)

## 提交信息

- **序号**：1970 / 4088
- **哈希**：a0194e26a2c8f693bb6004acdc22a17be370bcc2
- **短哈希**：a0194e26a
- **日期**：2025-04-07 12:09:54 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport using ExternalTypeInfo in Rowconverter code instead of deprecated TableSchema.getFieldTypes (#12739) / backport of #11838
- **PR/Issue**：#12739（backport of #11838）

## 总体目的

本提交是 #11838 的反向移植（backport），目的是替换 Flink `RowConverter` 中已废弃的 `TableSchema.getFieldTypes()` 调用，改用 `ExternalTypeInfo` 来构建 `RowTypeInfo`，以消除对已弃用 API 的依赖。

`TableSchema.getFieldTypes()` 在较新的 Flink 版本中已被标记为 `@Deprecated`，因为它返回的是 `TypeInformation[]`，而 Flink 的类型系统已经迁移到以 `DataType` 为核心的模型。继续使用该废弃方法会在编译时产生弃用警告，并可能在未来的 Flink 版本中被移除，导致代码无法编译。

通过改用 `TableSchema.getFieldDataTypes()` 获取字段的 `DataType` 数组，再用 `ExternalTypeInfo.of(DataType)` 将其转换为 `TypeInformation`，既保留了原有的运行时行为，又使用了 Flink 推荐的现代 API。该修改同时应用到 Flink 1.18 和 1.19 两个版本的模块。

## 如何达成设计目的

设计思路是用等价的现代 API 替换废弃 API：

1. 从 `TableSchema` 获取字段数据类型数组 `getFieldDataTypes()`（非废弃方法）。
2. 通过 `Stream.of(...).map(ExternalTypeInfo::of).toArray(...)` 将每个 `DataType` 转换为对应的 `TypeInformation`。
3. 用得到的 `TypeInformation[]` 和 `getFieldNames()` 构造 `RowTypeInfo`，与原逻辑等价。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java` (修改, +9/-2 lines)

**修改目的**：替换废弃的 `TableSchema.getFieldTypes()` 调用。

**工作逻辑**：
- 新增 `java.util.stream.Stream` 和 `org.apache.flink.table.runtime.typeutils.ExternalTypeInfo` 两个 import。
- 在 `fromIcebergSchema` 方法中，将原 `new RowTypeInfo(tableSchema.getFieldTypes(), tableSchema.getFieldNames())` 替换为：先通过 `Stream.of(tableSchema.getFieldDataTypes()).map(ExternalTypeInfo::of).toArray(TypeInformation[]::new)` 得到类型信息数组，再构造 `RowTypeInfo`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowConverter.java` (修改, +9/-2 lines)

**修改目的**：与 1.18 模块相同的修改。

**工作逻辑**：与上述 1.18 模块的修改完全一致，保证两个 Flink 版本的行为统一。

## 总结

本提交将 Flink `RowConverter` 中对已废弃 `TableSchema.getFieldTypes()` 的调用替换为基于 `getFieldDataTypes()` + `ExternalTypeInfo.of()` 的现代写法，消除弃用警告并保证未来 Flink 版本兼容性。修改在 Flink 1.18 和 1.19 两个模块同步应用。
