# 提交 3603：Flink:Backport RewriteDataFile support dynamic filter (#16132)

## 提交信息

- **序号**：3603 / 4088
- **哈希**：b0f022ff29945fe70f51485a1f1cda3d35eed8ca
- **短哈希**：b0f022ff2
- **日期**：2026-04-27 13:52:26 -0700
- **作者**：GuoYu
- **提交说明**：Flink:Backport RewriteDataFile support dynamic filter (#16132)
- **PR/Issue**：#16132

## 总体目的

这个提交是将提交 3602（Flink: RewriteDataFile support dynamic filter #15865）的功能反向移植（backport）到 Flink 1.20 和 Flink 2.0 版本。

Iceberg 同时维护多个 Flink 版本的集成模块（v1.20、v2.0、v2.1）。提交 3602 首先在 Flink 2.1 中实现了动态过滤器支持，本提交将相同的改动同步到 Flink 1.20 和 2.0 版本，确保所有支持的 Flink 版本都具有动态过滤器功能。

## 如何达成设计目的

将 Flink 2.1 中的改动原样应用到 Flink 1.20 和 2.0 的对应文件中。涉及 14 个文件（每个 Flink 版本 7 个文件），改动内容与提交 3602 完全一致。

## 修改详情

### Flink 1.20 版本文件 (7 files)

以下文件的修改与提交 3602 中 Flink 2.1 的对应文件完全一致：

- `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+26/-2 lines)：将 `filter` 字段改为 `SerializableSupplier<Expression> filterSupplier`，新增 `filter(SerializableSupplier)` 方法，标记旧方法为 `@Deprecated`。
- `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+6/-3 lines)：使用 `filterSupplier.get()` 在每次规划时获取动态过滤器。
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+52/-0 lines)：新增动态过滤器测试。
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+38/-0 lines)：更新测试基类。
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+1/-1 lines)：适配新 API。
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+46/-3 lines)：更新 planner 测试。
- `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+1/-1 lines)：适配 runner 测试。

### Flink 2.0 版本文件 (7 files)

与 Flink 1.20 完全相同的改动，应用到 `flink/v2.0/` 目录下的对应文件。

## 总结

这个提交是提交 3602 的 backport，将 Flink RewriteDataFiles 的动态过滤器支持同步到 Flink 1.20 和 2.0 版本，确保 Iceberg 在所有支持的 Flink 版本上提供一致的功能。这是 Iceberg 多版本维护策略的标准做法。
