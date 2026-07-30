# 提交 1631：Spark 3.5: Procedure to rewrite table path (#11931)

## 提交信息

- **序号**：1631 / 4088
- **哈希**：72a165a815735eb9e2f5fad90e926af80cb1a513
- **短哈希**：72a165a81
- **日期**：2025-01-24（Fri Jan 24 00:52:24 2025 -0800）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.5: Procedure to rewrite table path
- **PR/Issue**：#11931

## 总体目的

Iceberg 的 `RewriteTablePath` 是一个用于"把表的所有文件路径前缀从 source 替换为 target"的动作（典型场景：表迁移到新的存储桶/文件系统）。Core 模块早已提供 `RewriteTablePath` Action 抽象，Spark 3.5 也已有 `RewriteTablePathSparkAction` 实现，但此前用户只能通过 Java/Scala API 调用 `SparkActions.get().rewriteTablePath(table)` 来执行，无法在 Spark SQL 中以 `CALL` 语句直接调用，对 SQL 工作流不友好。

本提交为 Spark 3.5 新增 `system.rewrite_table_path` 存储过程（procedure），把已有的 `RewriteTablePathSparkAction` 暴露为 Spark SQL 的 CALL 接口，用户可通过 SQL 完成表路径重写，并接收 `latest_version` 与 `file_list_location` 两个返回值用于后续追踪。同时把 `RewriteTablePathSparkAction` 中一处"version file 找不到"的异常类型从 `NullPointerException`（由 `checkNotNull` 抛出）改为 `IllegalArgumentException`（由 `checkArgument` 抛出）并把消息中的占位符改成原始文件名，便于上层 procedure 报错时给用户更准确的提示。

## 如何达成设计目的

1. 新增 `RewriteTablePathProcedure`，继承 `BaseProcedure`，定义 6 个参数（3 个必填：`table`/`source_prefix`/`target_prefix`；3 个可选：`start_version`/`end_version`/`staging_location`），输出 schema 为 `(latest_version, file_list_location)`；
2. `call()` 中通过 `ProcedureInput` 解析参数，构造 `RewriteTablePathSparkAction` 并按可选参数链式设置，最终调用 `rewriteLocationPrefix(source, target).execute()`，把结果转为 `InternalRow` 返回；
3. 在 `SparkProcedures` 注册表中注册 `rewrite_table_path` → `RewriteTablePathProcedure::builder`，使 SQL 中 `CALL catalog.system.rewrite_table_path(...)` 可被路由到本 procedure；
4. `RewriteTablePathSparkAction.findVersionFile`（推断）里把 `Preconditions.checkNotNull(versionFile, ...)` 改为 `Preconditions.checkArgument(versionFile != null, "Cannot find provided version file %s in metadata log.", versionFileName)`，统一异常类型与消息；
5. 新增 `TestRewriteTablePathProcedure` 集成测试，覆盖位置参数、命名参数、各种非法输入（缺参、表不存在、version 文件不在 metadata log 中）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteTablePathProcedure.java`（新增，130 行）

**修改目的**：实现 `system.rewrite_table_path` 存储过程。

**工作逻辑**：

- 参数定义：
  ```
  TABLE_PARAM          (required, string)        -- 表标识
  SOURCE_PREFIX_PARAM  (required, string)        -- 原路径前缀
  TARGET_PREFIX_PARAM  (required, string)        -- 目标路径前缀
  START_VERSION_PARAM  (optional, string, null)  -- 起始 metadata version 文件名
  END_VERSION_PARM     (optional, string, null)  -- 结束 metadata version 文件名
  STAGING_LOCATION_PARAM (optional, string, null)-- 暂存目录
  ```
  （注意 `END_VERSION_PARM` 拼写少了一个 A，原文如此，作为字段名后续不修正）
- `OUTPUT_TYPE`：`StructType(latest_version: string, file_list_location: string)`；
- `builder()`：返回 `BaseProcedure.Builder` 子类，由 `SparkProcedures` 注册时使用；
- `call(InternalRow args)`：
  ```
  ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);
  Identifier tableIdent = input.ident(TABLE_PARAM);
  String sourcePrefix = input.asString(SOURCE_PREFIX_PARAM);
  String targetPrefix = input.asString(TARGET_PREFIX_PARAM);
  String startVersion = input.asString(START_VERSION_PARAM, null);
  String endVersion = input.asString(END_VERSION_PARM, null);
  String stagingLocation = input.asString(STAGING_LOCATION_PARAM, null);

  return withIcebergTable(tableIdent, table -> {
    RewriteTablePathSparkAction action = SparkActions.get().rewriteTablePath(table);
    if (startVersion != null) action.startVersion(startVersion);
    if (endVersion != null) action.endVersion(endVersion);
    if (stagingLocation != null) action.stagingLocation(stagingLocation);
    return toOutputRows(action.rewriteLocationPrefix(sourcePrefix, targetPrefix).execute());
  });
  ```
- `toOutputRows(RewriteTablePath.Result)`：把 `result.latestVersion()` 与 `result.fileListLocation()` 包成 `UTF8String` 放进单行 `InternalRow`。
- `withIcebergTable` 是 `BaseProcedure` 提供的模板方法，负责按 identifier 加载 Iceberg `Table` 并在 Spark 上下文中执行。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`（修改，+1）

**修改目的**：注册新 procedure。

**工作逻辑**：在静态 `buildProcedures()` 的 mapBuilder 中追加 `mapBuilder.put("rewrite_table_path", RewriteTablePathProcedure::builder);`，使 `CALL catalog.system.rewrite_table_path(...)` 路由到新 procedure。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java`（修改，+4/-2）

**修改目的**：把"version 文件未找到"的异常从 `checkNotNull` 改为 `checkArgument`，并把消息占位符改为原始文件名 `versionFileName`。

**工作逻辑**：

```
- Preconditions.checkNotNull(
-     versionFile, "Version file %s does not exist in metadata log.", versionFile);
+ Preconditions.checkArgument(
+     versionFile != null,
+     "Cannot find provided version file %s in metadata log.",
+     versionFileName);
```

改造后异常类型为 `IllegalArgumentException`，消息用用户传入的 `versionFileName`（而非可能为 null 的 `versionFile`）填充，便于在 procedure 调用路径上对外报错。原 `checkNotNull` 在 `versionFile==null` 时会抛 `NullPointerException` 且消息中 `%s` 被填充为 `null`，对排查不友好。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java`（新增，174 行）

**修改目的**：覆盖 procedure 的 SQL 调用路径。

**工作逻辑**：

- `testRewriteTablePathWithPositionalArgument`：建表后用位置参数形式 `CALL catalog.system.rewrite_table_path('table', 'sourcePrefix', 'targetPrefix')` 调用，断言返回的 `latest_version` 等于当前 metadata 文件名、`file_list_location` 以表 location 开头并以 `file-list` 结尾，且 file-list 文件行数为 1。
- `testRewriteTablePathWithNamedArgument`：建表后 `INSERT` 一次产生 v1，再用命名参数形式调用并传入 `start_version=v0`、`end_version=v1`、`staging_location`，断言 `latest_version==v1Metadata`、`file_list_location==stagingLocation+"file-list"`、file-list 行数为 4。
- `testProcedureWithInvalidInput`：覆盖多种非法输入：
  - 缺 `source_prefix`/`target_prefix` → `AnalysisException`，消息含 `Missing required parameters: [...]`；
  - 缺 `target_prefix` → 同上；
  - 表不存在 → `RuntimeException` 含 `Couldn't load table`；
  - `start_version=v20.metadata.json`（不存在）→ `IllegalArgumentException` 含 `Cannot find provided version file v20.metadata.json in metadata log.`；
  - `end_version=v11.metadata.json`（不存在）→ 同上。
- 辅助 `checkFileListLocationCount`：用 `spark.read.format("text").load(fileListLocation).count()` 读取 file-list 文本文件行数。

## 小结

- **成效**：补齐了 Spark 3.5 上 `rewrite_table_path` 的 SQL CALL 接口，用户无需写代码即可在 SQL 工作流中迁移表路径；同时修正了 `RewriteTablePathSparkAction` 中"version file 未找到"的异常类型与消息，使其更友好、更易被 procedure 层捕获和展示。
- **影响范围**：纯新增 procedure + 注册项 + 一处异常类型修正，无对现有 Action 行为的语义改动（只是异常类型从 NPE 变为 IAE，调用方若按 `NullPointerException` 捕获需调整）。新增 procedure 不影响其他 procedure。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支需已合入 `RewriteTablePathSparkAction` 与 `RewriteTablePath` Action 抽象，本提交才能在其上叠加 procedure；
  - `BaseProcedure`/`ProcedureInput`/`withIcebergTable` 等 Spark 3.5 procedure 框架基类需在 1.4.x 上等价可用；
  - 异常类型从 NPE 改为 IAE，若 1.4.x 上已有调用方按 NPE 捕获，回迁时需同步调整；
  - 后续提交（如 #11982 / #12111）会修复 broadcasting specs 与回迁到 Spark 3.4，1.4.x 回迁时应一并考虑；
  - 测试依赖 `@TempDir`、`ExtensionsTestBase`、`sql(...)` 辅助，1.4.x 上需确认测试基础设施一致。
