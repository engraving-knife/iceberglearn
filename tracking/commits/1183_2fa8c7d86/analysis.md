# 提交 1183：Core: Add rewritten delete files to write results (#11203)

## 提交信息

- **序号**：1183 / 4088
- **哈希**：2fa8c7d86c72e1869a97b847eb6b31fed2c4abb3
- **短哈希**：2fa8c7d86
- **日期**：2024-09-25（Wed Sep 25 08:30:06 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Add rewritten delete files to write results (#11203)
- **PR/Issue**：#11203

## 总体目的

Iceberg 在写入流程中会产出 `WriteResult`，用以汇总本次写入产生的数据文件（data files）、删除文件（delete files）以及被引用的数据文件（referenced data files）。在某些写场景（例如 position delta 写入、行级更新）中，写入任务除了"产出新的 delete 文件"之外，还可能"重写已有的 delete 文件"（即把旧的等值删除/位置删除合并、压缩成新的 delete 文件）。

本提交的核心目的是在 `WriteResult` 与 `DeleteWriteResult` 中显式区分并承载"被重写的 delete 文件"（rewritten delete files）这一新类别，使下游（如 commit 阶段、统计、重试逻辑）能够区分"新产出的 delete 文件"与"被重写的 delete 文件"，从而为后续的删除文件生命周期管理（例如 commit 时移除旧 delete 文件、保留新 delete 文件）提供必要的元信息。

这是后续支持"重写 delete 文件"特性（如 row-level rewrite、copy-on-write 与 merge-on-read 中的 delete compaction）的基础设施改动。

## 如何达成设计目的

1. 在 `DeleteWriteResult` 中新增字段 `rewrittenDeleteFiles`，并提供新的构造函数接收该参数；老的构造函数将该字段默认为空列表以保持向后兼容。同时新增 `rewrittenDeleteFiles()` 访问器。
2. 在 `WriteResult` 中新增字段 `rewrittenDeleteFiles`（数组形式，因为 `WriteResult` 是 `Serializable`，用于 Spark 等分布式执行环境序列化传递）。同时新增 `rewrittenDeleteFiles()` 访问器。
3. 在 `WriteResult.Builder` 中新增 `rewrittenDeleteFiles` 列表字段，并提供 `addRewrittenDeleteFiles(DeleteFile...)` 与 `addRewrittenDeleteFiles(Iterable<DeleteFile>)` 两个重载方法。`build()` 时将该列表传入构造函数。`add(WriteResult)` 也会合并该字段。
4. 在 `BasePositionDeltaWriter` 中将 `DeleteWriteResult.rewrittenDeleteFiles()` 透传到 `WriteResult` 的 builder。
5. 在 `.palantir/revapi.yml` 中显式接受 `WriteResult` 的默认序列化版本变更（因为新增字段会改变 `serialVersionUID` 的隐式计算），避免 revapi API 检查失败。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：豁免 `WriteResult` 由于新增字段导致的二进制兼容性检查失败。

**工作逻辑**：在 `acceptedBreaks` 列表中新增一条规则，针对 `org.apache.iceberg.io.WriteResult` 类的 `java.class.defaultSerializationChanged` 错误，理由是 "Serialization across versions is not supported"（Iceberg 不支持跨版本序列化，因此 `WriteResult` 的序列化兼容性变更不被视为破坏性变更）。Palantir revapi 是用于检测 Java 库 API 二进制兼容性的工具，新增字段会改变默认 `serialVersionUID`，此处提前登记豁免。

### `core/src/main/java/org/apache/iceberg/io/BasePositionDeltaWriter.java`

**修改目的**：将 `DeleteWriteResult` 中的 rewritten delete files 透传到最终的 `WriteResult`。

**工作逻辑**：在 `result()` 方法构建 `WriteResult` 的链式调用中，于 `.addReferencedDataFiles(...)` 之后追加一行 `.addRewrittenDeleteFiles(deleteWriteResult.rewrittenDeleteFiles())`，把 `DeleteWriteResult` 中的重写删除文件合并到 `WriteResult` 中。

### `core/src/main/java/org/apache/iceberg/io/DeleteWriteResult.java`

**修改目的**：在删除写入结果中承载 rewritten delete files 字段。

**工作逻辑**：
- 新增私有 final 字段 `List<DeleteFile> rewrittenDeleteFiles`。
- 修改原有 4 个构造函数，均将 `rewrittenDeleteFiles` 默认初始化为 `Collections.emptyList()`，保持向后兼容（即老的写入路径不会产出 rewritten delete files，但字段非 null，避免 NPE）。
- 新增第 5 个构造函数 `DeleteWriteResult(List<DeleteFile> deleteFiles, CharSequenceSet referencedDataFiles, List<DeleteFile> rewrittenDeleteFiles)`，显式接收 rewritten delete files。
- 新增访问器方法 `public List<DeleteFile> rewrittenDeleteFiles()`。

### `core/src/main/java/org/apache/iceberg/io/WriteResult.java`

**修改目的**：在写入结果中承载 rewritten delete files 字段，并提供 builder 支持。

**工作逻辑**：
- 新增字段 `private DeleteFile[] rewrittenDeleteFiles;`（数组形式以支持序列化）。
- 私有构造函数新增参数 `List<DeleteFile> rewrittenDeleteFiles`，并通过 `toArray(new DeleteFile[0])` 转成数组存储。
- 新增访问器 `public DeleteFile[] rewrittenDeleteFiles()`。
- 在内部 `Builder` 类中新增 `List<DeleteFile> rewrittenDeleteFiles` 字段，初始化为 `Lists.newArrayList()`。
- `Builder.add(WriteResult result)` 方法中追加 `addRewrittenDeleteFiles(result.rewrittenDeleteFiles)` 调用，实现多结果合并。
- 新增两个重载方法 `addRewrittenDeleteFiles(DeleteFile... files)` 与 `addRewrittenDeleteFiles(Iterable<DeleteFile> files)`，分别用 `Collections.addAll` 与 `Iterables.addAll` 累加到列表。
- `build()` 方法将 `rewrittenDeleteFiles` 一并传入构造函数。

## 小结

- **成效**：`WriteResult` 与 `DeleteWriteResult` 现可显式携带"被重写的 delete 文件"列表，与"新产出的 delete 文件"区分开，为后续删除文件重写、compaction 等场景提供元信息支撑。改动向后兼容（老构造路径默认空列表），并通过 revapi 豁免登记序列化变更。
- **影响范围**：核心 IO 层（`org.apache.iceberg.io` 包）的 3 个 Java 文件 + 1 个 revapi 配置文件，共新增约 47 行。
- **回迁到 1.4.x 的注意事项**：这是为后续 row-level rewrite 等 delete compaction 特性做铺垫的基础改动。**回迁必要性不高**——1.4.x 作为维护分支一般不引入新特性的中间基础设施，且该改动本身只是"加字段、加访问器"，单独回迁不会带来直接功能收益。如果 1.4.x 后续需要回迁与之相关的删除文件重写特性（例如 #11208 等），则需将本提交一并带回，否则无独立回迁价值。回迁时需注意：revapi 配置中对应的豁免规则也要同步带上，避免 API 检查失败；同时要核查 1.4.x 中是否已有调用 `WriteResult` 老构造路径的代码，新增字段默认空列表的设计保证不会破坏它们。
