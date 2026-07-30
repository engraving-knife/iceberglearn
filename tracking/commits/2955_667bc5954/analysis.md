# 提交 2955：Flink: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14191)

## 提交信息

- **序号**：2955 / 4088
- **哈希**：667bc595476c6e991fba2c1544e72926dd82fc42
- **短哈希**：667bc5954
- **日期**：2025-12-04
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14191)
- **PR/Issue**：#14191

## 总体目的

Iceberg Flink v2.1 的 Dynamic Sink 引入了 `writeParallelism` 参数，用来控制写入器的并行度（即把数据按写键分发到的并行 writer 数量）。但该参数此前存在两个问题：一是 `DynamicRecord` 的构造器没有 Javadoc，用户无从知晓 `writeParallelism` 的合法取值范围与语义（例如可以设为 `Integer.MAX_VALUE` 表示"始终用最大可用并行度"、且会被 sink 并行度自动封顶）；二是 `HashKeyGenerator` 内部对非法配置的处理过于宽松——当 `writeParallelism > maxWriteParallelism` 时只是 `LOG.warn` 后静默封顶，而当 `writeParallelism <= 0` 时没有任何校验，会直接进入 `new int[writeParallelism]` 之类的数组分配，抛出含义不明的 `NegativeArraySizeException`，让用户难以定位配置错误。

本提交同时解决这两点：为 `DynamicRecord` 构造器补全 Javadoc，明确 `writeParallelism` 的含义、取值约束与自动封顶行为；并把 `HashKeyGenerator` 中"警告+封顶"的宽容逻辑改为"快速失败"——在 `InnerHashKeyGenerator` 构造器里用 `Preconditions.checkArgument` 校验 `writeParallelism > 0` 且 `<= maxWriteParallelism`，给出带表名与实际值的清晰错误消息。为避免破坏"用户设大值表示用满并行度"的既有用法，在 `getKey` 调用处用 `Math.min(writeParallelism, maxWriteParallelism)` 先做封顶，再传入构造器，从而让这条主流路径仍能静默封顶，而把"显式非法"（负数或零）留给构造器报错。

## 如何达成设计目的

设计分三步。文档侧：给 `DynamicRecord` 构造器加 Javadoc，说明 `writeParallelism` 可设任意 `> 0` 的值、会被 sink 并行度封顶、设 `Integer.MAX_VALUE` 表示用满可用并行度。逻辑侧：`HashKeyGenerator.getKey` 在调用 `getWriteKey` 时把 `dynamicRecord.writeParallelism()` 用 `Math.min(..., maxWriteParallelism)` 封顶；`InnerHashKeyGenerator` 构造器删除原 `if (wp > max) { LOG.warn; wp = max; }` 的宽容块，改为两条 `Preconditions.checkArgument`（`wp > 0` 与 `wp <= maxWriteParallelism`）。测试侧：新增 `testFailOnNonPositiveWriteParallelism`，验证 `writeParallelism = -1` 与 `0` 都会抛异常。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (+14/-0 lines)

**修改目的**：为 `DynamicRecord` 构造器补全 Javadoc，明确 `writeParallelism` 语义。

**工作逻辑**：
在构造器上方新增 Javadoc，逐个说明参数：`tableIdentifier`（目标表标识）、`branch`（目标分支）、`schema`（目标表 schema）、`rowData`（匹配 schema 的数据）、`partitionSpec`（目标分区规约）、`distributionMode`（`DistributionMode`）。对 `writeParallelism` 着重说明：可设为任意 `> 0` 的值，但始终会被"最大写并行度"（即 sink 并行度）自动封顶；设为 `Integer.MAX_VALUE` 表示始终使用最大可用写并行度。这段文档把原先隐式的契约显式化，是用户正确配置的关键依据。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+18/-12 lines, 净 +6)

**修改目的**：把对非法 `writeParallelism` 的"警告+封顶"改为"快速失败"，并在主流路径前置封顶以保持兼容。

**工作逻辑**：
- `getKey` 方法（约第 99 行）：把传入 `getWriteKey` 的最后一个参数从 `dynamicRecord.writeParallelism()` 改为 `Math.min(dynamicRecord.writeParallelism(), maxWriteParallelism)`。这样当用户设了一个大于 sink 并行度的值时，在调用 `InnerHashKeyGenerator` 之前就被封顶，构造器里的 `<= maxWriteParallelism` 校验不会触发——保留了"设大值=用满并行度"的既有用法，无需用户感知封顶。
- `InnerHashKeyGenerator` 构造器：删除原 `if (writeParallelism > maxWriteParallelism) { LOG.warn(...); writeParallelism = maxWriteParallelism; }` 整块，替换为两条 `Preconditions.checkArgument`：
  - `writeParallelism > 0`，消息 `"%s: writeParallelism must be > 0 (is: %s)"`，带 `tableName` 与实际值；
  - `writeParallelism <= maxWriteParallelism`，消息 `"%s: writeParallelism (%s) must be <= maxWriteParallelism (%s)"`，带表名与两个值。
  这样构造器对外契约变为"必须传入合法的 0 < wp <= max"，任何绕过 `getKey` 直接构造的路径都会快速失败并给出可定位的错误消息。注意：由于 `getKey` 已做 `Math.min`，正常路径不会触发第二条校验；该校验主要保护构造器作为公共契约的严格性，并捕获其他潜在调用方。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+29/-0 lines)

**修改目的**：验证非正的 `writeParallelism` 会快速失败。

**工作逻辑**：
新增 `testFailOnNonPositiveWriteParallelism`：构造 `HashKeyGenerator(16, maxWriteParallelism=5)`，用 `assertThatThrownBy` 分别以 `writeParallelism = -1` 与 `0` 调 `getWriteKey(...)`，断言两者都抛异常。测试用 `PartitionSpec.unpartitioned()`、`DistributionMode.NONE`、空等值字段集合与 `GenericRowData.of()`，专注于配置校验而非写入语义。导入新增 `assertThatThrownBy`。注意已有的 `testCapAtMaxWriteParallelism` 测试仍保留并继续验证"大于 max 时被 `Math.min` 封顶"的兼容行为，说明本次改动并未破坏既有用法。

## 总结

本提交为 Flink Dynamic Sink 的 `writeParallelism` 同时补全了文档与失败语义：`DynamicRecord` 构造器 Javadoc 明确取值范围与自动封顶行为；`HashKeyGenerator` 把宽容的"警告+封顶"改为构造器内的 `Preconditions.checkArgument` 快速失败，并在 `getKey` 主路径用 `Math.min` 前置封顶以保留"设大值=用满并行度"的既有用法。新增测试覆盖非正值的失败场景，既提升了配置错误的可诊断性，又保持向后兼容。
