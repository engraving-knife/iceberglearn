# 提交 3371：Core: Make sequence number conflicts retryable when there are concurrent commits (#15126)

## 提交信息

- **序号**：3371 / 4088
- **哈希**：1d6c685c1391df604d59f3335a1fba7a1af2c25f
- **短哈希**：1d6c685c1
- **日期**：2026-03-10
- **作者**：Xinyi Lu
- **提交说明**：Core: Make sequence number conflicts retryable when there are concurrent commits (#15126)
- **PR/Issue**：#15126

## 总体目的

在 Iceberg 的 REST Catalog 架构中，当多个并发提交同时到达服务器时，可能会出现序列号冲突（sequence number conflict）问题。具体场景是：客户端 A 基于当前表状态准备了一个提交，该提交携带的快照有一个预期的序列号。但在提交到达服务器之前，客户端 B 已经成功提交了一个快照，使得服务器端的 `lastSequenceNumber` 已经前进。当客户端 A 的提交到达服务器时，`TableMetadata.addSnapshot` 中的校验会发现提交的序列号不大于当前的 `lastSequenceNumber`，从而抛出 `ValidationException`。

问题在于，`ValidationException` 是一个不可重试的异常，REST 客户端收到后不会自动重试，而是直接将错误暴露给用户。但实际上这种冲突是临时的——客户端 A 只需要刷新表元数据，重新获取最新的序列号，然后重新提交即可成功。这属于"可重试的验证失败"，而非永久性的数据冲突。

本次提交通过引入新的 `RetryableValidationException` 类来区分这类可重试的验证失败，并在 `CatalogHandlers` 中将其捕获并包装为 `CommitFailedException`，使 REST 客户端能够识别并自动重试提交。

此外，对于 format version 3 的表（支持 row lineage），`first-row-id` 落后于表 `next-row-id` 的情况也属于同类可重试冲突，同样改为使用 `RetryableValidationException`。

## 如何达成设计目的

设计分为三层：核心层新增 `RetryableValidationException` 类作为 `ValidationException` 的子类，语义上表示"可重试的验证失败"；在 `TableMetadata.addSnapshot` 中将序列号和 first-row-id 的校验从 `ValidationException.check` 改为 `RetryableValidationException.check`；在 REST 服务端 `CatalogHandlers` 中捕获 `RetryableValidationException` 并包装为 `CommitFailedException`（通过 `ValidationFailureException`），使客户端的提交重试机制能够生效。测试覆盖了单元测试（验证抛出正确异常类型）和集成测试（模拟并发提交场景验证客户端收到 `CommitFailedException`）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RetryableValidationException.java` (+48/-0 lines)

**修改目的**：新增可重试验证异常类，区分可重试与不可重试的验证失败。

**工作逻辑**：
该类继承自 `ValidationException`，文档注释明确说明它不是冲突（conflict），而是因为提交包含了过时的值（如序列号或 first-row-id 落后于当前表状态），通过刷新元数据后重试可以解决。提供两个构造函数（带/不带 cause）和一个静态 `check` 方法：`public static void check(boolean test, String message, Object... args)`，当 `test` 为 false 时抛出 `RetryableValidationException`。使用 `@FormatMethod` 注解确保格式化字符串的安全性。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+4/-2 lines)

**修改目的**：将序列号和 first-row-id 的校验改为抛出可重试异常。

**工作逻辑**：
两处改动：
1. 序列号校验：原来 `ValidationException.check(formatVersion == 1 || snapshot.sequenceNumber() > lastSequenceNumber || snapshot.parentId() == null, ...)` 改为 `RetryableValidationException.check(...)`，表示当提交的序列号不大于当前表的 lastSequenceNumber 时，这是可重试的。
2. first-row-id 校验（format version >= 3，支持 row lineage）：原来 `ValidationException.check(snapshot.firstRowId() != null && snapshot.firstRowId() >= nextRowId, ...)` 改为 `RetryableValidationException.check(...)`，表示当提交的 first-row-id 落后于表的 next-row-id 时，这是可重试的。

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+13/-3 lines)

**修改目的**：在 REST 服务端捕获 `RetryableValidationException` 并包装为 `CommitFailedException`。

**工作逻辑**：
在 `applyChanges` 方法中，原来直接 `request.updates().forEach(update -> update.applyTo(metadataBuilder))`，修改后用 try-catch 包裹：
```java
try {
  request.updates().forEach(update -> update.applyTo(metadataBuilder));
} catch (RetryableValidationException e) {
  throw new ValidationFailureException(
      new CommitFailedException(e, "Validation failed, please retry: %s", e.getMessage()));
}
```
注释解释了设计考量：服务端重试无济于事，因为过时的值在请求本身中；将其包装为 `CommitFailedException` 后，REST 客户端可以识别并使用刷新后的元数据重新提交。`ValidationFailureException` 是 REST 层的异常类型，最终会映射为 HTTP 409 状态码，触发客户端的提交重试逻辑。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (+78/-0 lines)

**修改目的**：验证序列号和 first-row-id 过时时抛出 `RetryableValidationException`。

**工作逻辑**：
新增两个测试：
1. `testAddSnapshotWithStaleSequenceNumberIsRetryable`：先添加一个根快照（seqNum=1），然后添加一个 seqNum=1 且有 parentId 的快照，验证抛出 `RetryableValidationException`，消息包含 "Cannot add snapshot with sequence number"。
2. `testAddSnapshotWithStaleFirstRowIdIsRetryable`：在 format version 3 的表上，先添加一个分配了 5 行的快照（nextRowId 变为 5），然后添加一个 firstRowId=0 的快照（0 < 5），验证抛出 `RetryableValidationException`，消息包含 "Cannot add a snapshot, first-row-id is behind table next-row-id"。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+57/-0 lines)

**修改目的**：验证并发提交场景下客户端收到 `CommitFailedException` 而非不可重试的异常。

**工作逻辑**：
新增 `testSequenceNumberConflictThrowsCommitFailed` 测试。设计巧妙地使用 Mockito spy 拦截 REST 适配器的 `execute` 方法：在主分支的提交请求到达服务器执行前，先向 `other` 分支提交一个快照，使服务器的 `lastSequenceNumber` 前进。然后再执行主分支的提交，此时该提交携带的序列号已过时。断言客户端收到 `CommitFailedException`，消息包含 "Validation failed, please retry"。这验证了从客户端视角看，这类冲突是可重试的提交失败而非永久错误。

## 总结

本次提交通过引入 `RetryableValidationException` 并在 REST 层将其包装为 `CommitFailedException`，优雅地解决了并发提交时序列号冲突导致不可重试的问题。设计层次清晰：核心层标记可重试异常，服务端捕获并转换，客户端自动重试。测试覆盖了单元测试和集成测试，对提升 Iceberg 在高并发场景下的提交可靠性有重要价值。
