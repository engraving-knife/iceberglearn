# 提交 3426：Data: Add TCK tests for ReadBuilder in BaseFormatModelTests (#15633)

## 提交信息

- **序号**：3426 / 4088
- **哈希**：1139cd441fcb82832fc3eb6a73a1913ca6ea42c6
- **短哈希**：1139cd441f
- **日期**：2026-03-20 16:56:12 +0100
- **作者**：GuoYu
- **提交说明**：Data: Add TCK tests for ReadBuilder in BaseFormatModelTests (#15633)
- **PR/Issue**：#15633

## 总体目的

为 `BaseFormatModelTests` 添加 ReadBuilder 的 TCK（Technology Compatibility Kit）测试。此前 BaseFormatModelTests 主要测试了写入功能，但对读取构建器（ReadBuilder）的测试覆盖不足。本提交新增大量测试覆盖各种读取场景，包括投影、过滤、列统计等，确保各格式模型（Avro、ORC、Parquet）的读取行为一致。

## 如何达成设计目的

1. 在 `BaseFormatModelTests` 中新增多个测试方法覆盖 ReadBuilder 的各种场景
2. 扩展 `DataGenerator` 和 `DataGenerators` 以支持更多测试数据模式
3. 测试包括：基础读取、投影读取、列统计读取、过滤读取等

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+422/-48 lines)

**修改目的**：新增 ReadBuilder 的 TCK 测试。

**工作逻辑**：
- 新增多个参数化测试方法，覆盖：
  - 基础数据读取
  - 投影读取（选择部分列）
  - 列统计读取
  - 带过滤条件的读取
  - 删除文件读取（位置删除、等值删除）
- 重构现有测试以复用辅助方法
- 扩展测试数据生成以支持更多场景

### `data/src/test/java/org/apache/iceberg/data/DataGenerator.java` (+7/-1 lines)

**修改目的**：扩展数据生成器接口。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+15 lines)

**修改目的**：新增数据生成器实现。

## 总结

本提交为 `BaseFormatModelTests` 新增了大量 ReadBuilder 的 TCK 测试，覆盖投影、过滤、列统计等读取场景。这些测试确保各格式模型（Avro、ORC、Parquet）的读取行为一致和正确。同时扩展了数据生成器以支持更多测试场景。
