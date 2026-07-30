# 提交 0656：Hive: Avoid NPE on Throwables without error msg (#10069)

## 提交信息

- **序号**：0656 / 4088
- **哈希**：c65023b1e740280044da034150eacf745147e26a
- **短哈希**：c65023b1e
- **日期**：2024-04-03 21:27:39 +0800
- **作者**：lurnagao-dahua
- **提交说明**：Hive: Avoid NPE on Throwables without error msg (#10069)
- **PR/Issue**：#10069

## 总体目的

这个提交修复了 `HiveTableOperations` 提交（commit）流程中的一个空指针异常（NPE）隐患。在 Hive 元数据存储的表提交路径中，`commit` 方法用一个大型的 `catch (Throwable e)` 块来兜底处理各种未知异常。在该块内，代码会调用 `e.getMessage().contains(...)` 来判断异常消息是否包含特定字符串，从而识别出"表已被并发修改"这一已知场景，并将其转换为语义更明确的 `CommitFailedException`。

问题在于：`Throwable.getMessage()` 允许返回 `null`（例如 `new RuntimeException()` 这样不带消息的异常，其 `getMessage()` 就是 `null`）。当底层抛出一个没有错误消息的 `Throwable` 时，原代码直接对 `e.getMessage()` 的返回值调用 `.contains(...)`，等价于对 `null` 调用方法，从而抛出 `NullPointerException`。这个二次抛出的 NPE 会掩盖原始异常，并且发生在 `catch` 块内、尚未设置 `commitStatus` 之前，使提交状态更难判定，破坏了原本期望的 `CommitStateUnknownException` 兜底逻辑。

值得注意的是，同一方法内紧随其后的另一个类似判断（针对 `"Table/View 'HIVE_LOCKS' does not exist"`）**已经**带有 `e.getMessage() != null &&` 的保护，说明这是一个既有不一致——一条路径已防护空消息，另一条却没有。本提交正是补齐了这一遗漏。

## 如何达成设计目的

修复策略非常直接：在调用 `e.getMessage().contains(...)` 之前先短路判断 `e.getMessage() != null`，与同方法内已有的 `HIVE_LOCKS` 判断保持一致的写法。由于短路求值，当消息为 `null` 时不会再访问 `.contains()`，从而避免 NPE；当消息非 `null` 时行为与原来完全一致。这样既修复了缺陷，又不改变任何已有的异常分类语义。

同时新增了一个单元测试 `testCommitExceptionWithoutMessage`，通过 Mockito 的 `spy` 让 `persistTable` 抛出一个不带消息的 `RuntimeException`，验证此时提交会以 `CommitStateUnknownException` 的形式抛出（消息以 `"null\nCannot determine whether the commit was successful or not"` 开头），而不是以 NPE 形式抛出，从而锁定修复行为。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：避免对没有错误消息的 `Throwable` 调用 `.contains()` 而引发的 NPE。

**工作逻辑**：

在 `catch (Throwable e)` 分支中，原代码为：

```java
if (e.getMessage()
    .contains(
        "The table has been modified. The parameter value for key '"
            + HiveTableOperations.METADATA_LOCATION_PROP
            + "' is")) {
  throw new CommitFailedException(
      e, "The table %s.%s has been modified concurrently", database, tableName);
}
```

修改后为：

```java
if (e.getMessage() != null
    && e.getMessage()
        .contains(
            "The table has been modified. The parameter value for key '"
                + HiveTableOperations.METADATA_LOCATION_PROP
                + "' is")) {
  throw new CommitFailedException(
      e, "The table %s.%s has been modified concurrently", database, tableName);
}
```

关键点：`e.getMessage() != null` 作为短路条件先行求值。当消息为 `null` 时，整个条件为 `false`，跳过该分支，异常继续向下走到后续的 `HIVE_LOCKS` 判断（同样带 null 保护）和最终的 `LOG.error` + `CommitStateUnknownException` 兜底路径，从而把"无法判定提交结果"的语义正确地传递给上层，而不是被一个意外 NPE 打断。这与同方法内已有的 `HIVE_LOCKS` 判断写法完全对齐，消除了同一 `catch` 块内两条路径的防护不一致。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommits.java`

**修改目的**：新增针对"无消息异常"场景的回归测试，确保修复后行为稳定。

**工作逻辑**：

新增测试 `testCommitExceptionWithoutMessage`，其步骤为：
1. 加载测试表并取得其 `HiveTableOperations`。
2. 先提交一次 schema 变更（新增列 `n`），使元数据推进到一个新版本，并 `ops.refresh()` 刷新到最新。
3. 用 Mockito `spy(ops)` 创建一个被代理的 `HiveTableOperations`，并通过 `doThrow(new RuntimeException()).when(spyOps).persistTable(any(), anyBoolean(), any())` 让 `persistTable` 抛出一个**不带消息**的 `RuntimeException`。
4. 调用 `spyOps.commit(ops.current(), metadataV1)`（用旧元数据作为 base 提交，触发 `persistTable` 抛异常）。
5. 断言抛出的是 `CommitStateUnknownException`，且消息以 `"null\nCannot determine whether the commit was successful or not"` 开头。

其中断言里的 `"null"` 正是被包装异常 `getMessage()` 为 `null` 时拼出的字符串表现。这验证了在无消息异常下，提交流程不再抛 NPE，而是按预期进入 `CommitStateUnknownException` 兜底分支。注意：该测试需要 `RuntimeException` 在到达本提交修复的 `catch (Throwable e)` 之前不被其它更具体的 `catch` 分支捕获，因此其触发路径对应的是 `persistTable` 抛出的、不属于 `AlreadyExistsException`/`InvalidObjectException`/`LockException` 等已分类异常的通用 `Throwable`。

## 小结

- **成效**：成功达成目的。修复后，当 Hive 提交过程中遇到无消息的 `Throwable` 时，不再因对 `null` 调用 `.contains()` 而抛出 NPE，而是按设计落入 `CommitStateUnknownException` 兜底路径，与同方法内 `HIVE_LOCKS` 判断的防护保持一致。新增的测试锁定了该行为。
- **影响范围**：仅影响 `hive-metastore` 模块的 `HiveTableOperations.commit` 异常处理路径，对使用 Hive 元数据存储的 Iceberg 表提交流程生效。不改变任何正常的提交或已分类异常的处理逻辑。
- **回迁到 1.4.x 的注意事项**：该修改是纯防御性的、向后兼容的，可安全回迁。需注意测试用到的 Mockito `spy`/`doThrow` 与 `persistTable` 签名（`any(), anyBoolean(), any()`）需与 1.4.x 分支上 `HiveTableOperations.persistTable` 的实际签名一致；若 1.4.x 上该方法签名或参数数量不同，测试中的 `when(...).persistTable(...)` 匹配器需相应调整。生产代码本身的改动不依赖任何新 API，回迁无风险。
