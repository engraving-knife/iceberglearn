# 提交 3398：Avro: Reading files using DataFileStream with ROW LINEAGE if the column isn't projected (#15508)

## 提交信息

- **序号**：3398 / 4088
- **哈希**：49a9f9946a04121dcf7a9cb9c3f5c68837301dee
- **短哈希**：49a9f9946a
- **日期**：2026-03-16 19:03:00 +0100
- **作者**：Ayush Saxena
- **提交说明**：Avro: Reading files using DataFileStream with ROW LINEAGE if the column isn't projected (#15508)
- **PR/Issue**：#15508

## 总体目的

修复 Avro 读取器在文件中包含行级别元数据列（如 ROW_ID、LAST_UPDATED_SEQUENCE_NUMBER）但这些列未被投影（projected）时的读取问题。当列存在于文件中但不在投影模式中时，`projectionPos` 为 null，原有代码逻辑会错误地尝试处理这些列，导致读取失败。需要确保未被投影的列仅被跳过（skip）而非错误处理。

## 如何达成设计目的

1. 在 `ValueReaders` 的 `initColumnOrRowMetadataField` 方法中，首先检查 `projectionPos == null`，若为 null 则返回 `(null, fieldReader)`，保留 reader 仅用于跳过该字段
2. 在 `initConstantField` 方法中，移除对 `projectionPos != null` 的前置检查，改为只检查 `constant != null`，因为 `projectionPos` 为 null 时也正确返回 `(null, constantReader)`
3. 新增测试验证当文件中包含行级别列但投影模式不包含它们时，能正确读取数据列

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java` (+7/-2 lines)

**修改目的**：修复 `initColumnOrRowMetadataField` 和 `initConstantField` 方法对未投影列的处理。

**工作逻辑**：
- `initColumnOrRowMetadataField`：在方法开头新增 `projectionPos == null` 检查分支，返回 `Pair.of(null, fieldReader)`，注释说明 "field is in the file but not projected; keep the reader only for skipping"
- `initConstantField`：将条件从 `projectionPos != null && constant != null` 改为仅 `constant != null`，因为 `Pair.of(null, ...)` 在投影位置为 null 时是合法的返回值

### `core/src/test/java/org/apache/iceberg/data/avro/TestPlannedDataReader.java` (+64/-2 lines)

**修改目的**：新增测试 `testLineageColumnsNotProjected` 验证修复。

**工作逻辑**：
- 创建一个 Avro 文件，包含 data 列、ROW_ID 列和 LAST_UPDATED_SEQUENCE_NUMBER 列
- 使用 Iceberg Schema 仅投影 data 列
- 通过 `Avro.read(Files.localInput(file)).project(icebergSchema)` 读取文件
- 验证能正确读取 1 行数据，data 字段值为 "a"

## 总结

本提交修复了 Avro 读取器在文件包含行级别元数据列但这些列未被投影时的处理逻辑。通过在初始化阶段正确处理 `projectionPos == null` 的情况，确保未投影列仅被跳过而非导致错误。新增测试覆盖了该场景。
