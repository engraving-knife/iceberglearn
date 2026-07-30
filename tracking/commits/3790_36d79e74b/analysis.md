# 提交 3790：Spark: Trim TestStructuredStreamingRead3 parameter rows from 8 to 2 (#16559)

## 提交信息

- **序号**：3790 / 4088
- **哈希**：36d79e74bb19e56b7b0ca3fcc25d9e557b118745
- **短哈希**：36d79e74b
- **日期**：2026-05-27 11:35:46 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spark: Trim TestStructuredStreamingRead3 parameter rows from 8 to 2 (#16559)
- **PR/Issue**：#16559

## 总体目的

这个提交大幅精简了 `TestStructuredStreamingRead3` 测试的参数化行数，从 8 行减少到 2 行，削减约 75% 的测试调用。这是继 3789 号提交之后又一个测试精简提交，专注于 Spark 结构化流读取测试。

**精简策略**：原来 8 行 = 4 个 catalog × async{true, false}。精简后保留 2 行：
1. **testhive (async=true)**：Hive metastore 基线，异步规划。
2. **testrest (async=false)**：REST catalog（OSS 战略 catalog），同步规划。

**移除理由**：
- 流读取语义不是 catalog 特定的，catalog 后端差异主要在 DDL/表解析路径，不在流式读取。
- async-vs-sync 规划是该测试真正覆盖的唯一维度，保留两个值即可。
- testhadoop（HadoopCatalog）不推荐用于生产。
- spark_catalog（SessionCatalog）的差异在 DDL/表解析，不在流式读取。

**性能影响**：每次调用运行 33 个流式测试，8→2 行将调用从 264 减到 66，减少约 75% 的 CPU 时间。该测试是 Spark 核心 CI 中 CPU 占比最高的类（20.3% 总测试 CPU）。

## 如何达成设计目的

从 `TestStructuredStreamingRead3` 的 `parameters()` 方法中移除 6 行参数配置，保留 testhive(async=true) 和 testrest(async=false) 两行。

## 修改详情

### `spark/v3.5/spark/src/test/java/.../TestStructuredStreamingRead3.java` (-39 lines)

**修改目的**：精简 v3.5 测试参数行。

**工作逻辑**：移除 6 行参数：testhive(async=false)、testhadoop(async=false)、testhadoop(async=true)、testrest(async=true)、spark_session(async=false)、spark_session(async=true)。保留 testhive(async=true) 和 testrest(async=false)。

### `spark/v4.0/spark/src/test/java/.../TestStructuredStreamingRead3.java` (-39 lines)

**修改目的**：精简 v4.0 测试参数行。

**工作逻辑**：与 v3.5 相同的精简。

### `spark/v4.1/spark/src/test/java/.../TestStructuredStreamingRead3.java` (-39 lines)

**修改目的**：精简 v4.1 测试参数行。

**工作逻辑**：与 v3.5 相同的精简。

## 总结

这个提交通过精简 `TestStructuredStreamingRead3` 的参数化行数从 8 到 2，减少了约 75% 的测试 CPU 开销。该测试是 Spark 核心 CI 中 CPU 占比最高的类（20.3%），精简后显著缩短 CI 运行时间。同时保持了对生产重要 catalog（Hive 和 REST）和异步/同步规划两种模式的覆盖。这是 CI 性能优化的一个重要贡献，由 Steven Zhen Wu 使用 Claude Code AI 辅助编写。
