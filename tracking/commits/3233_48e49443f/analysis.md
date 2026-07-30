# 提交 3233：Core: Convert metrics to Content Stats (#15251)

## 提交信息

- **序号**：3233 / 4088
- **哈希**：48e49443f7113c1d789d8dc588312d4d81687b88
- **短哈希**：48e49443f
- **日期**：2026-02-10
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Convert metrics to Content Stats (#15251)
- **PR/Issue**：#15251

## 总体目的

本提交在 Iceberg 核心模块中新增从传统 `Metrics` 对象到新 `ContentStats` 对象的转换方法，并修复 `BaseFieldStats` 在序列化场景下的若干问题。Iceberg 的 `Metrics` 是传统的文件级统计信息模型，包含记录数、列大小、值计数、null 计数、NaN 计数以及上下界等，以 `Map<Integer, ByteBuffer>` 形式存储边界值。而 `ContentStats`/`FieldStats` 是更新的统计抽象，提供了更结构化、类型安全的字段统计表示，并设计为可序列化（`Serializable`）。

在将 `Metrics` 转换为 `ContentStats` 的过程中，发现 `BaseFieldStats` 存在序列化问题：`ByteBuffer` 和 `CharBuffer` 是 Java NIO 缓冲区对象，不可序列化（不实现 `Serializable`），而 `ContentStats` 需要在分布式环境（如 Spark executor）中序列化传输。此外，二进制类型的边界值在 `Metrics` 中以 `ByteBuffer` 形式存在，转换后需要处理类型兼容性。`equals()` 方法对数组类型的边界值也使用浅比较（`Objects.equals`）导致数组内容比较不正确。

本提交通过在 `BaseFieldStats` 的 builder 中将不可序列化的 `ByteBuffer`/`CharBuffer` 边界值转换为可序列化的 `byte[]`/`String`，并在 `lowerBound()`/`upperBound()` getter 中按需转换回原始类型，解决了序列化问题。同时新增 `MetricsUtil.fromMetrics()` 转换方法，将 `Metrics` 中的各种计数和边界映射合并到 `BaseFieldStats` 构建器中。

## 如何达成设计目的

在 `MetricsUtil` 中新增 `fromMetrics()` 静态方法，将 `Metrics` 的 `valueCounts`、`nullValueCounts`、`nanValueCounts`、`lowerBounds`、`upperBounds` 通过 `mergeCountMetric` 和 `mergeBoundMetric` 合并到按字段 ID 索引的 `BaseFieldStats.Builder` map 中，最终构建 `BaseContentStats`。在 `BaseFieldStats` 中，`lowerBound()`/`upperBound()` getter 增加类型感知的转换逻辑（`byte[]` → `ByteBuffer`），`equals()` 改用 `Objects.deepEquals`，builder 的 `lowerBound()`/`upperBound()` setter 通过 `serializableBound()` 将不可序列化类型转换为可序列化类型。测试全面重构为同时验证 `Metrics` 和 `ContentStats` 的一致性，并新增序列化往返测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+64/-0 lines)

**修改目的**：新增 `Metrics` 到 `ContentStats` 的转换方法。

**工作逻辑**：
`fromMetrics(Schema schema, Metrics metrics)` 方法首先处理 null metrics 直接返回 null。然后创建 `BaseContentStats.Builder` 并设置表 schema。通过 `mergeCountMetric` 将 `valueCounts`、`nullValueCounts`、`nanValueCounts` 分别合并到字段 ID 索引的 builder map 中——该方法使用 `BiFunction` 接受 builder、值并设置对应属性，通过 `Map.merge` 处理同一字段多次设置的情况。通过 `mergeBoundMetric` 将 `lowerBounds`/`upperBounds` 合并——该方法需要同时参考 `originalTypes` 映射以获取字段类型，使用 `Conversions.fromByteBuffer(type, byteBuffer)` 将 `ByteBuffer` 边界值转换为 Java 对象，并设置对应的 builder。最终遍历所有 builder 构建 `BaseFieldStats` 并添加到 `BaseContentStats`。

### `core/src/main/java/org/apache/iceberg/stats/BaseContentStats.java` (+3/-2 lines)

**修改目的**：使用 pattern matching for instanceof 简化代码。

**工作逻辑**：
`set()` 方法中将 `if (value instanceof GenericRecord) { GenericRecord record = (GenericRecord) value; ...` 改为 Java 16+ 的 pattern matching `if (value instanceof GenericRecord record)`，减少冗余的类型转换代码。

### `core/src/main/java/org/apache/iceberg/stats/BaseFieldStats.java` (+85/-30 lines)

**修改目的**：修复序列化问题并增强边界值类型兼容性。

**工作逻辑**：
新增 `serializableBound(T bound)` 私有静态方法，将 `CharBuffer` 转换为 `String`、`ByteBuffer` 转换为 `byte[]`，解决两者不可序列化的问题。`lowerBound()` 和 `upperBound()` getter 增加类型检查：当 `type` 的 Java 类为 `ByteBuffer.class` 且内部存储为 `byte[]` 时，通过 `ByteBuffer.wrap()` 转换回 `ByteBuffer` 返回。builder 的 `lowerBound()`/`upperBound()` setter 通过 `serializableBound()` 包装输入值。builder 的 `build()` 校验逻辑放宽——允许 `byte[]` 作为 `ByteBuffer` 类型的边界值（`type.typeId().javaClass().isInstance(lowerBound) || (type.typeId().javaClass().equals(ByteBuffer.class) && lowerBound instanceof byte[])`）。`get()` 方法重构为 switch 表达式（Java 14+）并改为调用 `lowerBound()`/`upperBound()` 方法而非直接访问字段（确保转换逻辑生效）。`equals()` 方法将边界比较从 `Objects.equals` 改为 `Objects.deepEquals`（正确比较数组内容）。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+424/-190 lines)

**修改目的**：同时验证传统 `Metrics` 和新 `ContentStats` 的一致性及序列化。

**工作逻辑**：
引入 `MetricsWithStats` record 封装 `Metrics` 和 `ContentStats` 对。所有测试方法在获取 `Metrics` 后额外调用 `MetricsUtil.fromMetrics()` 生成 `ContentStats`，并使用 `MetricsWithStats` 进行断言。`assertCounts()` 和 `assertBounds()` 方法签名改为接受 `MetricsWithStats`，在原有验证 `Metrics` 的基础上，新增验证 `ContentStats` 中对应字段的计数和边界一致性，并对 `ContentStats` 执行序列化往返测试（`assertSerializable`）。对 `CharBuffer` 类型的边界，验证时转换为 `String` 比较（因 `ContentStats` 中存储为 `String`）。新增 `assertSerializable()` 私有方法执行 `TestHelpers.roundTripSerialize` 并验证相等性。`TestOrcMetrics` 子类的 `assertBounds` 也同步适配。

### `core/src/test/java/org/apache/iceberg/stats/TestFieldStats.java` (+38/-0 lines)

**修改目的**：验证二进制类型字段的序列化兼容性。

**工作逻辑**：
新增参数化测试 `statsForBinaryTypes`，覆盖 `BinaryType` 和 `FixedType`。分别测试 `ByteBuffer` 和 `byte[]` 作为边界值构建 `BaseFieldStats`，验证序列化往返后相等，且两种形式构建的 stats 对象相等——证明 `ByteBuffer` 被正确转换为 `byte[]` 存储，而 getter 正确转换回 `ByteBuffer`。

### `data/src/test/java/org/apache/iceberg/orc/TestOrcMetrics.java` (+8/-4 lines)

**修改目的**：适配父类测试方法签名变更。

**工作逻辑**：
`assertBounds` 方法签名从 `Metrics` 改为 `MetricsWithStats`，内部从 `metrics.lowerBounds()` 改为 `metricsWithStats.metrics().lowerBounds()`，并调用 `super.assertBounds` 传递新参数。

## 总结

本提交通过新增 `MetricsUtil.fromMetrics()` 转换方法，建立了传统 `Metrics` 与新 `ContentStats` API 之间的桥梁，并为 `BaseFieldStats` 修复了关键的序列化问题（`ByteBuffer`/`CharBuffer` 不可序列化）。`equals()` 使用 `deepEquals` 修复了数组比较缺陷。测试全面验证了两种统计模型的一致性和序列化正确性，为后续从 `Metrics` 迁移到 `ContentStats` 奠定了基础。
