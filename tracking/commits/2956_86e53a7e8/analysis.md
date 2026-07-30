# 提交 2956：Flink: Backport: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14758)

## 提交信息

- **序号**：2956 / 4088
- **哈希**：86e53a7e83d1604b25535cf8ce34c39e8fd4fa1f
- **短哈希**：86e53a7e8
- **日期**：2025-12-04
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14758)
- **PR/Issue**：#14758（回移自 #14191）

## 总体目的

本提交是 main 分支上 #14191 改动向 1.4.x 维护分支的回移（backport），同时应用于 `flink/v1.20` 和 `flink/v2.0` 两个 Flink 版本目录，两处改动内容完全一致。其核心目标是改进 Flink 动态 Sink（Dynamic Sink）中 `writeParallelism` 参数的处理方式与文档说明。

在此前的实现中，`HashKeyGenerator.WriteKey` 构造器对 `writeParallelism > maxWriteParallelism` 的情况仅打印一条 WARN 日志并静默将值截断为 `maxWriteParallelism`。这种"静默兜底"策略存在两个问题：其一，用户配置错误（例如传入了负数或 0）不会被及时发现，可能引发数组越界等难以追溯的运行时异常，因为内部会用 `writeParallelism` 来分配 `distinctKeys` 数组；其二，缺少对 `writeParallelism` 参数的 Javadoc 说明，调用方难以理解该参数的合法取值范围与语义（例如可以用 `Integer.MAX_VALUE` 表示"始终使用最大可用并行度"）。

本提交将"静默兜底"改为"快速失败"（fail-fast）：对非法配置直接抛出 `IllegalArgumentException`，使问题在配置阶段暴露而非在运行时潜伏。同时补充了构造器文档，明确了 `writeParallelism` 的取值约定与自动封顶行为。

## 如何达成设计目的

整体思路分三层：一是在 `DynamicRecord` 构造器上补全 Javadoc，说明 `writeParallelism` 的语义与自动封顶约定；二是在 `HashKeyGenerator.getWriteKey` 调用处用 `Math.min` 将用户传入的 `writeParallelism` 提前封顶到 `maxWriteParallelism`，保留 `Integer.MAX_VALUE` 这一"用满可用并行度"的合法用法；三是在 `WriteKey` 构造器内用 `Preconditions.checkArgument` 做硬校验，对非正值和超过上限的值直接抛异常。配套测试验证了 -1 与 0 两种非法取值均会抛出异常。改动同时落地到 v1.20 与 v2.0 两套目录。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (+14/-0 lines)

**修改目的**：为 `DynamicRecord` 构造器补充 `writeParallelism` 参数的 Javadoc 文档。

**工作逻辑**：
新增 Javadoc 明确说明 `writeParallelism` 为"并行 writer 数量"，可设为任意 `> 0` 的值，但始终会被 sink 并行度（即最大写并行度）自动封顶；当设为 `Integer.MAX_VALUE` 时表示始终使用最大可用写并行度。这一文档与后续 `Math.min` 的封顶逻辑形成呼应，向调用方传达了"可以放心传一个大值"的约定。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+13/-9 lines)

**修改目的**：将 `writeParallelism` 的越界处理由静默截断改为快速失败，并在调用处提前封顶。

**工作逻辑**：
改动集中在两处。第一处在 `getWriteKey` 方法中，构造 `WriteKey` 时传入的 `writeParallelism` 由原来的 `dynamicRecord.writeParallelism()` 改为 `Math.min(dynamicRecord.writeParallelism(), maxWriteParallelism)`。这样在到达严格校验之前，合法的"大值"用法（如 `Integer.MAX_VALUE`）会被安全地封顶到 `maxWriteParallelism`，不会触发构造器里的上限校验。

第二处在 `WriteKey` 构造器内部。原先的 `if (writeParallelism > maxWriteParallelism)` 分支会打印 WARN 并赋值截断，现在被替换为两个 `Preconditions.checkArgument` 校验：`writeParallelism > 0`（非正则抛出，错误信息含 `tableName` 与实际值）和 `writeParallelism <= maxWriteParallelism`（超限则抛出，错误信息同时给出两个值便于排查）。由于调用处已用 `Math.min` 封顶，第二个校验在正常路径下不会触发，主要用于防御性编程；第一个校验则直接拦截 -1、0 这类会导致 `new int[writeParallelism]` 抛 `NegativeArraySizeException` 或产生空数组的非法输入。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+29/-0 lines)

**修改目的**：新增测试验证非正 `writeParallelism` 会快速失败。

**工作逻辑**：
新增 `testFailOnNonPositiveWriteParallelism` 测试，分别用 `-1` 和 `0` 作为 `writeParallelism` 调用 `getWriteKey`，使用 AssertJ 的 `assertThatThrownBy` 断言两者均抛出异常。测试构造了 `maxWriteParallelism = 5` 的 `HashKeyGenerator`，使用未分区表、`DistributionMode.NONE`、空 equality 字段集与空 `GenericRowData`，聚焦验证校验逻辑本身。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (+14/-0 lines)

**修改目的**：与 v1.20 同步，为 v2.0 目录下的 `DynamicRecord` 构造器补充 Javadoc。内容与 v1.20 完全一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+13/-9 lines)

**修改目的**：与 v1.20 同步，将 v2.0 目录下的 `HashKeyGenerator` 改为快速失败校验并在调用处封顶。内容与 v1.20 完全一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+29/-0 lines)

**修改目的**：与 v1.20 同步，为 v2.0 目录补充非正 `writeParallelism` 的失败测试。内容与 v1.20 完全一致。

## 总结

该回移提交通过"文档 + 快速失败 + 调用处封顶"三管齐下的方式，使 Flink 动态 Sink 的 `writeParallelism` 配置更加健壮且对用户友好：合法的大值用法被保留并自动封顶，而真正的非法配置（非正数）不再被静默吞掉而是立即报错，避免后续出现难以定位的数组异常。改动同步覆盖 v1.20 与 v2.0 两个 Flink 版本目录，保证维护分支与主线行为一致。
