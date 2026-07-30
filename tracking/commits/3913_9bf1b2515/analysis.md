# 提交 3913：Aliyun: Pass known file length through OSSFileIO.newInputFile (#16870)

## 提交信息

- **序号**：3913 / 4088
- **哈希**：9bf1b25159c8ae03fea2bda67c22b13d7e4e6c27
- **短哈希**：9bf1b2515
- **日期**：2026-06-20 13:15:02 -0700
- **作者**：Eunbin Son
- **提交说明**：Aliyun: Pass known file length through OSSFileIO.newInputFile (#16870)
- **PR/Issue**：#16870

## 总体目的

修复阿里云 OSS FileIO 的性能问题：当调用方已知文件大小时，仍会触发不必要的 HEAD 请求获取文件元数据。`OSSFileIO` 此前未覆写 `newInputFile(String path, long length)` 方法，导致该调用回退到默认实现 `newInputFile(String path)`，丢弃了已知的文件长度。当后续调用 `getLength()` 时，会触发阿里云 OSS 的 `getSimplifiedObjectMeta` HEAD 请求，即使调用方已经知道文件大小。

S3FileIO、GCSFileIO 和 ADLSFileIO 已经覆写了此方法，OSSFileIO 是遗漏的实现。在频繁读取已知大小文件的场景下（如读取 manifest 文件、metadata 文件），这个额外的 HEAD 请求会累积成显著的网络开销和延迟。

## 如何达成设计目的

覆写 `OSSFileIO.newInputFile(String path, long length)` 方法，使用支持已知长度的 `OSSInputFile` 构造器，将长度传递下去，避免后续 `getLength()` 触发 HEAD 请求。

## 修改详情

### `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/OSSFileIO.java` (+5/-0 lines)

**修改目的**：覆写 newInputFile 方法以传递已知文件长度。

**工作逻辑**：
```java
@Override
public InputFile newInputFile(String path, long length) {
  return new OSSInputFile(client(), new OSSURI(path), aliyunProperties, length, metrics);
}
```
使用 `OSSInputFile` 的五参数构造器（包含 `length`），与已有的无长度构造器 `new OSSInputFile(client(), new OSSURI(path), aliyunProperties, metrics)` 相比，将已知长度直接传入，后续 `getLength()` 直接返回该值而无需网络请求。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSFileIO.java` (+27/-0 lines)

**修改目的**：验证已知长度传递和 HEAD 请求避免。

**工作逻辑**：
新增 `testNewInputFileWithLength` 测试：
1. 写入 10KB 数据到 OSS
2. 使用 Mockito 的 `delegatesTo` 创建 OSS 客户端 mock
3. 调用 `newInputFile(location, dataSize)` 传入已知长度
4. 断言 `getLength()` 返回已知长度
5. 验证 `getSimplifiedObjectMeta` 被调用 0 次（无 HEAD 请求）
6. 调用 `newInputFile(location)` 不传长度
7. 断言 `getLength()` 返回实际长度
8. 验证 `getSimplifiedObjectMeta` 被调用 1 次（需要 HEAD 请求获取大小）

```java
verify(ossMock, times(0)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
// ...
verify(ossMock, times(1)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
```

## 总结

覆写 `OSSFileIO.newInputFile(String, long)` 方法，使已知文件长度时避免不必要的 OSS HEAD 请求。该修复使 OSSFileIO 与 S3FileIO、GCSFileIO、ADLSFileIO 的行为保持一致，减少了频繁文件读取场景下的网络开销。测试通过 Mockito 验证了 HEAD 请求的调用次数，确保优化生效。

该提交由 Claude Code (claude-opus-4-8) 生成，符合 AGENTS.md 中新增的 AI 披露规范。
