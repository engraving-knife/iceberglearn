# 提交 2692：Flink: Backport add uid-suffix write option to prevent operator UID hash collisions (#14193)

## 提交信息

- **序号**：2692 / 4088
- **哈希**：69b5caaa9776f3dbb8903a6ce358c74485bf8d11
- **短哈希**：69b5caaa9
- **日期**：2025-09-28 12:28:40 +0200
- **作者**：Rodrigo
- **提交说明**：Flink: Backport add uid-suffix write option to prevent operator UID hash collisions (#14193)
- **PR/Issue**：#14193，回自 #14063

## 总体目的

本提交是 PR #14063 的 backport，目标是为 Flink IcebergSink 引入可通过 SQL 提示（hint）/写选项设置的 `uid-suffix` 参数，以避免在同一作业中存在多个写入同一张表的 IcebergSink 时，由于算子 UID 哈希冲突导致的 Flink savepoint 兼容性问题。

在 Flink 中，每个算子的 UID 用于在 savepoint/checkpoint 中标识算子状态。当作业里出现多个写入同一张 Iceberg 表的 sink（例如通过 `STATEMENT SET` 同时执行多条 INSERT 到同一表，或 DAG 中存在多个分支），如果不为每个 sink 显式指定不同的 UID 后缀，Flink 会基于表名等生成相同的算子 UID，导致状态后端无法区分这些算子，savepoint 恢复时会出现状态分配错乱或冲突。

此前的 `uidSuffix` 仅能通过 Builder API 设置，无法通过 SQL 选项（`/*+ OPTIONS('uid-suffix'='...') */`）来设置，导致 SQL 作业用户无法规避该冲突。本次 backport 将 `uid-suffix` 提升为标准的 Flink 写选项（`FlinkWriteOptions`），使其可经 SQL 提示传入。

backport 同时覆盖 `flink/v1.20` 与 `flink/v2.1` 两个版本目录，保证两条维护分支都能获得该修复。

## 如何达成设计目的

主要改动思路是：将原先保存在 Builder 私有字段中的 `uidSuffix` 改为通过 `writeOptions`（写选项 map）传递，并新增一个标准 `ConfigOption` `UID_SUFFIX` 与对应的 `FlinkWriteConf.uidSuffix()` 解析方法。这样：

1. SQL 作业可通过 `/*+ OPTIONS('uid-suffix'='source1') */` 设置该选项；
2. Builder API 的 `uidSuffix(String)` 方法仍保留，但内部改为把值写入 `writeOptions`，从而与 SQL 路径统一；
3. `IcebergSink` 构造时从 `FlinkWriteConf` 读取最终的 `uidSuffix`，保证 Builder 与 SQL 两条入口行为一致。

此外新增测试 `testIcebergSinkDifferentDAG`，通过禁用 sink 复用并使用 `STATEMENT SET` 同时向同一表写入两次（每次指定不同的 `uid-suffix`），验证不会发生算子命名冲突。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (+8/-0 lines)

**修改目的**：新增 `uidSuffix()` 配置解析方法。

**工作逻辑**：使用 `confParser.stringConf()` 读取 `FlinkWriteOptions.UID_SUFFIX` 选项，默认值为空字符串。这与同文件中其他写选项解析方法风格一致，使 `uid-suffix` 可与表属性、SQL 提示等配置来源统一解析。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (+4/-0 lines)

**修改目的**：定义 `UID_SUFFIX` 配置项。

**工作逻辑**：新增 `ConfigOption<String> UID_SUFFIX`，键名 `uid-suffix`，字符串类型，默认值空字符串。注释说明其用于为底层 IcebergSink 指定 uid 后缀。注册为标准 Flink 配置选项后，即可被 SQL 提示识别。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+4/-4 lines)

**修改目的**：将 `uidSuffix` 从 Builder 私有字段迁移为通过 `writeOptions` 与 `FlinkWriteConf` 读取。

**工作逻辑**：
- 移除 Builder 中的 `private String uidSuffix = ""` 字段；
- `Builder.uidSuffix(String)` 改为 `writeOptions.put(FlinkWriteOptions.UID_SUFFIX.key(), newSuffix)`，使 Builder 调用与 SQL 选项走同一条配置通路；
- `build()` 中构造 `IcebergSink` 时，原先传入 `uidSuffix` 字段，改为传入 `flinkWriteConf.uidSuffix()`，确保最终值来自统一的配置解析；
- `append()` 中原先使用 Builder 的 `uidSuffix` 字段计算默认后缀，改为使用 `sink.uidSuffix`（即已构建 sink 实例中的值），保证与构建结果一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java` (+55/-0 lines)

**修改目的**：新增端到端测试，验证多 sink 写同一表时通过 `uid-suffix` 避免冲突。

**工作逻辑**：`testIcebergSinkDifferentDAG`：
- 仅在 V2 sink 下运行（`assumeThat(useV2Sink).isTrue()`）；
- 通过 `table.optimizer.reuse-sink-enabled=false` 禁用 sink 复用，强制创建两个独立 IcebergSink 实例；
- 注册两个临时源表 `sourceTable`、`sourceTable1`，各含 4 行相同数据；
- 使用 `EXECUTE STATEMENT SET` 同时执行两条 INSERT 到同一目标表，分别通过 SQL 提示指定 `uid-suffix` 为 `source1` 与 `source2`；
- 断言目标表最终包含 8 条记录（两组 4 行），证明两个 sink 独立运行未发生冲突。

### `flink/v2.1/flink/...` 同名文件（+71/-4 lines 合计）

**修改目的**：在 Flink v2.1 维护分支上同步应用与 v1.20 完全相同的改动。

**工作逻辑**：与 v1.20 中对应文件改动一致——`FlinkWriteConf` 新增 `uidSuffix()`、`FlinkWriteOptions` 新增 `UID_SUFFIX`、`IcebergSink` 将 `uidSuffix` 迁移至 `writeOptions`/`FlinkWriteConf`、`TestFlinkTableSink` 新增 `testIcebergSinkDifferentDAG` 测试。注意 v2.1 的 `IcebergSink.java` 基线行号略有差异（如起始行 321 vs 320），但改动语义相同。

## 总结

本 backport 把主分支 PR #14063 的 `uid-suffix` 写选项能力同步到 Flink v1.20 与 v2.1 两条维护分支，使 SQL 用户也能通过 `/*+ OPTIONS('uid-suffix'='...') */` 为同一作业中多个写入同一 Iceberg 表的 sink 指定不同的算子 UID 后缀，从而避免 savepoint 恢复时的算子 UID 哈希冲突。改动将 `uidSuffix` 统一收敛到 `FlinkWriteOptions`/`FlinkWriteConf` 配置通路，Builder API 与 SQL 入口行为一致，并配有端到端测试覆盖该场景。
