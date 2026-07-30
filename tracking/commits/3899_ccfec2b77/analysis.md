# 提交 3899：Flink: Add equality delete conversion operators (#16844)

## 提交信息

- **序号**：3899 / 4088
- **哈希**：ccfec2b771c31ad7b9bd893fb3dc9630c3cf982f
- **短哈希**：ccfec2b77
- **日期**：2026-06-17 13:20:45 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add equality delete conversion operators (#16844)
- **PR/Issue**：#16844

## 总体目的

为 Flink equality delete 转换任务添加两个核心算子：`EqualityConvertReader`（读取器）和 `EqualityConvertPKIndex`（主键索引）。这是从大型 PR #15996 拆分出的第二个提交（第一个是 #16831 的数据模型），实现了将 equality-delete 文件转换为 deletion vectors 的 Flink 作业中的核心处理逻辑。

`EqualityConvertReader` 负责读取文件并将每行数据转换为索引命令：数据文件发出 `ADD_DATA_ROW` 或 `ADD_STAGING_DATA_ROW`，equality delete 文件发出 `RESOLVE_DELETE`。`EqualityConvertPKIndex` 维护索引分片，解析 equality delete 并发出要标记删除的行位置（DVPosition）。

该转换的核心挑战在于正确处理事件时间排序：删除操作必须在被删除数据之后解析，而并行读取器交付记录的顺序不确定。通过 Flink 的事件时间定时器机制确保删除在正确的时序下解析。

## 如何达成设计目的

实现两个 Flink 算子，利用 Flink 的 keyed stream 和事件时间定时器机制来管理索引状态和删除解析顺序。Reader 以格式无关方式通过 `FormatModelRegistry` 读取文件，PK index 维护分片级索引并通过定时器确保时序正确。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertReader.java` (+254 lines, new file)

**修改目的**：实现文件读取算子。

**工作逻辑**：
- 通过 `FormatModelRegistry` 格式无关地读取每个文件
- 数据 FileScanTask：每行发出 `ADD_DATA_ROW` 或 `ADD_STAGING_DATA_ROW`
- EqualityDeleteFileScanTask：每行发出 `RESOLVE_DELETE`
- 使用 `StructLikeSerializer` 序列化每行的主键
- 跳过已有 DV 覆盖的行
- V2 位置删除在 main 数据文件上抛出异常（目标必须是 V3 DV-only）
- 失败发送到 ERROR_STREAM 和 READER_ABORT_STREAM

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertPKIndex.java` (+274 lines, new file)

**修改目的**：实现主键索引算子。

**工作逻辑**：
- 维护索引分片，每个解析的行发出一个 DVPosition
- Main 数据到达时立即索引
- Staging 快照的新行和 RESOLVE_DELETE 注册事件时间定时器
- 定时器按时间戳顺序触发，确保删除在后续 staging 行加入索引前解析
- 同一周期内重新插入的键在同周期删除中存活
- 共享 staging/target 分支时按序列号感知（序列 S 的删除只移除低于 S 的行）
- 独立分支时 committer 重新分配序列号，每个匹配都被删除
- 通过 per-key 检查和 CLEAR_INDEX 广播清除陈旧索引状态
- 指标：`resolvedDeleteNum`、`indexedKeyNum`、`eagerlyEvictedKeyNum`

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+13 lines)

**修改目的**：为算子测试添加公共基础设施。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertReader.java` (+421 lines, new file)

**修改目的**：测试 Reader 算子的各种场景。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertPKIndex.java` (+675 lines, new file)

**修改目的**：测试 PK Index 算子的索引、删除解析、定时器排序和陈旧状态清除。

## 总结

为 equality delete 转换任务实现了两个核心算子：Reader 负责文件读取和行级命令生成，PK Index 负责索引维护和删除解析。通过 Flink 事件时间定时器机制解决了并行读取下删除解析的时序正确性问题，并通过 per-key 检查和广播清除保证了索引状态的正确性。这是 equality delete 到 deletion vector 转换功能的关键组件，配有超过 1000 行的详细测试覆盖各种边界场景。
