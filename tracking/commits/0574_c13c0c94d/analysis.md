# 提交 0574：Spark 3.3/3.4 回port rewrite data files 的 output-spec-id 支持

## 提交信息

- **序号**：0574 / 4088
- **哈希**：c13c0c94daf9f6abc34cb109a1a4fd15c8a47a04
- **短哈希**：c13c0c94d
- **日期**：2024-03-08 23:29:09 -0800
- **作者**：Himadri Pal <mehimu@gmail.com>
- **提交说明**：Spark 3.4, 3.3 : Support output-spec-id in rewrite data files(#9901)(Backport #9803)
- **PR/Issue**：#9901（回port 主分支 PR #9803，对应主分支提交 `99b1d0ee4c8fb67d457c4d7a03065e4cb63af435`，在追踪编号体系中即 0564）
- **共同作者**：hpal <hpal@apple.com>

## 总体目的

把主分支（Spark 3.5）在 PR #9803 中引入的「rewrite data files 时指定输出分区 spec」能力，回port 到 Spark 3.3 和 Spark 3.4 两个旧版本模块。该能力允许用户通过 `output-spec-id` 选项显式指定重写后的数据文件应写入哪一个 PartitionSpec，从而支持「数据回退到旧 spec」「跨 spec 重写整理」等场景。

背景动机：
- Iceberg 表在演化过程中会产生多个 PartitionSpec（每次 `updateSpec` 都会创建一个新 spec 并保留历史 spec）。数据文件用 `specId` 标识它属于哪个 spec。
- 当表变更分区策略后，旧 spec 下的数据文件仍然存在；用户在做 rewrite data files 时，往往希望把整理后的文件回写到「当前 spec」，但有时也希望写到「某个历史 spec」（例如回退一次不成功的 spec 演进，或把多个 spec 的数据统一整理到指定的 spec）。
- 在 #9803 之前，rewrite data files 始终写入当前 `table.spec()`，没有选项可以让用户选择目标 spec。#9803 在 core/api 层引入了 `output-spec-id` 选项以及对应的 `SizeBasedFileRewriter.outputSpecId()/outputSpec()` 抽象，并在 Spark 3.5 模块完成适配。
- 本 PR #9901 把同样的 Spark 模块适配工作迁移到 Spark 3.4 与 3.3 两个仍在维护的旧分支，让这两个版本的用户也能用 `output-spec-id`。

## 如何达成设计目的

### 整体设计思路

`output-spec-id` 的能力分两层：

1. **Core/API 层**（已由 #9803 在主分支落地，本 PR 不再改动）：
   - `api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java` 增加常量 `String OUTPUT_SPEC_ID = "output-spec-id"`；
   - `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java` 增加 `private int outputSpecId` 字段、`outputSpecId(Map options)` 解析与校验方法（默认值为 `table.spec().specId()`，并通过 `table.specs().containsKey(specId)` 校验存在性，否则抛 `IllegalArgumentException`），以及 `protected PartitionSpec outputSpec()` 与 `protected int outputSpecId()` 两个供子类访问的钩子。
2. **Spark 层**（本 PR 改动）：把 `outputSpecId()` 透传给 Spark 写入选项 `SparkWriteOptions.OUTPUT_SPEC_ID`，并基于 `outputSpec()` 替代 `table().spec()` 来决定是否需要 repartition / 重排，同时引入新的 `sortSchema()` 钩子让 Z-Order 这种带计算列的 sort 也能正确生成 sort order。

### 关键设计点

- **为什么需要 `outputSpec()` 而不是直接用 `table().spec()`**：原本各 rewriter 在判断「输入 spec 与输出 spec 是否一致」时直接拿 `table().spec()`（即当前表的 spec），但在指定了 `output-spec-id` 后，输出 spec 可能不是当前 spec，而是 `table.specs().get(outputSpecId)`。因此所有「比较 spec 一致性」与「构造 sort order」的逻辑都要从「表当前 spec」切换到「目标输出 spec」。
- **为什么引入 `sortSchema()`**：默认实现返回 `table().schema()`。对 SORT rewriter 这够用；但对 Z-Order rewriter，它需要在 sort 时引入额外的计算列（如 `Z_COLUMN` 与相关 `Z_SCHEMA` 字段），这些字段不在表 schema 中。原代码 `SortOrderUtil.buildSortOrder(table(), sortOrder)` 在内部用 `table.schema()` + `table.spec()` 构造 sort order；要支持任意输出 spec，并允许扩展 schema，就要把 `(schema, spec, sortOrder)` 三者都显式传入。新的 `SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder())` 调用就是为此服务——`sortSchema()` 在 Z-Order 中返回「表 schema + Z_SCHEMA 计算列」的合并 schema。
- **抽象方法 `sortOrder()` 在 3.3 上是新加的**：3.3 模块的 `SparkShufflingDataRewriter` 之前没有把 `sortOrder()` 抽象出来，本 PR 顺手补上 `protected abstract SortOrder sortOrder();`，让 `outputSortOrder()` 可以从子类拿到具体 sortOrder，与 3.4/3.5 模块对齐。
- **校验与提示**：core 层 `outputSpecId(options)` 在 spec id 不存在时抛 `IllegalArgumentException`，消息为 `Cannot use output spec id %s because the table does not contain a reference to this spec-id.`，对应新增测试 `testBinpackRewriteWithInvalidOutputSpecId` 验证。
- **`RewriteDataFilesSparkAction` 选项注册**：在 `ADDITIONAL_VALID_OPTIONS` 集合中追加 `OUTPUT_SPEC_ID`，让 `output-spec-id` 成为合法的可配置选项（否则会被选项校验拒绝）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`
与 `spark/v3.3/.../RewriteDataFilesSparkAction.java` 同步改动。

**修改目的**：把 `OUTPUT_SPEC_ID` 加入 action 接受的额外合法选项集合。

**工作逻辑**：在 `ADDITIONAL_VALID_OPTIONS` 不可变集合中追加 `OUTPUT_SPEC_ID`，与既有的 `PARTIAL_PROGRESS_MAX_COMMITS`、`TARGET_FILE_SIZE_BYTES`、`USE_STARTING_SEQUENCE_NUMBER`、`REWRITE_JOB_ORDER` 并列，使 `validateAndInitOptions` 校验时不再拒绝 `output-spec-id`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkBinPackDataRewriter.java`
与 3.3 版同步。

**修改目的**：让 BinPack rewriter 把 `outputSpecId` 透传到 Spark 写入侧，并基于目标 spec 决定是否需要 repartition。

**工作逻辑**：
- 在 `.option(...)` 链中追加 `.option(SparkWriteOptions.OUTPUT_SPEC_ID, outputSpecId())`，让 `DataFrameWriter` 把目标 spec id 带到 `SparkWrite`，写入时按此 spec 落盘。
- `distributionMode(List<FileScanTask> group)` 中把 `requiresRepartition` 的判断从 `!group.get(0).spec().equals(table().spec())` 改为 `!group.get(0).spec().equals(outputSpec())`：当输入文件 spec 与目标输出 spec 不一致时启用 RANGE 分布（强制 shuffle），否则 NONE（无需 shuffle）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingDataRewriter.java`
与 3.3 版基本同步，但接口签名略有差异（3.4 用 `Function<Dataset<Row>, Dataset<Row>> sortFunc`，3.3 直接传 `List<FileScanTask> group`）。

**修改目的**：让 Shuffle 系列 rewriter（被 SORT / Z-Order 继承）支持自定义输出 spec，并引入 `sortSchema()` 钩子让带计算列的 sort（Z-Order）能正确扩展 schema。

**工作逻辑**：
- 新增 `protected Schema sortSchema()` 方法，默认实现 `return table().schema();`，子类（Z-Order）可覆盖以追加计算列。
- 在 `.option(...)` 链中追加 `.option(SparkWriteOptions.OUTPUT_SPEC_ID, outputSpecId())`，与 BinPack 保持一致。
- 重写 `outputSortOrder(...)` 的实现：
  - 3.4 中方法签名为 `private org.apache.iceberg.SortOrder outputSortOrder(List<FileScanTask> group)`；3.3 中为 `protected org.apache.iceberg.SortOrder outputSortOrder(List<FileScanTask> group, org.apache.iceberg.SortOrder sortOrder)`（接收外部传入的 sortOrder）。
  - 把判断变量从 `includePartitionColumns` 重命名为 `requiresRepartitioning`，更清晰表达语义。
  - 取 `PartitionSpec spec = outputSpec();`，比较 `!group.get(0).spec().equals(spec)` 判断是否需要 repartition。
  - 若需要 repartition，调用 `SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder())`（3 参数版本），把分区列按目标 spec 加入 sort order；否则直接返回原 `sortOrder`。原实现 `SortOrderUtil.buildSortOrder(table(), sortOrder())`（2 参数版本）只能用当前表 schema/spec，无法支持自定义输出 spec 与扩展 schema。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingDataRewriter.java`
3.3 版多一处额外改动：新增 `protected abstract org.apache.iceberg.SortOrder sortOrder();` 抽象方法声明。

**修改目的**：3.3 模块此前没有把 `sortOrder()` 抽象出来，本 PR 借回 port 之机补齐该抽象方法，使 `outputSortOrder(group, sortOrder)` 可以从子类取到具体 sortOrder（与 3.4/3.5 模块对齐）。该方法在 3.4 上已存在，所以 3.4 diff 里看不到这一行。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/SparkSortDataRewriter.java`
3.3 独有改动。

**修改目的**：实现 `sortOrder()` 抽象方法以提供 SORT rewriter 的 sort order。

**工作逻辑**：新增 `@Override protected SortOrder sortOrder() { return sortOrder; }`，把类内已有的 `sortOrder` 字段（由 SortStrategy 配置）暴露给父类使用。3.4 上此方法此前已存在，因此 3.4 diff 中没有此修改。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderDataRewriter.java`
与 3.3 版基本同步。

**修改目的**：让 Z-Order rewriter 适配新的 `sortSchema()` 钩子，把 Z_ORDER 计算列纳入 sort schema，并暴露 `sortOrder()`。

**工作逻辑**：
- 实现 `@Override protected SortOrder sortOrder() { return Z_SORT_ORDER; }`（3.4 上 `sortOrder()` 抽象方法此前已存在，所以这里其实是补实现；3.3 上同时补抽象方法声明 + 实现）。
- 重写 `sortSchema()`：返回一个新 `Schema`，由 `table().schema().columns()` 与 `Z_SCHEMA.columns()` 合并而成。`Z_SCHEMA` 是 Z-Order rewriter 内部定义的、用于排序的 z-value 计算列集合（不在表 schema 中持久化）。这样 `SortOrderUtil.buildSortOrder(sortSchema(), spec, sortOrder())` 在构造 sort order 时就能拿到包含 z-value 列的完整 schema，从而正确生成 sort order。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`
与 3.3 版同步。

**修改目的**：覆盖 BinPack / Sort / ZOrder 三类 rewriter 在指定 `output-spec-id` 时的行为，以及非法 spec id 的错误处理。

**工作逻辑**：新增 5 个测试方法 + 2 个辅助方法：
- `testBinPackRewriterWithSpecificUnparitionedOutputSpec()`：先建表（10 文件），记录原 unpartitioned spec id；随后给表加上 `truncate(c2, 2)` 分区（产生新 spec）；用 `output-spec-id` 指回原来的 unpartitioned spec 进行 BinPack 重写。断言重写字节数等于原数据量、行数不变、所有重写文件 `specId == outputSpecId`，且分区类型与目标 spec 一致。
- `testBinPackRewriterWithSpecificOutputSpec()`：类似上面，但演进两次 spec（先 `truncate(c2, 2)`，再 `bucket(c3, 2)`），用 `output-spec-id` 指向第一次演进的 spec（即「回退」到中间 spec）。验证逻辑同上。
- `testBinpackRewriteWithInvalidOutputSpecId()`：传入不存在的 spec id（1234），断言抛出 `IllegalArgumentException`，消息精确匹配 `Cannot use output spec id 1234 because the table does not contain a reference to this spec-id.`。
- `testSortRewriterWithSpecificOutputSpecId()`：演化两次 spec 后，用 `output-spec-id` 指向中间 spec，并指定 SORT order (`c2 asc, c3 asc`) 进行 sort rewrite，验证重写后文件落到了目标 spec。
- `testZOrderRewriteWithSpecificOutputSpecId()`：演化两次 spec 后，用 `output-spec-id` 指向中间 spec，zOrder `c2, c3` 进行重写，验证目标 spec。
- 辅助方法 `shouldRewriteDataFilesWithPartitionSpec(Table table, int outputSpecId)`：扫描当前表所有数据文件，断言 `file.specId() == outputSpecId`，且 `((PartitionData) file.partition()).getPartitionType()` 与 `table.specs().get(outputSpecId).partitionType()` 一致。
- 辅助方法 `currentDataFiles(Table table)`：用 `table.newScan().planFiles()` 收集所有 `FileScanTask` 的 `DataFile`，返回列表。

### 3.3 vs 3.4 实现差异总结

- 3.3 `SparkShufflingDataRewriter.sortedDF` 签名：`sortedDF(Dataset<Row> df, List<FileScanTask> group)`；3.4：`sortedDF(Dataset<Row> df, Function<Dataset<Row>, Dataset<Row>> sortFunc)`。
- 3.3 `outputSortOrder(List<FileScanTask> group, SortOrder sortOrder)` 接收外部 sortOrder；3.4 `outputSortOrder(List<FileScanTask> group)` 内部通过 `sortOrder()` 取得。
- 3.3 上 `SparkShufflingDataRewriter.sortOrder()` 是本 PR 新增的抽象方法声明；3.4 上已存在。
- 3.3 上 `SparkSortDataRewriter.sortOrder()` 实现是新加的；3.4 上已存在。
- 测试用例的引入 import（如 `PartitionData`、`Assertions`）两个版本完全一致，测试体本身也几乎逐字相同。

## 小结

- **成效**：把 #9803 的能力对称地补齐到 Spark 3.3 / 3.4 两个旧版本模块，让这两个版本用户也能用 `output-spec-id` 控制重写后的目标分区 spec；同时借回port 把 3.3 上缺失的 `sortOrder()` 抽象方法补齐，使三个版本在 rewriter 抽象层次上趋于一致，便于后续同步。
- **影响范围**：仅影响 Spark 3.3 / 3.4 模块的 rewrite data files 行为，新增了一个可选项（默认行为不变——`outputSpecId` 默认值为 `table.spec().specId()`，与回 port 前的写入目标一致）。core/api 模块已由 #9803 在主分支落地，本 PR 不重复修改。
- **回迁到 1.4.x 注意事项**：
  1. 本 PR 是 backport，回迁到 1.4.x 时必须确保 1.4.x 上 #9803 的 core/api 层改动（`RewriteDataFiles.OUTPUT_SPEC_ID` 常量、`SizeBasedFileRewriter.outputSpec()/outputSpecId()`）已存在，否则会编译失败。如果 1.4.x 分支本身没有 #9803，需要把 #9803 一起回迁（或仅回迁 core/api 部分与本 PR 配套）。
  2. 1.4.x 上的 Spark 模块（3.3 / 3.4）类结构与本 PR 基于的状态需要一致；如果 1.4.x 落后于 main 的 3.3 / 3.4 模块结构（例如 `SparkShufflingDataRewriter` 已有差异），需要做小幅适配，特别注意 3.3 上是否已有 `sortOrder()` 抽象方法。
  3. 测试依赖 `Expressions.truncate`、`Expressions.bucket`、`SortOrder.builderFor`、`PartitionData.getPartitionType()` 等已存在 API，1.4.x 上应保持兼容。
  4. 注意关联说明：本提交（0574）与 0564（即主分支 `99b1d0ee4`，PR #9803）是同一特性跨 Spark 版本的两次落地。在追踪表里应标记两者为「同一特性、不同 Spark 版本」关系；如果 1.4.x 决定只支持 Spark 3.3/3.4，可只回迁 0574；若 1.4.x 还支持 Spark 3.5（即包含 main 路径），则 0564 与 0574 都要回迁。
