# 提交 2267：Core: Fix filed ids of partition stats file (#13329)

## 提交信息

- **序号**：2267 / 4088
- **哈希**：bcb68800e1a9946fced40c006c6b55ec02445c1c
- **短哈希**：bcb68800e
- **日期**：2025-06-25 08:47:26 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Core: Fix filed ids of partition stats file
- **PR/Issue**：#13329

## 总体目的

本提交修复了分区统计文件（partition stats file）中字段 ID（field ID）不符合 Iceberg 规范的问题。在原有实现中，分区统计文件的 schema 字段 ID 从 0 开始（PARTITION_FIELD_ID=0, SPEC_ID=1, DATA_RECORD_COUNT=2, ...），但根据 Iceberg 规范，字段 ID 应从 1 开始（PARTITION_FIELD_ID=1, SPEC_ID=2, DATA_RECORD_COUNT=3, ...）。

字段 ID 从 0 开始的问题可能导致与某些读取器或工具的兼容性问题，因为 Iceberg 规范中字段 ID 0 通常有特殊含义或不被使用。此外，`recordToPartitionStats` 方法中使用了硬编码的 field ID 来按位置读取记录字段，这种方式在字段 ID 与记录位置不一致时容易出错。本提交同时修复了字段 ID 编号和记录读取逻辑。

此外，本提交还增加了对损坏的统计文件的容错处理——当增量计算统计时遇到损坏的旧统计文件（`InvalidStatsFileException`），回退到全量计算而非直接失败。

## 如何达成设计目的

- 将所有分区统计文件字段的 ID 从 0-based 改为 1-based（PARTITION_FIELD_ID: 0->1, SPEC_ID: 1->2, ... LAST_UPDATED_SNAPSHOT_ID: 11->12）。
- 重构 `recordToPartitionStats()` 方法，从基于 field ID 的位置查找改为基于顺序位置（pos）的递增读取，避免 ID 与位置不一致的问题。
- 在 `computeAndWriteStatsFile()` 中添加 try-catch 处理 `InvalidStatsFileException`，捕获后回退到全量统计计算。
- 更新和增强测试以验证新的字段 ID 和容错行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (修改, +76/-51 lines)

**修改目的**：修复字段 ID 编号，重构记录读取逻辑，增加容错处理。

**工作逻辑**：
1. **字段 ID 修复**：将所有常量的 field ID 从 0-based 改为 1-based。`PARTITION_FIELD_ID` 从 0 改为 1，`SPEC_ID` 的 NestedField ID 从 1 改为 2，依此类推，所有字段的 ID 统一加 1。添加注释 "schema of the partition stats file as per spec" 说明这是规范要求。
2. **recordToPartitionStats 重构**：原方法使用 `record.get(FIELD_ID, Type.class)` 通过 field ID 读取每个字段，修改为使用递增的位置索引 `pos` 读取：`record.get(pos++, StructLike.class)` 读取 partition 和 spec_id，然后通过循环 `for (; pos < record.size(); pos++)` 读取剩余字段。这种方式更健壮，不依赖 field ID 与位置的对应关系。
3. **容错处理**：在 `computeAndWriteStatsFile()` 方法中，将 `computeAndMergeStatsIncremental()` 调用包装在 try-catch 中。捕获 `InvalidStatsFileException` 后记录警告日志 "Using full compute as previous statistics file is corrupted for incremental compute"，然后回退到 `computeStats()` 全量计算。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (修改, +135/-XX lines)

**修改目的**：更新测试以验证新的字段 ID 和增加容错测试。

**工作逻辑**：更新测试中所有对字段 ID 的断言为新的 1-based 值。新增测试验证当旧统计文件损坏时，`computeAndWriteStatsFile` 能正确回退到全量计算而非抛出异常。

### `core/src/test/java/org/apache/iceberg/TestOrcPartitionStatsHandler.java` (修改, +14/0 lines)

**修改目的**：为 ORC 格式的分区统计处理器添加针对新字段 ID 的测试。

## 总结

本提交修复了分区统计文件中字段 ID 不符合 Iceberg 规范的问题（从 0-based 改为 1-based），重构了记录读取逻辑使其更健壮，并增加了对损坏统计文件的容错处理。这是一个影响数据正确性和兼容性的重要修复，确保分区统计文件的 schema 符合规范要求，同时提升了系统在遇到损坏文件时的鲁棒性。
