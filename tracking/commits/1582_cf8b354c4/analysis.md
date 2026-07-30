# 提交 1582：Core: Fix loading a table in CachingCatalog with Metadata table name (#11738)

## 提交信息

- **哈希**：cf8b354c43d6768b2dd7f72b40421a2a67487520
- **短哈希**：cf8b354c4
- **日期**：2025-01-14（Tue Jan 14 17:27:48 2025 +0100）
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：Core: Fix loading a table in CachingCatalog with Metadata table name (#11738)
- **PR/Issue**：#11738
- **共同作者**：Manu Zhang <OwenZhang1990@gmail.com>

## 总体目的

CachingCatalog 是 Iceberg 中对底层 Catalog 的缓存包装层，用于缓存已加载的 Table 实例，避免重复加载并支持元数据表（metadata table，如 `snapshots`、`partitions`、`history` 等）与底层原始表共享 TableOperations 实例。元数据表的标识约定是：在原表标识符末尾再追加一个与 `MetadataTableType` 同名的 level，例如 `db.ns.tbl.snapshots`。

本次修复要解决的 bug 是：当一个**普通表**的名字恰好与某个元数据表类型名相同时（例如一个表就叫 `partitions`），CachingCatalog 在加载时会被 `MetadataTableUtils.hasMetadataTableName()` 误判为元数据表，进而尝试按"去掉最后一个 name level 得到原表"的方式去加载一个不存在的原表，最终抛出 `NoSuchTableException`。

举例来说，标识符 `db.ns1.ns2.partitions` 既可能表示 `db.ns1.ns2` 下的元数据表 `partitions`，也可能表示 `db.ns1` 命名空间下名为 `ns2.partitions` 的普通表（最后一段恰好叫 `partitions`）。原实现仅凭名字判断，无法区分这两种情况，导致后一种合法的普通表无法被加载。本提交将"按名字猜测"改为"先加载、再按实际类型判断"，从根本上消除了误判。

## 如何达成设计目的

核心思路是将元数据表的判断时机从"加载之前"推迟到"加载之后"。原代码先用 `MetadataTableUtils.hasMetadataTableName(canonicalized)` 判断名字是否像元数据表，若是则跳过正常加载去取原表；新代码改为先通过 `tableCache.get(canonicalized, catalog::loadTable)` 正常加载该标识符对应的表，再用 `table instanceof BaseMetadataTable` 判断加载结果是否真的是元数据表。只有在确认是元数据表时，才去额外缓存其原表并共享 TableOperations。

这样改的好处是：判断依据从"名字特征"变成了"实际加载出来的对象类型"，不会把名字碰巧相同的普通表误判为元数据表；同时保留了元数据表与原表共享 TableOperations 的优化逻辑。新增了两个测试用例验证修复行为：一个验证加载不存在的表仍抛 `NoSuchTableException`，另一个验证名字为 `partitions` 的普通表能被正常加载且不会被当作元数据表处理。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/CachingCatalog.java`

**修改目的**：修复因仅凭表名判断元数据表而导致的普通表加载失败问题。

**工作逻辑**：在 `loadTable` 方法（加载表的缓存逻辑）中，原流程为：

1. 先尝试从缓存命中，命中则返回。
2. 未命中时，用 `MetadataTableUtils.hasMetadataTableName(canonicalized)` 判断名字是否像元数据表；若是，则计算原表标识符（去掉最后一个 name level），从缓存加载原表并共享其 TableOperations，构造元数据表并缓存。
3. 最后再走 `tableCache.get(canonicalized, catalog::loadTable)` 返回。

问题在于第 2 步对"名字像元数据表但实际是普通表"的情况会错误地进入元数据表分支，尝试加载不存在的原表而抛异常，且即使没抛异常，最后一步的 `tableCache.get` 也无法修正已发生的错误。

新流程改为：

1. 先尝试从缓存命中，命中则返回。
2. 未命中时，**先**通过 `tableCache.get(canonicalized, catalog::loadTable)` 正常加载该标识符的表（缓存 loader 会调用底层 `catalog::loadTable`）。
3. 加载完成后，再用 `table instanceof BaseMetadataTable` 判断返回的表是否真的是元数据表。若是，则计算原表标识符，从缓存加载原表，并在原表是 `HasTableOperations` 时共享其 TableOperations 给元数据表，保证刷新联动。
4. 返回已加载的 `table`（而非再次调用 `tableCache.get`）。

关键差异：判断依据从名字（`hasMetadataTableName`）变为实际对象类型（`instanceof BaseMetadataTable`）；并且最终返回的是第 2 步已加载的 `table`，避免了重复加载。代码注释也相应更新，将原来断行的注释合并为一句更清晰的说明。

#### `core/src/test/java/org/apache/iceberg/hadoop/TestCachingCatalog.java`

**修改目的**：为修复新增回归测试，覆盖"普通表名与元数据表类型名冲突"的场景，以及"加载不存在的表"的基本场景。

**工作逻辑**：

- 新增 import：`org.apache.iceberg.BaseMetadataTable` 和 `org.apache.iceberg.exceptions.NoSuchTableException`。
- `testNonExistingTable`：用 `CachingCatalog.wrap` 包装 hadoop catalog，加载一个不存在的表 `otherDB.otherTbl`，断言抛出 `NoSuchTableException` 且消息为 `Table does not exist: otherDB.otherTbl`。这是对基本错误路径的补充覆盖。
- `testTableWithMetadataTableName`：构造一个普通表，标识符为 `db.ns1.ns2.partitions`（最后一段恰好等于元数据表类型 `partitions`）；并构造一个真正的元数据表标识符 `db.ns1.ns2.partitions.partitions`（在原表后再追加 `partitions`）。先创建并加载普通表，断言其名字为 `hadoop.db.ns1.ns2.partitions` 且缓存中只包含该表、不包含元数据表标识符；清空缓存后加载元数据表标识符，断言返回对象是 `BaseMetadataTable` 实例，名字为 `hadoop.db.ns1.ns2.partitions.partitions`，且缓存中同时包含原表与元数据表两个标识符。该测试精准复现了 bug 场景并验证修复后的正确缓存行为。

## 小结

- **成效**：修复了普通表名与元数据表类型名冲突时 CachingCatalog 抛 `NoSuchTableException` 的 bug，将判断依据从"名字特征"改为"实际对象类型"，逻辑更稳健；同时保留元数据表与原表共享 TableOperations 的优化。
- **影响范围**：仅 `CachingCatalog` 的 `loadTable` 路径及其测试。所有使用 CachingCatalog（即几乎所有启用表缓存的引擎集成）的场景都会受益，尤其是用户碰巧用 `partitions`、`snapshots`、`history` 等名字命名普通表时。
- **回迁到 1.4.x 的价值**：这是一个影响正确性的 bug 修复，且涉及表加载这一核心路径，建议回迁到 1.4.x 维护分支以避免该分支用户踩到同样的问题。
