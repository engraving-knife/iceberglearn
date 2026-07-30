# 提交 4023：Core: Add EagerInputFile and EagerInputStream to buffer files below a size threshold (#16729)

## 提交信息

- **序号**：4023 / 4088
- **哈希**：80ec024326e988a7135e5c4bc906aa28bf25bffd
- **短哈希**：80ec02432
- **日期**：2026-07-13 09:52:22 -0500
- **作者**：Varun Lakhyani
- **提交说明**：Core: Add EagerInputFile and EagerInputStream to buffer files below a size threshold (#16729)
- **PR/Issue**：#16729

## 总体目的

本提交新增 `EagerInputFile` 和 `EagerInputStream` 两个类，用于在首次访问时一次性预取（eagerly fetch）整个文件到内存，从而将多次对象存储（object store）请求合并为一次。

背景：对象存储（如 S3）每次 range 请求都有延迟和开销。Iceberg 读取小文件（如 metadata 文件、小数据文件）时，可能发起多次 range 请求（如先读 header、再读 footer、再读中间块）。对于小文件，一次性把整个文件读入内存，后续所有读取都从内存字节数组进行，可以显著减少远程请求次数和延迟。

`EagerInputFile` 是 `InputFile` 的装饰器，在 `newStream()` 时一次性读取整个文件到 `byte[]`，返回 `EagerInputStream`；`EagerInputStream` 是基于字节数组的 `SeekableInputStream` + `RangeReadable` 实现，支持 seek、range read、tail read，全部从内存操作。

注意提交说明提到"below a size threshold"，但实际实现中 `EagerInputFile` 本身不做阈值判断（阈值门控已在后续 review 中移除，由调用方决定是否包装），构造时只校验 fileSize 非负且不超过 `Integer.MAX_VALUE`（因为用 `byte[]` 承载）。

## 如何达成设计目的

设计思路：
1. `EagerInputFile` 装饰一个底层 `InputFile`，持有 `fileSize`。`newStream()` 时分配 `byte[fileSize]`，用 `IOUtil.readFully` 一次性读满，包装为 `EagerInputStream` 返回。
2. `EagerInputStream` 内部用 `ByteArrayInputStream` 作为 delegate 实现 `read`/`skip`/`available`/`getPos`；`seek` 通过 reset + skip 实现；实现 `RangeReadable` 的 `readFully(pos, ...)` 和 `readTail(...)` 直接用 `System.arraycopy` 从字节数组拷贝。
3. `close()` 是空操作（内存流无需释放资源）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/EagerInputFile.java` (+71/-0 lines, 新文件)

**修改目的**：实现一次性预取整个文件的 InputFile 装饰器。

**工作逻辑**：
```java
public class EagerInputFile implements InputFile {
  private final InputFile delegate;
  private final long fileSize;

  public EagerInputFile(InputFile delegate, long fileSize) {
    // 校验 delegate 非空、fileSize >= 0 且 <= Integer.MAX_VALUE
  }

  @Override
  public SeekableInputStream newStream() {
    byte[] bytes = new byte[(int) fileSize];
    try (SeekableInputStream src = delegate.newStream()) {
      IOUtil.readFully(src, bytes, 0, bytes.length);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to fetch file: %s", delegate.location());
    }
    return new EagerInputStream(bytes);
  }
}
```
`getLength` 返回 fileSize，`location`/`exists` 委托给 delegate。

### `core/src/main/java/org/apache/iceberg/io/EagerInputStream.java` (+99/-0 lines, 新文件)

**修改目的**：基于字节数组的可 seek、可 range read 的流。

**工作逻辑**：
- 持有 `byte[] contents` 和 `ByteArrayInputStream delegate`。
- `getPos()`：`contents.length - delegate.available()`。
- `seek(newPos)`：`delegate.reset()` 后 `skip(newPos)`，若 skip 不足抛 `EOFException`。
- `read()`/`read(byte[],int,int)`/`skip(n)`/`available()` 委托给 `ByteArrayInputStream`。
- `readFully(long pos, byte[] buffer, int offset, int length)`：校验边界后 `System.arraycopy(contents, (int) pos, buffer, offset, length)`。
- `readTail(byte[] buffer, int offset, int length)`：从数组末尾拷贝 `Math.min(length, contents.length)` 字节。
- `close()` 空操作。

### `core/src/test/java/org/apache/iceberg/io/TestEagerInputFile.java` (+234/-0 lines, 新文件)

**修改目的**：测试 `EagerInputFile`/`EagerInputStream` 的各种读取场景。

**工作逻辑**：测试覆盖 newStream 一次性读取、seek、readFully range read、readTail、边界和异常场景（具体见文件）。

## 总结

本提交新增 `EagerInputFile`/`EagerInputStream`，提供将整个文件一次性预取到内存的能力，用于将多次对象存储 range 请求合并为一次，优化小文件读取延迟。设计为装饰器模式，底层流只需读取一次，后续所有 seek/range/tail 读取均从内存字节数组进行。这是 Iceberg 读取性能优化的基础设施，后续可由调用方根据文件大小阈值决定是否包装。配套补齐了完整测试。
