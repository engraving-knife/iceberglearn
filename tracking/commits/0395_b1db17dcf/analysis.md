# 提交 0395：Core: Mark NoSuchViewException as CleanableFailure

## 提交信息

- **序号**：0395
- **哈希**：b1db17dcf75b8f77e44c32908bcf385eb1990431
- **短哈希**：b1db17dcf
- **日期**：2024-01-19（Fri Jan 19 18:34:01 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Mark NoSuchViewException as CleanableFailure
- **PR/Issue**：#9516

## 总体目的

本提交将 `NoSuchViewException` 标记为 `CleanableFailure`，使视图不存在异常与表不存在异常（`NoSuchTableException`）在清理策略上保持一致。这是 Iceberg 视图（View）功能完善过程中的一个重要补丁，解决了视图操作失败时临时文件未被清理的问题。

要理解本提交的意义，需要了解 Iceberg 的提交清理机制。Iceberg 在执行提交操作（如写入数据、更新元数据）时，会先生成临时的 manifest 文件和数据文件。如果提交失败，这些临时文件就成为"孤儿文件"，需要清理。但清理策略必须谨慎——并非所有失败都适合清理。例如 `CommitStateUnknownException` 表示提交状态未知（可能已成功），此时清理可能误删已提交的数据文件，因此绝不能清理。

为区分"可安全清理"和"不可清理"的失败，Iceberg 定义了 `CleanableFailure` 标记接口（marker interface）。核心模块的清理逻辑（`SnapshotProducer` 和 `BaseTransaction`）通过 `instanceof CleanableFailure` 判定：当严格清理模式开启（`requireStrictCleanup()` 默认返回 `true`）时，只有标记为 `CleanableFailure` 的异常才会触发临时文件清理。

在本次修改之前，`NoSuchTableException` 已经实现了 `CleanableFailure`——因为表不存在意味着提交必然失败，临时文件可安全清理。然而 `NoSuchViewException` 却未实现该接口，导致视图不存在时产生的临时文件无法被自动清理。这在视图功能日益成熟的背景下成为一个需要修复的不一致问题。本提交通过为 `NoSuchViewException` 添加 `implements CleanableFailure` 修复了这一不一致。

## 如何达成设计目的

修改方式极为简洁：在 `NoSuchViewException` 的类声明中添加 `implements CleanableFailure` 子句。由于 `CleanableFailure` 是一个空的标记接口（`public interface CleanableFailure {}`），不需要实现任何方法，仅需声明实现关系即可。这样，当视图操作因 `NoSuchViewException` 失败时，`SnapshotProducer` 和 `BaseTransaction` 中的 `e instanceof CleanableFailure` 判定将为 `true`，从而触发 `cleanAll()` / `cleanAllUpdates()` 清理临时文件。

## 修改详情

### api/src/main/java/org/apache/iceberg/exceptions/NoSuchViewException.java

**修改目的**：将 `NoSuchViewException` 标记为 `CleanableFailure`，使其在视图不存在时触发的提交失败能够自动清理临时文件。

**工作逻辑**：

修改前：
```java
public class NoSuchViewException extends RuntimeException {
```

修改后：
```java
public class NoSuchViewException extends RuntimeException implements CleanableFailure {
```

`CleanableFailure` 是 Iceberg API 模块中定义的标记接口，本身不包含任何方法，仅用于"标记"某类异常代表一种可安全清理的失败状态。核心模块中有三处清理逻辑依赖此标记：

1. **`SnapshotProducer.java`（第 499 行）**：
   ```java
   } catch (RuntimeException e) {
     if (!strictCleanup || e instanceof CleanableFailure) {
       Exceptions.suppressAndThrow(e, this::cleanAll);
     }
     throw e;
   }
   ```
   当 `strictCleanup` 为 `true`（默认）时，只有 `CleanableFailure` 异常才会触发 `cleanAll()` 清理已写入的 manifest 文件；否则直接抛出异常不清理。

2. **`BaseTransaction.java`（第 422、486、550 行）**：
   ```java
   } catch (RuntimeException e) {
     if (!ops.requireStrictCleanup() || e instanceof CleanableFailure) {
       cleanAllUpdates();
     }
     throw e;
   }
   ```
   事务提交失败时，同样的判定逻辑决定是否清理各 update 产生的临时文件。

3. **`TableOperations.requireStrictCleanup()`**：默认返回 `true`，意味着默认采用严格清理策略——仅 `CleanableFailure` 异常触发清理。

通过将 `NoSuchViewException` 标记为 `CleanableFailure`，当某次提交操作因视图不存在而失败时（例如在事务中引用了一个已被删除的视图），上述清理逻辑会识别此异常为"可清理"，进而删除操作过程中产生的临时 manifest 和数据文件。这与 `NoSuchTableException` 的行为完全一致，体现了"不存在 ⇒ 提交必然失败 ⇒ 临时状态可安全清理"的设计原则。

## 小结

本提交通过一行代码修改（添加 `implements CleanableFailure`）修复了 `NoSuchViewException` 与 `NoSuchTableException` 之间的清理策略不一致问题。这是一个典型的"标记接口"模式应用——通过类型标记而非条件分支来控制清理行为，体现了 Iceberg 在异常处理与资源清理方面的一致性设计哲学。

该修复在视图功能（View，Iceberg 1.4 引入的稳定特性）日益广泛使用的背景下尤为重要：视图不存在的场景在实际使用中并不罕见（如并发删除、元数据不一致等），若不清理临时文件会导致存储空间泄漏。此提交保障了视图操作失败时的资源清理正确性，是视图功能可靠性的重要补充。从分支维护角度看，这类一致性修复回溯到 1.4.x 维护分支是必要的，因为 1.4.x 是首个包含稳定视图 API 的发布线。
