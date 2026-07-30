# 提交 3140：Spark 4.1: Add tests for MERGE INTO schema evolution nested case (#15028)

## 提交信息

- **序号**：3140 / 4088
- **哈希**：135659c2d2711a3673a48440609d2e02d8dab376
- **短哈希**：135659c2d
- **日期**：2026-01-21
- **作者**：Varun Lakhyani
- **提交说明**：Spark 4.1: Add tests for MERGE INTO schema evolution nested case (#15028)
- **PR/Issue**：#15028

## 总体目的

本提交针对 Spark 4.1 的 `MERGE WITH SCHEMA EVOLUTION INTO` 能力补齐一个此前缺失的测试方向——嵌套 struct 字段在“源表字段比目标表少”时的演化行为。仓库中既有的测试 `testMergeWithSchemaEvolutionNestedStruct` 只覆盖了源 struct 比目标 struct 字段更多的方向（即源带 `c3`、目标不带，演化为目标新增 `c3`），但对反方向——目标 struct 已有 `c3` 而源 struct 没有 `c3`——却没有用例验证。这一方向在真实数据管道中很常见：上游源系统结构精简、下游 Iceberg 表结构更丰富，MERGE 时需要在 UPDATE 命中行保留目标中存在而源中缺失的嵌套字段、在 INSERT 新行时把缺失嵌套字段填为 null。缺乏该测试意味着 Spark 4.1 的嵌套类型合并强制转换（`spark.sql.mergeNestedTypeCoercion.enabled`）与 Iceberg schema evolution 的协同在该方向上缺少回归保护。

本提交通过重命名既有用例使其语义更精确（`...NestedStruct` → `...NestedStructSourceHasMoreFields`），并新增对称用例 `testMergeWithSchemaEvolutionNestedStructSourceHasFewerFields`，把双向嵌套字段演化都锁定下来。改动仅限测试代码，不涉及生产逻辑变更。

## 如何达成设计目的

思路是在 `TestMergeSchemaEvolution` 中复用既有的建表/建视图/执行 MERGE/断言结果框架，构造目标 struct 含 3 个字段（`c1,c2,c3`）、源 struct 含 2 个字段（`c1,c2`）的场景，开启 Spark 的 `spark.sql.mergeNestedTypeCoercion.enabled=true` 以允许 struct 大小不一致时的类型强转，执行 `MERGE WITH SCHEMA EVOLUTION INTO ... WHEN MATCHED UPDATE SET * WHEN NOT MATCHED INSERT *`，然后断言：命中行保留目标的 `c3`、未命中行 `c3` 为 null、目标原有未匹配行保持不变。同时把既有“源字段更多”的用例重命名以体现对称性。

## 修改详情

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeSchemaEvolution.java` (+41/-1 lines)

**修改目的**：补齐嵌套 struct “源字段更少”方向的 schema evolution 测试，并重命名既有用例以体现对称语义。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap`，用于设置 Spark SQL conf。
- 将既有用例 `testMergeWithSchemaEvolutionNestedStruct` 重命名为 `testMergeWithSchemaEvolutionNestedStructSourceHasMoreFields`，用例本身逻辑不变：目标 struct 为 `STRUCT<c1:INT,c2:STRING>`、源 struct 为 `STRUCT<c1:INT,c2:STRING,c3:INT>`，MERGE 后目标演化为带 `c3`，命中行更新为新值、未命中保留旧行（`c3` 为 null）、新插入行带 `c3`。
- 新增用例 `testMergeWithSchemaEvolutionNestedStructSourceHasFewerFields`：
  - `assumeThat(branch).as("Schema evolution does not work for branches currently").isNull();` 跳过分支场景（schema evolution 当前不支持分支）。
  - 目标表 `id INT, s STRUCT<c1:INT,c2:STRING,c3:INT>`，数据 `id=1 (100,"aa",1000)`、`id=2 (200,"bb",2000)`。
  - 源视图 `id INT, s STRUCT<c1:INT,c2:STRING>`，数据 `id=1 (10,"a")`、`id=3 (30,"c")`。
  - 在 `withSQLConf(ImmutableMap.of("spark.sql.mergeNestedTypeCoercion.enabled","true"), ...)` 中执行 MERGE。该 conf 是关键：默认情况下 Spark 不允许源与目标 struct 字段数不一致的合并，开启后才允许对嵌套类型做大小不一致的强制转换，从而让 Iceberg 的 schema evolution 能处理“源少字段”场景。
  - 断言结果：`id=1 → (10,"a",1000)`（命中更新，源缺失的 `c3` 保留目标原值 1000）；`id=2 → (200,"bb",2000)`（未匹配，原样保留）；`id=3 → (30,"c",null)`（新插入，源无 `c3` 故为 null）。这验证了 UPDATE 保留目标嵌套字段、INSERT 缺失字段填 null 的语义。

## 总结

该提交以纯测试改动补齐了 Spark 4.1 MERGE WITH SCHEMA EVOLUTION 在嵌套 struct “源字段更少”方向上的回归覆盖，与既有“源字段更多”用例形成对称，并显式开启 `spark.sql.mergeNestedTypeCoercion.enabled` 验证了 UPDATE 保留目标字段、INSERT 缺失字段填 null 的行为，强化了 Iceberg 与 Spark 嵌套类型 schema evolution 协同的正确性保障。
