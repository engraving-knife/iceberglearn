# 提交 2756：Core: Fix for respecting custom location providers in SerializableTable #12564 (#14280)

## 提交信息

- **序号**：2756 / 4088
- **哈希**：e667f64f5bddbacb1a641ac8ea67fc21a76e434d
- **短哈希**：e667f64f5
- **日期**：2025-10-16 11:37:01 -0600
- **作者**：przemekd
- **提交说明**：Core: Fix for respecting custom location providers in SerializableTable #12564 (#14280)
- **PR/Issue**：#14280（关联 issue #12564）

## 总体目的

本提交修复了 `SerializableTable` 在序列化/反序列化过程中未正确保留自定义 `LocationProvider` 的问题（issue #12564）。

背景在于：`SerializableTable` 是 Iceberg 中用于跨节点（如 Spark executor）传递表对象的轻量级可序列化实现。它在构造时持久化表的 schema、spec、sort order、properties 等元数据，以避免在远端节点重新读取 metadata 文件。原实现在 `locationProvider()` 方法中使用懒加载：首次调用时通过 `LocationProviders.locationsFor(location, properties)` 基于表位置和属性重新构建 `LocationProvider`。

问题在于：这种重建方式忽略了用户在原表上注册的自定义 `LocationProvider`。如果用户通过自定义实现（例如基于表属性中的特定键来决定数据文件存放路径的策略）来创建表，那么 `SerializableTable` 在远端节点重建时会退化为默认的 `LocationProvider` 实现，导致数据文件被写到错误的位置。自定义 LocationProvider 可能依赖原始表对象上下文或非序列化属性，无法仅凭 `location` 和 `properties` 重建。

修复策略是：在 `SerializableTable.copyOf(table)` 构造时，直接捕获原始表的 `locationProvider()` 实例（通过 `Try.of(table::locationProvider)` 延迟捕获异常），并将其作为可序列化字段保存。这样反序列化后调用 `locationProvider()` 时，直接返回捕获的实例，而非重建。同时，类文档也更新说明 `LocationProvider` 实例需要是可序列化的。

## 如何达成设计目的

整体设计引入了一个新的工具类 `Try<T>` 来安全地捕获可能在构造时抛出异常的操作结果：

1. **新增 `Try<T>` 类**：一个可序列化的容器，持有操作的值或异常。`Try.of(supplier)` 执行操作并捕获结果或异常；`getOrThrow()` 返回值或在失败时通过 `sneakyThrow` 重抛原始异常。这解决了"构造 `SerializableTable` 时如果 `table.locationProvider()` 抛异常怎么办"的问题——异常被延迟到实际调用 `locationProvider()` 时才抛出，而不是在 `copyOf` 阶段就失败。

2. **改造 `SerializableTable`**：用 `Try<LocationProvider> locationProviderTry` 字段替代原来的 `transient volatile LocationProvider lazyLocationProvider`。构造时执行 `this.locationProviderTry = Try.of(table::locationProvider)`，捕获原始 LocationProvider（或其异常）。`locationProvider()` 方法简化为 `return this.locationProviderTry.getOrThrow()`。由于 `Try` 是 `Serializable` 且持有 `LocationProvider` 引用，要求 `LocationProvider` 实现可序列化（文档已更新）。

3. **测试覆盖**：在 core 的 `TestTableSerialization`（Hadoop）和 Spark v3.4/v3.5/v4.0 的 `TestTableSerialization` 中，新增三个测试：验证 LocationProvider 异常被延迟（`testLocationProviderExceptionIsDeferred`）、Java 序列化后异常仍可重现（`testLocationProviderExceptionJavaSerialization`）、Kryo 序列化后异常仍可重现（`testLocationProviderExceptionKryoSerialization`）。这些测试通过 Mockito spy 让原表的 `locationProvider()` 抛出异常，验证 `copyOf` 不抛异常但后续 `locationProvider()` 调用会抛出原异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java` (+20/-13 lines)

**修改目的**：改为在构造时捕获原始 LocationProvider，而非反序列化后重建。

**工作逻辑**：
- 类文档更新：在可序列化假设中新增 `LocationProvider`，说明若使用 Kryo 等自定义序列化框架，`LocationProvider` 实例也需被该框架支持。
- 字段：移除 `private transient volatile LocationProvider lazyLocationProvider = null`，新增 `private final Try<LocationProvider> locationProviderTry`。
- 构造器：新增 `this.locationProviderTry = Try.of(table::locationProvider)`，捕获原始 LocationProvider 或其异常。
- `locationProvider()` 方法：从原先的双检锁懒加载（`LocationProviders.locationsFor(location, properties)`）简化为 `return this.locationProviderTry.getOrThrow()`。

### `core/src/main/java/org/apache/iceberg/Try.java` (+68/-0 lines, 新文件)

**修改目的**：新增一个可序列化的"成功或异常"容器，用于延迟异常抛出。

**工作逻辑**：
- `Try<T>` 实现 `Serializable`，持有 `value` 和 `throwable` 两个字段。
- `static <T> Try<T> of(SerializableSupplier<T> supplier)`：执行 supplier，成功则包装值，失败则包装异常。
- `T getOrThrow()`：若有异常则通过 `sneakyThrow` 重抛原始异常（保持原始异常类型，不包装为 `RuntimeException`），否则返回值。
- `sneakyThrow` 利用 Java 泛型类型擦除实现"sneaky throw"，避免在方法签名上声明 checked exception。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java` (+50/-0 lines)

**修改目的**：为 Hadoop 表的场景新增 LocationProvider 异常延迟与序列化测试。

**工作逻辑**：新增三个测试方法：
- `testLocationProviderExceptionIsDeferred`：spy 原 table 使 `locationProvider()` 抛 `RuntimeException`，验证 `SerializableTable.copyOf` 不抛异常，但后续 `locationProvider()` 抛出相同异常，且原 table 的 `locationProvider()` 被调用恰好 1 次。
- `testLocationProviderExceptionJavaSerialization`：验证经 Java 序列化往返后，反序列化对象调用 `locationProvider()` 仍抛出含相同消息的 `RuntimeException`。
- `testLocationProviderExceptionKryoSerialization`：验证经 Kryo 序列化往返后的相同行为。

### `spark/v3.4|v3.5|v4.0/spark/src/test/java/org/apache/iceberg/TestTableSerialization.java` (各 +43/-0 lines)

**修改目的**：为 Spark 三个版本同步新增 LocationProvider 异常延迟与序列化测试。

**工作逻辑**：与 core 测试类似，但使用 `SerializableTableWithSize.copyOf`（Spark 使用的子类）。三个测试方法逻辑相同，覆盖 `testLocationProviderExceptionIsDeferred`、`testLocationProviderExceptionJavaSerialization`、`testLocationProviderExceptionKryoSerialization`。

## 总结

本提交修复了 `SerializableTable` 不尊重自定义 `LocationProvider` 的缺陷（#12564）。核心思路是在构造时通过 `Try.of(table::locationProvider)` 捕获原始 LocationProvider 实例（或其异常），序列化传递该实例，反序列化后直接返回，而非基于 location/properties 重建。新增的 `Try` 工具类优雅地处理了"构造时 LocationProvider 可能抛异常"的边界情况，将异常延迟到实际访问时。配套测试覆盖了异常延迟、Java 序列化、Kryo 序列化三种场景，确保修复在主流序列化框架下都有效。修复后，使用自定义 LocationProvider 的表在 Spark 等分布式引擎中能正确地在远端节点使用原始的路径策略，避免数据文件写错位置。
