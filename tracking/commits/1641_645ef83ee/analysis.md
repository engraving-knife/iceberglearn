# 提交 1641：Spark 3.4: Backport Spark actions and procedures for RewriteTablePath (#12111)

## 提交信息

- **序号**：1641 / 4088
- **哈希**：645ef83eec0b993dcc79310de343c04689a5c651
- **短哈希**：645ef83ee
- **日期**：2025-01-26（Sun Jan 26 16:43:44 2025 -0800）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.4: Backport Spark actions and procedures for RewriteTablePath
- **PR/Issue**：#12111

注：任务清单中给出的完整哈希 `645ef83eec2b0b993dcc79310de343c04689a5c651` 多了一个 `2`（41 字符），实际仓库中的哈希为 `645ef83eec0b993dcc79310de343c04689a5c651`（40 字符）。本分析以仓库实际哈希为准。

## 总体目的

Iceberg 此前已经把 `RewriteTablePath` 这条能力（用于把表的所有文件路径前缀从 source 替换为 target，典型场景为表迁移到新存储桶）落地到 Spark 3.5 模块：包括 `RewriteTablePathSparkAction`（Java API）、`RewriteTablePathProcedure`（SQL CALL 接口）、`SparkProcedures` 注册项、`SparkActions.rewriteTablePath` 入口、`BaseSparkAction.newStaticTable` 辅助方法，以及对应的两个测试类（`TestRewriteTablePathProcedure` / `TestRewriteTablePathsAction`）。相关修复（如 #11982 修复 broadcasting specs）也已合入 Spark 3.5。

但 Iceberg 同时维护 Spark 3.3 / 3.4 / 3.5 三个版本模块，许多用户仍在 Spark 3.4 上运行。若只在 3.5 上提供 `rewrite_table_path`，3.4 用户无法使用该能力。本提交把整套 `RewriteTablePath` 功能从 Spark 3.5 回迁（backport）到 Spark 3.4 模块，让 3.4 用户也能通过 Java API 与 SQL CALL 调用 `system.rewrite_table_path`。

关键点：本次回迁不是简单复制——由于 Spark 3.4 模块仍使用 JUnit 4 而 Spark 3.5 已迁到 JUnit 5，测试类需要把 `@TestTemplate`/`@TempDir`/`ExtensionsTestBase` 等 JUnit 5 用法改写为 JUnit 4 的 `@Test`/`@Rule TemporaryFolder`/`SparkExtensionsTestBase`。Action 与 Procedure 主体逻辑保持一致，且本次回迁的 `RewriteTablePathSparkAction` 直接采用了 #11982 修复后的版本（broadcast 已修正为 `SerializableTableWithSize` + 单 broadcast），相当于把 3.5 上已修复的最新代码整体搬到 3.4。

## 如何达成设计目的

1. 在 `spark/v3.4/spark/src/main/java/.../actions/` 新增 `RewriteTablePathSparkAction.java`（714 行），从 Spark 3.5 同名文件复制，主体逻辑一致（含 #11982 的 broadcast 修复）；
2. 在 `spark/v3.4/spark/src/main/java/.../procedures/` 新增 `RewriteTablePathProcedure.java`（130 行），与 Spark 3.5 版本几乎逐字一致；
3. `SparkProcedures.java`（3.4）注册 `rewrite_table_path` → `RewriteTablePathProcedure::builder`；
4. `SparkActions.java`（3.4）新增 `rewriteTablePath(Table)` 工厂方法返回 `RewriteTablePathSparkAction`；
5. `BaseSparkAction.java`（3.4）新增 `protected Table newStaticTable(String metadataFileLocation, FileIO io)` 辅助方法（用 `StaticTableOperations` 包一层 `BaseTable`），供 Action 加载历史 metadata 文件；
6. 新增 `TestRewriteTablePathProcedure.java`（3.4，184 行）与 `TestRewriteTablePathsAction.java`（3.4，1080 行），从 3.5 版本回迁并把 JUnit 5 改写为 JUnit 4。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java`（新增，714 行）

**修改目的**：把 `RewriteTablePath` Action 实现回迁到 Spark 3.4。

**工作逻辑**：与 Spark 3.5 版本（#11931 引入 + #11982 修复后）一致，主要包括：

- 继承 `BaseSparkAction<RewriteTablePathSparkAction>`，实现 `RewriteTablePath` 接口；
- 字段：`sourcePrefix`、`targetPrefix`、`startVersion`、`endVersion`、`stagingDir`、`table`、`tableBroadcast`（懒加载缓存）；
- `rewriteLocationPrefix(source, target)` 链式设置前缀并返回 this；
- `execute()` 主流程：找到 start/end version 对应的 metadata 文件，构造 `StaticTableOperations` 加载历史 metadata，扫描该范围内所有 metadata 文件，收集需要重写的 manifest 与 position delete 文件，分别调 `rewriteManifests` / `rewritePositionDeleteFiles` 用 Spark Dataset 并行处理，最后写出 file-list 文件并返回 `RewriteResult(latestVersion, fileListLocation)`；
- `tableBroadcast()` 方法（`@VisibleForTesting`）：懒加载广播 `SerializableTableWithSize.copyOf(table)`，避免重复广播（已含 #11982 修复）；
- `rewriteManifests` / `writeDataManifest` / `writeDeleteManifest` / `rewritePositionDelete` 系列：从广播表 `table.getValue().io()` 取 IO、`table.getValue().specs()` 取 specs，调 `RewriteTablePathUtil.rewriteDataManifest`/`rewriteDeleteManifest`/`rewritePositionDeleteFile` 完成实际路径替换与写出；
- `findVersionFile`：在 metadata log 中查找用户指定的 version 文件名，未找到抛 `IllegalArgumentException("Cannot find provided version file %s in metadata log.", versionFileName)`（已含 #11931 的异常类型修正）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteTablePathProcedure.java`（新增，130 行）

**修改目的**：把 SQL CALL 接口回迁到 Spark 3.4。

**工作逻辑**：与 Spark 3.5 版本逐字一致——定义 6 个参数（3 必填 `table`/`source_prefix`/`target_prefix`，3 可选 `start_version`/`end_version`/`staging_location`），输出 schema `(latest_version, file_list_location)`，`call()` 通过 `ProcedureInput` 解析参数，构造 `RewriteTablePathSparkAction` 链式设置后 `rewriteLocationPrefix(source, target).execute()`，结果转 `InternalRow` 返回。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`（修改，+1）

**修改目的**：注册新 procedure。

**工作逻辑**：`buildProcedures()` 中追加 `mapBuilder.put("rewrite_table_path", RewriteTablePathProcedure::builder);`，使 `CALL catalog.system.rewrite_table_path(...)` 路由到新 procedure。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`（修改，+5）

**修改目的**：暴露 Action 入口。

**工作逻辑**：新增
```
@Override
public RewriteTablePathSparkAction rewriteTablePath(Table table) {
  return new RewriteTablePathSparkAction(spark, table);
}
```
使 `SparkActions.get().rewriteTablePath(table)` 在 3.4 上可用。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java`（修改，+5）

**修改目的**：提供加载历史 metadata 文件的辅助方法。

**工作逻辑**：
```
protected Table newStaticTable(String metadataFileLocation, FileIO io) {
  StaticTableOperations ops = new StaticTableOperations(metadataFileLocation, io);
  return new BaseTable(ops, metadataFileLocation);
}
```
`StaticTableOperations` 用一个固定的 metadata 文件构造 TableOperations，不依赖 catalog，便于 Action 在指定 version 上加载表状态。该方法是 Spark 3.5 已有方法的回迁。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java`（新增，184 行）

**修改目的**：回迁 procedure 集成测试到 Spark 3.4。

**工作逻辑**：与 Spark 3.5 版本测试用例对等，覆盖位置参数、命名参数、非法输入（缺参、表不存在、version 文件不在 metadata log）；差异在于：

- 继承 `SparkExtensionsTestBase`（3.4 的 JUnit 4 基类）而非 `ExtensionsTestBase`（3.5 的 JUnit 5 基类）；
- 用 `@Rule public TemporaryFolder temp` 替代 `@TempDir Path`；
- 用 `@Before`/`@After`/`@Test` 替代 `@BeforeEach`/`@AfterEach`/`@TestTemplate`；
- 临时目录通过 `temp.newFolder(...)` / `temp.getRoot().toURI().toString()` 访问。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java`（新增，1080 行）

**修改目的**：回迁 Action 集成测试到 Spark 3.4。

**工作逻辑**：与 Spark 3.5 版本测试用例对等，覆盖多种重写场景（data files、position delete files、manifests、end version 缺省、staging dir、跨文件系统前缀替换等）、错误输入、`testKryoDeserializeBroadcastValues`（验证 Kryo 反序列化广播表，依赖 `@VisibleForTesting` 的 `tableBroadcast()`）；差异同样在于 JUnit 4 化（`@Test`/`@Rule`/JUnit 4 断言风格等）以及辅助方法 `removeBroadcastValuesFromLocalBlockManager` 一并回迁。

## 小结

- **成效**：把 `RewriteTablePath` Action + `system.rewrite_table_path` procedure 从 Spark 3.5 完整回迁到 Spark 3.4，让 3.4 用户也能通过 Java API 与 SQL CALL 迁移表路径前缀；回迁版本已包含 #11982 的 broadcast 修复，相当于 3.4 直接获得 3.5 上的最新实现。
- **影响范围**：仅 Spark 3.4 模块新增文件 + 两处注册项（`SparkProcedures`/`SparkActions`）+ 一处基类辅助方法。无对现有 3.4 行为的修改，纯功能新增。测试为 JUnit 4 风格以匹配 3.4 模块约定。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若仍维护 Spark 3.4 模块，本提交可直接 cherry-pick；前提是 1.4.x 的 Spark 3.4 模块已有 `BaseProcedure`/`ProcedureInput`/`withIcebergTable`/`SerializableTableWithSize`/`StaticTableOperations`/`RewriteTablePathUtil` 等依赖（这些在 main 上已存在，1.4.x 上需确认）；
  - 由于本提交已含 #11982 修复，1.4.x 回迁时无需再单独回迁 #11982 到 3.4；
  - 测试依赖 JUnit 4 基础设施（`SparkExtensionsTestBase`、`TemporaryFolder`、`@Rule`），1.4.x 上 3.4 模块应仍是 JUnit 4，可直接套用；
  - 若 1.4.x 上的 Spark 3.4 模块版本与 main 上的 3.4 模块有 API 差异（如 `ProcedureInput` API 变化），需手动调整 procedure 实现；
  - 注意任务清单中本提交的完整哈希多了一个字符（41 位），实际操作时用短哈希 `645ef83ee` 或仓库内 40 位完整哈希 `645ef83eec0b993dcc79310de343c04689a5c651`。
