# 提交 3706：Spark: Backport migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16303)

## 提交信息

- **序号**：3706 / 4088
- **哈希**：bc8f264be1a77f5979616d582fa121c681b87ca7
- **短哈希**：bc8f264be
- **日期**：2026-05-13 23:30:33 -0700
- **作者**：drexler-sky
- **提交说明**：Spark: Backport migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16303)
- **PR/Issue**：#16303

## 总体目的

这个提交是 PR #16295（提交 3691）向 Spark 3.4、3.5 和 4.0 模块的回移植。它将这些版本的 `SparkCopyOnWriteScan` 类从旧的 `SupportsRuntimeFiltering` 接口迁移到新的 `SupportsRuntimeV2Filtering` 接口。

提交 3691 已经在 Spark 4.1 中完成了此迁移，本提交将同样的修改应用到 Spark 3.4、3.5 和 4.0，确保所有 Spark 版本的 Copy-on-Write 扫描使用统一的 V2 过滤 API。

此外，Spark 3.4 还包含一个 Scala 文件 `RowLevelCommandDynamicPruning.scala` 的适配修改。

## 如何达成设计目的

通过与提交 3691 相同的修改方式，将三个 Spark 版本的 `SparkCopyOnWriteScan` 类迁移到 `SupportsRuntimeV2Filtering` 接口，使用 V2 的 `Predicate` 类型替代 V1 的 `Filter` 类型。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+38/-16 lines)
### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+38/-16 lines)
### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+38/-16 lines)

**修改目的**：迁移到 SupportsRuntimeV2Filtering 接口。

**工作逻辑**：与提交 3691 完全相同的修改——更改实现的接口，修改 `filter` 方法签名从 `Filter[]` 到 `Predicate[]`，新增 `isFilePathInPredicate` 和 `extractStringLiterals` 辅助方法。

### `spark/v3.4/spark/src/main/scala/org/apache/iceberg/spark/source/RowLevelCommandDynamicPruning.scala` (+2/-2 lines)

**修改目的**：适配 Spark 3.4 的 V2 过滤 API 变更。

**工作逻辑**：Spark 3.4 的 Scala 文件需要相应调整以适配新的 V2 Predicate 类型。

## 总结

这是提交 3691 向 Spark 3.4/3.5/4.0 的回移植，确保所有 Spark 版本的 Copy-on-Write 扫描使用统一的 V2 过滤 API。至此，所有四个 Spark 版本（3.4、3.5、4.0、4.1）都完成了 `SupportsRuntimeV2Filtering` 迁移。
