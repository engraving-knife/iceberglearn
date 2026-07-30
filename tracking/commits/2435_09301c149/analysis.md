# 提交 2435：Flink: Backport RewriteDataFiles support filter in plan (#13702)

## 提交信息

- **序号**：2435 / 4088
- **哈希**：09301c149715f6d561df6dc80855c37576fcca07
- **短哈希**：09301c149
- **日期**：2025-07-30 17:49:08 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport RewriteDataFiles support filter in plan (#13702)
- **PR/Issue**：#13702（backports #13669）

## 总体目的

本提交是提交 2429（#13669）的 backport，将 `RewriteDataFiles` 的 filter 支持从 Flink v2.0 目录回移植到 Flink v1.19 和 v1.20 目录。Iceberg 同时维护三个 Flink 版本（1.19、1.20、2.0），功能需要在所有支持的版本上保持一致。

原始提交 2429 只修改了 `flink/v2.0/` 目录下的文件。本提交将完全相同的改动应用到 `flink/v1.19/` 和 `flink/v1.20/` 目录下对应的 6 个文件，使三个版本的 `RewriteDataFiles` 维护操作都支持通过 `Expression` 过滤参与重写的数据文件。

## 如何达成设计目的

将 v2.0 的改动原样应用到 v1.19 和 v1.20 的对应文件：
1. `RewriteDataFiles.java`：Builder 新增 `filter` 字段和 `filter(Expression)` 方法，构建 planner 时传入 filter。
2. `DataFileRewritePlanner.java`：构造函数新增 filter 参数，创建 `BinPackRewriteFilePlanner(table, filter)`。
3. `TestRewriteDataFiles.java`：新增 `testRewriteWithFilter` 测试。
4. `RewriteUtil.java`、`TestDataFileRewritePlanner.java`、`TestDataFileRewriteRunner.java`：适配构造函数签名，传入 `Expressions.alwaysTrue()`。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+18/-1 lines)

**修改目的**：在 v1.19 的 Builder 中暴露 filter 配置。

**工作逻辑**：与 2429 中 v2.0 的改动一致——新增 `filter` 字段（默认 `alwaysTrue()`）、`filter(Expression)` 方法，构建 planner 时传入 filter。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+9/-2 lines)

**修改目的**：v1.19 的 planner 算子接收并传递 filter。

**工作逻辑**：新增 `filter` 字段和构造参数，创建 `BinPackRewriteFilePlanner(table, filter)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+41/-0 lines)

**修改目的**：v1.19 新增 filter 功能测试。

**工作逻辑**：`testRewriteWithFilter` 测试用 `Expressions.in("id", 1, 2)` 过滤，验证只重写满足条件的文件。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+4/-1 lines)

**修改目的**：适配构造函数签名。

**工作逻辑**：传入 `Expressions.alwaysTrue()`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+7/-2 lines)

**修改目的**：适配构造函数签名。

**工作逻辑**：两处创建 planner 调用补充 `Expressions.alwaysTrue()`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewriteRunner.java` (+4/-1 lines)

**修改目的**：适配构造函数签名。

**工作逻辑**：创建 planner 时补充 `Expressions.alwaysTrue()`。

### v1.20 目录的 6 个文件（同上）

`flink/v1.20/` 下对应的 6 个文件做了与 v1.19 完全相同的改动（各 +18/-1、+9/-2、+41/-0、+4/-1、+7/-2、+4/-1 lines）。

## 总结

本提交是 2429 的 backport，将 `RewriteDataFiles` filter 支持同步到 Flink v1.19 和 v1.20 版本。改动内容与原提交完全一致，仅目录不同。这保证了 Iceberg 在三个 Flink 版本上功能的一致性。
