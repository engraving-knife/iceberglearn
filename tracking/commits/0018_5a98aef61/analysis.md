# 提交 0018：Spark: Clean up FileIO instances on executors (#8685)

## 提交信息

- **序号**：0018 / 4088
- **哈希**：5a98aef61a598057169c229ca0b804b0fef140f8
- **短哈希**：5a98aef61
- **日期**：2023-10-06 14:23:46 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark: Clean up FileIO instances on executors (#8685)
- **PR/Issue**：#8685

## 总体目的

这个提交修复了 Spark 在 executor 端使用 `SerializableTableWithSize` 广播变量时的 `FileIO` 资源泄漏问题，让该类实现 `AutoCloseable`，并利用 Java 序列化的 `transient` 字段语义区分"驱动端原始实例"和"executor 端反序列化副本"，仅在 executor 副本上关闭 `FileIO`，避免误关驱动端共享的 `FileIO` 实例。

背景：Iceberg 的 Spark 集成在读取表时，会把表对象包装成 `SerializableTableWithSize`（一个带大小估计的可序列化表）作为广播变量发送到所有 executor。`SerializableTable` 内部持有 `FileIO`（如 `HadoopFileIO`、`S3FileIO`、`GSFileIO` 等），这些 `FileIO` 通常持有底层客户端连接、线程池或凭证 provider 等昂贵资源。Spark 的广播变量机制在驱动端和每个 executor 上都会持有一份反序列化后的副本，并在该副本被垃圾回收前一直保留。当广播变量在驱动端被 GC 时，Spark 会调用广播变量的清理逻辑；但 Iceberg 此前的 `SerializableTableWithSize` 没有实现 `AutoCloseable`，其内部 `FileIO` 在 executor 端从不被显式关闭——executor 上每个任务结束后广播副本仍长期存活，`FileIO` 持有的资源也就一直不释放，在长运行作业、多表场景或动态资源调度下会累积成明显的资源泄漏（连接数膨胀、文件句柄耗尽、metastore 连接堆积等）。

本提交的核心难点在于"区分驱动端和 executor 端"：驱动端的 `FileIO` 通常是 catalog 共享的、生命周期由 catalog 管理的实例，绝不能在广播变量清理时被关闭；而 executor 端反序列化出来的 `FileIO` 副本是专属的，应该在使用完毕后关闭。提交利用了一个巧妙的 Java 序列化特性来区分两者，详见下文。

## 如何达成设计目的

设计思路是让 `SerializableTableWithSize` 实现 `AutoCloseable`，并在 `close()` 中只对"反序列化副本"关闭 `io()`，对"驱动端原始实例"跳过关闭。区分两者依靠一个 `transient Object serializationMarker` 字段：

- 在构造函数中，`serializationMarker = new Object()`，所以驱动端创建的原始实例该字段非 null。
- 该字段被声明为 `transient`，Java 序列化时会跳过它，因此反序列化后该字段为 `null`。
- 于是 `close()` 中 `if (serializationMarker == null)` 仅对反序列化副本成立——只有 executor 端的副本会执行 `io().close()`。

这样 Spark 在清理广播变量副本时调用 `close()`，就能安全地只释放 executor 端的 `FileIO`，而不会误伤驱动端共享实例。同一改动同步应用到 Spark 3.1/3.2/3.3/3.4 四个版本分支，并配套新增 Kryo 与 Java 两种序列化路径的单元测试。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SerializableTableWithSize.java`（及 v3.1/v3.2/v3.3 同名文件，改动一致）

**修改目的**：让 `SerializableTableWithSize` 实现 `AutoCloseable`，在广播变量清理时安全释放 executor 端的 `FileIO`。

**工作逻辑**：

1. **新增 import 与 Logger**：引入 `org.slf4j.Logger` / `org.slf4j.LoggerFactory`，并声明 `private static final Logger LOG = LoggerFactory.getLogger(SerializableTableWithSize.class);`，用于在释放资源时记录 info 日志。

2. **类签名扩展**：从 `implements KnownSizeEstimation` 改为 `implements KnownSizeEstimation, AutoCloseable`，使 Spark 在清理广播副本时可调用 `close()`。

3. **类注释补充**：在原有"提供已知大小估计以跳过 SizeEstimator"的说明后，新增一段注释解释 `AutoCloseable` 的目的——"广播变量在驱动端和 executor 上都会在驱动端 GC 后被销毁和清理，本实现确保只释放主表副本使用的资源"。

4. **新增 `transient` 标记字段与构造器初始化**：

   ```java
   private final transient Object serializationMarker;

   protected SerializableTableWithSize(Table table) {
     super(table);
     this.serializationMarker = new Object();
   }
   ```

   关键点：`transient` 修饰使该字段不参与序列化。驱动端构造的实例字段非 null；executor 端反序列化得到的副本该字段为 null。

5. **新增 `close()` 方法**：

   ```java
   @Override
   public void close() throws Exception {
     if (serializationMarker == null) {
       LOG.info("Releasing resources");
       io().close();
     }
   }
   ```

   仅当 `serializationMarker == null`（即反序列化副本）时才调用 `io().close()`。驱动端原始实例的 `serializationMarker` 非 null，`close()` 直接返回，不会关闭共享的 `FileIO`。

注意：`SerializableMetadataTableWithSize` 内部嵌套类未做同样改动，本提交仅处理主表场景。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/TestTableSerialization.java`（及 v3.1/v3.2/v3.3 同名文件，改动一致）

**修改目的**：验证 `close()` 行为——驱动端原始实例关闭时不关闭其 `FileIO`，executor 端反序列化副本关闭时关闭副本自身的 `FileIO`。

**工作逻辑**：新增两个测试用例，分别覆盖 Kryo 与 Java 两种序列化路径（对应 Spark 广播变量可能的序列化器）：

1. **`testCloseSerializableTableKryoSerialization`**：用 Mockito `spy` 包装真实 `table` 和 `table.io()`，并通过 `SerializableTableWithSize.copyOf(spyTable)` 得到可序列化表。然后用 `KryoHelpers.roundTripSerialize` 模拟广播序列化得到副本，再 `spy` 副本并把副本的 `io()` 替换为 `spyFileIOCopy`。接着分别对原始 `serializableTable`（模拟驱动端 close）和 `serializableTableCopy`（模拟 executor 端 close）调用 `((AutoCloseable) ...).close()`。最后用 `verify` 断言：驱动端原始 `spyIO` **从未被关闭**（`never().close()`），executor 副本的 `spyFileIOCopy` **被关闭恰好一次**（`times(1).close()`）。

2. **`testCloseSerializableTableJavaSerialization`**：与上一测试结构完全相同，仅把序列化路径从 Kryo 换成 `TestHelpers.roundTripSerialize`（Java 原生序列化），验证两条序列化路径下 `transient` 字段语义与 `close()` 行为一致。

测试用 `spy` + `when().thenReturn()` 替换 `io()` 是关键——这样能在不修改生产代码的前提下精确观测哪个 `FileIO` 实例被关闭，从而验证"区分驱动端/executor 端"的设计意图。

## 小结

该提交通过让 `SerializableTableWithSize` 实现 `AutoCloseable` 并利用 `transient` 标记字段区分驱动端原始实例与 executor 反序列化副本，修复了 Spark 广播表对象时 executor 端 `FileIO` 资源泄漏的问题，是 Iceberg Spark 集成在资源治理上的一项重要修复。
