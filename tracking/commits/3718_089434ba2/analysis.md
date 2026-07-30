# 提交 3718：Core: Fix ByteBufferInputStream.read() to return -1 at EOF (#16167)

## 提交信息

- **序号**：3718 / 4088
- **哈希**：089434ba2b3881f0fbd31968cf763001ed324c7a
- **短哈希**：089434ba2
- **日期**：2026-05-16 21:55:51 +0200
- **作者**：Sachin Ranjalkar
- **提交说明**：Core: Fix ByteBufferInputStream.read() to return -1 at EOF (#16167)
- **PR/Issue**：#16167（原始 issue #16127）

## 总体目的

本提交修复了 `ByteBufferInputStream`（包括 `MultiBufferInputStream` 和 `SingleBufferInputStream`）的 `read()` 方法在到达流末尾（EOF）时的行为不符合 `java.io.InputStream` 契约的 Bug。

根据 `InputStream.read()` 的契约，当到达流末尾时应当返回 `-1`，而不是抛出异常。然而 Iceberg 的这两个实现类在 EOF 时抛出的是 `EOFException`。这种异常行为破坏了 `InputStream` 的标准契约，会使得依赖 `read()` 返回 `-1` 来检测 EOF 的下游代码（例如各种 IO 工具类、序列化框架、ORC/Parquet 读取器等）在到达流末尾时意外抛出异常，导致读取失败或需要额外的 try-catch 处理。

修复此问题后，调用方可以像使用标准 `InputStream` 一样通过判断返回值 `-1` 来检测 EOF，提升了与标准库及第三方库的兼容性，同时减少了不必要的异常开销。

## 如何达成设计目的

修复非常直接：将 `MultiBufferInputStream.read()` 和 `SingleBufferInputStream.read()` 中原本抛出 `EOFException` 的两处代码改为 `return -1`。同时增强测试覆盖，新增 `assertAtEOF` 辅助断言方法统一验证 EOF 状态下 `read()`、`read(byte[])`、`getPos()`、`available()` 的行为，并新增 `testEmptyStream` 和 `testDrainedMultiBufferStream` 两个测试用例，分别覆盖空流和 drained 多缓冲区流的 EOF 行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/MultiBufferInputStream.java` (+2/-2 lines)

**修改目的**：修复多缓冲区输入流在 EOF 时抛异常的行为。

**工作逻辑**：
`read()` 方法中有两处 EOF 检测：
1. 当 `current == null`（初始就无缓冲区，即空流）时：由 `throw new EOFException()` 改为 `return -1`。
2. 在循环中 `nextBuffer()` 返回 false（无更多缓冲区可切换）时：由 `throw new EOFException()` 改为 `return -1`。

```java
@Override
public int read() throws IOException {
  if (current == null) {
    return -1;  // 原为 throw new EOFException();
  }
  while (true) {
    ...
    } else if (!nextBuffer()) {
      return -1;  // 原为 throw new EOFException();
    }
  }
}
```

### `core/src/main/java/org/apache/iceberg/io/SingleBufferInputStream.java` (+1/-1 lines)

**修改目的**：修复单缓冲区输入流在 EOF 时抛异常的行为。

**工作逻辑**：
`read()` 方法检测 `buffer.hasRemaining()` 为 false（缓冲区已读完）时，由 `throw new EOFException()` 改为 `return -1`。

```java
@Override
public int read() throws IOException {
  if (!buffer.hasRemaining()) {
    return -1;  // 原为 throw new EOFException();
  }
  return buffer.get() & 0xFF;
}
```

### `core/src/test/java/org/apache/iceberg/io/TestByteBufferInputStreams.java` (+28/-4 lines)

**修改目的**：增强 EOF 行为的测试覆盖，确保修复后的行为正确且不再回归。

**工作逻辑**：
- 新增 `assertAtEOF` 辅助方法，对 EOF 状态下的流进行多重断言：
  - 连续调用 `read()` 始终返回 `-1`（确保幂等）
  - 调用 `read(byte[])` 返回 `-1`
  - `getPos()` 不前进（位置不越界）
  - `available()` 为 0
- 将原测试中多处只断言 `available() == 0` 的地方替换为 `assertAtEOF(stream)`，增强断言强度。
- 将原 `testReadByteByByte` 中 `assertThatThrownBy(stream::read).isInstanceOf(EOFException.class)` 改为 `assertAtEOF(stream)`，反映新的契约行为。
- 新增 `testEmptyStream`：对空流（`ByteBuffer.allocate(0)`、两个空 buffer、空列表）验证立即处于 EOF 状态。
- 新增 `testDrainedMultiBufferStream`：构造两个 buffer（5 字节内容），读完 5 字节后验证 EOF，覆盖 `MultiBufferInputStream` 中 `nextBuffer() -> return -1` 的代码路径。

## 总结

本提交修复了 `ByteBufferInputStream` 系列违反 `InputStream.read()` 标准契约的 Bug，将 EOF 行为从抛出 `EOFException` 改为返回 `-1`。修复使得 Iceberg 的字节缓冲输入流与标准 Java IO 契约保持一致，避免下游使用者因 EOF 异常而意外失败，提升了库的兼容性与健壮性。配套增强的测试覆盖了空流、drained 多缓冲区流等边界场景，并新增统一的 `assertAtEOF` 断言以防止回归。这是一个面向正确性的核心修复。
