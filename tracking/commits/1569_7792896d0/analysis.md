# 提交 1569：Hive: Remove Hive runtime (#11801)

## 提交信息

- **序号**：1569
- **哈希**：7792896d08b624e31ac72855bfe7dd9204ddd5fe
- **短哈希**：7792896d0
- **日期**：2025-01-13（Mon Jan 13 14:02:00 2025 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Hive: Remove Hive runtime (#11801)
- **PR/Issue**：#11801

## 总体目的

Iceberg 长期维护三个与 Hive 集成强相关的模块：
- `hive3/`：Hive 3 专用代码，包括 ObjectInspector、向量化 reader、`OrcSplit`、`VectorizedReadUtils` 等。
- `hive3-orc-bundle/`：用于解决 Hive 3 与新 ORC 依赖冲突的 shaded bundle。
- `hive-runtime/`：聚合 `hive2`/`hive3` 的 mr jar 与依赖的发布产物（含 LICENSE/NOTICE）。

此外 `mr/` 模块（MapReduce）中也包含大量 Hive StorageHandler 相关代码（`HiveIcebergStorageHandler`、`HiveIcebergSerDe`、`HiveIcebergInputFormat`、`HiveIcebergOutputFormat`、各种 ObjectInspector 等），用于让 Hive SQL 直接读写 Iceberg 表。

随着 Hive 集成维护成本上升、社区贡献者减少、Hive 3 自身停止演进，Iceberg 社区决定**停止维护 Hive runtime**（即 Hive StorageHandler 集成路径），但保留 `mr` 模块作为纯 MapReduce/Tez InputFormat/OutputFormat 的实现（不依赖 Hive StorageHandler，仍可用于 MR 作业读写 Iceberg 表）。

本提交执行该清理：删除 `hive3`、`hive3-orc-bundle`、`hive-runtime` 三个模块及 `mr` 模块中所有 Hive StorageHandler 相关代码；把 `mr` 模块从"仅当 hiveVersions 启用时才构建"改为无条件构建；清理 Gradle 配置、CI 工作流、labeler、release 脚本中所有 Hive 相关条目。总计删除约 13000 行代码。

这是一个**面向下游用户的破坏性变更**：通过 Hive StorageHandler 用 HiveSQL 读写 Iceberg 表的能力不再由 Iceberg 项目本身提供（用户可转向 Iceberg 自身的 Spark/Flink/Trino 引擎，或由 Hive 社区自行维护集成）。

## 如何达成设计目的

### 1. 删除整个模块目录

- `hive3/`（约 1900 行源代码 + 1600 行测试）：Hive 3 专用 ObjectInspector、向量化 reader、`OrcSplit`、`VectorizedReadUtils` 等。
- `hive3-orc-bundle/`：Hive 3 ORC bundle（shaded）。
- `hive-runtime/`：聚合 jar 模块，含 LICENSE（510 行）、NOTICE（92 行）、`build.gradle`（92 行）。

### 2. 删除 `mr/` 模块中的 Hive StorageHandler 代码

- `mr/src/main/java/org/apache/iceberg/mr/hive/` 下所有类（`Deserializer`、`HiveIcebergFilterFactory`、`HiveIcebergInputFormat`、`HiveIcebergMetaHook`、`HiveIcebergOutputCommitter`、`HiveIcebergOutputFormat`、`HiveIcebergRecordWriter`、`HiveIcebergSerDe`、`HiveIcebergSplit`、`HiveIcebergStorageHandler`、`TezUtil`）。
- `mr/src/main/java/org/apache/iceberg/mr/hive/objectinspector/` 下所有 ObjectInspector。
- `mr/src/main/java/org/apache/hadoop/hive/ql/exec/vector/VectorizedSupport.java`。
- `mr/src/test/java/org/apache/iceberg/mr/hive/` 下所有测试。

### 3. 改造 `mr` 模块使其独立于 Hive

- `mr/build.gradle`：移除 `hive2-exec`/`hive2-metastore`/`hive2-serde` 的 `compileOnly` 依赖；新增 `parquet.column` 与 `orc-core:nohive` 的 `implementation` 依赖（`mr` 现在自己直接处理 parquet/orc，且要用 `nohive` 变体避免引入 hive-storage-api）。
- `mr/src/main/java/org/apache/iceberg/mr/InputFormatConfig.java`：删除 `InMemoryDataModel` 枚举（`HIVE`/`GENERIC`）与 `useHiveRows()` 方法。
- `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`：
  - 删除对 `HiveIcebergStorageHandler` 的所有调用（`table(conf, ...)`、`checkAndSkipIoConfigSerialization`、`checkAndSetIoConfig`），改为直接 `Catalogs.loadTable(conf)` 与基于 `HadoopConfigurable` 接口的本地实现。
  - 删除基于 `HiveVersion.min(HIVE_3)` + `DynMethods` 反射调用 Hive 向量化 reader 的逻辑。
  - `open()`/`openTask()`/`newParquetIterable()`/`newOrcIterable()`/`newAvroIterable()` 等方法删除 `inMemoryDataModel` 分支，只保留 GENERIC 数据模型路径。
  - 新增内部类 `NonSerializingConfig implements Serializable`：用于"故意不序列化 Configuration"的场景——`get()` 返回 transient conf，反序列化后为 null 时抛 `IllegalStateException`，提示需手动 `setConf`。配合 `checkAndSkipIoConfigSerialization`（写侧，把 `FileIO` 的 conf 序列化器替换为 `new NonSerializingConfig(config)::get`）与 `checkAndSetIoConfig`（读侧，`((HadoopConfigurable) table.io()).setConf(config)`）使用。
- `mr/src/test/java/org/apache/iceberg/mr/TestIcebergInputFormats.java`：移除 Hive 相关测试代码。

### 4. Gradle 配置清理

- `settings.gradle`：把 `include 'mr'` 与 `project(':mr').name = 'iceberg-mr'` 从原本"仅当 hiveVersions 含 2 或 3 时才 include"的分支中提升为无条件 include；删除 `hive3`、`hive3-orc-bundle`、`hive-runtime` 的 include；移除 `hiveVersions` 系统属性解析与校验逻辑；`allModules` 分支不再设置 `hiveVersions`。
- `gradle.properties`：删除 `systemProp.defaultHiveVersions=2` 与 `systemProp.knownHiveVersions=2,3`。
- `gradle/libs.versions.toml`：删除 `hive3` 版本条目与 `hive3-exec`/`hive3-metastore`/`hive3-serde`/`hive3-service` 四个依赖条目。

### 5. CI 工作流清理

- `.github/workflows/hive-ci.yml`：删除整个 `hive3-tests` job；`hive2-tests` job 改为只跑 `:iceberg-mr:check`（去掉 `:iceberg-hive-runtime:check`），不再传 `-DhiveVersions=2`。
- `.github/workflows/java-ci.yml`、`spark-ci.yml`、`flink-ci.yml`、`delta-conversion-ci.yml`、`kafka-connect-ci.yml`、`publish-snapshot.yml`：移除所有 `-DhiveVersions=` 参数。
- `.github/workflows/spark-ci.yml`：触发路径列表移除 `hive3/**`、`hive3-orc-bundle/**`、`hive-runtime/**`。

### 6. 其他

- `.github/labeler.yml`：`HIVE` 标签的 glob 列表删除 `hive3/**`、`hive-runtime/**`、`hive3-orc-bundle/**`，仅保留 `hive-metastore/**`。
- `dev/stage-binaries.sh`：删除 `HIVE_VERSIONS` 变量与 `-DhiveVersions=$HIVE_VERSIONS` 参数。

## 小结

- **成效**：移除 Hive runtime（Hive StorageHandler 集成）相关代码与构建基础设施，约 13000 行删除；保留并独立化 `mr` 模块作为纯 MapReduce/Tez 读写实现。社区维护负担大幅降低，`mr` 模块依赖更清爽（不再 compileOnly 依赖 hive2-exec fat jar）。
- **影响范围**：这是一个面向下游的**破坏性变更**。依赖 Iceberg 提供 Hive StorageHandler（用 HiveSQL 读写 Iceberg 表）的用户需要迁移到 Iceberg 的 Spark/Flink/Trino 引擎，或由 Hive 社区自行维护集成。构建系统、CI、release 流程全面清理 Hive 相关条目。`mr` 模块的公开行为也有变化（`InMemoryDataModel.HIVE` 数据模型不再支持）。
- **回迁到 1.4.x 的注意事项**：这是删功能而非修 bug，1.4.x 作为维护分支**不应回迁**——回迁会破坏 1.4.x 用户既有的 Hive 集成能力。1.4.x 应继续保留 Hive runtime 直至其 EOL。**禁止回迁**。
