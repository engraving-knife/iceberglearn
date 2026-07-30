# 提交 3283：Spark 4.1: Use table IDs in scan equals/hashCode (#15363)

## 提交信息

- **序号**：3283 / 4088
- **哈希**：3f6d3de04961fbf7455e4e8d3ee74ff5922cc122
- **短哈希**：3f6d3de04
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Use table IDs in scan equals/hashCode (#15363)
- **PR/Issue**：#15363

## 总体目的

本提交在四个 Spark 扫描类的 `equals()` 和 `hashCode()` 方法中加入表 UUID（`table().uuid()`）作为判等依据，修复仅凭表名（`table().name()`）判等可能导致误判的隐患。在此之前，`SparkBatchQueryScan`、`SparkChangelogScan`、`SparkCopyOnWriteScan`、`SparkStagedScan` 的 `equals`/`hashCode` 仅使用表名（即表标识符字符串）来区分扫描所属的表。然而在不同 catalog 或不同存储位置下，两张物理上完全不同的 Iceberg 表可能拥有相同的表名（例如 `catalog1.db.t` 与 `catalog2.db.t` 在某些路径解析下 name 可能相同，或同名但不同元数据路径的表）。此时如果两个扫描恰好同表名、同读 schema、同过滤条件，`equals` 会错误返回 true，而实际上它们指向不同表的底层数据。

Spark 在查询计划与缓存复用阶段依赖扫描的 `equals`/`hashCode` 判断是否可复用或合并扫描。若两张不同表被误判为同一扫描，可能导致读取错误表的数据，造成查询结果错误。引入表 UUID（Iceberg 表创建时生成并写入元数据的全局唯一标识）后，可确保只有指向同一物理表的扫描才被判等，从根本上消除这一风险。

## 如何达成设计目的

在四个扫描类的 `equals()` 方法中追加 `Objects.equals(table().uuid(), that.table().uuid())` 条件，在 `hashCode()` 的 `Objects.hash(...)` 参数列表中加入 `table().uuid()`，使表标识同时包含 name 与 uuid，保证判等的唯一性。改动覆盖 v4.1 下的四个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java` (+2/-0 lines)

**修改目的**：在批量查询扫描的判等中加入表 UUID。

**工作逻辑**：
`equals()` 在原有 `table().name().equals(that.table().name())` 之后追加 `&& Objects.equals(table().uuid(), that.table().uuid())`；`hashCode()` 的 `Objects.hash(...)` 参数在 `table().name()` 后插入 `table().uuid()`。其余判等字段（branch、readSchema、filtersDesc 等）不变。这样同名不同表的两张表因 UUID 不同而不会被判为相等。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogScan.java` (+3/-1 lines)

**修改目的**：在 changelog 扫描的判等中加入表 UUID。

**工作逻辑**：
`equals()` 追加 `&& Objects.equals(table.uuid(), that.table.uuid())`；`hashCode()` 由 `Objects.hash(table.name(), readSchema(), filtersDesc(), startSnapshotId, endSnapshotId)` 改为 `Objects.hash(table.name(), table.uuid(), readSchema(), filtersDesc(), startSnapshotId, endSnapshotId)`，在 name 后插入 uuid。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+7/-1 lines)

**修改目的**：在 copy-on-write 扫描的判等中加入表 UUID。

**工作逻辑**：
`equals()` 追加 `&& Objects.equals(table().uuid(), that.table().uuid())`；`hashCode()` 的 `Objects.hash(...)` 参数列表在 `table().name()` 后插入 `table().uuid()`，其余字段（readSchema、filtersDesc、snapshotId、filteredLocations）不变，因参数增多改为多行格式。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScan.java` (+8/-1 lines)

**修改目的**：在 staged 扫描的判等中加入表 UUID。

**工作逻辑**：
`equals()` 追加 `&& Objects.equals(table().uuid(), that.table().uuid())`；`hashCode()` 的 `Objects.hash(...)` 参数列表在 `table().name()` 后插入 `table().uuid()`，其余字段（taskSetId、readSchema、splitSize、splitLookback、openFileCost）不变，同样改为多行格式。

## 总结

本提交在四个 Spark 扫描类的 `equals`/`hashCode` 中加入表 UUID，使扫描判等不再仅依赖可能重复的表名，而是结合 UUID 精确区分物理表，避免了同名不同表扫描被误判相等而导致的查询复用错误，提升了扫描身份识别的可靠性。
