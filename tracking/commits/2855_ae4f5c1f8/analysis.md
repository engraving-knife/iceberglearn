# 提交 2855：Test: Remove redundant String.format for AssertJ (#14544)

## 提交信息

- **序号**：2855 / 4088
- **哈希**：ae4f5c1f8893f199b633f3ccbc6ddd94170db596
- **短哈希**：ae4f5c1f8
- **日期**：2025-11-09 16:12:16 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Test: Remove redundant String.format for AssertJ (#14544)
- **PR/Issue**：#14544

## 总体目的

这个提交清理了测试代码中大量冗余的 `String.format()` 调用。AssertJ 的断言方法（如 `hasMessage`、`hasMessageContaining`、`as` 等）本身支持格式化字符串参数，内部会自动进行 `String.format` 处理。因此，在外部再包裹一层 `String.format()` 是完全多余的，不仅增加了代码冗余，还影响了可读性。

这是一个纯代码质量改进提交，不影响任何功能逻辑，仅让测试代码更简洁、更符合 AssertJ 的惯用写法。

## 如何达成设计目的

通过批量修改 21 个测试文件，将所有形如 `hasMessage(String.format("...", args))` 的调用改为 `hasMessage("...", args)`，即直接将格式字符串和参数传给 AssertJ 方法，由 AssertJ 内部完成格式化。修改覆盖了 api、core、aws、hive、spark 等多个模块的测试代码。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestHelpers.java` (+1/-3 lines)

**修改目的**：移除 `as()` 描述调用中的冗余 `String.format`。

**工作逻辑**：将 `.as(String.format("Should be the same schema. Schema 1: %s, schema 2: %s", schema1, schema2))` 改为 `.as("Should be the same schema. Schema 1: %s, schema 2: %s", schema1, schema2)`，直接使用 AssertJ 的可变参数格式化能力。

### `core/src/test/java/org/apache/iceberg/TestBaseIncrementalAppendScan.java` (+7/-14 lines)

**修改目的**：移除增量扫描测试中多处 `hasMessage`/`hasMessageContaining` 调用的冗余 `String.format`。

**工作逻辑**：将多处以 `String.format` 包裹的断言消息改为直接传递格式字符串和参数。例如 `.hasMessage(String.format("Ref %s is not a tag", branchName))` 改为 `.hasMessage("Ref %s is not a tag", branchName)`。

### `core/src/test/java/org/apache/iceberg/TestFormatVersions.java` (+2/-4 lines)

**修改目的**：移除格式版本测试中的冗余 `String.format`。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java` (+1/-2 lines)

**修改目的**：移除 LocationProvider 测试中的冗余 `String.format`。

### `core/src/test/java/org/apache/iceberg/TestRewriteFiles.java` (+1/-2 lines)

**修改目的**：移除文件重写测试中的冗余 `String.format`。

### `core/src/test/java/org/apache/iceberg/TestSortOrder.java` (+1/-1 lines)

**修改目的**：移除排序顺序测试中的冗余 `String.format`。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java` (+2/-6 lines)

**修改目的**：移除更新需求验证测试中的冗余 `String.format`。

### 其他测试文件

包括 `TestStaticTable.java`、`TestJdbcCatalog.java`、`TestExponentialHttpRequestRetryStrategy.java`、`TestHTTPClient.java`、`TestRESTCatalog.java`、`TestZOrderByteUtil.java`、`TestDeltaLakeTypeToType.java`、`TestHiveCatalog.java`、`TestHiveCommits.java`、`TestHiveTable.java`、`TestViews.java`（spark）、`TestAddFilesProcedure.java` 等，均执行了同样的清理操作，移除冗余的 `String.format` 调用。

其中 `TestZOrderByteUtil.java` 的改动量最大（115 行变动），因为该测试文件中大量使用了带格式化消息的断言。

## 总结

这是一个纯代码质量改进提交，批量移除了 21 个测试文件中冗余的 `String.format()` 调用，改用 AssertJ 原生的格式化字符串支持。修改不影响任何功能逻辑，仅使测试代码更简洁、更符合 AssertJ 的惯用写法，减少了约 56 行代码。
