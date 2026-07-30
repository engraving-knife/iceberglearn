# 提交 2440：Core: refactor BaseTransaction for extensibility (#13631)

## 提交信息

- **序号**：2440 / 4088
- **哈希**：a8fdb23682a7c083ea6ff9873f1531dd9d465aa7
- **短哈希**：a8fdb2368
- **日期**：2025-08-01 13:50:10 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: refactor BaseTransaction for extensibility (#13631)
- **PR/Issue**：#13631

## 总体目的

本提交对 `BaseTransaction` 进行了重构，旨在提升其可扩展性，使子类能够更方便地自定义事务中的操作（如 append、overwrite 等）。重构的核心是引入了一个统一的 `appendUpdate` 模板方法，将所有操作创建时重复的样板代码（检查上次操作是否已提交、设置 deleteWith、设置 reportWith、加入 updates 列表）集中到一处。

此前，`BaseTransaction` 中每个操作方法（如 `updateSchema`、`newAppend`、`newRewrite` 等，共 18 个方法）都各自重复实现相同的模式：调用 `checkLastOperationCommitted`、创建 update 对象、如果是 SnapshotUpdate 则 `deleteWith(enqueueDelete)`、如果是 SnapshotProducer 则 `reportWith(reporter)`、加入 `updates` 列表、返回。这种重复不仅冗长，而且使得子类难以自定义——因为样板逻辑散落在每个方法中，子类 override 一个方法就必须复制全部样板代码。

重构后，所有操作方法只需一行 `return appendUpdate(new XxxUpdate(...))`，样板逻辑集中在 `appendUpdate` 中。同时 `appendUpdate` 被声明为 `protected final`，子类可以调用它来构建自定义操作（如测试中演示的 `AppendToBranchTransaction`）。此外，`checkLastOperationCommitted` 对 `SnapshotManager` 做了特殊处理（其内部自行管理提交），不再设置 `hasLastOpCommitted = false`。

## 如何达成设计目的

1. 新增 `protected final <T extends PendingUpdate> T appendUpdate(T update)` 模板方法，集中处理检查、deleteWith、reportWith、加入列表。
2. 重构 `checkLastOperationCommitted` 为接受 `Class<? extends PendingUpdate>` 参数的私有方法，从类名推导操作名称，并对 `SnapshotManager` 做特殊处理（不重置 `hasLastOpCommitted`）。
3. 将所有 18 个操作方法简化为一行调用 `appendUpdate`。
4. 在测试中新增 `AppendToBranchTransaction` 子类和 `testExtendBaseTransaction` 测试，演示并验证扩展能力。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java` (+101/-99 lines)

**修改目的**：重构为可扩展的模板方法模式。

**工作逻辑**：
- **`appendUpdate`**：新增 `protected final <T extends PendingUpdate> T appendUpdate(T update)` 方法。先调用 `checkLastOperationCommitted(update.getClass())` 检查上次操作已提交；若 update 是 `SnapshotUpdate` 则 `deleteWith(enqueueDelete)`；若 update 是 `SnapshotProducer` 则 `reportWith(reporter)`；加入 `this.updates`；返回 update。
- **`checkLastOperationCommitted`**：改为接受 `Class<? extends PendingUpdate>` 参数。从类的接口（取第一个接口的 simpleName，若无则取类 simpleName）推导操作名称用于错误信息。关键改动：`SnapshotManager` 内部自行管理提交，因此对其不设置 `hasLastOpCommitted = false`，其他类型照常设置。
- **18 个操作方法简化**：每个方法从 4-6 行简化为一行，如 `updateSchema()` 变为 `return appendUpdate(new SchemaUpdate(transactionOps));`，`newAppend()` 变为 `return appendUpdate(new MergeAppend(tableName, transactionOps));`。原来分散在各方法中的 `deleteWith` 和 `reportWith` 调用被移入 `appendUpdate`，因为 `MergeAppend`、`FastAppend` 等同时实现了 `SnapshotUpdate` 和 `SnapshotProducer` 接口。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java` (+60/-1 lines)

**修改目的**：验证 BaseTransaction 的可扩展性。

**工作逻辑**：
- `testExtendBaseTransaction`：定义内部类 `AppendToBranchTransaction extends BaseTransaction`，override `newAppend()` 方法创建 `MergeAppend` 并调用 `.toBranch("branch")` 实现向分支追加。测试流程：先在分支上追加 FILE_A（不提交），验证主表 metadata 未变；再向主表追加 FILE_B 并提交（version=1）；最后提交分支事务（version=2），验证 refs 同时包含 main 和 branch，且各自的 snapshot 和 manifest 数量正确。这验证了子类可以通过 override + `appendUpdate` 来自定义事务行为。
- 附带的小改动：`ManifestWriter` 改为 try-with-resources；移除一个测试方法的 `throws IOException`。

## 总结

本提交通过引入 `appendUpdate` 模板方法，将 `BaseTransaction` 中 18 个操作方法的重复样板代码集中到一处，大幅减少了代码冗余（净减少约 40 行），更重要的是使 `BaseTransaction` 可被子类扩展——子类只需 override 特定操作方法并调用 `appendUpdate` 即可实现自定义逻辑（如向特定分支写入）。测试通过 `AppendToBranchTransaction` 演示了这一扩展能力。同时修复了 `SnapshotManager` 在事务中的提交状态管理问题。
