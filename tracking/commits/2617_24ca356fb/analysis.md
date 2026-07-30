# 提交 2617：Spark 4.0, 3.4: Backport #13824 to Support Trigger AvailableNow in SS (#14026)

## 提交信息

- **序号**：2617 / 4088
- **哈希**：24ca356fb4ecd48d593949fd25c852c21bc87d53
- **短哈希**：24ca356fb
- **日期**：2025-09-09 10:13:25 -0700
- **作者**：Alex Prosak
- **提交说明**：Spark 4.0, 3.4: Backport #13824 to Support Trigger AvailableNow in SS
- **PR/Issue**：#14026（backport 原始 PR #13824）

## 总体目的

这是将 PR #13824（即提交 2614，为 Spark 3.5 实现 `Trigger.AvailableNow()` 支持）backport 到 Spark 4.0 和 Spark 3.4 两个版本分支的提交。Iceberg 项目为不同 Spark 版本维护各自的源码目录（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`），功能需要在各版本目录中分别落地。

`Trigger.AvailableNow()` 是 Spark 3.3+ 引入的触发器模式，Spark 3.4 与 4.0 均支持。将此功能 backport 到这两个版本，使得使用 Spark 3.4 / 4.0 的用户也能在 Iceberg 流式读取中正确使用 AvailableNow 语义：查询启动时只处理当前可用数据，运行期间新写入的数据留待下次，处理完自行终止。

## 如何达成设计目的

与提交 2614 完全一致的设计思路，分别应用到 `spark/v3.4` 和 `spark/v4.0` 两个目录下的同名文件：

1. `SparkMicroBatchStream` 将实现的接口从 `SupportsAdmissionControl` 改为 `SupportsTriggerAvailableNow`。
2. 新增 `lastOffsetForTriggerAvailableNow` 字段与 `prepareForTriggerAvailableNow()` 方法，在查询启动前预计算快照上界。
3. 修改 micro-batch 循环终止条件，用预计算的 `latestSnapshotId` 替代动态的 `table.currentSnapshot().snapshotId()`。
4. 在 `TestStructuredStreamingRead3` 中为各 ReadLimit 测试增加 AvailableNow 变体，并新增两个专门测试（不挂起/不重复处理、运行期间不处理新数据）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+25/-4 lines)

**修改目的**：为 Spark 3.4 实现 SupportsTriggerAvailableNow。

**工作逻辑**：与提交 2614 中 Spark 3.5 的改动完全一致——接口替换、新增字段与方法、修改循环终止条件。详见 2614 的分析。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+148 lines)

**修改目的**：为 Spark 3.4 增加 AvailableNow 测试覆盖。

**工作逻辑**：与 2614 中 Spark 3.5 的测试改动一致——重载 `assertMicroBatchRecordSizes` 支持 Trigger 参数、为各 ReadLimit 测试增加 AvailableNow 变体、新增两个专门测试。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+25/-4 lines)

**修改目的**：为 Spark 4.0 实现 SupportsTriggerAvailableNow。

**工作逻辑**：同上，与 2614 改动一致。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+149 lines)

**修改目的**：为 Spark 4.0 增加 AvailableNow 测试覆盖。

**工作逻辑**：同上，与 2614 改动一致。Spark 4.0 测试比 3.4/3.5 多 1 行，可能因版本间测试基类的细微差异，但核心测试内容一致。

## 总结

本提交是 PR #13824（提交 2614）向 Spark 4.0 和 3.4 的 backport，使这两个版本的 Iceberg 流源也支持 `Trigger.AvailableNow()`。改动内容与 2614 完全对应，分别落地到 `spark/v3.4` 和 `spark/v4.0` 目录。至此三个支持的 Spark 版本（3.4、3.5、4.0）均具备该能力，保证跨版本功能一致性。
