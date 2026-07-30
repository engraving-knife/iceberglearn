# 提交 0232：Nessie: Reimplement namespace operations (#8857)

## 提交信息

- **序号**：0232 / 4088
- **哈希**：ea7665e788cb33507e6eb19eddd4f0c24eb92983
- **短哈希**：ea7665e78
- **日期**：2023-12-07 12:34:21 +0100
- **作者**：Alexandre Dutra
- **提交说明**：Nessie: Reimplement namespace operations (#8857)
- **PR/Issue**：#8857

## 总体目的

这个提交对 Iceberg 的 Nessie catalog 集成层中所有命名空间（namespace）操作进行了实质性重构。在此提交之前，`NessieIcebergClient` 中的 `createNamespace`、`listNamespaces`、`dropNamespace`、`loadNamespaceMetadata`、`setProperties`、`removeProperties` 六个方法都直接调用 Nessie 客户端提供的命名空间专用 API 端点（`createNamespace()`、`getMultipleNamespaces()`、`deleteNamespace()`、`getNamespace()`、`updateProperties()`）。这些专用端点虽然在服务端封装了命名空间的语义，但存在两个关键缺陷。

第一个也是最核心的缺陷，是命名空间专用 API 端点在提交时不会携带完整的提交元数据（commit metadata），尤其是作者信息（author）和应用标识（app-id、application-type）。提交说明明确指出："This change enhances the process of creating new namespaces by retaining commit authorship information when committing the new namespace."。这意味着通过 Iceberg catalog 创建的命名空间在 Nessie 的提交日志中缺失来源信息，不利于审计与追溯，也与表操作（rename、drop）走通用提交路径、能携带完整 commit metadata 的行为不一致。

第二个缺陷是异常处理与 Nessie 服务端返回的冲突信息（`ReferenceConflicts`）脱耦。原实现依赖 `NessieNamespaceAlreadyExistsException`、`NessieNamespaceNotFoundException`、`NessieNamespaceNotEmptyException` 等专用异常类型，而这些异常携带的信息有限，且无法与服务端返回的结构化冲突列表对齐，导致错误语义不够精确。

本提交通过"放弃使用命名空间专用 API 端点、改用通用内容（Content）操作 API"来解决上述问题：把命名空间视为 Nessie 的一种 `Content` 类型（`org.projectnessie.model.Namespace`），通过 `getContent()` 读取、通过 `commitMultipleOperations()` + `Operation.Put` / `Operation.Delete` 提交，从而让命名空间的创建/删除/属性修改与表操作走完全相同的提交通道，统一携带 commit metadata，并统一通过 `NessieReferenceConflictException` + `ReferenceConflicts` 进行冲突处理。这对 Iceberg-Nessie 集成的演进意义重大：它消除了命名空间与表在提交语义上的不对称，使所有 catalog 写操作具有一致的审计能力与错误处理模型。

## 如何达成设计目的

整体设计思路是"以通用内容操作替代专用命名空间端点"。具体通过以下结构性的改动达成：

1. **新增统一的提交重试基础设施**：在 `NessieIcebergClient` 中新增 `commitRetry(message, ops)` / `commitRetry(message, retryConflicts, CommitEnhancer)` 私有方法与 `CommitEnhancer` 函数式接口，封装"构建 commitMeta（带 author/app-id/application-type）→ 绑定当前 reference → 提交 → 失败时 refresh 并重试"的逻辑，取代原先散落在 `renameTable`、`dropTable` 中各自手写的 `Tasks.foreach(...).retry(5)` 重试块。
2. **重写六个命名空间方法**：全部改为先 `getContent` 探测、再 `commitRetry` 提交（或 `getEntries`+`getContent` 列举），并基于 `NessieReferenceConflictException` 的 `ReferenceConflicts` 精确映射到 Iceberg 异常。
3. **抽取冲突解析工具**：在 `NessieUtil` 中新增 `extractSingleConflict(ex, handledConflictTypes)`，把"从异常中取出唯一的、属于关注类型的冲突"这一逻辑从 `NessieTableOperations` 抽出并复用。
4. **大规模测试补全**：在 `TestNessieIcebergClient` 中新增覆盖正常路径、冲突路径、外部冲突、引用失效等场景的测试，并验证 commit metadata（author、message、properties）正确写入。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改目的**：重写全部命名空间操作，改用通用内容操作 API，并引入统一的提交重试基础设施。

**工作逻辑**：

这是本提交的核心文件，改动约 371 行。关键变化如下：

1. **`createNamespace(Namespace, Map)` 重写**：不再调用 `api.createNamespace().namespace(...).properties(...).create()`。新流程为：先 `api.getContent().reference(getReference()).key(key).get()` 探测 key 是否已存在，若存在则根据已存在内容是 Namespace 还是其他类型分别抛出 `AlreadyExistsException`（"Namespace already exists"）或 `AlreadyExistsException`（"Another content object with name ... already exists"）；若不存在则调用 `commitRetry("create namespace " + key, Operation.Put.of(key, content))` 提交。提交时若捕获到 `NessieReferenceConflictException`，通过 `NessieUtil.extractSingleConflict` 解析冲突类型：`KEY_EXISTS` 表示并发创建冲突（再查一次内容后抛 AlreadyExists），`NAMESPACE_ABSENT` 表示父命名空间不存在（抛 `NoSuchNamespaceException` 并指明父级）。新增了对空 namespace 的校验（`namespace.isEmpty()` 抛 `IllegalArgumentException`）。

2. **`listNamespaces(Namespace)` 重写**：不再调用 `api.getMultipleNamespaces()`。新流程改用 `api.getEntries()` 加 CEL 过滤器：`entry.contentType == 'NAMESPACE'` 且按层级深度与 key 前缀过滤（空 namespace 时取 `size(entry.keyElements) == 1`，否则取 `size == root.getElementCount()+1 && entry.encodedKey.startsWith('root.')`）；再用 `api.getContent()` 批量获取这些 key 的内容，`unwrap(Namespace.class)` 后映射回 Iceberg `Namespace`。若查询不到任何条目返回空列表；若引用失效抛 `NoSuchNamespaceException`（区分顶层与子级两种消息）。

3. **`dropNamespace(Namespace)` 重写**：不再调用 `api.deleteNamespace()`。新流程先 `getContent` 探测，若内容存在但类型非 NAMESPACE 则抛 `NoSuchNamespaceException`（"Content object ... is not a namespace"）；否则 `commitRetry("drop namespace " + key, Operation.Delete.of(key))`。冲突处理：`KEY_DOES_NOT_EXIST` 返回 `false`，`NAMESPACE_NOT_EMPTY` 抛 `NamespaceNotEmptyException`。

4. **`loadNamespaceMetadata(Namespace)` 重写**：不再调用 `api.getNamespace()`。改为 `api.getContent().key(key).get()`，用 `unwrapNamespace(...).orElseThrow(NoSuchNamespaceException)` 取出属性。

5. **`setProperties` / `removeProperties` 合并重写**：两者都委托给新增的私有方法 `updateProperties(Namespace, Consumer<Map<String,String>>)`。该方法通过 `commitRetry(..., true, commitBuilder -> {...})`（注意 `retryConflicts=true`，因为属性更新需要乐观重试）实现"读旧属性 → 应用变更 → 构造新 Namespace → Put 提交"的 read-modify-write 流程。`setProperties` 的 Consumer 是 `putAll`，`removeProperties` 是 `keySet().removeAll`。冲突处理中 `KEY_DOES_NOT_EXIST` 映射为 `NoSuchNamespaceException`，`NessieContentNotFoundException` 同样映射为 `NoSuchNamespaceException`。这里 `retryConflicts=true` 是关键：当外部并发修改了同一 namespace 导致 `NessieReferenceConflictException`（内容版本不匹配）时，会 refresh 引用后重试整个 read-modify-write，最终基于最新内容叠加自己的修改，保证属性更新不会因并发而丢失。

6. **`renameTable` / `dropTable` 重构**：这两个非命名空间方法也被改为使用新的 `commitRetry`，删除了各自手写的 `Tasks.foreach(operations).retry(5)...` 重试块，使所有提交路径统一。`dropTable` 中移除了 `threw` 标志位模式，改为正常返回 `true`、异常路径返回 `false`。

7. **新增 `commitRetry` 方法族**：两个重载。简单版 `commitRetry(message, Operation... ops)` 委托给增强版 `commitRetry(message, retryConflicts, CommitEnhancer)`。增强版用 `Tasks.range(1).retry(5)` 实现，`shouldRetry` 谓词默认不重试 `NessieNotFoundException`，且仅在 `retryConflicts=true` 时重试 `NessieConflictException`（否则冲突意味着乐观锁失败，对非 read-modify-write 场景直接抛出）。每次失败 `refresh()` 刷新引用。提交体通过 `CommitEnhancer.enhance(builder)` 注入操作，再统一 `.commitMeta(NessieUtil.buildCommitData(message, catalogOptions))` 携带 author/app-id/application-type。冲突时若 `retryConflicts` 则先 `refresh()` 再重试。

8. **新增辅助方法 `namespaceAlreadyExists(ContentKey, Content, Exception)`**：根据已存在内容是否为 Namespace 类型返回不同的 `AlreadyExistsException` 消息。

9. **新增 `unwrapNamespace(Content)`**：null 安全地把 `Content` 转为 `Optional<Namespace>`。

10. **新增 `CommitEnhancer` 接口**：函数式接口，允许调用方在提交前定制操作列表（用于 `updateProperties` 的 read-modify-write）。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieTableOperations.java`

**修改目的**：将冲突解析逻辑委托给 `NessieUtil.extractSingleConflict`，消除重复代码。

**工作逻辑**：

`maybeThrowSpecializedException(NessieReferenceConflictException)` 方法原本内联了"取 `ReferenceConflicts` → 检查 conflicts 大小为 1 → 按 conflictType switch 映射到 NoSuchNamespaceException/NamespaceNotEmptyException/NoSuchTableException/AlreadyExistsException"的逻辑。本提交将其改为调用 `NessieUtil.extractSingleConflict(ex, EnumSet.of(...))` 并以 `ifPresent(conflict -> {...})` 链式处理，switch 逻辑不变但代码更简洁，且冲突类型过滤逻辑集中到工具方法中复用。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieUtil.java`

**修改目的**：新增 `extractSingleConflict` 工具方法，供 `NessieIcebergClient` 与 `NessieTableOperations` 共享。

**工作逻辑**：

新增方法 `extractSingleConflict(NessieReferenceConflictException, Collection<Conflict.ConflictType>)`：从异常的 `getErrorDetails()`（即 `ReferenceConflicts`）中取出冲突列表，用传入的 `handledConflictTypes` 过滤，仅当过滤后恰好剩 1 个冲突时返回 `Optional.of(conflict)`，否则返回 `Optional.empty()`。相比原 `NessieTableOperations` 中的内联实现，这里增加了"按关注类型过滤"的能力——即使服务端返回多个冲突，只要其中属于关注类型的恰好一个，也能正确解析。这为命名空间操作中处理"KEY_EXISTS 与 NAMESPACE_ABSENT 共存"等场景提供了灵活性。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieIcebergClient.java`

**修改目的**：为重写后的命名空间操作补全端到端测试，覆盖正常路径、冲突、外部并发、引用失效，并验证 commit metadata。

**工作逻辑**：

新增约 458 行测试，关键用例包括：

- `testCreateNamespace`：验证创建后 commit log 中 `CommitMeta` 的 message（"create namespace a"）、author（"iceberg-user"）、properties（application-type=iceberg, app-id=iceberg-nessie）正确写入——这正是本提交的核心目标验证。
- `testCreateNamespaceInvalid`：空 namespace 抛 IllegalArgumentException；创建 `a.b` 时父 `a` 不存在抛 NoSuchNamespaceException。
- `testCreateNamespaceConflict`：重复创建抛 AlreadyExistsException；在已有表 `a.tbl` 上创建 namespace `a.tbl` 抛 "Another content object" 异常。
- `testCreateNamespaceExternalConflict`：通过直接 Nessie API 提交制造并发冲突，验证客户端仍能正确识别。
- `testCreateNamespaceNonExistingRef` / `testDropNamespaceNonExistingRef` / `testSetPropertiesNonExistingRef` / `testRemovePropertiesNonExistingRef`：删除分支后验证各操作抛出含 "ref ... is no longer valid" 的异常。
- `testDropNamespace` / `testDropNamespaceNotEmpty` / `testDropNamespaceConflict` / `testDropNamespaceExternalConflict`：覆盖删除正常、非空、类型冲突、外部并发修改（"Values of existing and expected content are different"）。
- `testSetProperties` / `testRemoveProperties`：验证属性增删改后 commit metadata（author、message "update namespace a"、properties）正确，且最终属性值符合预期。
- `testSetPropertiesExternalConflict` / `testRemovePropertiesExternalConflict`：外部并发更新 namespace 属性后，客户端 `setProperties`/`removeProperties` 能通过重试（`retryConflicts=true`）在最新内容基础上叠加自己的修改，验证了 read-modify-write 的正确性。
- `testSetPropertiesNonExistingNs` / `testRemovePropertiesNonExistingNs`：namespace 被外部删除后，属性操作抛 NoSuchNamespaceException。

新增私有辅助方法 `commit(branch, message, Operation...)`（直接通过 Nessie API 提交以模拟外部并发）、`fetchNamespace(key, branch)`、`newTableMetadata()`。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestMultipleClients.java`

**修改目的**：增强多客户端场景测试，验证重写后 listNamespaces 与 loadNamespaceMetadata 在多客户端、引用失效下的行为。

**工作逻辑**：

- `testListNamespaces`：新增空列表断言、对不存在的 namespace 列举返回空的断言；新增"删除分支后列举抛 NoSuchNamespaceException（区分顶层与子级消息）"的断言。
- `testLoadNamespaceMetadata`：新增"namespace 不存在时抛 NoSuchNamespaceException"断言；关键新增——验证"另一个客户端在旧 ref hash 上 `setProperties` 会抛 NoSuchNamespaceException"（因为 `updateProperties` 的 read-modify-write 依赖客户端持有的引用，旧 hash 上 getContent 读不到新 namespace），而同一客户端（已 refresh）`setProperties` 成功；并验证两客户端读取（loadNamespaceMetadata）都基于 HEAD，因此都能看到最新属性。删除分支后验证 loadNamespaceMetadata 抛 RuntimeException。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNamespace.java`

**修改目的**：适配 `dropNamespace` 非空时异常消息的变化。

**工作逻辑**：

将断言从精确匹配 `"Namespace 'test' is not empty. One or more tables exist."` 改为 `hasMessageContaining("Namespace 'test' is not empty")`，因为重写后 `NamespaceNotEmptyException` 的消息变为 `"Namespace '%s' is not empty."`（不再包含 "One or more tables exist." 后缀）。

## 小结

这个提交通过将 Nessie 命名空间操作从专用 API 端点迁移到通用内容提交通道，统一了命名空间与表的提交语义，使命名空间创建/修改能携带完整的 commit authorship 信息，并引入了统一的 `commitRetry` 重试基础设施与 `extractSingleConflict` 冲突解析工具，显著提升了 Iceberg-Nessie 集成的审计能力、并发正确性与代码一致性。
