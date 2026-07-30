# 提交 4060：API: Harden variant binary parsing against malformed input (#16568)

## 提交信息

- **序号**：4060 / 4088
- **哈希**：c8521d869df1a76ea72d7482c196fc15de08dd14
- **短哈希**：c8521d869
- **日期**：2026-07-17 19:39:06 +0200
- **作者**：Neelesh Salian
- **提交说明**：API: Harden variant binary parsing against malformed input (#16568)
- **PR/Issue**：#16568

## 总体目的

这个提交对 Iceberg Variant（变体类型）的二进制解析逻辑进行了全面加固，使其能够安全地处理格式错误（malformed）的输入，防止因恶意或损坏的数据导致越界读取、整数溢出、过度内存分配或无限递归等问题。

Variant 是 Iceberg 支持的半结构化数据类型，其二进制格式包含嵌套的对象、数组、元数据字典等结构。此前的解析代码信任输入 buffer 中的字段计数、偏移量、长度等元数据，没有充分校验它们是否在 buffer 范围内、是否为非负值、是否超过合理上限。这意味着：
- 一个精心构造的恶意 variant 值可以声称包含数百万个字段，导致分配巨大的数组。
- 偏移量可以指向 buffer 之外，导致越界读取。
- 无限嵌套的对象/数组可以导致栈溢出。
- 负数的长度/计数可能导致意外行为。

本提交通过在所有解析路径中添加边界检查、范围验证和安全限制（最大嵌套深度、最大元素数），使 variant 解析对格式错误输入具有鲁棒性，遇到非法输入时抛出 `IllegalArgumentException` 而非产生未定义行为。这与 parquet-java 的类似安全加固（apache/parquet-java#3562）保持一致。

## 如何达成设计目的

设计上采用集中式入口 + 深度传递 + 分层校验的策略：

1. **集中式入口**：将原先分散在 `VariantValue.from()` 中的解析逻辑提取到 `VariantUtil.fromBuffer(metadata, value, depth)`，统一作为 variant 值的解析入口，在此进行深度限制和空 buffer 检查。

2. **深度传递**：`SerializedObject` 和 `SerializedArray` 的构造函数和 `from()` 方法新增 `depth` 参数，每次递归解析嵌套元素时 `depth + 1`，并在入口处校验 `depth <= MAX_VARIANT_DEPTH`（1000）。

3. **分层校验**：在每个序列化类型的构造函数中，对 buffer 大小、元素计数、偏移量范围、数据长度等进行逐项校验：
   - 元素计数非负且不超过 `MAX_ELEMENTS`（16_777_216）。
   - 偏移表和数据区域不超出 buffer 范围。
   - 各元素偏移在声明的数据长度内。
   - 字段 ID 在元数据字典范围内。
   - 基本类型（primitive）的 payload 大小与 buffer 一致。

4. **溢出防护**：将原先 `int` 运算的偏移表大小计算改为 `long` 运算，防止大元素计数导致的整数溢出（如 `(1 + numElements) * offsetSize` 溢出后变为小正数，绕过范围检查）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/VariantUtil.java` (+39/-0 lines)

**修改目的**：提供集中的 variant 解析入口，添加深度限制和安全常量。

**工作逻辑**：
- 新增安全常量：
  ```java
  static final int MAX_VARIANT_DEPTH = 1000;  // 最大嵌套深度，匹配 parquet-java
  static final int MAX_ELEMENTS = 16_777_216; // 最大元素数，防止堆分配放大
  ```
- 新增 `fromBuffer(metadata, value, depth)` 方法：
  ```java
  static VariantValue fromBuffer(VariantMetadata metadata, ByteBuffer value, int depth) {
    Preconditions.checkArgument(depth >= 0, ...);
    Preconditions.checkArgument(depth <= MAX_VARIANT_DEPTH, ...);
    Preconditions.checkArgument(value.remaining() >= 1, "Invalid variant: empty value buffer");
    int header = ByteBuffers.readByte(value, 0);
    BasicType basicType = basicType(header);
    switch (basicType) {
      case PRIMITIVE: return SerializedPrimitive.from(value, header);
      case SHORT_STRING: return SerializedShortString.from(value, header);
      case OBJECT: return SerializedObject.from(metadata, value, header, depth);
      case ARRAY: return SerializedArray.from(metadata, value, header, depth);
    }
    ...
  }
  ```
  在解析前校验深度和 buffer 非空，将 depth 传递给嵌套类型的构造。

### `api/src/main/java/org/apache/iceberg/variants/VariantValue.java` (+3/-13 lines)

**修改目的**：将解析委托给集中的 `VariantUtil.fromBuffer`。

**工作逻辑**：`VariantValue.from(metadata, value)` 简化为 `return VariantUtil.fromBuffer(metadata, value, 0);`，从 depth=0 开始解析。移除了原先内联的 switch 逻辑和 `ByteBuffers` import。

### `api/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java` (+50/-0 lines)

**修改目的**：校验 primitive 值的 payload 大小不超出 buffer。

**工作逻辑**：构造函数中新增校验：
```java
long requiredBytes = PRIMITIVE_OFFSET + payloadSize(type, value);
Preconditions.checkArgument(requiredBytes <= value.remaining(), ...);
```
新增 `payloadSize(type, value)` 方法，根据物理类型返回所需的 payload 字节数：
- NULL/BOOLEAN: 0 字节
- INT8: 1, INT16: 2, INT32/DATE/FLOAT: 4
- INT64/TIMESTAMP/DOUBLE 等: 8
- DECIMAL4: 5, DECIMAL8: 9, DECIMAL16: 17, UUID: 16
- BINARY/STRING: 读取 4 字节 size 字段，校验非负，返回 `4 + size`

对 BINARY/STRING 还额外校验 size 字段本身不超出 buffer。

### `api/src/main/java/org/apache/iceberg/variants/SerializedObject.java` (+69/-0 lines, 重构)

**修改目的**：添加深度传递、元素计数限制、偏移范围校验、字段 ID 范围校验。

**工作逻辑**：
- `from()` 新增 `depth` 参数重载，保留旧签名委托（depth=0）。
- 构造函数新增 `depth` 字段，校验：
  - buffer 足够容纳元素计数字段。
  - 元素计数非负且 ≤ `MAX_ELEMENTS`。
  - 偏移表和数据区域不超出 buffer（使用 `long` 运算防溢出：`long dataStart = (long) fieldIdListOffset + (long) numElements * fieldIdSize + ((long) numElements + 1L) * offsetSize`）。
- 数据长度校验：`dataLength` 非负且在数据区域内。
- 各元素偏移校验：`offsets[index]` 在 `[0, dataLength]` 范围内。
- 长度计算重构：原先遍历 `numElements` 个 sortedOffsets 取相邻差，改为遍历 `sortedOffsets.size() - 1`，并注释说明共享偏移（零长度跨度）是 spec 合法的，不要求唯一性。
- 字段 ID 惰性校验：`id(int index)` 方法在首次读取字段 ID 时校验 `id >= 0 && id < dictSize`，注释说明「字段 ID 范围在首次访问时惰性校验，而非构造时」。
- 嵌套值解析改用 `VariantUtil.fromBuffer(metadata, slice, depth + 1)` 传递递增的深度。

### `api/src/main/java/org/apache/iceberg/variants/SerializedArray.java` (+37/-0 lines, 重构)

**修改目的**：与 SerializedObject 类似的加固。

**工作逻辑**：
- `from()` 新增 `depth` 重载。
- 构造函数校验：buffer 足够大、元素计数非负且 ≤ `MAX_ELEMENTS`、偏移表不超出 buffer（`long` 运算防溢出）。
- 元素读取时校验偏移范围 `[offset, next]` 在数据区域 `[0, dataLen]` 内。
- 嵌套元素解析改用 `VariantUtil.fromBuffer(metadata, slice, depth + 1)`。

### `api/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java` (+42/-0 lines, 重构)

**修改目的**：加固元数据字典解析。

**工作逻辑**：
- 校验 buffer 足够容纳字典大小字段。
- 字典大小非负且 ≤ `MAX_ELEMENTS`。
- 偏移表不超出 buffer（`long` 运算：`long offsetTableEnd = (long) offsetListOffset + ((long) dictSize + 1L) * offsetSize`）。
- 末尾偏移（endOffset）非负且不超出 buffer。
- 各字典条目的偏移范围校验。
- 将 `metadata.limit()` 改为 `metadata.remaining()` 使校验基于相对位置更准确。

### `api/src/main/java/org/apache/iceberg/variants/SerializedShortString.java` (+4/-0 lines)

**修改目的**：校验短字符串长度不超出 buffer。

**工作逻辑**：
```java
Preconditions.checkArgument(
    HEADER_SIZE + length <= value.remaining(),
    "Invalid variant short string: length %s exceeds buffer", length);
```

### `api/src/test/java/org/apache/iceberg/variants/TestMalformedVariant.java` (+519/-0 lines, 新文件)

**修改目的**：全面测试格式错误输入的拒绝。

**工作逻辑**：新增 519 行测试，覆盖各种 malformed variant 输入场景，包括：空 buffer、截断的 buffer、超出范围的字段 ID、负数元素计数、超大元素计数、偏移超出数据区域、嵌套深度超限、未知 primitive type-id 等。每个测试构造特定的 malformed 字节序列，断言解析抛出 `IllegalArgumentException`。

### 其他测试文件修改

- `TestSerializedArray.java` (+32/-0)：调整以适应新的构造函数签名和校验。
- `TestSerializedMetadata.java` (+10/-0)：同上。
- `TestSerializedObject.java` (+4/-0)：同上。

## 总结

这是一个重要的安全性加固提交，全面提升了 Variant 二进制解析对格式错误输入的鲁棒性。通过集中式解析入口、深度限制（防止无限递归/栈溢出）、元素计数上限（防止过度内存分配）、偏移和长度范围校验（防止越界读取）、以及整数溢出防护（使用 long 运算），使恶意或损坏的 variant 数据无法导致未定义行为，而是被安全地拒绝并抛出清晰的异常。新增的 519 行 malformed input 测试充分覆盖了各种攻击向量。这与 parquet-java 的类似加固保持一致，体现了 Iceberg 对解析安全性的重视。该提交由 Neelesh Salian 和 Steve Loughran 协作完成。
