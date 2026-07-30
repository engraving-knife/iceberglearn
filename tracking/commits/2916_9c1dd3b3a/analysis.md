# 提交 2916：Core: Wrong reported length of encrypted Puffin files (#14645)

## 提交信息

- **序号**：2916 / 4088
- **哈希**：9c1dd3b3a51aded4a6a079dfd49d0f94ec88f3cd
- **短哈希**：9c1dd3b3a
- **日期**：2025-11-24 15:36:13 +0100
- **作者**：Adam Szita
- **提交说明**：Core: Wrong reported length of encrypted Puffin files
- **PR/Issue**：#14645

## 总体目的

Iceberg 使用 Puffin 文件格式存储统计信息（如 Bloom filter、统计直方图等）。当 Puffin 文件被写入后，其文件长度会通过 `PuffinWriter#length()` 报告并记录到 manifest 文件中。然而对于加密的 Puffin 文件，此前的实现使用底层 `PositionOutputStream.getPos()` 来获取文件长度，但这返回的是未加密内容的逻辑位置，而非实际写入存储的字节长度。

问题在于，加密输出流（如 `AesGcmOutputStream`）在 `close()` 调用之前可能不会将最后的加密块（如 GCM 认证标签、填充字节等）写入底层流，因此 `getPos()` 在 close 前返回的长度小于实际文件大小。这导致 manifest 中记录的 Puffin 文件长度不正确，可能影响后续读取时的完整性校验或范围请求。

本提交修复了这一问题，确保在 close 之后获取实际存储长度，并使用 `storedLength()` 方法获取加密流的真实文件大小。

## 如何达成设计目的

核心改动是将 `outputStream.close()` 的调用从 `close()` 方法移到 `finish()` 方法中（在计算 fileSize 之前），确保所有加密流的最终字节已写入。同时将文件大小的获取从 `outputStream.getPos()` 改为 `outputStream.storedLength()`，后者返回实际存储的字节数（对于加密流是加密后的长度）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/puffin/PuffinWriter.java` (+5/-3 lines)

**修改目的**：修复加密 Puffin 文件的长度计算错误。

**工作逻辑**：
原先 `close()` 方法在 `finish()` 后调用 `outputStream.close()`，而 `finish()` 中通过 `outputStream.getPos()` 获取 fileSize。修改后：
1. 从 `close()` 中移除 `outputStream.close()` 调用
2. 在 `finish()` 方法中，先 `writeFooter()`，然后在计算 fileSize 之前调用 `outputStream.close()`，确保加密流的最终字节已写入
3. 将 `this.fileSize = Optional.of(outputStream.getPos())` 改为 `this.fileSize = Optional.of(outputStream.storedLength())`，获取实际存储长度

注释说明：某些流（如 AesGcmOutputStream）只有在 close() 被调用后才写入最后的字节。

### `core/src/test/java/org/apache/iceberg/puffin/TestPuffinWriter.java` (+43/-0 lines)

**修改目的**：添加参数化测试验证加密和非加密场景下的文件长度计算。

**工作逻辑**：
新增 `testFileSizeCalculation` 参数化测试，使用 `@CsvSource({"true, 158", "false, 122"})` 分别测试加密和非加密场景。加密场景使用 `AesGcmOutputFile` 包装本地文件输出，非加密场景使用 `InMemoryOutputFile`。写入一个 blob 后关闭 writer，断言 `writer.length()` 等于期望值（加密 158 字节，非加密 122 字节），验证加密文件的长度计算现在正确反映了加密后的实际大小。

## 总结

本提交修复了加密 Puffin 文件长度报告错误的 bug。根因是加密输出流在 close 之前不会写入最终字节，且 getPos() 返回的是逻辑位置而非存储长度。修复方案是在计算 fileSize 前先 close 流，并使用 storedLength() 获取真实存储长度。这是一个影响数据完整性的重要 bug 修复，确保 manifest 中记录的 Puffin 文件长度准确。
