# 提交 1782：Core: Add "volatile" to HadoopFileIO#hadoopConf (#12388)

## 提交信息

- **序号**：1782 / 4088
- **哈希**：1a044adbd5f76ad2a404ea52e3c26aaff5970215
- **短哈希**：1a044adbd
- **日期**：2025-02-25 08:01:52 +0100
- **作者**：Shohei Okumiya
- **提交说明**：Core: Add "volatile" to HadoopFileIO#hadoopConf (#12388)
- **PR/Issue**：#12388

## 总体目的

这个提交修复了 `HadoopFileIO` 类中 `hadoopConf` 字段的线程可见性问题。`hadoopConf` 字段类型为 `SerializableSupplier<Configuration>`，是一个可变字段，会在 `HadoopFileIO` 的生命周期中被 `serialize()` 和 `deserialize()` 方法或 `setConf()` 方法修改。然而该字段没有声明为 `volatile`，这意味着在多线程环境下，一个线程对该字段的修改可能对其他线程不可见。

`HadoopFileIO` 实现了 `Serializable` 接口，在分布式计算框架（如 Spark、Flink）中，`HadoopFileIO` 实例会被序列化后发送到不同的 executor 节点，然后反序列化重建。在这种场景下，`hadoopConf` 字段会在反序列化时被设置。如果该字段不是 `volatile` 的，JVM 不保证其他线程能看到反序列化后设置的值，可能导致线程读到 null 或旧的配置值，进而引发空指针异常或使用错误的 Hadoop 配置。

添加 `volatile` 关键字确保该字段的写入对所有线程立即可见，保证内存可见性。

## 如何达成设计目的

提交通过在 `hadoopConf` 字段声明中添加 `volatile` 关键字来达成目标。这与同文件中已有的 `executorService` 字段（已声明为 `volatile`）保持一致的处理方式。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java`（修改, +1/-1 lines）

**修改目的**：为 `hadoopConf` 字段添加 `volatile` 修饰符，确保多线程环境下的内存可见性。

**工作逻辑**：
- 原代码：`private SerializableSupplier<Configuration> hadoopConf;`
- 修改后：`private volatile SerializableSupplier<Configuration> hadoopConf;`
- `volatile` 关键字确保该字段的写入操作会立即刷新到主内存，读取操作会直接从主内存读取，而不是从 CPU 缓存中读取。这保证了在序列化/反序列化或 `setConf()` 修改该字段后，所有线程都能看到最新值。
- 同文件中 `executorService` 字段已经使用了 `volatile`（`private static volatile ExecutorService executorService;`），此修改使 `hadoopConf` 保持一致的可见性处理。

## 小结

- **成效**：修复了 `HadoopFileIO.hadoopConf` 字段的线程可见性问题，确保在多线程和分布式环境下对该字段的修改能被正确传播。
- **影响范围**：仅影响 `HadoopFileIO` 类的 `hadoopConf` 字段。影响所有使用 `HadoopFileIO` 的多线程和分布式场景，特别是 Spark/Flink 中的序列化/反序列化场景。
- **回迁到 1.4.x 的注意事项**：建议回迁。这是一个重要的并发安全修复，变更极简。可独立回迁，无前置依赖。需确认 1.4.x 分支中 `HadoopFileIO.java` 的该字段存在。
