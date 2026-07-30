# 提交 1653 61241ed47 分析

## 提交信息
- 哈希：61241ed4789e719237d276a08fc3cc4113b312c0
- 日期：2025-01-28 09:46:10 -0700
- 作者：Ryan Blue
- 消息：Core: Update variant class visibility (#12105)

## 总体目的

本提交对 Iceberg 中 Variant 类型系统（variants 包）进行了 API 可见性和接口层面的重构。Variant 是一种自描述的半结构化数据类型（类似 JSON），由 metadata 和 value 两部分组成。此前该模块的多数类与方法为包级私有（package-private），仅能在 `org.apache.iceberg.variants` 包内部使用，外部模块（如 Spark、Flink 等集成）无法直接构建或操作 Variant 对象。

本次重构将必要的构造方法、工厂方法以及若干只读访问方法提升为 `public`，使得外部模块可以通过统一的 `Variants` 入口类来创建 metadata、解析 value、构造 shredded（剥离）对象以及构造基本类型原语。这是为后续在 Spark 等引擎中支持 Variant 类型的读写、Shredding 优化做铺垫。

此外，本提交还扩展了 `ShreddedObject` 的能力，引入了字段删除（`remove`）的支持，并将内部使用的具体类型（`SerializedMetadata`、`SerializedObject`）替换为接口类型（`VariantMetadata`、`VariantObject`），从而让 ShreddedObject 可以包装任意 `VariantObject` 实现，而非仅限于 `SerializedObject`。

## 如何达成设计目的

设计思路是将"对外 API"与"内部实现"清晰分离：
- 对外：通过 `Variants` 公开类提供静态工厂方法（`metadata`、`value`、`object`、`of`、`ofNull`），让调用方无需关心具体实现类即可创建 Variant 实例。
- 对内：保留 `SerializedMetadata`、`SerializedObject`、`SerializedArray`、`PrimitiveWrapper`、`ShreddedObject` 等实现类为包级私有或受保护，仅通过接口暴露能力。

同时，将接口（`VariantArray`、`VariantMetadata`、`VariantObject`）补充了 `numElements`、`numFields`、`dictionarySize`、`fieldNames` 等只读方法，使外部调用方能获取结构信息；并改造 `ShreddedObject` 以支持字段删除和基于任意 `VariantObject` 的 shredding。

### 修改详情

#### core/src/main/java/org/apache/iceberg/variants/Variants.java
这是 Variant 模块的对外入口类，改动最关键：
- 重命名内部 `from(SerializedMetadata, ByteBuffer)` 为 `public value(VariantMetadata, ByteBuffer)`，用于根据 metadata 和 value 字节流解析出 `VariantValue`。
- 新增 `public metadata(ByteBuffer)`，用于从字节流构造 `VariantMetadata`（替代旧的 `from(ByteBuffer, ByteBuffer)` 公开方法，将其拆分为两步，便于复用 metadata）。
- 新增 `public object(VariantMetadata)` 和 `public object(VariantMetadata, VariantObject)` 两个工厂方法，用于创建空的 ShreddedObject 或基于已有对象的 ShreddedObject。
- 新增泛型 `public <T> VariantPrimitive<T> of(PhysicalType, T)`，统一原语构造入口。
- 简化 `of(boolean)`：不再根据 true/false 分别选择 BOOLEAN_TRUE/BOOLEAN_FALSE，而是统一传入 BOOLEAN_TRUE，由 `PrimitiveWrapper` 构造函数内部根据值决定实际物理类型。这避免了重复逻辑。

#### core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java
- 将 `Variants.PhysicalType` 引用改为直接导入 `Variants.PhysicalType`，减少内层类访问层级。
- 构造函数新增逻辑：当传入值为 Boolean 且 type 为 BOOLEAN_TRUE/BOOLEAN_FALSE 时，根据实际布尔值修正物理类型。这样 `of(boolean)` 可以统一传 BOOLEAN_TRUE，由 wrapper 自行归一化，确保序列化时使用正确的物理类型。

#### core/src/main/java/org/apache/iceberg/variants/SerializedArray.java
- 方法签名中的 `SerializedMetadata` 改为接口 `VariantMetadata`，使数组解析可接受任意 metadata 实现。
- `numElements()` 由 `@VisibleForTesting` 包级方法改为 `@Override public`，实现 `VariantArray` 接口新方法。
- 内部调用 `Variants.from` 改为 `Variants.value`，与重命名同步。

#### core/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java
- 新增静态常量 `EMPTY_V1_METADATA = from(EMPTY_V1_BUFFER)`，缓存空 metadata 实例避免重复构造。
- `dictionarySize()` 由 `@VisibleForTesting` 改为 `@Override public`，实现 `VariantMetadata` 接口新方法。

#### core/src/main/java/org/apache/iceberg/variants/SerializedObject.java
- 同样将 `SerializedMetadata` 替换为 `VariantMetadata`。
- `numElements()` 改名为 `numFields()` 并提升为 `public`（语义更准确，对象用 field 而非 element）。
- `metadata()` 返回类型改为 `VariantMetadata`，并加上 `@VisibleForTesting`。
- `fieldNames()` 加上 `@Override`，实现接口契约。
- 内部 `Variants.from` 调用同步改为 `Variants.value`。

#### core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java
改动最大，扩展了功能：
- 类由包级私有改为 `public`，让外部可引用此类型（通过 `Variants.object` 工厂返回）。
- 字段类型从 `SerializedMetadata` 改为 `VariantMetadata`，从 `SerializedObject` 改为 `VariantObject`，使 ShreddedObject 可包装任意 VariantObject 实现。
- 新增 `removedFields` 集合，用于追踪被删除的字段名。
- 新增 `remove(String field)` 方法：从 shreddedFields 中移除并将字段加入 removedFields。
- 新增 `nameSet()` 私有方法：合并 shreddedFields、unshredded 字段名，再排除 removedFields，得到当前有效字段集合。
- 新增 `fieldNames()` 和 `numFields()` 的 `@Override` 实现，基于 `nameSet()`。
- `get(field)` 增加对 removedFields 的判断，被删除的字段返回 null。
- `SerializationState` 构造逻辑重构：
  - 当 unshredded 是 `SerializedObject` 时，走原有优化路径（直接复用其 ByteBuffer 切片，避免物化值）。
  - 当 unshredded 是其他 `VariantObject` 实现时，通过 `fieldNames()` 遍历并将其值放入 shreddedFields 中（物化路径）。
  - 两条路径都将 removedFields 视为"被替换"，不写入输出。

#### core/src/main/java/org/apache/iceberg/variants/VariantArray.java
接口新增 `int numElements()` 方法，返回数组元素数量。

#### core/src/main/java/org/apache/iceberg/variants/VariantMetadata.java
接口新增 `int dictionarySize()` 方法，返回 metadata 字典大小。

#### core/src/main/java/org/apache/iceberg/variants/VariantObject.java
接口新增 `Iterable<String> fieldNames()` 和 `int numFields()` 两个方法，用于获取对象字段名集合和字段数量。

#### 测试文件（TestPrimitiveWrapper、TestSerializedArray、TestSerializedMetadata、TestSerializedObject、TestShreddedObject、VariantTestUtil）
- 同步 API 重命名（`from` -> `value`，类型签名从 `SerializedMetadata` 改为 `VariantMetadata` 等）。
- `TestShreddedObject` 中新增对 `remove`、`fieldNames`、`numFields` 等新功能的测试覆盖。

## 小结

本次重构是 Variant 类型走向外部可用的关键一步，影响范围集中在 `core` 模块的 `variants` 包及其测试。成效：
- 建立了清晰的公共 API（`Variants.metadata/value/object/of/ofNull`），外部模块可稳定依赖。
- 接口能力补全（numElements/numFields/dictionarySize/fieldNames），便于查询结构。
- ShreddedObject 支持字段删除和包装任意 VariantObject，为 Variant 的 Shredding 优化（将热门字段从 unshredded 值中"剥离"出来单独存储）提供更完整支持。

回迁到 1.4.x 注意事项：
- 1.4.x 分支通常不引入新特性，但 Variant 是较新的功能，如果 1.4.x 已包含 Variant 模块的早期版本，回迁此提交可以让外部集成模块（如 Spark 3.5/4.0 的 Variant 支持）使用统一 API。
- 回迁时需注意：本提交依赖 Variant 模块已存在的代码基线；若 1.4.x 的 Variant 模块与本提交基于的版本差异较大，需一并回迁相关前置提交。
- 由于 API 签名发生变化（`from` 重命名为 `value`、`of(boolean)` 行为变化等），回迁后需同步更新所有内部调用点和测试。
- `ShreddedObject.remove` 与 `removedFields` 是新功能，回迁需连同测试一并移植以保证覆盖。
