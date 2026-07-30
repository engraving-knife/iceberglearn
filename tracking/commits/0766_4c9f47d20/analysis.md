# 提交 0766：Kafka-connect: Handle namespace creation for auto table creation (#10186)

## 提交信息

- **序号**：0766 / 4088
- **哈希**：4c9f47d208b16921f825a66e24d0693f2b76b03b
- **短哈希**：4c9f47d20
- **日期**：2024-05-16 00:44:20 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Kafka-connect: Handle namespace creation for auto table creation (#10186)
- **PR/Issue**：#10186

## 总体目的

本提交为 Kafka Connect Iceberg Sink 的"自动建表"（auto table creation）能力补齐了一个前置缺陷：在自动创建目标表之前，先确保目标表所在的命名空间（namespace）及其所有父级命名空间存在。此前 `IcebergWriterFactory.autoCreateTable` 在目标表不存在且开启自动建表时，直接调用 `catalog.createTable(...)`，但并未先创建命名空间。对于 REST、Hive、JDBC、Nessie 等支持命名空间语义的 Catalog，若命名空间不存在，`createTable` 会抛出 `NoSuchNamespaceException` 之类的错误，导致自动建表在多级命名空间场景下失败。本提交在 `autoCreateTable` 中新增 `createNamespaceIfNotExist` 步骤，递归地为目标命名空间的每一层级调用 `createNamespace`，并吞掉"已存在"和"无权限"两类异常，从而让自动建表对命名空间缺失的情况具备容错能力。

## 如何达成设计目的

### 问题背景

Iceberg 的 `Catalog` 接口区分两类：
- 仅支持表操作的 `Catalog`。
- 额外支持命名空间操作的 `SupportsNamespaces`（提供 `createNamespace`、`namespaceExists`、`listNamespaces` 等）。

Iceberg 的表标识符 `TableIdentifier` 由 `Namespace`（可多级，如 `foo1.foo2.foo3`）和表名构成。许多 Catalog（特别是 REST Catalog）要求命名空间必须先存在才能在其下建表，且要求**逐级存在**——即要建 `foo1.foo2.foo3.bar`，通常需要 `foo1`、`foo1.foo2`、`foo1.foo2.foo3` 三级命名空间依次存在。

Kafka Connect Sink 在收到 sink record 时，`IcebergWriterFactory.createWriter` 先 `loadTable`，若抛 `NoSuchTableException` 且配置开启了自动建表（`config.autoCreateEnabled()`），则调用 `autoCreateTable`。原 `autoCreateTable` 直接调用 `catalog.createTable(identifier, schema, partitionSpec, props)`，并未处理命名空间缺失的情况，因此当目标命名空间（尤其多级命名空间）不存在时建表会失败。

### 设计方案

新增静态方法 `createNamespaceIfNotExist(Catalog catalog, Namespace identifierNamespace)`，在 `autoCreateTable` 解析出 `TableIdentifier` 之后、调用 `catalog.createTable` 之前调用。其设计要点：

1. **能力探测**：先判断 `catalog instanceof SupportsNamespaces`，若 catalog 不支持命名空间操作（例如某些仅支持扁平表名的 Catalog），则直接返回，不做任何处理。这保证了对不支持命名空间的 Catalog 的兼容性，不会因强转而抛 `ClassCastException`。

2. **逐级创建**：`Namespace.levels()` 返回命名空间各级名称数组（如 `["foo1", "foo2", "foo3"]`）。方法用循环从第一级开始，逐级构造 `Namespace.of(Arrays.copyOfRange(levels, 0, index + 1))`，依次调用 `createNamespace`。这种"自顶向下逐级创建"的方式保证每一级在创建时其父级已存在，符合大多数 Catalog 对命名空间层级完整性的要求。

3. **乐观创建 + 异常吞并**：直接调用 `createNamespace` 而非先 `namespaceExists` 再创建，是一种"乐观"策略（try-then-catch）。捕获 `AlreadyExistsException`（命名空间已存在）和 `ForbiddenException`（无权限创建，常见于某些 Catalog 对已存在命名空间重复创建时返回该异常）并忽略。注释明确说明：这样做的目的是"避免双重 `namespaceExists()` 检查"——即省去先检查后创建的两次远程调用，用一次 `createNamespace` 调用同时覆盖"不存在则创建"和"已存在则跳过"两种情况。这在大多数命名空间已存在的常态下能减少一次 RPC。

4. **放置位置**：调用点位于 `autoCreateTable` 中 `TableIdentifier.parse(tableName)` 之后、`catalog.createTable` 之前。这样只在"确实需要自动建表"时才执行命名空间创建，不影响正常的 `loadTable` 路径。

### 行为说明

- 对于支持命名空间的 Catalog，自动建表前会逐级确保命名空间存在；已存在的命名空间不会报错。
- 对于不支持命名空间的 Catalog，行为完全不变。
- 由于只捕获 `AlreadyExistsException` 和 `ForbiddenException`，其他异常（如真正的权限拒绝 `AuthorizationDeniedException`、网络错误等）仍会向上抛出，不会误吞严重错误。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterFactory.java`

**修改目的**：在自动建表前新增命名空间创建逻辑。

**新增 import**：
- `java.util.Arrays`：用于 `copyOfRange` 切分命名空间各级。
- `org.apache.iceberg.catalog.Namespace`：命名空间类型。
- `org.apache.iceberg.catalog.SupportsNamespaces`：命名空间能力接口，用于 instanceof 探测与强转。
- `org.apache.iceberg.exceptions.ForbiddenException`：无权限异常，需吞并。
- `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`：标记新方法供测试访问。

**调用点插入**：在 `autoCreateTable` 方法中，`TableIdentifier identifier = TableIdentifier.parse(tableName);` 之后新增一行：

```java
createNamespaceIfNotExist(catalog, identifier.namespace());
```

**新增静态方法 `createNamespaceIfNotExist`**（标注 `@VisibleForTesting`）：
- 入参：`Catalog catalog`、`Namespace identifierNamespace`。
- 若 catalog 不是 `SupportsNamespaces` 实例，直接 return。
- 取 `levels = identifierNamespace.levels()`，循环 `index` 从 0 到 `levels.length - 1`：
  - 构造 `Namespace namespace = Namespace.of(Arrays.copyOfRange(levels, 0, index + 1))`（取前 index+1 级）。
  - 调用 `((SupportsNamespaces) catalog).createNamespace(namespace)`。
  - 捕获 `AlreadyExistsException | ForbiddenException` 并忽略，注释说明为避免双重 `namespaceExists()` 检查而强制创建。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/IcebergWriterFactoryTest.java`

**修改目的**：验证多级命名空间的逐级创建行为。

**修改要点**：

1. **Mock 增强**：原 `testAutoCreateTable` 中 `Catalog catalog = mock(Catalog.class)` 改为 `mock(Catalog.class, withSettings().extraInterfaces(SupportsNamespaces.class))`，使 mock 同时实现 `SupportsNamespaces` 接口，从而让 `createNamespaceIfNotExist` 的 instanceof 判定为真、`createNamespace` 调用可被验证。

2. **测试用表名改为多级**：原 `factory.autoCreateTable("db.tbl", record)` 改为 `factory.autoCreateTable("foo1.foo2.foo3.bar", record)`，三级命名空间 `foo1.foo2.foo3` + 表名 `bar`，用于验证逐级创建。

3. **断言更新**：
   - 原断言 `identCaptor.getValue()` 等于 `TableIdentifier.of("db", "tbl")` 改为 `TableIdentifier.of(Namespace.of("foo1", "foo2", "foo3"), "bar")`。
   - 新增 `ArgumentCaptor<Namespace> namespaceCaptor`，`verify((SupportsNamespaces) catalog, times(3)).createNamespace(namespaceCaptor.capture())` 验证 `createNamespace` 被调用恰好 3 次。
   - 依次断言三次捕获的 namespace 分别为 `Namespace.of("foo1")`、`Namespace.of("foo1", "foo2")`、`Namespace.of("foo1", "foo2", "foo3")`，确认是自顶向下逐级创建。

## 小结

- **成效**：补齐了 Kafka Connect Sink 自动建表路径上的命名空间缺失缺陷。现在自动建表会先逐级创建目标命名空间（对支持命名空间的 Catalog），使多级命名空间下的自动建表不再因命名空间不存在而失败。采用乐观创建 + 异常吞并策略，避免多余的 `namespaceExists` RPC，常态性能更优。对不支持命名空间的 Catalog 完全向后兼容。
- **影响范围**：仅影响 `kafka-connect` 模块的 `IcebergWriterFactory.autoCreateTable` 路径。仅在"表不存在且开启自动建表"时触发，不影响正常 `loadTable` 路径，也不影响其他模块。对 HiveCatalog（`reconnect` 行为不同的实现不受影响）、RESTCatalog、JdbcCatalog 等支持命名空间的 Catalog 均生效。
- **回迁注意事项**：
  1. 此提交位于 `kafka-connect/kafka-connect/` 模块，回迁到 1.4.x 时需确认 1.4.x 分支存在该模块且 `IcebergWriterFactory` 结构与此提交前状态一致。
  2. `ForbiddenException` 是 Iceberg core 中已有的异常类型，1.4.x 分支应已包含，无需额外引入。
  3. `SupportsNamespaces`、`Namespace` 均为 `org.apache.iceberg.catalog` 包下稳定 API，回迁无依赖问题。
  4. 测试依赖 Mockito 的 `withSettings().extraInterfaces(...)`，需确认 1.4.x 测试环境 Mockito 版本支持。
  5. 该方法用"吞并 `ForbiddenException`"来兼容某些 Catalog 对已存在命名空间重复创建返回 Forbidden 的行为。若 1.4.x 分支使用的某个 Catalog 实现对真正的权限拒绝也抛 `ForbiddenException`，则该异常会被静默吞并——这是设计上的已知取舍，回迁时需知悉。
