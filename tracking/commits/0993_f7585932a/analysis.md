# 提交 0993：Flink: support limit pushdown in FLIP-27 source (#10748)

## 提交信息

- **序号**：0993 / 4088
- **哈希**：f7585932a6d89b04c0d45b5f9dfe6f45483efd0b
- **短哈希**：f7585932a
- **日期**：2024-07-29（Mon Jul 29 15:35:18 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: support limit pushdown in FLIP-27 source (#10748)
- **PR/Issue**：#10748

## 总体目的

Iceberg 的 Flink 集成提供两套 source：基于旧 `TableSource` 的 source，以及基于 FLIP-27 的现代 `Source` API 实现（`IcebergSource`）。Flink 读选项 `FlinkReadOptions.LIMIT_OPTION` 用于限制读取的记录数。在旧 source 中 limit 下推已经可用，但 FLIP-27 source 此前并未实现 limit 下推——对应的测试 `TestFlinkSourceConfig.testReadOptionHierarchy` 甚至用 `assumeThat(useFlip27Source).isFalse()` 显式跳过了 FLIP-27 场景，并留下 TODO 注释 `FLIP-27 source doesn't implement limit pushdown yet`。

这意味着使用 FLIP-27 source 时，即便设置了 `limit`，读取器仍会扫描并返回全量数据，造成不必要的 I/O 与计算开销，尤其是在带 limit 的探查查询（如 `SELECT * FROM t LIMIT 1`）场景下体验较差。

本提交为 FLIP-27 source 增加 limit 下推能力：当用户配置了 limit 时，reader 在达到该上限后提前停止读取，从而避免读取多余数据。

## 如何达成设计目的

设计上采用"装饰器 + 计数器"的轻量方案：

1. 新增 `RecordLimiter`：一个基于 `AtomicLong` 的线程安全计数器，记录已读取条数，并在达到阈值时判定 `reachedLimit()`。limit <= 0 视为不限制。
2. 新增 `LimitableDataIterator`：继承已有的 `DataIterator`，在 `hasNext()` 中先检查是否达到 limit（达到则返回 false 提前终止），在 `next()` 中递增计数器。
3. 修改 `RowDataReaderFunction`：增加一个带 `limit` 参数的构造函数；`createDataIterator` 改为返回 `LimitableDataIterator`。为避免 `RecordLimiter` 的可序列化问题，采用 `lazyLimiter()` 懒初始化。
4. 修改 `IcebergSource`：构造 `RowDataReaderFunction` 时传入 `context.limit()`，把 Flink source 上下文中的 limit 透传下去。

同时移除测试中的 `assumeThat` 跳过逻辑，让 FLIP-27 source 也参与 limit 下推验证，并新增 `TestLimitableDataIterator` 专门测试 `LimitableDataIterator` 在不同 limit 值（-1/0/1/6/7）下的行为。

此改动只针对 Flink 1.19 模块；1.17/1.18 的回迁由后续提交 0996（#10813）完成。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：将 source 上下文中的 limit 透传给 reader。

**工作逻辑**：在构造 `RowDataReaderFunction` 的调用处追加 `context.limit()` 参数，使其接收 limit 值。仅 1 行新增。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/reader/LimitableDataIterator.java`（新增）

**修改目的**：实现带 limit 的数据迭代器。

**工作逻辑**：继承 `DataIterator<T>`，持有一个 `RecordLimiter`。构造时校验 limiter 非空。重写 `hasNext()`：若 `limiter.reachedLimit()` 为真则直接返回 false，提前终止迭代；否则委托给父类。重写 `next()`：先 `limiter.increment()` 再返回父类 `next()`。这样在达到 limit 后 reader 会自然结束本轮 split 的读取。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/reader/RecordLimiter.java`（新增）

**修改目的**：提供线程安全的记录计数与 limit 判定。

**工作逻辑**：内部持有 `long limit` 与 `AtomicLong counter`。`reachedLimit()` 仅当 `limit > 0` 且 `counter.get() >= limit` 时返回 true（limit <= 0 表示不限）。`increment()` 调用 `counter.incrementAndGet()`。提供静态工厂 `create(long limit)`。标注 `@Internal`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowDataReaderFunction.java`

**修改目的**：让 reader function 支持 limit，并在创建迭代器时使用 `LimitableDataIterator`。

**工作逻辑**：
- 新增 `long limit` 字段与 `transient RecordLimiter recordLimiter` 字段；
- 保留旧构造函数（向后兼容），委托给新构造函数并传 `limit = -1L`（不限）；
- 新增带 `long limit` 参数的构造函数；
- `createDataIterator(...)` 由原先 `new DataIterator<>(...)` 改为 `new LimitableDataIterator<>(..., lazyLimiter())`；
- `lazyLimiter()` 懒初始化 `RecordLimiter`，注释说明是为了"避免让 `RecordLimiter` 必须实现 `Serializable`"——因为 `RowDataReaderFunction` 会被序列化分发到 task，`transient` + 懒加载规避了序列化要求。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSourceConfig.java`

**修改目的**：启用此前被跳过的 FLIP-27 limit 下推测试。

**工作逻辑**：移除 `assumeThat(useFlip27Source).isFalse()` 与 TODO 注释，并补充注释说明该断言之所以成立是因为 limit 被下推到 reader 且 reader 并行度为 1。这样 FLIP-27 source 也会执行 `LIMIT_OPTION=1` 与 `OPTIONS('limit'='3')` 的断言。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestLimitableDataIterator.java`（新增）

**修改目的**：针对 `LimitableDataIterator` 的单元测试。

**工作逻辑**：使用 `ReaderUtil.createRecordBatchList` 构造 3 批 × 2 条共 6 条记录的 `CombinedScanTask`，参数化测试 `@ValueSource(longs = {-1L, 0L, 1L, 6L, 7L})`：limit <= 0 或超过总数时读全部 6 条，否则读 limit 条。覆盖了不限、边界、超限等典型情形。

## 小结

- **成效**：FLIP-27 source 现已支持 limit 下推，带 limit 的查询不再扫描全量数据，显著减少 I/O；并补齐了对应的测试覆盖。
- **影响范围**：仅 Flink 1.19 模块，6 个文件（3 个新增主/测试类、1 个修改 reader function、1 个修改 source、1 个修改测试）。无公共 API 破坏（旧构造函数保留）。
- **回迁到 1.4.x 的注意事项**：该功能为独立增强，适合回迁。回迁时需注意 1.4.x 对应的 Flink 模块版本：1.4.x 主干已含 1.17/1.18/1.19 模块。本提交只动了 1.19；1.17/1.18 的等价改动由提交 0996（#10813）单独完成，回迁时应一并考虑两个提交，确保各 Flink 版本模块行为一致。`RecordLimiter` 的 `transient` + 懒加载设计需原样保留，否则会引发序列化失败。
