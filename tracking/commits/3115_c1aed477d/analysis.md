# 提交 3115：Spark 3.5,4.0: Add test coverage for Hive View catalog (#15052)

## 提交信息

- **序号**：3115 / 4088
- **哈希**：c1aed477d1c0c8ef93aa8dea23f75e5ab11f5be6
- **短哈希**：c1aed477d
- **日期**：2026-01-14
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.5,4.0: Add test coverage for Hive View catalog (#15052)
- **PR/Issue**：#15052

## 总体目的

本提交是上一个提交 3114（PR #15048，为 Spark 4.1 补齐 Hive View catalog 测试覆盖）向 Spark 3.5 与 Spark 4.0 的回移（backport）。Iceberg 项目为每个受支持的 Spark 版本（v3.4/v3.5/v4.0/v4.1）维护独立的源码树，测试代码不共享，因此 #15048 在 spark/v4.1 模块做的改动需要分别在 spark/v3.5 与 spark/v4.0 模块重做，才能让这两个版本也获得同等的 Hive View catalog 测试覆盖。回移的原因很直接：保持各 Spark 版本测试矩阵一致，避免只在 4.1 上发现 Hive catalog 视图回归而 3.5/4.0 漏测。

回移的内容与 3114 完全对应：在 v3.5 与 v4.0 的 `SparkCatalogConfig` 中新增 `SPARK_WITH_HIVE_VIEWS` 配置，在 `TestViews` 中将其加入参数化 catalog 矩阵，对子查询表达式用例在 hive 类型下跳过，对 `showViews`/`showViewsWithCurrentNamespace` 做 snake_case 改名与断言适配。差异在于 v3.5 的 `TestViews` 还伴随了一批 google-java-format 的格式化收敛（把多处 `hasMessageContaining(String.format(...))`/`hasMessageStartingWith(String.format(...))` 由多行折回单行），属于随构建格式化产生的附带清理，非逻辑变更。

## 如何达成设计目的

对 spark/v3.5 与 spark/v4.0 两个模块分别应用与 3114 相同的逻辑改动：`SparkCatalogConfig` 新增 `SPARK_WITH_HIVE_VIEWS` 枚举（并对 v3.5/v4.0 中既有的 `SPARK` hive 配置做 spotless 多行格式化），`TestViews` 加入新 catalog 配置、子查询用例跳过 hive、`showViews` 改 snake_case 与断言适配、`showViewsWithCurrentNamespace` 改名。v3.5 额外包含 `TestViews` 中多处断言格式化的附带改动。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java` (+11/-6 lines)

**修改目的**：为 Spark 3.5 新增 Hive View catalog 测试配置。

**工作逻辑**：新增 `SPARK_WITH_HIVE_VIEWS("spark_hive_with_views", SparkCatalog.class.getName(), ImmutableMap.of("type", "hive", "default-namespace", "default", "cache-enabled", "false"))`，与 3114 中 v4.1 的定义一致；同时对既有 `SPARK`（type=hive）配置做 spotless 格式化——把 `ImmutableMap.of(...)` 的键值对从紧凑单行改为每参数一行的展开形式，并把 `"false"` 注释的缩进对齐，纯格式调整无行为变化。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (+47/-55 lines)

**修改目的**：将 Hive View catalog 纳入 Spark 3.5 的视图测试并适配差异。

**工作逻辑**：与 3114 中 v4.1 的 `TestViews` 改动逻辑一致——参数矩阵追加 `SPARK_WITH_HIVE_VIEWS`；`createViewWithSubqueryExpressionInFilterThatIsRewritten`/`createViewWithSubqueryExpressionInQueryThatIsRewritten` 用 `assumeThat(...).isNotEqualTo("hive")` 跳过；`showViews` 视图名改 snake_case、临时视图去 `toLowerCase`、命名空间条件纳入 hive、`spark_catalog` 检查收窄到 `SPARK_WITH_VIEWS`；`showViewsWithCurrentNamespace` 改 `view_one`/`view_two` 与对应 LIKE 模式。此外伴随多处 spotless 格式化：把 `hasMessageContaining(String.format("...", args))`、`hasMessageStartingWith(String.format("...", args))` 等由多行折回单行（`String.format` 仍在，仅去换行），涉及约十几处断言，属格式清理无逻辑变化。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java` (+11/-6 lines)

**修改目的**：为 Spark 4.0 新增 Hive View catalog 测试配置。

**工作逻辑**：与 v3.5 同名文件改动一致——新增 `SPARK_WITH_HIVE_VIEWS`，并对既有 `SPARK` hive 配置做相同的 spotless 多行格式化。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (+24/-19 lines)

**修改目的**：将 Hive View catalog 纳入 Spark 4.0 的视图测试并适配差异。

**工作逻辑**：与 3114 中 v4.1 的 `TestViews` 改动完全一致——参数矩阵追加 `SPARK_WITH_HIVE_VIEWS`、两个子查询用例跳过 hive、`showViews`/`showViewsWithCurrentNamespace` 的 snake_case 改名与断言适配。v4.0 的 `TestViews` 此前与 v4.1 同形，故无 v3.5 那样的额外格式化噪声。

## 总结

本提交是把 3114（Spark 4.1 的 Hive View catalog 测试覆盖）回移到 Spark 3.5 与 4.0 的 backport：在两个版本的 `SparkCatalogConfig` 新增 `SPARK_WITH_HIVE_VIEWS` 配置、在 `TestViews` 中纳入参数化矩阵并对子查询跳过与 `SHOW VIEWS` 差异做适配。其核心价值在于让 Iceberg 支持的三个 Spark 版本（3.5/4.0/4.1）对 Hive catalog 视图功能保持一致的测试覆盖，避免单版本覆盖带来的回归盲区；v3.5 附带的 spotless 格式化为随构建产生的清理，不影响逻辑。
