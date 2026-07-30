# 提交 3114：Spark: Add test coverage for Hive View catalog (#15048)

## 提交信息

- **序号**：3114 / 4088
- **哈希**：b62802faf9edc2aca89a85a1ab0a6e13e1b61472
- **短哈希**：b62802faf
- **日期**：2026-01-14
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Add test coverage for Hive View catalog (#15048)
- **PR/Issue**：#15048

## 总体目的

本提交为 Spark 4.1 的视图（View）相关测试补齐对 Hive View catalog 的覆盖。此前 `TestViews` 这套参数化测试只针对 REST catalog（`SPARK_SESSION_WITH_VIEWS`，type=rest）以及会话 catalog 跑视图场景，并没有覆盖到以 Hive catalog 作为视图存储后端的情况。Iceberg 的 `HiveCatalog` 支持视图，但其在标识符大小写处理、命名空间要求、以及子查询表达式执行等方面与 REST/JDBC catalog 存在差异，缺乏测试意味着这些差异行为未被验证，回归风险难以察觉。

本提交新增了一个 `SPARK_WITH_HIVE_VIEWS` 测试 catalog 配置（type=hive），把它加入 `TestViews` 的参数化 catalog 矩阵，使全部视图测试自动在 Hive catalog 上重跑。同时对少数在 Hive 上确有已知问题的场景做了合理跳过（子查询表达式因 Hive 的 `FileInputFormat` 实例化异常而跳过），并对 `showViews`/`showViewsWithCurrentNamespace` 中受 Hive 大小写不敏感与命名空间要求影响的断言做了适配（视图名改为 snake_case、临时视图不再强制小写、命名空间条件纳入 hive、`spark_catalog` 命名空间检查收窄到特定 catalog）。整体目的是在不过度放宽断言的前提下，把 Hive View catalog 纳入持续测试，提升视图功能的跨 catalog 一致性保障。

## 如何达成设计目的

在 `SparkCatalogConfig` 枚举中新增 `SPARK_WITH_HIVE_VIEWS` 配置项（catalog 名 `spark_hive_with_views`，实现 `SparkCatalog`，type=hive），并在 `TestViews.catalogConfigs()` 参数矩阵中追加该配置，让所有 `@TestTemplate` 用例在该 catalog 下重复执行。对两个子查询表达式用例用 AssertJ 的 `assumeThat` 在 catalog 类型为 hive 时跳过；对 `SHOW VIEWS` 相关用例，把 camelCase 视图名改为 snake_case（规避 Hive 把标识符强制小写导致的比较不一致），去掉临时视图的 `toLowerCase(Locale.ROOT)`，把"需要命名空间"的判断从仅排除 rest 扩展为同时排除 hive，并把"在 spark_catalog 下创建命名空间并校验"的条件从"非 SPARK_CATALOG"收窄为"等于 SPARK_WITH_VIEWS 的 catalog 名"，使断言精确匹配真正适用的 catalog。

## 修改详情

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/SparkCatalogConfig.java` (+5/-1 lines)

**修改目的**：新增 Hive View catalog 测试配置。

**工作逻辑**：在枚举中 `SPARK_SESSION_WITH_VIEWS` 之后新增 `SPARK_WITH_HIVE_VIEWS("spark_hive_with_views", SparkCatalog.class.getName(), ImmutableMap.of("type", "hive", "default-namespace", "default", "cache-enabled", "false"))`。与 `SPARK_SESSION_WITH_VIEWS`（type=rest、用 SparkSessionCatalog）不同，这里用独立的 catalog 名 `spark_hive_with_views`、`SparkCatalog` 实现、type=hive，专门用于在 Hive catalog 后端跑视图测试。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (+24/-19 lines)

**修改目的**：将 Hive View catalog 纳入参数化测试，并适配其行为差异。

**工作逻辑**：
- **参数矩阵**：在 `catalogConfigs()` 返回数组末尾追加 `{ SPARK_WITH_HIVE_VIEWS.catalogName(), .implementation(), .properties() }`，使全部视图用例在 Hive catalog 上重跑。
- **子查询表达式用例跳过**：`createViewWithSubqueryExpressionInFilterThatIsRewritten` 与 `createViewWithSubqueryExpressionInQueryThatIsRewritten` 开头增加 `assumeThat(catalogConfig.get(CatalogUtil.ICEBERG_CATALOG_TYPE)).as("Executing subquery expression with Hive fails due to an exception when instantiating the FileInputFormat").isNotEqualTo("hive");`，因为 Hive 执行子查询表达式时实例化 `FileInputFormat` 会抛异常，属于已知差异，故跳过而非误报失败。
- **`showViews` 适配**：视图名由 camelCase（`prefixV2`/`prefixV3`/`globalViewForListing`/`tempViewForListing`）改为 snake_case（`prefix_v2`/`prefix_v3`/`global_view_for_listing`/`temp_view_for_listing`），避免 Hive 把标识符强制小写后与原始大小写名比较不一致；删除 `import java.util.Locale` 与临时视图的 `tempViewForListing.toLowerCase(Locale.ROOT)`，新增 `globalView` 行对象；"需要命名空间"的判断由 `!"rest".equals(...)` 改为提取 `catalogType` 后 `!"rest".equals(catalogType) && !"hive".equals(catalogType)`（Hive 同样要求命名空间）；`spark_catalog` 命名空间检查由 `if (!catalogName.equals(SPARK_CATALOG))` 收窄为 `if (catalogName.equals(SparkCatalogConfig.SPARK_WITH_VIEWS.catalogName()))`，仅在该特定 catalog 下执行；`global_temp` 断言改用未小写的 `globalView`。
- **`showViewsWithCurrentNamespace` 适配**：`viewOne`/`viewTwo` 改为 `view_one`/`view_two`，对应 `SHOW VIEWS LIKE 'view_one*'`/`'view_two*'` 同步更新，原因同样是 Hive 大小写不敏感。

## 总结

本提交通过新增 `SPARK_WITH_HIVE_VIEWS` 测试 catalog 配置并将其加入 `TestViews` 参数化矩阵，把 Hive View catalog 纳入 Spark 4.1 视图测试的持续覆盖；同时对子查询表达式（Hive FileInputFormat 已知异常）、`SHOW VIEWS`（Hive 大小写不敏感与命名空间要求）等差异场景做了精准的跳过与断言适配。核心价值在于补齐 Hive catalog 视图行为的测试盲区，使视图功能在 REST 与 Hive 两种后端下都能得到回归保护。
