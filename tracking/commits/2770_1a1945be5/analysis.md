# 提交 2770：REST: Reconcile on CommitStateUnknown for simple update (#14320)

## 提交信息

- **序号**：2770 / 4088
- **哈希**：1a1945be5a46a981c20eb3a66b95084a2630bac9
- **短哈希**：1a1945be5
- **日期**：2025-10-19 19:15:59 -0700
- **作者**：Huaxin Gao
- **提交说明**：REST: Reconcile on CommitStateUnknown for simple update (#14320)
- **PR/Issue**：#14320

## 总体目的

在 REST Catalog 中，提交操作（commit）通过 HTTP 请求发送到服务端。当服务端实际完成了提交，但在返回响应时发生瞬时错误（如 5xx 服务端错误、网络中断等），客户端会收到 `CommitStateUnknownException`——即客户端无法确定提交是否成功。这是一个常见的不确定性问题：提交可能已成功，也可能未成功。

在此提交之前，遇到 `CommitStateUnknownException` 时客户端只能将异常抛出，让用户自己处理。这导致用户体验差：对于实际已成功的提交，用户可能会重试，从而产生重复的 snapshot 或其他问题。

本提交针对一类特殊的简单提交场景——只添加 snapshot（append-only）的 SIMPLE 更新——引入轻量级的"对账"（reconciliation）机制。对于这类提交，如果服务端确实已成功提交，那么刷新后的表元数据中应该能找到新添加的 snapshot。因此客户端可以在捕获 `CommitStateUnknownException` 后，主动刷新表元数据并检查预期的 snapshot 是否存在：若存在则认为提交已成功，静默返回；若不存在或刷新失败，则重新抛出原始异常。

## 如何达成设计目的

整体设计思路是在 `RESTTableOperations.commit()` 方法中捕获 `CommitStateUnknownException`，并对 SIMPLE 类型的更新尝试对账：

1. **判断是否可对账**：通过 `expectedSnapshotIdIfSnapshotAddOnly(updates)` 方法分析本次提交的 `MetadataUpdate` 列表。只有当更新列表中只包含一个 `AddSnapshot` 和（可选的）针对 main 分支的 `SetSnapshotRef`，且二者指向同一 snapshot 时，才认为这是"纯 append"操作，可以安全对账。任何其他类型的更新（如 rollback、分支操作、schema 变更等）都不可对账，直接抛出原始异常。

2. **执行对账**：调用 `refresh()` 刷新表元数据，检查预期的 snapshot 是否存在。存在则返回 true 表示对账成功；不存在或刷新抛异常则返回 false。

3. **异常处理**：对账过程中如果发生异常，将该异常作为 suppressed 异常附加到原始 `CommitStateUnknownException` 上，便于诊断，然后返回 false 让原始异常被重新抛出。

4. **测试覆盖**：新增两个测试——一个验证服务端实际提交成功但返回 CommitStateUnknown 时，客户端能通过对账识别成功；另一个验证服务端确实未提交时，客户端不会误判，且原始异常的 suppressed 列表为空。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableOperations.java` (+74/-2 lines)

**修改目的**：在 REST 表提交逻辑中加入对 `CommitStateUnknownException` 的捕获和轻量级对账。

**工作逻辑**：

- 在 `commit()` 方法中，将原来的 `client.post(...)` 调用包裹在 try-catch 中。捕获到 `CommitStateUnknownException` 时，判断当前是否为 SIMPLE 更新类型，并调用 `reconcileOnSimpleUpdate(updates, e)` 尝试对账。若对账成功则直接 `return`（提交视为完成）；否则重新 `throw e`。

- 新增 `reconcileOnSimpleUpdate(List<MetadataUpdate> updates, CommitStateUnknownException original)` 方法：先通过 `expectedSnapshotIdIfSnapshotAddOnly(updates)` 获取预期的 snapshot ID（若不可对账则返回 null，方法返回 false）；然后调用 `refresh()` 刷新元数据，检查 `refreshed.snapshot(expectedSnapshotId)` 是否非空。若刷新过程抛 `RuntimeException`，将其作为 suppressed 异常附加到原始异常上，返回 false。

- 新增静态方法 `expectedSnapshotIdIfSnapshotAddOnly(List<MetadataUpdate> updates)`：遍历更新列表，只允许出现 `AddSnapshot`（最多一个）和 `SetSnapshotRef`（且 name 必须为 `SnapshotRef.MAIN_BRANCH`）。若有多个 AddSnapshot、非 main 分支的 SetSnapshotRef、或任何其他类型更新，返回 null 表示不可安全对账。同时检查若同时有 AddSnapshot 和 main 的 SetSnapshotRef，二者 snapshotId 必须一致（否则说明是 rollback/move main 操作，不能仅凭 snapshot 存在判断 main 是否已指向它），不一致返回 null。最终返回 addedSnapshotId。

## 总结

本提交为 REST Catalog 的 SIMPLE append 提交引入了优雅的 `CommitStateUnknownException` 对账机制。对于只添加 snapshot 的纯 append 操作，客户端在遇到不确定的提交状态时，会主动刷新表元数据验证预期 snapshot 是否已落库，从而避免因瞬时网络/服务端错误导致的误报失败。设计上非常保守——只对"纯 append 到 main"这一最安全的场景做对账，其他复杂操作仍直接抛出异常让用户决策。对账失败时的异常也被妥善保存为 suppressed 异常以供诊断。这显著提升了 REST Catalog 在不稳定网络环境下的用户体验。
