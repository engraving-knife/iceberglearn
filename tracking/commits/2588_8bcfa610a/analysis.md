# 提交 2588：Data, Flink, Spark: Use TestHelpers for FormatVersion (#13880)

## 提交信息

- **序号**：2588 / 4088
- **哈希**：8bcfa610a2a742ce66d480b87cbd583f27d555d5
- **短哈希**：8bcfa610a
- **日期**：2025-09-02 12:38:05 -0500
- **作者**：Russell Spitzer
- **提交说明**：Data, Flink, Spark: Use TestHelpers for FormatVersion (#13880)
- **PR/Issue**：#13880

## 总体目的

此次提交对跨模块（data、Flink、Spark）的测试参数化代码进行重构，将硬编码的格式版本列表（如 `Arrays.asList(2, 3)`、`new int[]{1, 2}`）替换为 `TestHelpers` 中集中定义的版本常量（`ALL_VERSIONS`、`V2_AND_ABOVE`）。

Iceberg 的 `TestHelpers`（位于 `api/src/test/java`）定义了格式版本常量：
- `MAX_FORMAT_VERSION = 4`
- `ALL_VERSIONS = [1, 2, 3, 4]`（1 到 MAX_FORMAT_VERSION）
- `V2_AND_ABOVE = [2, 3, 4]`（2 到 MAX_FORMAT_VERSION）

此前各测试类中硬编码了格式版本列表，如 `Arrays.asList(2, 3)` 表示在 v2、v3 上运行测试。这种硬编码存在两个问题：一是版本列表分散在几十个测试类中，当新增格式版本（如 v4）时需要逐个修改；二是硬编码的版本可能与 `MAX_FORMAT_VERSION` 不同步，导致新版本未被测试覆盖或测试了不支持的版本。

通过统一使用 `TestHelpers` 常量，当 `MAX_FORMAT_VERSION` 变化时，所有引用常量的测试会自动跟随扩展版本覆盖范围，无需逐个修改。此次重构将 v3.4/v3.5/v3.6 三个 Spark 版本、v1.19/v1.20/v2.0 三个 Flink 版本以及 data 模块的测试参数化方法统一改为使用常量，同时将返回类型从 `List<Object>` 收窄为 `List<Integer>` 以提升类型安全。

## 如何达成设计目的

- 对于仅支持 v2 及以上版本的测试（如重写、删除相关 action 测试），将 `Arrays.asList(2, 3)` / `ImmutableList.of(2, 3)` 替换为 `TestHelpers.V2_AND_ABOVE`。
- 对于支持所有版本的测试（如 committer 测试），将 `new int[]{1, 2}` 替换为 `TestHelpers.ALL_VERSIONS`。
- 将参数化方法的返回类型从 `List<Object>` 改为 `List<Integer>`，提升类型安全。
- 对于多维参数化（如 fileFormat × vectorized × formatVersion），将硬编码的 Object[][] 改为基于常量的循环生成，使版本维度自动扩展。
- 移除不再需要的 `Arrays`、`ImmutableList` import，新增 `TestHelpers` import。

## 修改详情

### `data/src/test/java/org/apache/iceberg/io/TestDVWriters.java` (+3/-3)

**修改目的**：将 DV 写入器测试的版本参数改用常量。

**工作逻辑**：`parameters()` 方法将 `Arrays.asList(new Object[]{2, 3})` 改为 `TestHelpers.V2_AND_ABOVE`，返回类型从 `List<Object>` 改为 `List<Integer>`。

### `flink/v1.19|v1.20|v2.0` 的 `TestRewriteDataFilesAction.java` (各 +2/-2)

**修改目的**：将 Flink 重写数据文件测试的版本循环改用常量。

**工作逻辑**：将 `for (int version : Arrays.asList(2, 3))` 改为 `for (int version : TestHelpers.V2_AND_ABOVE)`。

### `flink/v1.19|v1.20|v2.0` 的 `TestIcebergCommitter.java` (各 +1/-1)

**修改目的**：将 Flink committer 测试的版本循环改用常量。

**工作逻辑**：将 `for (int formatVersion : new int[]{1, 2})` 改为 `for (int formatVersion : TestHelpers.ALL_VERSIONS)`，使 committer 测试自动覆盖 v1 到 v4 所有版本。

### `spark/v3.4|v3.5|v3.6` 的 `TestDeleteReachableFilesAction.java`、`TestExpireSnapshotsAction.java`、`TestRewriteDataFilesAction.java`、`TestRemoveDanglingDeleteAction.java` (各 +2/-3)

**修改目的**：将 Spark action 测试的版本参数改用 `V2_AND_ABOVE`。

**工作逻辑**：将 `Arrays.asList(2, 3)` 改为 `TestHelpers.V2_AND_ABOVE`，返回类型改为 `List<Integer>`。

### `spark/v3.4|v3.5|v3.6` 的 `TestPositionDeletesReader.java` (各 +2/-2)

**修改目的**：将位置删除读取器测试的版本参数改用常量。

**工作逻辑**：将 `ImmutableList.of(2, 3)` 改为 `TestHelpers.V2_AND_ABOVE`。

### `spark/v3.4|v3.5|v3.6` 的 `TestSparkMetadataColumns.java` (各 +8/-18)

**修改目的**：将元数据列测试的多维参数化改为基于常量循环生成。

**工作逻辑**：原硬编码 15 组 Object[][] 参数组合改为遍历 `TestHelpers.ALL_VERSIONS`，对每个版本生成 PARQUET(false/true)、AVRO(false)、ORC(false/true) 五组参数，使版本维度自动扩展。

### `spark/v3.4|v3.5|v3.6` 的 `TestSparkReaderDeletes.java` (各 +7/-6)

**修改目的**：将删除读取测试的多维参数化改为基于常量循环生成，并保留版本相关的条件逻辑。

**工作逻辑**：原硬编码 6 组参数改为遍历 `TestHelpers.V2_AND_ABOVE`，对每个版本生成 PARQUET(false/true) 参数；仅 v2 生成 ORC 和 AVRO 参数（因这些格式在 v3 的删除读取支持有限），通过 `if (version == 2)` 条件保留原有约束。

## 总结

一次跨模块测试重构提交，将 data、Flink（v1.19/v1.20/v2.0）、Spark（v3.4/v3.5/v3.6）多个测试类中硬编码的格式版本列表统一替换为 `TestHelpers.ALL_VERSIONS` 和 `TestHelpers.V2_AND_ABOVE` 常量。这使测试版本覆盖范围能随 `MAX_FORMAT_VERSION` 常量自动扩展，消除分散的硬编码版本列表，提升类型安全和可维护性，为未来新增格式版本（如 v4）的测试覆盖提供自动化基础。
