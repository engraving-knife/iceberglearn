# 提交 1985：Flink: Upgrades Flink Minor versions to 1.19.2 and 1.20.1 (#12745)

## 提交信息

- **序号**：1985 / 4088
- **哈希**：85b8fd35958c46a830472a512303e59189ee6e35
- **短哈希**：85b8fd359
- **日期**：2025-04-11 16:43:24 +0200
- **作者**：Rodrigo
- **提交说明**：Flink: Upgrades Flink Minor versions to 1.19.2 and 1.20.1 (#12745)
- **PR/Issue**：#12745

## 总体目的

本提交将 Iceberg 支持的 Flink 1.19 与 1.20 次版本分别从 1.19.1 / 1.20.0 升级到 1.19.2 / 1.20.1（补丁版本升级），并适配升级后 `TestIcebergCommitter` 测试中暴露的行为变化。

Flink 1.19.2 与 1.20.1 是对应次版本线的维护版本，包含 bug 修复与改进。升级版本范围（`strictly` 约束）使 Iceberg 在构建与测试时使用新的 Flink 补丁版本。升级后，`TestIcebergCommitter` 中关于从外部 checkpoint 恢复时 flink manifests 数量的断言不再成立——新版本下 snapshot 后仍会保留 1 个未提交的 manifest（原先断言为 0），且测试 harness 在 `initializeState` 时不会自动提交 pending commits（当 checkpointId > 0），需要显式调用 `notifyOfCompletedCheckpoint` 触发提交。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中将 `flink119` 与 `flink120` 的严格版本约束更新为新版本。
2. 更新 `TestFlinkPackage` 中对 `FlinkPackage.version()` 的期望字符串，使其与升级后的版本一致（1.19.2 / 1.20.1）。
3. 调整 `TestIcebergCommitter`（v1.19 与 v1.20 两份）的恢复测试：snapshot 后将 `assertFlinkManifests(0)` 改为 `assertFlinkManifests(1)`（新版本下 snapshot 后仍有 1 个 pending manifest 未清理），并在 `initializeState`/`open` 之后显式调用 `harness.notifyOfCompletedCheckpoint(checkpointId)` 以触发 pending commits 的提交，随后才断言 manifests 已清理为 0。

## 修改详情

### `gradle/libs.versions.toml` (修改, +2/-2 lines)

**修改目的**：升级 Flink 版本范围。

**工作逻辑**：`flink119 = { strictly = "1.19.1"}` → `{ strictly = "1.19.2"}`；`flink120 = { strictly = "1.20.0"}` → `{ strictly = "1.20.1"}`。`flink118` 保持 1.18.1 不变。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java` (修改, +1/-1 lines)

**修改目的**：更新版本断言。

**工作逻辑**：`testVersion` 中 `assertThat(FlinkPackage.version()).isEqualTo("1.19.1")` 改为 `"1.19.2"`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java` (修改, +7/-1 lines)

**修改目的**：适配新版本下 checkpoint 恢复行为。

**工作逻辑**：
- snapshot 后由 `assertFlinkManifests(0)` 改为 `assertFlinkManifests(1)`（新版本下 snapshot 时仍有 1 个未提交 manifest）。
- 在恢复并 `initializeState`/`open` 后，新增显式调用 `harness.notifyOfCompletedCheckpoint(checkpointId)`，并加注释说明 test harness 在 checkpointId > 0 时无法在 initializeState 中提交 pending commits，需显式触发；随后断言 `assertFlinkManifests(0)`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java` (修改, +1/-1 lines)

**修改目的**：更新版本断言。**工作逻辑**：期望版本由 `"1.20.0"` 改为 `"1.20.1"`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java` (修改, +6/-1 lines)

**修改目的**：与 v1.19 相同的恢复行为适配。**工作逻辑**：同 v1.19 的 `TestIcebergCommitter` 调整（`assertFlinkManifests(1)` + `notifyOfCompletedCheckpoint`）。

## 总结

本提交将 Flink 1.19/1.20 版本范围从 1.19.1/1.20.0 升级到 1.19.2/1.20.1，并适配升级后 `TestIcebergCommitter` 恢复测试的行为变化（snapshot 后保留 1 个 pending manifest、需显式 `notifyOfCompletedCheckpoint` 触发提交），同时更新 `TestFlinkPackage` 的版本断言。改动集中在版本目录与两个 Flink 版本的测试文件。
