# 提交 0190：Core: Lazily create LocationProvider in SerializableTable (#9029)

## 提交信息

- **序号**：0190 / 4088
- **哈希**：229a243ddad20a725d9f7e4446ce8bb3fc2c9a53
- **短哈希**：229a243dd
- **日期**：2023-11-21 20:53:23 -0800
- **作者**：przemekd
- **提交说明**：Core: Lazily create LocationProvider in SerializableTable (#9029)
- **PR/Issue**：#9029

## 总体目的

本提交把 [`SerializableTable`](../../../core/src/main/java/org/apache/iceberg/SerializableTable.java) 中 `LocationProvider` 的获取方式从"构造时急切捕获并随对象序列化"改为"在执行节点上按需懒构造"，从而不再要求 `LocationProvider` 实例本身可序列化。

`SerializableTable` 是 Iceberg 提供的只读可序列化表副本，用于把表状态发送到集群中的其他节点（典型场景是 Spark driver 把表对象广播/序列化到 executor）。它持久化的是表当前状态的快照：`name`、`location`、`metadataFileLocation`、`properties`、schema/spec/sortOrder 的 JSON、`FileIO`、`EncryptionManager`、`refs` 等，以便在 executor 上不必回读 metadata 文件即可获得常用元数据。

改动前，`SerializableTable` 在构造函数里调用 `table.locationProvider()` 把 `LocationProvider` 实例存入 `final` 字段 `locationProvider`，并随对象一起序列化传输。类 Javadoc 据此声明：传入的 `FileIO`、`EncryptionManager`、`LocationProvider` 必须可序列化；若用 Kryo 等自定义序列化框架，这些实例也必须被该框架支持。这一约束带来实际问题：

1. `LocationProvider` 由 `LocationProviders.locationsFor(location, properties)` 工厂构造，其实现可能持有 `FileIO` 或 Hadoop `Configuration` 等不平凡状态，并非总能让 Java/Kryo 干净地序列化。
2. 即便 `LocationProvider` 本身轻量，强制它可序列化也为使用方增加了一项隐性义务，与"在 executor 上用 `location` + `properties` 即可重建"的事实不符——`LocationProviders.locationsFor` 本就是从 `location`（String）与 `properties`（Map）这两项已序列化字段纯函数式地构造 `LocationProvider`。

改动后：`locationProvider` 字段移除，新增 `transient volatile LocationProvider lazyLocationProvider = null`，`locationProvider()` 方法用双检锁（double-checked locking）在首次访问时调用 `LocationProviders.locationsFor(location, properties)` 现场构造。`transient` 保证该字段不参与序列化，`volatile` + 双检锁保证多线程下安全初始化。这与类内既有的 `lazyTable`、`lazySchema`、`lazySpecs`、`lazySortOrder` 完全同构——它们都是 `transient volatile` + 双检锁懒加载。

意义：消除了 `LocationProvider` 必须可序列化的约束，减少序列化失败面（尤其在 Kryo/Hadoop Configuration 场景），让 `SerializableTable` 的序列化依赖面收敛到 `FileIO` 与 `EncryptionManager` 两项，并把 `LocationProvider` 的构造延迟到真正被读取路径需要时，与类内其他派生状态的懒加载策略统一。

## 如何达成设计目的

整体设计是"字段降级 + 方法懒加载 + 文档同步"：

1. 删除 `final LocationProvider locationProvider` 字段，新增 `transient volatile LocationProvider lazyLocationProvider = null`，使其不参与序列化、且线程安全懒初始化。
2. 构造函数移除 `this.locationProvider = table.locationProvider();`，不再在 driver 侧捕获 `LocationProvider`。
3. `locationProvider()` 改为双检锁：若 `lazyLocationProvider == null`，进入 `synchronized(this)` 块再次判空后调用 `LocationProviders.locationsFor(location, properties)` 构造并赋值，最后返回。
4. `lazyTable()` 中原本传 `locationProvider`（字段）给 `StaticTableOperations`，改为传 `locationProvider()`（方法调用，触发懒加载）。
5. 类 Javadoc 移除"LocationProvider 必须可序列化"的声明，只保留 `FileIO`、`EncryptionManager`。

`LocationProviders.locationsFor` 是纯工厂：依据 `properties` 中 `WRITE_LOCATION_PROVIDER_IMPL`（自定义实现，反射构造）、对象存储路径属性等决定返回自定义实现、`ObjectStoreLocationProvider` 或 `TableLocationProvider`，输入仅 `location` 与 `properties`——这两项本就是 `SerializableTable` 的已序列化字段，因此在 executor 上重建 `LocationProvider` 无需任何额外序列化支持。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：把 `LocationProvider` 从急切捕获的可序列化字段改为执行节点上懒构造的 `transient` 状态，解除其序列化约束。

**工作逻辑**：

1. 类 Javadoc 调整：

   原：

   ```
   The implementation assumes the passed instances of FileIO, EncryptionManager,
   LocationProvider are serializable. If you are serializing the table using a custom
   serialization framework like Kryo, those instances of FileIO, EncryptionManager,
   LocationProvider must be supported by that particular serialization framework.
   ```

   新：

   ```
   The implementation assumes the passed instances of FileIO, EncryptionManager
   are serializable. If you are serializing the table using a custom serialization framework like
   Kryo, those instances of FileIO, EncryptionManager must be supported by that
   particular serialization framework.
   ```

   明确告知使用方：`LocationProvider` 不再需要可序列化。

2. 字段替换：

   原：

   ```java
   private final LocationProvider locationProvider;
   ```

   新：

   ```java
   private transient volatile LocationProvider lazyLocationProvider = null;
   ```

   `transient`：不参与 Java 默认序列化（Kryo 若按字段序列化也通常尊重 `transient`，或在 Iceberg 的 `SerializableTable` 受控序列化下被忽略），反序列化后为 `null`，由懒加载重建。`volatile`：保证一个线程的初始化写入对其他线程可见，配合双检锁避免重复构造。该字段与既有 `lazyTable`、`lazySchema`、`lazySpecs`、`lazySortOrder` 并列，命名与风格一致。

3. 构造函数移除急切捕获：

   原：

   ```java
   this.io = fileIO(table);
   this.encryption = table.encryption();
   this.locationProvider = table.locationProvider();
   this.refs = SerializableMap.copyOf(table.refs());
   ```

   新：

   ```java
   this.io = fileIO(table);
   this.encryption = table.encryption();
   this.refs = SerializableMap.copyOf(table.refs());
   ```

   driver 侧不再调用 `table.locationProvider()`，避免捕获可能不可序列化的实例。

4. `locationProvider()` 改为双检锁懒加载：

   原：

   ```java
   @Override
   public LocationProvider locationProvider() {
     return locationProvider;
   }
   ```

   新：

   ```java
   @Override
   public LocationProvider locationProvider() {
     if (lazyLocationProvider == null) {
       synchronized (this) {
         if (lazyLocationProvider == null) {
           this.lazyLocationProvider = LocationProviders.locationsFor(location, properties);
         }
       }
     }
     return lazyLocationProvider;
   }
   ```

   双检锁模式：第一次判空避免已初始化时的锁开销；进入 `synchronized(this)` 后再次判空防止多线程同时通过第一次判空时重复构造。构造仅依赖 `location`（String）与 `properties`（Map），二者已是 `SerializableTable` 的 `final` 序列化字段，在 executor 上可用。这与类内 `lazyTable()`、`schema()`、`specs()`、`sortOrder()` 的懒加载风格完全一致。

5. `lazyTable()` 调用点适配：

   原：

   ```java
   TableOperations ops =
       new StaticTableOperations(metadataFileLocation, io, locationProvider);
   ```

   新：

   ```java
   TableOperations ops =
       new StaticTableOperations(metadataFileLocation, io, locationProvider());
   ```

   把字段引用改为方法调用，使 `StaticTableOperations` 拿到的 `LocationProvider` 也是懒构造所得。`StaticTableOperations` 的三参构造接受 `LocationProvider`（可为 null），此处传入懒加载后的实例，行为与改动前等价，但仅在 `lazyTable()` 真正被调用（即需要加载完整 metadata）时才触发 `LocationProvider` 构造。

## 小结

通过把 `LocationProvider` 从急切捕获的可序列化字段改为 `transient volatile` 双检锁懒加载字段，`SerializableTable` 不再要求 `LocationProvider` 可序列化，将其序列化依赖面收敛到 `FileIO` 与 `EncryptionManager`，并与类内既有的懒加载模式统一，降低了序列化失败风险与使用方约束。
