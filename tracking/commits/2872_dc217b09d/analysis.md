# 提交 2872：Core: Fix RESTFileScanTaskParser to handle empty delete file references list (#14568)

## 提交信息

- **序号**：2872 / 4088
- **哈希**：dc217b09ddf7824c125d580f42501de36307e9cf
- **短哈希**：dc217b09d
- **日期**：2025-11-12 19:03:12 +0100
- **作者**：ajreid21
- **提交说明**：Core: Fix RESTFileScanTaskParser to handle empty delete file references list (#14568)
- **PR/Issue**：#14568

## 总体目的

`RESTFileScanTaskParser` 负责将 `FileScanTask` 序列化和反序列化为 JSON 格式，用于 REST 通信。当一个文件扫描任务的删除文件引用列表（delete file references）为空列表（非 null 但 size 为 0）时，解析器存在两个问题：

1. **序列化端**：原代码仅检查 `deleteFileReferences != null`，因此空列表也会被序列化写入 JSON 中的 `delete-file-references` 字段（值为空数组 `[]`），而不是省略该字段。这导致不必要的空字段出现在 JSON 中。

2. **反序列化端**：当 JSON 中存在 `delete-file-references` 字段且为空列表时，原代码会调用 `Collections.max(indices)`，而对空列表调用 `Collections.max()` 会抛出 `NoSuchElementException`。这是一个明确的 bug，会导致无法解析包含空删除文件引用列表的 REST 响应。

此修复确保空删除文件引用列表在序列化时被省略，并在反序列化时正确处理空列表情况。

## 如何达成设计目的

修改分为两部分：

1. **序列化端**：在 `toJson` 方法中，将条件从 `deleteFileReferences != null` 改为 `deleteFileReferences != null && !deleteFileReferences.isEmpty()`，确保空列表不写入 JSON。
2. **反序列化端**：在 `fromJson` 方法的校验逻辑中，增加 `indices.isEmpty() ||` 前置短路条件，空列表时跳过 `Collections.max()` 调用，避免异常。
3. **测试**：新增 `roundTripSerdeWithoutDeleteFiles` 测试用例，验证没有删除文件的扫描任务可以正确进行序列化和反序列化往返。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTFileScanTaskParser.java` (+2/-2 lines)

**修改目的**：修复序列化和反序列化对空删除文件引用列表的处理。

**工作逻辑**：
- 序列化：`if (deleteFileReferences != null && !deleteFileReferences.isEmpty())`，只有非空列表才写入 `DELETE_FILE_REFERENCES` 字段。
- 反序列化校验：`indices.isEmpty() || Collections.max(indices) < allDeleteFiles.size()`，当索引列表为空时直接通过校验，不调用 `Collections.max()`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+45/-0 lines)

**修改目的**：新增测试验证无删除文件场景的序列化往返。

**工作逻辑**：`roundTripSerdeWithoutDeleteFiles` 测试创建一个 `FileScanTask`，其 `DeleteFile[]` 数组为空。构建 `PlanTableScanResponse` 后，验证：
1. 序列化后的 JSON 不包含 `delete-file-references` 字段。
2. 反序列化后重新序列化的结果与原始 JSON 一确一致。

## 总结

该提交修复了 REST 文件扫描任务解析器在处理空删除文件引用列表时的两个缺陷：序列化时不应写入空数组字段，反序列化时不应因空列表调用 `Collections.max()` 而崩溃。这是一个健壮性修复，确保 REST 协议能正确处理只有数据文件、没有删除文件的扫描任务场景。
