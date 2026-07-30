# 提交 3395：Core: Rename cleanUpOnCommitFailure to cleanUp and make it protected (#15389)

## 提交信息

- **序号**：3395 / 4088
- **哈希**：063c463fe27692fb0fa8ea249d02015cc31cae9d
- **短哈希**：063c463f
- **日期**：2026-03-16 12:46:06 +0100
- **作者**：Denys Kuzmenko
- **提交说明**：Core: Rename cleanUpOnCommitFailure to cleanUp and make it protected (#15389)
- **PR/Issue**：#15389

## 总体目的

将 `BaseTransaction` 中的 `cleanUpOnCommitFailure()` 方法重命名为 `cleanUp()`，并将其访问修饰符从 `private` 改为 `protected`。重命名使方法名更简洁通用，去除"仅用于 commit 失败"的语义限制；改为 `protected` 则允许子类覆盖清理逻辑，为后续扩展（如自定义事务实现需要定制清理行为）提供钩子。

背景：`cleanUpOnCommitFailure` 当前仅在 commit 失败的 catch 块中被调用，方法名过于具体。随着事务清理需求演进，需要更通用的命名和可继承的访问级别，以便子类（如不同 catalog 的事务实现）能够定制失败后的清理行为。

## 如何达成设计目的

直接重命名方法并修改访问修饰符，同时更新所有调用点（两处 catch 块）。改动范围小且机械。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java` (+3/-3 lines)

**修改目的**：重命名方法并放宽访问权限以支持子类覆盖。

**工作逻辑**：
- 方法声明：`private void cleanUpOnCommitFailure()` → `protected void cleanUp()`。
- 调用点 1（`catch (PendingUpdateFailedException e)`）：`cleanUpOnCommitFailure()` → `cleanUp()`。
- 调用点 2（`catch (RuntimeException e)`，条件 `!ops.requireStrictCleanup() || e instanceof CleanableFailure`）：`cleanUpOnCommitFailure()` → `cleanUp()`。
- 方法体不变，仍执行 `cleanAllUpdates()` 等清理逻辑。

## 总结

这是一次小范围的方法重命名 + 访问修饰符调整重构。将 `cleanUpOnCommitFailure` 重命名为 `cleanUp` 使其语义更通用，从 `private` 改为 `protected` 允许子类定制清理逻辑，为后续事务清理行为的扩展打下基础。功能行为不变，属于可扩展性改进。
