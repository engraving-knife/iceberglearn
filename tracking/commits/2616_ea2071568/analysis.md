# 提交 2616：Test: Avoid running redundant tests (#14036)

## 提交信息

- **序号**：2616 / 4088
- **哈希**：ea2071568dc66148b483a82eefedcd2992b435f7
- **短哈希**：ea2071568
- **日期**：2025-09-09 10:47:21 -0500
- **作者**：Yuya Ebihara
- **提交说明**：Test: Avoid running redundant tests
- **PR/Issue**：#14036

## 总体目的

`DeleteFileIndexTestBase` 是一个参数化测试基类，通过 `@Parameters(name = "formatVersion = {0}")` 以 `formatVersion`（V2 及以上，即 V2、V3…）为参数运行多轮测试。然而其中两个测试方法 `testUnpartitionedTableScan` 和 `testUnpartitionedTableSequenceNumbers` 在内部通过 `TestTables.create(..., 2)` 硬编码创建 format version 2 的表，并不依赖参数化的 `formatVersion` 值。

这意味着当 `formatVersion` 参数为 V3（或更高）时，这两个测试仍会执行，但其行为与 `formatVersion=2` 时完全一致（因为表本身是 V2），属于冗余运行，浪费 CI 时间且不提供额外覆盖。文件中其他类似测试（如行 375、405）已通过 `assumeThat(formatVersion).as("Requires V2 position deletes").isEqualTo(2);` 跳过非 V2 参数的运行，这两个方法遗漏了该假设。

本提交为这两个测试方法补上相同的 `assumeThat` 假设，使它们仅在 `formatVersion=2` 时运行，避免冗余执行。

## 如何达成设计目的

在每个测试方法体开头加入 `assumeThat(formatVersion).as("Requires V2 position deletes").isEqualTo(2);`。AssertJ 的 `assumeThat` 在条件不满足时会抛出 `AssumptionViolatedException`，JUnit 5 会将该测试标记为"跳过"而非失败，从而跳过 V3 等参数下的冗余运行，与文件中其他同类测试保持一致。

## 修改详情

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java` (+2 lines)

**修改目的**：跳过 `formatVersion != 2` 时这两个测试的冗余运行。

**工作逻辑**：
- 在 `testUnpartitionedTableScan` 方法体首行加入 `assumeThat(formatVersion).as("Requires V2 position deletes").isEqualTo(2);`。该方法内部用 `TestTables.create(tableDir, "unpartitioned", SCHEMA, PartitionSpec.unpartitioned(), 2)` 硬编码创建 V2 表，测试 position deletes 的扫描行为，与参数化 formatVersion 无关，故仅在 V2 参数下运行即可。
- 在 `testUnpartitionedTableSequenceNumbers` 方法体首行加入同样的假设语句。该方法同样硬编码 V2 表，测试未分区表的序列号行为。

两处修改与文件中已有的同类假设（行 375、405 等）保持一致，统一了"硬编码 V2 表的测试只在 formatVersion=2 时运行"的约定。

## 总结

这是一次测试优化，通过为两个遗漏假设的参数化测试补上 `assumeThat(formatVersion).isEqualTo(2)`，避免在 V3 等参数下重复运行实质上只测试 V2 行为的用例，减少 CI 冗余开销，并与文件中其他测试保持一致约定。改动极小但有助于提升测试效率。
