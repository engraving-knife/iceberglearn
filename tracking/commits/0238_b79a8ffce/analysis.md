# 提交 0238：Delta: Fix integration tests and Create DataFile by partition values instead of path (#8398)

## 提交信息

- **序号**：0238 / 4088
- **哈希**：b79a8ffce3a95e5e67a4f926ee63d165153e1c54
- **短哈希**：b79a8ffce
- **日期**：2023-12-07 11:33:20 -0800
- **作者**：HonahX
- **提交说明**：Delta: Fix integration tests and Create DataFile by partition values instead of path (#8398)
- **PR/Issue**：#8398

## 总体目的

本提交针对 Iceberg 的 Delta Lake 表迁移能力（`delta-lake` 模块，把 Delta 表快照为 Iceberg 表）做两件事：(1) 修复并升级 Delta 集成测试，使其在 Spark 3.5 + delta-spark 3.0.0 上重新跑通；(2) 修复一个真实缺陷——原先在为 Delta 文件构造 Iceberg `DataFile` 时通过拼 partition path 字符串（`name=value/name=value`）来设置分区，本提交改为直接传分区值列表 `withPartitionValues(...)`，由 Iceberg 自身的 `PartitionSpec` 完成值到 `StructLike` 的解析。

背景动机：Delta 集成测试此前绑定 Spark 3.3 与 `delta-core` 2.2.0，随着 Iceberg 默认 Spark 升级到 3.5，旧测试已无法构建。更关键的是，path-based 分区构造方式存在隐患：当分区值本身包含路径分隔符（如日期字符串 `"2024/1/1"`）或特殊字符时，拼出的 `dt=2024/1/1` 会被 Iceberg 的 partition path 解析器误解为多级目录结构，从而写错分区值。HonahX 在测试中特意加入一个 `timestampStrCol = date_format(timestampCol, "yyyy/M/d")` 的列作为分区列，正是为了复现并验证这一场景。改用 `withPartitionValues` 后，分区值绕过字符串编解码，直接交给 spec 按字段类型反序列化，从根本上消除该类问题。

## 如何达成设计目的

整体设计是"测试驱动 + 缺陷修复"：先升级测试栈（Spark 3.3 → 3.5、delta-core → delta-spark 3.0.0、JUnit5 参数化简化、用例调整），并在测试中刻意构造会触发 path-based 缺陷的分区场景；同时把 `BaseSnapshotDeltaLakeTableAction` 中构造 `DataFile` 的一处 `withPartitionPath` 改为 `withPartitionValues`。两者配套：修复让新测试通过，新测试反过来覆盖修复路径。CI 配置与 build.gradle 同步把 delta-conversion-ci 的 Spark 版本切到 3.5。

## 修改详情

### `gradle/libs.versions.toml`、`build.gradle`、`.github/workflows/delta-conversion-ci.yml`

**修改目的**：把 delta-lake 集成测试栈从 Spark 3.3 / delta-core 2.2.0 升级到 Spark 3.5 / delta-spark 3.0.0。

**工作逻辑**：
- `libs.versions.toml`：版本别名 `delta-core = "2.2.0"` 改为 `delta-spark = "3.0.0"`；库别名 `delta-core = { module = "io.delta:delta-core_2.12", ... }` 改为 `delta-spark = { module = "io.delta:delta-spark_2.12", ... }`，对齐 delta 3.x 的新 artifact 命名。
- [`build.gradle`](build.gradle)：在 `:iceberg-delta-lake` 项目中，把 `if (sparkVersions.contains("3.3"))` 守卫与对应 `iceberg-spark-3.3` / `spark-hive_${scalaVersion}:${spark.hive33}` 依赖改为 `3.5` / `iceberg-spark-3.5` / `spark.hive35`，并改用 `delta-spark_${scalaVersion}`；注释同步更新为 "delta-core uses Spark 3.5.*"；为 `integrationTest` 任务新增 `useJUnitPlatform()`，使集成测试在 JUnit 5 Platform 下运行（与新测试代码使用 `@Test`（JUnit5）一致）。
- `.github/workflows/delta-conversion-ci.yml`：两条 CI 命令的 `-DsparkVersions=3.3` 改为 `-DsparkVersions=3.5`（2.12 与 2.13 两条 scala 流各一）。

### `delta-lake/src/main/java/org/apache/iceberg/delta/BaseSnapshotDeltaLakeTableAction.java`

**修改目的**：把构造 `DataFile` 时设置分区的方式从 path 字符串改为 partition values 列表，修复分区值含路径分隔符等特殊字符时的解析错误。

**工作逻辑**：原代码（约 386-400 行）先把 `partitionValues` map 拼成形如 `name1=value1/name2=value2` 的字符串再调用 `DataFiles.builder(spec).withPartitionPath(partition)`，依赖 Iceberg 内部对 partition path 的字符串解析。新代码改为：

```java
List<String> partitionValueList =
    spec.fields().stream()
        .map(PartitionField::name)
        .map(partitionValues::get)
        .collect(Collectors.toList());

return DataFiles.builder(spec)
    .withPath(fullFilePath)
    .withFormat(format)
    .withFileSizeInBytes(fileSize)
    .withMetrics(metrics)
    .withPartitionValues(partitionValueList)
    .build();
```

即按 `spec.fields()` 顺序取出每个分区字段的值组成 `List<String>`，交给 `withPartitionValues`，由 `DataFiles.builder` 内部根据 `PartitionSpec` 的类型信息把字符串值反序列化为对应的 `StructLike`。这样分区值的语义不再依赖字符串编码格式，对包含 `/`、`=`、转义字符的值安全。注意 `partitionValues` 仍来自 Delta `addFile.getPartitionValues()`/`removeFile.getPartitionValues()`，且 unpartitioned 表上游已校验为空 map 而非 null，故 `partitionValueList` 在无分区时为空列表，与 `withPartitionValues` 的契约一致。

### `delta-lake/src/integration/java/org/apache/iceberg/delta/TestSnapshotDeltaLakeTable.java`

**修改目的**：适配新测试栈并新增覆盖 `withPartitionValues` 修复路径的用例。

**工作逻辑**：
- 去参数化：原用 `@ParameterizedTest + @MethodSource("parameters")` 且只提供一组 catalog 配置，意义不大；改为普通 `@Test`，构造函数硬编码 `icebergCatalogName`/`SparkCatalog`/固定 `config` map。`parameters()` 静态方法删除。
- 双 temp 目录：`@TempDir private Path temp` 改为 `@TempDir private File tempA` 与 `@TempDir private File tempB`，因为多个用例（如 `testSnapshotWithNewLocation`、`testSnapshotTableWithExternalDataFiles`）需要两个独立路径（源 Delta 表位置 + 新 Iceberg 表位置），原先共用一个 `temp` 会导致路径冲突。所有 `temp.toFile().toURI().toString()` 相应改为 `tempA.toURI().toString()` / `tempB.toURI().toString()`。
- 引入 `@org.junit.jupiter.api.Test` 取代 `@ParameterizedTest` 系列 import；新增 `import static ... functions.col` 与 `functions.date_format`，删除 `java.nio.file.Path`、`java.util.stream.Stream`、`org.junit.jupiter.params.*` 等不再使用的 import。
- 测试数据帧调整：`typeTestDataFrame` 中把原来 `stringCol = CAST(timestampCol AS STRING)`（其值形如 `"2024-01-02 00:00:00"`）替换为 `timestampStrCol = date_format(col("timestampCol"), "yyyy/M/d")`（其值形如 `"2024/1/2"`，含路径分隔符），并保留 `stringCol` 仍指向该列。这是修复的关键回归用例：旧 path-based 写法会因 `2024/1/2` 中的 `/` 被误解析为多级分区目录而出错，新 `withPartitionValues` 写法可正确处理。
- `testSnapshotSupportedTypes` 用例：分区列由单一 `stringCol` 改为 `stringCol, timestampStrCol, booleanCol, longCol` 四列，覆盖字符串（含 `/`）、布尔、长整型等多种分区值类型，更全面地验证 `withPartitionValues` 的类型反序列化路径。
- `writeDeltaTable` 签名：`String partitionColumn` 改为 `String... partitionColumns`，分支条件由 `partitionColumn != null` 改为 `partitionColumns.length > 0`，`.partitionBy(partitionColumn)` 改为 `.partitionBy(partitionColumns)`，以支持多分区列写入。所有调用点相应去掉末尾 `null` 参数。

## 小结

本提交一方面把 delta-lake 集成测试栈升级到 Spark 3.5 / delta-spark 3.0.0 并简化为 JUnit5 普通测试，另一方面把 `BaseSnapshotDeltaLakeTableAction` 构造 `DataFile` 的方式从 `withPartitionPath` 改为 `withPartitionValues`，从根本上修复分区值含路径分隔符（如 `yyyy/M/d`）时的解析缺陷，并通过新增多类型多列分区用例回归覆盖。
