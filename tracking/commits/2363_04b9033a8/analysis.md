# 提交 2363：Core: Add Schema evolution test with partition transform on a field with default values (#13570)

## 提交信息

- **序号**：2363 / 4088
- **哈希**：04b9033a83591dffb1b354f84ca8be1a77a89359
- **短哈希**：04b9033a8
- **日期**：2025-07-17 09:12:06 +0200
- **作者**：Anoop Johnson
- **提交说明**：Core: Add Schema evolution test with partition transform on a field with default values (#13570)
- **PR/Issue**：#13570

## 总体目的

这个提交是提交 2359 的延续，进一步为 Iceberg Core 添加了关于在带默认值列上应用分区变换（partition transform）的 schema 演进测试。同时重构了测试中临时目录的使用方式，使代码更简洁。

背景：提交 2359 已验证了添加带初始默认值的列后扫描行为的正确性。但实际场景中，用户可能不仅添加带默认值的新列，还可能在该列上添加分区变换（如 bucket 变换），将新列作为分区字段。这种组合场景——"带默认值的列 + 分区变换"——需要额外验证，确保 schema 演进和分区规范变更同时发生时扫描规划仍正确工作。

此外，本提交还将测试中分散的临时目录创建逻辑统一为使用 `@TempDir` 注解的 `File` 类型字段，消除了重复代码。

## 如何达成设计目的

1. 新增 `testAddColumnWithDefaultValueAndPartitionTransform` 测试方法，验证在带默认值列上添加 bucket 分区变换后扫描行为的正确性。
2. 重构现有测试方法，将临时目录创建逻辑统一为共享 `@TempDir File temp` 字段。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestScansAndSchemaEvolution.java` (+74/-11 lines)

**修改目的**：新增分区变换+默认值组合测试，并重构临时目录使用。

**工作逻辑**：

**重构部分**：
- 将 `@TempDir private Path temp` 改为 `@TempDir private File temp`，简化类型。
- 移除 `testPartitionSourceRename` 和 `testAddColumnWithDefaultValueAndQuery` 中各自的 `Files.createTempDirectory(temp, "junit").toFile()` + `assertThat(location.delete())` 逻辑，统一使用 `temp` 作为表创建路径。
- 移除不再需要的 `java.nio.file.Files` 和 `java.nio.file.Path` 导入，新增 `org.apache.iceberg.transforms.Transforms` 导入。

**新增测试 `testAddColumnWithDefaultValueAndPartitionTransform()`**：
- 仅在 v3+ 运行（默认值需要 v3+）。
- 创建表并写入两个初始数据文件。
- 添加带初始默认值 `"default_category"` 的新列 `category`。
- 通过 `updateSpec().addField(Expressions.bucket("category", 8))` 在新列上添加 bucket(8) 分区变换。
- 验证更新后的分区规范包含 2 个字段：原始 `part`（identity）和新的 `category_bucket_8`（bucket(8)）。
- 验证基础扫描规划返回 2 个任务。
- 验证投影扫描中每个任务的 schema 包含 `category` 字段且默认值正确。
- 验证基于 `category` 默认值的过滤扫描返回所有文件。
- 写入第三个数据文件后，验证所有 3 个任务的 schema 均包含带正确默认值的 `category` 字段。

## 总结

该提交在提交 2359 基础上进一步验证了"带默认值列 + 分区变换"这一更复杂的 schema 演进场景，确保添加带默认值的新列并在其上应用 bucket 分区变换后，扫描规划、投影扫描和过滤扫描都能正确工作。同时重构了测试中临时目录的使用方式，消除重复代码。纯测试改动，无生产代码变更。
