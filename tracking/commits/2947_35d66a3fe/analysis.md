# 提交 2947：Core: Support Custom Table/View Operations in RESTCatalog (#14465)

## 提交信息

- **序号**：2947 / 4088
- **哈希**：35d66a3fe8ad76e1c76b39a898d9477f1da023ae
- **短哈希**：35d66a3fe
- **日期**：2025-12-02
- **作者**：Rulin Xing
- **提交说明**：Core: Support Custom Table/View Operations in RESTCatalog (#14465)
- **PR/Issue**：#14465

## 总体目的

`RESTCatalog` 在内部通过 `RESTTableOperations` 和 `RESTViewOperations` 与 REST Catalog 服务端通信：每次加载/创建/替换表或视图时，`RESTSessionCatalog` 都会 `new RESTTableOperations(...)` / `new RESTViewOperations(...)` 直接构造这些对象。对于需要扩展 Iceberg 行为的下游用户（例如想在每次 REST 请求中注入自定义 HTTP 头、附加鉴权信息、做请求审计、或替换为带缓存的 Operations 实现），唯一的做法是 fork 整个 `RESTSessionCatalog`，因为构造点散落在 5+ 处且全部硬编码 `new RESTTableOperations(...)`，子类无法介入。

本次提交把这些直接 `new` 改成可覆写的工厂方法 `newTableOps(...)`（两个重载：一个用于普通加载，一个用于 create/replace 事务）和 `newViewOps(...)`，并在 `RESTCatalog` 顶层新增 `newSessionCatalog(...)` 钩子，让子类可以整体替换 `RESTSessionCatalog` 实现。这样下游只需继承 `RESTSessionCatalog`/`RESTCatalog` 并覆写对应工厂方法，就能插入自定义的 Table/View Operations，而无需复制粘贴大量加载逻辑。

## 如何达成设计目的

核心思路是"用 protected 工厂方法替换直接 new"。改动涉及三个层次：`RESTCatalog` 暴露 `newSessionCatalog` 让子类注入自定义 SessionCatalog；`RESTSessionCatalog` 把 5 处 `new RESTTableOperations(...)` 和 3 处 `new RESTViewOperations(...)` 替换为 `newTableOps`/`newViewOps` 调用，并提供带完整签名的默认实现；新增测试用自定义 Operations（注入 `X-Custom-Table-Header`/`X-Custom-View-Header`）验证钩子确实被调用且请求带上了自定义头。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java` (+16/-2 lines)

**修改目的**：在 `RESTCatalog` 构造器暴露可覆写的 `newSessionCatalog` 钩子。

**工作逻辑**：
构造器原本直接 `this.sessionCatalog = new RESTSessionCatalog(clientBuilder, null)`，现在改为 `this.sessionCatalog = newSessionCatalog(clientBuilder)`。新增的 `protected RESTSessionCatalog newSessionCatalog(Function<Map<String, String>, RESTClient> clientBuilder)` 默认实现仍返回 `new RESTSessionCatalog(clientBuilder, null)`，行为不变；但子类可以覆写它返回自定义的 `RESTSessionCatalog` 子类。这是让整条扩展链生效的入口——因为 `RESTCatalog` 是用户面对的类，只有它提供了钩子，用户才能在不改造 `RESTCatalog` 内部的前提下注入自定义 SessionCatalog。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+93/-7 lines)

**修改目的**：把对 `RESTTableOperations` 和 `RESTViewOperations` 的直接构造替换为可覆写的工厂方法。

**工作逻辑**：
`RESTSessionCatalog` 中有 5 处构造 `RESTTableOperations`：分别对应普通 `loadTable`、事务性 `createTable`/`replaceTable` 路径以及 `registerTable`/`loadTable` 的若干变体，以及 `createTable` 的事务分支。这些调用点全部从 `new RESTTableOperations(...)` 改为 `newTableOps(...)`。两个重载分别对应两种构造签名：
- 简单加载场景：`(RESTClient, String path, Supplier<Map<String,String>> headers, FileIO, TableMetadata current, Set<Endpoint> supportedEndpoints)`；
- 事务性 create/replace 场景：多了 `RESTTableOperations.UpdateType updateType` 与 `List<MetadataUpdate> createChanges` 参数。

类似地，3 处 `new RESTViewOperations(...)`（`createView`、`loadView` 的两个分支）改为 `newViewOps(...)`。每个工厂方法都是 `protected`，默认实现就是把原参数原样传给 `new RESTTableOperations(...)`/`new RESTViewOperations(...)`，保证行为完全兼容。同时新增 `import java.util.function.Supplier` 以匹配 headers 参数类型。这种"工厂方法+默认实现"是典型的模板方法模式，让 Iceberg 在不破坏现有 API 的前提下提供了扩展点。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+173/-2 lines)

**修改目的**：验证自定义 `RESTTableOperations` 能通过子类化的方式注入并被实际使用。

**工作逻辑**：
新增 `testCustomTableOperationsInjection` 测试。测试定义了内嵌的 `CustomRESTTableOperations`，它在构造时把 headers supplier 替换成返回 `X-Custom-Table-Header: custom-value-12345` 的固定 Map，并设置 `AtomicBoolean` 标记。再定义 `CustomRESTSessionCatalog` 覆写两个 `newTableOps` 重载，返回 `Mockito.spy` 包装的自定义 ops。通过新加的 `catalog(adapter, sessionCatalogFactory)` 工厂方法（利用 `RESTCatalog` 新的 `newSessionCatalog` 钩子匿名覆写）构造 catalog，然后：
- `catalog.createTable(TABLE, SCHEMA)` 触发事务版 `newTableOps`，断言 `customTransactionTableOpsCalled` 为 true；
- `table.updateProperties()...commit()` 触发普通版 `newTableOps`，并通过 `Mockito.verify(capturedOps.get()).current()`/`commit(...)` 确认自定义 ops 真的被调用；
- 用 `reqMatcher(POST, RESOURCE_PATHS.table(TABLE), customHeaders)` 验证发往 adapter 的请求确实带上了自定义头；
- 再用 `buildTable(table2, SCHEMA).createTransaction().commitTransaction()` 覆盖纯事务路径。测试还复用了 `catalog(adapter, sessionCatalogFactory)` 这个新的辅助方法，它通过匿名子类覆写 `newSessionCatalog` 来注入自定义 SessionCatalog，是对外暴露的扩展用法的活样板。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+110/-1 lines)

**修改目的**：验证自定义 `RESTViewOperations` 的注入与使用，与 table 侧对称。

**工作逻辑**：
`testCustomViewOperationsInjection` 定义 `CustomRESTViewOperations` 注入 `X-Custom-View-Header`，并定义 `CustomRESTSessionCatalog` 覆写 `newViewOps`。通过 `catalog.buildView(...).create()` 创建视图后，断言 `customViewOpsCalled` 为 true；再 `view.updateProperties().set("test-key","test-value").commit()` 触发 commit，用 `Mockito.verify` 确认 `current()` 与 `commit(...)` 被调用，并校验发往 `resourcePaths.view(viewIdentifier)` 的 POST 请求带上了自定义头。同样新增了 `catalog(adapter, sessionCatalogFactory)` 辅助方法，与 table 侧测试保持一致。

## 总结

该提交通过引入 `newSessionCatalog`/`newTableOps`/`newViewOps` 三个 protected 工厂方法，把 RESTCatalog 内部对 Operations 的硬编码构造解耦为可子类化的扩展点，让下游无需 fork 即可注入自定义 Table/View Operations（典型用途是注入自定义 HTTP 头、鉴权或缓存）。默认实现保持原行为，配套测试以"注入自定义头 + Mockito 验证请求"的方式证明了扩展链可用，是一次低风险、高扩展性的 API 改进。
