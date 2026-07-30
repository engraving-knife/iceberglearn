# 提交 1518 dea2fd1d9 分析

## 提交信息
- 哈希：dea2fd1d9debfd23aeda9403ed3eb81c6aebf30f
- 日期：2024-12-20（Fri Dec 20 13:58:43 2024 -0800）
- 作者：Ryan Blue <blue@apache.org>
- 消息：Core: Add Variant implementation to read serialized objects (#11415)

## 总体目的

本提交为 Iceberg 引入 **Variant 类型** 的 Java 实现——一种二进制编码的半结构化数据类型（类似 JSON，但二进制紧凑、可索引、可 shredding）。Variant 是 Iceberg/Spark/Databricks 等社区联合推进的新类型，用于在表里存储 schema 不固定的灵活数据（如日志、配置、JSON 文档），同时获得比纯字符串更好的查询性能。

一个 Variant 值由两部分字节序列组成：
- **metadata**：字段名字典（field id → name 的映射），可被多个值共享以减少重复存储。
- **value**：实际的数据值，按 Variant 二进制规范编码。值可以是 primitive（null/bool/int/long/float/double/decimal/date/timestamp/binary/string）、short_string（≤63 字节的短串）、object（字段集合）、array（元素列表）。

本提交实现了完整的 Variant 读取能力：从已有的 metadata+value 字节缓冲解析出结构化的 `VariantValue` 树，支持按字段名/索引访问 object/array，支持懒解析与缓存。同时实现了 `PrimitiveWrapper`（把 Java 值包装为可序列化的 Variant 原语）和 `ShreddedObject`（处理"shredding"——把 Variant 对象的部分字段拆成独立列存储以提升查询性能，剩余部分仍以未拆分形式存储）。

这是 Iceberg 支持 Variant 类型的基础设施。后续提交会在 Parquet/ORC/Avro 读写器、Spark/Flink 引擎集成中复用本提交的 API。本提交尚未把 Variant 接入 Iceberg 类型系统（`Type` 接口）和 schema 模型，仅提供独立的 variants 包。

## 如何达成设计目的

通过新增 `core/.../variants/` 包（13 个主类）+ 8 个测试类，并对 3 个已有工具类做小幅扩展。设计上分层清晰：接口层（Variant/VariantValue/...）+ 工具层（VariantUtil）+ 序列化实现层（Serialized*）+ 包装层（PrimitiveWrapper）+ shredding 层（ShreddedObject）。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/io/CloseableIterable.java`（修改）
- 新增静态方法 `static <E> CloseableIterable<E> of(Iterable<E> iterable)`：把任意 `Iterable` 适配为 `CloseableIterable`。若已是 CloseableIterable 直接返回；若是 Closeable 则用 `combine(iterable, closeable)`；否则用 `withNoopClose`（no-op close）。
- **目的**：SortedMerge 等工具的 `of(Iterable...)` 工厂需要把普通 Iterable 转为 CloseableIterable，此方法提供统一适配，避免每个调用点重复处理。

#### `api/src/test/java/org/apache/iceberg/util/RandomUtil.java`（修改）
- 把 `randomString` 内部逻辑提取为 public `generateString(int length, Random random)`，`randomString` 改为委托调用。
- **目的**：让 Variant 测试工具能生成指定长度的随机字符串（测试 short string 边界等场景），而原 `randomString` 只能生成 0-50 长度。

#### `core/src/main/java/org/apache/iceberg/util/SortedMerge.java`（修改）
- 新增两个静态工厂：
  - `static <C extends Comparable<C>> CloseableIterable<C> of(Iterable<C> left, Iterable<C> right)`：合并两个有序 Iterable。
  - `static <C extends Comparable<C>> CloseableIterable<C> of(List<Iterable<C>> iterables)`：合并多个有序 Iterable。
  - 都用 `Comparator.naturalOrder()`（要求元素 Comparable），并通过 `CloseableIterable::of` 适配。
- **目的**：ShreddedObject 在序列化时需要合并已 shredding 字段与未 shredding 字段的有序迭代器（按 field id/name 排序），用 SortedMerge 自然顺序合并。原 SortedMerge 只能通过构造函数+Comparator 使用，不便。

#### `core/src/main/java/org/apache/iceberg/variants/Variant.java`（新增）
- 顶层接口：`metadata()` + `value()`。表示一个 metadata-value 对。

#### `core/src/main/java/org/apache/iceberg/variants/VariantValue.java`（新增）
- 值的根接口。方法：`PhysicalType type()`、`int sizeInBytes()`、`int writeTo(ByteBuffer, int offset)`。
- 提供 `asPrimitive()`/`asObject()`/`asArray()` 默认实现，默认抛 `IllegalArgumentException`，子类按需覆盖返回 this。这是典型的"类型安全的窄化访问"模式——调用方先 `value.type()` 判断或直接 `asObject()` 拿到强类型接口。

#### `core/src/main/java/org/apache/iceberg/variants/VariantMetadata.java`（新增）
- 元数据字典接口，`extends Variants.Serialized`。方法：`int id(String name)`（name→id，不存在返回 -1）、`String get(int id)`（id→name，不存在抛 NoSuchElementException）。

#### `core/src/main/java/org/apache/iceberg/variants/VariantObject.java`（新增）
- 对象值接口，`extends VariantValue`。`VariantValue get(String name)` 按字段名取值。`type()` 返回 OBJECT，`asObject()` 返回 this。

#### `core/src/main/java/org/apache/iceberg/variants/VariantArray.java`（新增）
- 数组值接口，`extends VariantValue`。`VariantValue get(int index)` 按索引取值。`type()` 返回 ARRAY，`asArray()` 返回 this。

#### `core/src/main/java/org/apache/iceberg/variants/VariantPrimitive.java`（新增）
- 原语值接口，`extends VariantValue`，泛型 `<T>`。`T get()` 返回 Java 值。`asPrimitive()` 返回 this。

#### `core/src/main/java/org/apache/iceberg/variants/Variants.java`（新增，276 行）
- 核心工厂与类型定义类。包含：
  - **`LogicalType` 枚举**：NULL/BOOLEAN/EXACT_NUMERIC/FLOAT/DOUBLE/DATE/TIMESTAMPTZ/TIMESTAMPNTZ/BINARY/STRING/ARRAY/OBJECT——Variant 的逻辑类型分类。
  - **`PhysicalType` 枚举**：17 个物理类型，每个映射到一个 LogicalType 和一个 Java 类（如 INT8→Byte.class、DECIMAL4→BigDecimal.class、STRING→String.class）。提供 `from(int primitiveType)` 把原始 type 码转为枚举，`javaClass()` 给出 Java 表示类。物理类型比逻辑类型更细——例如 EXACT_NUMERIC 拆成 INT8/16/32/64 和 DECIMAL4/8/16，按数值位数选择最紧凑存储。
  - **`Primitives` 常量类**：TYPE_NULL=0 ... TYPE_STRING=16 共 17 个 type 码常量，`PRIMITIVE_TYPE_SHIFT=2`（type 码左移 2 位放进 header 高 6 位，低 2 位是 basic type）。
  - **`BasicType` 枚举**：PRIMITIVE/SHORT_STRING/OBJECT/ARRAY——header 最低 2 位编码的"基本类型"。
  - **`Serialized` 接口**：`ByteBuffer buffer()`——所有"序列化态"实现共享，返回底层字节缓冲。
  - **`SerializedValue` 抽象类**：实现 `sizeInBytes`（=buffer 剩余字节数）和 `writeTo`（绝对位置写入 buffer）。
  - **`from(ByteBuffer metadata, ByteBuffer value)` 工厂**：读 header 第 0 字节，按 basic type 分派到 SerializedPrimitive/SerializedShortString/SerializedObject/SerializedArray 的 `from`。这是整个解析的入口。
  - **`of(...)` 工厂系列**：把 Java 值包装为 `PrimitiveWrapper`。覆盖所有原语类型（boolean/byte/short/int/long/float/double/BigDecimal/ByteBuffer/String）以及 date/timestamp（用 `DateTimeUtil` 从 ISO 字符串转 epoch days/micros）。BigDecimal 按 unscaledValue 的 bitLength 选择 DECIMAL4/8/16。

#### `core/src/main/java/org/apache/iceberg/variants/VariantUtil.java`（新增，195 行）
- 底层字节缓冲读写工具。所有读取都基于 `buffer.position() + offset` 的绝对位置（不消耗 buffer 的 position/limit）。
- **写**：`writeBufferAbsolute`（绝对位置批量写入）、`writeByte`、`writeLittleEndianUnsigned`（1/2/3/4 字节小端无符号写）。
- **读**：`readByte`、`readLittleEndianInt8/16/32/64`、`readLittleEndianUnsigned`（1-4 字节）、`readFloat`、`readDouble`、`readString`（优先用 `buffer.hasArray()` 走零拷贝路径，否则 UTF-8 decode）、`slice`（切片，小端序）。
- **`find`**：泛型二分查找，用于在有序字段名/id 列表中定位——Variant 规范要求 object 字段按 name 字典序排列，metadata 字典可选 sorted 标志。
- **`sizeOf`**：根据 maxValue 选最小字节宽度（1/2/3/4）。
- **header 构造**：`primitiveHeader`（type<<2）、`objectHeader`（isLarge|fieldIdSize|offsetSize|0b10）、`arrayHeader`（isLarge|offsetSize|0b11）。
- **`basicType(int header)`**：取低 2 位映射到 BasicType。

#### `core/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java`（新增，130 行）
- 序列化态原语实现。`from(ByteBuffer value, int header)` 读 header 高 6 位的 primitive type，按类型读取对应字节数的值（如 INT8 读 1 字节、DECIMAL4 读 1 字节 scale + 4 字节 unscaled int、TIMESTAMPTZ 读 8 字节 long micros 等），构造 `SerializedPrimitive`。
- `get()` 返回对应 Java 值。`buffer()` 返回底层切片。
- **工作逻辑**：每种物理类型有固定的 header+payload 布局，解析时按 type 分支读取。DECIMAL16 特殊处理：16 字节小端二进制补码，转回 BigDecimal。

#### `core/src/main/java/org/apache/iceberg/variants/SerializedShortString.java`（新增，69 行）
- 短字符串实现。header 低 2 位 = 0b01（SHORT_STRING），高 6 位是字符串长度（最多 63 字节）。`from` 读长度并切片出 UTF-8 字节，`get()` 解码为 String。

#### `core/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java`（新增，113 行）
- 元数据字典实现。布局：1 字节 header（低 4 位 version=1，第 4 位 SORTED_STRINGS 标志，高 2 位 offsetSize-1）+ offsetSize 字节 dictSize + (dictSize+1)*offsetSize 字节 offset 列表 + 字符串数据区。
- `from` 校验 version==1。构造时读 dictSize、计算 offsetListOffset 和 dataOffset。
- `id(String name)`：若 isSorted 用 `VariantUtil.find` 二分查找；否则线性扫描。返回 id 或 -1。
- `get(int id)`：读 offset[id] 和 offset[id+1] 切片出字符串。缓存到 `dict[]`。
- 提供 `EMPTY_V1_BUFFER`（空字典）常量。

#### `core/src/main/java/org/apache/iceberg/variants/SerializedArray.java`（新增，88 行）
- 序列化态数组实现。布局：1 字节 header（低 2 位=0b11，2-3 位 offsetSize-1，第 4 位 isLarge）+ numElementsSize 字节元素数（isLarge 时 4 字节否则 1 字节）+ (numElements+1)*offsetSize 字节 offset 列表 + 数据区。
- `get(int index)`：懒解析——读 offset[index] 和 offset[index+1]，切片出该元素的字节，递归调用 `Variants.from` 解析为 VariantValue，缓存到 `array[]`。
- `numElements()` 返回元素数。

#### `core/src/main/java/org/apache/iceberg/variants/SerializedObject.java`（新增，229 行）
- 序列化态对象实现，最复杂的 Serialized* 类。布局：1 字节 header（低 2 位=0b10，2-3 位 offsetSize-1，4-5 位 fieldIdSize-1，第 6 位 isLarge）+ numElementsSize 字节字段数 + numElements*fieldIdSize 字节 field id 列表 + (numElements+1)*offsetSize 字节 offset 列表 + 数据区。
- 构造时计算 fieldIdListOffset、offsetListOffset、dataOffset，并调用 `initOffsetsAndLengths` 预读所有 offset 并推断每个字段的 length（通过排序 offset 列表，相邻 offset 之差即为字段长度）。
- **字段 id 懒解析**：`id(int index)` 按需读 field id 列表并缓存到 `fieldIds[]`。
- **`get(String name)`**：用 `VariantUtil.find` 在字段名（通过 `metadata.get(id(pos))` 解析）上二分查找定位 index，再懒解析该字段值并缓存。Variant 规范要求字段按 name 字典序排列，故可二分。
- **`sliceValue(name/index)`**：返回字段的原始 ByteBuffer 切片（不解析为 VariantValue），用于 shredding 等需要原始字节的场景。
- 提供 `fields()`、`fieldNames()` 迭代器（按 index 顺序，即字典序）。
- **工作逻辑**：懒解析 + 缓存——构造时只读 header 和 offset 列表，字段 id 和字段值都按需读取并缓存，避免一次性解析整个对象的开销。

#### `core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java`（新增，206 行）
- 包装态原语实现（区别于 SerializedPrimitive 的序列化态）。把 Java 值包装为 `VariantPrimitive<T>`，并能序列化到 ByteBuffer。
- 预计算每种类型的 header 常量（如 `INT8_HEADER = primitiveHeader(TYPE_INT8)`）。
- `sizeInBytes()` 按 type 返回序列化字节数（如 INT8=2、INT64=9、DECIMAL4=6、STRING=5+UTF8字节数）。String 缓存 UTF-8 字节 buffer。
- `writeTo(ByteBuffer, offset)` 按 type 分支写入 header + payload：
  - 简单类型直接 `put`/`putShort`/`putInt`/`putLong`/`putFloat`/`putDouble`。
  - DECIMAL4/8 写 scale + unscaled int/long。
  - **DECIMAL16**：把 BigInteger 的大端字节数组转成 16 字节小端二进制补码，不足补符号位（0x00 正/0xFF 负）。
  - BINARY/STRING：写 length(int) + 字节内容。
- 校验 buffer 为小端序。这是 `Variants.of(...)` 工厂的返回类型，用于构造新的 Variant 值。

#### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java`（新增，211 行）
- 处理 shredding 的 VariantObject 实现。Shredding 是 Variant 的重要优化：把对象里高频查询的字段拆出来作为独立列存储（便于向量化扫描、谓词下推），剩余字段仍以未拆分 Variant 字节存储。读取时把两部分重新组合成完整 VariantObject。
- 两个构造函数：`ShreddedObject(SerializedMetadata metadata)`（空 shredded，仅 metadata）、`ShreddedObject(SerializedObject unshredded)`（包装一个未 shredding 的对象，所有字段都来自 unshredded）。
- `put(String name, VariantValue value)`：添加一个 shredded 字段。
- `get(String name)`：先查 shreddedFields map，命中则返回；否则委托 unshredded。
- 序列化（`serializationState`/`toBuffer` 等）：合并 shredded 字段与 unshredded 字段，按 name 字典序输出为标准 Variant object 字节。用 `SortedMerge` 合并两边的字段名迭代器。
- **设计要点**：注释强调 metadata 必须在 shredded 和 unshredded 间一致（field id 共享），且不允许更新 unshredded 的 metadata（会破坏 field id 一致性）。

#### 测试文件（8 个新增）
- `TestPrimitiveWrapper.java`：验证 PrimitiveWrapper 的 sizeInBytes 和 writeTo 各类型正确。
- `TestSerializedArray.java`：解析数组的各元素类型、嵌套数组、numElements 等。
- `TestSerializedMetadata.java`：字典 id/get、sorted vs unsorted、version 校验、空字典。
- `TestSerializedObject.java`：对象字段按字典序访问、嵌套对象、缺失字段返回 null、fieldNames 迭代、sliceValue。
- `TestSerializedPrimitives.java`（465 行，最大）：覆盖所有 17 种物理类型的解析，包括边界值、DECIMAL 各种精度、short string vs string、binary、timestamp/date。
- `TestShreddedObject.java`（448 行）：shredding 各种组合——全 shredded、全 unshredded、混合、put/get、序列化回写后与原对象比较。
- `TestVariantUtil.java`：sizeOf、find 等工具方法。
- `VariantTestUtil.java`（214 行）：测试基础设施——生成随机 Variant 值（带可配置深度/宽度）、把 Variant 序列化到 buffer、比较两个 Variant 树相等。复用 `RandomUtil.generateString`。

## 小结

- **成效**：为 Iceberg 引入了完整的 Variant 二进制类型读取/构造/shredding 能力。这是 Iceberg 支持 Variant 数据类型的基础设施层，提供了类型安全的 Java API（VariantValue 树）+ 高性能的懒解析序列化实现 + shredding 优化支持。后续可在 Parquet/ORC/Avro 读写器和 Spark/Flink 引擎集成中复用。
- **影响范围**：core 模块新增 `variants` 包（13 个主类）+ 8 个测试类，api 模块小幅扩展 2 个工具类，core 模块扩展 SortedMerge。共 26 个文件，+3813/-1 行。属于大型新功能引入，但本提交尚未接入 Iceberg 类型系统（`Type`/`Schema`）和读写器，仅是独立的 variants 包。
- **设计亮点**：（1）接口/实现分离——`VariantValue` 接口 + `Serialized*`（读已有字节）和 `PrimitiveWrapper`（构造新值）两套实现；（2）懒解析 + 缓存——SerializedObject/Array 构造时只读 header 和 offset，字段值按需解析并缓存；（3）二分查找利用规范要求的字段名字典序；（4）shredding 用 SortedMerge 合并 shredded/unshredded 字段，对调用方透明；（5）物理类型细分（INT8/16/32/64、DECIMAL4/8/16）实现紧凑存储。
- **回迁到 1.4.x 的注意事项**：这是大型新功能，1.4.x 维护分支**不应回迁**。Variant 类型需要完整的类型系统、读写器、引擎集成才能端到端工作，单独回迁 variants 包无意义且会引入维护负担。Variant 应作为下一个大版本（如 1.5/2.0）的特性整体发布。

__tr_native_ec=$?; pwd -P >| '/var/folders/j4/8_ygb9zx7ll_gb4jlr9jd_vw0000gn/T/trae-agent-toolhost-501/jobs/job-359a221391814fcc9322904e2f5278be/cwd.txt'; exit "$__tr_native_ec"