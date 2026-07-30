# 提交 1138：Spark 3.3, 3.4: Fix incorrect catalog loaded in TestCreateActions (#11049)

## 提交信息

- **序号**：1138 / 4088
- **哈希**：153b07069513d2cb772175594acb284fa9540387
- **短哈希**：153b07069
- **日期**：2024-09-09（Mon Sep 9 22:21:32 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.3, 3.4: Fix incorrect catalog loaded in TestCreateActions (#11049)
- **PR/Issue**：#11049（Back-port of #10952）
- **备注**：本提交是 #10952 的回迁版本，仅作用于 Spark 3.3 与 Spark 3.4 两个模块的测试代码。

## 总体目的

`TestCreateActions` 是 Spark 模块中验证 `migrate`/`snapshot` 等 Create Actions（把非 Iceberg 表迁移或快照为 Iceberg 表）的测试类，继承自 `SparkCatalogTestBase`，会被参数化地用多种 catalog 类型（`hive`、`hadoop`、`spark_catalog` 即 SessionCatalog）各跑一遍。

问题在于：当测试以 `spark_catalog`（SessionCatalog）方式运行时，部分用例会在测试过程中给 `spark_catalog` 设置一系列配置项，把它"改造"成 Iceberg 的 `SparkSessionCatalog`（带 Hive 元数据、默认 namespace、parquet-enabled、cache-enabled 等）。但测试结束后的 `@After after()` 方法只做了 `DROP TABLE`，没有把这些配置还原。结果是 Spark 的 `CatalogManager` 缓存里仍保留着上一次测试构造出的、被改造过的 `spark_catalog` 实例，导致后续测试在加载 `spark_catalog` 时拿到了"错误"的 catalog（行为已不是默认的 SessionCatalog），测试之间产生脏状态互相污染，甚至出现本应失败却通过、或本应通过却失败的不可靠结果。

本提交通过两处改动修复该问题：

1. 在 `after()` 中清理 catalog 状态：调用 `spark.sessionState().catalogManager().reset()` 重置 catalog 管理器缓存，并 `unset` 之前在测试中 set 的四项 `spark.sql.catalog.spark_catalog.*` 配置，使后续测试重新加载到干净的默认 SessionCatalog。
2. 在以 hadoop catalog 类型运行时跳过若干"迁移"类用例：这些用例（`testTwoLevelList`、`threeLevelList`、`threeLevelListWithNestedStruct`、`threeLevelLists`、`structOfThreeLevelLists`）本质上无法迁移到 hadoop 类型的 catalog，原先会因 catalog 错误而异常，现通过 `Assume.assumeTrue("Cannot migrate to a hadoop based catalog", !type.equals("hadoop"))` 显式跳过，使测试意图与运行条件一致。

本提交对 Spark 3.3 与 3.4 两个版本的 `TestCreateActions.java` 做了完全相同的改动（两个文件内容一致）。

## 如何达成设计目的

修改两个测试文件（Spark 3.3 与 3.4 各一份，内容相同）：

1. 在 `@After public void after()` 中、`DROP TABLE` 之后追加 5 行清理代码：重置 catalog 管理器并 unset 4 项配置。
2. 在 5 个迁移相关测试方法（1 个 `@Test` 公有方法 + 4 个 `private` 辅助方法）开头加 `Assume.assumeTrue(...)` 跳过 hadoop 类型。

## 修改详情

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java`

**修改目的**：清理测试间残留的 catalog 状态，并跳过 hadoop catalog 不适用的迁移用例。

**工作逻辑**：

1. `after()` 方法清理（共 5 行新增）：

```java
   @After
   public void after() throws IOException {
     // Drop the hive table.
     spark.sql(String.format("DROP TABLE IF EXISTS %s", baseTableName));
+    spark.sessionState().catalogManager().reset();
+    spark.conf().unset("spark.sql.catalog.spark_catalog.type");
+    spark.conf().unset("spark.sql.catalog.spark_catalog.default-namespace");
+    spark.conf().unset("spark.sql.catalog.spark_catalog.parquet-enabled");
+    spark.conf().unset("spark.sql.catalog.spark_catalog.cache-enabled");
   }
```

- `catalogManager().reset()`：让 Spark 的 `CatalogManager` 丢弃已缓存的 catalog 实例（尤其是被改造过的 `spark_catalog`），下次访问时按当前配置重建。
- unset 四项配置：把测试中为把 `spark_catalog` 改造为 Iceberg `SparkSessionCatalog` 而设置的 `type`/`default-namespace`/`parquet-enabled`/`cache-enabled` 还原为未设置状态，确保后续测试加载默认 SessionCatalog。

2. 跳过 hadoop catalog 的迁移用例（共 5 处，每处 2 行新增）：

```java
   @Test
   public void testTwoLevelList() throws IOException {
+    Assume.assumeTrue("Cannot migrate to a hadoop based catalog", !type.equals("hadoop"));
+
     spark.conf().set("spark.sql.parquet.writeLegacyFormat", true);
     ...
   }

   private void threeLevelList(boolean useLegacyMode) throws Exception {
+    Assume.assumeTrue("Cannot migrate to a hadoop based catalog", !type.equals("hadoop"));
+
     ...
   }
   // 同样地修改 threeLevelListWithNestedStruct、threeLevelLists、structOfThreeLevelLists
```

- `Assume.assumeTrue(...)`：JUnit 的假设断言，当条件不满足（即 `type.equals("hadoop")`）时该用例被标记为跳过而非失败。`type` 是 `SparkCatalogTestBase` 参数化注入的 catalog 类型字符串。

这些迁移类用例需要把目标表创建到一个 Iceberg catalog 中，而 hadoop 类型的 catalog 路径语义与"迁移"动作不兼容，原先会因 catalog 错误而异常；现显式跳过，测试结果更可读、更可靠。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java`

与 Spark 3.3 的改动完全相同（文件内容在该测试范围内一致）。同样是 `after()` 加 5 行清理 + 5 个方法各加 2 行 `Assume.assumeTrue`。

## 小结

- **成效**：`TestCreateActions` 在 Spark 3.3 与 3.4 中不再因 `spark_catalog` 被前序测试改造后未还原而加载到错误的 catalog，测试间状态隔离恢复正常；同时 hadoop catalog 类型下不适用的迁移用例被显式跳过，测试套件整体更可靠、更可读。
- **影响范围**：仅两个测试文件（Spark 3.3 与 3.4 各一份），每份新增 15 行（5 行 after 清理 + 5×2 行 Assume 跳过），共 30 行新增。无产品代码变更。
- **回迁到 1.4.x 的注意事项**：本提交本身就是一次回迁（从 main 的 #10952 回迁到 Spark 3.3/3.4）。1.4.x 若仍包含 Spark 3.3/3.4 模块且该测试存在同类问题，**建议回迁**以保证测试可靠性。回迁时需注意：1.4.x 的 Spark 3.3/3.4 测试基类 `SparkCatalogTestBase` 是否提供相同的 `type` 参数与 `catalogManager().reset()` API（Spark 3.3/3.4 均支持）；若 1.4.x 已删除 Spark 3.3 或 3.4 模块，则相应文件无需回迁。本改动仅影响测试，无运行时风险。
