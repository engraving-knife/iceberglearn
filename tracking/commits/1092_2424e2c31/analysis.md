# 提交 1092：Flink: Port #10992 to v1.19 (#10994)

## 提交信息

- **序号**：1092 / 4088
- **哈希**：2424e2c31dbee75d642fed4c10bf5bd9ebbe09bd
- **短哈希**：2424e2c31
- **日期**：2024-08-23 17:51:00 +0200
- **作者**：pvary
- **提交说明**：Flink: Port #10992 to v1.19 (#10994)
- **PR/Issue**：#10994（移植自 #10992）

## 总体目的

Iceberg 的 Flink 维护（maintenance）模块提供了一套基于 Flink 的表维护触发机制，其中 `TableChange` 用于描述表的一次变更事件（包含数据文件数、删除文件数、文件大小、提交数等指标），`TriggerEvaluator` 基于这些指标判断是否触发维护任务。原实现存在两个问题：

1. `TableChange` 只笼统统计"删除文件数"和"删除文件大小"，没有区分位置删除（position deletes）和等值删除（equality deletes）。这两种删除文件对维护策略的意义不同（位置删除通常可通过重写数据文件清除，等值删除则更复杂），笼统统计无法支持精细的维护触发条件。

2. 字段命名不规范，如 `dataFileNum`、`deleteFileNum`、`commitNum`、`fileSize` 等，不够清晰。

本提交（从 main 分支 #10992 移植到 v1.19 分支）重构 `TableChange`，将删除文件按 position/equality 拆分统计（文件数 + 记录数），并统一字段命名为更规范的 `xxxCount` / `xxxInBytes` 形式。`TriggerEvaluator` 同步支持基于拆分后指标的触发条件。

## 如何达成设计目的

主要改动集中在 `TableChange` 和 `TriggerEvaluator` 两个类：

1. `TableChange` 字段从 5 个（dataFileNum、deleteFileNum、dataFileSize、deleteFileSize、commitNum）扩展为 7 个（dataFileCount、dataFileSizeInBytes、posDeleteFileCount、posDeleteRecordCount、eqDeleteFileCount、eqDeleteRecordCount、commitCount）。构造函数从 `Snapshot` 构建时，根据 `deleteFile.content()` 区分 POSITION_DELETES 和 EQUALITY_DELETES 分别累加文件数和记录数。`merge`、`copy`、`equals`、`hashCode`、`toString`、Builder 全部同步更新。

2. `TriggerEvaluator.Builder` 把原来的 `commitNumber/fileNumber/fileSize/deleteFileNumber` 替换为 `dataFileCount/dataFileSizeInBytes/posDeleteFileCount/posDeleteRecordCount/eqDeleteFileCount/eqDeleteRecordCount/commitCount`，`build()` 时为每个非 null 阈值生成一个谓词（任一满足即触发）。同时移除了原来 `check` 方法中包裹 `RuntimeException` 的 try-catch（"Error accessing state"）。

3. `JdbcLockFactory` 顺带做了一些清理：把内部 `Lock` 类重命名为 `JdbcLock` 并改为 private，`Type.key` 字段加 final，移除冗余的 `catch (UncheckedSQLException e) { throw e; }`，修正一处错误消息文案。

4. 测试 `TestMonitorSource` 和 `TestTriggerManager` 全面更新到新 API，并为新增的 6 类触发条件分别补充测试方法。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/JdbcLockFactory.java` (+5/-9 lines)

**修改目的**：清理 `JdbcLockFactory` 内部实现，提高封装性。

**工作逻辑**：
- `createLock()` / `createRecoveryLock()` 中 `new Lock(...)` 改为 `new JdbcLock(...)`。
- 内部类 `Lock` 重命名为 `JdbcLock` 并从 `public static class` 改为 `private static class`，构造函数从 public 改为 private，避免外部直接依赖。
- `Type.key` 字段加 `final`（原本可变）。
- `isHeld` 的异常消息从 "Failed to get lock information for %s" 改为 "Failed to check the state of the lock %s"。
- `unlock` 方法删除冗余的 `catch (UncheckedSQLException e) { throw e; }`（直接捕获 SQLException 即可）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java` (+148/-45 lines)

**修改目的**：重构 `TableChange`，拆分 position/equality 删除统计并规范化命名。

**工作逻辑**：
字段从 5 个扩展为 7 个：
```java
private int dataFileCount;
private long dataFileSizeInBytes;
private int posDeleteFileCount;
private long posDeleteRecordCount;
private int eqDeleteFileCount;
private long eqDeleteRecordCount;
private int commitCount;
```
从 `Snapshot` 构建时按删除类型分别统计：
```java
deleteFiles.forEach(deleteFile -> {
  switch (deleteFile.content()) {
    case POSITION_DELETES:
      this.posDeleteFileCount++;
      this.posDeleteRecordCount += deleteFile.recordCount();
      break;
    case EQUALITY_DELETES:
      this.eqDeleteFileCount++;
      this.eqDeleteRecordCount += deleteFile.recordCount();
      break;
    default:
      throw new IllegalArgumentException("Unexpected delete file content: " + deleteFile);
  }
});
```
`merge`、`copy`、`equals`、`hashCode`、`toString`、`Builder` 全部按新字段重写。注意删除文件不再统计 `fileSizeInBytes`，改为统计 `recordCount`，这与维护策略更相关（删除记录数更能反映删除开销）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerEvaluator.java` (+52/-33 lines)

**修改目的**：`TriggerEvaluator` 支持基于拆分后指标的触发条件。

**工作逻辑**：
`Builder` 字段替换为新指标：
```java
private Integer dataFileCount;
private Long dataFileSizeInBytes;
private Integer posDeleteFileCount;
private Long posDeleteRecordCount;
private Integer eqDeleteFileCount;
private Long eqDeleteRecordCount;
private Integer commitCount;
```
`build()` 为每个非 null 阈值生成独立谓词，任一满足即触发：
```java
if (posDeleteFileCount != null) {
  predicates.add((change, unused, unused2) -> change.posDeleteFileCount() >= posDeleteFileCount);
}
// ... 类似处理其他指标
```
注意原来 `fileNumber` 是 `dataFileNum + deleteFileNum >= fileNumber`（数据+删除合计），`fileSize` 是 `dataFileSize + deleteFileSize >= fileSize`；新实现改为各指标独立判断，语义更精细。同时 `check` 方法移除了 try-catch 包裹：
```java
- predicates.stream().anyMatch(p -> { try { return p.evaluate(...); } catch (Exception e) { throw new RuntimeException("Error accessing state", e); } });
+ predicates.stream().anyMatch(p -> p.evaluate(event, lastTimeMs, currentTimeMs));
```

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestMonitorSource.java` (+18/-9 lines)

**修改目的**：更新测试到新 API。

**工作逻辑**：
- `TableChange.builder().dataFileNum(1).dataFileSize(size).commitNum(1).build()` 改为 `.dataFileCount(1).dataFileSizeInBytes(size).commitCount(1)`。
- `commitNum()` 断言改为 `commitCount()`。
- 计算删除大小时改用 `DeleteFile::recordCount` 而非 `fileSizeInBytes`，并用 `eqDeleteFileCount` / `eqDeleteRecordCount` 构建（注释说明当前测试只使用等值删除）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (+140/-42 lines)

**修改目的**：更新测试到新 API，并为新增的触发指标补充测试方法。

**工作逻辑**：
- 原 `testCommitNumber` 拆分为 `testCommitCount`（保留原提交数测试）。
- 新增 `testDataFileCount`、`testDataFileSizeInBytes`、`testPosDeleteFileCount`、`testPosDeleteRecordCount`、`testEqDeleteFileCount`、`testEqDeleteRecordCount` 六个测试方法，分别验证每种触发阈值。
- 原 `testFileNumber` / `testFileSize` / `testDeleteFileNumber` 被替换（原合计语义不再存在）。
- 所有使用 `commitNum` / `dataFileNum` / `dataFileSize` / `deleteFileNum` 的断言改为新命名。
- `manager` 工厂方法中 `commitNumber(2)` 改为 `commitCount(2)`。

## 总结

这是一次从 main 分支移植到 v1.19 分支的功能性重构提交。核心价值在于让 Flink 维护触发机制能够基于更精细的删除文件指标（区分 position/equality，统计记录数而非文件大小）来决定何时触发维护任务，使维护策略更贴合实际需求。同时统一了字段命名规范，清理了 `JdbcLockFactory` 的封装。测试覆盖充分，为每个新触发指标都补充了独立测试。属于提升维护模块表达力和可维护性的重要改进。
