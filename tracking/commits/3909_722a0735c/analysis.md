# 提交 3909：Flink: Backport: Add equality delete conversion DV resolution and writing (#16858) (#16869)

## 提交信息

- **序号**：3909 / 4088
- **哈希**：722a0735c0e7f0b8bb3bbe0245c6604ca9b773a9
- **短哈希**：722a0735c
- **日期**：2026-06-19 08:24:02 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add equality delete conversion DV resolution and writing (#16858) (#16869)
- **PR/Issue**：#16858, #16869

## 总体目的

将提交 3905（PR #16858）的 equality delete 转换 DV 写入器算子代码从 Flink 2.1 backport（向后移植）到 Flink 1.20 和 2.0 版本。这确保了 `EqualityConvertDVWriter` 算子在所有受支持的 Flink 版本上同步可用。

与之前的 backport 提交（3894、3904）类似，Iceberg 项目需要保持三个 Flink 版本的功能一致性。

## 如何达成设计目的

将 Flink 2.1 中新增的 4 个文件完整复制到 Flink 1.20 和 2.0 的对应目录下，并在各自的 `build.gradle` 中添加所需依赖。

## 修改详情

### Flink 1.20 和 2.0 新增文件和修改（共 8 个文件，+1678 lines）

以下文件从 `flink/v2.1/` 复制到 `flink/v1.20/` 和 `flink/v2.0/` 的对应路径：

- `flink/v1.20/build.gradle` 和 `flink/v2.0/build.gradle` (+1 line each)：添加 DV 写入器所需依赖
- `flink/maintenance/operator/EqualityConvertDVWriter.java` (+354 lines each)
- `flink/maintenance/operator/OperatorTestBase.java` (+12 lines each)
- `flink/maintenance/operator/TestEqualityConvertDVWriter.java` (+472 lines each)

**修改目的**：将 DV 写入器算子同步到 Flink 1.20 和 2.0。

**工作逻辑**：
所有文件内容与提交 3905（Flink 2.1 版本）完全一致，包括 DV 写入器实现、测试基类更新和详细的测试类。

## 总结

将 equality delete 转换的 DV 写入器算子从 Flink 2.1 backport 到 Flink 1.20 和 2.0，确保三个 Flink 版本的功能一致性。共修改 8 个文件（每个 Flink 版本 4 个），内容与原始提交完全一致。有关 DV 写入器的详细分析，请参见提交 3905 的分析文档。
