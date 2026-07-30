# 提交 2200：Spark: Remove dependency on hadoop's filesystem class from remove orphan files (#12254)

## 提交信息

- **序号**：2200 / 4088
- **哈希**：8e803312cf823fd76bdd3bc8ecfb3f6dbaddc542
- **短哈希**：8e803312c
- **日期**：2025-06-03 23:01:11 +0800
- **作者**：Ziyan
- **提交说明**：Spark: Remove dependency on hadoop's filesystem class from remove orphan files (#12254)
- **PR/Issue**：#12254

## 总体目的

这个提交为 Spark 的 `remove_orphan_files` 过程新增了一种基于 Iceberg FileIO 的前缀列举（prefix listing）方式，减少对 Hadoop FileSystem 类的依赖。原有的孤立文件删除逻辑依赖 Hadoop 的 `FileSystem` API 递归列出目录下的文件，这对于非 Hadoop 兼容的存储（如纯 S3 等）不太友好，且 Hadoop FileSystem 的递归列举在对象存储上可能性能较差或行为不一致。Iceberg 的 FileIO 接口提供了 `SupportsPrefixOperations` 扩展，支持通过 `listPrefix` 一次性列出某前缀下的所有文件，更适合对象存储。本提交新增 `usePrefixListing` 选项，当启用时使用 FileIO 的前缀列举替代 Hadoop FileSystem 的递归列举，从而降低对 Hadoop FileSystem 的耦合，并改善对象存储场景下的列举性能和行为一致性。

## 如何达成设计目的

- 在 `DeleteOrphanFilesSparkAction` 中新增 `usePrefixListing` 字段和 `usePrefixListing(boolean)` 设置方法。
- 将原有的 `listDirRecursively` 方法重命名为 `listDirRecursivelyWithHadoop`，并新增 `listDirRecursivelyWithFileIO` 方法，后者使用 `SupportsPrefixOperations.listPrefix` 列出前缀下所有文件。
- 在执行列举时根据 `usePrefixListing` 标志选择使用 FileIO 路径还是 Hadoop 路径；使用 FileIO 路径时校验 `table.io()` 必须实现 `SupportsPrefixOperations`。
- 新增 `isHiddenPath` 辅助方法，在 FileIO 路径中过滤隐藏路径（因为前缀列举返回所有文件，需自行过滤隐藏路径，而 Hadoop 路径有 PathFilter 在递归时处理）。
- 在 `RemoveOrphanFilesProcedure` 中新增 `prefix_listing` 参数，透传给 action。
- 在文档中新增 `prefix_listing` 参数说明和使用示例。
- 在测试中新增对 prefix listing 模式的测试覆盖。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +6/-0 lines)

**修改目的**：文档补充 prefix_listing 参数说明。

**工作逻辑**：在 `remove_orphan_files` 过程的参数表中新增 `prefix_listing` 行（boolean 类型，默认 false），说明启用时通过 `SupportsPrefixOperations` 接口进行前缀列举，要求 FileIO 实现该接口。并在示例区新增一段 `CALL ... remove_orphan_files(..., prefix_listing => true)` 的 SQL 示例。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (修改, +88/-22 lines)

**修改目的**：新增基于 FileIO 的前缀列举实现，保留原有 Hadoop 路径。

**工作逻辑**：
- 新增 `usePrefixListing` 字段和 setter 方法 `usePrefixListing(boolean)`。
- 将 `listDirRecursively` 重命名为 `listDirRecursivelyWithHadoop`，原有逻辑不变。
- 新增 `listDirRecursivelyWithFileIO(SupportsPrefixOperations io, String dir, Predicate<FileInfo>, PathFilter, List<String>)`：在 dir 末尾补 "/"，调用 `io.listPrefix(listPath)` 获取所有 `FileInfo`，对每个文件检查是否隐藏路径且满足时间谓词，加入匹配列表。
- 新增 `isHiddenPath(String baseDir, Path path, PathFilter)`：沿路径父目录向上遍历，检查是否有被 PathFilter 拒绝的隐藏路径段。
- 在 `withDataSource`（或主要列举入口）方法中，根据 `usePrefixListing` 分支：若为 true 则校验 FileIO 实现 `SupportsPrefixOperations` 并走 FileIO 列举路径；否则走原有 Hadoop 递归列举路径。
- 更新内部 `ListDirsRecursively` 等对 `listDirRecursively` 的调用为 `listDirRecursivelyWithHadoop`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java` (修改, +4/-2 lines)

**修改目的**：在存储过程层面暴露 prefix_listing 参数。

**工作逻辑**：新增 `prefix_listing` 布尔类型参数，在调用 action 时通过 `usePrefixListing` 方法透传给 `DeleteOrphanFilesSparkAction`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (修改, +142/-27 lines)

**修改目的**：为 prefix listing 模式新增测试覆盖。

**工作逻辑**：新增测试用例验证启用 `usePrefixListing` 时的孤立文件删除行为，包括正常删除、隐藏路径过滤、与 Hadoop 路径行为一致性等场景。

## 总结

该提交为 Spark 的 remove_orphan_files 新增了基于 Iceberg FileIO `SupportsPrefixOperations` 的前缀列举模式，作为 Hadoop FileSystem 递归列举的替代方案。这降低了对 Hadoop FileSystem 的耦合，更适合对象存储场景，并通过 `prefix_listing` 参数让用户按需选择。属于功能性增强。
