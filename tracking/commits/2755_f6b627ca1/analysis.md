# 提交 2755：Core: Explicitly close SeekableInput in the AvroIterable (#14322)

## 提交信息

- **序号**：2755 / 4088
- **哈希**：f6b627ca1762dbd594c6c1bfe753139c61889124
- **短哈希**：f6b627ca1
- **日期**：2025-10-16 08:38:46 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Core: Explicitly close SeekableInput in the AvroIterable (#14322)
- **PR/Issue**：#14322

## 总体目的

本提交修复了 `AvroIterable` 中一个资源泄漏缺陷：当 Avro 文件格式不正确时，`DataFileReader.openReader` 会抛出 `InvalidAvroMagicException`（或其它 `IOException`），导致已打开的 `SeekableInput` 输入流未被关闭，从而发生文件句柄泄漏。

背景在于：`AvroIterable.newFileReader()` 方法原先将 `file.newStream()` 创建的输入流直接内联到 `DataFileReader.openReader(...)` 调用中，没有保留对底层流的引用。一旦 `openReader` 抛出异常，控制流直接进入 catch 块并重新抛出 `RuntimeIOException`，但底层输入流无人关闭。在读取大量损坏的 Avro 文件（例如 manifest 文件、metadata 文件）的场景下，这种泄漏会逐步耗尽文件句柄，最终导致系统无法打开新文件。

提交说明明确指出："When avro file is malformed, DataFileReader.openReader can exit by throwing InvalidAvroMagicException which will leave input stream open and in the result, leaked."

## 如何达成设计目的

修复思路是显式管理 `SeekableInput` 的生命周期：

1. 将 `AvroIO.stream(file.newStream(), file.getLength())` 的结果赋值给一个局部变量 `stream`，保留引用。
2. 在 `DataFileReader.openReader(stream, reader)` 成功时正常返回（此时流的所有权移交给了 `DataFileReader`，由其负责关闭）。
3. 在 `openReader` 抛出 `IOException` 的 catch 块中，显式调用 `stream.close()` 关闭输入流，并忽略 close 本身可能抛出的异常（避免掩盖原始异常）。

此外新增了单元测试 `TestAvroIterable`，使用 Mockito 模拟 `DataFileReader.openReader` 抛出 `IOException`，通过反射调用私有方法 `newFileReader`，并验证 `seekableInput.close()` 被调用至少一次。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/AvroIterable.java` (+13/-2 lines)

**修改目的**：在 `newFileReader()` 方法中显式关闭发生异常时的输入流。

**工作逻辑**：
- 新增 import `org.apache.avro.file.SeekableInput`。
- 在 `newFileReader()` 中，先声明 `SeekableInput stream = null`，将 `AvroIO.stream(file.newStream(), file.getLength())` 赋值给 `stream`，再传给 `DataFileReader.openReader(stream, reader)`。
- 在 catch `IOException` 块中，若 `stream != null`，则尝试 `stream.close()`，并对 close 抛出的 `IOException` 静默忽略（注释 `// Ignore close exception`），然后照常抛出 `RuntimeIOException`。
- 正常路径下 `openReader` 成功后，`DataFileReader` 接管流的关闭责任，无需在此处关闭。

### `core/src/test/java/org/apache/iceberg/avro/TestAvroIterable.java` (+67/-0 lines, 新文件)

**修改目的**：新增单元测试验证异常路径下输入流被关闭。

**工作逻辑**：
- 使用 Mockito 的 `mockStatic` 模拟 `AvroIO` 和 `DataFileReader` 两个静态方法。
- 模拟 `InputFile.newStream()` 返回 mock 的 `SeekableInputStream`，`AvroIO.stream(...)` 返回 mock 的 `SeekableInput`。
- 模拟 `DataFileReader.openReader(seekableInput, datumReader)` 抛出 `IOException`。
- 通过反射获取 `AvroIterable` 的私有方法 `newFileReader` 并设为可访问，调用后断言抛出的异常 cause 为 `RuntimeIOException`。
- 关键断言：`verify(seekableInput, atLeastOnce()).close()`，验证输入流在异常路径下被关闭。

## 总结

本提交修复了一个实际的资源泄漏缺陷：当 Avro 文件损坏导致 `DataFileReader.openReader` 抛异常时，底层输入流未被关闭。修复方式简洁直接——保留流引用并在异常路径显式关闭。配套的 Mockito 单元测试通过模拟异常场景，确保关闭行为被正确执行。该修复有助于在读取大量损坏 Avro 文件时避免文件句柄耗尽，提升了 Iceberg 在异常输入下的健壮性。该 PR 由 Yuya Ebihara 发起，Eduard Tudenhoefner 和 Mateusz Gajewski 共同参与。
