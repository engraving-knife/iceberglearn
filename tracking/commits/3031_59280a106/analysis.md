# 提交 3031：Hive: Update view query in HMS when replacing view (#14831)

## 提交信息

- **序号**：3031 / 4088
- **哈希**：59280a106ca5f378edfd47d3576c4d10011e5e80
- **短哈希**：59280a106
- **日期**：2025-12-19
- **作者**：wuya
- **提交说明**：Hive: Update view query in HMS when replacing view (#14831)
- **PR/Issue**：#14831

## 总体目的

本提交修复了 Hive Catalog 中视图（view）替换（replace）时 HMS（Hive Metastore）表属性中 SQL 查询文本未更新的问题。Iceberg 视图的 SQL 查询存储在视图元数据（`ViewMetadata`）的 representation 中，同时也会同步写入 HMS 表的 `viewOriginalText` 和 `viewExpandedText` 字段，以便 Hive 引擎能读取视图定义。

在原实现中，`HiveViewOperations.doCommit()` 在提交视图变更时调用 `HMSTablePropertyHelper.setHmsTableParameters()` 更新 HMS 表属性，但该方法只更新了表参数（parameters，如 schema、snapshot 统计等），并未更新 `viewOriginalText` 和 `viewExpandedText`。这意味着当用户通过 `buildView().withQuery(...).replace()` 替换视图时，即使 Iceberg 视图元数据中的 SQL 查询已更新，HMS 表中记录的视图查询文本仍然是创建时的旧值，导致通过 Hive 引擎或其他读取 HMS 表 `viewOriginalText`/`viewExpandedText` 的工具看到的视图定义与实际不一致。

本提交通过在 `setHmsTableParameters()` 方法中新增 `sqlQuery` 参数，当传入的 SQL 查询非空时同步更新 `tbl.setViewExpandedText(sqlQuery)` 和 `tbl.setViewOriginalText(sqlQuery)`，并在 `HiveViewOperations.doCommit()` 调用处传入 `sqlFor(metadata)`（从视图元数据中提取 SQL 查询的方法），从而确保替换视图时 HMS 中的查询文本也同步更新。

## 如何达成设计目的

改动涉及两个主文件和一个测试文件。在 `HMSTablePropertyHelper.java` 中为 `setHmsTableParameters()` 方法新增 `sqlQuery` 参数并在非空时设置 HMS 表的视图文本字段；在 `HiveViewOperations.java` 中调用处传入 `sqlFor(metadata)`（该方法已存在，优先返回 "hive" 方言的 SQL，否则返回首个 SQL representation）；在 `TestHiveViewCommits.java` 中新增测试验证创建和替换视图后 HMS 表中文本的一致性。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HMSTablePropertyHelper.java` (+7/-1 lines)

**修改目的**：在设置 HMS 表属性时同步更新视图查询文本。

**工作逻辑**：
`setHmsTableParameters()` 方法签名新增参数 `String sqlQuery`。在方法末尾（`tbl.setParameters(parameters)` 之后）新增条件块：`if (sqlQuery != null) { tbl.setViewExpandedText(sqlQuery); tbl.setViewOriginalText(sqlQuery); }`。仅当 `sqlQuery` 非空时才更新，避免在非视图场景（如表操作）中误设视图文本字段。`sqlQuery` 为 null 时保持原有行为不变。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveViewOperations.java` (+2/-1 lines)

**修改目的**：在提交视图变更时传入 SQL 查询文本。

**工作逻辑**：
在 `doCommit()` 方法中调用 `setHmsTableParameters()` 的地方，新增参数 `sqlFor(metadata)`。`sqlFor(ViewMetadata)` 是 `HiveViewOperations` 中已有的私有方法，它遍历当前视图版本的 representations，优先返回 dialect 为 "hive" 的 `SQLViewRepresentation.sql()`，否则返回第一个 SQL representation 的 sql，若没有则返回 null。由于该方法已存在，此处仅是将其结果传递给属性设置方法。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveViewCommits.java` (+33/-1 lines)

**修改目的**：验证视图替换后 HMS 表查询文本的同步更新。

**工作逻辑**：
1. 新增常量 `VIEW_QUERY = "select * from ns.tbl"`，并将原有建视图中 `.withQuery("hive", "select * from ns.tbl")` 改为使用该常量，便于断言引用。
2. 新增 import：`org.apache.hadoop.hive.metastore.api.Table` 和 `org.apache.iceberg.view.ImmutableSQLViewRepresentation`。
3. 新增测试 `testViewQueryIsUpdatedOnCommit()`：首先验证建表后视图元数据的 representation 与 HMS 表的 `getViewOriginalText()`/`getViewExpandedText()` 均等于 `VIEW_QUERY`；然后通过 `buildView().withQuery("hive", newQuery).replace()` 替换视图（`newQuery = "select * from ns.tbl2 limit 10"`），再验证替换后视图元数据 representation 和 HMS 表的两个文本字段均更新为 `newQuery`。该测试通过 `ops.loadHmsTable()` 直接从 HMS 加载表对象进行断言，确保端到端验证。

## 总结

本提交修复了 Hive Catalog 视图替换时 HMS 表 `viewOriginalText`/`viewExpandedText` 未同步更新的缺陷，通过在属性设置方法中传入并设置 SQL 查询文本，确保 Iceberg 视图元数据与 HMS 表定义的一致性，并新增了完整的端到端测试覆盖。
