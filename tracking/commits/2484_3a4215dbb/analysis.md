# 提交 2484：Spark, Core: Refactor Delete OrphanFiles by moving common code from Spark to core (#13429)

## 提交信息

- **序号**：2484 / 4088
- **哈希**：3a4215dbb714477c89681ab94f1197b6ebcbdfff
- **短哈希**：3a4215dbb
- **日期**：2025-08-11 19:29:21 +0200
- **作者**：GuoYu
- **提交说明**：Spark, Core: Refactor Delete OrphanFiles by moving common code from Spark to core (#13429)
- **PR/Issue**：#13429

## 总体目的

该提交将删除孤儿文件（Delete Orphan Files）功能中的通用代码从 Spark 模块重构到 core 模块，使这些逻辑可以被其他引擎（如 Flink、Trino 等）复用，减少代码重复。

此前，`DeleteOrphanFilesSparkAction` 中包含了大量与具体引擎无关的通用逻辑，包括文件系统递归遍历（`listDirRecursivelyWithHadoop`、`listDirRecursivelyWithFileIO`）、隐藏路径过滤（`PartitionAwareHiddenPathFilter`）、路径隐藏判断（`isHiddenPath`）、以及 `FileURI` 数据类等。这些逻辑本质上属于核心功能，不应仅存在于 Spark 模块中。如果其他引擎也需要实现删除孤儿文件功能，将不得不复制这些代码。通过将通用代码提取到 core 模块的 `FileSystemWalker` 和 `FileURI` 类中，实现了逻辑的单一来源和跨引擎复用。

## 如何达成设计目的

核心设计点如下：

1. **新建 `FileURI` 类（core 模块）**：将原 Spark 内部静态类 `FileURI` 提取为 `org.apache.iceberg.actions.FileURI` 公共类。增加了基于 `equalSchemes` 和 `equalAuthorities` 映射的构造方法，以及 `schemeMatch`、`authorityMatch` 等匹配方法，封装了 URI 组件比较逻辑。

2. **新建 `FileSystemWalker` 工具类（core 模块）**：将文件系统递归遍历逻辑提取为 `org.apache.iceberg.util.FileSystemWalker`，包含两个静态方法：
   - `listDirRecursivelyWithFileIO`：基于 `SupportsPrefixOperations` 的前缀列举遍历。
   - `listDirRecursivelyWithHadoop`：基于 Hadoop `FileSystem` API 的递归遍历，支持深度限制和子目录数量限制。
   - 内部封装了 `PartitionAwareHiddenPathFilter` 和 `isHiddenPath` 逻辑。

3. **重构 `DeleteOrphanFilesSparkAction`**：移除被提取的方法和内部类，改为调用 `FileSystemWalker` 的静态方法和 `FileURI` 的实例方法。方法签名从传入 `PathFilter` 改为传入 `Map<Integer, PartitionSpec> specs`，由 `FileSystemWalker` 内部构建过滤器。

4. **配套测试**：为 `FileURI` 和 `FileSystemWalker` 新增独立单元测试，同时更新 Spark 测试以适配新的方法签名和 mock 目标。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/FileURI.java` (+102/-0 lines, 新文件)

**修改目的**：将 FileURI 提取为 core 模块的公共类。

**工作逻辑**：定义了包含 scheme、authority、path、uriAsString 四个字段的 URI 数据类。提供两个构造方法：一个直接传入四个字段值，另一个基于 `URI` 和 `equalSchemes`/`equalAuthorities` 映射构建（用于处理等价的 scheme/authority）。提供 getter/setter（支持 Spark bean 编码器），以及 `schemeMatch`、`authorityMatch` 方法封装 URI 组件匹配逻辑（空值或大小写不敏感匹配）。

### `core/src/main/java/org/apache/iceberg/util/FileSystemWalker.java` (+219/-0 lines, 新文件)

**修改目的**：将文件系统遍历逻辑提取为 core 模块的工具类。

**工作逻辑**：

- `listDirRecursivelyWithFileIO`：基于 `SupportsPrefixOperations.listPrefix` 列举文件，使用 `PartitionAwareHiddenPathFilter` 过滤隐藏路径，对每个非隐藏文件应用 predicate 过滤后通过 Consumer 回调输出。

- `listDirRecursivelyWithHadoop`：基于 Hadoop `FileSystem.listStatus` 递归遍历。支持 `maxDepth` 深度限制（达到时将目录加入待处理列表）和 `maxDirectSubDirs` 子目录数量限制（超过时将子目录加入待处理列表），通过 Consumer 回调输出文件和未处理目录。

- 内部类 `PartitionAwareHiddenPathFilter`：从 Spark 模块迁移，过滤隐藏路径但保留以 `_` 或 `.` 开头的分区字段路径。`forSpecs` 静态方法从分区 specs 中提取隐藏分区名集合构建过滤器。

- `isHiddenPath`：从叶子路径向上遍历至 baseDir，检查路径层级中是否有被过滤器拒绝的隐藏路径。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+21/-201 lines)

**修改目的**：移除被提取的通用代码，改为调用 core 模块的工具类。

**工作逻辑**：

- 移除 `listDirRecursivelyWithFileIO`、`listDirRecursivelyWithHadoop`、`isHiddenPath` 方法。
- 移除内部类 `PartitionAwareHiddenPathFilter` 和 `FileURI`。
- 改为调用 `FileSystemWalker.listDirRecursivelyWithFileIO` 和 `FileSystemWalker.listDirRecursivelyWithHadoop`，方法参数从 `PathFilter` 改为 `Map<Integer, PartitionSpec> specs` 和 `Consumer<String>` 回调。
- `FileURI` 引用改为 `org.apache.iceberg.actions.FileURI`，字段访问改为 getter 方法调用（因 FileURI 从内部静态类变为公共类）。
- `ListDirsRecursively` 内部类中 `PathFilter` 字段改为 `specs` 字段，调用改为 `FileSystemWalker` 方法。
- `FILE_URI_ENCODER` 改为类内静态常量。

### `core/src/test/java/org/apache/iceberg/actions/TestFileURI.java` (+165/-0 lines, 新文件)

**修改目的**：为 `FileURI` 类添加单元测试。

### `core/src/test/java/org/apache/iceberg/util/TestFileSystemWalker.java` (+245/-0 lines, 新文件)

**修改目的**：为 `FileSystemWalker` 工具类添加单元测试，覆盖递归遍历、隐藏路径过滤、深度和子目录限制等场景。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+8/-7 lines)

**修改目的**：适配重构后的方法签名。

**工作逻辑**：将 mock 静态方法的目标从 `DeleteOrphanFilesSparkAction` 改为 `FileSystemWalker`，验证参数从 `anyList()`/`any(PathFilter.class)` 改为 `anyMap()`/`any()` 以匹配新方法签名（specs map 和 Consumer 回调）。

## 总结

该提交是一个重要的重构，将删除孤儿文件功能中的通用逻辑（文件系统遍历、隐藏路径过滤、FileURI 数据类）从 Spark 模块提取到 core 模块。这不仅减少了代码重复，更重要的是使这些核心逻辑可以被其他引擎复用，为后续 Flink、Trino 等引擎实现删除孤儿文件功能奠定基础。重构后 Spark 模块代码从约 242 行减少到约 21 行（净减少 180 行），同时配套了充分的单元测试。
