# 提交 0274：Core: Remove deprecated classes related to rewrite data files (#9296)

## 提交信息

- **序号**：0274 / 4088
- **哈希**：09290c58c5257078641367a76fa3c12aaf1ebc9b
- **短哈希**：09290c58c
- **日期**：2023-12-14 17:11:19 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Core: Remove deprecated classes related to rewrite data files (#9296)
- **PR/Issue**：#9296

## 总体目的

本提交移除 Iceberg core 模块中三个早已标记为 `@Deprecated` 的"旧版数据文件重写策略"类：`RewriteStrategy` 接口、`BinPackStrategy` 抽象类、`SortStrategy` 抽象类，以及对应的两个 core 测试 `TestBinPackStrategy`、`TestSortStrategy`。这三个类自 1.3.0 起被弃用，原 javadoc 明确写道"will be removed in 1.4.0"且附有一条关键约束："This can only be removed once Spark 3.2 isn't using this API anymore."

背景是 Iceberg 在 1.3.0 引入了新的 `FileRewriter` / `SizeBasedFileRewriter` / `SizeBasedDataRewriter` API 来取代旧的 `RewriteStrategy` 体系。新 API 把"选择文件 → 分组 → 重写"的流程拆分为更细粒度的 `FileRewriter` 接口，并把"按大小重写"的具体实现收敛到 `SizeBasedFileRewriter`，使重写逻辑可以独立于具体的 Action 框架（Spark/Flink 等）复用。但旧 API 的三个类仍然存在于 core 中，因为 spark/v3.2 模块（在 commit 0271 中刚刚被整体删除）还在直接使用它们。

因此本提交与 commit 0271 存在严格的依赖关系：0271 移除了 Spark 3.2 模块，从而消除了最后一个引用旧 `RewriteStrategy` API 的引擎代码；本提交紧随其后清理 core 中已无引用的弃用类，兑现 1.3.0 给出的"1.4.0 移除"承诺，避免弃用代码长期堆积。同时本提交还顺手把 spark/v3.3 模块下仍在用旧 API 常量的 `TestRewriteDataFilesAction` 与 `IcebergSortCompactionBenchmark` 迁移到新 API，并在 `.palantir/revapi.yml` 中登记这些类的移除为可接受的二进制不兼容变更。

## 如何达成设计目的

整体设计分四步：第一，删除 core 模块下三个弃用类（`RewriteStrategy`、`BinPackStrategy`、`SortStrategy`）以及两个对应的 core 测试（`TestBinPackStrategy`、`TestSortStrategy`）；第二，把 spark/v3.3 模块下 `TestRewriteDataFilesAction` 与 `IcebergSortCompactionBenchmark` 中所有对旧常量的引用（`BinPackStrategy.MIN_INPUT_FILES`、`SortStrategy.REWRITE_ALL` 等）迁移到新 API 的等价常量（`SizeBasedFileRewriter.MIN_INPUT_FILES`、`SizeBasedFileRewriter.REWRITE_ALL` 等），其中 `DELETE_FILE_THRESHOLD` 被进一步收敛到 `SizeBasedDataRewriter`；第三，在 `.palantir/revapi.yml` 的 `1.4.0` 段登记三个类的移除为"Removing deprecated code"，使 revapi API 兼容性检查不再因这次移除而报错。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：把三个弃用类的移除登记为可接受的 API break，避免 revapi 兼容性检查失败。

**工作逻辑**：在 `acceptedBreaks` 的 `1.4.0` 段下新增三条 `java.class.removed` 条目，分别对应 `org.apache.iceberg.actions.BinPackStrategy`、`org.apache.iceberg.actions.SortStrategy`、`org.apache.iceberg.actions.RewriteStrategy`，justification 统一为 "Removing deprecated code"。revapi 是 Palantir 维护的二进制/源码兼容性检查工具，会在 PR CI 中比对 `iceberg-core` 的公共 API；这里显式接受这三处移除，让 1.4.0 的发布可以合法地不含这三个类。

### `core/src/main/java/org/apache/iceberg/actions/RewriteStrategy.java`（删除，78 行）

**修改目的**：移除已弃用的 `RewriteStrategy` 接口。

**工作逻辑**：该接口是旧版"重写策略"抽象，定义了 `name()`、`table()`、`validOptions()`、`options(Map)`、`selectFilesToRewrite(Iterable<FileScanTask>)`、`planFileGroups(Iterable<FileScanTask>)`、`rewriteFiles(List<FileScanTask>)` 等方法，把"选文件、分组、重写"三步揉在一个接口里。javadoc 标注 `@deprecated since 1.3.0, will be removed in 1.4.0; use FileRewriter instead`。随着 Spark 3.2 模块被移除，core 中不再有任何代码引用该接口，可以安全删除。

### `core/src/main/java/org/apache/iceberg/actions/BinPackStrategy.java`（删除，333 行）

**修改目的**：移除已弃用的 `BinPackStrategy` 抽象类。

**工作逻辑**：`BinPackStrategy implements RewriteStrategy` 是旧版"按大小装箱"重写策略的基类，定义了大量公共常量与默认值，包括：
- `MIN_INPUT_FILES` / `MIN_INPUT_FILES_DEFAULT = 5`：文件组参与重写的最小文件数；
- `MIN_FILE_SIZE_BYTES` / `MIN_FILE_SIZE_DEFAULT_RATIO = 0.75d`：小于该阈值的文件被视为重写候选，默认为目标文件大小的 75%；
- `MAX_FILE_SIZE_BYTES` / `MAX_FILE_SIZE_DEFAULT_RATIO = 1.80d`：大于该阈值的文件被视为重写候选，默认为目标文件大小的 180%；
- `DELETE_FILE_THRESHOLD` / `DELETE_FILE_THRESHOLD_DEFAULT = Integer.MAX_VALUE`：当一个数据文件关联的删除文件数达到该阈值时强制重写，默认关闭；
- `REWRITE_ALL`：是否无视大小判断全部重写。

它还实现了 `selectFilesToRewrite`、`planFileGroups` 等基于 `BinPacking.ListPacker` 的装箱逻辑。这些能力在 1.3.0 起被 `SizeBasedFileRewriter` 取代——新类把同样的常量与逻辑搬到 `FileRewriter` 框架下，与 Spark/Flink 等具体 Action 解耦。删除该类是兑现弃用承诺。

### `core/src/main/java/org/apache/iceberg/actions/SortStrategy.java`（删除，97 行）

**修改目的**：移除已弃用的 `SortStrategy` 抽象类。

**工作逻辑**：`SortStrategy extends BinPackStrategy` 在 binpack 基础上增加按 `SortOrder` 重排数据文件的能力，`name()` 返回 `"SORT"`，并允许通过 `sortOrder(SortOrder)` 设置排序规则。它复用 `BinPackStrategy` 的所有阈值常量。javadoc 标注 `@deprecated since 1.3.0, will be removed in 1.4.0; use SizeBasedFileRewriter instead`，明确指向 `SizeBasedFileRewriter` 作为替代。新 API 下排序重写由 `SortOrderWriters` / `SortingFileWriter` 配合 `FileRewriter` 实现，不再需要这个独立的 strategy 抽象。

### `core/src/test/java/org/apache/iceberg/actions/TestBinPackStrategy.java`（删除，385 行）

**修改目的**：移除针对已删除 `BinPackStrategy` 的测试类。

**工作逻辑**：该测试类继承 `TableTestBase`，使用 `MockFileScanTask` 等手段对 `BinPackStrategy` 的文件选择、分组、阈值判断等行为进行单元测试。被测类已删除，测试随之删除。新 API 下等价的覆盖由 `TestSizeBasedFileRewriter` 等测试提供。

### `core/src/test/java/org/apache/iceberg/actions/TestSortStrategy.java`（删除，154 行）

**修改目的**：移除针对已删除 `SortStrategy` 的测试类。

**工作逻辑**：与 `TestBinPackStrategy` 类似，专门测试 `SortStrategy` 的行为。被测类已删除，测试随之删除。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：把 Spark 3.3 模块下 `TestRewriteDataFilesAction` 中对旧 `BinPackStrategy` / `SortStrategy` 常量的引用迁移到新 API 的 `SizeBasedFileRewriter` / `SizeBasedDataRewriter`。

**工作逻辑**：该文件原 import 了 `org.apache.iceberg.actions.BinPackStrategy` 与 `org.apache.iceberg.actions.SortStrategy`，并在多个 `@Test` 方法中通过 `.option(BinPackStrategy.MIN_INPUT_FILES, "1")`、`.option(SortStrategy.REWRITE_ALL, "true")`、`.option(BinPackStrategy.MAX_FILE_SIZE_BYTES, ...)` 等方式设置重写选项。本提交把 import 替换为 `SizeBasedFileRewriter` 与 `SizeBasedDataRewriter`，并把所有常量引用做对应替换：
- `BinPackStrategy.MIN_INPUT_FILES` → `SizeBasedFileRewriter.MIN_INPUT_FILES`
- `BinPackStrategy.MIN_FILE_SIZE_BYTES` → `SizeBasedFileRewriter.MIN_FILE_SIZE_BYTES`
- `BinPackStrategy.MAX_FILE_SIZE_BYTES` → `SizeBasedFileRewriter.MAX_FILE_SIZE_BYTES`
- `BinPackStrategy.REWRITE_ALL` → `SizeBasedFileRewriter.REWRITE_ALL`
- `BinPackStrategy.DELETE_FILE_THRESHOLD` → `SizeBasedDataRewriter.DELETE_FILE_THRESHOLD`（注意：常量从 `BinPackStrategy` 迁移到了 `SizeBasedDataRewriter`，因为删除文件阈值在语义上更贴近"按数据删除"而非"按文件大小装箱"）
- `SortStrategy.*`（`MIN_INPUT_FILES`、`REWRITE_ALL`、`MAX_FILE_SIZE_BYTES`）→ 同样替换为 `SizeBasedFileRewriter.*`，因为 `SortStrategy` 继承自 `BinPackStrategy`，常量本质相同。

替换涉及约 20 处 `.option(...)` 调用，覆盖 `dataFilesBinPack`、`dataFilesWithSortOrder`、`zOrder` 等多个测试用例。注意 spark/v3.4 与 spark/v3.5 的同名测试无需修改——它们已经基于新 API 编写，这也说明 v3.3 是最后一个仍引用旧 API 的 Spark 模块。

### `spark/v3.3/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergSortCompactionBenchmark.java`

**修改目的**：把 Spark 3.3 模块下 JMH 基准测试中对 `BinPackStrategy.REWRITE_ALL` 的引用迁移到 `SizeBasedFileRewriter.REWRITE_ALL`。

**工作逻辑**：该基准测试用于衡量排序压缩性能，每个 `@Benchmark` 方法（`sortInt`、`sortString`、`sortFourColumns`、`zSortInt` 等）都会调用 `SparkActions.get().rewriteDataFiles(table).option(BinPackStrategy.REWRITE_ALL, "true").sort(...).execute()`。本提交把 import 从 `BinPackStrategy` 改为 `SizeBasedFileRewriter`，并把所有 `BinPackStrategy.REWRITE_ALL` 替换为 `SizeBasedFileRewriter.REWRITE_ALL`。逻辑保持不变，只是常量来源类更换。

## 小结

本提交兑现了 1.3.0 给出的弃用承诺，删除了 core 模块中三个早已弃用的重写策略类（`RewriteStrategy`、`BinPackStrategy`、`SortStrategy`）及对应测试，并把 spark/v3.3 下仍引用旧常量的测试与基准测试迁移到新 API（`SizeBasedFileRewriter` / `SizeBasedDataRewriter`），同时在 revapi 配置中登记 API break。它与紧邻的 commit 0271（移除 Spark 3.2）构成一组连续的"先解除引用、再删除被引用代码"的清理动作，使 1.4.0 的核心 API 更精简，重写数据文件的能力完全由新的 `FileRewriter` 框架承载。
