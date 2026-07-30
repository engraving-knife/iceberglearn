# 提交 1343：Core: Adapt commit, scan, and snapshot stats for DVs (#11464)

## 提交信息

- **序号**：1343 / 4088
- **哈希**：549674b3fc0cdb18d6cad3e2d6320236fba8c562
- **短哈希**：549674b3f
- **日期**：2024-11-05（Tue Nov 5 17:02:00 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Adapt commit, scan, and snapshot stats for DVs (#11464)
- **PR/Issue**：#11464

## 总体目的

提交 #11467 让 `DeleteFileIndex` 能够识别并索引 DV，但 Iceberg 的统计体系（snapshot summary、commit metrics、scan metrics）此前只把 DV 当成普通位置删除文件来统计：DV 会被计入 `added-position-delete-files`/`removed-position-delete-files` 等位置删除相关字段，无法体现 DV 的存在。

本提交把 DV 与传统位置删除区分开，新增独立的统计字段：
- Snapshot 摘要：`added-dvs`、`removed-dvs`（与原有 `added-position-delete-files`、`removed-position-delete-files` 并列但不重叠）。
- Commit metrics：`added-dvs`、`removed-dvs` 计数器。
- Scan metrics：`dvs` 计数器，统计扫描时索引到的 DV 数量。

这样运维、监控与下游消费者可以单独观测 DV 的产出/清理速度，以及扫描时 DV 的应用规模，为后续评估 DV 迁移效果、调优 Puffin 读取提供数据支撑。

## 如何达成设计目的

- **统一识别 DV**：所有统计入口复用上提交（#11467）的 `ContentFileUtil.isDV(deleteFile)`，按 `format == PUFFIN` 判定。
- **Snapshot 摘要层**：在 `SnapshotSummary` 的 `Changes` 内部类中新增 `addedDVs`/`removedDVs` 计数；在 `addFile`/removeFile 处理 `POSITION_DELETES` 分支时按 DV 与否分别累加到 DV 或位置删除字段，**保持 `addedDeleteFiles`/`removedDeleteFiles`/`addedPosDeletes` 等总数不变**（DV 也算删除文件、也记录删除行数），保证向后兼容的总量统计。
- **Commit metrics 层**：`CommitMetricsResult` 新增 `addedDVs()`/`removedDVs()` 两个 `CounterResult` 字段（默认 null）；`fromSnapshotSummary` 时从新的 snapshot 属性映射；`CommitMetricsResultParser` 同步补 JSON 序列化/反序列化分支。
- **Scan metrics 层**：`ScanMetrics` 新增 `DVS = "dvs"` 计数键与 `dvs()` 方法；`ScanMetricsResult` 与 `ScanMetricsResultParser` 同步暴露并序列化；`ScanMetricsUtil.indexedDeleteFile(...)` 中按 `isDV` 分别自增 `dvs()` 或 `positionalDeleteFiles()`，`indexedDeleteFiles()` 仍统一自增。
- **reset/merge 一致性**：`SnapshotSummary.Changes` 的 `reset()` 与 `merge()` 都同步处理 `addedDVs`/`removedDVs`，避免分批统计时遗漏。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotSummary.java`

**修改目的**：在快照摘要中新增 DV 计数属性。

**工作逻辑**：
- 新增常量 `ADDED_DVS_PROP = "added-dvs"` 与 `REMOVED_DVS_PROP = "removed-dvs"`。
- `Changes` 内部类新增 `int addedDVs`/`removedDVs` 字段。
- `build()` 时通过 `setIf(addedDVs > 0, ...)`、`setIf(removedDVs > 0, ...)` 输出，沿用"为 0 不写"的旧规则。
- `addFile(ManifestEntry, ContentFile)` 的 `POSITION_DELETES` 分支改为：
  ```java
  DeleteFile deleteFile = (DeleteFile) file;
  if (ContentFileUtil.isDV(deleteFile)) {
    this.addedDVs += 1;
  } else {
    this.addedPosDeleteFiles += 1;
  }
  this.addedDeleteFiles += 1;
  this.addedPosDeletes += file.recordCount();
  ```
  注意：`addedPosDeletes`（删除行数）与 `addedDeleteFiles`（删除文件总数）仍照常累加，DV 同时计入总数与细分项。
- `removeFile(...)` 同理：DV 累加到 `removedDVs`，传统位置删除累加到 `removedPosDeleteFiles`，`removedDeleteFiles`/`removedPosDeletes` 仍累加。
- `reset()` 将两个新计数器归零；`merge(Changes other)` 累加 other 的两个计数器。

### `core/src/main/java/org/apache/iceberg/metrics/CommitMetricsResult.java`

**修改目的**：在 commit 指标结果接口中暴露 DV 计数。

**工作逻辑**：
- 新增常量 `ADDED_DVS = "added-dvs"`、`REMOVED_DVS = "removed-dvs"`。
- 新增两个 `@Nullable @Value.Default` 方法 `addedDVs()`/`removedDVs()`，默认返回 null（与现有 `addedPositionalDeleteFiles()` 等风格一致，保持向后兼容）。
- `fromSnapshotSummary(...)` 中调用 `counterFrom(snapshotSummary, SnapshotSummary.ADDED_DVS_PROP)` 与 `REMOVED_DVS_PROP` 填充。

### `core/src/main/java/org/apache/iceberg/metrics/CommitMetricsResultParser.java`

**修改目的**：序列化/反序列化 DV 计数。

**工作逻辑**：
- `toJson(...)` 中新增两段：当 `metrics.addedDVs()` 不为 null 时写入字段 `added-dvs`；`removedDVs()` 同理。
- `fromJson(...)` 中新增 `.addedDVs(CounterResultParser.fromJson(ADDED_DVS, json))`、`.removedDVs(...)` 调用。

### `core/src/main/java/org/apache/iceberg/metrics/ScanMetrics.java`

**修改目的**：在扫描指标中新增 DV 计数器。

**工作逻辑**：
- 新增常量 `DVS = "dvs"`。
- 新增 `@Value.Derived public Counter dvs()`，返回 `metricsContext().counter(DVS)`。
- 与现有 `positionalDeleteFiles()`/`equalityDeleteFiles()`/`indexedDeleteFiles()` 并列。

### `core/src/main/java/org/apache/iceberg/metrics/ScanMetricsResult.java`

**修改目的**：在扫描指标结果中暴露 DV 计数。

**工作逻辑**：
- 新增 `@Nullable @Value.Default default CounterResult dvs()`，默认 null。
- `fromScanMetrics(...)` 中通过 `.dvs(CounterResult.fromCounter(scanMetrics.dvs()))` 填充。

### `core/src/main/java/org/apache/iceberg/metrics/ScanMetricsResultParser.java`

**修改目的**：序列化/反序列化扫描指标中的 DV 计数。

**工作逻辑**：
- `toJson(...)` 在 `positionalDeleteFiles` 之后新增：当 `metrics.dvs()` 不为 null 时写入字段 `dvs`。
- `fromJson(...)` 中新增 `.dvs(CounterResultParser.fromJson(ScanMetrics.DVS, json))`。

### `core/src/main/java/org/apache/iceberg/metrics/ScanMetricsUtil.java`

**修改目的**：在扫描阶段对索引到的删除文件按 DV/位置删除分别计数。

**工作逻辑**：
- `indexedDeleteFile(ScanMetrics, DeleteFile)` 中：
  ```java
  metrics.indexedDeleteFiles().increment();
  if (deleteFile.content() == FileContent.POSITION_DELETES) {
    if (ContentFileUtil.isDV(deleteFile)) {
      metrics.dvs().increment();
    } else {
      metrics.positionalDeleteFiles().increment();
    }
  } else if (deleteFile.content() == FileContent.EQUALITY_DELETES) {
    metrics.equalityDeleteFiles().increment();
  }
  ```
  `indexedDeleteFiles` 仍按总文件数自增，确保向后兼容。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java`

**修改目的**：验证快照摘要中 DV 计数正确。

**工作逻辑**：
- `testFileSizeSummaryWithDVs`：v3 表上先 commit 两个 DV（dv1 对 FILE_A、dv2 对 FILE_B），断言 summary 中 `added-dvs=1`、`added-position-delete-files` 不存在、`added-delete-files=1`、`total-delete-files=2`、`added-pos-deletes` 与 `total-pos-deletes` 仍包含两个 DV 的 recordCount 总和、`added-file-size` 与 `total-file-size` 包含 contentSizeInBytes 总和。
- 然后第二次 commit：移除 dv1/dv2，新增 dv3。断言 `added-dvs=1`、`removed-dvs=2`、`removed-delete-files=2`、`added-pos-deletes`/`removed-pos-deletes` 仍正确、`total-delete-files=1`。

### `core/src/test/java/org/apache/iceberg/metrics/TestCommitMetricsResultParser.java`

**修改目的**：验证 commit metrics JSON 中 DV 字段往返正确。

**工作逻辑**：在原测试中向 snapshot summary 注入 `added-dvs=1`、`removed-dvs=4`，断言 `result.addedDVs().value() == 1L`、`result.removedDVs().value() == 4L`；并在期望的 JSON 输出中加入对应字段块。

### `core/src/test/java/org/apache/iceberg/metrics/TestScanMetricsResultParser.java`

**修改目的**：验证 scan metrics JSON 中 `dvs` 字段往返正确。

**工作逻辑**：在原 round-trip 测试中调用 `scanMetrics.dvs().increment()`（值 1）和 `scanMetrics.dvs().increment(3L)`（值 3）两组用例，断言 JSON 中包含 `\"dvs\":{\"unit\":\"count\",\"value\":...}`。

### `core/src/test/java/org/apache/iceberg/metrics/TestScanReportParser.java`

**修改目的**：验证 scan report 中 `dvs` 字段往返正确。

**工作逻辑**：在原 round-trip 测试中调用 `scanMetrics.dvs().increment()`，并补充期望 JSON 中 `\"dvs\":{\"unit\":\"count\",\"value\":1}` 字段；同时在 pretty-print 用例中补充 `\"dvs\":{\"unit\":\"count\",\"value\":0}`（默认 metrics 下 counter 值为 0）。

## 小结

- **成效**：DV 现可在快照摘要、commit metrics、scan metrics 三个层面独立统计，与位置删除文件解耦；同时总量字段（`added-delete-files`、`total-delete-files`、`added-pos-deletes`、`added-file-size` 等）保持原有语义，向后兼容。
- **影响范围**：core 模块的统计与 metrics 模块；新增 JSON 字段对解析端是可选的（默认 null），不破坏旧客户端解析。
- **回迁到 1.4.x 的注意事项**：
  - DV 是 v3 表特性，1.4.x 作为维护分支通常只产出 v1/v2 表，DV 计数器在 v1/v2 表上恒为 0/不出现，回迁无实际收益。
  - 本提交依赖 #11467 的 `ContentFileUtil.isDV`，单独回迁会引入编译依赖。
  - 新增的 JSON 字段对 metrics 解析器是可选的，若 1.4.x 仅消费 metrics 而不产出 DV，可考虑仅回迁 metrics 解析端的兼容性改动（接受 `dvs`/`added-dvs`/`removed-dvs` 字段但不使用），但意义有限。
  - **不建议单独回迁**，应与整套 DV 功能一并评估。
