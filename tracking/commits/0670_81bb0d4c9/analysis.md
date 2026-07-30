# 提交 0670：Core: Add EnvironmentContext to commit summary

## 提交信息

- **序号**：0670 / 4088
- **哈希**：81bb0d4c9bfe0c227d20342be5ebddcc6fafe4a6
- **短哈希**：81bb0d4c9
- **日期**：2024-04-09 23:22:32 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Core: Add EnvironmentContext to commit summary (#9273)
- **PR/Issue**：#9273

## 总体目的

本提交是一个**功能增强类改动**：在 Iceberg 核心模块的快照（Snapshot）提交摘要（commit summary）中，将 `EnvironmentContext` 维护的环境元数据（如引擎名称、引擎版本、Iceberg 版本、应用 ID 等）写入快照 summary，使得每次提交的快照都能记录"是谁、用什么引擎、什么版本"产生的，便于后续的可观测性、审计和问题排查。

### 背景

Iceberg 的快照（Snapshot）对象中包含一个 `summary` 字段，它是一个 `Map<String, String>`，记录了本次提交的统计信息，例如新增/删除的文件数、记录数、数据量等。这些信息由 `SnapshotProducer.summary(TableMetadata previous)` 方法构建。

与此同时，Iceberg 在 `EnvironmentContext` 类中维护了一组全局属性：
- `iceberg-version`：Iceberg 自身的版本号（在类加载时通过 `IcebergBuild.fullVersion()` 自动注入）
- `engine-name`：引擎名称（如 "spark"、"flink" 等）
- `engine-version`：引擎版本（如 Spark 的版本号）
- `app-id`：应用 ID（如 Spark 的 applicationId）

各引擎集成模块在初始化时会通过 `EnvironmentContext.put(...)` 设置这些属性。例如，Spark 在 `SparkCatalog` 构造函数中设置：
```java
EnvironmentContext.put(EnvironmentContext.ENGINE_NAME, "spark");
EnvironmentContext.put(EnvironmentContext.ENGINE_VERSION, sparkSession.sparkContext().version());
EnvironmentContext.put(CatalogProperties.APP_ID, sparkSession.sparkContext().applicationId());
```

然而，在本次提交之前，这些环境信息**并未被写入快照 summary**。这意味着用户无法从快照元数据中得知某个快照是由哪个引擎、哪个版本产生的，给运维排查和审计带来不便。本提交通过在 `summary(TableMetadata previous)` 方法中调用 `builder.putAll(EnvironmentContext.get())`，将所有环境上下文属性合并到快照 summary 中。

## 如何达成设计目的

### 设计思路

设计思路简洁而优雅：在 `SnapshotProducer` 构建快照 summary 的最后阶段（在计算完所有统计总量之后、返回不可变映射之前），将 `EnvironmentContext.get()` 返回的所有环境属性一次性合并到 summary builder 中。

这样做有几个优点：
1. **最小侵入性**：只需在 `SnapshotProducer` 中添加一行代码 `builder.putAll(EnvironmentContext.get());`，不影响现有的 summary 构建逻辑。
2. **自动扩展性**：未来如果在 `EnvironmentContext` 中新增属性（如新的引擎标识），无需修改 `SnapshotProducer`，新属性会自动被包含在 summary 中。
3. **统一性**：所有通过 `SnapshotProducer` 提交的快照（包括 append、overwrite、delete、rewrite 等操作）都会自动包含环境信息，无需在各操作类型中重复添加。

### 工作流程

1. 引擎启动时（如 Spark 的 `SparkCatalog` 初始化），通过 `EnvironmentContext.put(...)` 设置引擎名称、版本、应用 ID 等属性。`iceberg-version` 在 `EnvironmentContext` 类加载时自动注入。
2. 用户执行提交操作（如 `table.newFastAppend().appendFile(FILE_A).commit()`），触发 `SnapshotProducer` 的提交流程。
3. `SnapshotProducer.summary(TableMetadata previous)` 方法被调用，构建快照 summary：
   - 首先获取实现类提供的 summary（`summary()`）
   - 然后基于前一个快照的 summary 计算各种总量（TOTAL_RECORDS、TOTAL_DATA_FILES 等）
   - **新增步骤**：调用 `builder.putAll(EnvironmentContext.get())` 将环境属性合并进来
   - 返回最终的不变映射
4. 快照被写入元数据文件，summary 中现在包含 `iceberg-version`、`engine-name`、`engine-version`、`app-id` 等键。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：在快照 summary 中注入 EnvironmentContext 的环境属性。

**工作逻辑**：

在 `summary(TableMetadata previous)` 方法中，所有 `updateTotal(...)` 调用之后、`return builder.build();` 之前，新增一行：
```java
builder.putAll(EnvironmentContext.get());
```

这一行将 `EnvironmentContext` 中所有键值对（`iceberg-version`、`engine-name`、`engine-version`、`app-id` 等）添加到 summary builder 中。由于 `builder` 是 `ImmutableMap.Builder`，如果出现重复键会抛出异常，但由于环境属性使用的键名（如 `engine-name`）与 summary 统计属性的键名（如 `added-records`）不冲突，因此不会出现问题。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java`

**修改目的**：验证快照 summary 中包含 `iceberg-version` 属性。

**工作逻辑**：

新增测试方法 `testIcebergVersionInSummary()`：
1. 执行 `table.newFastAppend().appendFile(FILE_A).commit()` 创建一个新快照
2. 获取当前快照的 summary
3. 断言 summary 中包含键 `"iceberg-version"`

这个测试验证了最基本的功能：`EnvironmentContext` 在类加载时自动注入的 `iceberg-version` 属性能正确出现在快照 summary 中。由于核心测试不依赖特定引擎，只验证 `iceberg-version`（该属性不需要引擎设置，在类加载时自动注入）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`

**修改目的**：验证通过 Spark 引擎执行 `rewrite_data_files` 存储过程后，快照 summary 中包含引擎相关的环境属性。

**工作逻辑**：

1. 新增 import：`CatalogProperties` 和 `EnvironmentContext`。

2. 新增测试方法 `testRewriteDataFilesSummary()`：
   - 调用 `createTable()` 创建非分区表（`c1 int, c2 string, c3 string`）
   - 调用 `insertData(10)` 插入 10 个文件
   - 执行 SQL `CALL %s.system.rewrite_data_files(table => '%s')` 触发数据文件重写
   - 获取重写后快照的 summary
   - 断言 summary 中：
     - 包含键 `CatalogProperties.APP_ID`（即 `"app-id"`）—— 验证 Spark 设置的应用 ID 被写入
     - `EnvironmentContext.ENGINE_NAME`（即 `"engine-name"`）的值为 `"spark"` —— 验证引擎名称正确
     - `EnvironmentContext.ENGINE_VERSION`（即 `"engine-version"`）的值以 `"3.5"` 开头 —— 验证引擎版本正确（因为这是 Spark 3.5 模块的测试）

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java`

**修改目的**：验证通过 Spark 引擎执行 `rewrite_position_delete_files` 存储过程后，快照 summary 中包含引擎相关的环境属性。

**工作逻辑**：

1. 新增 import：`CatalogProperties` 和 `EnvironmentContext`。

2. 新增测试方法 `testRewriteSummary()`：
   - 调用 `createTable()` 创建表
   - 执行 `DELETE FROM %s WHERE id=1` 产生位置删除文件
   - 执行 SQL `CALL %s.system.rewrite_position_delete_files(table => '%s', options => map('rewrite-all','true'))` 触发位置删除文件重写
   - 获取重写后快照的 summary
   - 断言 summary 中：
     - 包含键 `CatalogProperties.APP_ID`（即 `"app-id"`）
     - `EnvironmentContext.ENGINE_NAME`（即 `"engine-name"`）的值为 `"spark"`
     - `EnvironmentContext.ENGINE_VERSION`（即 `"engine-version"`）的值以 `"3.5"` 开头

该测试复用了已有的 `snapshotSummary()` 私有辅助方法（通过 validation catalog 加载表并获取当前快照的 summary）。

## 小结

- **成效**：成功将 EnvironmentContext 的环境属性（iceberg-version、engine-name、engine-version、app-id 等）注入到快照 summary 中，使得每个快照都能记录产生它的引擎和版本信息。核心改动仅一行代码，配合三个测试（核心层 1 个 + Spark 层 2 个）覆盖了基本功能和 Spark 引擎集成场景。这是一个对可观测性和审计有重要价值的增强。
- **影响范围**：
  - 核心模块 `core`：`SnapshotProducer` 的 summary 构建逻辑（影响所有通过 SnapshotProducer 提交的快照）
  - 测试模块：核心层 `TestSnapshotSummary`、Spark 3.5 层 `TestRewriteDataFilesProcedure` 和 `TestRewritePositionDeleteFilesProcedure`
  - 所有引擎集成（Spark、Flink 等）的快照提交都会自动受影响，summary 中会新增环境属性键
- **回迁到 1.4.x 的注意事项**：
  1. 核心改动只有一行 `builder.putAll(EnvironmentContext.get());`，回迁本身很简单。
  2. 但需确认 1.4.x 分支的 `EnvironmentContext` 类是否已包含 `ENGINE_NAME`、`ENGINE_VERSION` 等常量定义，以及各引擎模块（特别是 Spark 3.x、Flink）是否已在 catalog 初始化时调用 `EnvironmentContext.put(...)` 设置引擎信息。如果 1.4.x 分支的引擎集成代码尚未设置这些属性，则回迁后 summary 中可能只有 `iceberg-version` 而缺少 `engine-name` 等。
  3. 测试文件回迁时需注意 Spark 版本差异：原提交针对 Spark 3.5 模块，1.4.x 分支可能需要同时覆盖 Spark 3.3、3.4 等版本的测试（如有对应模块），且 `ENGINE_VERSION` 断言中的版本前缀需匹配对应 Spark 版本（如 "3.3"、"3.4"）。
  4. 需确认 `ImmutableMap.Builder.putAll()` 在遇到重复键时的行为。`EnvironmentContext` 的属性键（`iceberg-version`、`engine-name` 等）通常不会与现有 summary 键冲突，但如果某些自定义实现也在 summary 中使用了这些键名，可能导致 `IllegalArgumentException`。回迁后建议运行完整的 summary 相关测试以确认无冲突。
