# 提交 3199：Flink: Backport: Dynamic Sink: Fix partition field check in non-immediate update path (#15190) (#15216)

## 提交信息

- **序号**：3199 / 4088
- **哈希**：995101f17999d09602af4bc43fe8aad351c10251
- **短哈希**：995101f17
- **日期**：2026-02-02
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Fix partition field check in non-immediate update path (#15190) (#15216)
- **PR/Issue**：#15190（源 PR），#15216（回移 PR）

## 总体目的

这是一个 backport（回移）提交，将序号 3196（PR #15190）中针对 Flink 动态 Sink `HashKeyGenerator` 的分区字段校验修复，回移到仍需维护的两个旧版本 Flink 模块 `flink/v1.20` 与 `flink/v2.0`。

源改动要解决的问题回顾：在 `hash` 分布模式 + equality fields 场景下，原校验 `equalityFields.contains(partitionField.name())` 比对的是分区字段名（可能是 transform 名如 `id_bucket`），而非 equality fields 中实际存放的源列名（如 `id`），导致合法写入被误判为"分区字段未包含在 equality fields 中"而抛 `IllegalStateException`。修复改为通过 `schema.findField(partitionField.sourceId())` 取源字段，再比对 `sourceField.name()`。

回移的原因是 `flink/v1.20` 和 `flink/v2.0` 是仍在使用的 Flink 集成版本，它们各自维护一份独立的 `HashKeyGenerator` 源码副本，存在同样的 bug，需要同步修复以保证这些版本上的用户不受该问题影响。注意主分支（序号 3196）只改了 `flink/v2.1`，本回移补齐了 v1.20 与 v2.0 两个版本。

## 如何达成设计目的

将 v2.1 中对 `HashKeyGenerator.java` 的修改与 `TestHashKeyGenerator.java` 新增的两个测试，原样应用到 `flink/v1.20` 与 `flink/v2.0` 两个目录下的同名文件。改动内容与源提交完全一致，属于纯代码回移。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+4/-1 lines)

**修改目的**：在 v1.20 版本修正 equality fields 与分区字段名称比对对象。

**工作逻辑**：
与序号 3196 相同：新增 `import org.apache.iceberg.types.Types;`，在校验循环中改为 `Types.NestedField sourceField = schema.findField(partitionField.sourceId());` 并以 `sourceField != null && equalityFields.contains(sourceField.name())` 替换原来的 `equalityFields.contains(partitionField.name())`，使比对基于源列名而非分区字段名。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+42/-0 lines)

**修改目的**：在 v1.20 版本补充正反两个测试用例。

**工作逻辑**：
新增 `testHashModeWithPartitionFieldAndEqualityField`（`bucket("id",4)` + equality `{"id","data"}`，同 id 不同 data 两行 writeKey 相等）与 `testHashModeWithPartitionFieldNotInEqualityFieldsFails`（equality 仅 `{"data"}` 时抛 `IllegalStateException` 且消息含关键词），与源提交测试一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+4/-1 lines)

**修改目的**：在 v2.0 版本做与 v1.20 相同的源码修复。

**工作逻辑**：
与上述 v1.20 改动完全一致——取源字段、比对源列名、加空值保护。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+42/-0 lines)

**修改目的**：在 v2.0 版本补充相同的两个测试用例。

**工作逻辑**：
与 v1.20 新增测试一致。

## 总结

该提交将主分支上 Flink 动态 Sink 分区字段校验修复（PR #15190）回移到 `flink/v1.20` 与 `flink/v2.0` 两个仍在维护的旧版本模块，确保多版本用户都能获得该 bug 修复。改动内容与源提交完全相同，属于标准的跨版本同步回移，无新增设计。
