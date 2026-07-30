# 提交 2227：Spark: Port prefix listing option in remove orphan files to Spark 3.4 and Spark 4.0 (#13264)

## 提交信息

- **序号**：2227 / 4088
- **哈希**：7b8bd29ce80bc78ed882f0613f7570e78e325988
- **短哈希**：7b8bd29ce
- **日期**：2025-06-10 17:07:18 +0800
- **作者**：Ziyan
- **提交说明**：Spark: Port prefix listing option in remove orphan files to Spark 3.4 and Spark 4.0 (#13264) backports #12254
- **PR/Issue**：#13264（backport #12254）

## 总体目的

这个提交是 PR #12254 的反向移植，将为 Spark 3.4 和 Spark 4.0 模块的 RemoveOrphanFiles（删除孤儿文件）功能添加 prefix listing（前缀列举）选项。此前，删除孤儿文件操作使用 Hadoop 的递归目录列举方式来发现表目录下的所有文件，这在某些对象存储（如 S3）上性能较差，因为对象存储更适合通过前缀查询而非递归目录遍历。本提交为 `DeleteOrphanFilesSparkAction` 新增 `usePrefixListing` 选项，当设置为 true 且 FileIO 支持 `SupportsPrefixOperations` 时，使用 FileIO 的 `listPrefix` 方法替代 Hadoop 递归目录列举，从而在对象存储场景下显著提升孤儿文件清理的性能。该功能此前已在 Spark 3.5 中实现，本提交将其同步到 Spark 3.4 和 4.0。

## 如何达成设计目的

- 在 `DeleteOrphanFilesSparkAction` 中新增 `usePrefixListing` 字段和 setter 方法。
- 重构 `toDataset` 方法：根据 `usePrefixListing` 标志选择列举方式。prefix 模式使用 `listDirRecursivelyWithFileIO`（通过 FileIO 的 listPrefix），默认模式使用 `listDirRecursivelyWithHadoop`（通过 Hadoop 递归列举）。
- 新增 `listDirRecursivelyWithFileIO` 方法：使用 `SupportsPrefixOperations.listPrefix` 列举前缀下所有文件，通过 `FileInfo.createdAtMillis` 过滤修改时间，并使用 `isHiddenPath` 过滤隐藏路径。
- 将原 `listDirRecursively` 重命名为 `listDirRecursivelyWithHadoop` 以区分两种列举方式。
- 在 `RemoveOrphanFilesProcedure` 中新增 `prefix_listing` 过程参数，传递给 action。
- 在测试中新增 prefix listing 模式的测试用例。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (修改, +78/-33 lines)

**修改目的**：添加 prefix listing 支持并重构目录列举逻辑。

**工作逻辑**：
- 新增 `usePrefixListing` 字段（默认 false）和 `usePrefixListing(boolean)` setter。
- `toDataset` 方法分支：
  - **prefix 模式**：校验 `table.io()` 实现 `SupportsPrefixOperations`，创建基于 `FileInfo.createdAtMillis` 的谓词，调用 `listDirRecursivelyWithFileIO` 列举，并行化后返回 Dataset。
  - **hadoop 模式**（默认）：使用原逻辑，基于 `FileStatus.getModificationTime`，调用 `listDirRecursivelyWithHadoop`，在 driver 上列举浅层目录后将深层子目录分发到 executor 并行列举。
- 新增 `listDirRecursivelyWithFileIO`：调用 `io.listPrefix(listPath)` 获取所有文件，通过 `isHiddenPath` 和时间谓词过滤。
- 新增 `isHiddenPath`：从文件路径向上遍历父目录，检查路径过滤器是否接受，用于过滤隐藏文件和目录。
- 将 `listDirRecursively` 重命名为 `listDirRecursivelyWithHadoop`（标注 `@VisibleForTesting`），内部递归逻辑不变。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java` (修改, +6 lines)

**修改目的**：暴露 prefix_listing 参数到 SQL 存储过程。

**工作逻辑**：
- 在 `PROCEDURE_PARAMETERS` 数组末尾新增 `ProcedureParameter.optional("prefix_listing", DataTypes.BooleanType)`。
- 在方法体中从参数索引 9 读取 `prefixListing`（默认 false）。
- 调用 `action.usePrefixListing(prefixListing)` 传递给 action。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (修改, +160/-76 lines)

**修改目的**：测试 prefix listing 模式的正确性。

**工作逻辑**：新增测试用例验证 prefix listing 模式下的孤儿文件删除行为，包括正常删除、隐藏文件过滤、时间过滤等场景。

### Spark 4.0 模块的相同文件（修改, 共约 +317/-49 lines）

与 Spark 3.4 模块完全对应的修改，包括：
- `DeleteOrphanFilesSparkAction.java`（+78/-32）：相同的 prefix listing 实现。
- `RemoveOrphanFilesProcedure.java`（+6）：相同的参数添加。
- `TestRemoveOrphanFilesAction.java`（+93/-17）：对应的测试用例。

## 总结

该提交为 Spark 3.4 和 4.0 的删除孤儿文件功能添加了 prefix listing 选项，使其能利用对象存储的前缀列举能力替代 Hadoop 递归目录遍历，显著提升在 S3 等对象存储上的性能。通过 `usePrefixListing` 标志在两种列举方式间切换，并校验 FileIO 是否支持前缀操作。同时通过 SQL 存储过程参数 `prefix_listing` 暴露给用户。测试覆盖了 prefix listing 模式的各种场景。
