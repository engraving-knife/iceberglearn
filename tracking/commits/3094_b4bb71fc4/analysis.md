# 提交 3094：Spark: Backport #14933: Snapshot location overlap check to spark v3.4, v3.5, v4.0 (#15016)

## 提交信息

- **序号**：3094 / 4088
- **哈希**：b4bb71fc408d17d2c724f7a2faf622759746523d
- **短哈希**：b4bb71fc4
- **日期**：2026-01-10
- **作者**：Varun Lakhyani
- **提交说明**：Spark: Backport #14933: Snapshot location overlap check to spark v3.4, v3.5, v4.0 (#15016)
- **PR/Issue**：#15016

## 总体目的

该提交是将 #14933（序号 3093）引入的 `SnapshotTableSparkAction` 位置重叠校验功能回移（backport）到 Spark v3.4、v3.5 和 v4.0 三个版本模块。Iceberg 的 Spark 集成针对不同 Spark 版本维护了独立的代码模块（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1`），每个版本模块有独立的源码目录和构建配置。#14933 最初只在最新的 `spark/v4.1` 模块中实现了位置重叠校验，而 v3.4、v3.5、v4.0 三个仍在维护的版本模块中仍保留着 `// TODO: Check the dest table location does not overlap with the source table location` 注释，缺少实际校验。

由于这四个版本模块的 `SnapshotTableSparkAction` 代码几乎完全相同（只是包路径中的版本号不同），位置重叠的安全风险在所有版本中都存在。为确保所有受支持的 Spark 版本都具备相同的数据安全保护，本提交将 v4.1 中的校验逻辑和测试原样回移到 v3.4、v3.5 和 v4.0 三个模块。回移内容完全一致：将 TODO 注释替换为 `Preconditions.checkArgument` 校验，并新增相同的两个测试方法（`testSnapshotWithOverlappingLocation` 和 `testSnapshotWithNonOverlappingLocation`）。

## 如何达成设计目的

对 `spark/v3.4`、`spark/v3.5`、`spark/v4.0` 三个模块分别应用与 #14933 在 `spark/v4.1` 中完全相同的改动：修改 `SnapshotTableSparkAction.java` 添加位置重叠校验，修改 `TestSnapshotTableAction.java` 添加两个测试方法。三个模块的改动内容完全一致，仅包路径中的版本号不同。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java` (+10/-1 lines)

**修改目的**：为 Spark v3.4 模块添加位置重叠校验。

**工作逻辑**：
与 #14933 在 v4.1 中的改动完全一致。将 `// TODO` 注释替换为获取 `sourceTableLocation` 和 `stagedTableLocation`，使用 `Preconditions.checkArgument` 校验三种重叠情况（完全相同、目标是子目录、源是子目录），失败时抛出 `IllegalArgumentException`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java` (+92/-1 lines)

**修改目的**：为 Spark v3.4 模块添加位置重叠测试。

**工作逻辑**：
与 #14933 在 v4.1 中的测试改动完全一致。新增 `SOURCE` 常量、`testSnapshotWithOverlappingLocation`（验证完全相同、子目录、父目录三种重叠场景）和 `testSnapshotWithNonOverlappingLocation`（验证非重叠场景正常执行）两个测试方法。跳过 Hadoop Catalog 场景。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java` (+10/-1 lines)

**修改目的**：为 Spark v3.5 模块添加位置重叠校验。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java` (+92/-1 lines)

**修改目的**：为 Spark v3.5 模块添加位置重叠测试。

**工作逻辑**：与 v3.4 测试改动完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/SnapshotTableSparkAction.java` (+10/-1 lines)

**修改目的**：为 Spark v4.0 模块添加位置重叠校验。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java` (+92/-1 lines)

**修改目的**：为 Spark v4.0 模块添加位置重叠测试。

**工作逻辑**：与 v3.4 测试改动完全一致。

## 总结

该提交将 #14933 的 `SnapshotTableSparkAction` 位置重叠校验功能原样回移到 Spark v3.4、v3.5、v4.0 三个版本模块，确保所有受支持的 Spark 版本都具备相同的数据安全保护。三个模块的改动内容与 v4.1 中的原始改动完全一致，包括主代码中的三条件重叠校验和测试中的重叠/非重叠场景覆盖。这是一个标准的多版本同步 backport 操作，保证功能一致性。
