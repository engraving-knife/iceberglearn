# 提交 0489：Spark: Avoid NPE when catalog config doesn't have "type" set (#9676)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0489 |
| 完整哈希 | 0cab58b04f4c86e556ff2f4aea7a9dbeee801ce7 |
| 短哈希 | 0cab58b04 |
| 日期 | 2024-02-07（Wed Feb 7 10:35:35 2024 +0100） |
| 作者 | Eduard Tudenhoefner <etudenhoefner@gmail.com> |
| 说明 | Spark: Avoid NPE when catalog config doesn't have "type" set (#9676) |
| PR | #9676 |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`

## 总体目的

本提交修复测试基类 `TestBaseWithCatalog` 中一处潜在的空指针异常（NPE）：当 catalog 配置中没有设置 `type` 键时，原代码会对 `null` 调用 `equalsIgnoreCase` 而抛出 `NullPointerException`。修复方式是把字符串比较的字面量与变量调换位置——从 `catalogConfig.get("type").equalsIgnoreCase("hadoop")` 改为 `"hadoop".equalsIgnoreCase(catalogConfig.get("type"))`，利用"字面量在左"的写法（常被称为 Yoda condition）使 `Map.get` 返回 `null` 时不再触发 NPE，而是安全地返回 `false`。

`TestBaseWithCatalog` 是 Spark 3.5 测试模块中所有带 catalog 的测试类的公共基类，其构造逻辑会读取 `catalogConfig` 这个 `Map<String, String>`，逐项写入 Spark session 配置（`spark.sql.catalog.<name>.<key>`），并特别地当 `type` 为 `"hadoop"` 时额外设置 `warehouse` 指向本地文件路径（`file:<warehouse>`）。问题在于：并非所有 catalog 配置都显式设置 `type`（例如某些 catalog 实现通过实现类名 `catalog-impl` 而非 `type` 来标识，或者测试场景本身就不需要 type）。当 `catalogConfig` 不含 `type` 键时，`catalogConfig.get("type")` 返回 `null`，旧表达式 `null.equalsIgnoreCase("hadoop")` 直接抛 NPE，导致基类初始化失败、相关测试无法运行。

提交说明明确提到"我们在 `SparkTestBaseWithCatalog` 中也做了同样的检查"——即项目另一个测试基类 `SparkTestBaseWithCatalog`（位于 api/test 或更早的测试模块）已经采用了字面量在左的防御性写法，本提交把 `TestBaseWithCatalog` 对齐到同样的写法，消除两处基类之间的不一致。这是一个典型的"用比较顺序消除 null 风险"的防御性编程修复：`"hadoop".equalsIgnoreCase(null)` 返回 `false` 而不抛异常，因此当 type 未设置时分支自然跳过、不设置 warehouse，符合预期。

## 如何达成设计目的

实现路径是单点修改：在 `TestBaseWithCatalog` 初始化 catalog 配置的代码块中，把条件判断从 `catalogConfig.get("type").equalsIgnoreCase("hadoop")` 调换为 `"hadoop".equalsIgnoreCase(catalogConfig.get("type"))`。由于 `String.equalsIgnoreCase(Object)` 接受 `null` 参数并返回 `false`（不会对入参解引用），调换后即使 `catalogConfig.get("type")` 为 `null` 也只是让 `if` 条件为 `false` 而跳过 warehouse 设置，不再抛 NPE。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`

**修改目的**：消除当 `catalogConfig` 不含 `type` 键时初始化基类抛 NPE 的风险。

**工作逻辑**：

- 该文件位于 `spark/v3.5/spark/src/test/java/`，是 Spark 3.5 测试模块的 catalog 测试基类。在初始化阶段有如下逻辑（修改前）：
  ```java
  catalogConfig.forEach(
      (key, value) -> spark.conf().set("spark.sql.catalog." + catalogName + "." + key, value));

  if (catalogConfig.get("type").equalsIgnoreCase("hadoop")) {
    spark.conf().set("spark.sql.catalog." + catalogName + ".warehouse", "file:" + warehouse);
  }
  ```
  即先把 `catalogConfig` 的每一项写入 Spark session 的 catalog 配置前缀，再判断 type 是否为 hadoop，若是则补设 warehouse 为本地文件路径（因为 hadoop catalog 需要一个文件系统 warehouse）。
- 修改将条件改为：
  ```java
  if ("hadoop".equalsIgnoreCase(catalogConfig.get("type"))) {
    spark.conf().set("spark.sql.catalog." + catalogName + ".warehouse", "file:" + warehouse);
  }
  ```
- 关键差异：`String.equalsIgnoreCase(Object other)` 在 `other` 为 `null` 时返回 `false`（内部会先判空），因此 `"hadoop".equalsIgnoreCase(null)` 安全返回 `false`；而旧写法 `null.equalsIgnoreCase("hadoop")` 会对 `null` 接收者解引用抛 NPE。修改后，当 `catalogConfig` 不含 `type` 键（`get` 返回 `null`）时，条件安全地为 `false`、跳过 warehouse 设置，基类初始化正常完成。
- 该写法与项目另一测试基类 `SparkTestBaseWithCatalog` 中已有的同类检查保持一致，消除两处基类对 `type` 缺失处理方式的不一致，便于维护。

## 小结

本提交修复 Spark 3.5 测试基类 `TestBaseWithCatalog` 中一处潜在 NPE：当 `catalogConfig` 未设置 `type` 键时，原 `catalogConfig.get("type").equalsIgnoreCase("hadoop")` 会对 `null` 解引用抛异常。修复方式是调换比较顺序为 `"hadoop".equalsIgnoreCase(catalogConfig.get("type"))`，利用 `equalsIgnoreCase` 接受 `null` 入参返回 `false` 的特性，使 type 缺失时安全跳过 warehouse 设置。该写法与项目另一基类 `SparkTestBaseWithCatalog` 已有的同类检查对齐。属测试基础设施的防御性修复，回迁 1.4.x 风险极低。
