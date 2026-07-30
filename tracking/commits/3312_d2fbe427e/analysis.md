# 提交 3312：Flink: Backport TableMaintenance Support Coordinator Lock to 1.20 and 2.0 (#15438)

## 提交信息

- **序号**：3312 / 4088
- **哈希**：d2fbe427ecec298f1600260f40fcab1b7ab2e695
- **短哈希**：d2fbe427e
- **日期**：2026-02-25
- **作者**：GuoYu
- **提交说明**：Flink: Backport TableMaintenance Support Coordinator Lock to 1.20 and 2.0 (#15438)
- **PR/Issue**：#15438（回移 #15151）

## 总体目的

本提交是提交 3310（#15151，Flink: TableMaintenance Support Coordinator Lock）的回移（backport），将协调锁功能从 Flink 2.1 回移到 Flink 1.20 和 Flink 2.0 两个版本分支。回移的源改动是 #15151，该改动引入了基于 Flink OperatorCoordinator 事件的内置协调锁机制，替代外部 `TriggerLockFactory`，使 Flink TableMaintenance 可以无需外部锁基础设施即可运行。

回移的原因是 Iceberg 同时维护多个 Flink 版本分支（1.20、2.0、2.1），协调锁作为一项重要的可用性改进，需要尽快惠及使用旧版本 Flink 的用户。由于不同 Flink 版本的 API 可能存在细微差异，回移时针对各版本做了适配调整（如 `LockRemoverOperatorFactory` 在 1.20 中为 88 行，2.0 中为 86 行，2.1 中为 86 行；`TriggerManagerOperatorFactory` 在 1.20 中为 113 行，2.0 和 2.1 中为 110 行；`TestTriggerManagerOperator` 在 1.20 中为 673 行，2.0 和 2.1 中为 668 行），但核心设计完全一致。

## 如何达成设计目的

将 #15151 在 `flink/v2.1/` 下的全部 19 个文件（12 个主代码 + 7 个测试代码）复制到 `flink/v1.20/` 和 `flink/v2.0/` 对应路径，并根据各 Flink 版本的 API 差异做适配。总计修改 38 个文件，新增约 5571 行，删除约 100 行（删除部分来自两个版本各自的 `TableMaintenance.java` 和 `TriggerManager.java` 原有代码替换）。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+90/-42 lines)

**修改目的**：为 Flink 1.20 回移协调锁 API。

**工作逻辑**：与 3310 中 `flink/v2.1` 版本完全一致——新增不传 `lockFactory` 的重载方法，旧方法标记 `@Deprecated`，`append()` 根据 `lockFactory` 是否为 null 选择协调锁或外部锁拓扑。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/BaseCoordinator.java` (+306 lines, 新文件)

**修改目的**：为 Flink 1.20 回移 Coordinator 基类。

**工作逻辑**：与 3310 完全一致，提供线程管理、SubtaskGateway 管理和锁事件注册/转发。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRegisterEvent.java` (+47 lines, 新文件)

**修改目的**：回移锁注册事件类。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockReleaseEvent.java` (+56 lines, 新文件)

**修改目的**：回移锁释放事件类。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverCoordinator.java` (+60 lines, 新文件)

**修改目的**：回移 LockRemover 的 Coordinator。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperator.java` (+111 lines, 新文件)

**修改目的**：回移 LockRemover 算子。与 3310 基本一致（行数差 1 行，为版本适配微调）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemoverOperatorFactory.java` (+88 lines, 新文件)

**修改目的**：回移 LockRemover 算子工厂。与 3310 基本一致（2.1 版本为 86 行，此处 88 行为版本适配差异）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java` (+3/-20 lines)

**修改目的**：抽取 `nextTrigger` 到 `TriggerUtil`。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerCoordinator.java` (+59 lines, 新文件)

**修改目的**：回移 TriggerManager 的 Coordinator。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerOperator.java` (+322 lines, 新文件)

**修改目的**：回移 TriggerManager 算子。与 3310 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManagerOperatorFactory.java` (+113 lines, 新文件)

**修改目的**：回移 TriggerManager 算子工厂。与 3310 基本一致（2.1 版本为 110 行，此处 113 行为版本适配差异）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerUtil.java` (+46 lines, 新文件)

**修改目的**：回移触发工具类。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestMaintenanceE2E.java` (+39 lines)

**修改目的**：回移协调锁端到端测试。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestTableMaintenanceCoordinationLock.java` (+344 lines, 新文件)

**修改目的**：回移协调锁集成测试。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../CoordinatorTestBase.java` (+43 lines, 新文件)

**修改目的**：回移 Coordinator 测试基类。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestLockRemoveCoordinator.java` (+69 lines, 新文件)

**修改目的**：回移 LockRemoverCoordinator 测试。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestLockRemoverOperation.java` (+207 lines, 新文件)

**修改目的**：回移 LockRemoverOperator 测试。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestTriggerManagerCoordinator.java` (+102 lines, 新文件)

**修改目的**：回移 TriggerManagerCoordinator 测试。与 3310 一致。

### `flink/v1.20/flink/src/test/java/.../TestTriggerManagerOperator.java` (+673 lines, 新文件)

**修改目的**：回移 TriggerManagerOperator 测试。与 3310 基本一致（2.1 版本为 668 行，此处 673 行为版本适配差异）。

### `flink/v2.0/` 下 19 个文件

**修改目的**：将上述同样 19 个文件回移到 Flink 2.0 版本分支。

**工作逻辑**：与 `flink/v1.20/` 下的改动完全对应，核心逻辑与 3310（`flink/v2.1/`）一致。行数与 2.1 版本基本相同（`LockRemoverOperatorFactory` 86 行、`TriggerManagerOperatorFactory` 110 行、`TestTriggerManagerOperator` 668 行），说明 2.0 与 2.1 的 Flink API 差异更小。

## 总结

本提交将协调锁功能（#15151）从 Flink 2.1 回移到 Flink 1.20 和 2.0 两个版本分支，共 38 个文件、约 5571 行新增。回移内容与源提交完全对应，仅根据各 Flink 版本 API 差异做了少量适配。这确保了使用旧版本 Flink 的用户也能受益于无需外部锁基础设施的 TableMaintenance 协调锁机制。
