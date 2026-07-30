# 提交 3894：Flink: Backport: Add data model and key serialization for equality delete conversion (#16831) (#16842)

## 提交信息

- **序号**：3894 / 4088
- **哈希**：f1d8c9bc6495553e9d3f2c8b779e309a5c634413
- **短哈希**：f1d8c9bc6
- **日期**：2026-06-17 10:02:45 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add data model and key serialization for equality delete conversion (#16831) (#16842)
- **PR/Issue**：#16831, #16842

## 总体目的

将提交 3891（PR #16831）的 equality delete 转换数据模型和键序列化代码从 Flink 2.1 backport（向后移植）到 Flink 1.20 和 2.0 版本。这确保了 equality delete 转换功能在所有受支持的 Flink 版本上同步可用。

Iceberg 项目同时维护多个 Flink 版本的适配（1.20、2.0、2.1），功能需要在所有版本上保持一致。由于不同 Flink 版本的 API 存在差异，代码无法直接共享，需要通过 backport 方式同步。

## 如何达成设计目的

将 Flink 2.1 中新增的 11 个文件完整复制到 Flink 1.20 和 2.0 的对应目录下，文件内容和功能完全一致。

## 修改详情

### Flink 1.20 和 2.0 新增文件（各 11 个文件，共 22 个文件，+1784 lines）

以下文件从 `flink/v2.1/` 复制到 `flink/v1.20/` 和 `flink/v2.0/` 的对应路径：

- `flink/maintenance/operator/DVPosition.java` (+54 lines each)
- `flink/maintenance/operator/DVWriteResult.java` (+46 lines each)
- `flink/maintenance/operator/EqualityConvertPlan.java` (+76 lines each)
- `flink/maintenance/operator/EqualityDeleteFileScanTask.java` (+51 lines each)
- `flink/maintenance/operator/FlinkAddedRowsScanTask.java` (+72 lines each)
- `flink/maintenance/operator/IndexCommand.java` (+93 lines each)
- `flink/maintenance/operator/ReadCommand.java` (+86 lines each)
- `flink/maintenance/operator/SerializedEqualityValues.java` (+51 lines each)
- `flink/maintenance/operator/StructLikeSerializer.java` (+140 lines each)
- `flink/maintenance/operator/TestFlinkPojoTypes.java` (+47 lines each)
- `flink/maintenance/operator/TestStructLikeSerializer.java` (+176 lines each)

**修改目的**：将 equality delete 转换的数据模型同步到 Flink 1.20 和 2.0。

**工作逻辑**：
所有文件内容与提交 3891（Flink 2.1 版本）完全一致，包括：
- 8 个 Java record 消息类型（ReadCommand、IndexCommand、DVPosition、DVWriteResult、EqualityConvertPlan、SerializedEqualityValues、EqualityDeleteFileScanTask、FlinkAddedRowsScanTask）
- 1 个序列化器（StructLikeSerializer）
- 2 个测试类（TestFlinkPojoTypes、TestStructLikeSerializer）

## 总结

将 equality delete 转换的数据模型和键序列化代码从 Flink 2.1 backport 到 Flink 1.20 和 2.0，确保三个 Flink 版本的功能一致性。这是 Iceberg 多版本维护策略的标准操作，共新增 22 个文件（每个 Flink 版本 11 个），内容与原始提交完全一致。有关数据模型的详细分析，请参见提交 3891 的分析文档。
