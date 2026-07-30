# 提交 2680：API, Spark 4.0: Add create_file_list option to RewriteTablePathProcedure. (#13837)

## 提交信息

- **序号**：2680 / 4088
- **哈希**：5f30e4fae80fc8dc589e477cfe76b63d3f08b80b
- **短哈希**：5f30e4fae
- **日期**：2025-09-24 17:46:42 +0200
- **作者**：slfan1989
- **提交说明**：API, Spark 4.0: Add create_file_list option to RewriteTablePathProcedure. (#13837)
- **PR/Issue**：#13837

## 总体目的

本提交为 Iceberg 的表路径重写（RewriteTablePath）功能新增两个方面的增强：

**1. `create_file_list` 选项**：在重写表路径时，默认会生成一个"文件列表"（file list），记录所有需要从源路径移动到目标路径的文件对（source path → target path）。这个文件列表对于实际执行文件搬迁操作的用户很有用，但在某些场景下用户不需要它——例如用户只想重写元数据（metadata）中的路径引用而不实际搬迁文件，或者用户有自己的文件追踪方式。新增 `create_file_list` 布尔选项（默认 true），设为 false 时跳过文件列表的生成，返回 "N/A" 作为文件列表位置，节省 IO 和存储开销。

**2. 结果计数增强**：在 RewriteTablePath 的结果中新增两个计数字段——`rewrittenDeleteFilePathsCount`（重写了路径的删除文件数量）和 `rewrittenManifestFilePathsCount`（重写了路径的 manifest 文件数量）。此前结果只返回 latest version 和 file list location，用户无法直观了解重写了多少 manifest 文件和 delete 文件。新增的计数提供了操作透明度，便于用户验证和监控。

这两项增强首先在 API 层和 Spark 4.0 模块落地，后续提交 2688 将 `create_file_list` 选项 backport 到 Spark 3.4 和 3.5。

## 如何达成设计目的

整体设计分为三层：
1. **API 层**：在 `RewriteTablePath` 接口新增 `createFileList(boolean)` 默认方法（默认返回 this，即不改变行为）和 `Result` 接口新增两个计数的默认方法（默认返回 0）。
2. **Core 层**：`BaseRewriteTablePath.Result` 用 Immutables 的 `@Value.Default` 覆盖两个新计数方法。
3. **Spark 4.0 层**：`RewriteTablePathSparkAction` 实现新的 `createFileList` 设置，重构 `rebuildMetadata()` 方法在构建结果时根据 `createFileList` 标志决定是否生成文件列表，并填充两个新计数字段。`RewriteTablePathProcedure` 在存储过程参数中新增 `create_file_list` 布尔参数，在输出类型中新增两个计数的列。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteTablePath.java` (+23/-0 lines)

**修改目的**：在 API 接口层声明新选项和新结果字段。

**工作逻辑**：
- 新增 `createFileList(boolean createFileList)` 默认方法，默认实现返回 `this`（不改变行为），由具体实现覆盖。文档说明默认值为 true（创建文件列表），设为 false 则跳过。
- 在 `Result` 接口新增 `rewrittenDeleteFilePathsCount()` 和 `rewrittenManifestFilePathsCount()` 两个默认方法，默认返回 0，保证向后兼容。

### `core/src/main/java/org/apache/iceberg/actions/BaseRewriteTablePath.java` (+14/-1 lines)

**修改目的**：在 core 的 Immutables Result 接口中覆盖新计数方法。

**工作逻辑**：`BaseRewriteTablePath.Result` 接口用 `@Value.Default` 注解覆盖 `rewrittenDeleteFilePathsCount()` 和 `rewrittenManifestFilePathsCount()`，使其调用父接口的默认实现。这样 Immutables 生成的 builder 可以设置这些值。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+32/-6 lines)

**修改目的**：实现 `create_file_list` 选项和计数统计。

**工作逻辑**：
- 新增 `NOT_APPLICABLE = "N/A"` 常量和 `createFileList` 字段（默认 true）。
- 实现 `createFileList(boolean)` 方法设置该字段。
- 重构 `doExecute()` 和 `rebuildMetadata()`：`rebuildMetadata()` 的返回类型从 `String`（文件列表位置）改为 `Result`（完整结果对象）。在 `rebuildMetadata()` 中，先执行 manifest list、manifest 和 position delete 的重写，收集 `deleteFiles` 和 `metaFiles`（manifest 文件集合），构建 `ImmutableRewriteTablePath.Result.Builder` 并填入 staging location、两个计数（`deleteFiles.size()` 和 `metaFiles.size()`）和 latest version。然后判断 `createFileList`：若为 false，直接返回 `fileListLocation(NOT_APPLICABLE)` 的结果；若为 true，继续构建 copyPlan 并调用 `saveFileList()` 生成文件列表，设置 fileListLocation 后返回。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteTablePathProcedure.java` (+21/-3 lines)

**修改目的**：在存储过程中暴露新参数和输出列。

**工作逻辑**：
- 新增 `CREATE_FILE_LIST_PARAM` 可选布尔参数（`optionalInParameter("create_file_list", DataTypes.BooleanType)`），加入 PARAMETERS 数组。
- 输出类型 `OUTPUT_TYPE` 从两列（latest_version、file_list_location）扩展为四列，新增 `rewritten_manifest_file_paths_count`（IntegerType）和 `rewritten_delete_file_paths_count`（IntegerType）。
- 在 `call()` 方法中读取 `create_file_list` 参数（默认 true），调用 `action.createFileList(createFileList)`。
- `toOutputRows()` 方法新增两个计数列的输出。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteTablePathProcedure.java` (+92/-0 lines)

**修改目的**：验证存储过程的新选项和输出。

**工作逻辑**：
- `testRewriteTablePathWithoutFileList()`：调用 `rewrite_table_path` 传 `create_file_list => false`，断言 file list location 为 "N/A"。
- `testRewriteTablePathWithManifestAndDeleteCounts()`：插入数据、添加 position delete 文件、再次插入数据，然后执行路径重写（`create_file_list => false`），断言 `rewritten_delete_file_paths_count` 为 1（一个 delete 文件）、`rewritten_manifest_file_paths_count` 为 5（5 个 manifest 文件被重写）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+19/-0 lines)

**修改目的**：验证 Action API 层的 `createFileList(false)` 行为。

**工作逻辑**：`testRewritePathWithoutCreateFileList()` 通过 Action API 调用 `.createFileList(false).execute()`，断言 latest version 为 "v3.metadata.json"，file list location 为 `NOT_APPLICABLE`。

## 总结

本提交为 RewriteTablePath 功能增加了 `create_file_list` 选项（允许跳过文件列表生成以节省开销）和两个结果计数字段（manifest 文件和 delete 文件的重写计数），提升了操作的灵活性和可观测性。改动覆盖 API、core 和 Spark 4.0 三层，含存储过程参数和输出列的扩展。后续提交 2688 将 `create_file_list` 选项 backport 到 Spark 3.4 和 3.5。
