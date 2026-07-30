# 提交 0432：API, Spark: Fix aggregation pushdown on struct fields

## 提交信息

- **序号**：0432
- **哈希**：9de693f1e7f46024f47cdc971d8603fd76d87705
- **短哈希**：9de693f1e
- **日期**：2024-01-31 09:29:43 -0800
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：API, Spark: Fix aggregation pushdown on struct fields (#9176)
- **PR/Issue**：#9176

## 总体目的

Iceberg 的聚合下推（aggregate pushdown）能力允许 Spark 在扫描 Iceberg 表时直接利用元数据中的统计信息（如 manifest 文件中记录的列级 min/max/value count 等）来计算 `COUNT/MAX/MIN` 这类聚合，而无需真正读取数据文件。这对于 struct 类型的子字段（例如 `struct_with_int.c1`、深度嵌套的 `struct.a.b.c.d`）原本无法正常下推，原因在于 `ValueAggregate` 这个内部聚合累加器的 `get(int pos, Class<T> javaClass)` 方法只做了一次无脑的 `(T) value` 强转。

当 Spark 在 struct 子字段上做聚合下推时，调用方传入的目标类型是 `StructLike`（因为 struct 字段需要按 `StructLike` 协议被解构成多个内部位置再访问），但 `value` 字段持有的其实是基础类型（如 `Long`、`Integer`、`Timestamp` 等），强转就会抛 `ClassCastException`，导致整个聚合下推路径失败回退或直接报错。这就让 Iceberg 在 struct 字段上完全丧失了利用元数据加速聚合的能力——本来 Iceberg 在 manifest 中是按 struct 子字段独立记录统计值的，但聚合累加器的访问协议没有跟上。

这个提交修复 `ValueAggregate.get()` 的类型分发逻辑：当调用方请求的类型能赋值给 `StructLike` 时，返回累加器自身（`this`）——因为 `ValueAggregate` 本身实现了 `StructLike` 接口，它把单一的 `value` 包装成一个单字段的结构，让上层按 `StructLike` 协议继续读取；否则保持原有的 `(T) value` 行为。这是一个最小改动且语义正确的修复：把"取单个值"和"以 StructLike 方式取值"这两种访问路径显式区分开来，让 struct 字段也能享受聚合下推的性能红利。

## 如何达成设计目的

实现非常精简：核心修改只有 `ValueAggregate.get()` 方法中加的一个 `if/else` 分支判断 `javaClass.isAssignableFrom(StructLike.class)`，是则返回 `this`、否则返回 `value`。这种"自身即 StructLike"的设计利用了 `ValueAggregate` 早已实现 `StructLike` 接口的事实（一个聚合值占位置 0），因此无需新增包装类。配套新增了一组针对 struct 字段聚合下推的端到端 Spark 测试（覆盖基本 struct、深度嵌套 struct、timestamp 类型 struct 子字段、以及分桶列场景），并提取了 `assertAggregates` 和 `assertExplainContains` 两个辅助方法来同时校验"聚合结果正确"和"执行计划确实走了下推"。

## 修改详情

### api/src/main/java/org/apache/iceberg/expressions/ValueAggregate.java

**修改目的**：修复 `ValueAggregate` 内部类 `get(int pos, Class<T> javaClass)` 方法在 struct 子字段聚合下推场景下的类型转换错误。

**工作逻辑**：
- 原实现只有 `return (T) value;`，对任何请求类型都强转 `value` 字段。
- 新实现增加分支：`if (javaClass.isAssignableFrom(StructLike.class)) return (T) this; else return (T) value;`。
- 关键点在于 `ValueAggregate` 自身实现了 `StructLike` 接口（在文件的其他部分定义），其 `get(int pos, Class<T>)` 会返回 `value`。因此当 Spark 以 `StructLike` 协议访问聚合结果时，调用 `ValueAggregate.get(0, StructLike.class)` 返回 `this`，再由调用方对该返回值调用 `get(0, Long.class)` 之类即可拿到真正的标量值。这一层间接正好适配了 struct 子字段被当作"结构"访问的协议。
- `isAssignableFrom` 的方向是"参数类型能否接受 `StructLike`"，即调用方传入的目标类是 `StructLike` 或其父类时才返回 `this`，对其它类型（Long、Integer 等）仍走原来的强转路径，不影响非 struct 场景。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/spark/sql/TestAggregatePushDown.java

**修改目的**：为 struct 字段聚合下推的修复新增端到端测试覆盖，并提取公共断言工具方法。

**工作逻辑**：
- 新增 4 个 `@TestTemplate` 测试方法：
  - `testAggregationPushdownStructInteger`：在 `STRUCT<c1:BIGINT>` 的子字段 `c1` 上做 `COUNT/MAX/MIN`，插入 3 行（其中 1 行 c1 为 NULL）校验聚合结果为 `(2L, 3L, 2L)`，并通过 `EXPLAIN` 校验执行计划中确实包含 `count/max/min(struct_with_int.c1)` 这些下推后的聚合算子。
  - `testAggregationPushdownNestedStruct`：构造 4 层嵌套 struct `STRUCT<c1:STRUCT<c2:STRUCT<c3:STRUCT<c4:BIGINT>>>>`，在 `c1.c2.c3.c4` 上聚合，校验深度嵌套场景同样能下推。
  - `testAggregationPushdownStructTimestamp`：在 `STRUCT<c1:TIMESTAMP>` 上做聚合，覆盖 timestamp 类型子字段，校验 min/max 返回正确的 `Timestamp` 对象。
  - `testAggregationPushdownOnBucketedColumn`：在分桶列 `bucket(8, id)` 上做聚合，校验分桶列同样能享受聚合下推（与 struct 字段不是同一问题，但作为补充覆盖加进来）。
- 新增 `assertAggregates(List<Object[]>, Object, Object, Object)` 辅助方法：从结果集取第一行的 count/max/min 三列，用 AssertJ 的 `assertThat` 配合 `as()` 描述逐一比对期望值，失败时有可读的断言描述。
- 新增 `assertExplainContains(List<Object[]>, String...)` 辅助方法：把 `EXPLAIN` 输出转小写后，对每个期望片段断言其存在于执行计划中，用于验证聚合确实被下推到了 Iceberg 扫描层（而不是回退到 Spark 上层聚合）。

## 小结

这是一个聚焦于"修一个具体 bug + 补一组端到端测试"的小而精的提交。修复本身只有 5 行代码，但解决的是 Iceberg 在 struct 子字段聚合下推路径上的类型协议不一致问题——`ValueAggregate` 既是单值容器又是 `StructLike`，原本的 `get` 方法没有区分这两种访问语义。修复通过让 `get` 在被请求 `StructLike` 类型时返回 `this`，巧妙复用了已有的 `StructLike` 实现，无需引入新的包装类。测试覆盖了基础 struct、深度嵌套 struct、timestamp 类型、分桶列四种场景，并通过 `EXPLAIN` 校验确保下推路径确实生效而不只是结果正确。这种"结果对 + 计划对"的双重断言模式是验证下推类优化的最佳实践。对下游的影响是：Spark 3.5 用户对 Iceberg 表的 struct 子字段做 `COUNT/MAX/MIN` 时将真正享受到元数据加速，而不是被迫读取全部数据文件。
