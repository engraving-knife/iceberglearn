# 提交 1897：Spark 3.4: Test metadata tables with format-version=v3 / add ExtensionsTestBase (#12600)

## 提交信息

- **序号**：1897 / 4088
- **哈希**：d14298fe9e826f67e0a2c4d8e279c6b032912726
- **短哈希**：d14298fe9
- **日期**：2025-03-21 13:18:54 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Test metadata tables with format-version=v3 / add ExtensionsTestBase (#12600)

  this backports #12135 to Spark 3.4 and also includes changes around switching from JUnit4 to JUnit5
- **PR/Issue**：#12600（backport #12135）

## 总体目的

这个提交将 #12135 的元数据表测试改进向后移植到 Spark 3.4，主要包括两个方面的改进：

1. **使用 format-version v3 测试元数据表**：扩展 `TestMetadataTables` 测试，使其覆盖 format-version v3 的场景。v3 引入了 DV（Deletion Vector）等新特性，元数据表的行为可能与 v2 不同，需要专门的测试覆盖。

2. **新增 `ExtensionsTestBase`**：创建一个抽象测试基类，用于 Spark extensions 相关的测试。这个基类封装了 Hive metastore 和 Spark session 的初始化逻辑，供 extensions 测试类继承使用。

3. **JUnit 4 到 JUnit 5 迁移**：提交说明中提到包含了从 JUnit 4 到 JUnit 5 的切换。

## 如何达成设计目的

- 在 `build.gradle` 中为 extensions 模块启用 JUnit Platform（JUnit 5 运行平台）
- 新增 `ExtensionsTestBase` 抽象类，继承 `CatalogTestBase`，封装 Hive metastore 和 Spark session（带 Iceberg 扩展）的启动逻辑
- 扩展 `TestMetadataTables` 测试，新增 v3 format-version 的测试用例
- 更新 `DVIterator` 和 `CatalogTestBase` 适配

## 修改详情

### `spark/v3.4/build.gradle` (修改, +4 lines)

**修改目的**：为 extensions 模块启用 JUnit 5 Platform。

**工作逻辑**：在 extensions 子项目的 test 配置中添加 `useJUnitPlatform()`，使 Gradle 使用 JUnit 5 运行测试。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ExtensionsTestBase.java` (新增, +70 lines)

**修改目的**：创建 extensions 测试的抽象基类。

**工作逻辑**：继承 `CatalogTestBase`，在 `@BeforeAll` 方法中初始化 Hive metastore 和带有 Iceberg Spark 扩展的 Spark session。配置包括：dynamic partition overwrite、Iceberg SQL extensions、Hive metastore URI、shuffle partitions 等。随机设置 AQE（自适应查询执行）开关以增加测试覆盖面。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeOnReadUpdate.java` (修改, +3/-1 lines)

**修改目的**：适配 ExtensionsTestBase 的引入。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMetadataTables.java` (修改, +480/-118 lines)

**修改目的**：使用 format-version v3 测试元数据表。

**工作逻辑**：大幅扩展元数据表测试，新增 v3 format-version 场景的测试用例，覆盖 DV 相关的元数据表行为。同时迁移到 JUnit 5 测试框架。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/DVIterator.java` (修改, +3 lines)

**修改目的**：小幅适配。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java` (修改, +2/-1 lines)

**修改目的**：适配 ExtensionsTestBase。

## 总结

本提交将元数据表 v3 测试向后移植到 Spark 3.4，新增 `ExtensionsTestBase` 测试基类，并完成 extensions 模块从 JUnit 4 到 JUnit 5 的迁移。这是 Spark 3.4 支持 v3 format-version 测试基础设施的重要部分。
