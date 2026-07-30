# 提交 3466：Spark: Validate Z-order rewrite does not conflict with internal ICEZVALUE column name (#15706)

## 提交信息

- **序号**：3466 / 4088
- **哈希**：24cb0389034d7718308d85f16c65147de29547cf
- **短哈希**：24cb038903
- **日期**：2026-03-27 11:05:05 -0500
- **作者**：Yaniv Zalach
- **提交说明**：Spark: Validate Z-order rewrite does not conflict with internal ICEZVALUE column name (#15706)
- **PR/Issue**：#15706

## 总体目的

在 Z-order 重写操作开始前添加验证，检查表中是否存在与 Iceberg 内部 Z-order 列名（`ICEZVALUE`）冲突的列。如果用户表中有一列名为 `ICEZVALUE`，Z-order 重写会使用该列名作为内部排序值列，导致冲突和潜在的数据错误。此提交添加前置验证，在检测到冲突时抛出清晰的错误信息。

## 如何达成设计目的

- 在 `SparkZOrderFileRewriteRunner` 的 Z-order 列收集逻辑前添加前置检查
- 使用 `Preconditions.checkArgument()` 验证 schema 中不存在名为 `Z_COLUMN`（即 `ICEZVALUE`）的列
- 区分大小写和大小写不敏感两种情况
- 在 4 个 Spark 版本中同步添加相同的验证和测试

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderFileRewriteRunner.java` (+7/-0 lines)

**修改目的**：添加 Z-order 列名冲突验证。

**工作逻辑**：
```java
Preconditions.checkArgument(
    caseSensitive
        ? schema.findField(Z_COLUMN) == null
        : schema.caseInsensitiveFindField(Z_COLUMN) == null,
    "Cannot zorder because the table has a column named '%s', which conflicts with Iceberg's internal Z-order column name",
    Z_COLUMN);
```
- 在收集 Z-order 列名之前执行检查
- 大小写敏感模式下使用 `findField()`
- 大小写不敏感模式下使用 `caseInsensitiveFindField()`
- 如果找到同名列，抛出 IllegalArgumentException 并提供清晰的错误信息

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+20/-0 lines)

**修改目的**：添加冲突列名的回归测试。

**工作逻辑**：
- 创建包含名为 `ICEZVALUE` 列的表
- 尝试执行 Z-order 重写
- 验证抛出预期的异常，包含冲突列名的错误信息

### Spark 3.4、3.5、4.0 模块

每个版本都添加了相同的 `SparkZOrderFileRewriteRunner` 验证和 `TestRewriteDataFilesAction` 测试。

## 总结

该提交在 Z-order 重写操作前添加了列名冲突验证，防止用户表中存在与 Iceberg 内部 `ICEZVALUE` 列同名的列时导致数据错误。验证在 4 个 Spark 版本中同步添加，并附带回归测试。
