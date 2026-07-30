# 提交 4000：Core: Clean up TrackingStruct tests (#17041)

## 提交信息

- **序号**：4000 / 4088
- **哈希**：20bf72be73e9b4aa1dec30913e891fe8adabd8c5
- **短哈希**：20bf72be7
- **日期**：2026-07-08 11:13:53 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Clean up TrackingStruct tests (#17041)
- **PR/Issue**：#17041

## 总体目的

本提交对 `TrackingStruct` 和 `TrackingBuilder` 的测试进行清理和重组，提升测试的可读性、专注度和可维护性。提交说明列出了三项目标：
1. 将 `TrackingBuilder` 相关测试拆分到独立的测试套件 `TestTrackingBuilder`。
2. 在测试中用构造函数调用替代基于 ordinal 的 setter 调用（除非测试本身就是要验证 setter 行为）。
3. 在 `TestTrackingStruct` 中用构造函数调用替代 builder 的使用。

之前测试混合使用了 builder、按 ordinal 索引的 setter 和构造函数多种方式构造 `TrackingStruct`，且 builder 测试与 struct 测试混在同一个类中，导致测试意图不清晰、样板代码多。本次清理让 struct 测试聚焦于 struct 自身的行为，builder 测试聚焦于 builder 的契约。

## 如何达成设计目的

设计思路：
1. 新建 `TestTrackingBuilder.java`（286 行），把原本散落在 `TestTrackingStruct` 中验证 `TrackingBuilder` 行为的测试（如状态转换、DV 更新、源字段继承、非法参数校验等）集中到这个新类。
2. 重构 `TestTrackingStruct.java`：删除大量按字段名查 ordinal 的常量，改用 `TrackingStruct` 新增的构造函数直接传入 status/snapshotId/dataSeq/fileSeq/dvSnapshotId/firstRowId/deletedPositions/replacedPositions 等参数；保留专门的 `testSetByPosition`/`testGetByPosition` 测试来验证基于位置的存取。
3. 用常量 `DELETED_POSITIONS`/`REPLACED_POSITIONS` 替代散落的 `new byte[]{1,2}` 字面量。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTrackingBuilder.java` (+286/-0 lines, 新文件)

**修改目的**：集中 `TrackingBuilder` 的测试。

**工作逻辑**：
新增 286 行测试，覆盖：
- `added(...)`/`from(...)`/`deleted(...)`/`replaced(...)` 等工厂方法对字段的正确设置与保留。
- `dvUpdated()` 将状态改为 MODIFIED 并推进 dvSnapshotId（但保留原 snapshotId）。
- manifest 级 DV 位置变更（deletedPositions/replacedPositions）也会产生 MODIFIED 状态。
- 源 DV 位置不被 carry forward 到新 entry。
- 非法转换校验：ADDED 上设置 deletedPositions/replacedPositions 抛 `IllegalStateException`；manifest entry 上调 `dvUpdated()` 抛异常；null source 抛 `IllegalArgumentException`；源缺少 data/file sequence number 抛异常；从终态（DELETED/REPLACED）转换抛异常。
- 参数化测试 `terminalTransitionCases` 覆盖多种终态转换组合。
- EXISTING→EXISTING/DELETED/REPLACED 的合法转换。
- MODIFIED 源 carry forward 为 EXISTING。

辅助方法 `sourceTracking()`/`sourceTrackingWithStatus(status)`/`manifestSourceTracking()` 用 `TrackingStruct` 构造函数构造测试数据。

### `core/src/test/java/org/apache/iceberg/TestTrackingStruct.java` (+124/-405 lines)

**修改目的**：清理 `TrackingStruct` 测试，改用构造函数，移除 builder 依赖。

**工作逻辑**：
- 删除大量 `*_ORDINAL` 常量（基于 `TRACKING_FIELDS.indexOf(...)` 查找），仅保留 `MANIFEST_POSITION_ORDINAL`。
- 新增 `DELETED_POSITIONS`/`REPLACED_POSITIONS` 静态常量复用。
- `testFieldAccess`：从 `new TrackingStruct(Tracking.schema())` + 多个 `set(ordinal, value)` 改为 `new TrackingStruct(EntryStatus.ADDED, 42L, 10L, 11L, 43L, 1000L, DELETED_POSITIONS, REPLACED_POSITIONS)` + `setManifestLocation`/`set(MANIFEST_POSITION_ORDINAL, 7L)`，并增加对 deletedPositions/replacedPositions/manifestLocation/manifestPos 的断言。
- 新增 `testSetByPosition` 和 `testGetByPosition`：通过 `pos("field_name")` 辅助方法按名称查位置，专门验证基于位置的 set/get 行为，集中了原本散落的按位置访问测试。
- `testCopy`、`testInheritSnapshotId`、`testInheritSequenceNumberForAddedEntries`、`testDoNotInheritSequenceNumberForExistingEntries` 等全部改用构造函数初始化，例如 `new TrackingStruct(EntryStatus.ADDED, 42L, null, null, null, null, null, null)`。
- 移除原 `testAllStatuses` 参数化测试（用 EnumSource 遍历所有状态），因新构造方式已覆盖。
- builder 相关测试（from/deleted/replaced/dvUpdated 等）整体迁移到 `TestTrackingBuilder`。

## 总结

本提交是一次纯测试重构，没有修改生产代码。它通过拆分测试套件、统一用构造函数替代按 ordinal 的 setter、提取复用常量，显著提升了 `TrackingStruct`/`TrackingBuilder` 测试的清晰度和可维护性，使每个测试类聚焦于各自的职责。这为后续 Tracking 相关功能演进（如 row lineage、DV 继承）提供了更扎实的测试基础。
