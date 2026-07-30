# 提交 0291：Forward properties in HadoopCatalog initialization to the default HadoopFileIO (#9283)

## 提交信息

- **序号**：0291 / 4088
- **哈希**：c2018f894920ff6c50190c2b4ee113a4b10fb15a
- **短哈希**：c2018f894
- **日期**：2023-12-19 07:13:58 -0800
- **作者**：Reetika <agrawal.reetika786@gmail.com>
- **提交说明**：Forward properties in HadoopCatalog initialization to the default HadoopFileIO (#9283)
- **PR/Issue**：#9283

## 总体目的

本提交要修复 `HadoopCatalog` 初始化逻辑中一个长期存在的属性丢失问题。在 `HadoopCatalog.initialize(name, properties)` 方法中，原本对 FileIO 实例的构造存在两条分支：当用户在 catalog 属性中显式配置了 `file-io-impl` 时，会通过 `CatalogUtil.loadFileIO(fileIOImpl, properties, conf)` 加载并初始化 FileIO；当未配置该属性时，则直接通过 `new HadoopFileIO(conf)` 构造一个默认的 `HadoopFileIO` 实例。问题恰恰出在第二条分支：直接调用构造器只会注入 Hadoop `Configuration`，而完全跳过了 `FileIO.initialize(Map)` 调用，导致 catalog 的属性 map（例如 `warehouse`、`io.manifest.cache-enabled` 等）无法传递给默认的 `HadoopFileIO` 实例。

这一行为的不一致性会带来实际的功能影响。`HadoopFileIO` 内部维护了一个 `SerializableMap<String, String> properties` 字段，默认是空 map，只有 `initialize(Map)` 方法才会填充它。许多依赖 `FileIO.properties()` 的下游能力（例如 manifest 缓存 `io.manifest.cache-enabled`、各种 FileIO 行为开关、metrics 配置等）会在读取属性时取不到值，从而退化为默认行为。对用户而言，他们通过 catalog 属性配置的某些 FileIO 相关参数在 `HadoopCatalog` 默认路径下完全失效，这与配置了 `file-io-impl` 时的行为不一致，属于隐蔽的可用性问题。

更深层的动机是统一 Iceberg Catalog 在默认 IO 与自定义 IO 两条初始化路径上的语义。Iceberg 的设计原则是 catalog 属性应能透传给底层的 FileIO，使所有 FileIO 实现（包括默认的 `HadoopFileIO`）都能依据同一份属性进行配置。因此本提交通过把"默认 FileIO"也走 `CatalogUtil.loadFileIO` 的标准加载流程，消除了之前用 `new HadoopFileIO(conf)` 绕过 `initialize` 的特殊分支，从而保证属性在任何情况下都能被正确转发，并补齐单元测试以防回归。

## 如何达成设计目的

设计思路非常直接：统一 FileIO 的构造路径，让默认实现也走标准的反射加载 + `initialize` 流程。具体做法是用 `properties.getOrDefault(CatalogProperties.FILE_IO_IMPL, "org.apache.iceberg.hadoop.HadoopFileIO")` 取得 FileIO 实现类名——当用户未配置时显式回退到 `HadoopFileIO` 的全限定类名——然后无条件调用 `CatalogUtil.loadFileIO(fileIOImpl, properties, conf)`。

`CatalogUtil.loadFileIO` 的内部逻辑是：通过 `DynConstructors` 反射调用无参构造器创建实例；若对象实现了 `Configurable`（`HadoopFileIO` 实现了 `HadoopConfigurable`，其 `setConf` 会用 `SerializableConfiguration` 包装并注入 Hadoop `Configuration`），则注入 Hadoop 配置；最后调用 `fileIO.initialize(properties)` 把 catalog 属性传入。这样默认的 `HadoopFileIO` 既能拿到 Hadoop `Configuration`，也能拿到完整的属性 map，与配置了 `file-io-impl` 的路径行为完全一致。同时在测试侧新增 `testHadoopFileIOProperties`，验证初始化后 `FileIO.properties()` 中确实包含 `warehouse` 与 `io.manifest.cache-enabled` 等条目，确保修复可被回归测试覆盖。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopCatalog.java`

**修改目的**：让默认 `HadoopFileIO` 的构造也走 `CatalogUtil.loadFileIO`，从而把 catalog 属性透传给 FileIO。

**工作逻辑**：
原代码片段如下：

```java
String fileIOImpl = properties.get(CatalogProperties.FILE_IO_IMPL);
this.fileIO =
    fileIOImpl == null
        ? new HadoopFileIO(conf)
        : CatalogUtil.loadFileIO(fileIOImpl, properties, conf);
```

这里存在两个问题：一是当 `fileIOImpl == null` 时直接 `new HadoopFileIO(conf)`，只调用了带 `Configuration` 参数的构造器，并未调用 `initialize(Map)`，因此 `HadoopFileIO.properties` 保持为空 map；二是两条路径行为不一致，使属性透传逻辑依赖于是否配置了 `file-io-impl`。

修改后的代码：

```java
String fileIOImpl =
    properties.getOrDefault(
        CatalogProperties.FILE_IO_IMPL, "org.apache.iceberg.hadoop.HadoopFileIO");

this.fileIO = CatalogUtil.loadFileIO(fileIOImpl, properties, conf);
```

改动要点：
- 用 `getOrDefault` 把"未配置"的回退值显式写为 `HadoopFileIO` 的全限定类名，等价于原来的默认行为，但统一了入口。
- 删除了三元表达式，无条件调用 `CatalogUtil.loadFileIO(fileIOImpl, properties, conf)`。`loadFileIO` 会反射构造 `HadoopFileIO` 实例，调用 `setConf(conf)` 注入 Hadoop 配置（因为 `HadoopFileIO` 实现了 `HadoopConfigurable`），再调用 `initialize(properties)` 把 catalog 属性写入 `HadoopFileIO.properties`。
- 结果是默认路径与自定义路径在属性转发上完全对齐，下游调用 `fileIO.properties()` 总能拿到完整的 catalog 属性。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCatalog.java`

**修改目的**：新增单元测试 `testHadoopFileIOProperties`，验证默认 `HadoopFileIO` 在 `HadoopCatalog` 初始化后能拿到 catalog 属性，防止未来回归。

**工作逻辑**：
新增测试方法构造了一个最小可复现的 catalog 属性 map：

```java
ImmutableMap.of(
    "warehouse", "/hive/testwarehouse",
    "io.manifest.cache-enabled", "true");
```

其中 `io.manifest.cache-enabled` 是典型的 FileIO 相关属性，正是会被原先默认路径丢失的那类条目。然后：

- `new HadoopCatalog()` + `catalog.setConf(new Configuration())` + `catalog.initialize("hadoop", catalogProps)` 完成初始化，注意这里没有设置 `file-io-impl`，因此走的是默认 `HadoopFileIO` 路径——正是修复前出问题的分支。
- `catalog.newTableOps(tableIdent).io()` 取出 `HadoopCatalog` 内部使用的 `FileIO` 实例（`newTableOps` 返回的 `HadoopTableOperations` 持有 catalog 的 `fileIO`）。
- 用 AssertJ 断言 `fileIO.properties()` 同时包含 `warehouse=/hive/testwarehouse` 与 `io.manifest.cache-enabled=true`。

测试用例直接对应修复目标：在修复前，由于 `initialize` 没被调用，`fileIO.properties()` 是空 map，两个断言都会失败；修复后断言通过，从而把"默认 FileIO 也必须接收 catalog 属性"这一行为以测试形式固化下来。

## 小结

本提交修复了 `HadoopCatalog` 在默认 `HadoopFileIO` 路径下不向 FileIO 转发 catalog 属性的缺陷。修复手法极简——把分支收敛到 `CatalogUtil.loadFileIO` 一个入口——但收益是实质性的：用户配置的 `io.manifest.cache-enabled`、`warehouse` 等 FileIO 相关属性在 `HadoopCatalog` 默认场景下也能正确生效，消除了"配置但不生效"的隐蔽陷阱，并补齐了回归测试。这种把特殊分支收敛到统一加载入口的改法，符合 Iceberg 一贯的"通过 `CatalogUtil` 反射加载 + `initialize` 注入属性"的设计约定，提升了代码的一致性与可维护性。
