# 提交 2296：Spark 3.4: Backport #13061 fix for row lineage inheritance in distributed planning (#13436)

## 提交信息

- **序号**：2296 / 4088
- **哈希**：28b90ea1870643fcdb3afca5426656ab6caa8163
- **短哈希**：28b90ea18
- **日期**：2025-06-30 21:20:58 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.4: Backport #13061 fix for row lineage inheritance in distributed planning (#13436)
- **PR/Issue**：#13436

## 总体目的

本提交将 PR #13061 中关于行谱系（Row Lineage）在分布式规划（distributed planning）下的继承修复回移植到 Spark 3.4 分支。行谱系是 Iceberg v3 表格式的重要特性，通过为每行分配唯一标识符（row ID）来支持行级操作的正确性。

在分布式规划模式下，Manifest 文件需要在 Spark 任务之间序列化传输。此前 `ManifestFileBean` 类（用于在 Spark 分布式环境中传递 Manifest 元数据的 Java Bean）缺少 `firstRowId` 字段，导致行谱系信息在分布式规划过程中丢失，从而影响行级操作的正确性。

## 如何达成设计目的

修复分为两部分：

1. **为 `ManifestFileBean` 添加 `firstRowId` 字段**：确保 Manifest 文件的 `firstRowId` 属性在 Spark 序列化传输过程中不丢失。`ManifestFileBean` 是一个实现了 `ManifestFile` 接口的可序列化 Java Bean，用于在 Spark 的分布式执行环境中传递 Manifest 元数据。

2. **扩展测试参数化以覆盖分布式规划模式**：在 `TestRowLevelOperationsWithLineage` 测试类中添加参数化测试配置，使其同时测试 LOCAL 和 DISTRIBUTED 两种规划模式，确保行谱系继承在两种模式下都能正确工作。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+11/-0 lines)

**修改目的**：为 `ManifestFileBean` 添加 `firstRowId` 字段支持。

**工作逻辑**：
- 新增 `private Long firstRowId = null` 字段
- 在 `fromManifest` 工厂方法中，调用 `bean.setFirstRowId(manifest.firstRowId())` 从原始 Manifest 文件中复制 `firstRowId`
- 添加 `setFirstRowId` setter 方法和 `firstRowId()` 的接口实现方法

这样确保了在 Spark 分布式环境中通过 `ManifestFileBean` 传递 Manifest 元数据时，`firstRowId` 信息被正确保留。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (+41/-0 lines)

**修改目的**：扩展测试以覆盖分布式规划模式下的行谱系继承。

**工作逻辑**：
- 重写 `parameters()` 方法，提供两组测试参数：
  - 第一组：LOCAL 规划模式 + HASH 分布模式 + 向量化读取启用 + format version 3
  - 第二组：DISTRIBUTED 规划模式 + RANGE 分布模式 + 非向量化读取 + format version 3
- 添加了相关的导入语句，包括 `PlanningMode.DISTRIBUTED`、`PlanningMode.LOCAL`、写入分布模式常量和 `SparkCatalog` 类

通过两组参数的对比测试，验证行谱系继承在 LOCAL 和 DISTRIBUTED 两种规划模式下都能正确工作。

## 总结

本提交是行谱系继承修复的 Spark 3.4 回移植。核心问题是 `ManifestFileBean` 在分布式规划模式下缺少 `firstRowId` 字段导致行谱系信息丢失。通过添加该字段并扩展测试覆盖分布式规划模式，确保了行谱系特性在 Spark 3.4 的所有规划模式下都能正确工作。
