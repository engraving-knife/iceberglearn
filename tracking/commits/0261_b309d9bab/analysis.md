# 提交 0261：Nessie: Support views for NessieCatalog (#8909)

## 提交信息

- **序号**：0261 / 4088
- **哈希**：b309d9babea9bd58e54f9c8597a8853379751c2b
- **短哈希**：b309d9bab
- **日期**：2023-12-12 09:21:52 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Support views for NessieCatalog (#8909)
- **PR/Issue**：#8909

## 总体目的

Iceberg 在 view（视图）规范上已经有了一段时间的演进，`BaseMetastoreViewCatalog` 抽象类为基于 metastore 的 Catalog 提供了视图的 CRUD 能力（list/drop/rename/load/buildView 等）。然而在此之前，`NessieCatalog` 仍继承自 `BaseMetastoreCatalog`，只支持 Iceberg 表的管理，无法在 Nessie 中创建、列举、加载或重命名视图。这导致 Nessie 用户在使用 Iceberg 视图能力时遇到了功能缺口。

本提交的目的就是为 `NessieCatalog` 增加 Iceberg 视图的完整支持，使其能够把视图作为一等公民与表一样地存储、版本化和管理。由于 Nessie 本身支持任意 `Content` 类型，并且已经引入了 `IcebergView` 这一内容类型，所以这次改动主要是把 Iceberg 视图 API 与 Nessie 的内容模型对接起来。

从动机上看，这次改动同时是 Iceberg View 规范在多 Catalog 上落地的一部分，使得 Nessie 在功能矩阵上与 Hive、JDBC、REST 等 Catalog 持平。改动还顺手把表和视图的大量重复逻辑（rename、drop、commit、异常处理）抽取到公共方法，把此前散落在 `NessieTableOperations` 中的异常转换逻辑迁移到 `NessieUtil`，以便表与视图共享，避免代码重复，这对后续维护具有重要意义。

## 如何达成设计目的

整体设计思路是：让 `NessieCatalog` 从 `BaseMetastoreCatalog` 改为继承 `BaseMetastoreViewCatalog`，并实现视图相关的抽象方法（`newViewOps`、`listViews`、`dropView`、`renameView`）。同时新增 `NessieViewOperations` 类，负责视图的 refresh / commit 逻辑，对标 `NessieTableOperations`。底层 `NessieIcebergClient` 中抽取了 `commitTable` 和 `commitView` 的公共部分到 `commitContent`，并把 `table`、`renameTable`、`dropTable` 等方法泛化为基于 `Content.Type` 的通用方法（`fetchContent`、`renameContent`、`dropContent`、`listContents`）。最后把表与视图共享的异常转换逻辑集中到 `NessieUtil`，新增 `handleExceptionsForCommits`、`handleBadRequestForCommit`、`maybeUseSpecializedException`、`contentTypeString`、`loadViewMetadata` 等工具方法。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java`

**修改目的**：让 `NessieCatalog` 支持 Iceberg 视图，与表统一管理。

**工作逻辑**：

- 把基类由 `BaseMetastoreCatalog` 改为 `BaseMetastoreViewCatalog`，从而获得 `ViewCatalog` 的所有抽象方法骨架；同时 import 了 `org.apache.iceberg.view.BaseMetastoreViewCatalog` 和 `org.apache.iceberg.view.ViewOperations`。
- 新增 `newViewOps(TableIdentifier)` 方法，构造 `NessieViewOperations`，参数与 `newTableOps` 对齐（构造 `ContentKey`、`client.withReference(...)`、`fileIO`）。这是 `BaseMetastoreViewCatalog` 要求实现的钩子。
- 实现 `listViews(Namespace)`：委托给 `client.listViews(namespace)`。
- 实现 `dropView(TableIdentifier)`：解析 `TableReference` 后委托给 `client.withReference(...).dropView(identifierWithoutTableReference(...), false)`。
- 实现 `renameView(TableIdentifier from, TableIdentifier to)`：调用新抽取的 `validateReferenceForRename(from, to, Content.Type.ICEBERG_VIEW)` 校验引用一致后，委托给 `client.renameView`。
- 重构 `renameTable`：把原来内联的"from/to reference 必须相同"校验替换为对新方法 `validateReferenceForRename(..., Content.Type.ICEBERG_TABLE)` 的调用，并保留 commit 流程。这样表与视图共用同一份校验逻辑，错误信息也统一为"Cannot rename table '...' on reference '...' to '...' on reference '...': source and target references must be the same."，便于排查。
- `newTableOps` 中去掉了不再使用的 `catalogOptions` 参数，由 `NessieTableOperations` 的构造方法对应简化。
- 新增私有方法 `validateReferenceForRename(TableReference from, TableReference to, Content.Type type)`：根据 content type 生成"table"/"view"字样，校验 from / to 解析出的引用名相同。错误信息中通过 `NessieUtil.contentTypeString(type).toLowerCase(Locale.ENGLISH)` 把类型转成可读字符串。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改目的**：把 client 中只针对表的方法泛化为表与视图通用，并新增视图提交能力。

**工作逻辑**：

- 把 `listTables(Namespace)` 拆为 `listTables`、`listViews` 两个公共入口，二者都委托给私有方法 `listContents(Namespace, Content.Type)`：用 `getEntries().stream().filter(namespacePredicate).filter(e -> type.equals(e.getType()))` 按 Nessie 内容类型过滤后转为 `TableIdentifier`。错误信息也用 `contentTypeString(type).toLowerCase(Locale.ENGLISH)` 拼"Unable to list tables/views due to missing ref"。
- 把 `table(TableIdentifier)` 重命名为 `fetchContent(TableIdentifier)`，并把 `IcebergTable` 返回类型改为 `IcebergContent`，使用 `content.unwrap(IcebergContent.class)`，这样同一个方法既能取表也能取视图，供 rename / drop 复用。
- 把 `renameTable(from, to)` 重构为 `renameTable` + `renameView` + 私有 `renameContent(from, to, Content.Type type)`：先 `getRef().checkMutable()`，再 `fetchContent` 拿到源/目的内容，调用新增的 `validateFromContentForRename` 和 `validateToContentForRename` 进行细化校验（如：源不存在则根据类型抛 `NoSuchViewException` 或 `NoSuchTableException`；目的已存在则抛带类型提示的 `AlreadyExistsException`）。最终通过 `commitRetry` 提交 `Operation.Delete(from) + Operation.Put(to, existingFromContent)`。对 `NessieConflictException` 还会调用 `NessieUtil.handleExceptionsForCommits` 把冲突细化成对应异常，使表/视图语义一致。
- 把 `dropTable(identifier, purge)` 拆成 `dropTable` + `dropView` + 私有 `dropContent(identifier, purge, Content.Type type)`：先 `checkMutable`，再 `fetchContent`，若为空直接返回 false；若内容类型与请求类型不匹配则抛 `RuntimeException`。提交时日志改为带类型信息（"Cannot drop {table|view}"），便于审计。
- `commitTable` 重构：原先一大段"获取 `UpdateableReference`、计算 `expectedHead`、构造 `ImmutableCommitMeta`、构造 `ImmutableIcebergTable`、调用 `commitMultipleOperations`"被拆分。`commitTable` 现在只负责构造 `ImmutableIcebergTable` 和 `CommitMeta`（含 `iceberg.operation` 属性），然后调用新方法 `commitContent(key, newTable, properties, commitMeta)` 完成提交。`commitContent` 是公共的提交逻辑：负责 `checkMutable`、从 properties 读取 `NESSIE_COMMIT_ID_PROPERTY` 计算 `expectedHead`、调用 `api.commitMultipleOperations().operation(Operation.Put.of(key, newContent)).commitMeta(commitMeta).branch(expectedHead).commit()`。
- 新增 `commitView(ViewMetadata base, ViewMetadata metadata, String newMetadataLocation, String contentId, ContentKey key)`：构造 `ImmutableIcebergView`（设置 id、versionId、schemaId、metadataLocation；sqlText 和 dialect 用占位符 "-"，因为 Nessie 只需 metadataLocation，其他信息从视图元数据文件解析）。然后构造 `CommitMeta`，把 `metadata.currentVersion().operation()` 放到 `iceberg.operation` 属性中，最后调用 `commitContent`。这与表的提交流程对齐。
- 新增 `buildCommitMsg(ViewMetadata base, ViewMetadata metadata, String viewName)`：根据 `metadata.currentVersion().operation()` 生成提交日志，例如"Iceberg view created with name xxx" / "Iceberg schema change against view xxx for the operation xxx"。原 `buildCommitMsg`（表）的消息也细化，加了"table"字样以区分。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieTableOperations.java`

**修改目的**：精简表操作类，把异常处理下沉到 `NessieUtil`，去除不再需要的字段。

**工作逻辑**：

- 去除 `catalogOptions` 字段和构造参数（该参数早已不在此类使用），构造方法只剩 `(ContentKey, NessieIcebergClient, FileIO)`。
- `doRefresh` 中把"Iceberg content 类型不是 IcebergTable"的 `IllegalStateException` 改为抛 `NessieContentNotFoundException(key, reference.getName())`，由调用栈上层翻译，更贴近 Nessie 语义。
- `doCommit` 中原先的 `catch (NessieConflictException | HttpClientException | NessieNotFoundException)` 三段处理被合并为：捕获 `NessieConflictException | NessieNotFoundException | HttpClientException`，先标记 failure（仅对前两类），然后调用 `NessieUtil.handleExceptionsForCommits(ex, client.refName(), Content.Type.ICEBERG_TABLE)`，若返回非空异常则抛出。另外新增 `catch (NessieBadRequestException ex)` 分支，调用 `NessieUtil.handleBadRequestForCommit(client, key, Content.Type.ICEBERG_TABLE)`：这个方法会用 client 再去查询同一 key 上是否存在另一种类型（视图）的内容，从而把"视图与表同名冲突"翻译成 `AlreadyExistsException`，让用户得到清晰的错误。
- 删除了原 `maybeThrowSpecializedException(NessieReferenceConflictException)` 私有方法——其逻辑被搬到 `NessieUtil.maybeUseSpecializedException` 并扩展为可区分表/视图。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieUtil.java`

**修改目的**：集中表/视图共享的工具方法，提供视图元数据加载和异常翻译。

**工作逻辑**：

- 新增 `loadViewMetadata(ViewMetadata metadata, String metadataLocation, Reference reference)`：在视图元数据 properties 中写入 `NESSIE_COMMIT_ID_PROPERTY = reference.getHash()`，并通过 `ViewMetadata.buildFrom(...).setMetadataLocation(...).build()` 重建 `ViewMetadata`，使 refresh 后的元数据携带 Nessie commit id，以便后续 commit 计算 `expectedHead`。这与表 refresh 时把 commit id 写入 properties 的做法一致。
- 新增 `handleExceptionsForCommits(Exception exception, String refName, Content.Type type)`：把 commit 阶段的 `NessieConflictException`、`NessieNotFoundException`、`HttpClientException` 翻译为 Iceberg 异常。对 `NessieReferenceConflictException` 还会先调用 `maybeUseSpecializedException` 尝试得到带类型语义的细粒度异常；否则返回 `CommitFailedException`（ref hash 过期）。`HttpClientException` 翻译为 `CommitStateUnknownException`，对应网络错误下提交状态未知的情况。
- 新增 `handleBadRequestForCommit(NessieIcebergClient client, ContentKey key, Content.Type type)`：用于处理 `NessieBadRequestException`。它反查同一 key 上的内容类型，如果与"另一种"类型（表↔视图）相同则抛 `AlreadyExistsException`，从而把"表/视图同名冲突"翻译为友好错误。
- 新增 `maybeUseSpecializedException(NessieReferenceConflictException ex, Content.Type type)`：原 `NessieTableOperations.maybeThrowSpecializedException` 的迁移增强版。基于 `extractSingleConflict` 提取唯一冲突，根据冲突类型映射到对应异常。其中 `KEY_DOES_NOT_EXIST` 会区分 `ICEBERG_VIEW`（抛 `NoSuchViewException`）和 `ICEBERG_TABLE`（抛 `NoSuchTableException`），`KEY_EXISTS` 则抛带类型提示的 `AlreadyExistsException`。
- 新增 `contentTypeString(Content.Type type)`：把 `ICEBERG_VIEW`/`ICEBERG_TABLE`/`NAMESPACE` 映射为字符串"View"/"Table"/"Namespace"，供日志、错误消息使用。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieViewOperations.java`（新文件）

**修改目的**：为视图提供 refresh / commit 实现，对标 `NessieTableOperations`。

**工作逻辑**：

- 继承 `BaseViewOperations`，持有 `NessieIcebergClient`、`ContentKey`、`FileIO` 与 `IcebergView`。
- `doRefresh()`：先 `client.refresh()`；调用 `client.getApi().getContent().key(key).reference(reference).get().get(key)`，若内容为空则视情况抛 `NoSuchViewException`；否则 `unwrap(IcebergView.class)`（失败时抛 `NessieContentNotFoundException`），取 `metadataLocation`，最终通过 `refreshFromMetadataLocation(...)` 把解析任务委托给 `NessieUtil.loadViewMetadata(ViewMetadataParser.read(io().newInputFile(location)), location, reference)`，在加载的同时把 Nessie commit id 注入到 properties。
- `doCommit(ViewMetadata base, ViewMetadata metadata)`：调用 `writeNewMetadataIfRequired(metadata)` 写新元数据文件，然后 `client.commitView(base, metadata, newMetadataLocation, contentId, key)`。异常处理与 `NessieTableOperations.doCommit` 完全对称：捕获 `NessieConflictException | NessieNotFoundException | HttpClientException` 后调 `NessieUtil.handleExceptionsForCommits(..., Content.Type.ICEBERG_VIEW)`；额外 `catch (NessieBadRequestException)` 调 `handleBadRequestForCommit`。失败时删除新写的元数据文件以避免泄漏。
- `viewName()` 返回 `key.toString()`，`io()` 返回 `fileIO`。

### `nessie/src/main/java/org/apache/iceberg/nessie/UpdateableReference.java`

**修改目的**：把 `checkMutable` 错误信息中的"tables"改为"tables/views"，反映视图现在也走相同的可变性约束。

### `nessie/src/test/java/org/apache/iceberg/nessie/BaseTestIceberg.java`

**修改目的**：为视图测试提供基础设施。

**工作逻辑**：

- 在 `BaseTestIceberg` 中新增 `createView(NessieCatalog, TableIdentifier)` 与 `createView(NessieCatalog, TableIdentifier, Schema)`：自动 `createMissingNamespaces`，然后 `nessieCatalog.buildView(...).withSchema(...).withDefaultNamespace(...).withQuery("spark", "select * from ns.tbl").create()`。
- 新增 `replaceView(NessieCatalog, TableIdentifier)`：用新 schema 调用 `buildView(...).replace()`，用于多分支视图元数据变更测试。
- 新增静态方法 `viewMetadataLocation(NessieCatalog, TableIdentifier)`：通过 `((BaseView) catalog.loadView(identifier)).operations().current().metadataFileLocation()` 取当前视图元数据文件路径。
- 把原来在 `TestNessieTable` 里的 `metadataVersionFiles` / `filterByExtension` / `metadataFiles` 等工具方法上移到 `BaseTestIceberg`，方便表与视图测试共用。
- `createTable` 中 `Schema` 构造简化为 `new Schema(required(1, "id", LongType.get()))`。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieView.java`（新文件）

**修改目的**：覆盖 Nessie 视图的核心行为，重点验证跨分支元数据可见性语义。

**工作逻辑**：

- 继承 `BaseTestIceberg`，在 `beforeEach` 中通过 `createView` 初始化一个视图并记录其 location。
- `verifyStateMovesForDML`：在主分支连续两次 `replaceVersion` 修改视图，并在另一个分支上观察。断言主分支上的 `IcebergView.metadataLocation` 会随版本变更而改变，而未参与提交的分支上的 `metadataLocation` 保持不变；同时验证 `versionId` 与 SQL 方言按预期演进。这覆盖了 Nessie "global-contents"模型与 Iceberg 视图元数据文件版本的语义。
- 其他用例覆盖视图的 schema 变更、`replaceVersion` 在不同分支上的独立性等。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieViewCatalog.java`（新文件）

**修改目的**：让 Nessie 视图接入 Iceberg 标准的 `ViewCatalogTests` 套件，统一验证视图 Catalog 行为。

**工作逻辑**：

- 继承 `ViewCatalogTests<NessieCatalog>`，使用 `NessieJaxRsExtension` + `InmemoryBackendTestFactory` 启动内嵌 Nessie，`@NessieApiVersions` 让所有 API 版本都跑一遍。
- `setUp` 中初始化 `NessieCatalog` 并记录默认分支初始 hash；`afterEach` 复位。
- 同时提供对 v1/v2 API 的覆盖，配合标准 `ViewCatalogTests` 的所有用例（list / drop / rename / alter schema / replace / properties 等）来验证 `NessieCatalog` 实现是否符合 Iceberg View 规范。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestBranchVisibility.java`

**修改目的**：补充视图在多分支下的可见性测试。

**工作逻辑**：

- 新增 `testViewMetadataLocation`：在 branch1 上 `replaceView`，然后在 branch2 上再次 `replaceView`，断言两个分支上视图的 `metadataFileLocation` 不同；切回 branch1 后加载视图，其元数据位置仍保持 branch1 的提交结果，没有被 branch2 的提交影响。
- 新增 `testDifferentViewSameName`：在 branch1 和 branch2 上分别用不同 schema 创建同名视图，断言两个分支上视图的 schema 与 location 互不影响，验证了 Nessie 在分支隔离下视图与表一致的语义。
- 把原"mutate tables"的断言消息更新为"mutate tables/views"，与 `UpdateableReference.checkMutable` 的新消息对齐。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieTable.java`

**修改目的**：跟随主代码调整测试与断言信息。

**工作逻辑**：

- 把原 import 自 `TableMetadataParser.getFileExtension` 等工具迁移到 `BaseTestIceberg` 后删除本地引用。
- `ImmutableTableReference` 类型改为接口 `TableReference`，符合新代码风格。
- rename 相关两条断言的期望错误信息更新为新版"Cannot rename table '...' on reference '...' to '...' on reference '...': source and target references must be the same."。
- 把"mutate tables"的断言更新为"mutate tables/views"。

## 小结

本提交让 `NessieCatalog` 从仅支持表的 Catalog 升级为同时支持 Iceberg 视图的 Catalog，并把表与视图共享的 rename / drop / commit / 异常翻译逻辑下沉到 `NessieIcebergClient` 与 `NessieUtil`，新增 `NessieViewOperations` 对标表操作，使 Nessie 在多 Catalog 的视图能力矩阵中补齐了关键一环，也为后续 Iceberg View 规范的统一推进奠定了基础。
