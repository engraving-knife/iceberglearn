# 提交 3695：API: Remove unnecessary EOFException in FileRange constructor. (#15973)

## 提交信息

- **序号**：3695 / 4088
- **哈希**：947a7b7913d201544262fbc0a50784d23f3f5af5
- **短哈希**：947a7b791
- **日期**：2026-05-12 13:37:45 -0700
- **作者**：Mukund Thakur
- **提交说明**：API: Remove unnecessary EOFException in FileRange constructor. (#15973)
- **PR/Issue**：#15973

## 总体目的

这个提交移除了 `FileRange` 构造函数中不必要的 `EOFException` 声明。`FileRange` 是 Iceberg IO 模块中用于表示文件读取范围的类，包含偏移量、长度和异步读取future。

此前的构造函数签名为 `FileRange(CompletableFuture<ByteBuffer>, long offset, int length) throws EOFException`，但实际上构造函数内部从未抛出 `EOFException`——它仅使用 `Preconditions` 进行参数校验，会抛出 `IllegalArgumentException` 或 `NullPointerException`，而非 `EOFException`。

这个不必要的 `throws EOFException` 声明强制所有调用者必须处理或声明 `EOFException`，增加了代码复杂度。例如 `ParquetIO` 中不得不使用 try-catch 包装构造调用，将 `EOFException` 转换为 `RuntimeIOException`，这是完全不必要的代码。

## 如何达成设计目的

通过以下修改实现：
1. 从 `FileRange` 构造函数中移除 `throws EOFException` 声明
2. 简化 `ParquetIO` 中调用 `FileRange` 构造函数的代码，移除不必要的 try-catch

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/FileRange.java` (+1/-3 lines)

**修改目的**：移除构造函数的 EOFException 声明。

**工作逻辑**：

```java
-import java.io.EOFException;
 // ...
-public FileRange(CompletableFuture<ByteBuffer> byteBuffer, long offset, int length)
-    throws EOFException {
+public FileRange(CompletableFuture<ByteBuffer> byteBuffer, long offset, int length) {
   Preconditions.checkNotNull(byteBuffer, "byteBuffer can't be null");
   Preconditions.checkArgument(length >= 0, "Invalid length: %s in range (must be >= 0)", length);
   Preconditions.checkArgument(offset >= 0, "Invalid offset: %s in range (must be >= 0)", offset);
```

移除了 `EOFException` 的导入和构造函数的 `throws` 声明。构造函数体保持不变，仍然使用 `Preconditions` 进行参数校验。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetIO.java` (+4/-13 lines)

**修改目的**：简化 FileRange 构造调用，移除不必要的 try-catch。

**工作逻辑**：

```java
-                try {
-                  return new FileRange(
-                      parquetFileRange.getDataReadFuture(),
-                      parquetFileRange.getOffset(),
-                      parquetFileRange.getLength());
-                } catch (EOFException e) {
-                  throw new RuntimeIOException(
-                      e,
-                      "Failed to create range file for offset: %s and length: %s",
-                      parquetFileRange.getOffset(),
-                      parquetFileRange.getLength());
-                }
+                return new FileRange(
+                    parquetFileRange.getDataReadFuture(),
+                    parquetFileRange.getOffset(),
+                    parquetFileRange.getLength());
```

移除了 try-catch 包装和 `EOFException` 导入，直接构造 `FileRange` 对象。代码从 12 行简化为 4 行。

## 总结

这是一个 API 清理提交，移除了 `FileRange` 构造函数中不必要的 `EOFException` 声明。这是一个 breaking change（虽然实际不会影响运行时行为），因为构造函数签名发生了变化。但由于 `EOFException` 从未被实际抛出，所有调用者只需移除相应的 try-catch 或 throws 声明即可适配。这种 API 清理减少了不必要的异常处理代码，提升了代码简洁性。
