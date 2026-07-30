# 提交 3002：Flink: Backport: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812) (#14819)

## 提交信息

- **序号**：3002 / 4088
- **哈希**：e90b06cc5f07b948a56021d129f128dd8b81123c
- **短哈希**：e90b06cc5
- **日期**：2025-12-11 18:41:14 -0800
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812) (#14819)
- **PR/Issue**：#14819（backport PR），源 PR #14812

## 总体目的

这是一个 backport 提交，把 PR #14812（提交 `11188387f`，作者 Maximilian Michels，2025-12-10）在 Flink v2.1 上修复的"动态 Sink 处理 `NoSuchNamespaceException`"问题，干净地回移到 Flink v1.20 和 v2.0 两个老版本分支。Iceberg 维护多个 Flink 版本（v1.20、v2.0、v2.1）的并行源码目录，每个版本都有自己的 `flink/<version>/flink/src/.../sink/dynamic/` 副本，因此 bug 修复需要分别落到每个版本目录。

源 PR #14812 解决的问题：Flink 动态 sink 的 `TableMetadataCache.refreshTable(TableIdentifier)` 在调用 `catalog.loadTable(identifier)` 失败时，原先只捕获了 `NoSuchTableException`。但当目标命名空间（namespace）不存在时，许多 catalog 实现（例如 REST Catalog、Hive Catalog 等）会抛出 `NoSuchNamespaceException` 而不是 `NoSuchTableException`——因为命名空间都不存在，更谈不上表。原代码没有捕获这个异常，会导致：

1. `refreshTable` 把 `NoSuchNamespaceException` 直接向上抛出，sink 任务在运行时崩溃；
2. 缓存里不会写入"表不存在"的标记，下一次访问同一 identifier 还会再次访问 catalog 并再次抛异常，无法利用缓存短路；
3. 动态 sink 的"按需建表/按需建命名空间"路径（`TableUpdater`）依赖 `refreshTable` 返回 `NOT_FOUND` 信号来触发后续的命名空间与表创建——异常向上抛会让这条路径走不通。

源 PR 的修复方式：把 `catch (NoSuchTableException e)` 改成 `catch (NoSuchTableException | NoSuchNamespaceException e)`，统一按"表不存在"处理，写入缓存标记并返回 `Tuple2.of(false, e)`，让上层 `TableUpdater` 能据此走"创建命名空间 + 创建表"的流程。本 backport 提交把同样的修复一字不差地应用到 `flink/v1.20` 和 `flink/v2.0` 两个版本目录，并附上对应测试。

## 如何达成设计目的

整体思路是"clean backport"——源 PR 的 diff 原样复制到 v1.20 与 v2.0 两个目录。改动分两类文件：

1. 生产代码：`TableMetadataCache.java` 在两个版本目录各改一处——`refreshTable` 的 catch 子句从 `NoSuchTableException` 扩展为 `NoSuchTableException | NoSuchNamespaceException`，并调整日志文案与 import。
2. 测试代码：`TestTableMetadataCache.java` 和 `TestTableUpdater.java` 在两个版本目录各新增 2 个测试，分别覆盖"命名空间不存在"和"表不存在"两种情况下的缓存行为，以及 `TableUpdater` 在命名空间不存在/已存在时的建表流程。

两个版本目录的改动完全对称（diff 内容一致），符合 Iceberg 多 Flink 版本维护的常规做法。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+3/-2 lines)

**修改目的**：让 `refreshTable` 在命名空间不存在时也按"表不存在"处理，而不是让 `NoSuchNamespaceException` 向上抛。

**工作逻辑**：
新增 import `org.apache.iceberg.exceptions.NoSuchNamespaceException`。`refreshTable` 的 catch 子句改为多异常捕获：

```java
} catch (NoSuchTableException | NoSuchNamespaceException e) {
  LOG.debug("Table or namespace doesn't exist {}", identifier, e);
  tableCache.put(
      identifier, new CacheItem(cacheRefreshClock.millis(), false, null, null, null, 1));
  return Tuple2.of(false, e);
}
```

这样无论 catalog 是因为表不存在还是命名空间不存在而抛异常，都会：1) 在 DEBUG 级别记录一条带 identifier 的日志；2) 向缓存写入一个 `tableExists=false` 的 `CacheItem`（`new CacheItem(clock.millis(), false, null, null, null, 1)`），让后续访问能在缓存有效期内短路返回 `NOT_EXISTS`，避免重复打 catalog；3) 返回 `Tuple2.of(false, e)` 把"不存在"信号传给调用方（如 `exists()`、`schema()` 等），最终驱动 `TableUpdater` 走建表流程。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+24/-0 lines)

**修改目的**：验证 `TableMetadataCache.schema(...)` 在命名空间不存在和表不存在时都返回 `NOT_FOUND` 并写入缓存。

**工作逻辑**：
新增两个测试：

- `testNoSuchNamespaceExceptionHandling`：用 `TableIdentifier.of("nonexistent_namespace", "myTable")`（命名空间根本不存在）调用 `cache.schema(tableIdentifier, SCHEMA, false)`，断言返回 `TableMetadataCache.NOT_FOUND`，并断言 `cache.getInternalCache().get(tableIdentifier)` 非空——即缓存里写入了"不存在"标记，下次访问可短路。
- `testNoSuchTableExceptionHandling`：用 `TableIdentifier.parse("default.nonexistent_table")`（命名空间 `default` 存在但表不存在）调用同样方法，断言同样结果。

两个测试合在一起覆盖了"`NoSuchTableException` 与 `NoSuchNamespaceException` 两条路径都返回 `NOT_FOUND` 并缓存"的行为，正是本 backport 的核心保证。`new TableMetadataCache(catalog, 10, Long.MAX_VALUE, 10)` 把 `refreshMs` 设为 `Long.MAX_VALUE`，确保测试期间不会因超时触发刷新干扰断言。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+48/-1 lines)

**修改目的**：验证 `TableUpdater` 在命名空间不存在时会自动创建命名空间再建表，在命名空间已存在时直接建表。

**工作逻辑**：
新增 import `org.apache.iceberg.catalog.SupportsNamespaces`，新增两个测试：

- `testNamespaceAndTableCreation`：先断言 `new_namespace` 不存在、表不存在；调用 `tableUpdater.update(tableIdentifier, "main", SCHEMA, PartitionSpec.unpartitioned(), TableCreator.DEFAULT)`；断言 `new_namespace` 已被创建、表已存在、返回的 `resolvedTableSchema` 与输入 `SCHEMA` 一致、`compareResult` 为 `SAME`。这条路径覆盖了"命名空间不存在 → `refreshTable` 抛 `NoSuchNamespaceException` → 返回 `NOT_FOUND` → `TableUpdater` 创建命名空间 → 创建表"的完整流程，正是源 PR 修复要打通的场景。
- `testTableCreationWithExistingNamespace`：先 `createNamespace(Namespace.of("existing_namespace"))`，再调用 `tableUpdater.update(...)`；断言命名空间仍存在、表被创建、schema 一致、`compareResult` 为 `SAME`。这条路径覆盖"命名空间已存在但表不存在 → `refreshTable` 抛 `NoSuchTableException` → 返回 `NOT_FOUND` → `TableUpdater` 跳过建命名空间直接建表"，保证原 `NoSuchTableException` 路径不受影响。

两个测试共同验证了 catch 子句扩展后，"建命名空间 + 建表"与"只建表"两条路径都能正常工作。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (+3/-2 lines)

**修改目的**：同 v1.20，对 Flink v2.0 版本目录做相同修复。

**工作逻辑**：与 v1.20 完全相同——新增 `NoSuchNamespaceException` import，`refreshTable` catch 子句扩展为 `NoSuchTableException | NoSuchNamespaceException`，日志文案改为 `"Table or namespace doesn't exist {}"`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableMetadataCache.java` (+24/-0 lines)

**修改目的**：同 v1.20，为 v2.0 增加相同的两个缓存测试。

**工作逻辑**：与 v1.20 中的 `testNoSuchNamespaceExceptionHandling` 和 `testNoSuchTableExceptionHandling` 完全一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+48/-1 lines)

**修改目的**：同 v1.20，为 v2.0 增加相同的两个 `TableUpdater` 测试。

**工作逻辑**：与 v1.20 中的 `testNamespaceAndTableCreation` 和 `testTableCreationWithExistingNamespace` 完全一致。

## 总结

该提交是 PR #14812 的 clean backport，把"Flink 动态 Sink 的 `TableMetadataCache.refreshTable` 在命名空间不存在时正确捕获 `NoSuchNamespaceException` 而不是向上抛"这一修复同步到 Flink v1.20 和 v2.0 两个版本目录。修复让动态 sink 在目标命名空间不存在时不再崩溃，并打通了 `TableUpdater` 自动创建命名空间 + 建表的流程；同时附带覆盖命名空间不存在/表不存在/命名空间已存在三种场景的测试。两个版本目录的改动与源 PR 完全对称，是 Iceberg 多 Flink 版本并行维护的标准回移操作。
