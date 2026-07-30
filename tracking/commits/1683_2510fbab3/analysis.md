# 提交 1683 2510fbab3 分析

## 提交信息
- 哈希：2510fbab36df0cd965474ce14f92601984d75ccd
- 日期：2025-02-04 15:45:18 -0800
- 作者：Ryan Blue
- 消息：Core: Refactor to enable moving Variant interfaces to API. (#12167)

## 总体目的

本提交是对 Variant 模块的一次结构性重构，目的是把 Variant 相关的公共接口（`VariantValue`、`VariantObject`、`VariantArray`、`VariantPrimitive`、`VariantMetadata` 以及 `PhysicalType` 枚举）从 `core` 模块迁移到 `api` 模块做准备。Iceberg 的模块分层约定是：`api` 模块只放对外公共接口、不依赖 `core`；`core` 模块放实现。Variant 接口当前都放在 `core` 的 `org.apache.iceberg.variants` 包下，且与 `core` 内部的 `Variants` 工具类耦合较深，无法直接搬到 `api`。

具体来说，重构前 `Variants` 这个工具类里嵌套定义了三个东西：`LogicalType` 枚举（包级私有）、`PhysicalType` 公共枚举、`Primitives` 包级私有类。任何使用 `PhysicalType` 的接口（如 `VariantValue.type()` 返回 `Variants.PhysicalType`）都必须通过 `Variants.PhysicalType` 引用，这意味着接口要搬到 `api` 模块就会把 `Variants` 这个 core 工具类也带过去，破坏分层。

同时 `VariantMetadata` 接口继承自 `Variants.Serialized`（一个仅提供 `buffer()` 方法的内部接口），这也是一种对 core 工具类的耦合。本提交通过把这些嵌套定义提升为顶层类型、并把序列化能力直接放到接口自身，解除耦合，为后续把接口搬到 `api` 铺路。

## 如何达成设计目的

设计思路分三步：

1. 把 `Variants` 中的三个嵌套类型提升为顶层类型：
   - `LogicalType` 提升为独立的包级私有枚举文件 `LogicalType.java`。
   - `PhysicalType` 提升为独立的公共枚举文件 `PhysicalType.java`（因为要被 api 模块使用，必须 public）。
   - `Primitives` 提升为独立的包级私有类文件 `Primitives.java`（只存原始类型常量，core 内部使用）。
2. 解除 `VariantMetadata` 对 `Variants.Serialized` 的继承：在 `VariantMetadata` 接口中直接声明 `sizeInBytes()` 和 `writeTo(ByteBuffer, int)` 两个方法，把序列化写出能力内聚到接口本身。`SerializedMetadata` 实现类相应补充这两个方法的实现。
3. 全量替换引用：把代码中所有 `Variants.PhysicalType` 改为 `PhysicalType`，`Variants.Primitives` 改为 `Primitives`，删除不再需要的 import，并更新测试中的断言引用。

### 修改详情

#### core/src/main/java/org/apache/iceberg/variants/PhysicalType.java（新增）
新的公共枚举，从 `Variants.PhysicalType` 原样搬出。每个枚举常量关联一个 `LogicalType` 和一个 Java 类（如 `INT8(EXACT_NUMERIC, Byte.class)`）。提供 `javaClass()`、包级 `toLogicalType()` 和静态 `from(int primitiveType)` 方法（把原始类型字节码映射到 `PhysicalType`）。`from` 方法内部 switch 引用 `Primitives.TYPE_*` 常量。

#### core/src/main/java/org/apache/iceberg/variants/LogicalType.java（新增）
新的包级私有枚举，从 `Variants.LogicalType` 原样搬出，列出 NULL、BOOLEAN、EXACT_NUMERIC、FLOAT、DOUBLE、DATE、TIMESTAMPTZ、TIMESTAMPNTZ、BINARY、STRING、ARRAY、OBJECT 共 12 个逻辑类型。

#### core/src/main/java/org/apache/iceberg/variants/Primitives.java（新增）
新的包级私有类，从 `Variants.Primitives` 原样搬出，定义 `TYPE_NULL=0` 到 `TYPE_STRING=16` 共 17 个原始类型常量，以及 `PRIMITIVE_TYPE_SHIFT=2` 常量。

#### core/src/main/java/org/apache/iceberg/variants/Variants.java
删除了 `LogicalType`、`PhysicalType`、`Primitives` 三个嵌套定义（约 120 行），保留 `Serialized` 接口、`SerializedValue` 抽象类、`BasicType` 枚举、`HEADER_SIZE` 常量以及工厂方法 `metadata`/`primitive` 等。删除了不再需要的 `List`、`Map` 导入。

#### core/src/main/java/org/apache/iceberg/variants/VariantMetadata.java
关键变更：不再 `extends Variants.Serialized`，改为独立接口。新增 `import java.nio.ByteBuffer`。新增两个方法声明：`int sizeInBytes()`（返回序列化字节数）和 `int writeTo(ByteBuffer buffer, int offset)`（把元数据写到 buffer 指定偏移，返回写入字节数）。`writeTo` 的 Javadoc 说明会写入 `sizeInBytes()` 字节，且忽略 buffer 自身的 position/limit。这样元数据接口自身就具备序列化能力，无需依赖 `Variants.Serialized`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java
为新增的接口方法提供实现：`sizeInBytes()` 返回 `buffer().remaining()`；`writeTo` 调用 `VariantUtil.writeBufferAbsolute(buffer, offset, value)` 把底层字节写到指定偏移并返回字节数。

#### core/src/main/java/org/apache/iceberg/variants/VariantArray.java
把 `type()` 返回类型从 `Variants.PhysicalType` 改为 `PhysicalType`，返回值 `PhysicalType.ARRAY`。

#### core/src/main/java/org/apache/iceberg/variants/VariantObject.java
同上，`type()` 返回 `PhysicalType.OBJECT`。

#### core/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java
字段类型 `Variants.PhysicalType type` 改为 `PhysicalType type`；构造函数中 `Variants.PhysicalType.from(...)` 改为 `PhysicalType.from(...)`；`type()` 方法返回类型同步更新。

#### core/src/main/java/org/apache/iceberg/variants/SerializedShortString.java
`type()` 返回 `PhysicalType.STRING` 而非 `Variants.PhysicalType.STRING`。

#### core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java
删除对 `Variants.PhysicalType` 和 `Variants.Primitives` 的 import（改为直接引用同包顶层类型）。

#### core/src/main/java/org/apache/iceberg/variants/VariantUtil.java
`primitiveHeader` 中 `Variants.Primitives.PRIMITIVE_TYPE_SHIFT` 改为 `Primitives.PRIMITIVE_TYPE_SHIFT`。

#### core/src/main/java/org/apache/iceberg/variants/VariantValue.java
删除对 `Variants.PhysicalType` 的 import。

#### 测试文件（TestSerializedArray/TestSerializedObject/TestSerializedPrimitives/TestShreddedObject）
删除对 `Variants.PhysicalType` 的 import，把断言中所有 `Variants.PhysicalType.XXX` 改为 `PhysicalType.XXX`。`TestShreddedObject` 改动最多，涉及多个测试用例中对 OBJECT/INT32/STRING/DECIMAL4/DATE 等类型的断言。

## 小结

成效：成功解除了 Variant 公共接口对 `Variants` 工具类的编译期耦合，`PhysicalType` 成为顶层公共类型，`VariantMetadata` 自带序列化方法。接口现在可以独立搬到 `api` 模块。影响范围限于 `core` 模块的 variants 包及其测试，无运行时行为变更，无序列化格式变更，纯结构重构。

回迁到 1.4.x 的注意事项：本提交是 Variant 模块演进的中间步骤，依赖前序提交（如 1682 的 toString）已落地。若 1.4.x 已有 variants 包的早期版本（嵌套类型版本），则本提交可 cherry-pick，但需确保整个 variants 包的文件状态与 main 一致，否则 `Variants.java` 的删改和新增文件的包结构可能冲突。若 1.4.x 完全没有 variants 包，则应整体引入 Variant 模块而非单独回迁此重构。由于涉及多文件互相引用的引用路径变更，建议作为一批而非单独 cherry-pick。
