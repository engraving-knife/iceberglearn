# 提交 0113：Spark 3.4: Don't cache or reuse manifest entries while rewriting metadata by default (#8954)

## 提交信息

- **序号**：0113 / 4088
- **哈希**：86bb1c09f5ffd2b6a7c72683cb86bb95f4c2b72f
- **短哈希**：86bb1c09f
- **日期**：2023-10-30 17:35:59 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.4: Don't cache or reuse manifest entries while rewriting metadata by default (#8954)
- **PR/Issue**：#8954（cherry-pick 自 #8935）

## 总体目的

这个提交是 PR #8935 的第三个 cherry-pick，落地目标是 Spark 3.4 分支，与 0111（3.2）、0112（3.3）同源同构。它要解决的同样是 `RewriteManifestsSparkAction` 重写 manifest 元数据时默认缓存/重用 manifest entries 的正确性隐患：旧实现默认 `USE_CACHING_DEFAULT = true`，且关闭缓存时走 `ds.repartition(parallelism).map(value -> value, ds.exprEnc())` 的兜底路径，会把 entries 重物化并在多个 manifest 间重用，带来正确性风险。

本提交把 Spark 3.4 的默认值翻转为 `false`，并把非缓存分支简化为 `useCaching ? ds.cache() : ds`，直接返回原 Dataset。

对 Iceberg 演进的意义与 0111、0112 一致：以"安全优先"的默认值加固 manifest 重写正确性。本提交与 0110、0111、0112 共同构成跨 Spark 3.2/3.3/3.4/3.5 多版本同步的同一系列改动。三个 cherry-pick 提交（0111/0112/0113）由同一作者在同一分钟内（17:34、17:35、17:35）连续提交，PR 号也连续（#8956/#8955/#8954），典型地展示了 Iceberg 把一个核心修复同时 backport 到所有受支持 Spark 版本分支的发布流程：一个原始 PR（#8935）+ 每个版本一个 cherry-pick PR，确保各版本行为一致。

## 如何达成设计目的

设计思路与 0111、0112 完全相同，仅目标路径为 `spark/v3.4/`：翻转 `USE_CACHING_DEFAULT` 为 `false`；简化 `withReusableDS` 非缓存分支为直接返回原 Dataset；清理 `MapFunction`、`SQLConf` import；测试侧把 `useCaching` 加入参数化维度并统一注入各 rewrite 用例。改动结构与 0111、0112 一一对应。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：翻转 Spark 3.4 的缓存默认值为关闭，并移除关闭缓存时的 repartition+恒等 map 兜底路径。

**工作逻辑**：与 0111、0112 同构。`USE_CACHING_DEFAULT` 从 `true` 改为 `false`；`withReusableDS` 由 if/else 简化为 `Dataset<T> reusableDS = useCaching ? ds.cache() : ds;`，删除非缓存分支的 `repartition + 恒等 map`；移除 `MapFunction` 与 `SQLConf` import。该生产文件改动与 3.2、3.3 版本逐行一致（3.3 与 3.4 的 `RewriteManifestsSparkAction.java` 基线 blob 相同：`06a5c8c57`）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：把 `useCaching` 加入参数化测试维度，覆盖缓存开/关两条路径。

**工作逻辑**：与 0111、0112 同构。`@Parameterized.Parameters` 从单维扩展为二维 4 组合 `{snapshotIdInheritanceEnabled, useCaching}`；构造函数新增 `useCaching` 字段；所有 rewrite 用例统一注入 `.option(RewriteManifestsSparkAction.USE_CACHING, useCaching)`；原硬编码 `.option("use-caching", "false")` 改为常量引用 + 参数化变量。与 3.2/3.3 不同的是，3.4 分支的测试文件基线 blob 为 `64dbf42d4`（与 3.2/3.3 的 `4aafb72ac` 不同），说明 3.4 测试代码此前已有独立演进，但本次改动语义与 3.2/3.3 完全一致。

## 小结

通过把 Spark 3.4 的 `RewriteManifestsSparkAction` 缓存默认值翻转为关闭、并移除非缓存分支的兜底路径，本提交完成 PR #8935 向 Spark 3.4 的同步落地，与 0111/0112 一起构成跨 Spark 3.2/3.3/3.4 的同系列正确性加固。
