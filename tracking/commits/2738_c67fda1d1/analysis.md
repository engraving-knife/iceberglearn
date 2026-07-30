# 提交 2738：Test: Fix package of TestParquetPartitionStatsHandler and TestOrcPartitionStatsHandler

## 提交信息

- **序号**：2738 / 4088
- **哈希**：c67fda1d1970f48f815f4a3afc3a8109ff392046
- **短哈希**：c67fda1d1
- **日期**：2025-10-13 10:18:25 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Test: Fix package of TestParquetPartitionStatsHandler and TestOrcPartitionStatsHandler
- **PR/Issue**：#14307

## 总体目的

这是一个测试代码组织的修复提交。在 Java 中，测试类的包声明（package）应该与其所在的文件目录结构一致，这是 Java 的基本约定。此前 `TestOrcPartitionStatsHandler` 和 `TestParquetPartitionStatsHandler` 两个测试类虽然分别位于 `orc/src/test/java/org/apache/iceberg/` 和 `parquet/src/test/java/org/apache/iceberg/` 目录下，但其 package 声明却是 `org.apache.iceberg`，而非各自模块对应的子包 `org.apache.iceberg.orc` 和 `org.apache.iceberg.parquet`。

这种不一致虽然不影响测试运行（因为测试类在各自的模块中），但违反了 Java 包与目录结构对应的约定，可能导致代码导航困难和维护混乱。本提交将这两个测试类移动到正确的子包目录下，并修改 package 声明使其与目录结构一致。

## 如何达成设计目的

通过以下步骤完成修复：
1. 将 `TestOrcPartitionStatsHandler.java` 从 `org/apache/iceberg/` 移动到 `org/apache/iceberg/orc/` 目录
2. 将 `TestParquetPartitionStatsHandler.java` 从 `org/apache/iceberg/` 移动到 `org/apache/iceberg/parquet/` 目录
3. 修改各自的 package 声明为对应的子包
4. 添加必要的 import 语句，因为类移到子包后，原先同包可直接访问的 `FileFormat` 和 `PartitionStatsHandlerTestBase` 需要显式导入

## 修改详情

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatsHandler.java` (重命名自 `org/apache/iceberg/TestOrcPartitionStatsHandler.java`) (+5/-1 lines)

**修改目的**：修复 ORC 分区统计处理器测试类的包声明。

**工作逻辑**：
- package 从 `org.apache.iceberg` 改为 `org.apache.iceberg.orc`
- 新增 `import org.apache.iceberg.FileFormat` 和 `import org.apache.iceberg.PartitionStatsHandlerTestBase`，因为移动到子包后这两个类不再同包，需要显式导入

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetPartitionStatsHandler.java` (重命名自 `org/apache/iceberg/TestParquetPartitionStatsHandler.java`) (+5/-1 lines)

**修改目的**：修复 Parquet 分区统计处理器测试类的包声明。

**工作逻辑**：
- package 从 `org.apache.iceberg` 改为 `org.apache.iceberg.parquet`
- 新增 `import org.apache.iceberg.FileFormat` 和 `import org.apache.iceberg.PartitionStatsHandlerTestBase`

## 总结

这是一个纯粹的测试代码组织修复，将两个分区统计处理器测试类移动到与其功能对应的子包（orc、parquet）下，使 package 声明与目录结构一致。改动简单但有助于代码组织的规范性，体现了项目对代码整洁度的要求。
