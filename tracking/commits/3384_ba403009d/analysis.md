# 提交 3384：Core: Move Hadoop conf serialization into SerializableConfiguration (#15583)

## 提交信息

- **序号**：3384 / 4088
- **哈希**：ba403009d2636e1f422a865ebae083e77aa443ad
- **短哈希**：ba403009d
- **日期**：2026-03-13
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Move Hadoop conf serialization into SerializableConfiguration (#15583)
- **PR/Issue**：#15583

## 总体目的

本提交将 Hadoop `Configuration` 的序列化逻辑从分散的多处调用点统一收敛到 `SerializableConfiguration` 类自身，使其从单纯的序列化包装器升级为同时实现 `SerializableSupplier<Configuration>` 的、自管理的可序列化配置载体，简化代码并消除重复的序列化模式。

此前 `SerializableConfiguration` 通过自定义 `writeObject`/`readObject` 利用 Hadoop `Configuration` 自身的 `write(DataOutput)`/`readFields(DataInput)` 实现序列化。但 Hadoop 的 Java 序列化方式与 Kryo 序列化行为不一致，且这种方式较为脆弱。与此同时，项目中存在多处重复的"将 Configuration 转为可序列化形式"的模式：`HadoopFileIO` 和 `ResolvingFileIO` 在 `setConf` 时都写 `new SerializableConfiguration(conf)::get`（将 `SerializableConfiguration` 降级为方法引用）；`SerializationUtil.serializeToBytes` 也用 `conf -> new SerializableConfiguration(conf)::get` 作为默认序列化器；`SerializableTable` 内部更是单独定义了一个 `SerializableConfSupplier` 私有静态类来做完全相同的事——把 Configuration 转为 `Map<String,String>` 并在反序列化时按需重建。这些重复逻辑分散在各处，维护成本高且行为容易不一致。

本提交的目标是让 `SerializableConfiguration` 自身成为 `SerializableSupplier<Configuration>` 的实现，直接可以作为可序列化的 Configuration 供应商使用，从而消除所有 `new SerializableConfiguration(conf)::get` 的方法引用降级，并删除 `SerializableTable` 中冗余的 `SerializableConfSupplier`。

## 如何达成设计目的

核心改造是让 `SerializableConfiguration` 实现 `SerializableSupplier<Configuration>` 接口：构造时将 Configuration 转为 `Map<String,String>` 存储（可被 Java 序列化与 Kryo 序列化统一处理），`get()` 方法用双重检查锁的懒加载方式从 map 重建 Configuration。这样 `SerializableConfiguration` 实例本身就是可序列化的 Configuration 供应商，无需再 `::get` 降级。随后将 `HadoopFileIO`、`ResolvingFileIO`、`SerializationUtil` 中所有 `new SerializableConfiguration(conf)::get` 替换为直接使用 `new SerializableConfiguration(conf)`，并从 `SerializableTable` 中移除 `SerializableConfSupplier` 及相关的 `fileIO` 封装方法。同时将 `HadoopFileIO` 中基于 `SerializableSupplier<Configuration>` 的构造器和 `serializeConfWith` 方法标记为 `@Deprecated`（1.11.0 起，1.12.0 移除），引导使用者迁移到新 API。最后在 revapi 配置中接受 `SerializableConfiguration` 的序列化格式变更。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/SerializableConfiguration.java` (+20/-17 lines)

**修改目的**：将 SerializableConfiguration 改造为自管理的 SerializableSupplier 实现。

**工作逻辑**：
这是本提交的核心。原实现通过 `transient Configuration hadoopConf` + 自定义 `writeObject`/`readObject`（调用 Hadoop 的 `write`/`readFields`）做序列化。新实现改为：
- 类声明变为 `public class SerializableConfiguration implements SerializableSupplier<Configuration>`。
- 字段改为 `private final Map<String, String> confAsMap`（可被 Java/Kryo 序列化）和 `private transient volatile Configuration hadoopConf = null`（懒加载缓存）。
- 构造器将 Configuration 的所有条目拷贝到 `confAsMap`。
- `get()` 方法实现双重检查锁懒加载：若 `hadoopConf` 为 null，则同步创建 `new Configuration(false)` 并逐条 `set` map 中的键值，赋值给 `hadoopConf` 后返回。

这种基于 Map 的序列化方式比 Hadoop 原生 `write`/`readFields` 更可预测，且对 Java 序列化与 Kryo 序列化行为一致，解决了此前两种序列化路径行为不一致的隐患。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java` (+11/-4 lines)

**修改目的**：改用 SerializableConfiguration 直接作为 Configuration 供应商，并弃用旧 API。

**工作逻辑**：
- 构造器 `HadoopFileIO(Configuration)` 从 `this(new SerializableConfiguration(hadoopConf)::get)` 改为 `this(new SerializableConfiguration(hadoopConf))`，直接传入 `SerializableConfiguration` 实例（它本身就是 `SerializableSupplier<Configuration>`）。
- `setConf(Configuration)` 从 `new SerializableConfiguration(conf)::get` 改为 `new SerializableConfiguration(conf)`。
- `getConf()` 中初始化空配置也做同样替换。
- 将 `HadoopFileIO(SerializableSupplier<Configuration>)` 构造器标记 `@Deprecated`（since 1.11.0，1.12.0 移除），引导使用 `HadoopFileIO(Configuration)`。
- 将 `serializeConfWith(...)` 方法标记 `@Deprecated`，因序列化逻辑已内化到 `SerializableConfiguration`，外部不再需要自定义序列化器。

### `core/src/main/java/org/apache/iceberg/io/ResolvingFileIO.java` (+1/-1 lines)

**修改目的**：同步替换序列化方式。

**工作逻辑**：
`setConf(Configuration)` 中 `this.hadoopConf = new SerializableConfiguration(conf)::get` 改为 `this.hadoopConf = new SerializableConfiguration(conf)`，与 `HadoopFileIO` 保持一致。

### `core/src/main/java/org/apache/iceberg/util/SerializationUtil.java` (+1/-1 lines)

**修改目的**：将默认 Configuration 序列化器改为直接使用 SerializableConfiguration。

**工作逻辑**：
`serializeToBytes(Object obj)` 的默认序列化器从 `conf -> new SerializableConfiguration(conf)::get` 改为方法引用 `SerializableConfiguration::new`，更简洁且与全局统一的序列化方式一致。

### `core/src/main/java/org/apache/iceberg/SerializableTable.java` (+4/-36 lines)

**修改目的**：移除冗余的 SerializableConfSupplier 与 fileIO 封装方法。

**工作逻辑**：
- 移除 `fileIO(Table table)` 私有方法（该方法此前会检测 `table.io()` 是否为 `HadoopConfigurable`，若是则调用 `serializeConfWith(SerializableConfSupplier::new)` 注入自定义序列化器）。构造器中 `this.io = fileIO(table)` 直接改为 `this.io = table.io()`，因为序列化逻辑已内化到 `SerializableConfiguration`，`HadoopFileIO` 序列化时会自动用 Map 方式，无需外部干预。
- 删除内部静态类 `SerializableConfSupplier`（约 30 行），其功能与新的 `SerializableConfiguration` 完全重复。
- 移除相关 import（`Configuration`、`HadoopConfigurable`、`SerializableSupplier`）。

### `core/src/test/java/org/apache/iceberg/hadoop/TestSerializableConfiguration.java` (+55/-0 lines，新建)

**修改目的**：验证新 SerializableConfiguration 在 Kryo 与 Java 序列化下的正确性。

**工作逻辑**：
新增两个测试：
- `kryoSerialization()`：构造带 `prefix.key1`/`prefix.key2` 两个键的 Configuration，包装为 `SerializableConfiguration`，通过 `TestHelpers.KryoHelpers.roundTripSerialize` 做 Kryo 往返序列化，断言序列化后的 `get().getPropsWithPrefix("prefix")` 与原始一致。
- `javaSerialization()`：构造同样的 Configuration，通过 `TestHelpers.roundTripSerialize` 做 Java 序列化往返，断言一致。两个测试共同验证新实现在这两种序列化路径下都能正确保存和恢复 Configuration 内容，解决了此前两种序列化方式行为不一致的隐患。

### `.palantir/revapi.yml` (+4/-0 lines)

**修改目的**：接受 SerializableConfiguration 的二进制序列化格式变更。

**工作逻辑**：
在 `acceptedBreaks` 中为 `org.apache.iceberg:iceberg-core` 新增一条 `java.class.defaultSerializationChanged` 记录，old/new 均为 `class org.apache.iceberg.hadoop.SerializableConfiguration`，justification 为 "Serialization across versions is not guaranteed"。由于 `SerializableConfiguration` 的序列化机制从 Hadoop `write`/`readFields` 改为基于 Map 的 Java 序列化，其默认序列化格式发生变更，revapi 会检测到这一破坏性变更，此处显式接受并以"跨版本序列化不保证兼容"为由记录。

## 总结

本提交将 Hadoop Configuration 的序列化逻辑统一收敛到 `SerializableConfiguration`，使其从依赖 Hadoop 原生序列化的简单包装器升级为实现 `SerializableSupplier<Configuration>` 的自管理可序列化载体（基于 Map 存储 + 懒加载重建）。这一改造消除了项目中四处重复的 `new SerializableConfiguration(conf)::get` 方法引用降级模式和 `SerializableTable` 中冗余的 `SerializableConfSupplier` 内部类，简化了代码结构，统一了 Java 与 Kryo 两种序列化路径的行为，并为弃用旧的 `SerializableSupplier` 构造器和 `serializeConfWith` 方法铺平了迁移路径。
