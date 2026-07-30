# 提交 0112：Spark 3.3: Don't cache or reuse manifest entries while rewriting metadata by default (#8955)

## 提交信息

- **序号**：0112 / 4088
- **哈希**：721419db01ee20faafd303199d88c8be8d24e310
- **短哈希**：721419db0
- **日期**：2023-10-30 17:35:04 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.3: Don't cache or reuse manifest entries while rewriting metadata by default (#8955)
- **PR/Issue**：#8955（cherry-pick 自 #8935）

## 总体目的

这个提交与 0111 完全同源，是 PR #8935 的另一个 cherry-pick，落地目标是 Spark 3.3 分支。它要解决的同样是 `RewriteManifestsSparkAction` 在重写 manifest 元数据时默认缓存/重用 manifest entries 的正确性隐患：旧实现 `USE_CACHING_DEFAULT = true` 默认缓存 Dataset，且在关闭缓存时走 `ds.repartition(parallelism).map(value -> value, ds.exprEnc())` 的兜底路径，会把 manifest entries 重物化、可能在多个 manifest 间重用，带来正确性风险。

本提交把 Spark 3.3 的默认值翻转为 `false`，并把非缓存分支简化为 `useCaching ? ds.cache() : ds`，直接使用原 Dataset，不再 repartition + 恒等 map。

对 Iceberg 演进的意义与 0111 一致：以"安全优先"的默认值加固 manifest 重写的正确性。本提交与 0110、0111、0113 共同构成跨 Spark 3.2/3.3/3.4/3.5 多版本同步的同一系列改动。这种"一个 PR 同时 cherry-pick 到多个 Spark 版本分支"的模式是 Iceberg 维护多 Spark 版本并行支持的典型工程实践——Iceberg 同时维护 spark/v3.2、spark/v3.3、spark/v3.4、spark/v3.5 多套几乎同构的源码树，每个 bugfix 都需逐版本同步，以保证各版本行为一致。

## 如何达成设计目的

设计思路与 0111 完全相同，仅目标路径不同（`spark/v3.3/` vs `spark/v3.2/`）：翻转 `USE_CACHING_DEFAULT` 为 `false`；简化 `withReusableDS` 非缓存分支为直接返回原 Dataset；清理 `MapFunction`、`SQLConf` import；测试侧把 `useCaching` 加入参数化维度并统一注入各 rewrite 用例。改动结构与 0111 一一对应。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：翻转 Spark 3.3 的缓存默认值为关闭，并移除关闭缓存时的 repartition+恒等 map 兜底路径。

**工作逻辑**：与 0111 同构。`USE_CACHING_DEFAULT` 从 `true` 改为 `false`；`withReusableDS` 由 if/else 两分支简化为三元表达式 `useCaching ? ds.cache() : ds`，删除非缓存分支的 `ds.repartition(SQLConf.get().numShufflePartitions()).map(...)`；移除 `MapFunction` 与 `SQLConf` 两个 import。该文件与 Spark 3.2 版本的对应文件内容一致（diff 中可见 3.3 与 3.2 的 `RewriteManifestsSparkAction.java` 改动行完全相同）。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：把 `useCaching` 加入参数化测试维度，覆盖缓存开/关两条路径。

**工作逻辑**：与 0111 同构。`@Parameterized.Parameters` 从单维 `{true, false}` 扩展为二维 4 组合 `{snapshotIdInheritanceEnabled, useCaching}`；构造函数新增 `useCaching` 字段；所有 rewrite 用例统一注入 `.option(RewriteManifestsSparkAction.USE_CACHING, useCaching)`；原硬编码 `.option("use-caching", "false")` 改为常量引用 + 参数化变量，注释同步去掉"without caching"措辞。值得注意的是，本测试文件在 3.3 分支的基线 blob（`4aafb72ac`）与 3.2 分支完全一致，改动结果（`5cc79423`）也一致，说明这两个版本的测试代码当时是逐字同步的。

## 小结

通过把 Spark 3.3 的 `RewriteManifestsSparkAction` 缓存默认值翻转为关闭、并移除非缓存分支的兜底路径，本提交与 0111/0113 一起完成 PR #8935 跨 Spark 版本同步落地，体现了 Iceberg 多 Spark 版本并行维护的工程模式。
