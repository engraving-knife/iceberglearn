# 提交 4048：Core: Clean up TestTrackedFileAdapters and drop TrackedFileBuilder (#17144)

## 提交信息

- **序号**：4048 / 4088
- **哈希**：2660ac0fe2330aebc4393401279aad94ceac7f0b
- **短哈希**：2660ac0fe
- **日期**：2026-07-16 08:13:32 -0600
- **作者**：gaborkaszab
- **提交说明**：Core: Clean up TestTrackedFileAdapters and drop TrackedFileBuilder (#17144)
- **PR/Issue**：#17144

## 总体目的

这是一个测试代码清理提交，移除了 `TrackedFileBuilder` 这个测试辅助类及其测试套件，并将 `TestTrackedFileAdapters` 中对它的使用替换为直接构造 `TrackedFileStruct` 和 `TrackingStruct` 对象。

`TrackedFileBuilder` 是一个用于在测试中构建 `TrackedFile`（带跟踪元数据的文件，如序列号、行 ID 等）的构建器工具类。随着代码演进，它已成为最后的使用者只剩测试代码的死代码。同时，测试中通过 `ordinal`（字段序号）来设置字段值的方式（如 `file.set(SPEC_ID_ORDINAL, 99)`）是脆弱的——它依赖于 schema 中字段的排列位置，一旦字段顺序变化就会出错。

本提交的目标是：(1) 删除已无实际生产用途的 `TrackedFileBuilder` 类及其 863 行测试套件，减少维护负担；(2) 消除测试中基于 ordinal 的脆弱字段设置方式，改为通过构造函数按字段语义直接传值，使测试更清晰、更健壮。

## 如何达成设计目的

设计思路是用直接构造替代构建器模式：

1. **删除 `TrackedFileBuilder`** 及其测试 `TestTrackedFileBuilder`（共 1224 行删除），因为生产代码已不再使用它。

2. **重写 `TestTrackedFileAdapters`** 中的测试用例：原本通过 `TrackedFileBuilder.data(42L).formatVersion(...).location(...)...build()` 链式构建，再通过 `populateTrackingFields(file)` 用 ordinal 设置跟踪字段；现在改为直接 `new TrackedFileStruct(tracking, FileContent.DATA, ...)` 构造，跟踪字段也通过 `new TrackingStruct(EntryStatus.ADDED, 42L, DATA_SEQUENCE_NUMBER, ...)` 直接传入。

3. **移除 ordinal 查找逻辑**：删除了 `ordinalOf(schema, fieldName)` 辅助方法和 `DATA_SEQUENCE_NUMBER_ORDINAL`、`FILE_SEQUENCE_NUMBER_ORDINAL`、`CONTENT_TYPE_ORDINAL`、`SPEC_ID_ORDINAL`、`DELETION_VECTOR_ORDINAL` 等基于位置的常量，改为构造时按参数语义直接传值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackedFileBuilder.java` (deleted, -361 lines)

**修改目的**：删除已无生产用途的 `TrackedFileBuilder` 类。

**工作逻辑**：该类是一个包级可见的构建器，提供 `data(snapshotId)`、`equalityDelete(snapshotId)` 等工厂方法，链式设置 formatVersion、location、fileFormat、partition、recordCount、fileSizeInBytes、specId、contentStats、sortOrderId、keyMetadata、splitOffsets、equalityIds、deletionVector 等字段，最终 `build()` 出 `TrackedFileStruct`。随着生产代码改用其他方式构建文件元数据，该构建器仅被测试使用，故整体删除。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileBuilder.java` (deleted, -863 lines)

**修改目的**：删除 `TrackedFileBuilder` 对应的测试套件。

**工作逻辑**：该测试类包含 863 行测试，覆盖 `TrackedFileBuilder` 的各种构建场景。由于被测类已删除，测试随之删除。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileAdapters.java` (+163/-85 lines)

**修改目的**：将测试从 `TrackedFileBuilder` + ordinal 设置改为直接构造 `TrackedFileStruct`/`TrackingStruct`。

**工作逻辑**：
- 删除基于 ordinal 的常量和 `ordinalOf` 方法：
  ```java
  // 删除：
  private static final int DATA_SEQUENCE_NUMBER_ORDINAL = ordinalOf(Tracking.schema(), "sequence_number");
  private static final int SPEC_ID_ORDINAL = ordinalOf(TRACKED_FILE_SCHEMA, "spec_id");
  private static int ordinalOf(Types.StructType schema, String fieldName) { ... }
  ```
- 每个测试用例改为直接构造。例如 `testDataFileAdapterDelegation` 原本：
  ```java
  TrackedFile file = TrackedFileBuilder.data(42L)
      .formatVersion(FORMAT_VERSION_V4).location(DATA_FILE_LOCATION)...build();
  populateTrackingFields(file);
  ```
  改为：
  ```java
  TrackingStruct tracking = new TrackingStruct(
      EntryStatus.ADDED, 42L, DATA_SEQUENCE_NUMBER, FILE_SEQUENCE_NUMBER,
      null, FIRST_ROW_ID, null, null);
  tracking.setManifestLocation(MANIFEST_LOCATION);
  tracking.set(MANIFEST_POS_ORDINAL, MANIFEST_POS);
  TrackedFile file = new TrackedFileStruct(
      tracking, FileContent.DATA, FORMAT_VERSION_V4, DATA_FILE_LOCATION,
      FileFormat.PARQUET, PARTITION, 100L, 1024L, PARTITIONED_SPEC_ID,
      createContentStats(), 3, null, null,
      ByteBuffer.wrap(new byte[] {1, 2, 3}), ImmutableList.of(50L, 100L), null);
  ```
- `dummyTrackedFile` 辅助方法也改为通过构造函数直接传入 `contentType` 等参数，而非 `file.set(CONTENT_TYPE_ORDINAL, contentType.id())`。
- `populateTrackingFields` 辅助方法被删除，跟踪字段直接在 `TrackingStruct` 构造时传入。
- 拒绝/异常测试（如 `testUnknownSpecIdThrows`）原本通过 `file.set(SPEC_ID_ORDINAL, 99)` 设置非法 specId，改为在构造时直接传入 `99` 作为 specId 参数。

## 总结

这是一个纯测试代码清理提交，删除了 1224 行死代码（`TrackedFileBuilder` 及其测试），并消除了测试中基于字段 ordinal 的脆弱设置方式。改动后测试通过构造函数按语义直接传值，更清晰也更健壮（不再依赖 schema 字段排列顺序）。净减少约 1146 行代码，降低了维护负担，体现了对测试代码质量的持续投入。
