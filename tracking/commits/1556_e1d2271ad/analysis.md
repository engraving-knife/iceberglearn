# 提交 1556 e1d2271ad 分析

## 提交信息
- 哈希：e1d2271ad911d4224ad53ac2e0142b28984e5f0b
- 日期：2025-01-07（Tue Jan 7 02:09:16 2025 -0800）
- 作者：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- 消息：Hive: Optimize viewExists API in hive catalog (#11813)

## 总体目的

本提交为 `HiveCatalog` 增加一个专门的 `viewExists(TableIdentifier)` 覆写实现，替换 `ViewCatalog` 接口提供的默认实现，以显著提升"视图是否存在"判断的性能与语义准确性。

Iceberg 的 `ViewCatalog` 接口为 `viewExists` 提供了一个默认实现：

```java
default boolean viewExists(TableIdentifier identifier) {
  try {
    loadView(identifier);
    return true;
  } catch (NoSuchViewException e) {
    return false;
  }
}
```

该默认实现通过 `loadView` 完整加载视图——`BaseMetastoreViewCatalog.loadView` 会构造 `ViewOperations` 并调用 `ops.current()`，后者会读取视图的 metadata JSON 文件（通过底层 FileIO，可能是 HadoopFileIO/S3FileIO），这是一个相对昂贵的操作（涉及远端存储 I/O 与 metadata 解析）。对于"只想知道是否存在"的场景而言，做了远多于必要的活。

而在 Hive 场景下，Hive Metastore（HMS）已经持有每个表/视图对应的 `Table` 对象，`HiveCatalog.tableExists` 早已通过 `client.getTable(database, tableName)` + `validateTableIsIceberg` 这种"轻量级存在性检查"的方式覆写了相同模式。本提交把同样的优化模式应用到 `viewExists`：直接调用 HMS 的 `getTable`，然后用 `validateTableIsIcebergView` 校验它确实是 Iceberg 视图，避免读取 metadata 文件。

附带的好处还包括：能够精确区分"标识符指向的是一个 Iceberg 表 / Hive 表 / Hive view / 不存在对象"，对前三种情况都返回 `false`（因为不是 Iceberg view），而不是像默认实现那样可能因 metadata 文件不存在而抛 `NoSuchViewException`——语义更明确，性能也更可预测。

## 如何达成设计目的

提交修改两个文件：

1. **`hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`**：新增 `viewExists` 覆写方法，整体结构参照同文件中已有的 `tableExists` 实现。
2. **`hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCatalog.java`**：新增 `testHiveViewExists` 测试覆盖各种边界情况；并把私有工具方法 `createHiveView` 重构为更通用的 `createHiveTableWithType`，抽出公共的 `Table` 构造逻辑，新增 `createHiveTable` 复用同一逻辑构造普通 Hive 表。

### 修改详情

#### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：新增高效的 `viewExists` 覆写，避免读取 view metadata 文件。

**工作逻辑**：

```java
@Override
public boolean viewExists(TableIdentifier viewIdentifier) {
  if (!isValidIdentifier(viewIdentifier)) {
    return false;
  }

  String database = viewIdentifier.namespace().level(0);
  String viewName = viewIdentifier.name();
  try {
    Table table = clients.run(client -> client.getTable(database, viewName));
    HiveOperationsBase.validateTableIsIcebergView(table, fullTableName(name, viewIdentifier));
    return true;
  } catch (NoSuchIcebergViewException | NoSuchObjectException e) {
    return false;
  } catch (TException e) {
    throw new RuntimeException("Failed to check view existence of " + viewIdentifier, e);
  } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException(
        "Interrupted in call to check view existence of " + viewIdentifier, e);
  }
}
```

- **入口校验**：`isValidIdentifier` 与 `tableExists` 一致，标识符非法时直接返回 `false`，避免无效请求打到 HMS；
- **HMS 调用**：通过 `clients.run(client -> client.getTable(database, viewName))` 在 HMS 上执行 `getTable`，与 `tableExists` 完全相同。这是单次 HMS RPC，远比读 view metadata 文件轻量；
- **类型校验**：`HiveOperationsBase.validateTableIsIcebergView(table, fullTableName(...))` 校验 HMS `Table` 是否是 Iceberg view。若 HMS 上该名字指向一个 Iceberg 表、Hive 表或 Hive 虚拟视图（非 Iceberg view），此调用会抛出 `NoSuchIcebergViewException`；
- **异常映射**：
  - `NoSuchIcebergViewException`（标识符指向非 Iceberg view 的对象，包括 Iceberg table、Hive table、Hive view）→ 返回 `false`；
  - `NoSuchObjectException`（HMS 上根本不存在该对象）→ 返回 `false`；
  - 其他 `TException` → 包装为 `RuntimeException` 抛出，与 `tableExists` 行为一致；
  - `InterruptedException` → 恢复中断状态并抛 `RuntimeException`，符合 Iceberg 处理中断的统一约定。

新引入 import：`org.apache.iceberg.exceptions.NoSuchIcebergViewException`，对应 catch 子句。

#### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCatalog.java`

**修改目的**：为新的 `viewExists` 覆写添加端到端测试，覆盖各类边界情况；并重构既有辅助方法以避免重复。

**工作逻辑**：

新增测试 `testHiveViewExists`，按以下顺序验证：

1. **非法标识符**：`TableIdentifier.of(dbName, "invalid", viewName)`（多级 namespace，HMS 不支持）→ `viewExists` 应返回 `false`；
2. **创建前**：合法但未创建的标识符 → `false`；
3. **创建后**：调用 `catalog.buildView(...).create()` 后 → `true`；
4. **删除后**：`dropView` 后 → `false`；
5. **存在 Hive 表**：在 HMS 上创建一个 `EXTERNAL_TABLE` 类型的普通 Hive 表，调用 `viewExists` → `false`（因为不是 Iceberg view）；
6. **存在 Hive 虚拟视图**：在 HMS 上创建一个 `VIRTUAL_VIEW` 类型的 Hive 视图（非 Iceberg view），调用 `viewExists` → `false`；
7. **存在 Iceberg 表**：在同名标识符下创建 Iceberg 表，调用 `viewExists` → `false`，但同时调用 `tableExists` → `true`，证明视图与表的存在性互斥判断正确。

测试还配套重构辅助方法：
- 把原 `createHiveView` 重命名为 `createHiveTableWithType`，并接受 `TableType` 参数，把通用 `Table` 构造逻辑抽出；
- 新增 `createHiveTable` 调用 `createHiveTableWithType(..., TableType.EXTERNAL_TABLE)` 用于构造普通 Hive 表；
- 保留 `createHiveView` 调用 `createHiveTableWithType(..., TableType.VIRTUAL_VIEW)` 用于构造 Hive 视图。

这样测试既可构造 Hive 表，也可构造 Hive 视图，且共享同一段 `Table` 字段填充逻辑，减少重复。

## 小结

- **成效**：`HiveCatalog.viewExists` 不再走 `ViewCatalog` 默认实现（会读 view metadata 文件），而是直接查询 HMS 的 `getTable`，性能显著提升；同时语义更精确，能够正确识别"标识符存在但指向 Iceberg 表 / Hive 表 / Hive 视图"的情况并返回 `false`。
- **影响范围**：仅 `hive-metastore` 模块，新增一个覆写方法（+24 行）和一组端到端测试（+83/-16）。无对外 API 变更，调用方感知不到差异（除性能提升与边界语义修正外）。
- **回迁到 1.4.x 的注意事项**：1.4.x 上 `HiveCatalog` 同样有 `viewExists` 走默认实现的问题。**建议回迁**——这是一个明显的性能优化，对 HMS 调用量大、视图数量多的场景收益明显；同时测试一并回迁可覆盖边界语义。回迁风险低，不改变对外接口签名，仅是替换实现。
