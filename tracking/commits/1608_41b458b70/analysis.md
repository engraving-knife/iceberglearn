# 提交 1608 41b458b70 分析

## 提交信息
- 哈希：41b458b7022c7b0cd78eeca9102392db7889d3c9
- 日期：2025-01-20 11:51:12 +0100
- 作者：Eduard Tudenhoefner
- 消息：Core: List namespaces/tables when testing identifier with a dot (#11991)

## 总体目的

本次提交强化了 Iceberg 通用目录测试基类 `CatalogTests` 中针对“带点标识符”（identifier with a dot）的测试覆盖，并完善了不同 catalog 实现对带点命名空间/表名的支持声明。

具体来说包含三方面目的：

1. 在 `testNamespaceWithDot` 与 `testTableNameWithDot` 测试中，原本只验证带点的命名空间/表能够被创建、加载、删除，但没有验证它们能被正确“列举”出来。带点标识符（如命名空间 `new.db`、表名 `ta.ble`）在许多 catalog 实现中容易被错误地按点拆分成多级命名空间，导致列举（`listNamespaces` / `listTables`）时遗漏或错配。本次提交新增 `listNamespaces()` 与 `listTables(namespace)` 的断言，确保带点标识符在列举结果中可见，从而更严格地校验 catalog 对带点命名的端到端支持。

2. 将若干错误消息断言从硬编码字符串（如 `"Namespace does not exist: newdb"`、`"Table does not exist: newdb.table"`）改为参数化格式 `"Namespace does not exist: %s", NS` / `"Table does not exist: %s", TABLE`，使断言与实际使用的 `NS`/`TABLE` 标识符保持同步，避免测试常量调整后断言文本失配。

3. 为不支持带点命名空间的 catalog 实现（JdbcCatalog、JdbcCatalogWithV1Schema、REST 兼容性测试套件）补充 `supportsNamesWithDot()` 覆盖声明，使这些 catalog 在运行 `testNamespaceWithDot` / `testTableNameWithDot` 时正确跳过，而非误报失败。

## 如何达成设计目的

设计思路是在 `CatalogTests` 抽象测试基类中通过“能力开关”方法（`supportsNamesWithDot()` 等）声明各 catalog 的特性支持情况，并在测试方法中用 `Assumptions.assumeTrue(supportsNamesWithDot())` 做条件跳过。本次提交在这一既有机制基础上：在带点测试中追加列举断言以提升覆盖强度，并在各 catalog 子类测试中正确声明 `supportsNamesWithDot()` 的取值。

需要特别说明：`supportsNamesWithDot()` 这一能力开关方法本身（默认返回 `true`，并在 `testNamespaceWithDot` / `testTableNameWithDot` 中通过 `Assumptions.assumeTrue` 调用）是在更早的 main 分支提交中引入的，本次提交并未新增该基类方法，而是依赖其已存在的前提，仅在各子类中补充覆盖声明。

### 修改详情

#### core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java

这是所有 catalog 实现共用的抽象测试基类，本次修改集中在四处：

1. **`testNamespaceWithDot` 新增列举断言**：在创建带点命名空间 `new.db` 并确认其存在后，新增
   ```java
   assertThat(catalog.listNamespaces()).contains(withDot);
   ```
   确保带点命名空间出现在根命名空间列举结果中，防止 catalog 把 `new.db` 误拆成 `new` 下的 `db` 子命名空间而无法在根级别列出。

2. **`testTableNameWithDot` 新增列举断言并整理变量**：将 `TableIdentifier ident = TableIdentifier.of("ns", "ta.ble");` 改为先声明 `Namespace namespace = Namespace.of("ns");` 再 `TableIdentifier ident = TableIdentifier.of(namespace, "ta.ble");`，并在建表成功后新增
   ```java
   assertThat(catalog.listTables(namespace)).contains(ident);
   ```
   确保带点表名 `ta.ble` 在其命名空间 `ns` 的表列举结果中可见，防止 catalog 把 `ta.ble` 误解析为命名空间 `ta` 下的表 `ble`。

3. **错误消息断言参数化**：将 `testLoadNamespaceMetadata`、`testSetNamespacePropertiesNamespaceDoesNotExist`、`testRemoveNamespacePropertiesNamespaceDoesNotExist` 中 `.hasMessageStartingWith("Namespace does not exist: newdb")` 改为 `.hasMessageStartingWith("Namespace does not exist: %s", NS)`；将 `testReplacePartitions` 等中 `.hasMessageStartingWith("Table does not exist: newdb.table")` 改为 `.hasMessageStartingWith("Table does not exist: %s", TABLE)`。这样断言文本随测试常量 `NS`/`TABLE` 自动同步，避免常量改名后断言失效。

#### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java

新增覆盖方法，声明 JDBC catalog 不支持带点命名空间：
```java
@Override
protected boolean supportsNamesWithDot() {
  // namespaces with a dot are not supported
  return false;
}
```
JdbcCatalog 在数据库表中以命名空间名为键存储，带点的命名空间名会与命名空间层级分隔符冲突（或与表名解析逻辑冲突），因此不支持。声明 `false` 后，`testNamespaceWithDot` / `testTableNameWithDot` 会在该子类测试中被 `Assumptions.assumeTrue` 跳过，而非失败。

#### core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalogWithV1Schema.java

将原有的 `supportsNamespaceProperties()` 覆盖（返回 `true`）替换为 `supportsNamesWithDot()` 覆盖（返回 `false`）。由于基类 `supportsNamespaceProperties()` 默认就是 `true`，移除该冗余覆盖不改变 namespace properties 相关测试的行为；新增的 `supportsNamesWithDot() = false` 则使 V1 schema 的 JDBC catalog 同样跳过带点测试，与 `TestJdbcCatalog` 保持一致。

#### open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitCatalogTests.java

新增覆盖方法，使 REST 兼容性测试套件对“是否支持带点命名空间”可配置：
```java
@Override
protected boolean supportsNamesWithDot() {
  // underlying JDBC catalog doesn't support namespaces with a dot
  return PropertyUtil.propertyAsBoolean(
      restCatalog.properties(), RESTCompatibilityKitSuite.RCK_SUPPORTS_NAMES_WITH_DOT, false);
}
```
默认返回 `false`（因为默认后端是 JDBC catalog，不支持带点命名空间），但可通过 REST catalog 的属性 `rck.supports-names-with-dot` 开启，便于后端支持带点命名空间时启用测试。

#### open-api/src/test/java/org/apache/iceberg/rest/RESTCompatibilityKitSuite.java

新增常量声明 `RCK_SUPPORTS_NAMES_WITH_DOT = "rck.supports-names-with-dot";`，作为上述可配置开关的属性键名，与既有的 `RCK_REQUIRES_NAMESPACE_CREATE`、`RCK_SUPPORTS_SERVERSIDE_RETRY` 等开关风格一致。

## 小结

本次提交成效在于：通过在带点标识符测试中追加 `listNamespaces()` / `listTables()` 断言，更严格地校验 catalog 对带点命名空间/表名的端到端支持，能够暴露将点误当作层级分隔符的回归缺陷；同时通过参数化错误消息断言和补充 `supportsNamesWithDot()` 能力声明，提升了测试的健壮性与准确性。影响范围限于测试代码（`CatalogTests` 基类及 JDBC、REST 测试子类），不修改任何生产代码。

回迁到 1.4.x 分支的注意事项：
- **强前置依赖**：本提交依赖 `CatalogTests` 基类中已存在 `supportsNamesWithDot()` 方法（默认 `true`）并在 `testNamespaceWithDot` / `testTableNameWithDot` 中通过 `Assumptions.assumeTrue(supportsNamesWithDot())` 调用。1.4.x 分支当前尚未引入该基础设施（基类无 `supportsNamesWithDot` 方法，带点测试也未用 `assumeTrue` 守卫）。因此必须先回迁引入 `supportsNamesWithDot()` 基础设施的更早提交，否则本提交中各子类的 `@Override protected boolean supportsNamesWithDot()` 将因无可覆盖方法而编译失败。
- 回迁后需确认 1.4.x 中各 catalog 子类测试（JdbcCatalog、REST 等）的带点测试行为：JdbcCatalog 应跳过带点测试（与 main 一致），其它支持带点命名的 catalog（如 HiveCatalog、HadoopCatalog、REST 后端为非 JDBC 时）应能通过新增的列举断言。
- 若 1.4.x 的 `NS` / `TABLE` 测试常量与 main 不同，参数化错误消息断言（`%s`, NS / TABLE）已能自适应，无需手工对齐文本。
