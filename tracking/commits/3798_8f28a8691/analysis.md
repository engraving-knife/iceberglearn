# 提交 3798：extends compaction base class in sort compaction (#16593)

## 提交信息

- **序号**：3798 / 4088
- **哈希**：8f28a86914d6beaf2615cdf5797da0acb72ea4d9
- **短哈希**：8f28a8691
- **日期**：2026-05-29 07:36:05 -0700
- **作者**：Varun Lakhyani <130844282+varun-lakhyani@users.noreply.github.com>
- **提交说明**：extends compaction base class in sort compaction (#16593)
- **PR/Issue**：#16593

## 总体目的

本提交对 Spark 4.1 模块下的 `IcebergSortCompactionBenchmark` JMH 基准测试类进行重构，让其继承新引入的 `IcebergCompactionBenchmark` 基类，从而消除与其他 compaction 基准测试（如 bin-pack compaction benchmark）之间重复的样板代码。在重构之前，该类自行实现了 Spark 会话的启动与销毁、Hadoop 配置初始化、临时仓库目录创建、表初始化、数据追加、清理以及 `table()`、`spark()` 等工具方法，这些逻辑在多个 compaction benchmark 中几乎完全相同，造成代码冗余，难以维护。

通过提取公共基类并让排序压缩基准继承之，可以集中维护 Spark/Hadoop 环境搭建与拆解逻辑，统一迭代级别的 setup/teardown 行为，并减少未来新增基准测试时的复制粘贴成本。本提交本身只调整 `IcebergSortCompactionBenchmark` 这一个文件，但前提是已存在（或同时引入）了 `IcebergCompactionBenchmark` 基类。

## 如何达成设计目的

设计思路是经典的"提取父类"重构：将原本散落在 `IcebergSortCompactionBenchmark` 中的通用基础设施方法（`setupSpark`、`tearDownSpark`、`setupIteration`、`cleanUpIteration`、`initTable`、`appendData`、`table()`、`spark()`、`getCatalogWarehouse`、`cleanupFiles`、`writeData` 等）上移到 `IcebergCompactionBenchmark` 基类，子类只保留与排序压缩相关的具体逻辑（schema、排序顺序、数据生成、具体的 `@Benchmark` 方法），并通过 `@Override` 暴露必要的钩子方法（`tableName()`、`initTable()`、`appendData()`）。

## 修改详情

### `spark/v4.1/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergSortCompactionBenchmark.java` (+8/-90 lines)

**修改目的**：将通用基础设施代码上移到基类，使本类专注于排序压缩的基准逻辑。

**工作逻辑**：
- 类声明改为继承基类：
```java
public class IcebergSortCompactionBenchmark extends IcebergCompactionBenchmark {
```
- 移除大量不再需要的 import（`IOException`、`UncheckedIOException`、`Files`、`UUID`、`Configuration`、`Table`、`TestBase`、`SaveMode`、`SparkSession`、`Level`、`Setup`、`TearDown` 等）。
- 移除自定义的 `hadoopConf`、`spark` 字段以及 `setupBench`、`teardownBench`、`setupIteration`、`cleanUpIteration` 等 JMH 生命周期方法，由基类统一提供。
- 新增 `tableName()` override 返回固定表名 `NAME`：
```java
@Override
protected String tableName() {
  return NAME;
}
```
- 将 `initTable()` 与 `appendData()` 改为 `protected` 并加 `@Override`，由基类在迭代 setup 时调用。
- 删除 `writeData`、`table()`、`spark()`、`getCatalogWarehouse`、`cleanupFiles`、`setupSpark`、`tearDownSpark`、`initHadoopConf` 等方法，这些已由基类提供。

## 总结

本提交是一次纯粹的代码重构，不改变基准测试的运行行为，但显著降低了 Spark 4.1 模块下 compaction 基准测试的代码重复度。统一的基础设施有利于后续维护与新基准测试的添加，是项目代码质量持续提升的体现。
