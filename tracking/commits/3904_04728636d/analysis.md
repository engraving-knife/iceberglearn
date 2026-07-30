# 提交 3904：Flink: Backport: Add equality delete conversion operators (#16844) (#16857)

## 提交信息

- **序号**：3904 / 4088
- **哈希**：04728636df3b12692f445427ba0f8da6c7f74e92
- **短哈希**：04728636d
- **日期**：2026-06-18 14:54:00 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add equality delete conversion operators (#16844) (#16857)
- **PR/Issue**：#16844, #16857

## 总体目的

将提交 3899（PR #16844）的 equality delete 转换算子代码从 Flink 2.1 backport（向后移植）到 Flink 1.20 和 2.0 版本。这确保了 equality delete 转换的 Reader 和 PK Index 算子在所有受支持的 Flink 版本上同步可用。

与提交 3894（数据模型的 backport）类似，Iceberg 项目同时维护多个 Flink 版本的适配，功能需要在所有版本上保持一致。

## 如何达成设计目的

将 Flink 2.1 中新增的 5 个文件完整复制到 Flink 1.20 和 2.0 的对应目录下，文件内容和功能完全一致。

## 修改详情

### Flink 1.20 和 2.0 新增文件（各 5 个文件，共 10 个文件，+3274 lines）

以下文件从 `flink/v2.1/` 复制到 `flink/v1.20/` 和 `flink/v2.0/` 的对应路径：

- `flink/maintenance/operator/EqualityConvertPKIndex.java` (+274 lines each)
- `flink/maintenance/operator/EqualityConvertReader.java` (+254 lines each)
- `flink/maintenance/operator/OperatorTestBase.java` (+13 lines each)
- `flink/maintenance/operator/TestEqualityConvertPKIndex.java` (+675 lines each)
- `flink/maintenance/operator/TestEqualityConvertReader.java` (+421 lines each)

**修改目的**：将 equality delete 转换算子同步到 Flink 1.20 和 2.0。

**工作逻辑**：
所有文件内容与提交 3899（Flink 2.1 版本）完全一致，包括：
- 2 个算子实现（EqualityConvertReader、EqualityConvertPKIndex）
- 1 个测试基类（OperatorTestBase）
- 2 个测试类（TestEqualityConvertReader、TestEqualityConvertPKIndex）

## 总结

将 equality delete 转换的 Reader 和 PK Index 算子从 Flink 2.1 backport 到 Flink 1.20 和 2.0，确保三个 Flink 版本的功能一致性。共新增 10 个文件（每个 Flink 版本 5 个），内容与原始提交完全一致。有关算子的详细分析，请参见提交 3899 的分析文档。
