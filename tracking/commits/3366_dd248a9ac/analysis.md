# 提交 3366：Spark: Add sort_by parameter to rewrite_manifests procedure (#15467)

## 提交信息

- **序号**：3366 / 4088
- **哈希**：dd248a9ac97480bafe4d895973178046ef6d8375
- **短哈希**：dd248a9ac
- **日期**：2026-03-09
- **作者**：hemanthboyina
- **提交说明**：Spark: Add sort_by parameter to rewrite_manifests procedure (#15467)
- **PR/Issue**：#15467

## 总体目的

Iceberg 的 `rewrite_manifests` 存储过程用于重写清单文件，使数据文件在清单中按特定顺序排列，从而优化查询规划阶段（scan planning）的性能。默认情况下，重写后的清单会按分区规范中的所有分区转换字段进行排序。但在实际业务中，用户的查询模式往往集中在某几个特定的分区字段上，而非全部字段。如果清单按全部分区字段排序，就可能在跳过不必要的清单时产生次优效果。

本次提交为 `rewrite_manifests` 存储过程新增了 `sort_by` 参数，允许用户指定一组分区转换名称来对清单进行聚类。当查询频繁过滤某个分区字段时，将清单按该字段聚类可以显著减少扫描规划阶段需要读取的清单数量，从而提升查询效率。

此外，如果未设置 `sort_by`，则保持原有行为——按分区规范中所有分区转换的顺序排序，保证向后兼容。

## 如何达成设计目的

改动涉及三个方面：文档更新、存储过程参数定义与逻辑实现、测试覆盖。核心实现位于 `RewriteManifestsProcedure.java`，新增 `SORT_BY_PARAM` 可选参数（类型为 `STRING_ARRAY`），在调用 `RewriteManifests` action 时将排序字段传入；同时引入了空值校验，确保传入空数组时报错。文档中新增了参数说明和使用示例。测试侧覆盖了多列排序、单列排序、无效字段、空数组四种场景。

## 修改详情

### `docs/docs/spark-procedures.md` (+7/-0 lines)

**修改目的**：为 `rewrite_manifests` 存储过程文档补充 `sort_by` 参数说明和使用示例。

**工作逻辑**：
在参数表格中新增 `sort_by` 行，类型为 `array<string>`，说明其可选性和语义：传入分区转换名称列表以聚类清单，可减少规划时间；未设置时按分区规范中所有分区转换排序。文档还新增了一段 SQL 示例：
```sql
CALL catalog_name.system.rewrite_manifests(table => 'db.sample', sort_by => array('category'));
```
帮助用户理解如何按 `category` 分区字段重写清单以优化针对该字段的查询。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteManifestsProcedure.java` (+95/-0 lines)

**修改目的**：为 `sort_by` 参数新增端到端测试，覆盖正常用例和异常用例。

**工作逻辑**：
新增四个测试方法：
1. `testRewriteManifestsWithSortBy`：创建按 `(data, category)` 分区的表，插入 4 条数据产生 4 个清单，调用 `rewrite_manifests` 并指定 `sort_by => array('category', 'data')`，验证结果合并为 1 个清单。
2. `testRewriteManifestsWithSortBySingleColumn`：类似场景，但仅指定 `sort_by => array('category')`，验证同样合并为 1 个清单。
3. `testRewriteManifestsWithInvalidSortBy`：传入不存在的分区字段 `nonexistent`，验证抛出 `IllegalArgumentException` 且消息包含 "not found in current partition spec"。
4. `testRewriteManifestsWithEmptySortBy`：传入空数组 `array()`，验证抛出 `IllegalArgumentException` 且消息为 "sort_by must not be empty when provided"。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteManifestsProcedure.java` (+13/-1 lines)

**修改目的**：在存储过程中定义并传递 `sort_by` 参数。

**工作逻辑**：
新增 `import java.util.Arrays` 和 `Preconditions`。定义 `SORT_BY_PARAM` 为 `optionalInParameter("sort_by", STRING_ARRAY)`，并加入 `PARAMETERS` 数组。在 `call` 方法中通过 `input.asStringArray(SORT_BY_PARAM, null)` 读取参数值。在调用 action 的回调中，当 `sortBy != null` 时，先用 `Preconditions.checkArgument(sortBy.length > 0, "sort_by must not be empty when provided")` 校验非空，再调用 `action.sortBy(Arrays.asList(sortBy))` 传入排序字段。该逻辑保证未传参数时保持原有默认行为。

## 总结

本次提交为 Spark 的 `rewrite_manifests` 存储过程增加了 `sort_by` 参数，让用户可以按特定分区字段聚类清单，从而针对高频查询字段优化扫描规划性能。改动同时覆盖了文档、实现和测试，设计简洁且向后兼容，对实际生产环境中的查询加速有直接价值。
