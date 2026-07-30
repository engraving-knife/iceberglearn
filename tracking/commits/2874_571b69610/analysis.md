# 提交 2874：REST: Fix serde of tasks with multiple deletes (#14573)

## 提交信息

- **序号**：2874 / 4088
- **哈希**：571b696106f4748d3edee9ee641e214377ddcb16
- **短哈希**：571b69610
- **日期**：2025-11-13 16:13:54 +0530
- **作者**：Prashant Singh
- **提交说明**：REST: Fix serde of tasks with multiple deletes (#14573)
- **PR/Issue**：#14573

## 总体目的

`TableScanResponseParser` 负责将表扫描响应（包含删除文件列表和文件扫描任务列表）序列化为 JSON。在处理多个文件扫描任务、每个任务引用不同删除文件的场景下，存在两个 bug：

1. **删除文件引用索引累积 bug**：原代码在循环外部创建了一个共享的 `Set<Integer> deleteFileReferences`，所有文件扫描任务共用同一个 Set。这意味着第一个任务的删除文件引用会累积到第二个任务中，第二个任务的引用又会累积到第三个任务中，以此类推。每个任务实际上会包含自己以及之前所有任务的删除文件引用，导致引用错误。

2. **文件路径匹配 bug**：原代码使用 `deleteFile.path().toString()`（即 `CharSequence` 形式的路径）作为 Map 的 key 来查找索引，但 `deleteFile.location()` 返回的是字符串路径。两者可能不一致（`path()` 返回 `CharSequence`，`location()` 返回 `String`），导致查找失败返回 null。

此修复将 `deleteFileReferences` Set 的创建移到每个任务的循环内部，确保每个任务独立计算自己的删除文件引用；同时统一使用 `location()` 进行路径匹配。

## 如何达成设计目的

1. 将 `Set<Integer> deleteFileReferences = Sets.newHashSet();` 的声明从循环外移到循环内（`for (FileScanTask fileScanTask : fileScanTasks)` 体内），使每个任务拥有独立的引用集合。
2. 将 `deleteFilePathToIndex.put(String.valueOf(deleteFile.path()), i)` 改为 `deleteFilePathToIndex.put(deleteFile.location(), i)`，统一使用 `location()` 作为 key。
3. 将 `deleteFilePathToIndex.get(taskDelete.path().toString())` 改为 `deleteFilePathToIndex.get(taskDelete.location())`，查找时也使用 `location()`。
4. 新增测试用例验证多个任务各自引用不同删除文件的场景。
5. 将 `TestBase` 中的 `FILE_B`、`FILE_B_DELETES`、`FILE_C`、`FILE_C2_DELETES` 从包级私有改为 public，以便测试类引用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/TableScanResponseParser.java` (+3/-3 lines)

**修改目的**：修复删除文件引用累积和路径匹配问题。

**工作逻辑**：
- 构建 `deleteFilePathToIndex` Map 时，使用 `deleteFile.location()` 作为 key（而非 `String.valueOf(deleteFile.path())`）。
- 将 `deleteFileReferences` Set 的声明移入 `for` 循环内部，每个 `FileScanTask` 独立拥有一个 Set，避免引用跨任务累积。
- 查找引用索引时，使用 `taskDelete.location()` 匹配。

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+4/-4 lines)

**修改目的**：将测试常量改为 public 以供其他测试类使用。

**工作逻辑**：`FILE_B`、`FILE_B_DELETES`、`FILE_C`、`FILE_C2_DELETES` 从 `static final` 改为 `public static final`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+131/-0 lines)

**修改目的**：新增测试验证多个任务引用不同删除文件时引用不累积。

**工作逻辑**：`multipleTasksWithDifferentDeleteFilesDontAccumulateReferences` 测试创建三个文件扫描任务（taskA、taskB、taskC），每个任务各引用一个不同的删除文件（FILE_A_DELETES、FILE_B_DELETES、FILE_C2_DELETES）。序列化后验证：
- taskA 的 `delete-file-references` 为 `[0]`
- taskB 的为 `[1]`
- taskC 的为 `[2]`

如果不修复累积 bug，taskB 会包含 `[0, 1]`，taskC 会包含 `[0, 1, 2]`。

## 总结

该提交修复了 REST 表扫描响应序列化中的两个 bug：删除文件引用跨任务累积（Set 共享）和路径匹配不一致（`path()` vs `location()`）。这确保了多个文件扫描任务各自正确引用自己的删除文件，不会因为其他任务的删除文件而被错误关联。这对于支持位置删除和等值删除的 Iceberg 表在 REST 通信中的正确性至关重要。
