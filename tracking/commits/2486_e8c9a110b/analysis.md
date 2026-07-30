# 提交 2486：Spark: Backport #13429 to Spark 4.0 and 3.4 (#13789)

## 提交信息

- **序号**：2486 / 4088
- **哈希**：e8c9a110b983f647d5360839a56dc9864ab4db23
- **短哈希**：e8c9a110b
- **日期**：2025-08-12 08:48:36 -0700
- **作者**：GuoYu
- **提交说明**：Spark: Backport #13429 to Spark 4.0 and 3.4 (#13789)
- **PR/Issue**：#13789

## 总体目的

该提交将 PR #13429（删除孤儿文件重构，将通用代码从 Spark 移到 core 模块）的变更同步（backport）到 Spark 4.0 和 Spark 3.4 两个模块，确保各 Spark 版本模块的一致性。

PR #13429（提交 2484）最初仅对 Spark 3.5 模块进行了重构，将 `DeleteOrphanFilesSparkAction` 中的通用逻辑（文件系统遍历、`PartitionAwareHiddenPathFilter`、`FileURI` 等）提取到 core 模块的 `FileSystemWalker` 和 `FileURI` 类。然而 Iceberg 同时维护 Spark 3.4、3.5 和 4.0 三个版本模块，Spark 4.0 和 3.4 模块中的 `DeleteOrphanFilesSparkAction` 存在相同的待重构代码。如果不同步这些变更，不同 Spark 版本模块之间会产生代码分叉，增加维护成本。该提交将相同的重构应用到 Spark 4.0 和 3.4 模块。

## 如何达成设计目的

对 Spark 4.0 和 Spark 3.4 两个模块的 `DeleteOrphanFilesSparkAction.java` 和 `TestRemoveOrphanFilesAction.java` 应用与 2484 提交完全相同的重构变更：

1. 移除被提取到 core 模块的方法和内部类（`listDirRecursivelyWithFileIO`、`listDirRecursivelyWithHadoop`、`isHiddenPath`、`PartitionAwareHiddenPathFilter`、`FileURI`）。
2. 改为引用 `org.apache.iceberg.actions.FileURI` 和调用 `FileSystemWalker` 的静态方法。
3. 方法参数从 `PathFilter` 改为 `Map<Integer, PartitionSpec> specs` 和 `Consumer<String>` 回调。
4. 更新测试中的 mock 目标和方法签名。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+15/-228 lines)

**修改目的**：将 Spark 4.0 模块的删除孤儿文件逻辑重构为使用 core 模块工具类。

**工作逻辑**：与提交 2484 对 Spark 3.5 的修改完全一致——移除被提取的通用方法和内部类，改为调用 `FileSystemWalker` 静态方法和引用 `FileURI` 公共类。移除大量 import（`IOException`、`Serializable`、`UncheckedIOException`、`Set`、`Collectors`、`Configuration`、`PathFilter`、`HiddenPathFilter` 等），新增 `FileURI` 和 `FileSystemWalker` import。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+8/-7 lines)

**修改目的**：适配重构后的方法签名。

**工作逻辑**：将 mock 静态方法目标从 `DeleteOrphanFilesSparkAction` 改为 `FileSystemWalker`，更新验证参数以匹配新方法签名。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+15/-228 lines)

**修改目的**：将 Spark 3.4 模块同步应用相同重构。

**工作逻辑**：与 Spark 4.0 模块修改完全一致。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+8/-7 lines)

**修改目的**：适配重构后的方法签名。

**工作逻辑**：与 Spark 4.0 测试修改完全一致。

## 总结

该提交是提交 2484（PR #13429）的 backport，将删除孤儿文件重构同步应用到 Spark 4.0 和 Spark 3.4 两个模块。这确保了 Iceberg 维护的三个 Spark 版本模块（3.4、3.5、4.0）在删除孤儿文件功能上保持代码一致性，均使用 core 模块的 `FileSystemWalker` 和 `FileURI` 工具类，避免代码分叉。该提交不引入新的功能或设计，仅是已有重构的版本同步。
