# 提交 0675：API, Core, Kafka, Spark: Reduce enum array allocation (#10126)

## 提交信息
- **序号**：0675 / 4088
- **哈希**：ce7c2c150ce449666cd690612d075fa9893f3118
- **短哈希**：ce7c2c150
- **日期**：2024-04-12 08:51:09 -0700
- **作者**：sullis
- **提交说明**：API, Core, Kafka, Spark: Reduce enum array allocation (#10126)
- **PR/Issue**：#10126

## 总体目的

这是一个跨模块的微优化（micro-optimization）提交。Java 中调用 `EnumType.values()` 时，JVM 会**每次都返回一个新分配的数组**（数组是可变的，JVM 必须复制以防止调用方修改数组后影响其他调用方）。在热点路径（hot path）上反复调用 `values()` 会产生大量短生命周期的小数组对象，给年轻代垃圾回收（Young GC）带来压力，间接增加整体 GC 开销。

Iceberg 代码库中有几处枚举遍历的代码在热点路径上调用 `values()`：
- `FileFormat.fromFileName`：根据文件名解析文件格式，在 manifest 读取、文件扫描等场景被高频调用。
- `BaseFile.put`：Avro 反序列化 manifest entry 时按 ID 设置 `FileContent`。
- `CachingCatalog.metadataTableIdentifiers`：遍历 `MetadataTableType` 列出元数据表标识符。
- `GenericManifestEntry.put`：按 ID 设置 `Status`。
- `GenericManifestFile.put`：按 ID 设置 `ManifestContent`。
- `HadoopTableOperations.getMetadataFile`：遍历 `TableMetadataParser.Codec` 查找存在的元数据文件。
- `Event.put`：Kafka 事件反序列化时按 ID 设置 `PayloadType`。
- `SparkContentFile.getFileContent`：Spark 读取文件内容类型时按 int 索引取 `FileContent`。

这些 `values()` 调用在批量扫描、manifest 解析、Spark 任务执行等高频路径上会被反复触发，本提交通过把这些 `values()` 结果缓存为 `private static final` 常量数组来避免重复分配。

## 如何达成设计目的

整体设计思路极其简单但有效：在每个调用 `EnumType.values()` 的类中，新增一个 `private static final EnumType[] XXX_VALUES = EnumType.values();` 常量字段，然后把所有 `EnumType.values()` 调用替换为引用此常量。

**优化原理**：

1. **`EnumType.values()` 的实现**：Java 编译器为每个枚举生成 `values()` 方法，其实现是调用 `clone()` 复制内部维护的 `$VALUES` 数组后返回。这意味着每次调用都分配一个新数组和一个数组对象头（约 16 字节 + 元素指针数组）。对于 Iceberg 的常见枚举（如 `FileContent` 有 3 个元素、`FileFormat` 有 4 个元素），每次调用约分配 40-56 字节。

2. **静态常量缓存**：把 `values()` 的结果存入 `private static final` 字段后，类初始化时（JVM 加载类时）只调用一次 `values()`，此后所有引用都使用同一个数组实例。由于这是 `private` 字段，外部无法修改；类内部代码也不会修改数组内容（只读遍历或按下标取值），因此共享一个实例是安全的。

3. **为何不用 `EnumSet.allOf()` 或 `Enum.getEnumConstants()`**：
   - `EnumSet.allOf()` 返回的是 Set，遍历有额外开销，且按下标取值场景不适用（如 `STATUS_VALUES[(Integer) v]`）。
   - `Class.getEnumConstants()` 通过反射访问，性能不如直接访问数组字段。
   - 直接缓存 `values()` 数组是最轻量、最直接的优化方式，不改变任何调用语义。

4. **为何选择这几个文件**：这些都是反序列化、文件解析、元数据加载等热点路径上的代码，每次 manifest 读取、文件扫描、Spark 任务执行都会触发。在批量场景下（如扫描百万级文件），节省的数组分配数量可观。

5. **影响 Spark v3.4 与 v3.5 双份**：注意 `SparkContentFile.java` 在 `spark/v3.4/spark` 与 `spark/v3.5/spark` 两份几乎相同的代码中都做了同样改动，保持两个 Spark 版本的行为一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/FileFormat.java`

**修改目的**：缓存 `FileFormat.values()` 避免在 `fromFileName` 中重复分配。

**工作逻辑**：
```java
private static final FileFormat[] VALUES = values();
```
新增静态常量后，`fromFileName(CharSequence filename)` 中的循环从 `for (FileFormat format : FileFormat.values())` 改为 `for (FileFormat format : VALUES)`。`fromFileName` 是根据文件名扩展名解析文件格式的核心方法，在 manifest 文件、数据文件的解析中被频繁调用。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：缓存 `FileContent.values()` 避免 Avro 反序列化时重复分配。

**工作逻辑**：
```java
private static final FileContent[] FILE_CONTENT_VALUES = FileContent.values();
```
在 `put(int pos, Object value)` 方法的 `case 0` 分支中，`FileContent.values()[(Integer) value]` 改为 `FILE_CONTENT_VALUES[(Integer) value]`。`BaseFile` 是 manifest entry 与数据文件的基类，`put` 方法在 Avro 反序列化每条记录时被调用，是高频路径。

### `core/src/main/java/org/apache/iceberg/CachingCatalog.java`

**修改目的**：缓存 `MetadataTableType.values()` 避免在元数据表标识符列举中重复分配。

**工作逻辑**：
```java
private static final MetadataTableType[] METADATA_TABLE_TYPE_VALUES = MetadataTableType.values();
```
在 `metadataTableIdentifiers(TableIdentifier ident)` 方法中，`for (MetadataTableType type : MetadataTableType.values())` 改为 `for (MetadataTableType type : METADATA_TABLE_TYPE_VALUES)`。该方法在每次 catalog 列举表时被调用，遍历所有元数据表类型生成候选标识符。

### `core/src/main/java/org/apache/iceberg/GenericManifestEntry.java`

**修改目的**：缓存 `Status.values()` 避免在 manifest entry 反序列化时重复分配。

**工作逻辑**：
```java
private static final Status[] STATUS_VALUES = Status.values();
```
在 `put(int i, Object v)` 的 `case 0` 分支中，`Status.values()[(Integer) v]` 改为 `STATUS_VALUES[(Integer) v]`。`GenericManifestEntry` 是 manifest 中每条 entry 的反序列化载体，`put` 方法在读取 manifest 时被高频调用。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java`

**修改目的**：缓存 `ManifestContent.values()` 避免在 manifest file 反序列化时重复分配。

**工作逻辑**：
```java
private static final ManifestContent[] MANIFEST_CONTENT_VALUES = ManifestContent.values();
```
在 `put(int pos, Object value)` 的 `case 3` 分支中，`ManifestContent.values()[(Integer) value]` 改为 `MANIFEST_CONTENT_VALUES[(Integer) value]`。`GenericManifestFile` 是 manifest list 中每个 manifest 的反序列化载体。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTableOperations.java`

**修改目的**：缓存 `TableMetadataParser.Codec.values()` 避免在元数据文件查找中重复分配。

**工作逻辑**：
```java
private static final TableMetadataParser.Codec[] TABLE_METADATA_PARSER_CODEC_VALUES =
    TableMetadataParser.Codec.values();
```
在 `getMetadataFile(int metadataVersion)` 方法中，`for (TableMetadataParser.Codec codec : TableMetadataParser.Codec.values())` 改为 `for (TableMetadataParser.Codec codec : TABLE_METADATA_PARSER_CODEC_VALUES)`。该方法在加载表元数据时遍历所有 codec（NONE/GZIP/SNAPPY/LZ4/BROTLI/ZSTD）查找存在的元数据文件。

### `kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/Event.java`

**修改目的**：缓存 `PayloadType.values()` 避免 Kafka 事件反序列化时重复分配。

**工作逻辑**：
```java
private static final PayloadType[] PAYLOAD_TYPE_VALUES = PayloadType.values();
```
在 `put(int i, Object v)` 的 `TYPE` 分支中，`PayloadType.values()[(Integer) v]` 改为 `PAYLOAD_TYPE_VALUES[(Integer) v]`。`Event` 是 Kafka Connect 事件的 Avro 反序列化载体。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`

**修改目的**：缓存 `FileContent.values()` 避免 Spark 读取文件内容类型时重复分配。

**工作逻辑**：
```java
private static final FileContent[] FILE_CONTENT_VALUES = FileContent.values();
```
在 `getFileContent()` 方法中，`FileContent.values()[wrapped.getInt(fileContentPosition)]` 改为 `FILE_CONTENT_VALUES[wrapped.getInt(fileContentPosition)]`。`SparkContentFile` 是 Spark 读取 Iceberg 文件元数据时的包装类，`getFileContent` 在 Spark 任务读取每个文件时被调用。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`

**修改目的**：与 v3.4 保持一致，缓存 `FileContent.values()`。

**工作逻辑**：与 v3.4 完全相同的改动（缓存 `FileContent.values()` 为 `FILE_CONTENT_VALUES` 常量，`getFileContent` 中改用常量）。Spark v3.5 模块维护一份独立但与 v3.4 几乎相同的代码，因此需要同步改动。

## 小结

- **成效**：成功把 9 处 `EnumType.values()` 调用替换为 `private static final` 缓存数组，避免了热点路径上每次调用都分配新数组。改动本身风险极低（不改变任何语义），但能减少 Young GC 压力，对长任务（如大规模 manifest 扫描、Spark 批处理）有累积收益。
- **影响范围**：跨 `api`、`core`、`kafka-connect`、`spark/v3.4`、`spark/v3.5` 五个模块共 9 个文件。所有涉及 manifest 解析、文件扫描、表元数据加载、Kafka 事件处理、Spark 任务执行的路径都受益。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯优化、无行为变更的改动，回迁风险极低，可直接 cherry-pick。
  2. 注意 1.4.x 中这些文件是否已存在——若文件路径或类结构有差异（如某些类名不同），需逐文件核对。
  3. 注意 `SparkContentFile.java` 在 v3.4 与 v3.5 两份代码都要回迁，避免只回迁一个版本导致两个版本行为不一致。
  4. 本提交未触及测试代码（因为无行为变更），但可考虑在回迁后运行现有测试套件确认无回归。
  5. 若 1.4.x 中已有类似的优化（例如某些文件已缓存 values），回迁时跳过已优化的文件即可。
