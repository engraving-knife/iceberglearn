# 提交 3177：Spark: backport `#14886` to other Spark versions (#15178)

## 提交信息

- **序号**：3177 / 4088
- **哈希**：707be1a6a69d555e6601ba5b3a0acca86cc225e2
- **短哈希**：707be1a6a
- **日期**：2026-01-29
- **作者**：Alessandro Nori
- **提交说明**：Spark: backport `#14886` to other Spark versions (#15178)
- **PR/Issue**：#15178（回移 #14886）

## 总体目的

PR #14886 此前为 Iceberg 的 `DeleteOrphanFiles` 操作结果对象新增了 `orphanFilesCount` 字段，使调用方可以直接获取被删除孤儿文件的数量，而无需对返回的 `orphanFileLocations` 集合做 `size()` 计数。该字段对于流式删除场景尤其重要：当启用流式删除（`stream()` 模式）时，`orphanFileLocations` 可能只返回采样后的部分文件路径（例如 sample size 为 5 时只返回 5 个路径），但实际删除的文件数量可能更多。仅靠 `orphanFileLocations().size()` 无法获知真实删除数量，`orphanFilesCount` 则提供了准确的计数值。

#14886 最初仅合入到 Spark 4.1（main 分支），本提交将该改动回移（backport）到 Spark 3.4、3.5、4.0 三个仍在维护的版本，确保各 Spark 版本的行为一致，避免下游用户因版本差异而无法使用该计数功能。

## 如何达成设计目的

改动覆盖 Spark v3.4、v3.5、v4.0 三个版本目录，每个版本涉及三方面：主代码 `DeleteOrphanFilesSparkAction.java` 在构建返回结果时补充 `.orphanFilesCount(filesCount)`；基类测试 `TestRemoveOrphanFilesAction.java` 在各处断言中增加对 `orphanFilesCount()` 的验证；子类测试 `TestRemoveOrphanFilesAction3.java` 同步补充断言。三个版本的改动内容完全相同，属于机械式回移。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+3/-1 lines)

**修改目的**：在删除孤儿文件的结果中携带文件计数字段。

**工作逻辑**：
原代码在记录日志 `LOG.info("Deleted {} orphan files", filesCount)` 后构建结果时仅设置了 `orphanFileLocations(orphanFileList)`。修改后在 builder 链中追加 `.orphanFilesCount(filesCount)`，将内部已统计的 `filesCount` 变量写入结果对象。`filesCount` 在删除流程中已准确累计，此处只是将其暴露到 API 返回值中。

### `spark/v3.5/spark/src/main/java/.../DeleteOrphanFilesSparkAction.java` (+3/-1 lines)

**修改目的**：同 v3.4，回移相同改动。

**工作逻辑**：与 v3.4 完全一致，追加 `.orphanFilesCount(filesCount)`。

### `spark/v4.0/spark/src/main/java/.../DeleteOrphanFilesSparkAction.java` (+3/-1 lines)

**修改目的**：同 v3.4，回移相同改动。

**工作逻辑**：与 v3.4 完全一致，追加 `.orphanFilesCount(filesCount)`。

### `spark/v3.4/spark/src/test/java/.../TestRemoveOrphanFilesAction.java` (+45/-0 lines)

**修改目的**：为已有测试用例补充 `orphanFilesCount` 断言。

**工作逻辑**：
在约 15 处已有断言旁新增 `assertThat(result.orphanFilesCount())` 断言，覆盖多种场景：默认 olderThan 间隔（预期 0）、删除 1 个文件、流式 dry-run、删除 4 个文件、不删除任何文件、删除 2 个文件、metadata 文件清理、非流式 dry-run 返回 10 个文件，以及流式采样场景。其中流式采样场景（sample size 5）的断言尤为关键：`orphanFileLocations` 仅返回 5 个路径，但 `orphanFilesCount` 断言为 `invalidFiles.size()`（10），验证了计数字段反映实际删除数量而非返回集合大小。

### `spark/v3.5/spark/src/test/java/.../TestRemoveOrphanFilesAction.java` (+45/-0 lines)

**修改目的**：同 v3.4 测试，回移相同断言。

**工作逻辑**：与 v3.4 测试改动完全一致。

### `spark/v4.0/spark/src/test/java/.../TestRemoveOrphanFilesAction.java` (+45/-0 lines)

**修改目的**：同 v3.4 测试，回移相同断言。

**工作逻辑**：与 v3.4 测试改动完全一致。

### `spark/v3.4/spark/src/test/java/.../TestRemoveOrphanFilesAction3.java` (+11/-1 lines)

**修改目的**：为 v3.4 子类测试补充 `orphanFilesCount` 断言并修正一处断言写法。

**工作逻辑**：
在 5 处 trash 文件清理测试中新增 `assertThat(results.orphanFilesCount()).isEqualTo(1L)` 断言。此外，一处测试原先使用 `assertThat(results.orphanFileLocations()).contains(...)`，改为先通过 `StreamSupport.stream(...)` 转为流再做 `.anyMatch(...)` 匹配，以适应可能返回多个路径的场景，并补充 `orphanFilesCount` 断言。

### `spark/v3.5/spark/src/test/java/.../TestRemoveOrphanFilesAction3.java` (+5/-0 lines)

**修改目的**：同 v3.4 子类测试，回移断言。

**工作逻辑**：在 5 处 trash 文件清理测试中新增 `orphanFilesCount` 断言。v3.5 版本无需像 v3.4 那样修改 `anyMatch` 写法（该子类测试此前已使用流式匹配），改动更少。

### `spark/v4.0/spark/src/test/java/.../TestRemoveOrphanFilesAction3.java` (+5/-0 lines)

**修改目的**：同 v3.5 子类测试，回移断言。

**工作逻辑**：与 v3.5 子类测试改动一致。

## 总结

本提交将 `orphanFilesCount` 结果字段从 Spark 4.1 回移到 3.4、3.5、4.0 三个版本，使所有维护中的 Spark 版本均能通过 API 直接获取孤儿文件删除数量，尤其解决了流式采样场景下返回路径集合无法反映真实删除数量的问题。改动以机械式回移为主，测试覆盖全面，确保跨版本行为一致。
