# 提交 4007：Flink: Integrate ConvertEqualityDeletes with IcebergSink (#17142)

## 提交信息

- **序号**：4007 / 4088
- **哈希**：f5770491d63d319f2c4d3308ac163415bb2a38ef
- **短哈希**：f5770491d
- **日期**：2026-07-10 15:12:21 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Integrate ConvertEqualityDeletes with IcebergSink (#17142)
- **PR/Issue**：#17142

## 总体目的

本提交将 `ConvertEqualityDeletes` 维护任务集成到 Flink `IcebergSink` 中，使 sink 在写入数据文件和 equality delete 文件后，能够自动将这些 equality delete 转换为 deletion vector（DV），作为 post-commit 维护任务。

之前 `ConvertEqualityDeletes` 作为独立的维护任务存在（见提交 4003 文档），但需要用户单独配置和维护。本次集成让 `IcebergSink` 直接支持 `convertEqualityDeletes()` 配置，sink 的写分支自动作为 staging 分支，转换器读取其上的 equality delete 并转换为 DV 提交回该分支（或配置的 target 分支）。这简化了 upsert/CDC 场景下的部署——equality delete 会被持续清理为更高效的 DV 形式。

要求表格式版本 >= 3（支持 DV）且配置了 equality field columns（upsert 或 CDC）。

## 如何达成设计目的

设计思路：
1. 新增 `ConvertEqualityDeletesConfig` 配置类，定义 target-branch、schedule.commit-count、schedule.interval-second 等配置项。
2. 在 `FlinkWriteOptions`/`FlinkWriteConf` 新增 `CONVERT_EQUALITY_DELETES_ENABLE` 开关。
3. `IcebergSink.Builder` 新增 `convertEqualityDeletes()` / `convertEqualityDeletes(Map config)` 方法。
4. `IcebergSink` 在构建维护任务时，若开关开启且 equalityFieldIds 非空，调用 `addConvertEqualityDeletesTask` 把 sink 写分支作为 staging 分支、把 equality field ids 转为列名，构建 `ConvertEqualityDeletes` 任务加入 maintenanceTasks。
5. `ConvertEqualityDeletes.Builder` 新增 `config(ConvertEqualityDeletesConfig)` 方法，从 config 读取调度参数。
6. 补充文档和测试。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ConvertEqualityDeletesConfig.java` (+90/-0 lines, 新文件)

**修改目的**：定义 ConvertEqualityDeletes 的配置项和解析逻辑。

**工作逻辑**：
- `PREFIX = FlinkMaintenanceConfig.PREFIX + "convert-equality-deletes."`
- 配置项：
  - `TARGET_BRANCH`（string，无默认，null 表示就地转换到 sink 写分支）
  - `SCHEDULE_ON_COMMIT_COUNT`（int，默认 1）
  - `SCHEDULE_ON_INTERVAL_SECOND`（long，无默认）
- 通过 `FlinkConfParser` 从 write options 和 Flink config 解析，`targetBranch()`/`scheduleOnCommitCount()`/`scheduleOnIntervalSecond()` 提供访问方法。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ConvertEqualityDeletes.java` (+17/-0 lines)

**修改目的**：Builder 新增 `config(ConvertEqualityDeletesConfig)` 方法。

**工作逻辑**：
```java
public Builder config(ConvertEqualityDeletesConfig config) {
  scheduleOnCommitCount(config.scheduleOnCommitCount());
  Long intervalSecond = config.scheduleOnIntervalSecond();
  if (intervalSecond != null) {
    scheduleOnInterval(Duration.ofSeconds(intervalSecond));
  }
  return this;
}
```
将 config 的调度参数应用到 Builder。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+57/-0 lines)

**修改目的**：在 sink 中集成 ConvertEqualityDeletes 维护任务。

**工作逻辑**：
- Builder 新增 `convertEqualityDeletes()` 和 `convertEqualityDeletes(Map<String,String> config)`，设置 `FlinkWriteOptions.CONVERT_EQUALITY_DELETES_ENABLE=true` 并可选地 putAll config。
- 在维护任务构建逻辑中：
  ```java
  if (flinkWriteConf.convertEqualityDeletesMode()) {
    addConvertEqualityDeletesTask(flinkWriteConf, flinkMaintenanceConfig, equalityFieldIds);
  }
  ```
- `addConvertEqualityDeletesTask`：
  - 校验 `equalityFieldIds` 非空（否则抛 IllegalStateException）。
  - 从 `flinkMaintenanceConfig.createConvertEqualityDeletesConfig()` 获取配置。
  - targetBranch 默认为 sink 写分支（就地转换）。
  - 把 equalityFieldIds 转为列名列表。
  - 构建 `ConvertEqualityDeletes.builder().stagingBranch(flinkWriteConf.branch()).targetBranch(convertTargetBranch).equalityFieldColumns(...).config(...)` 加入 maintenanceTasks。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (+9/-0 lines)

**修改目的**：新增 `convertEqualityDeletesMode()` 配置解析。

**工作逻辑**：解析 `CONVERT_EQUALITY_DELETES_ENABLE` 选项，默认 false。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (+6/-0 lines)

**修改目的**：定义 `CONVERT_EQUALITY_DELETES_ENABLE` 配置选项。

**工作逻辑**：
```java
public static final ConfigOption<Boolean> CONVERT_EQUALITY_DELETES_ENABLE =
    ConfigOptions.key(ConvertEqualityDeletesConfig.PREFIX + "enabled")
        .booleanType().defaultValue(false);
```

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/FlinkMaintenanceConfig.java` (+4/-0 lines)

**修改目的**：在 maintenance config 中新增创建 `ConvertEqualityDeletesConfig` 的工厂方法。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestConvertEqualityDeletesConfig.java` (+69/-0 lines)

**修改目的**：测试 `ConvertEqualityDeletesConfig` 的解析。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSinkTableMaintenance.java` (+138/-0 lines)

**修改目的**：端到端测试 sink 集成 ConvertEqualityDeletes 的行为。

### `docs/docs/flink-maintenance.md` (+29/-0 lines)

**修改目的**：补充 ConvertEqualityDeletes 与 IcebergSink 集成的文档。

### `docs/docs/flink-writes.md` (+26/-0 lines)

**修改目的**：在 Flink 写入文档中说明 convertEqualityDeletes 选项。

## 总结

本提交将 `ConvertEqualityDeletes` 维护任务集成到 Flink `IcebergSink`，使 upsert/CDC 场景下 sink 写入的 equality delete 能被自动转换为 deletion vector，简化部署并提升读取性能（DV 比 equality delete 更高效）。通过新增配置类、write option、builder 方法和维护任务接入逻辑，提供了完整的开箱即用体验，要求表格式 V3 和 equality field columns。配套补齐了文档和测试。该提交随后在 4009 被 backport。
