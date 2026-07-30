# 提交 3689：Spark: Fix dropped file length in SerializableFileIOWithSize (#16284)

## 提交信息

- **序号**：3689 / 4088
- **哈希**：575446ebf47e450e4b0016323d490767d7e5707b
- **短哈希**：575446ebf
- **日期**：2026-05-12 17:25:58 +0200
- **作者**：Ajay Yadav
- **提交说明**：Spark: Fix dropped file length in SerializableFileIOWithSize (#16284)
- **PR/Issue**：#16284

## 总体目的

这个提交修复了 `SerializableFileIOWithSize` 类中一个性能 bug。`SerializableFileIOWithSize` 是 Spark 模块中用于包装 `FileIO` 的可序列化代理类，它拦截 `FileIO` 的方法调用以添加额外行为。

问题在于：当调用 `FileIO.newInputFile(path, length)` 方法（带文件长度参数的版本）时，`SerializableFileIOWithSize` 没有重写这个方法，导致调用回退到默认实现 `newInputFile(path)`（不带长度参数的版本）。这意味着文件长度参数被丢弃了。

后果是：底层 IO 模块（如 `GCSFileIO`）在读取列式文件 footer 时，由于不知道文件长度，需要执行昂贵且同步的对象元数据 API 调用来获取文件大小。这在云存储场景下会显著影响查询性能。

## 如何达成设计目的

通过在所有受影响的 Spark 模块（v3.5、v4.0、v4.1）的 `SerializableFileIOWithSize` 类中添加 `newInputFile(String path, long length)` 方法的重写，将调用委托给底层 `fileIO` 并保留 length 参数。同时为每个模块添加对应的单元测试。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SerializableFileIOWithSize.java` (+5 lines)

**修改目的**：添加缺失的 `newInputFile(String path, long length)` 方法重写。

**工作逻辑**：

```java
@Override
public InputFile newInputFile(String path, long length) {
  return fileIO.newInputFile(path, length);
}
```

该方法将带有 length 参数的调用委托给底层 `fileIO`，确保 length 参数被正确传递。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSerializableFileIOWithSize.java` (new file, +51 lines)

**修改目的**：添加单元测试验证修复。

**工作逻辑**：

```java
@Test
void newInputFileWithLength() {
  FileIO mockFileIO = mock(FileIO.class);
  FileIO serializableFileIO = SerializableFileIOWithSize.wrap(mockFileIO);
  String path = "gs://bucket/path/to/file.parquet";
  long length = 1024L;

  serializableFileIO.newInputFile(path, length);

  verify(mockFileIO).newInputFile(path, length);
}
```

使用 Mockito mock FileIO，验证调用 `SerializableFileIOWithSize.newInputFile(path, length)` 时，底层 FileIO 的 `newInputFile(path, length)` 方法（带 length 参数）被调用，而非不带参数的版本。测试路径特意使用 `gs://` 前缀，呼应了提交说明中提到的 GCSFileIO 场景。

另一个测试 `newInputFileWithoutLength` 验证不带 length 参数的调用仍然正确委托。

### `spark/v4.0/spark/...` 和 `spark/v4.1/spark/...` (各 +5/+51 lines)

**修改目的**：在 Spark 4.0 和 4.1 模块中应用相同的修复和测试。

**工作逻辑**：与 v3.5 完全相同的修改。

## 总结

这是一个重要的性能修复提交，解决了 `SerializableFileIOWithSize` 丢弃文件长度参数导致的性能问题。在云存储场景下，这个 bug 会导致不必要的同步元数据 API 调用，显著影响查询性能。修复方式简洁直接，同时添加了完善的单元测试防止回归。值得注意的是，该修复仅应用于 v3.5/v4.0/v4.1，v3.4 可能已经修复或不受影响。
