# 提交 2468：AWS, Aliyun: Fix memory leak by removing deleteOnExit() calls (#13749)

## 提交信息

- **序号**：2468 / 4088
- **哈希**：05dbcae8c15b2ad2d3ecb06ea5f6f9f28964f842
- **短哈希**：05dbcae8c
- **日期**：2025-08-06 12:00:05 -0700
- **作者**：kamijin_fanta
- **提交说明**：AWS, Aliyun: Fix memory leak by removing deleteOnExit() calls (#13749)
- **PR/Issue**：#13749

## 总体目的

该提交修复了 AWS S3 和 Aliyun OSS 输出流中的内存泄漏问题。两个输出流类（`S3OutputStream` 和 `OSSOutputStream`）在创建临时暂存文件时调用了 `File.deleteOnExit()`，导致在长时间运行的服务器中出现内存泄漏。

`File.deleteOnExit()` 的工作原理是将文件路径注册到 JVM 的全局 `DeleteOnExitHook` 集合中，这些文件路径会在 JVM 正常退出时被删除。然而，这个集合在正常运行期间永远不会被清理——每个注册的文件路径都会一直保留在内存中。对于 Iceberg 这样的数据湖框架，在长时间运行的服务中会频繁创建大量的临时暂存文件（每个文件上传都会创建一个临时文件），这些文件路径不断累积在 `DeleteOnExitHook` 集合中，导致堆内存无限增长。

实际上，`S3OutputStream` 和 `OSSOutputStream` 都已经有自己的显式清理逻辑来删除临时文件（在文件上传完成后或流关闭时），因此 `deleteOnExit()` 调用是多余的，仅仅是一个兜底机制。移除这些调用不会影响正常的临时文件清理，但能彻底解决内存泄漏问题。

## 如何达成设计目的

通过简单地移除两个输出流类中的 `deleteOnExit()` 调用来解决内存泄漏问题。由于两个类都已有完善的显式临时文件清理逻辑，移除 `deleteOnExit()` 不会导致临时文件残留。

## 修改详情

### `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/OSSOutputStream.java` (+0/-1 lines)

**修改目的**：移除 OSS 输出流中的 deleteOnExit() 调用。

**工作逻辑**：
在 `newStagingFile()` 方法中，移除 `stagingFile.deleteOnExit()` 调用：
```java
// 修改前
File stagingFile = File.createTempFile("oss-file-io-", ".tmp", new File(ossStagingDirectory));
stagingFile.deleteOnExit();
return stagingFile;
// 修改后
File stagingFile = File.createTempFile("oss-file-io-", ".tmp", new File(ossStagingDirectory));
return stagingFile;
```

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputStream.java` (+0/-1 lines)

**修改目的**：移除 S3 输出流中的 deleteOnExit() 调用。

**工作逻辑**：
在创建暂存文件后，移除 `currentStagingFile.deleteOnExit()` 调用：
```java
// 修改前
currentStagingFile = File.createTempFile("s3fileio-", ".tmp", stagingDirectory);
currentStagingFile.deleteOnExit();
// 修改后
currentStagingFile = File.createTempFile("s3fileio-", ".tmp", stagingDirectory);
```

## 总结

该提交通过移除 AWS S3 和 Aliyun OSS 输出流中的 `File.deleteOnExit()` 调用，修复了长时间运行服务器中的内存泄漏问题。`deleteOnExit()` 会将文件路径注册到 JVM 全局集合中且永不清理，在频繁创建临时文件的场景下导致堆内存无限增长。由于两个输出流类都已有完善的显式临时文件清理逻辑，移除该调用是安全的，不会导致临时文件残留。该修改涉及 AWS 和 Aliyun 两个模块，虽然改动量极小但修复了一个重要的资源管理问题。
