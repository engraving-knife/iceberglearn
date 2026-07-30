# 提交 3017：Core: Deprecate scan response builder deleteFiles API (#14838)

## 提交信息

- **序号**：3017 / 4088
- **哈希**：ba28a3365a1a0e3cd64875c5d76191080799e8bf
- **短哈希**：ba28a3365
- **日期**：2025-12-15
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Deprecate scan response builder deleteFiles API (#14838)
- **PR/Issue**：#14838

## 总体目的

本提交旨在废弃 REST 扫描响应构建器中的 `deleteFiles` API，使其不再作为独立可设置的参数暴露给调用方。在原有设计中，`BaseScanTaskResponse` 的构建器同时接受 `fileScanTasks` 和 `deleteFiles` 两个独立列表，调用者需要显式地从 file scan tasks 中提取 delete files 并单独传入。这种设计存在两个问题：一是冗余——`FileScanTask` 本身已经包含 `deletes()` 方法，delete files 信息已经内嵌在 file scan tasks 中；二是脆弱（brittle）——调用者可能传入与 file scan tasks 完全不相关的 delete files，导致数据不一致。

提交说明明确指出："Users should not be able to build responses by passing an explicit list of delete files. They already have to pass through a list of file scan tasks which contain the delete files."（用户不应能通过显式传入 delete files 列表来构建响应，他们已经需要传入包含 delete files 的 file scan tasks 列表）。因此，本提交将 file scan tasks 作为唯一可信数据源（source of truth），在 `withFileScanTasks` 时自动从中派生 deleteFiles，从而消除数据不一致风险。

同时，本提交还在反序列化端增加了校验逻辑，确保从 JSON 解析出的 `deleteFiles` 字段只有在伴随 `fileScanTasks` 时才合法，否则视为非法响应。内部存储也从 `List<DeleteFile>` 改为 `DeleteFileSet`，以支持基于迭代器的惰性去重，避免重复存储同一 delete file。

## 如何达成设计目的

整体思路分三步：第一，在 `BaseScanTaskResponse` 内部将 `deleteFiles` 字段类型从 `List<DeleteFile>` 改为 `DeleteFileSet`，并在 `withFileScanTasks` 调用时自动从 file scan tasks 派生 deleteFiles；第二，将 `withDeleteFiles` 和 `deleteFiles()` 方法标记为 `@Deprecated`，警告调用方不要显式传入或获取 delete files；第三，清理所有内部调用方（CatalogHandlers、三个 Parser、对应测试），移除对 `withDeleteFiles` 的调用，并在 `TableScanResponseParser` 中增加一致性校验。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+0/-15 lines)

**修改目的**：移除服务端响应构建时对 `withDeleteFiles` 的显式调用。

**工作逻辑**：
原先在 `PlanTableScan`、`FetchPlanningResult`、`FetchScanTasks` 三处构建响应时，都通过 `fileScanTasks.stream().flatMap(task -> task.deletes().stream()).distinct().collect(Collectors.toList())` 手动提取 delete files 并调用 `withDeleteFiles`。本提交移除这三段代码，因为 `withFileScanTasks` 现在会自动派生 deleteFiles，无需手动传入。这消除了重复逻辑和潜在的数据不一致风险。

### `core/src/main/java/org/apache/iceberg/rest/TableScanResponseParser.java` (+10/-0 lines)

**修改目的**：在 JSON 反序列化时增加 deleteFiles 与 fileScanTasks 的一致性校验。

**工作逻辑**：
新增两处 `Preconditions.checkArgument` 校验。第一处：当 `fileScanTaskList` 为空时，检查 `deleteFiles` 必须为 null 或空，否则抛出 `"Invalid response: deleteFiles should only be returned with fileScanTasks that reference them"`。第二处：当外层分支中没有 fileScanTasks（即 `json` 中无对应字段）时，同样要求 `deleteFiles` 为 null 或空。这确保 REST 响应在协议层面的一致性——delete files 不能脱离 file scan tasks 独立存在。

### `core/src/main/java/org/apache/iceberg/rest/responses/BaseScanTaskResponse.java` (+22/-5 lines)

**修改目的**：重构响应基类，使 deleteFiles 从 file scan tasks 自动派生并废弃显式设置 API。

**工作逻辑**：
这是本提交的核心改动。具体变更：

1. 内部字段类型从 `List<DeleteFile> deleteFiles` 改为 `DeleteFileSet deleteFiles`，引入 `org.apache.iceberg.util.DeleteFileSet` 工具类支持去重和迭代器语义。
2. 构造方法中 `this.deleteFiles = deleteFiles` 改为 `this.deleteFiles = deleteFiles == null ? null : DeleteFileSet.of(deleteFiles)`，做防御性转换。
3. `deleteFiles()` 方法返回时从 `DeleteFileSet` 转回 `List<DeleteFile>`（通过 `Lists.newArrayList(deleteFiles.iterator())`），保持外部 API 兼容。
4. `withFileScanTasks` 方法新增派生逻辑：当 `tasks != null` 时，自动通过 `DeleteFileSet.of(() -> tasks.stream().flatMap(task -> task.deletes().stream()).iterator())` 从 file scan tasks 中提取并去重 delete files。这是"file scan tasks 作为唯一数据源"设计的关键实现。
5. `withDeleteFiles` 方法标记 `@Deprecated(since = "1.11.0, will be removed in 1.12.0")`，内部仍将传入的 list 转为 `DeleteFileSet`，但后续版本将移除该入口。
6. Builder 的 `deleteFiles()` getter 方法也标记 `@Deprecated(since = "1.11.0, visibility will be reduced in 1.12.0")`，提示可见性将在 1.12.0 降低。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponseParser.java` (+0/-1 lines)

**修改目的**：移除反序列化构建时对 `withDeleteFiles` 的调用。

**工作逻辑**：
删除 `.withDeleteFiles(deleteFiles)` 一行。虽然解析出的 `deleteFiles` 变量仍存在，但不再传入构建器，由 `withFileScanTasks` 自动派生。这是统一清理调用方的一部分。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchScanTasksResponseParser.java` (+0/-1 lines)

**修改目的**：移除反序列化构建时对 `withDeleteFiles` 的调用。

**工作逻辑**：
与上一文件同理，删除 `.withDeleteFiles(deleteFiles)` 调用。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponseParser.java` (+0/-1 lines)

**修改目的**：移除反序列化构建时对 `withDeleteFiles` 的调用。

**工作逻辑**：
与前两个 Parser 一致，删除 `.withDeleteFiles(deleteFiles)` 调用。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchPlanningResultResponseParser.java` (+0/-2 lines)

**修改目的**：移除测试中构建预期响应时对 `withDeleteFiles` 的调用。

**工作逻辑**：
两处测试构建响应时移除 `.withDeleteFiles(List.of(FILE_A_DELETES))` 和 `.withDeleteFiles(fromResponse.deleteFiles())`，使测试与新 API 行为一致——deleteFiles 由 fileScanTasks 自动派生。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestFetchScanTasksResponseParser.java` (+0/-2 lines)

**修改目的**：移除测试中对 `withDeleteFiles` 的调用。

**工作逻辑**：
与上一测试同理，移除两处 `.withDeleteFiles(...)` 调用。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestPlanTableScanResponseParser.java` (+0/-5 lines)

**修改目的**：移除测试中对 `withDeleteFiles` 的调用。

**工作逻辑**：
五处构建响应时移除 `.withDeleteFiles(...)` 调用，涉及单文件、多文件、复制响应、带凭证响应等场景。这些测试现在依赖 `withFileScanTasks` 自动派生 deleteFiles，验证新机制的正确性。

## 总结

本提交通过将 delete files 从独立可设置参数转变为 file scan tasks 的派生属性，消除了 REST 扫描响应中数据不一致的风险，简化了调用方代码。内部使用 `DeleteFileSet` 提升去重效率，并在反序列化端增加一致性校验，体现了 Iceberg 在 REST API 规范化与健壮性方面的持续投入。废弃 API 的标注也为后续 1.12.0 的彻底移除铺平了道路。
