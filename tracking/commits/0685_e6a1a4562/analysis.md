# 提交 0685：修复扫描元数据表时日志记录的表名错误

## 提交信息
- **序号**：0685 / 4088
- **哈希**：e6a1a45624b8bd8246c6ef5601cae046015f4532
- **短哈希**：e6a1a4562
- **日期**：2024-04-15
- **作者**：Manu Zhang
- **提交说明**：Core: Fix logging table name when scanning metadata table (#10141)
- **PR/Issue**：#10141

## 总体目的

本提交修复 `BaseAllMetadataTableScan.planFiles()` 中日志与扫描事件（ScanEvent）记录错误表名的 bug。

`BaseAllMetadataTableScan` 是 Iceberg core 模块中"跨快照元数据表"扫描的抽象基类，被 `AllDataFilesTable`、`AllFilesTable`、`AllDeleteFilesTable`、`AllEntriesTable`、`AllManifestsTable` 等"all_*"系列元数据表继承。这类表会遍历底层表的所有快照来汇总元数据（如所有数据文件、所有 manifest 等）。

在 `planFiles()` 方法中，原代码使用 `table()` 作为日志参数传入 `LOG.info("Scanning metadata table {} ...", table(), ...)`。问题在于：`table()` 返回的是**底层物理表**（即被元数据表包装的真实数据表），而非元数据表本身。这导致日志输出形如：

```
Scanning metadata table mydb.mytable with filter ...
```

而实际上正在扫描的是 `mydb.mytable.all_data_files` 这样的元数据表。日志表名与"metadata table"语义不符，会让运维和排查人员误以为在扫描普通数据表，造成混淆。同样地，`ScanEvent` 中的 `table().name()` 也传入了错误的表名，导致注册到 `Listeners` 的扫描事件也携带了不准确的表名信息。

## 如何达成设计目的

修复策略是在 `planFiles()` 内部构造一个正确的元数据表名，并把它同时用于日志和 ScanEvent。

具体地，作者引入一个局部变量 `metadataTableName`，其构造方式为：

```java
String metadataTableName = table().name() + "." + tableType().name().toLowerCase(Locale.ROOT);
```

其语义是：底层表名 + `.` + 元数据表类型的小写形式。例如底层表 `mydb.mytable`、`tableType()` 返回 `MetadataTableType.ALL_DATA_FILES`，则 `metadataTableName` 为 `mydb.mytable.all_data_files`，这正是 Iceberg 中元数据表的命名约定（`<base_table>.<metadata_table_type>`，与 `BaseMetastoreCatalog.loadMetadataTable` 中按末段解析元数据表类型的逻辑一致）。

随后把日志和 ScanEvent 的表名参数都从 `table()` / `table().name()` 改为 `metadataTableName`，保证日志、事件通知都使用正确的元数据表全名。

关于几个实现细节：

1. **`tableType().name().toLowerCase(Locale.ROOT)`**：`tableType()` 返回 `MetadataTableType` 枚举（如 `ALL_DATA_FILES`、`ALL_MANIFESTS`、`ENTRIES` 等），其 `name()` 是大写形式。必须用 `toLowerCase` 转成小写以匹配元数据表名约定。**显式传 `Locale.ROOT` 是关键**：避免在某些 locale（如 Turkish）下 `I` 被小写为 `ı`（不带点）而非 `i`，从而产生错误表名。这是 Java 国际化的经典坑，使用 `Locale.ROOT` 保证语义稳定。

2. **同时修日志和 ScanEvent**：原代码两处都用 `table()`，必须同步修复，否则日志对了而事件还是错的，仍会误导下游监听器。

3. **构造方式选择**：为什么不直接调 `metadataTableType()` 的字符串表示？因为底层 `table()` 并不直接持有"元数据表名"这一概念（元数据表名由 catalog 层根据 `<base>.<type>` 拼接），scan 层只能拿到底层表和 `tableType()`，所以就地拼字符串是最直接的方式。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseAllMetadataTableScan.java`
**修改目的**：修正 `planFiles()` 中日志与 ScanEvent 的表名，使其反映真实的元数据表名而非底层物理表名。
**工作逻辑**：

- 新增 `import java.util.Locale;`。
- 在 `planFiles()` 开头构造 `metadataTableName`：
  ```java
  String metadataTableName = table().name() + "." + tableType().name().toLowerCase(Locale.ROOT);
  ```
- 把 `LOG.info("Scanning metadata table {} with filter {}.", table(), ...)` 改为传入 `metadataTableName`。
- 把 `Listeners.notifyAll(new ScanEvent(table().name(), 0L, filter(), schema()))` 改为传入 `metadataTableName`。

修复前日志示例：`Scanning metadata table mydb.mytable with filter ...`
修复后日志示例：`Scanning metadata table mydb.mytable.all_data_files with filter ...`

## 小结
- **成效**：成功修复表名日志 bug，使日志和 ScanEvent 都能正确标识正在扫描的元数据表（如 `db.t.all_data_files`），便于运维排查与事件监听。
- **影响范围**：仅影响 `core` 模块的 `BaseAllMetadataTableScan`（被所有 "all_*" 元数据表扫描继承），属日志/事件层面的可观测性修复，不影响扫描结果本身。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支若存在同样的 `table()` 日志写法，应直接 cherry-pick；需确认 1.4.x 的 `BaseAllMetadataTableScan` 中 `tableType()` 方法可用（该方法来自父类 `BaseMetadataTableScan`，是稳定 API）。修复独立、无副作用，回迁风险极低。注意 `Locale.ROOT` 必须保留，不可省略为无参 `toLowerCase()`，否则在某些 locale 下会产生错误表名。
