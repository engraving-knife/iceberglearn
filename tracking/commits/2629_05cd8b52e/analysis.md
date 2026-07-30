# 提交 2629：fix: Make TableMetadataV3ValidMinimal actually v3 (#14061)

## 提交信息

- **序号**：2629 / 4088
- **哈希**：05cd8b52e7d06d0d246475bd11f138d25947fd13
- **短哈希**：05cd8b52e
- **日期**：2025-09-12 19:54:14 +0200
- **作者**：Christian
- **提交说明**：fix: Make TableMetadataV3ValidMinimal actually v3
- **PR/Issue**：#14061

## 总体目的

测试资源文件 `core/src/test/resources/TableMetadataV3ValidMinimal.json` 从文件名看应是"有效的最小化 V3 表元数据"，被 `TestLoadTableResponse.testRoundTripSerdeWithV3TableMetadata()` 测试用于验证 V3 表元数据的 round-trip 序列化/反序列化。

然而该文件的 `format-version` 字段实际写的是 `2`，而非 `3`。这意味着这个本应测试 V3 元数据的测试资源实际上是 V2 元数据，测试名与实际内容不符——测试并未真正覆盖 V3 场景。

本提交修正此错误：将 `format-version` 从 2 改为 3，并补充 V3 必需的 `next-row-id` 字段（值为 0），使该文件成为真正有效的最小化 V3 表元数据。V3 规范引入了行血缘（row lineage），`next-row-id` 是 V3 表元数据的必要字段，用于跟踪下一个待分配的行 ID。

此前测试之所以能通过，是因为 round-trip 测试（解析→序列化→比较）两边用的是同一个（错误的 V2）元数据，自然相等。修复后测试才真正验证 V3 元数据的序列化路径。

## 如何达成设计目的

直接修改 JSON 测试资源文件：
1. `"format-version": 2` 改为 `"format-version": 3`。
2. 在 `last-column-id` 之后新增 `"next-row-id": 0`，满足 V3 元数据对该字段的要求。

## 修改详情

### `core/src/test/resources/TableMetadataV3ValidMinimal.json` (+2/-1 lines)

**修改目的**：使该测试资源成为真正有效的 V3 表元数据。

**工作逻辑**：
- 将 `"format-version": 2` 改为 `"format-version": 3`，使文件名中的 "V3" 名副其实。
- 新增 `"next-row-id": 0` 字段。V3 表元数据要求此字段（行血缘特性的一部分，记录下一个待分配的行 ID）。设为 0 表示尚未分配任何行 ID，是一个有效的最小值。缺少该字段时，V3 元数据解析可能失败或被填充默认值，无法真正测试 V3 的完整路径。

该文件被 `TestLoadTableResponse.testRoundTripSerdeWithV3TableMetadata()` 使用：读取 JSON → `TableMetadataParser.fromJson` 解析为 `TableMetadata` 对象 → 用 `LoadTableResponse.builder().withTableMetadata(v3Metadata)` 构建 → 验证 round-trip 序列化等价。修复前，由于 format-version 实际为 2，该测试从未真正测试 V3 元数据；修复后才名副其实。

## 总结

本提交修复了一个测试资源文件的错误：`TableMetadataV3ValidMinimal.json` 的 `format-version` 实际是 2 而非 3，导致名为"V3"的测试实际测试的是 V2 元数据。通过将 format-version 改为 3 并补充 V3 必需的 `next-row-id` 字段，使测试资源名副其实，相关 round-trip 序列化测试才真正覆盖 V3 场景。这是一个测试正确性修复，虽小但重要——它确保了 V3 元数据序列化路径确实被测试覆盖。
