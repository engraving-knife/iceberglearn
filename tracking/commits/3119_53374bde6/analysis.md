# 提交 3119：API, Core: Support registerView for view catalog (#14868)

## 提交信息

- **序号**：3119 / 4088
- **哈希**：53374bde66955885de6fca0ea59b946346ec2d89
- **短哈希**：53374bde6
- **日期**：2026-01-16 15:21:34 +0100
- **作者**：Ajantha Bhat
- **提交说明**：API, Core: Support registerView for view catalog (#14868)
- **PR/Issue**：#14868

## 总体目的

Iceberg 的表目录（TableCatalog）早已支持 `registerTable`——即把一个已存在的元数据文件位置登记到 catalog，使其能像正常表一样被加载，常用于元数据恢复、跨 catalog 迁移、备份恢复等场景。然而视图目录（ViewCatalog）此前缺少对应的 `registerView` 能力：一旦一个视图的 catalog 条目被删除（即使元数据文件因 GC 关闭仍存在），或需要把外部元数据文件登记进来，都无法通过 catalog API 重新注册视图。

本提交为视图目录补齐这一能力，新增 `registerView(TableIdentifier, metadataFileLocation)` 接口方法及在 `BaseMetastoreViewCatalog` 中的通用实现。其语义与 `registerTable` 对称：若视图或同名表已存在则抛 `AlreadyExistsException`，否则读取指定元数据文件并提交为一个新视图。这为视图的元数据恢复与跨环境登记提供了基础，也与表侧能力对齐。注意本提交暂未支持 REST catalog（测试中以 `assumeThat(...).isNotInstanceOf(RESTCatalog.class)` 跳过），REST 端点将在后续提交单独实现。

## 如何达成设计目的

设计分三步：(1) 在 `ViewCatalog` 接口新增 `default registerView` 方法，默认抛 `UnsupportedOperationException`，保证向后兼容且明确告知未实现；(2) 在 `BaseMetastoreViewCatalog` 提供通用实现——校验标识符与元数据位置、检查视图/同名表不存在、读取元数据文件后 `ops.commit(null, metadata)` 完成注册；(3) 为 `ViewMetadataParser` 增加一个接收 `FileIO` 与路径的 `read` 重载，方便从路径直接读取，并将 `BaseViewOperations`、`NessieViewOperations` 中重复的 `io().newInputFile(location)` 调用统一替换为该重载。最后在 `ViewCatalogTests` 增加三个测试覆盖正常注册、视图已存在、同名表已存在三种场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/ViewCatalog.java` (+13/-0 lines)

**修改目的**：在视图目录接口声明 `registerView` 默认方法。

**工作逻辑**：
新增 `default View registerView(TableIdentifier identifier, String metadataFileLocation)`，默认实现 `throw new UnsupportedOperationException("Registering views is not supported")`。Javadoc 说明：若同名表/视图已存在则抛 `AlreadyExistsException`。用 `default` 方法保证现有实现不被破坏，未支持的实现直接抛异常。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java` (+24/-0 lines)

**修改目的**：为基于 metastore 的视图目录提供通用 `registerView` 实现。

**工作逻辑**：
`@Override registerView` 中先用 `Preconditions.checkArgument` 校验 `identifier` 有效且 `metadataFileLocation` 非空；若 `viewExists(identifier)` 为真抛 `AlreadyExistsException("View already exists: %s")`；若 `tableExists(identifier)` 为真抛 `AlreadyExistsException("Table with same name already exists: %s")`，避免视图与表命名冲突。随后 `ViewOperations ops = newViewOps(identifier)`，`ViewMetadata metadata = ViewMetadataParser.read(((BaseViewOperations) ops).io(), metadataFileLocation)` 读取元数据，`ops.commit(null, metadata)` 以"无 base"方式提交（即首次注册）。最后 `return new BaseView(ops, ViewUtil.fullViewName(name(), identifier))` 返回视图对象。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java` (+5/-0 lines)

**修改目的**：新增基于 `FileIO` 与路径的 `read` 重载，消除重复样板。

**工作逻辑**：
新增 `public static ViewMetadata read(FileIO io, String path) { return read(io.newInputFile(path)); }`，委托给既有的 `read(InputFile)`。这使得调用方只需传 `FileIO` 与路径字符串即可读取视图元数据，无需显式构造 `InputFile`。

### `core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java` (+1/-1 lines)

**修改目的**：复用新的 `read(FileIO, String)` 重载简化代码。

**工作逻辑**：
`refreshFromMetadataLocation` 内 lambda 由 `metadataLocation -> ViewMetadataParser.read(io().newInputFile(metadataLocation))` 改为 `metadataLocation -> ViewMetadataParser.read(io(), metadataLocation)`，逻辑等价但更简洁。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieViewOperations.java` (+1/-1 lines)

**修改目的**：同样复用新重载。

**工作逻辑**：
`refreshFromMetadataLocation` 的 lambda 由 `ViewMetadataParser.read(io().newInputFile(location))` 改为 `ViewMetadataParser.read(io(), location)`，逻辑等价。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+144/-0 lines)

**修改目的**：覆盖 `registerView` 的三种场景，验证语义正确性。

**工作逻辑**：
新增三个测试（均用 `assumeThat(catalog).isNotInstanceOf(RESTCatalog.class)` 跳过 REST catalog）：
- `registerView`：创建视图（设 `GC_ENABLED=false` 防止 drop 删除元数据文件），取其 `metadataFileLocation`，drop 视图，断言元数据文件仍存在，再 `registerView` 重新注册；验证注册后视图存在、name 正确、history 仅 1 条且 versionId=1、schema 与原视图一致、currentVersion 操作为 `create`、versions 与原视图一致、可正常 load 与 drop。
- `registerExistingView`：视图仍存在时调用 `registerView`，断言抛 `AlreadyExistsException` 且消息以 `"View already exists: ns.view"` 开头。
- `registerViewThatAlreadyExistsAsTable`：drop 视图后用同名标识创建表，再 `registerView`，断言抛 `AlreadyExistsException` 且消息以 `"Table with same name already exists: ns.view"` 开头，并清理表。

## 总结

本提交为 Iceberg 视图目录补齐了与表目录对齐的 `registerView` 能力，在接口层定义默认方法、在 `BaseMetastoreViewCatalog` 提供通用实现，并顺手统一了 `ViewMetadataParser` 的读取重载以简化多处样板。配合三个测试覆盖正常注册与冲突场景，为视图的元数据恢复、跨 catalog 登记等用例奠定基础；REST catalog 端的支持作为后续工作（见下一提交）。
