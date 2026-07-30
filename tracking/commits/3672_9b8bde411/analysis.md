# 提交 3672：ORC: Backport add _row_id and _last_updated_sequence_number reader in Orc to support lineage (#16256)

## 提交信息

- **序号**：3672 / 4088
- **哈希**：9b8bde411f81af88fb87457ebe3b78c62faee2eb
- **短哈希**：9b8bde411
- **日期**：2026-05-08 20:29:35 +0200
- **作者**：pvary
- **提交说明**：ORC: Backport add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#16256)
- **PR/Issue**：#16256（backport #15776）

## 总体目的

这个提交将 PR #15776（即本系列第 3667 号提交，ORC lineage 支持）backport 到 Flink 1.20 和 Flink 2.0。

原 PR #15776 为 ORC 读取器添加了 `_row_id` 和 `_last_updated_sequence_number` 元数据列的读取支持（用于 lineage），并将 ORC StructReader 从基于位置的绑定改为基于 field-id 的绑定。原 PR 涉及 Flink 2.1 和 Spark 4.0 的 reader 适配。本 backport 将 Flink 侧的 reader 适配改动应用到 Flink 1.20 和 2.0，使这两个版本也能支持 ORC lineage。

注意：ORC 核心模块（`OrcValueReaders`、`ORC.java`）和 Generic/Spark reader 的改动在原 PR 中已提交且为版本共享，本 backport 仅需调整 Flink 1.20 和 2.0 的 reader 适配。

## 如何达成设计目的

将 Flink 2.1 的 ORC reader 适配改动应用到 Flink 1.20 和 2.0：
1. `FlinkOrcReader`：适配新的 StructReader 构造函数（基于 TypeDescription）。
2. `FlinkOrcReaders`：适配新构造函数并支持元数据列。
3. 测试：适配 lineage 集成测试。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReader.java` (+1/-1 line)

**修改目的**：适配新的 StructReader 构造函数。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkOrcReaders.java` (+12/-3 lines)

**修改目的**：适配新构造函数并支持元数据列读取。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+34/-17 lines)

**修改目的**：适配 lineage 集成测试。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+27/-8 lines)

**修改目的**：适配 lineage 测试基类。

### `flink/v2.0/` 下对应文件

与 v1.20 完全相同的改动，为 Flink 2.0 应用相同适配。

## 总结

这个提交将 ORC lineage 支持（`_row_id` 和 `_last_updated_sequence_number` 读取）backport 到 Flink 1.20 和 2.0。backport 仅包含 Flink 侧的 ORC reader 适配改动，ORC 核心模块的改动已在原 PR 中完成。至此 ORC lineage 支持覆盖 Flink 1.20、2.0、2.1 三个版本。
