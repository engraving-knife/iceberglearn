# 提交 0173：Parquet: Add log entry when Bloom filters are used (#9010)

## 提交信息

- **序号**：0173 / 4088
- **哈希**：1e2a71398fb5564d1cd9f4e12b3d1acc568f8ef7
- **短哈希**：1e2a71398
- **日期**：2023-11-16 16:16:15 -0800
- **作者**：Huaxin Gao
- **提交说明**：Parquet: Add log entry when Bloom filters are used (#9010)
- **PR/Issue**：#9010

## 总体目的

这个提交为 `ParquetBloomRowGroupFilter` 增加一条 debug 级别日志，记录下"实际命中使用的 Bloom 过滤器列 ID 集合"。`ParquetBloomRowGroupFilter` 是 Iceberg 在 Parquet 读取路径上做行组（row group）级 Bloom 过滤裁剪的过滤器，它会先计算过滤条件引用的列（`filterRefs`）与 Parquet 文件中实际带有 Bloom 过滤器的列（`fieldsWithBloomFilter`）的交集；若交集为空则早退返回 `ROWS_MIGHT_MATCH`，否则继续走 `ExpressionVisitors.visitEvaluator`。

在此之前，Bloom 过滤器是否真的被使用、用在了哪些列上，对外完全不可见。对于运维和调优场景（比如验证写入端是否正确地为某些列生成了 Bloom 过滤器、读取端是否真的命中了这些列、`write.parquet.bloom-filter-enabled.column.*` 配置是否生效），缺乏可观测性是一个痛点。本提交在保持原有早退逻辑不变的前提下，把"实际参与过滤的列 ID 集合"以 debug 日志输出，让用户在排查 Bloom 过滤器是否生效时有据可查。

由于是 debug 级别且仅在确有交集时才打印，对正常运行性能与日志噪声几乎无影响，是低风险的可观测性增强。

## 如何达成设计目的

在 [`ParquetBloomRowGroupFilter`](../../../parquet/src/main/java/org/apache/iceberg/parquet/ParquetBloomRowGroupFilter.java) 类上引入 SLF4J `Logger`，把原 `if (!filterRefs.isEmpty() && Sets.intersection(...).isEmpty())` 单行早退条件重构为：先计算 `overlappedBloomFilters = Sets.intersection(fieldsWithBloomFilter, filterRefs)`，再判空——空则照旧早退，非空则 `LOG.debug(...)` 打印列 ID 集合后继续评估。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetBloomRowGroupFilter.java`

**修改目的**：在不改变 Bloom 过滤早退行为的前提下，增加对"被使用的 Bloom 过滤器列"的可观测性。

**工作逻辑**：

1. 新增 import 与静态 logger 字段：

   ```java
   import org.slf4j.Logger;
   import org.slf4j.LoggerFactory;

   private static final Logger LOG = LoggerFactory.getLogger(ParquetBloomRowGroupFilter.class);
   ```

2. 在 `eval` 流程的早退分支处，把：

   ```java
   if (!filterRefs.isEmpty() && Sets.intersection(fieldsWithBloomFilter, filterRefs).isEmpty()) {
     return ROWS_MIGHT_MATCH;
   }
   ```

   重构为：

   ```java
   if (!filterRefs.isEmpty()) {
     Set<Integer> overlappedBloomFilters = Sets.intersection(fieldsWithBloomFilter, filterRefs);
     if (overlappedBloomFilters.isEmpty()) {
       return ROWS_MIGHT_MATCH;
     } else {
       LOG.debug("Using Bloom filters for columns with IDs: {}", overlappedBloomFilters);
     }
   }
   ```

   行为上：`filterRefs.isEmpty()` 时仍不进入分支（与原 `!filterRefs.isEmpty() && ...` 等价）；交集为空仍早退 `ROWS_MIGHT_MATCH`；交集非空时打印日志后继续走 `ExpressionVisitors.visitEvaluator(expr, this)`——这部分与原逻辑完全一致。唯一新增的是非空分支的 `LOG.debug`。`overlappedBloomFilters` 是 `Sets.intersection` 返回的视图，本身就是 `Set<Integer>`，可直接用于日志格式化。

## 小结

通过一条 debug 日志让 Parquet Bloom 过滤器的实际命中列可观测，提升了读取路径上 Bloom 过滤行为的可调试性，对性能与正常运行无影响。
