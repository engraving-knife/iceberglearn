# 提交 1074：S3OutputStream: Don't complete multipart upload on finalize

## 提交信息

- **序号**：1074 / 4088
- **哈希**：b76d81acf09ae129b43a13dac70c8739b70dae1e
- **短哈希**：b76d81acf
- **日期**：2024-08-20（Tue Aug 20 18:24:08 2024 +0300，注：作者本地时间，提交清单显示 -0600）
- **作者**：Jason <jasonf20@gmail.com>
- **提交说明**：S3OutputStream: Don't complete multipart upload on finalize (#10874)
- **PR/Issue**：#10874

## 总体目的

`S3OutputStream` 是 Iceberg AWS 模块向 S3 写数据的输出流，写入流程是：先把数据落到本地暂存文件，当数据量超过阈值后切换为 multipart upload，并在 `close()` 时调用 `completeUploads()` 把暂存的内容真正提交到 S3（小文件走 PutObject，大文件走 CompleteMultipartUpload）。

类里还实现了一个 `finalize()` 方法作为兜底：如果某个 `S3OutputStream` 实例未被显式 `close()` 就被 GC 回收，`finalize()` 会尝试调用 `close()` 来"释放资源"，并打印一条 warn 日志（带创建栈）帮助排查泄漏。

问题在于：原 `finalize()` 直接调用 `close()`，而 `close()` 内部会执行 `completeUploads()`——也就是说，**GC 回收时会顺带把一个用户没显式关闭的流"假装成功提交"到 S3**。这会带来两类严重后果：

1. **数据完整性问题**：用户没显式 close 通常意味着上层逻辑出错或异常中断。此时悄悄完成上传，会让一份"本应被丢弃"的不完整数据落盘到 S3，造成脏数据，且很难被发现。
2. **违反 OutputStream 语义契约**：`finalize` 的设计意图是"释放底层资源"（如关闭文件句柄、删除暂存文件），而不是"完成业务操作"。把 completeMultipartUpload 这种有外部副作用的提交动作放进 finalize，与 JDK 通用约定相悖。

本提交的目的：让 `finalize()` 只做"资源清理"（关闭底层流、删除暂存文件），而**不**触发 `completeUploads()`，从而避免 GC 时静默完成上传导致脏数据。

## 如何达成设计目的

实现方式是给 `close()` 拆出一个带参数的私有重载 `close(boolean completeUploads)`：

- 公共 `close()` 调用 `close(true)`：保持原有行为，正常显式关闭时仍会完成上传；
- 私有 `close(boolean)`：当 `completeUploads=true` 时调用 `completeUploads()`，为 `false` 时跳过；
- `finalize()` 改为调用 `close(false)`：只关闭底层 `stream`、清理暂存文件，不提交任何数据到 S3。

这样语义就清晰了：**只有用户/上层显式 close 才会把数据真正写入 S3；如果流被 GC 兜底回收，仅清理本地资源，不上传任何内容**，那些半成品数据会被丢弃，符合"未关闭即视为失败"的预期。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputStream.java`

**修改目的**：把 `close()` 拆成公共入口 + 私有带参实现，并让 `finalize()` 调用不上传的版本，避免 GC 时静默完成 multipart upload。

**工作逻辑**：

1. 拆分 `close()`：
```java
@Override
public void close() throws IOException {
  close(true);
}

private void close(boolean completeUploads) throws IOException {
  if (closed) {
    return;
  }
  closed = true;
  try {
    stream.close();
    if (completeUploads) {
      completeUploads();
    }
  } finally {
    cleanUpStagingFiles();
  }
}
```
- `closed` 标志幂等保护仍生效；
- `stream.close()` 关闭底层缓冲流；
- 只有 `completeUploads=true` 时才调用 `completeUploads()`（该方法根据是否进入 multipart 模式选择 PutObject 或 CompleteMultipartUpload）；
- `finally` 块里 `cleanUpStagingFiles()` 始终执行，保证本地暂存文件被清理。

2. 修改 `finalize()`：
```java
protected void finalize() throws Throwable {
  super.finalize();
  if (!closed) {
    close(false); // releasing resources is more important than printing the warning
    String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
    LOG.warn("Unclosed output stream created by:\n\t{}", trace);
  }
}
```
- 改为 `close(false)`，只做本地资源清理；
- warn 日志保留，仍会输出创建栈帮助定位泄漏点。

## 小结

- **成效**：消除了 GC 兜底回收时静默完成 S3 上传导致脏数据的风险，让 `S3OutputStream` 的语义与"未显式关闭即视为失败"的契约一致。这对数据湖写入场景特别重要——半成品文件如果不被丢弃而悄悄提交，下游读者会读到不完整的数据。
- **影响范围**：仅 `S3OutputStream` 一个文件，4 处改动共 8 行新增、2 行删除。行为变化只影响"未显式 close 而被 GC 回收"这一异常路径；正常 close 路径行为完全不变。
- **回迁到 1.4.x 的注意事项**：建议回迁。这是一个数据正确性修复，1.4.x 用户同样可能遇到 GC 兜底静默上传的问题。回迁时需注意 1.4.x 上 `S3OutputStream` 的 `close()`、`completeUploads()`、`finalize()` 是否结构一致；如果 1.4.x 上有其他地方（如 sink/commit 流程）依赖"finalize 会自动完成上传"这种意外行为（理论上不该有），需要在回迁后做一次回归验证。另外，Java 9+ 中 `finalize` 已被标记 deprecated，更长期的方向是改用 `Cleaner`，但本提交并未做这个迁移。
