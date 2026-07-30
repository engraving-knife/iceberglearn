# 提交 2164：AWS: Close the S3SeekableInputStreamFactory before removing from cache (#12891)

## 提交信息

- **序号**：2164 / 4088
- **哈希**：dd7fa01bd5ac35532029bd98d785ea4b42f0def2
- **短哈希**：dd7fa01bd
- **日期**：2025-05-26 12:10:40 +0100
- **作者**：Sanjay Marreddi
- **提交说明**：AWS: Close the S3SeekableInputStreamFactory before removing from cache (#12891)
- **PR/Issue**：#12891

## 总体目的

`AnalyticsAcceleratorUtil` 使用 Caffeine 缓存来管理 `S3SeekableInputStreamFactory` 实例，缓存键为 `Pair<S3AsyncClient, S3FileIOProperties>`。当缓存条目被驱逐或手动失效（`invalidate`）时，原有的 Caffeine 缓存配置没有设置 `removalListener`，导致被移除的 `S3SeekableInputStreamFactory` 不会被正确关闭，造成资源泄漏。`S3SeekableInputStreamFactory` 内部持有底层资源（如对象客户端、流配置等），如果不关闭，可能导致连接泄漏或内存泄漏。该提交为 Caffeine 缓存添加了 `removalListener`，在缓存条目被移除时自动关闭对应的 `S3SeekableInputStreamFactory`，确保资源被正确释放。

## 如何达成设计目的

- 为 `STREAM_FACTORY_CACHE` 的 Caffeine 构建器添加 `removalListener`，在缓存条目被移除时调用 `close()` 方法关闭 `S3SeekableInputStreamFactory`。
- 新增私有的 `close(S3SeekableInputStreamFactory)` 方法，安全地关闭工厂实例，捕获 `IOException` 并记录警告日志。
- 为 `AnalyticsAcceleratorUtil` 类添加 SLF4J Logger 用于记录关闭失败警告。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/AnalyticsAcceleratorUtil.java` (修改, +23/-1 lines)

**修改目的**：在缓存条目移除时自动关闭 `S3SeekableInputStreamFactory`，避免资源泄漏。

**工作逻辑**：
- 新增 `RemovalListener`、`Logger`、`LoggerFactory` 的导入。
- 新增静态 Logger 字段 `LOG`。
- 修改 `STREAM_FACTORY_CACHE` 的构建：在 `Caffeine.newBuilder().maximumSize(100)` 之后链式调用 `.removalListener(...)`，注册一个 `RemovalListener<Pair<S3AsyncClient, S3FileIOProperties>, S3SeekableInputStreamFactory>`，在回调中调用 `close(factory)` 方法。
- 新增私有静态方法 `close(S3SeekableInputStreamFactory factory)`：若 factory 非空，尝试调用 `factory.close()`，若抛出 `IOException` 则通过 `LOG.warn` 记录警告，不中断流程。
- `cleanupCache` 方法保持不变，调用 `STREAM_FACTORY_CACHE.invalidate(...)` 时会自动触发 removalListener 执行关闭。

## 总结

该提交修复了 `AnalyticsAcceleratorUtil` 中 `S3SeekableInputStreamFactory` 的资源泄漏问题。通过为 Caffeine 缓存添加 removalListener，确保缓存条目被移除或失效时底层资源被正确关闭，提升了 S3 analytics accelerator 的资源管理可靠性。
