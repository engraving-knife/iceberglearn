# 提交 3285：Spark 4.1: Fix IcebergSource doc (#15359)

## 提交信息

- **序号**：3285 / 4088
- **哈希**：de3125afe64fc2b171a52b6e884c72f901e3cba1
- **短哈希**：de3125afe
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Fix IcebergSource doc (#15359)
- **PR/Issue**：#15359

## 总体目的

本提交修正 `IcebergSource` 类的 Javadoc 文档，使其反映当前 `path` 参数的实际解析行为。`IcebergSource` 是 Spark 数据源 V2 中注册名为 `"iceberg"` 的入口类，实现 `DataSourceRegister`、`SupportsCatalogOptions`、`SessionConfigSupport`，用户通过 `spark.read().format("iceberg").load(path)` 读取表时由此类负责解析 `path` 并定位到具体的 Iceberg 表。旧文档以大段文字示例描述了路径解析规则（如 `table = "file:///path/to/table"` 加载 HadoopTable、`table = "tablename"` 在当前 catalog/namespace 解析、`table = "catalog.tablename"` 从指定 catalog 加载等），这些描述已与当前实现脱节——实际解析逻辑已改为按优先级依次判断 rewrite key、表路径（含 `/`）、catalog 标识符三档。过时文档会误导使用者对路径解析优先级的理解，因此需要更新。

## 如何达成设计目的

将类注释重写为简洁的三级优先级有序列表，准确描述 `path` 的解析顺序：rewrite key 优先、其次按表路径（含 `/`）、最后按 Spark 规则作为 catalog 标识符解析。改动仅涉及一个文件的注释。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+7/-11 lines)

**修改目的**：更新类级 Javadoc 以匹配实际路径解析行为。

**工作逻辑**：
旧注释首行 `The IcebergSource loads/writes tables with format "iceberg". It can load paths and tables.` 改为 `Data source for reading and writing Iceberg tables using the "iceberg" format.`，更简洁。旧注释中以纯文本列举的六种 path 解析示例（`file:///path/to/table`、`tablename`、`catalog.tablename`、`namespace.tablename`、`catalog.namespace.tablename`、`namespace1.namespace2.tablename`）及其优先级说明，替换为一个 `<ol>` 有序列表，描述三级解析优先级：
1. **Rewrite key**——若 `path` 是 rewrite key，则从 rewrite catalog 加载表（对应 `RewriteTablePath` 动作的 rewrite catalog 机制）；
2. **Table location**——若 `path` 含 `/`，则按指定路径加载表；
3. **Catalog identifier**——否则按 Spark 规则将 `path` 作为 catalog 标识符解析。

新文档更准确地反映了实现的优先级判断顺序，便于使用者理解不同 path 形式的解析结果。

## 总结

本提交更新了 `IcebergSource` 的类 Javadoc，用简洁的三级优先级有序列表替代过时的路径解析示例，使文档与当前实现（rewrite key → 表路径 → catalog 标识符）保持一致，避免误导使用者。
