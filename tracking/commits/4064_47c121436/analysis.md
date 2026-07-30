# 提交 4064：API, Core: Make variant classes serializable. (#17260)

## 提交信息

- **序号**：4064 / 4088
- **哈希**：47c121436e36bb331188e91da20fc4bb9265999b
- **短哈希**：47c12143
- **日期**：2026-07-17 12:32:23 -0700
- **作者**：Ryan Blue
- **提交说明**：API, Core: Make variant classes serializable. (#17260)
- **PR/Issue**：#17260

## 总体目的

这个提交为 Iceberg Variant 类型系统的所有核心类添加了 Java `Serializable` 支持，使 variant 对象能够通过 Java 序列化机制传输和持久化。

Variant 类（如 `SerializedObject`、`SerializedArray`、`SerializedPrimitive` 等）内部持有 `ByteBuffer` 引用来引用底层二进制数据。`ByteBuffer` 本身不可序列化，且这些类内部有缓存数组、惰性解析等非序列化友好的状态。如果直接让这些类实现 `Serializable`，默认序列化会尝试序列化所有字段（包括 `ByteBuffer`），导致 `NotSerializableException`。

本提交采用**序列化代理（Serialization Proxy）模式**：每个 variant 类实现 `Serializable` 并通过 `writeReplace()` 方法返回一个轻量级的 `SerializationProxy`。代理只保存可序列化的核心数据（metadata + 原始字节数组），在反序列化时通过 `readResolve()` 重新构造原始对象。这样：
- 序列化时只保存原始字节，不保存内部缓存和惰性状态。
- 反序列化时重建干净的对象，所有缓存重新初始化。
- 避免了 `ByteBuffer` 不可序列化的问题。

这与 Flink 等引擎在算子间传输数据时的序列化需求密切相关，使 variant 值能作为 Flink 状态或 RDD 数据传输。提交由 Ryan Blue 与 Claude Opus 4.8 协作完成。

## 如何达成设计目的

为每个 variant 序列化类添加：
1. `implements Serializable`。
2. `private Object writeReplace()` 方法，返回 `SerializationProxy`。
3. 内部静态类 `SerializationProxy implements Serializable`，持有可序列化的核心字段（byte[] 或 metadata + byte[]）。
4. `readResolve()` 方法，调用 `from()` 工厂方法重建对象。

对 Core 模块的脱壳变体类（`PrimitiveWrapper`、`ShreddedObject`、`ValueArray`）和 `VariantData` 也添加了 `Serializable`。

## 修改详情

### API 模块的序列化类（各新增 ~19-21 行）

**修改目的**：为每个 Serialized* 类添加序列化代理。

涉及文件及工作逻辑（模式相同）：

- **`SerializedArray.java`** (+21/-1)：代理保存 `VariantMetadata metadata` + `byte[] valueBytes`，`readResolve` 调用 `SerializedArray.from(metadata, valueBytes)`。
- **`SerializedMetadata.java`** (+19/-1)：代理保存 `byte[] metadataBytes`，`readResolve` 调用 `SerializedMetadata.from(metadataBytes)`。
- **`SerializedObject.java`** (+21/-1)：代理保存 `VariantMetadata metadata` + `byte[] valueBytes`，`readResolve` 调用 `SerializedObject.from(metadata, valueBytes)`。
- **`SerializedPrimitive.java`** (+19/-1)：代理保存 `byte[] valueBytes`，`readResolve` 调用 `SerializedPrimitive.from(valueBytes)`。
- **`SerializedShortString.java`** (+20/-1)：代理保存 `byte[] valueBytes`，`readResolve` 调用 `SerializedShortString.from(valueBytes)`。新增 `import ByteBuffers`。
- **`VariantData.java`** (+3/-1)：直接 `implements Serializable`，因为其字段 `VariantMetadata` 和 `VariantValue` 现在都可序列化。

### Core 模块的脱壳变体类

- **`PrimitiveWrapper.java`** (+31/-... lines)：添加 `Serializable` 和序列化支持。
- **`ShreddedObject.java`** (+5/-1)：添加 `Serializable`。
- **`ValueArray.java`** (+5/-1)：添加 `Serializable`。

### 测试文件（各新增 ~8-44 行）

**修改目的**：验证序列化往返正确性。

涉及文件：
- `TestSerializedArray.java`、`TestSerializedMetadata.java`、`TestSerializedObject.java`、`TestSerializedPrimitives.java`：新增 `testSerialization` 参数化测试，使用 `TestHelpers#serializers` 提供的多种序列化方式（Java 原生、Kryo 等）验证往返。
- `TestVariant.java` (+44)：更全面的 variant 序列化测试。
- `TestPrimitiveWrapper.java`、`TestShreddedObject.java`、`TestValueArray.java`：Core 模块脱壳类的序列化测试。

## 总结

这个提交为 Iceberg Variant 类型系统的全部核心类添加了 Java 序列化支持，采用序列化代理模式优雅地处理了 `ByteBuffer` 不可序列化和内部缓存/惰性状态的问题。序列化时只保存原始字节，反序列化时重建干净对象。这使 variant 值能通过 Java 序列化机制传输（如 Flink 状态、Spark RDD 传输），对引擎集成具有重要意义。测试覆盖了多种序列化方式的往返正确性。由 Ryan Blue 与 Claude Opus 4.8 协作完成。
