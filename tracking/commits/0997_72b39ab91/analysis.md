# 提交 0997：Flink: backport PR #10748 for limit pushdown (#10813)

## 提交信息

- **序号**：0997 / 4088
- **哈希**：72b39ab91dfa04d713552e70b009c24510a1cd07
- **短哈希**：72b39ab91
- **日期**：2024-07-30（Tue Jul 30 14:49:44 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: backport PR #10748 for limit pushdown (#10813)
- **PR/Issue**：#10813（回迁自 #10748，即提交 0992）

## 总体目的

提交 0992（PR #10748）为 Flink 1.19 模块的 FLIP-27 source 实现了 limit 下推能力，但未覆盖 Flink 1.17 与 1.18 模块。Iceberg 同时维护多个 Flink 版本绑定模块（1.17/1.18/1.19），为保证各版本行为一致，需要把同一功能回迁到 1.17 与 1.18 模块。

本提交即该回迁操作，把 #10748 在 1.19 上的全部改动等价应用到 `flink/v1.17` 与 `flink/v1.18` 两个模块，使三个 Flink 版本的 FLIP-27 source 都支持 limit 下推。这避免了不同 Flink 版本间出现功能差异，降低用户在升级 Flink 版本时的行为不一致风险。

## 如何达成设计目的

将 0992 中的四个主代码文件与两个测试文件原样复制到 v1.17 与 v1.18 对应路径下。三个 Flink 模块的 FLIP-27 source 代码在此部分完全一致（`IcebergSource`、`RowDataReaderFunction` 等签名相同），因此回迁无需任何适配性修改，属于纯文件复制+路径调整。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java` 与 v1.18 同名文件

**修改目的**：将 `context.limit()` 透传给 `RowDataReaderFunction` 构造调用。

**工作逻辑**：与 0992 中 v1.19 的改动一致：在构造 `RowDataReaderFunction` 处追加 `context.limit()` 参数。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/LimitableDataIterator.java`（新增，v1.18 同）

**修改目的**：提供带 limit 的数据迭代器。

**工作逻辑**：继承 `DataIterator<T>`，在 `hasNext()` 中先检查 `RecordLimiter.reachedLimit()`，达到上限则返回 false 提前终止；`next()` 中递增计数器。内容与 0992 的 v1.19 版本完全相同。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/RecordLimiter.java`（新增，v1.18 同）

**修改目的**：线程安全的记录计数器与 limit 判定。

**工作逻辑**：基于 `AtomicLong`，`reachedLimit()` 仅在 `limit > 0` 且计数达阈值时为 true，提供 `create(long)` 工厂。标注 `@Internal`。与 0992 的 v1.19 版本完全相同。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowDataReaderFunction.java`（v1.18 同）

**修改目的**：让 reader function 支持 limit。

**工作逻辑**：新增 `long limit` 字段与 `transient RecordLimiter` 字段；保留旧构造函数（委托新构造函数传 `-1L`）；新增带 `limit` 参数的构造函数；`createDataIterator` 改用 `LimitableDataIterator` 与 `lazyLimiter()`（懒初始化以规避序列化）。与 0992 的 v1.19 版本完全相同。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceConfig.java`（v1.18 同）

**修改目的**：启用 FLIP-27 limit 下推测试。

**工作逻辑**：移除 `assumeThat(useFlip27Source).isFalse()` 跳过逻辑与 TODO 注释，补充说明注释。与 0992 的 v1.19 版本完全相同。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestLimitableDataIterator.java`（新增，v1.18 同）

**修改目的**：`LimitableDataIterator` 的参数化单元测试。

**工作逻辑**：构造 6 条记录，`@ValueSource(longs = {-1L, 0L, 1L, 6L, 7L})` 验证不同 limit 下的读取条数。与 0992 的 v1.19 版本完全相同。

## 小结

- **成效**：Flink 1.17 与 1.18 模块也获得了 FLIP-27 source 的 limit 下推能力，三个 Flink 版本模块行为统一。
- **影响范围**：Flink 1.17 与 1.18 两个模块，共 12 个文件（每模块 6 个，含 4 个新增主/测试类、2 个修改），456 insertions / 14 deletions。无公共 API 破坏。
- **回迁到 1.4.x 的注意事项**：本提交本身就是向旧 Flink 模块的回迁，与 0992 配套构成完整功能。1.4.x 分支若仍维护 Flink 1.17/1.18 模块且尚未具备该功能，可直接回迁本提交（及 0992 的 v1.19 部分）。由于改动是纯文件复制、无版本相关适配，回迁风险低。需注意保留 `RecordLimiter` 的 `transient` + 懒加载设计以避免序列化问题；并确认 1.4.x 的 `TestFlinkSourceConfig` 中 `assumeThat` 跳过逻辑存在与否，以便正确合并。
