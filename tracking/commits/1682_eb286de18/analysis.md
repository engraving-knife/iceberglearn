# 提交 1682 eb286de18 分析

## 提交信息
- 哈希：eb286de183fd5c78604280f83cf7a807ea75a6b4
- 日期：2025-02-04 12:58:02 -0800
- 作者：Ryan Blue
- 消息：Variants: Implement toString (#12138)

## 总体目的

本提交为 Iceberg 中新引入的 Variant 类型系统（位于 `core/src/main/java/org/apache/iceberg/variants/` 包）补充 `toString()` 实现。在此提交之前，Variant 相关接口的实现类没有重写 `toString()`，默认会使用 `Object.toString()` 输出类似 `org.apache.iceberg.variants.SerializedObject@1a2b3c4` 这样的无意义信息，既不便于调试，也不便于日志输出和测试断言。

Ryan Blue 作为 Variant 功能的主要作者，在落地 Variant 模块的同时逐步完善其可观测性。本提交通过在三个核心接口（`VariantMetadata`、`VariantObject`、`VariantPrimitive`）中提供静态 `asString(...)` 工具方法，并让所有具体实现类在 `toString()` 中调用这些方法，统一了 Variant 值的字符串表示形式。

这是一个纯增强性提交，不改变任何运行时语义或序列化格式，仅用于改善开发与调试体验，并为后续把 Variant 接口迁移到 API 模块（见下一个提交 1683）做铺垫——接口里提供的静态方法可以在任何实现中复用。

## 如何达成设计目的

设计思路是"接口提供工具方法 + 实现类委托调用"：

1. 在三个接口（`VariantMetadata`、`VariantObject`、`VariantPrimitive`）中各自新增一个 `static String asString(...)` 静态方法，封装该类型的字符串构造逻辑。由于是接口静态方法，可以被任意实现复用，避免在每个实现类中重复逻辑。
2. 在 `VariantPrimitive` 中还新增了一个 `private` 实例方法 `valueAsString()`，针对不同原始类型（DATE、TIMESTAMPTZ、TIMESTAMPNTZ、BINARY 等）做格式化，保证输出可读（如日期转 ISO 格式、二进制转 hex）。
3. 各具体实现类（`PrimitiveWrapper`、`SerializedMetadata`、`SerializedObject`、`SerializedPrimitive`、`SerializedShortString`、`ShreddedObject`）只需在 `toString()` 中调用对应接口的 `asString(this)` 即可，代码极其简洁，每处只增 5 行。

### 修改详情

#### core/src/main/java/org/apache/iceberg/variants/VariantPrimitive.java
这是本提交中最有实质内容的文件。新增了导入 `ByteBuffer`、`BaseEncoding`、`ByteBuffers`、`DateTimeUtil`，并新增一个 `private` 实例方法 `valueAsString()` 和一个 `static asString()` 方法。

`valueAsString()` 工作逻辑：通过 `type()` 判定 Variant 原始类型，对 DATE 用 `DateTimeUtil.daysToIsoDate` 把自纪元起的天数转成 ISO 日期字符串；对 TIMESTAMPTZ 用 `microsToIsoTimestamptz` 把微秒值转成带时区的 ISO 时间戳；对 TIMESTAMPNTZ 用 `microsToIsoTimestamp` 转成无时区时间戳；对 BINARY 用 `BaseEncoding.base16()` 把字节缓冲区编码为 hex 字符串；其余类型直接 `String.valueOf(get())`。

`asString()` 则把类型和格式化后的值拼成 `Variant(type=..., value=...)` 形式。

#### core/src/main/java/org/apache/iceberg/variants/VariantObject.java
新增 `static asString(VariantObject object)` 方法。遍历 `object.fieldNames()`，把每个字段名和对应的 `object.get(field)` 拼接成 `VariantObject(fields={name1: value1, name2: value2})` 形式。使用 `first` 标志位控制分隔符。

#### core/src/main/java/org/apache/iceberg/variants/VariantMetadata.java
新增 `static asString(VariantMetadata metadata)` 方法。通过 `dictionarySize()` 遍历字典，按索引 `i` 取出 `metadata.get(i)`，拼成 `VariantMetadata(dict={0 => v0, 1 => v1})` 形式。这里把字典序号显式输出，便于调试时对照字段 ID 与字典条目。

#### core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java
新增 `toString()`，委托给 `VariantPrimitive.asString(this)`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java
新增 `toString()`，委托给 `VariantMetadata.asString(this)`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedObject.java
新增 `toString()`，委托给 `VariantObject.asString(this)`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java
新增 `toString()`，委托给 `VariantPrimitive.asString(this)`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedShortString.java
新增 `toString()`，委托给 `VariantPrimitive.asString(this)`。

#### core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java
新增 `toString()`，委托给 `VariantObject.asString(this)`。

## 小结

成效：Variant 模块从此具备清晰、可读的字符串表示，对调试、日志、单元测试断言均有直接帮助；尤其对 DATE/TIMESTAMP/BINARY 等类型做了语义化格式化，避免输出原始数值。影响范围仅限 `core` 模块的 variants 包，无对外 API 行为变更，无序列化格式变更。

回迁到 1.4.x 的注意事项：1.4.x 分支是否已包含 Variant 模块是回迁的前提。如果 1.4.x 尚未引入 variants 包（Variant 是较新特性），则本提交无回迁意义，需要连同整个 Variant 模块一起引入。若已包含 variants 包但缺少 toString，则本提交可干净地 cherry-pick，无依赖冲突，因为只新增方法不改原有逻辑。需注意 `DateTimeUtil`、`ByteBuffers`、`BaseEncoding` 等工具类在 1.4.x 中是否存在且签名一致。
