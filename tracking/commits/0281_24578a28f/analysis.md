# 提交 0281：Core: Fix metadata table uuid to return a consistent UUID for the same reference (#9310)

## 提交信息

- **序号**：0281 / 4088
- **哈希**：24578a28fe69db96da460ac49eeb1a60fee7b8c7
- **短哈希**：24578a28f
- **日期**：2023-12-16 10:48:55 -0800
- **作者**：Ajantha Bhat
- **提交说明**：Core: Fix metadata table uuid to return a consistent UUID for the same reference (#9310)
- **PR/Issue**：#9310

## 总体目的

Iceberg 中的元数据表（metadata table，如 `ManifestsTable`、`FilesTable`、`EntriesTable` 等）是建立在基础数据表之上的虚拟表，用于暴露表的内部元数据信息。每个 `Table` 接口都定义了 `uuid()` 方法，用于返回表的唯一标识符，该标识符在缓存、指标上报、表识别等场景中被广泛使用。

本提交修复的 Bug 是：`BaseMetadataTable.uuid()` 方法在每次调用时都返回 `UUID.randomUUID()`，即每次调用都会生成一个全新的随机 UUID。这意味着同一个元数据表对象在多次调用 `uuid()` 时会得到不同的标识符，违反了 `Table.uuid()` 接口的语义契约——UUID 应当对一个表引用保持稳定。

这种不一致会导致依赖 UUID 进行缓存键控或表识别的下游组件出现错误行为。例如，引擎层可能用 UUID 作为缓存 key，如果每次读取都拿到不同的 UUID，缓存将完全失效；或者在指标系统中，同一张表会被统计成多个不同的表，导致指标混乱。该问题在元数据表被频繁引用的场景下尤其严重。

修复的动机是确保元数据表的 UUID 行为与普通数据表一致：对于同一个表引用，UUID 在其生命周期内保持不变，同时不同表实例之间的 UUID 互不相同。

## 如何达成设计目的

修复方式非常直接：在 `BaseMetadataTable` 的构造函数中生成一次随机 UUID 并存储为 final 字段，`uuid()` 方法改为返回该缓存字段，而非每次重新生成。这样每个元数据表实例在其生命周期内都会返回同一个 UUID，既保证了同一引用的一致性，又保证了不同实例之间的唯一性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java`

**修改目的**：修复元数据表 UUID 每次调用都变化的问题。

**工作逻辑**：
- 新增了 `private final UUID uuid` 字段，与已有的 `table`、`name` 等字段并列。
- 在构造函数 `protected BaseMetadataTable(Table table, String name)` 中增加 `this.uuid = UUID.randomUUID();`，使 UUID 在对象创建时生成一次。
- 将 `uuid()` 方法的返回值从 `UUID.randomUUID()`（每次调用生成新值）改为 `uuid`（返回缓存的字段值）。

由于 `BaseMetadataTable` 是所有元数据表（`ManifestsTable`、`FilesTable`、`DataFilesTable`、`EntriesTable`、`HistoryTable`、`SnapshotsTable` 等）的抽象父类，这一修改对所有元数据表子类均生效。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`

**修改目的**：添加测试验证修复后的 UUID 一致性行为。

**工作逻辑**：
新增 `testMetadataTableUUID()` 测试方法，使用 `ManifestsTable` 进行验证：
- 断言同一元数据表实例多次调用 `uuid()` 返回值相等（`isEqualTo(manifestsTable.uuid())`）。
- 断言元数据表的 UUID 与其底层基础数据表的 UUID 不同（`isNotEqualTo(table.uuid())`），确保元数据表有自己独立的标识，不会与基础表混淆。

## 小结

本提交修复了元数据表 UUID 不一致的 Bug，通过将随机 UUID 的生成从 `uuid()` 方法调用时移到构造函数中，确保同一元数据表引用在其生命周期内返回稳定的 UUID。这是一个小而重要的正确性修复，避免了下游依赖 UUID 的组件（缓存、指标等）因 UUID 漂移而出现异常行为。
