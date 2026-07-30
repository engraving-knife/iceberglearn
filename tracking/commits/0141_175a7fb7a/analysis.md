# 提交 0141：Core: Use InMemoryCatalog as backend catalog (#9014)

## 提交信息

- **序号**：0141 / 4088
- **哈希**：175a7fb7a00af30e68fd217947f5342fe337d6b8
- **短哈希**：175a7fb7a
- **日期**：2023-11-09 15:44:20 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use InMemoryCatalog as backend catalog (#9014)
- **PR/Issue**：#9014

## 总体目的

这个提交修改了 `TestRESTCatalog` 测试类中作为 RESTCatalog 后端使用的 catalog 实现，将其从 `JdbcCatalog`（基于 SQLite 内存数据库）替换为 `InMemoryCatalog`（纯内存数据结构）。`TestRESTCatalog` 是 Iceberg REST catalog 的核心测试类，它通过一个本地 HTTP Server 暴露 `RESTCatalogAdapter`，后者需要一个真实的 catalog 作为后端来持久化表元数据。在此提交之前，这个后端用的是 `JdbcCatalog`，并配置了一个 `jdbc:sqlite:file::memory:` 的内存 SQLite 连接。

替换的动机主要有两点。首先，`JdbcCatalog` 在初始化时需要建立 JDBC 连接、建表（`iceberg_tables`、`iceberg_namespace_properties`）、配置用户名密码等，配置项较多且依赖 JDBC 驱动；而 `InMemoryCatalog` 只需要一个 warehouse 路径即可初始化，依赖更少、更轻量。其次，两者对命名空间（namespace）的语义不同：`JdbcCatalog` 在创建表时会自动隐式创建父命名空间，而 `InMemoryCatalog` 要求命名空间必须显式创建（即 `createNamespace`），不会自动创建。这正好让 `TestRESTCatalog` 走上 `CatalogTests` 基类中"要求显式创建命名空间"的测试路径，覆盖更严格的语义。

这个改动对 Iceberg 测试基础设施的简化有意义：减少了对 JDBC/SQLite 的依赖，使 REST catalog 测试更纯粹地聚焦于 REST 协议本身而非后端 catalog 的实现细节，同时通过启用 `requiresNamespaceCreate()` 路径使测试更严格地校验命名空间行为。

## 如何达成设计目的

整体设计思路是：把后端 catalog 的类型从 `JdbcCatalog` 换成 `InMemoryCatalog`，简化初始化代码；由于 `InMemoryCatalog` 不会隐式创建命名空间，覆写 `requiresNamespaceCreate()` 返回 `true`，并在所有需要先决命名空间的测试方法里补上 `createNamespace` 调用；同时把局部辅助方法 `catalog()` 的返回类型从通用 `Catalog` 收窄为 `RESTCatalog`，以便直接调用 `RESTCatalog` 上的方法。改动集中在一个测试文件，共约 80 行新增、23 行删除。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：将 REST catalog 测试的后端从 `JdbcCatalog` 切换为 `InMemoryCatalog`，并适配命名空间必须显式创建的语义。

**工作逻辑**：

1. **导入与字段类型变更**：移除 `import org.apache.iceberg.catalog.Catalog` 和 `import org.apache.iceberg.jdbc.JdbcCatalog`，新增 `import org.apache.iceberg.inmemory.InMemoryCatalog`。字段 `private JdbcCatalog backendCatalog` 改为 `private InMemoryCatalog backendCatalog`。

2. **初始化逻辑简化**（`@BeforeEach` 方法中）：原本需要构造 `JdbcCatalog`、设置 `Configuration`、构造包含 warehouse 路径、SQLite URI（带随机 UUID 避免共享）、用户名、密码的属性 map，再调用 `initialize("backend", ...)`。替换后只需 `new InMemoryCatalog()` 加 `initialize("in-memory", ImmutableMap.of(CatalogProperties.WAREHOUSE_LOCATION, warehouse.getAbsolutePath()))`，配置项大幅减少，不再需要 JDBC 凭据与随机 URI。

3. **覆写 `requiresNamespaceCreate()`**：新增方法覆写，返回 `true`，告诉 `CatalogTests` 基类该 catalog 不会自动创建命名空间，相关基类测试会走"要求显式创建命名空间"的分支（如 `tableCreationWithoutNamespace` 测试会断言在不存在的命名空间下建表会抛 `NoSuchNamespaceException`）。

4. **在多个测试方法中补建命名空间**：在 `testSnapshotLoadingMode` 等 6 个 REST catalog 特有测试方法（以及 `testCleanupUncommitedFilesForCleanableFailures` 等 5 个清理相关测试）中，在创建表之前插入 `if (requiresNamespaceCreate()) { catalog.createNamespace(TABLE.namespace()); }`（或对应 namespace）。这样在新的后端语义下，测试不会因为命名空间不存在而失败。

5. **`catalog()` 辅助方法返回类型收窄**：`private Catalog catalog(RESTCatalogAdapter adapter)` 改为 `private RESTCatalog catalog(RESTCatalogAdapter adapter)`。这一改动使得 `testCleanupUncommitedFilesForCleanableFailures`、`testNoCleanupForNonCleanableExceptions` 等 5 个清理相关测试中，由 `catalog(adapter)` 返回的对象可以直接以 `RESTCatalog` 类型使用（这些测试原本声明为 `Catalog catalog = catalog(adapter)`，同步改为 `RESTCatalog catalog = catalog(adapter)`），从而能调用 `RESTCatalog` 特有的方法或与 spy/mock 交互更精确。

## 小结

本提交通过把 REST catalog 测试的后端从重量级的 `JdbcCatalog`+SQLite 替换为轻量的 `InMemoryCatalog`，并启用"要求显式创建命名空间"的严格语义，简化了测试依赖、增强了命名空间行为的覆盖度，使 REST catalog 测试更聚焦于协议本身。
