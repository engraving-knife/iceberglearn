# 提交 2743：Test: Remove unused methods and fix typo

## 提交信息

- **序号**：2743 / 4088
- **哈希**：58940f3cace188f0f90c47749f187e00f65ec546
- **短哈希**：58940f3ca
- **日期**：2025-10-13 09:55:39 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Test: Remove unused methods and fix typo
- **PR/Issue**：#14305

## 总体目的

这是一个测试代码清理提交，主要做了两件事：移除 Parquet 测试工具类中不再被使用的辅助方法，以及修复一个测试方法名中的拼写错误。

在代码维护过程中，随着测试的演进，一些曾经被使用的辅助方法可能变得不再被任何测试调用。这些死代码增加了维护负担，也容易给后续开发者造成困惑。定期清理未使用的代码是保持代码库健康的重要实践。同时，拼写错误虽然不影响功能，但影响代码可读性和专业性，尤其是测试方法名中的拼写错误可能导致理解偏差。

## 如何达成设计目的

清理工作分三个部分：
1. 移除 `ParquetWritingTestUtils` 中 3 个未使用的 `writeRecords` 重载方法
2. 移除 `TestVariantReaders` 中未使用的 `checkListType` 私有方法
3. 修复 `TestCDHParquetStatistics` 中测试方法名 `testCDHParquetStatistcs` 的拼写错误（缺少 `i`）

## 修改详情

### `parquet/src/test/java/org/apache/iceberg/parquet/ParquetWritingTestUtils.java` (+0/-24 lines)

**修改目的**：移除 3 个未使用的 `writeRecords` 重载方法。

**工作逻辑**：移除以下三个方法：
- `writeRecords(Path, Schema, GenericData.Record...)`：无 properties 和 createWriterFunc 的简化版本
- `writeRecords(Path, Schema, Map<String, String>, GenericData.Record...)`：带 properties 但无 createWriterFunc 的版本
- `writeRecords(Path, Schema, Map<String, String>, Function, GenericData.Record...)`：完整参数版本，创建临时文件并调用 `write` 方法

同时移除不再需要的 `Collections` import。这些方法不再被任何测试调用，保留的 `write` 方法仍然可用。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestCDHParquetStatistics.java` (+1/-1 lines)

**修改目的**：修复测试方法名拼写错误。

**工作逻辑**：将方法名 `testCDHParquetStatistcs` 修正为 `testCDHParquetStatistics`（在 `Statist` 和 `cs` 之间添加缺失的 `i`）。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantReaders.java` (+0/-16 lines)

**修改目的**：移除未使用的 `checkListType` 私有方法。

**工作逻辑**：移除 `checkListType(GroupType listType)` 方法，该方法用于验证 Parquet list 类型的 3 层结构（检查是否包含单个 repeated 字段和单个 required 子字段），但不再被任何测试调用。

## 总结

本提交清理了 Parquet 测试模块中的死代码和拼写错误。移除了 3 个未使用的 `writeRecords` 方法和 1 个未使用的 `checkListType` 方法（共 40 行代码），修复了测试方法名中的拼写错误。这是常规的代码卫生维护，有助于保持测试代码库的整洁和可维护性。
