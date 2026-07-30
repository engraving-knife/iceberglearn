# 提交 4009：Flink: Backport: Integrate ConvertEqualityDeletes with IcebergSink (#17142) (#17156)

## 提交信息

- **序号**：4009 / 4088
- **哈希**：9ddc391c1ac863e94be6520be5adc63ccdc19eb9
- **短哈希**：9ddc391c1
- **日期**：2026-07-10 15:46:28 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Integrate ConvertEqualityDeletes with IcebergSink (#17142) (#17156)
- **PR/Issue**：#17156（backport of #17142）

## 总体目的

本提交是将提交 4007（#17142，"Flink: Integrate ConvertEqualityDeletes with IcebergSink"）backport 到 Flink 1.20 和 2.0 两个版本。原提交只在 Flink 2.1 中集成了 `ConvertEqualityDeletes` 维护任务到 `IcebergSink`，本次将相同的改动应用到 `flink/v1.20` 和 `flink/v2.0` 两个模块，使所有支持的 Flink 版本都能使用该功能。

## 如何达成设计目的

将 4007 在 `flink/v2.1` 下的全部 8 个文件改动原样复制到 `flink/v1.20` 和 `flink/v2.0` 对应路径，共 16 个文件。代码逻辑与 4007 完全一致，仅包路径前缀（`flink/v1.20` vs `flink/v2.1`）不同。

## 修改详情

### `flink/v1.20/flink/...` (+390/-0 lines, 8 files)

**修改目的**：backport 到 Flink 1.20。

**工作逻辑**：与 4007 相同的改动：
- 新增 `ConvertEqualityDeletesConfig.java`
- `ConvertEqualityDeletes.java` 新增 `config(...)` 方法
- `FlinkWriteConf.java` 新增 `convertEqualityDeletesMode()`
- `FlinkWriteOptions.java` 新增 `CONVERT_EQUALITY_DELETES_ENABLE`
- `FlinkMaintenanceConfig.java` 新增工厂方法
- `IcebergSink.java` 新增 `convertEqualityDeletes()` builder 方法和 `addConvertEqualityDeletesTask`
- 新增 `TestConvertEqualityDeletesConfig.java` 和 `TestIcebergSinkTableMaintenance.java` 测试

### `flink/v2.0/flink/...` (+390/-0 lines, 8 files)

**修改目的**：backport 到 Flink 2.0。

**工作逻辑**：与 Flink 1.20 完全相同的改动。

## 总结

本提交是 4007 的跨版本 backport，将 `ConvertEqualityDeletes` 与 `IcebergSink` 的集成推广到 Flink 1.20 和 2.0，确保三个支持的 Flink 版本（1.20、2.0、2.1）功能一致。代码逻辑无差异，仅是并行维护多个 Flink 版本分支的常规操作。注意本次 backport 未包含 4007 中的文档改动（`docs/docs/flink-maintenance.md`、`flink-writes.md`），因为文档是版本无关的，已在 4007 中更新。
