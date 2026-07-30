# 提交 0354：Core: Minor updates to AES GCM streams (#9453)

## 提交信息

- **序号**：0354
- **哈希**：e76988b1aa29c3cffca64dc36ccebbfb1c670561
- **短哈希**：e76988b1a
- **日期**：2024-01-14 13:29:59 -0800
- **作者**：ggershinsky
- **提交说明**：Core: Minor updates to AES GCM streams (#9453)
- **PR/Issue**：#9453

## 总体目的

本提交对 Iceberg 核心加密模块（`core/src/main/java/org/apache/iceberg/encryption`）的 AES-GCM 加密流实现做三处"小修补"（minor updates），分别修复 `AesGcmInputStream` 的 EOF 语义、加固 `AesGcmOutputStream` 的关闭后行为，并补充对应的测试断言。这三处改动看似独立，实则共同指向同一个目标：让 AES-GCM 流实现更严格地遵守 Java 标准 `InputStream`/`OutputStream` 契约，并消除"流关闭后仍可写入"或"流关闭后 `getPos()` 返回错误值"等隐患，提升加密模块的健壮性。

第一处改动（`AesGcmInputStream.read`）是契约对齐。Java 标准 `InputStream.read(byte[])` 契约明确要求：当流已到达末尾且无数据可读时，应返回 `-1`（而非抛异常）；只有当 `len == 0` 时才返回 `0`。原实现在 `available() <= 0 && len > 0` 分支抛 `EOFException`，违反了该契约——上层调用方（如 Iceberg 的 `SeekableInputFile` 包装、Hadoop `FSDataInputStream` 桥接等）通常按 `read() == -1` 判断 EOF，遇到 `EOFException` 会误判为 I/O 错误而非正常结束。本提交把 `throw new EOFException()` 改为 `return -1`，让 `AesGcmInputStream` 的 EOF 行为与 Java 标准 `InputStream`、Iceberg `SeekableInputStream` 的其他实现（如 `HadoopInputStream`）一致。这对空文件的读取尤其关键：空文件解密后 `available()` 立即为 0，任何 `read()` 调用都应返回 `-1`，而非抛异常。

第二处改动（`AesGcmOutputStream` 关闭状态管理）是防御性加固。原 `AesGcmOutputStream` 没有 `isClosed` 标志，`close()` 后再次调用 `write()` 不会报错，而是会继续走 `writeHeader()` + `encryptAndWriteBlock()` 逻辑，向已关闭的底层 `targetStream` 写入数据，可能抛出难以诊断的 `IOException: stream closed`（来自底层流）或更隐蔽地写入脏数据。本提交新增 `isClosed` 字段，在 `write(byte[], int, int)` 入口处检查 `if (isClosed) throw new IOException("Writing to closed stream");`——把"写入已关闭流"这一错误提前到 `AesGcmOutputStream` 层面抛出，错误信息明确（"Writing to closed stream"），便于上层定位。同时新增 `finalPosition` 字段，在 `close()` 中先 `finalPosition = getPos()`（此时 `isClosed` 还未置 true，`getPos()` 返回正确的实时位置）再 `isClosed = true`，之后 `getPos()` 直接返回 `finalPosition`——这避免了"关闭后 `getPos()` 返回实时位置（可能因 `currentBlockIndex`/`positionInPlainBlock` 状态变化而不准）"的问题，让"关闭后的最终写入位置"成为一个稳定可查询的值。

第三处改动（`TestGcmStreams` 测试更新）是前两处改动的测试覆盖。(1) `testEmptyFile` 把原断言 `Assertions.assertThatThrownBy(() -> decryptedStream.read(readBytes)).isInstanceOf(EOFException.class)` 改为 `Assert.assertEquals("Read empty stream", -1, decryptedStream.read(readBytes))`——与 `AesGcmInputStream` 的新契约对齐。(2) 在 `testRandomWriteRead` 与 `testAlignedWriteRead` 两个写测试中，`encryptedStream.close()` 之后新增 `Assert.assertEquals("Final position in closed stream", offset, encryptedStream.getPos());`——验证关闭后 `getPos()` 返回的 `finalPosition` 等于预期写入的总字节数 `offset`（在写入循环中 `offset` 累加了每个 chunk 的长度，最终等于 `testFileSize`）。这两处新断言直接覆盖了 `finalPosition` 字段的正确性：若 `close()` 中 `finalPosition = getPos()` 的调用时机错误（如放在 `isClosed = true` 之后，会因 `getPos()` 返回 `finalPosition` 而死循环或返回 0），或 `getPos()` 在关闭后返回了错误的实时位置，断言会失败。

## 如何达成设计目的

实现路径分三步：(1) **`AesGcmInputStream.read` 契约对齐**——在 `available() <= 0 && len > 0` 分支把 `throw new EOFException()` 改为 `return -1`，单行改动。该分支的语义是"流已无数据可读且调用方请求读取大于 0 字节"，按 Java `InputStream.read(byte[], int, int)` 契约应返回 `-1` 表示 EOF。(2) **`AesGcmOutputStream` 关闭状态管理**——新增两个字段 `private boolean isClosed;` 与 `private long finalPosition;`，在构造函数中初始化为 `false` 与 `0`。`write(byte[], int, int)` 方法入口新增 `if (isClosed) throw new IOException("Writing to closed stream");`——在写头部、写数据块之前拦截，确保关闭后不可写入。`getPos()` 方法入口新增 `if (isClosed) return finalPosition;`——关闭后返回缓存的位置而非实时计算。`close()` 方法在 `encryptAndWriteBlock()` 之前新增 `finalPosition = getPos(); isClosed = true;`——关键顺序：先 `getPos()`（此时 `isClosed=false`，返回实时位置 `(long) currentBlockIndex * Ciphers.PLAIN_BLOCK_SIZE + positionInPlainBlock`，即已写入但尚未 flush 的最后一个块的位置），再 `isClosed = true`，最后 `encryptAndWriteBlock()` 把最后一个不完整块加密写入底层流。这样 `finalPosition` 捕获的是"close 调用瞬间的逻辑写入位置"，即用户视角的最终文件大小（明文字节数）。(3) **测试覆盖**——`testEmptyFile` 把 `EOFException` 断言改为 `-1` 断言，对应输入流改动；`testRandomWriteRead` 与 `testAlignedWriteRead` 在 `close()` 后新增 `getPos()` 等于 `offset` 的断言，对应输出流改动。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmInputStream.java`

**修改目的**：把 `read()` 的 EOF 行为从抛 `EOFException` 改为返回 `-1`，对齐 Java 标准 `InputStream` 契约。

**工作逻辑**：在 `public int read(byte[] b, int off, int len) throws IOException` 方法中，原代码 `if (available() <= 0 && len > 0) { throw new EOFException(); }` 改为 `if (available() <= 0 && len > 0) { return -1; }`。该分支位于 `Preconditions.checkArgument(len >= 0, ...)` 与 `if (currentPlainBlockIndex < 0) decryptBlock(0);` 之后、`if (len == 0) return 0;` 之前，语义是"流已解密到末尾、无更多数据可读，且调用方请求读取大于 0 字节"。按 `InputStream.read(byte[], int, int)` 契约，此时应返回 `-1` 表示 EOF，让上层调用方（如 `while ((n = in.read(buf)) != -1)` 循环）正常退出。原 `throw new EOFException()` 会让上层循环抛异常退出，对空文件读取尤其不友好（空文件解密后 `available()` 立即为 0，首次 `read()` 即抛异常）。注意：`EOFException` 在 Java 标准库中通常用于 `DataInputStream.readFully()` 等"必须读到指定字节数"的场景，普通 `read()` 不应抛此异常。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmOutputStream.java`

**修改目的**：新增 `isClosed` 标志与 `finalPosition` 缓存，防止关闭后写入并保证关闭后 `getPos()` 返回稳定值。

**工作逻辑**：(1) 字段新增——在 `private boolean lastBlockWritten;` 之后新增 `private boolean isClosed;` 与 `private long finalPosition;`。(2) 构造函数初始化——在 `this.lastBlockWritten = false;` 之后新增 `this.isClosed = false;` 与 `this.finalPosition = 0;`。(3) `write(byte[] b, int off, int len)` 方法入口（在 `if (!isHeaderWritten) writeHeader();` 之前）新增 `if (isClosed) { throw new IOException("Writing to closed stream"); }`——这是防御性检查，确保 `close()` 后任何 `write()` 调用立即抛 `IOException`，错误信息明确指向"流已关闭"，而非让底层 `targetStream` 抛晦涩的 `stream closed` 异常或写入脏数据。(4) `getPos()` 方法入口（在 `return (long) currentBlockIndex * Ciphers.PLAIN_BLOCK_SIZE + positionInPlainBlock;` 之前）新增 `if (isClosed) { return finalPosition; }`——关闭后返回缓存的最终位置。这避免了两个潜在问题：(a) 关闭后 `currentBlockIndex`/`positionInPlainBlock` 的语义可能不明确（`close()` 中调用了 `encryptAndWriteBlock()` 但未重置这两个字段，它们仍反映最后一个块的状态，`getPos()` 实时计算可能仍返回正确的"最终位置"，但语义上是"实时位置"而非"最终位置"，容易在后续重构中出错）；(b) 显式缓存 `finalPosition` 让"关闭后的位置查询"成为一个明确的语义概念，而非依赖实时计算的副作用。(5) `close()` 方法在 `if (!isHeaderWritten) writeHeader();` 之后、`encryptAndWriteBlock();` 之前新增 `finalPosition = getPos(); isClosed = true;`——关键顺序：先调用 `getPos()`（此时 `isClosed` 仍为 `false`，`getPos()` 走实时计算分支返回 `(long) currentBlockIndex * Ciphers.PLAIN_BLOCK_SIZE + positionInPlainBlock`，即当前累积的明文字节数），再把 `isClosed` 置 `true`（让后续 `getPos()` 走缓存分支），最后 `encryptAndWriteBlock()` 把 `plainBlock` 中剩余的不满一块数据加密写入底层流。这样 `finalPosition` 捕获的是"close 调用瞬间、最后一个块尚未 flush 时的明文字节数"，即用户视角的最终明文文件大小。

### `core/src/test/java/org/apache/iceberg/encryption/TestGcmStreams.java`

**修改目的**：更新测试以验证输入流的新 EOF 契约与输出流的新关闭后行为。

**工作逻辑**：(1) `import` 调整——删除 `import java.io.EOFException;`（不再使用）。(2) `testEmptyFile` 测试方法中，原断言 `Assertions.assertThatThrownBy(() -> decryptedStream.read(readBytes)).isInstanceOf(EOFException.class);` 改为 `Assert.assertEquals("Read empty stream", -1, decryptedStream.read(readBytes));`——验证空文件解密流读取返回 `-1`（与 `AesGcmInputStream` 新契约对齐），而非抛 `EOFException`。注意该测试紧接着还有一段验证"错误 AAD 触发 GCM tag 校验失败"的逻辑（`Assertions.assertThatThrownBy(...).isInstanceOf(RuntimeException.class).hasCauseInstanceOf(AEADBadTagException.class).hasMessageContaining("GCM tag check failed")`），这段不变——AAD 校验失败仍抛 `RuntimeException`（包装 `AEADBadTagException`），与 EOF 语义无关。(3) `testRandomWriteRead` 测试方法中，在 `encryptedStream.close();` 之后新增 `Assert.assertEquals("Final position in closed stream", offset, encryptedStream.getPos());`——`offset` 是写入循环中累加的写入字节数（初始 0，每次 `encryptedStream.write(testFileContents, offset, chunkLen)` 后 `offset += chunkLen`），最终等于 `testFileSize`。该断言验证：关闭后 `getPos()` 返回的 `finalPosition` 等于 `testFileSize`，即"用户写入的总明文字节数"。该测试覆盖 5 种文件大小（0.5 块、1.5 块、1 块、1 块-1、1 块+1）× 3 种 AES 密钥长度（16/24/32 字节）= 15 组参数化场景，每组都验证关闭后位置正确。(4) `testAlignedWriteRead` 测试方法中，同样在 `encryptedStream.close();` 之后新增 `Assert.assertEquals("Final position in closed stream", offset, encryptedStream.getPos());`——`offset` 同样是写入循环累加值，最终等于 `testFileSize`。该测试覆盖 3 种文件大小（1 块、1 块+1、1 块-1），验证对齐写入场景下关闭后位置正确。两处新断言共同确保 `finalPosition` 在"块对齐"与"非块对齐"两种写入模式下都正确捕获最终位置。

## 小结

本次提交对 Iceberg 核心 AES-GCM 加密流做三处小修补，提升契约合规性与健壮性：(1) `AesGcmInputStream.read` 在 EOF 时返回 `-1` 而非抛 `EOFException`，对齐 Java 标准 `InputStream` 契约，让上层调用方（按 `read() == -1` 判断 EOF 的循环）能正常处理空文件与流末尾，避免误判为 I/O 错误；(2) `AesGcmOutputStream` 新增 `isClosed` 标志与 `finalPosition` 缓存，`write()` 入口检查 `isClosed` 抛 `IOException("Writing to closed stream")` 防止关闭后写入，`close()` 中先 `finalPosition = getPos()` 再 `isClosed = true` 缓存最终明文位置，`getPos()` 关闭后返回 `finalPosition` 而非实时计算，让"关闭后的最终写入位置"成为稳定可查询的语义概念；(3) `TestGcmStreams` 测试同步更新：`testEmptyFile` 把 `EOFException` 断言改为 `-1` 断言（对应输入流改动），`testRandomWriteRead` 与 `testAlignedWriteRead` 在 `close()` 后新增 `getPos() == offset` 断言（对应输出流改动），覆盖块对齐与非块对齐、5 种文件大小 × 3 种密钥长度共 15+3 组参数化场景。三处改动共同让 AES-GCM 流实现更严格地遵守 Java `InputStream`/`OutputStream` 契约，消除"关闭后写入"与"关闭后位置查询不准"两个隐患。
