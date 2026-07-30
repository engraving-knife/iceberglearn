# 提交 1109：Flink: backport PR #10832 of inferring parallelism in FLIP-27 source (#11009)

## 提交信息

- **序号**：1109 / 4088
- **哈希**：bf00d51e4b80b428c44e429c26077b99f7212fdd
- **短哈希**：bf00d51e4
- **日期**：2024-08-27（Tue Aug 27 08:33:42 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: backport PR #10832 of inferring parallelism in FLIP-27 source (#11009)
- **PR/Issue**：#11009，回迁源 PR #10832（即提交 1102）
- **影响模块**：flink v1.18 + flink v1.19 source

## 总体目的

提交 1102 把"FLIP-27 source 在批模式下按 split 数推断并行度"的特性引入了 `flink/v1.20`。Iceberg 同时维护 v1.18/v1.19/v1.20 三个 Flink 版本目录，需要把该特性同步到 v1.18 与 v1.19，让旧版本 Flink 用户也能享受自动并行度推断。

本提交把 #10832 的全部改动**机械回迁到 `flink/v1.18` 与 `flink/v1.19` 两个目录**，并对 v1.18 的 API 差异做了适配。

## 如何达成设计目的

回迁策略：把 v1.20 上的 7 个文件改动按相同 diff 应用到 `flink/v1.18/flink/src/...` 与 `flink/v1.19/flink/src/...` 对应路径。两个版本各 7 个文件，共 14 个文件改动。

各文件内部改动逻辑与提交 1102 **基本一致**（详见 1102 的 analysis.md），核心包括：

- `IcebergSource.java`：新增 `volatile List<IcebergSourceSplit> batchSplits` 缓存；`planSplitsForBatch` 改为缓存命中即返回；新增 `shouldInferParallelism()` 与 `inferParallelism(flinkConf, env)`；Builder 新增 `buildStream(StreamExecutionEnvironment)` 方法与 `outputTypeInfo` 静态辅助方法；`createEnumerator` 末尾清空缓存。
- `IcebergTableSource.java`：`createFLIP27Stream` 改用 `IcebergSource.Builder.buildStream(env)`，返回类型由 `DataStreamSource<RowData>` 改为 `DataStream<RowData>`。
- `TestIcebergSourceInferParallelism.java`（新增）：mini cluster + 反射拿 `MiniCluster` 校验 source 顶点实际并行度的端到端测试，3 个用例覆盖空表/少文件/多文件场景。
- `TestIcebergSourceBounded.java`：改用 `buildStream(env)`。
- `TestIcebergSourceBoundedSql.java`：`setBoolean(key, true)` 改为 `set(ConfigOption, true)`。
- `TestIcebergSourceSql.java`：显式关闭 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`，避免干扰 watermark 顺序测试。
- `TestIcebergSpeculativeExecutionSupport.java`：加 `@Timeout(60)`；`TestingMap.map` 改为只让 subtask 0 + attempt 0 睡眠；显式关闭推断并行度。

### v1.18 与 v1.19/v1.20 的 API 差异适配

回迁到 v1.18 时遇到 API 差异，做了适配（v1.19 与 v1.20 的 API 基本一致）：

- **`TestIcebergSpeculativeExecutionSupport.java`**：
  - v1.18 原代码使用 `public static MiniClusterExtension miniClusterResource`（小写字段名），本提交一并重命名为 `public static final MiniClusterExtension MINI_CLUSTER_EXTENSION`（大写 + final，与 v1.19/v1.20 对齐）；v1.19 该字段原本已是大写，无需重命名。
  - v1.18 的 Flink 1.18 API 中 `RuntimeContext` 直接提供 `getAttemptNumber()` 与 `getIndexOfThisSubtask()`，因此 `TestingMap.map` 直接调 `getRuntimeContext().getIndexOfThisSubtask() == 0 && getRuntimeContext().getAttemptNumber() <= 0`；而 v1.19/v1.20 已废弃 RuntimeContext 上的直接方法，需通过 `TaskInfo taskInfo = getRuntimeContext().getTaskInfo();` 间接调用，因此 v1.19 多了一行 `import org.apache.flink.api.common.TaskInfo;` 与 `TaskInfo taskInfo = ...` 局部变量。两版本语义一致：只让 subtask 0 + attempt 0 永久睡眠触发推测执行。

- **`IcebergSource.java`、`IcebergTableSource.java`、`TestIcebergSourceInferParallelism.java`、`TestIcebergSourceBounded.java`、`TestIcebergSourceBoundedSql.java`、`TestIcebergSourceSql.java`**：v1.18 与 v1.19 改动完全一致，且与 v1.20 一致（验证：`diff` 比对显示这三个版本的 `IcebergSource.java` diff 除 commit 元信息外完全相同）。

## 修改详情

下表列出 v1.18 与 v1.19 下的 14 个文件改动（每版本 7 个），与提交 1102 的 7 个文件一一对应。除上述 `TestIcebergSpeculativeExecutionSupport.java` 在 v1.18 有额外字段重命名与 API 适配、v1.19 多一行 `TaskInfo` import 与局部变量外，其余文件的修改目的与工作逻辑**与提交 1102 完全相同**，此处不重复展开。

| 文件 | v1.18 | v1.19 | 改动概要 |
| --- | --- | --- | --- |
| `IcebergSource.java` | √ | √ | 与 1102 一致：volatile 缓存 + `buildStream(env)` + `inferParallelism` |
| `IcebergTableSource.java` | √ | √ | 与 1102 一致：改用 `buildStream(env)` |
| `TestIcebergSourceInferParallelism.java` | 新增 | 新增 | 与 1102 一致：mini cluster + 反射校验 source 并行度测试 |
| `TestIcebergSourceBounded.java` | √ | √ | 与 1102 一致：改用 `buildStream(env)` |
| `TestIcebergSourceBoundedSql.java` | √ | √ | 与 1102 一致：类型安全 setter |
| `TestIcebergSourceSql.java` | √ | √ | 与 1102 一致：显式关闭推断并行度 |
| `TestIcebergSpeculativeExecutionSupport.java` | √（含字段重命名 + API 适配） | √（含 `TaskInfo` 适配） | 与 1102 一致：`@Timeout(60)` + subtask 0 + attempt 0 睡眠 + 关闭推断并行度；v1.18 额外重命名 `miniClusterResource` → `MINI_CLUSTER_EXTENSION` 并加 `final`，用 `RuntimeContext` 直接方法；v1.19 多 `TaskInfo` import 与局部变量 |

## 小结

- **成效**：v1.18 与 v1.19 用户获得与 v1.20 完全对等的 FLIP-27 source 批模式自动并行度推断能力，三个 Flink 版本同步落地。回迁时正确处理了 v1.18 的 RuntimeContext API 差异与字段命名差异。
- **影响范围**：仅 `flink/v1.18` 与 `flink/v1.19` 的 source 子模块，每版本 7 个文件（1 个新增测试 + 6 个修改），共 14 个文件。
- **回迁到 1.4.x 的注意事项**：
  - 与提交 1102 同样，是否回迁取决于 1.4.x 是否包含 FLIP-27 `IcebergSource`。若 1.4.x 对应的 Flink 版本（1.17/1.18）已有 `IcebergSource` 与 `SourceUtil.inferParallelism`、`FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`，可参考本提交的 v1.18 路径回迁（1.4.x 的 Flink 版本若与 v1.18 接近，可直接复用本提交的 v1.18 diff 与 API 适配方案）。
  - 注意 1.4.x 的 Flink 1.17/1.18 API 与 v1.18 目录的 API 是否一致；若 1.4.x 实际基于 Flink 1.17，可能还有进一步 API 差异（如 `MiniClusterExtension` 在 1.17 是否存在、`getIndexOfThisSubtask` API 是否一致）。
  - 回迁需同时拉取 7 个文件改动（含新增的 `TestIcebergSourceInferParallelism.java`），以及配套的 `IcebergTableSource` 与现有测试的修改，确保 SQL/DataStream 入口都生效、现有 watermark 测试不受影响。
  - 公共 API `IcebergSource.Builder.buildStream` 是 `@Experimental`，回迁会引入新公共 API，需在 release notes 中声明。
