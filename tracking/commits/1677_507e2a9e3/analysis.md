# 提交 1677：Spark: Make delete file ratio configurable (#12148)

## 提交信息

- **序号**：1677 / 4088
- **哈希**：507e2a9e39f93356bdc0dae1521bbdc46629681c
- **短哈希**：507e2a9e3
- **日期**：2025-02-03（Mon Feb 3 21:13:31 2025 +0800，原始 +0100 时区为 14:13:31）
- **作者**：SongTao Zhuang <51652084+MichaelDeSteven@users.noreply.github.com>
- **提交说明**：Spark: Make delete file ratio configurable (#12148)
- **PR/Issue**：#12148

## 总体目的

Iceberg 的 `rewriteDataFiles` 动作中，`SizeBasedDataRewriter` 负责按文件大小决定哪些数据文件需要被重写。其中有一个"删除比例阈值"逻辑：当某数据文件中被删除的行占比超过一定阈值时，即使文件大小本身在 `MIN_FILE_SIZE_BYTES`/`MAX_FILE_SIZE_BYTES` 范围内不需要重写，也会因为删除比例过高而触发重写（因为高删除比例意味着大量行被标记删除，读取时需过滤，影响读性能）。

问题在于，此前的删除比例阈值 `DELETE_RATIO_THRESHOLD` 是一个**硬编码常量** `0.3`（30%），用户无法通过配置调整。不同业务场景对"什么程度的删除比例值得重写"有不同判断——有些场景希望更激进（如 10% 就重写），有些希望更保守（如 50% 才重写）。

本提交把该阈值从硬编码常量改为可通过 `delete-ratio-threshold` 配置项调整的参数，默认值仍为 `0.3`，保持向后兼容。

## 如何达成设计目的

1. 在 `SizeBasedDataRewriter` 中把 `private static final double DELETE_RATIO_THRESHOLD = 0.3` 改为 `public static final String DELETE_RATIO_THRESHOLD = "delete-ratio-threshold"`（配置键）+ `public static final double DELETE_RATIO_THRESHOLD_DEFAULT = 0.3`（默认值）；
2. 新增 `deleteRatioThreshold` 实例字段，在 `init(options)` 中通过 `deleteRatioThreshold(options)` 解析配置，校验值必须 > 0 且 <= 1；
3. 把 `validOptions()` 中加入 `DELETE_RATIO_THRESHOLD`；
4. `needsRewriting` 中把 `deleteRatio >= DELETE_RATIO_THRESHOLD`（常量比较）改为 `deleteRatio >= deleteRatioThreshold`（实例字段比较）；
5. 在 Spark 文档 `spark-procedures.md` 中补充 `delete-ratio-threshold` 参数说明；
6. 在 Spark 3.3/3.4/3.5 三个版本的 `TestSparkFileRewriter` 中补充 `DELETE_RATIO_THRESHOLD` 出现在 `validOptions()` 的断言，以及负值/超 1 值的参数校验测试。同时在已有断言上补充 `.isInstanceOf(IllegalArgumentException.class)` 使异常类型更严格。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedDataRewriter.java`（修改，+27/-2 行）

**修改目的**：把删除比例阈值从硬编码常量改为可配置参数。

**工作逻辑**：

- 把 `private static final double DELETE_RATIO_THRESHOLD = 0.3` 替换为：
  - `public static final String DELETE_RATIO_THRESHOLD = "delete-ratio-threshold"`（配置键）；
  - `public static final double DELETE_RATIO_THRESHOLD_DEFAULT = 0.3`（默认值）；
  - 并添加详细 Javadoc 说明：删除比例 ≥ 此值的数据文件将无视大小限制被重写，包含此类文件的文件组也将无视 `MIN_INPUT_FILES` 限制被重写；
- 新增 `private double deleteRatioThreshold` 实例字段；
- `validOptions()` 中加入 `DELETE_RATIO_THRESHOLD`；
- `init(options)` 中调用 `deleteRatioThreshold(options)` 解析；
- `deleteRatioThreshold(options)`：用 `PropertyUtil.propertyAsDouble` 解析，校验 `> 0` 且 `<= 1`，否则抛 `IllegalArgumentException`；
- `needsRewriting` 中 `deleteRatio >= DELETE_RATIO_THRESHOLD` 改为 `deleteRatio >= deleteRatioThreshold`。

### `docs/docs/spark-procedures.md`（修改，+1 行）

**修改目的**：在 `rewrite_data_files` 过程的参数表中补充 `delete-ratio-threshold` 行。

**工作逻辑**：新增一行 `| delete-ratio-threshold | 0.3 | Minimum deletion ratio that needs to be associated with a data file for it to be considered for rewriting |`。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java`（修改，+44/-5 行）

**修改目的**：补充 `DELETE_RATIO_THRESHOLD` 的 `validOptions` 断言与参数校验测试。

**工作逻辑**：

- 在 `BinPack`/`Sort`/`ZOrder` 三种 rewriter 的 `validOptions` 断言中加入 `DELETE_RATIO_THRESHOLD`；
- 在三种 rewriter 的参数校验测试中加入 `delete-ratio-threshold = "-1"`（期望报 `> 0` 错误）和 `delete-ratio-threshold = "127"`（期望报 `<= 1` 错误）两个用例；
- 在已有的 `assertThatThrownBy` 链上补充 `.isInstanceOf(IllegalArgumentException.class)` 使异常类型断言更严格。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java`（修改，+46/-5 行）

**修改目的**：与 3.3 模块相同的测试补充。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java`（修改，+46/-5 行）

**修改目的**：与 3.3/3.4 模块相同的测试补充。

## 小结

- **成效**：用户现在可通过 `delete-ratio-threshold` 参数自定义 `rewriteDataFiles` 动作中触发重写的删除比例阈值，默认 0.3 保持向后兼容。不同业务场景可按需调整重写激进程度，优化存储与读取性能的平衡。
- **影响范围**：`core` 模块的 `SizeBasedDataRewriter`（Spark/Flink 各版本共享）、Spark 文档、Spark 3.3/3.4/3.5 三个版本的测试。行为上对不配置该参数的用户无变化（默认值与原硬编码值一致）。
- **回迁到 1.4.x 的注意事项**：回迁安全且建议回迁。`SizeBasedDataRewriter` 在 core 模块，各 Spark 版本共享，回迁时只需修改 core 一处 + 文档 + 各 Spark 版本测试。需确认 1.4.x 分支的 `SizeBasedDataRewriter` 中 `DELETE_RATIO_THRESHOLD` 仍为硬编码 `0.3` 常量；若是则可直接回迁。同时注意 1.4.x 可能只有部分 Spark 版本（如 3.3/3.4/3.5），测试文件需按实际存在的版本调整。`PropertyUtil.propertyAsDouble` 需确认在 1.4.x 中可用。
