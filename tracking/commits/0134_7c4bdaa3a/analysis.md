# 提交 0134：Core: De-dup props in JdbcUtil (#8992)

## 提交信息

- **序号**：0134 / 4088
- **哈希**：7c4bdaa3a26f2a8b36f22b1a1257aa11bf136969
- **短哈希**：7c4bdaa3a
- **日期**：2023-11-07 07:27:45 +0100（作者时区显示为 -0800 的 Nov 6）
- **作者**：Thomas
- **提交说明**：Core: De-dup props in JdbcUtil (#8992)
- **PR/Issue**：#8992

## 总体目的

这个提交消除 JDBC catalog 模块中一处重复定义的属性常量。`JdbcUtil` 此前自行定义了 `METADATA_LOCATION = "metadata_location"` 和 `PREVIOUS_METADATA_LOCATION = "previous_metadata_location"` 两个字符串常量，而其父类 [`BaseMetastoreTableOperations`](../../core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java) 已经在更上层定义了语义完全相同的 `METADATA_LOCATION_PROP`（值同为 `"metadata_location"`）和 `PREVIOUS_METADATA_LOCATION_PROP`（值同为 `"previous_metadata_location"`）。

这种重复存在两类风险：其一，未来若某个模块修改其中一处的字面值（例如把 `metadata_location` 改为带前缀的形式），而另一处未同步，会导致 JDBC catalog 的建表 SQL、提交 SQL、读取 SQL 与 `JdbcTableOperations` 实际读写 Map 时使用不同的列名，引发"列不存在"或"读到 null"的运行期错误，且因为两个常量值当前恰好相等而难以在编译期暴露。其二，对于维护者而言，相同语义的两份常量增加了认知负担，违反 DRY 原则。

本次改动统一让 JDBC 模块复用 `BaseMetastoreTableOperations.METADATA_LOCATION_PROP` 和 `PREVIOUS_METADATA_LOCATION_PROP`，删除 `JdbcUtil` 中的两份重复定义，使"元数据位置"这一属性的来源唯一化。

## 如何达成设计目的

设计思路是"删除重复定义、统一引用基类常量"。由于 `JdbcTableOperations` 已经继承自 `BaseMetastoreTableOperations`（[第 92 行的类声明](../../core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java)），可以直接以非限定名 `METADATA_LOCATION_PROP` / `PREVIOUS_METADATA_LOCATION_PROP` 引用父类的 `public static final` 常量；而 `JdbcUtil` 是一个 final 工具类（不继承 `BaseMetstareTableOperations`），需要用 `JdbcTableOperations.METADATA_LOCATION_PROP` 的限定形式引用。改动分两步：第一步删除 `JdbcUtil` 中两个常量定义；第二步把 `JdbcUtil` 和 `JdbcTableOperations` 中所有引用旧常量的位置改为引用父类常量。

## 修改详情

### [`core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`](../../core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java)

**修改目的**：删除 `JdbcUtil.METADATA_LOCATION` 与 `JdbcUtil.PREVIOUS_METADATA_LOCATION` 两个重复常量，将其引用统一改为 `JdbcTableOperations.METADATA_LOCATION_PROP` / `JdbcTableOperations.PREVIOUS_METADATA_LOCATION_PROP`。

**工作逻辑**：

- 在常量声明区删除两行：`static final String METADATA_LOCATION = "metadata_location";` 与 `static final String PREVIOUS_METADATA_LOCATION = "previous_metadata_location";`。
- `DO_COMMIT_SQL` 字符串中，原来用 `+ METADATA_LOCATION + " = ? , " + PREVIOUS_METADATA_LOCATION + " = ? "` 拼 UPDATE 的 SET 子句，以及 `WHERE ... + METADATA_LOCATION + " = ?"` 做乐观锁条件判断，全部改为 `JdbcTableOperations.METADATA_LOCATION_PROP` 与 `JdbcTableOperations.PREVIOUS_METADATA_LOCATION_PROP`。这意味着提交 SQL 的列名仍为 `metadata_location` / `previous_metadata_location`，行为不变，只是常量来源换成了基类。
- `CREATE_CATALOG_TABLE` 建表 SQL 中，两处列定义 `+ METADATA_LOCATION + " VARCHAR(1000),"` 和 `+ PREVIOUS_METADATA_LOCATION + " VARCHAR(1000),"` 同样改为引用基类常量，建出的表结构（列名 `metadata_location`、`previous_metadata_location`，类型 `VARCHAR(1000)`）保持不变。
- `INSERT_TABLE_SQL`（INSERT 语句的列列表）中 `+ METADATA_LOCATION + ", " + PREVIOUS_METADATA_LOCATION + ") "` 也同步改写。

由于两个常量的字面值与基类常量完全一致，这一系列改动对外行为完全等价，不影响既有 JDBC catalog 表的读写兼容性。

### [`core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java`](../../core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java)

**修改目的**：将 `JdbcTableOperations` 内对 `JdbcUtil.METADATA_LOCATION` / `JdbcUtil.PREVIOUS_METADATA_LOCATION` 的引用改为继承自基类的 `METADATA_LOCATION_PROP` / `PREVIOUS_METADATA_LOCATION_PROP`。

**工作逻辑**：

- `doRefresh()` / `doCommit()` 中读取新元数据位置的 `table.get(JdbcUtil.METADATA_LOCATION)` 改为 `table.get(METADATA_LOCATION_PROP)`（非限定名，因继承自 `BaseMetastoreTableOperations`）。
- `validateMetadataLocation(Map, TableMetadata)` 中校验元数据位置时读 `table.get(JdbcUtil.METADATA_LOCATION)` 改为 `table.get(METADATA_LOCATION_PROP)`。
- `fetchTable()` 中从 `ResultSet` 读取列并填入 `table` Map 的两行：`table.put(JdbcUtil.METADATA_LOCATION, rs.getString(JdbcUtil.METADATA_LOCATION))` 与 `table.put(JdbcUtil.PREVIOUS_METADATA_LOCATION, rs.getString(JdbcUtil.PREVIOUS_METADATA_LOCATION))`，统一改为 `table.put(METADATA_LOCATION_PROP, rs.getString(METADATA_LOCATION_PROP))` 与 `table.put(PREVIOUS_METADATA_LOCATION_PROP, rs.getString(PREVIOUS_METADATA_LOCATION_PROP))`。这里 `rs.getString(...)` 的参数是 SQL 列名，由于列名仍是 `metadata_location`/`previous_metadata_location`（与基类常量值一致），JDBC 查询行为不变。

## 小结

通过删除 JDBC 模块中与基类 `BaseMetastoreTableOperations` 重复的 `METADATA_LOCATION` / `PREVIOUS_METADATA_LOCATION` 常量并统一引用基类版本，消除了潜在的字面值漂移风险，让元数据位置属性在 JDBC catalog 内有唯一权威定义。
