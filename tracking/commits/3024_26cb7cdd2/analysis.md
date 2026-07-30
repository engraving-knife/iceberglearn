# 提交 3024：Flink: Backport RewriteDataFiles add max file group count (#14861)

## 提交信息

- **序号**：3024 / 4088
- **哈希**：26cb7cdd2eff04b5cc7a88b899ee37e7d7f32f18
- **短哈希**：26cb7cdd2
- **日期**：2025-12-17
- **作者**：GuoYu
- **提交说明**：Flink: Backport RewriteDataFiles add max file group count (#14861)
- **PR/Issue**：#14861（回移自 #14837）

## 总体目的

本提交将 PR #14837（提交 3019）中新增的"RewriteDataFiles max file group count"功能从 Flink v2.1 模块回移（backport）到 Flink v1.20 和 v2.0 两个旧版本模块。PR #14837 原始提交在 Core 模块（`BinPacking.java`、`SizeBasedFileRewritePlanner.java`）和 Flink v2.1 模块中实现了该功能，但由于 Core 模块是所有 Flink 版本共享的，Core 侧的改动（BinPacking 算法新增 maxItemsPerBin、SizeBasedFileRewritePlanner 新增配置项、TestBinPacking 测试、文档）已经通过 PR #14837 自动惠及所有 Flink 版本。然而，Flink 版本特有的 API 代码（`RewriteDataFiles.Builder`、测试工具类、测试用例）需要分别为每个支持的 Flink 版本单独回移。

Iceberg 同时维护多个 Flink 版本绑定（v1.20、v2.0、v2.1），每个版本有独立的 `flink/v{version}/` 模块。当新功能涉及 Flink API 层面的改动时，需要确保所有受支持的 Flink 版本都能使用该功能。本提交补齐了 v1.20 和 v2.0 两个版本缺失的 Flink 侧改动，使这两个版本的用户也能通过 `maxFileGroupInputFiles` 配置项限制每个文件组的最大输入文件数，从而避免超大分区因文件数过多导致重写任务资源溢出。

回移的改动内容与 PR #14837 中 Flink v2.1 模块的改动完全一致，是逐文件复制。

## 如何达成设计目的

将 Flink v2.1 模块中已验证的三组文件（`RewriteDataFiles.java` 的 Builder 方法、`RewriteUtil.java` 的测试工具重构、`TestDataFileRewritePlanner.java` 的测试用例）逐一复制到 v1.20 和 v2.0 模块的对应路径下。由于三个 Flink 版本的代码结构完全一致，回移过程不涉及任何适配修改。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+13/-0 lines)

**修改目的**：在 Flink v1.20 的 RewriteDataFiles Builder 中暴露 `maxFileGroupInputFiles` 配置项。

**工作逻辑**：
新增 `maxFileGroupInputFiles(long maxFileGroupInputFiles)` 方法，将值转为字符串后写入 `rewriteOptions` map（key 为 `SizeBasedFileRewritePlanner.MAX_FILE_GROUP_INPUT_FILES`）。与 v2.1 中的实现完全一致，Javadoc 引用 `SizeBasedFileRewritePlanner#MAX_FILE_GROUP_INPUT_FILES` 说明语义。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+7/-1 lines)

**修改目的**：重构 v1.20 测试工具方法以支持自定义重写选项。

**工作逻辑**：
将 `planDataFileRewrite(TableLoader)` 改为委托到新方法 `planDataFileRewrite(TableLoader, Map<String, String>)`，原方法使用 `ImmutableMap.of(MIN_INPUT_FILES, "2")` 作为默认选项。新增 `Map` import。与 v2.1 中的重构完全一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+20/-0 lines)

**修改目的**：为 v1.20 添加 max file group count 功能测试。

**工作逻辑**：
新增 `testMaxFileGroupCount` 测试，创建分区表插入 p1（2 文件）和 p2（4 文件），验证不限制时产生 2 个文件组，设置 `MAX_FILE_GROUP_INPUT_FILES=2` 时产生 3 个文件组。与 v2.1 中的测试完全一致。同时新增 `MAX_FILE_GROUP_INPUT_FILES` 的 static import。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+13/-0 lines)

**修改目的**：在 Flink v2.0 的 RewriteDataFiles Builder 中暴露 `maxFileGroupInputFiles` 配置项。

**工作逻辑**：
与 v1.20 中的改动完全一致，新增 `maxFileGroupInputFiles` Builder 方法。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+7/-1 lines)

**修改目的**：重构 v2.0 测试工具方法以支持自定义重写选项。

**工作逻辑**：
与 v1.20 中的重构完全一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+20/-0 lines)

**修改目的**：为 v2.0 添加 max file group count 功能测试。

**工作逻辑**：
与 v1.20 中的测试完全一致。

## 总结

本提交将 PR #14837 的 max file group count 功能从 Flink v2.1 回移到 v1.20 和 v2.0 两个受支持的 Flink 版本，确保所有 Flink 用户都能使用该功能限制文件组内的最大输入文件数。回移内容为 Flink 版本特有的 API 和测试代码（Core 模块改动已通过原始 PR 自动共享），改动与原始 PR 逐文件一致，无适配差异，体现了 Iceberg 多 Flink 版本维护的一致性策略。
