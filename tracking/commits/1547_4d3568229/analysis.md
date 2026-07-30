# 提交 1547 4d3568229 分析

## 提交信息
- 哈希：4d35682295b6692a194b621e5afb880d9ccfab6f
- 日期：2025-01-03（Fri Jan 3 17:00:23 2025 +0800）
- 作者：GuoYu <511955993@qq.com>
- 消息：Flink: Backport #11662 Fix range distribution npe when value is null to Flink 1.18 and 1.19 (#11745)

## 总体目的

将 main 分支上修复的 PR #11662 回迁到 Flink 1.18 和 1.19 两个版本模块，修复 Flink sink 在 range distribution（范围分发）模式下，当排序列的值为 null 时抛出 NullPointerException 的问题。

Iceberg Flink sink 支持 `WRITE_DISTRIBUTION_MODE=RANGE` 模式，在此模式下会根据表的排序键（sort key）对数据进行范围分区，使数据按排序顺序写入数据文件，从而优化查询性能。这一过程依赖 `SortKeySerializer` 对 `SortKey`（排序键对象）进行序列化和反序列化，以便在 TaskManager 和 Coordinator 之间传输统计信息。

原始 `SortKeySerializer`（版本 1）在序列化每个排序字段时，直接根据字段类型调用 `record.get(i, Boolean.class)` 等方法获取值并写入。当排序键的某个字段值为 null 时，这些 get 方法返回 null，而后续的类型特定写入操作（如 `target.writeBoolean(null)`）会抛出 NPE，导致整个 range distribution 机制崩溃。

本次修复通过引入序列化器版本 2，在每个字段值写入前先写入一个 null 标志位（boolean），如果值为 null 则写入 true 并跳过后续类型特定的序列化；反序列化时先读取 null 标志位，如果为 true 则将该字段设为 null。同时提供了完善的版本迁移机制，确保从旧版本 checkpoint 恢复时能正确兼容。

## 如何达成设计目的

修改同时应用于 `flink/v1.18/` 和 `flink/v1.19/` 两个模块（代码基本一致），涉及以下核心改动：

1. **`SortKeySerializer`**：引入 `version` 字段，在序列化/反序列化时根据版本决定是否写入/读取 null 标志位。
2. **`SortKeySerializerSnapshot`**：将 `CURRENT_VERSION` 从 1 升级到 2，实现版本兼容性检查和迁移逻辑。
3. **`CompletedStatistics`**：新增 `isValid()` 方法，用于校验反序列化后的统计信息是否有效（排除 null 值污染）。
4. **`CompletedStatisticsSerializer`**：新增版本切换方法，支持在反序列化时动态切换 SortKeySerializer 的版本。
5. **`StatisticsUtil`**：增强反序列化逻辑，先尝试 v2 反序列化并校验，失败则回退到 v1 反序列化，再切换回 v2。
6. **`DataStatisticsCoordinator`**：将 serializer 类型转换为具体类型 `CompletedStatisticsSerializer` 以调用版本切换方法。
7. **测试**：新增多个测试用例覆盖 null 值场景和版本迁移场景。

### 修改详情

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`（v1.19 同理）

**修改目的**：核心修复——在序列化/反序列化 SortKey 时正确处理 null 值。

**工作逻辑**：
- 新增 `private int version` 字段，并新增构造函数 `SortKeySerializer(Schema, SortOrder, int version)`，原构造函数委托为新构造函数并传入 `CURRENT_VERSION`。
- 新增 `setVersion(int)`、`restoreToLatestVersion()`、`getLatestVersion()` 方法，支持运行时切换版本。
- **序列化**（`serialize` 方法）：在每个字段的处理循环中，当 `version > 1` 时，先获取字段值并判断是否为 null。如果为 null，写入 `true` 并 `continue` 跳过类型特定序列化；如果不为 null，写入 `false` 并继续正常的类型序列化。
- **反序列化**（`deserialize` 方法）：在每个字段的处理循环中，当 `version > 1` 时，先读取一个 boolean。如果为 true（表示 null），调用 `reuse.set(i, null)` 并 `continue`；否则继续类型特定的反序列化。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java` 内部类 `SortKeySerializerSnapshot`

**修改目的**：升级序列化器快照版本到 2，并实现 v1→v2 的兼容性迁移。

**工作逻辑**：
- `CURRENT_VERSION` 从 1 改为 2。
- 新增 `private int version = CURRENT_VERSION` 字段。
- `readSnapshot` 方法改为 switch 结构：读取 v1 时设置 `this.version = 1`，读取 v2 时保持默认版本。
- `resolveSchemaCompatibility`（v1.18）或 `resolveSchemaCompatibility`（v1.19）方法：当新序列化器为 v2 而旧快照为 v1 时，返回 `compatibleAfterMigration()`，表示可以通过迁移实现兼容。
- `restoreSerializer` 方法：传入 `version` 而非使用默认版本，确保恢复时使用与 checkpoint 匹配的版本。
- `readV1` 方法重命名为通用的 `read` 方法（v1 和 v2 的 schema/sortOrder 读取逻辑相同）。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`

**修改目的**：新增统计信息有效性校验方法，用于检测反序列化是否因版本不匹配而产生无效数据。

**工作逻辑**：`isValid()` 方法根据统计类型进行校验：
- `Sketch` 类型：检查 `keySamples` 是否为 null。
- `Map` 类型（即 `Range` 统计）：检查 `keyFrequency()` 是否为 null，以及其 values 中是否包含 null。如果包含 null，说明反序列化出错（可能是 v2 序列化器错误解析了 v1 数据），应触发回退逻辑。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatisticsSerializer.java`

**修改目的**：提供版本切换能力，支持在反序列化时动态调整内部 SortKeySerializer 的版本。

**工作逻辑**：新增两个方法：
- `changeSortKeySerializerVersion(int version)`：将内部的 `sortKeySerializer` 转型为 `SortKeySerializer` 并调用 `setVersion(version)`。
- `changeSortKeySerializerVersionLatest()`：调用 `restoreToLatestVersion()` 将版本恢复为最新（v2）。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsUtil.java`

**修改目的**：增强反序列化逻辑，实现 v1→v2 的平滑迁移。

**工作逻辑**：`deserializeCompletedStatistics` 方法签名从接收 `TypeSerializer<CompletedStatistics>` 改为接收具体类型 `CompletedStatisticsSerializer`。新逻辑为：
1. 先尝试用当前版本（v2）反序列化。
2. 调用 `isValid()` 校验结果。如果有效，直接返回。
3. 如果无效或抛出异常，进入 catch 块：将序列化器切换到 v1，重新反序列化（此时能正确解析旧格式数据），然后将序列化器切换回 v2（用于后续 TM 传来的新数据）。
4. 如果 v1 反序列化也失败，抛出 `UncheckedIOException`。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：适配 `StatisticsUtil.deserializeCompletedStatistics` 的新签名。

**工作逻辑**：将传入的 `completedStatisticsSerializer` 强转为 `CompletedStatisticsSerializer` 类型。

#### 测试文件（v1.18 和 v1.19 各 5 个测试文件）

- **`TestFlinkIcebergSinkDistributionMode.java`**：新增 `testRangeDistributionWithNullValue` 测试，验证包含 null 值的数据流在 range distribution 模式下能正确写入。
- **`TestCompletedStatisticsSerializer.java`**：新增 `testSerializer`、`testRestoreOldVersionSerializer`、`testRestoreNewSerializer` 三个测试，覆盖新版本序列化、从 v1 恢复、从 v2 恢复三种场景。
- **`TestDataStatisticsCoordinator.java`**：新增 `testDataStatisticsEventHandlingWithNullValue` 测试，验证 Coordinator 能正确处理包含 null SortKey 的统计事件。
- **`TestDataStatisticsOperator.java`**：新增 `testProcessElementWithNull` 测试，验证 Operator 能正确处理包含 null 值的记录。
- **`TestSortKeySerializerPrimitives.java`**：调整断言，序列化字节数从 38 字节改为 39 字节（新增 1 字节 null 标志位）。
- **`TestSortKeySerializerSnapshot.java`**：新增 `testRestoredOldSerializer` 测试，验证从 v1 快照恢复并反序列化 v1 数据的正确性。

## 小结

- **成效**：修复了 Flink sink range distribution 模式下排序键值为 null 时的 NPE 问题，通过引入序列化器 v2 格式（每个字段前增加 null 标志位）从根本上解决了 null 值的序列化/反序列化问题，同时提供了完善的 v1→v2 迁移机制确保已有 checkpoint 的兼容性。
- **影响范围**：涉及 Flink 1.18 和 1.19 两个模块的 22 个文件（每个模块 11 个），核心修改在 `SortKeySerializer` 和 `StatisticsUtil`，其余为适配和测试。改动涉及序列化格式变更（v1→v2），但通过迁移机制保证向后兼容。
- **回迁到 1.4.x 的注意事项**：这是 bug 修复回迁（从 main 回迁到 Flink 1.18/1.19 模块），1.4.x 分支如果包含 Flink 1.18/1.19 模块且存在同样的 NPE 问题，**应回迁**此修复。序列化器版本升级通过 `SortKeySerializerSnapshot` 的兼容性机制处理，不会破坏现有 checkpoint 的恢复。
