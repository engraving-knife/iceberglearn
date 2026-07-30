# 提交 1729：Core: Validate Arguments when Using adjustSplitSize (#12201)

## 提交信息

- **序号**：1729 / 4088
- **哈希**：e509cfcca151dd3279fd95844fc3fd0f81c01704
- **短哈希**：e509cfcca
- **日期**：2025-02-13 13:46:55 -0600
- **作者**：dongwang
- **提交说明**：Core: Validate Arguments when Using adjustSplitSize (#12201)
- **PR/Issue**：#12201

## 总体目的

`TableScanUtil.adjustSplitSize` 方法在计算拆分大小（split size）时，如果传入的 `parallelism`（并行度）或 `splitSize`（拆分大小）参数为 0 或负数，会导致数学运算异常（如除零错误）或不合理的拆分结果。此前该方法未对入参进行任何校验，使得无效配置可以在运行时传播到更深层，最终以难以理解的错误形式暴露出来。

本提交的核心目标是在 `adjustSplitSize` 方法入口处增加参数前置校验（precondition validation），当 `parallelism <= 0` 或 `splitSize <= 0` 时立即抛出 `IllegalArgumentException`，并附带清晰的错误信息，帮助用户快速定位配置问题。

此外，提交还补充了对应的单元测试和 Spark SQL 集成测试，确保校验逻辑在各种边界场景下均能正确触发，并验证了当用户将表的 `split-size` 属性设置为非法值时，查询能给出有意义的错误提示而非模糊的运行时异常。

## 如何达成设计目的

提交通过三层修改达成目标：

1. **核心逻辑层**：在 `TableScanUtil.adjustSplitSize` 方法体开头添加两个 `Preconditions.checkArgument` 校验，分别在 `parallelism` 和 `splitSize` 不满足条件时抛出带格式化信息的异常。

2. **单元测试层**：在 `TestTableScanUtil` 中新增四个 `assertThatThrownBy` 测试用例，分别覆盖 `splitSize` 为 -1 和 0、`parallelism` 为 -1 和 0 的场景，验证异常类型和错误信息。

3. **集成测试层**：在 Spark 3.5 的 `TestSelect` 中新增 `testSelectWithSpecifiedTargetSplitSize` 测试方法，模拟用户设置表的 `split-size` 属性为 -1 和 0 后执行查询，验证端到端的错误传播路径。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/TableScanUtil.java`（修改, +3 lines）

**修改目的**：在 `adjustSplitSize` 方法中添加参数校验，防止无效的 `parallelism` 和 `splitSize` 值导致后续计算异常。

**工作逻辑**：在方法体最开始处添加了两行 `Preconditions.checkArgument` 调用：
- 第一行校验 `parallelism > 0`，若不满足则抛出 `IllegalArgumentException`，消息为 `"Parallelism must be > 0: <实际值>"`。
- 第二行校验 `splitSize > 0`，若不满足则抛出 `IllegalArgumentException`，消息为 `"Split size must be > 0: <实际值>"`。

这两行校验位于所有计算逻辑之前，确保后续的除法和比较操作不会遇到除零或负值导致的未定义行为。

### `core/src/test/java/org/apache/iceberg/util/TestTableScanUtil.java`（修改, +16 lines）

**修改目的**：为新增的参数校验逻辑添加单元测试覆盖。

**工作逻辑**：在已有的 `adjustSplitSize` 测试方法末尾追加了四个 `assertThatThrownBy` 断言：
- `adjustSplitSize(scanSize, parallelism, -1)`：验证 `splitSize` 为 -1 时抛出 `IllegalArgumentException`，消息以 `"Split size must be > 0: -1"` 开头。
- `adjustSplitSize(scanSize, parallelism, 0)`：验证 `splitSize` 为 0 时抛出 `IllegalArgumentException`，消息以 `"Split size must be > 0: 0"` 开头。
- `adjustSplitSize(scanSize, -1, smallDefaultSplitSize)`：验证 `parallelism` 为 -1 时抛出 `IllegalArgumentException`，消息以 `"Parallelism must be > 0: -1"` 开头。
- `adjustSplitSize(scanSize, 0, largeDefaultSplitSize)`：验证 `parallelism` 为 0 时抛出 `IllegalArgumentException`，消息以 `"Parallelism must be > 0: 0"` 开头。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`（修改, +26 lines）

**修改目的**：添加端到端集成测试，验证当用户将表属性 `split-size` 设置为非法值时，Spark 查询能正确抛出带清晰信息的异常。

**工作逻辑**：新增 `testSelectWithSpecifiedTargetSplitSize` 测试方法，主要步骤如下：
1. 首先设置表属性 `read.split.target-size` 为 `1024` 并执行查询，验证正常场景下查询可成功返回预期结果。
2. 然后将表属性 `split-size`（即 `SPLIT_SIZE`）设置为 `-1`，执行 `REFRESH TABLE` 后查询，断言抛出 `IllegalArgumentException` 且消息包含 `"Split size must be > 0: -1"`。
3. 最后将 `split-size` 设置为 `0`，执行 `REFRESH TABLE` 后查询，断言抛出 `IllegalArgumentException` 且消息包含 `"Split size must be > 0: 0"`。

新增了 `import static org.apache.iceberg.TableProperties.SPLIT_SIZE` 以引用 `split-size` 属性键。

## 小结

- **成效**：在 `adjustSplitSize` 方法中增加了参数前置校验，当 `parallelism` 或 `splitSize` 为 0 或负数时立即抛出带清晰信息的 `IllegalArgumentException`，避免了后续计算中的除零异常或不可预期的行为。用户现在能直接从错误信息中了解到是哪个配置项出了问题。
- **影响范围**：涉及 core 模块的 `TableScanUtil` 工具类及其单元测试，以及 Spark 3.5 模块的集成测试。由于 `adjustSplitSize` 被读取路径广泛使用，该校验会影响所有依赖拆分大小计算的查询场景，但仅在参数非法时才会触发。
- **回迁到 1.4.x 的注意事项**：此提交是一个独立的参数校验增强，无前置依赖，回迁风险低。需确认 1.4.x 分支中 `TableScanUtil.adjustSplitSize` 方法签名和逻辑与 main 分支一致。测试中引用的 `SPLIT_SIZE` 常量和 `TableProperties` 类应在 1.4.x 中已存在。建议回迁。
