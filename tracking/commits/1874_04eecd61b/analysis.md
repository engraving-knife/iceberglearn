# 提交 1874：Core: JDBCCatalog's dropView() should purge metadata files if GC is enabled (#12511)

## 提交信息

- **序号**：1874 / 4088
- **哈希**：04eecd61beccc7fac3964326afc5e6ee95849ea4
- **短哈希**：04eecd61b
- **日期**：2025-03-18 20:55:00 -0600
- **作者**：hsiang-c
- **提交说明**：Core: JDBCCatalog's dropView() should purge metadata files if GC is enabled (#12511)
- **PR/Issue**：#12511

## 总体目的

本提交修复 JdbcCatalog 的 `dropView()` 方法在删除视图时不会清理底层 metadata 文件的问题。此前，`dropView()` 只是从 JDBC catalog 表中删除视图记录，但视图的 metadata 文件（位于存储层）会被遗留，造成存储泄漏。

Iceberg 对表（table）已有 GC（garbage collection）机制：当 `gc-enabled` 属性为 true 时，删除表会清理其 metadata 文件。但视图（view）的 `dropView` 路径在 JdbcCatalog 中未实现这一逻辑，导致行为不一致。

本提交使 JdbcCatalog 的 `dropView()` 在 GC 启用时，调用 `CatalogUtil.dropViewMetadata` 清理视图的 metadata 文件，与表删除行为对齐。同时，在删除前先加载视图的当前 metadata（若加载失败则跳过清理但不阻止删除），保证即使 metadata 不可达也能完成 catalog 记录的删除。

## 如何达成设计目的

整体设计思路：

1. **删除前加载 metadata**：在 `dropView()` 执行 SQL 删除前，先通过 `newViewOps(identifier)` 获取 `JdbcViewOperations`，调用 `current()` 加载当前 `ViewMetadata`。若抛出 `NotFoundException`（metadata 文件已不存在），则记录警告但不阻止后续删除流程，`lastViewMetadata` 保持 null。

2. **执行 JDBC 记录删除**：执行 `DROP_VIEW_SQL` 删除 catalog 表中的视图记录。若删除记录数为 0，返回 false（视图不存在）。

3. **条件性清理 metadata 文件**：JDBC 记录删除成功后，若 `lastViewMetadata` 不为 null，调用 `CatalogUtil.dropViewMetadata(ops.io(), lastViewMetadata)` 清理底层 metadata 文件。`CatalogUtil.dropViewMetadata` 内部会检查 GC 是否启用，仅在启用时执行删除。

4. **测试覆盖**：新增两个测试用例，分别验证 GC 禁用时 metadata 文件保留、GC 启用时 metadata 文件被删除。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (修改, +13/-0 lines)

**修改目的**：在 `dropView()` 中增加 metadata 文件清理逻辑。

**工作逻辑**：
- 导入 `org.apache.iceberg.view.ViewMetadata`。
- 在 `dropView()` 方法中，先校验后，获取 `JdbcViewOperations ops = (JdbcViewOperations) newViewOps(identifier);`，声明 `ViewMetadata lastViewMetadata = null;`。
- try-catch 调用 `lastViewMetadata = ops.current();`，捕获 `NotFoundException` 并记录警告日志。
- 执行 `DROP_VIEW_SQL` 删除 JDBC 记录，若 `deletedRecords == 0` 返回 false。
- 若 `lastViewMetadata != null`，调用 `CatalogUtil.dropViewMetadata(ops.io(), lastViewMetadata)` 清理 metadata 文件。
- 记录日志并返回 true。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java` (修改, +49/-0 lines)

**修改目的**：验证 dropView 在 GC 启用/禁用时的 metadata 文件清理行为。

**工作逻辑**：
- 新增 `dropViewShouldNotDropMetadataFileIfGcNotEnabled` 测试：创建视图时设置 `gc-enabled=false`，获取 metadata 文件路径，执行 `dropView`，断言 metadata 文件仍存在且视图已不存在。
- 新增 `dropViewShouldDropMetadataFileIfGcEnabled` 测试：创建视图时设置 `gc-enabled=true`，获取 metadata 文件路径，执行 `dropView`，断言 metadata 文件已不存在且视图已不存在。
- 两个测试都通过 `BaseView` 获取 `view.operations().current().metadataFileLocation()` 来定位 metadata 文件。

## 总结

本提交补齐了 JdbcCatalog 的 `dropView()` 缺失的 metadata 文件清理逻辑，使其在 GC 启用时调用 `CatalogUtil.dropViewMetadata` 清理底层文件，与表删除行为一致。删除前先尝试加载 metadata（容错处理），删除后条件性清理。新增两个测试覆盖 GC 禁用/启用两种场景。
