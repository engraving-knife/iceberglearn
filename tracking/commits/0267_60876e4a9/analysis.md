# 提交 0267：Hive: Make HiveMetastoreExtension configurable (#9288)

## 提交信息

- **序号**：0267 / 4088
- **哈希**：60876e4a925537780fc2198a21fbf26dd8598d36
- **短哈希**：60876e4a9
- **日期**：2023-12-13 09:41:16 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Hive: Make HiveMetastoreExtension configurable (#9288)
- **PR/Issue**：#9288

## 总体目的

`HiveMetastoreExtension` 是 Iceberg Hive 模块测试体系中的核心 JUnit 5 扩展（`@RegisterExtension`），负责在测试启动时拉起一个内嵌的 Hive Metastore、创建对应的 `HiveMetaStoreClient`，并在 `beforeAll` 阶段根据传入的数据库名预先创建一个数据库（database），测试结束时再统一清理资源。改造前，该扩展仅暴露一个 public 构造函数 `HiveMetastoreExtension(String databaseName, Map<String, String> hiveConfOverride)`，测试类必须同时传入数据库名和 Hive 配置覆盖项（即使是空 Map）。

这种直接构造的方式存在几个问题：其一，调用方必须显式构造一个 `Collections.emptyMap()` 或 `ImmutableMap.of(...)` 来表示"无配置覆盖"或"有配置覆盖"，写法冗长且语义不直观；其二，构造参数是位置参数（positional arguments），调用方容易把数据库名和配置 Map 顺序写反，编译期无法发现；其三，更关键的是，当某些测试场景不需要预创建数据库（例如测试本身会在 `beforeEach` 中按需创建数据库）时，原来的构造函数强制要求传入一个非空的 `databaseName`，否则会在 `beforeAll` 中尝试用 `null` 数据库名调用 `metastore.getDatabasePath(databaseName)` 而触发 NPE。

本次改造的目标是引入 Builder 模式重构 `HiveMetastoreExtension` 的构造方式，使配置更灵活、可读性更强，并允许 `databaseName` 为 `null` 时跳过数据库创建步骤，从而支持更多变的测试场景。

## 如何达成设计目的

整体思路是引入一个静态内部 `Builder` 类，将原 public 构造函数降级为 `private`，强制调用方通过 `HiveMetastoreExtension.builder()` 链式构造实例。Builder 提供 `withDatabase(String)` 和 `withConfig(Map<String,String>)` 两个可选方法，二者都不强制调用，未调用时对应字段保持为 `null`。在 `beforeAll` 中增加对 `databaseName` 的非空判断，只有非空时才执行建库逻辑。同时把所有测试子类中 `new HiveMetastoreExtension(DB_NAME, ...)` 的调用统一替换为 Builder 风格，移除不再需要的 `Collections` 导入。

## 修改详情

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveMetastoreExtension.java`

**修改目的**：将 `HiveMetastoreExtension` 改造为 Builder 模式，并允许 `databaseName` 为 `null` 时跳过建库。

**工作逻辑**：
1. 构造函数可见性由 `public` 改为 `private`，阻止外部直接 `new`，统一走 Builder 入口。
2. `beforeAll` 中原本无条件执行的建库逻辑被包裹进 `if (null != databaseName) { ... }` 判断内。这样当测试不需要预创建数据库时，可通过 `HiveMetastoreExtension.builder().build()`（不调用 `withDatabase`）获得一个仅启动 metastore、不建库的扩展实例，避免对 `null` 数据库名调用 `getDatabasePath` 导致 NPE。
3. 新增静态方法 `builder()` 返回一个新的 `Builder` 实例。
4. 新增静态内部类 `Builder`，包含 `databaseName` 与 `config` 两个字段，提供：
   - `withDatabase(String)`：设置要预创建的数据库名；
   - `withConfig(Map<String,String>)`：设置 Hive 配置覆盖项；
   - `build()`：调用 private 构造函数生成 `HiveMetastoreExtension` 实例。
   两个 `with*` 方法都返回 `this` 以支持链式调用，且都是可选的，未设置的字段保持为 `null`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveCreateReplaceTableTest.java`

**修改目的**：将扩展实例化方式从直接构造改为 Builder 风格。

**工作逻辑**：
原写法 `new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())` 替换为 `HiveMetastoreExtension.builder().withDatabase(DB_NAME).build()`。由于不再需要空 Map，对应移除了 `import java.util.Collections;`。该测试只需预创建数据库、无需额外 Hive 配置，因此不调用 `withConfig`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableBaseTest.java`

**修改目的**：同上，将扩展实例化改为 Builder 风格。

**工作逻辑**：
`new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())` 替换为 `HiveMetastoreExtension.builder().withDatabase(DB_NAME).build()`，并移除 `import java.util.Collections;`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestCachedClientPool.java`

**修改目的**：同上，将扩展实例化改为 Builder 风格。

**工作逻辑**：
`new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())` 替换为 `HiveMetastoreExtension.builder().withDatabase(DB_NAME).build()`，并移除 `import java.util.Collections;`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`

**修改目的**：同上，将扩展实例化改为 Builder 风格。

**工作逻辑**：
`new HiveMetastoreExtension(DB_NAME, Collections.emptyMap())` 替换为 `HiveMetastoreExtension.builder().withDatabase(DB_NAME).build()`，并移除 `import java.util.Collections;`。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommitLocks.java`

**修改目的**：将扩展实例化改为 Builder 风格，并演示带配置覆盖的用法。

**工作逻辑**：
原写法 `new HiveMetastoreExtension(DB_NAME, ImmutableMap.of(HiveConf.ConfVars.HIVE_TXN_TIMEOUT.varname, "1s"))` 替换为链式调用：

```java
HiveMetastoreExtension.builder()
    .withDatabase(DB_NAME)
    .withConfig(ImmutableMap.of(HiveConf.ConfVars.HIVE_TXN_TIMEOUT.varname, "1s"))
    .build();
```

该测试需要将 Hive 事务超时（`HIVE_TXN_TIMEOUT`）设置为 `1s` 以便快速触发锁超时场景，因此同时使用了 `withDatabase` 和 `withConfig`，恰好体现了 Builder 模式在多参数场景下的可读性优势——参数含义通过方法名自解释，不再依赖位置。

## 小结

本次提交通过为 `HiveMetastoreExtension` 引入 Builder 模式并允许 `databaseName` 为空，解决了原构造方式写法冗长、参数顺序易错、无法跳过建库等问题，并将所有 Hive 测试子类的实例化方式统一迁移到 Builder 风格，提升了测试基础设施的可读性与灵活性，为后续需要"只启 metastore、不预建库"的测试场景扫清了障碍。
