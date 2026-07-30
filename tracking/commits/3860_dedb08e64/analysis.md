# 提交 3860：API, Core: Reuse VariantUtil methods through ByteBuffers (#16748)

## 提交信息

- **序号**：3860 / 4088
- **哈希**：dedb08e645a5337bb938dfcc2571add4521445c1
- **短哈希**：dedb08e64
- **日期**：2026-06-11 13:48:45 -0600
- **作者**：Ryan Blue
- **提交说明**：API, Core: Reuse VariantUtil methods through ByteBuffers (#16748)
- **PR/Issue**：#16748

## 总体目的

本提交是一个代码重构，将 `VariantUtil` 中的 ByteBuffer 读写工具方法移动到 `ByteBuffers` 工具类中，实现代码复用。`VariantUtil` 是 Variant 数据类型（Iceberg 支持的半结构化数据类型）的内部工具类，包含大量小端序 ByteBuffer 读写方法（如 `readLittleEndianUnsigned`、`writeLittleEndianUnsigned`、`readByte` 等）。

这些 ByteBuffer 操作方法本质上是通用的字节缓冲区操作，不特定于 Variant 格式，将它们放在 `VariantUtil` 中限制了复用性。其他需要操作 ByteBuffer 的模块（如 Mumbling Bitmap 实现）无法使用这些方法。本提交将这些通用方法迁移到 `api` 模块的 `ByteBuffers` 类中，使其成为公共 API 的一部分，可供所有模块复用。

## 如何达成设计目的

整体设计步骤：

1. **在 `ByteBuffers` 中新增方法**：将 `VariantUtil` 中的 `writeByte`、`writeLittleEndianUnsigned`、`readLittleEndianInt8`、`readLittleEndianInt16`、`readByte`、`readLittleEndianUnsigned`、`readLittleEndianInt32`、`readLittleEndianInt64` 方法迁移到 `ByteBuffers`，访问修饰符从 package-private 改为 `public`。

2. **移除 `VariantUtil` 中的对应方法**：删除已迁移的方法，包括 `writeBufferAbsolute`（被直接使用 `buffer.put()` 替换）。

3. **更新所有调用点**：将 `SerializedArray`、`SerializedMetadata`、`SerializedObject`、`SerializedPrimitive`、`SerializedValue` 等 Variant 序列化类中的 `VariantUtil.readXxx()` 调用改为 `ByteBuffers.readXxx()`。

4. **更新测试**：将 `VariantTestUtil` 中的测试工具方法调用更新为使用 `ByteBuffers`。新增 `TestByteBuffers` 测试类。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/ByteBuffers.java` (+63/-1 lines)

**修改目的**：新增 ByteBuffer 读写工具方法。

**工作逻辑**：
新增 8 个 public static 方法：
- `writeByte(ByteBuffer, int value, int offset)`：在指定偏移写入单个字节。
- `writeLittleEndianUnsigned(ByteBuffer, int value, int offset, int size)`：写入小端序无符号整数（支持 1-4 字节）。
- `readLittleEndianInt8(ByteBuffer, int offset)`：读取 1 字节有符号值。
- `readLittleEndianInt16(ByteBuffer, int offset)`：读取 2 字节小端序有符号值。
- `readByte(ByteBuffer, int offset)`：读取 1 字节无符号值（0-255）。
- `readLittleEndianUnsigned(ByteBuffer, int offset, int size)`：读取小端序无符号整数（支持 1-4 字节）。
- `readLittleEndianInt32(ByteBuffer, int offset)`：读取 4 字节小端序整数。
- `readLittleEndianInt64(ByteBuffer, int offset)`：读取 8 字节小端序长整数。

所有方法基于 `buffer.position() + offset` 计算绝对位置进行操作。

### `api/src/main/java/org/apache/iceberg/variants/VariantUtil.java` (-73 lines)

**修改目的**：移除已迁移的方法。

**工作逻辑**：
删除 `writeBufferAbsolute`、`writeByte`、`writeLittleEndianUnsigned`、`readLittleEndianInt8`、`readLittleEndianInt16`、`readByte`、`readLittleEndianUnsigned`、`readLittleEndianInt32`、`readLittleEndianInt64` 方法。移除不再需要的 `Preconditions` 导入。

### `api/src/main/java/org/apache/iceberg/variants/SerializedArray.java` (+7/-2 lines)

**修改目的**：更新调用点。

**工作逻辑**：
将 `VariantUtil.readLittleEndianUnsigned` 替换为 `ByteBuffers.readLittleEndianUnsigned`（3 处）。

### `api/src/main/java/org/apache/iceberg/variants/SerializedMetadata.java` (+13/-4 lines)

**修改目的**：更新调用点。

**工作逻辑**：
将 `VariantUtil.readByte` 和 `VariantUtil.readLittleEndianUnsigned` 替换为 `ByteBuffers` 对应方法（4 处）。将 `VariantUtil.writeBufferAbsolute(buffer, offset, value)` 替换为直接 `buffer.put(offset, value, value.position(), value.remaining())`。

### `api/src/main/java/org/apache/iceberg/variants/SerializedObject.java` (+9/-3 lines)

**修改目的**：更新调用点。

**工作逻辑**：
将 `VariantUtil.readLittleEndianUnsigned` 替换为 `ByteBuffers.readLittleEndianUnsigned`（4 处）。

### `api/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java` (+25/-12 lines)

**修改目的**：更新调用点。

**工作逻辑**：
将所有 `VariantUtil.readXxx` 调用替换为 `ByteBuffers.readXxx`（约 10 处），涵盖 INT8、INT16、INT32、INT64、DATE、TIMESTAMP、TIME、DECIMAL、BINARY、STRING 等类型的读取。

### `api/src/main/java/org/apache/iceberg/variants/SerializedValue.java` (+2/-1 lines)

**修改目的**：更新 `writeTo` 默认方法。

**工作逻辑**：
将 `VariantUtil.writeBufferAbsolute(buffer, offset, value)` 替换为 `buffer.put(offset, value, value.position(), value.remaining())`。

### `api/src/main/java/org/apache/iceberg/variants/VariantValue.java` (+3/-1 lines)

**修改目的**：更新调用点。

### 测试文件（多处）

**修改目的**：更新测试调用并新增 `TestByteBuffers`。

**工作逻辑**：
- `VariantTestUtil.java`：更新工具方法调用。
- `PrimitiveWrapper.java`、`ShreddedObject.java`、`ValueArray.java`、`Variants.java`：更新 `writeBufferAbsolute` 和其他调用。
- 新增 `TestByteBuffers.java`（从原 `TestVariantUtil` 重命名/调整）。

## 总结

本提交是一个代码重构，将 `VariantUtil` 中的通用 ByteBuffer 读写方法迁移到 `ByteBuffers` 工具类，提升代码复用性。这些方法现在是 `api` 模块公共 API 的一部分，可供其他模块（如 Mumbling Bitmap 实现）复用。重构不改变任何运行时行为，纯结构调整，降低了 `VariantUtil` 的职责范围，使其更专注于 Variant 格式特有的逻辑。
