# 提交 3112：Core, Hive: Detect if a view already exists when registering a table (#15010)

## 提交信息

- **序号**：3112 / 4088
- **哈希**：bd96b79b4229f27c0aa4a0ed776a1493499cfd79
- **短哈希**：bd96b79b4
- **日期**：2026-01-14
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Hive: Detect if a view already exists when registering a table (#15010)
- **PR/Issue**：#15010

## 总体目的

本提交修复 `HiveCatalog` 在注册表（`registerTable`）时未能检测同名视图已存在的缺陷。背景是：Iceberg 中表与视图共享同一命名空间，不能用同一个 identifier 既注册表又存在视图。对于所有支持视图的 catalog，冲突检测本来是在提交元数据的 `doCommit()` 中完成的——当目标 identifier 已存在为另一种实体（表 vs 视图）时抛出 `AlreadyExistsException`。但 `HiveCatalog` 是个例外：它在更早的 `doRefresh()` 阶段（`HiveTableOperations.java` 第 175-177 行）就做了表/视图类型判定，导致 `registerTable` 流程下，当 identifier 已存在为视图时，行为不符合预期（不会在注册入口就抛出"视图同名"的清晰错误）。

提交说明中作者权衡了两种方案：方案一是为所有 view catalog 覆写 `registerTable()` 加入早期视图检测，但这会牵连 `JDBCCatalog` 也要覆写以判断是否启用视图支持，改动面大；方案二（最终采用）是只在 `HiveCatalog` 中覆写 `registerTable()`，在真正提交元数据之前先检测 `tableExists` 与 `viewExists`，命中即抛 `AlreadyExistsException`。这样既补齐了 HiveCatalog 的缺口，又不影响其他已经通过 `doCommit()` 正确处理的 catalog。同时在 core 的 `ViewCatalogTests` 基类中新增一个通用测试，确保各 view catalog 实现（包括 HiveCatalog）都覆盖该场景。

## 如何达成设计目的

在 `HiveCatalog` 中新增 `registerTable(TableIdentifier, String)` 覆写：先做参数校验，再依次检查 `tableExists`（抛"Table already exists"）与 `viewExists`（抛"View with same name already exists"），最后委托 `super.registerTable(...)`。同时借此机会整理了 `HiveCatalog` 中 `AlreadyExistsException` 的引用——改为统一 import Iceberg 自带的 `org.apache.iceberg.exceptions.AlreadyExistsException`，把原本内联的完整类名简化为短名；并修正了 `createNamespace` 中 `catch` 子句原本捕获的是 Hive metastore 的 `AlreadyExistsException`（因原先 import 的是 Hive 的那个），现在 import 切换后改用完整类名 `org.apache.hadoop.hive.metastore.api.AlreadyExistsException` 显式捕获 Hive 端异常。测试侧在 `ViewCatalogTests` 基类新增 `registerTableThatAlreadyExistsAsView`，构造"建表→保留元数据删表→同名建视图→注册表应抛 AlreadyExistsException"的链路。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+43/-18 lines)

**修改目的**：覆写 `registerTable` 加入同名视图检测，并整理 `AlreadyExistsException` 的 import 引用。

**工作逻辑**：
- **import 调整**：移除 Hive metastore 的 `org.apache.hadoop.hive.metastore.api.AlreadyExistsException` import，新增 Iceberg 的 `org.apache.iceberg.exceptions.AlreadyExistsException` import。这一切换使文件中大量原先写为 `org.apache.iceberg.exceptions.AlreadyExistsException`（完整类名）的抛出语句简化为短名 `AlreadyExistsException`，涉及 `renameTable`（表/视图已存在）、`renameTable` 的 `InvalidOperationException` 分支、`createOrReplaceTransaction`/`create`（表与视图构建器中互相检测）等多处，纯简化无行为变化。
- **`createNamespace` 的 catch 修正**：原代码 `catch (AlreadyExistsException e)` 因当时 import 的是 Hive 的 `AlreadyExistsException`，实际捕获的是 Hive metastore 抛出的同名异常；import 切换后此处改为 `catch (org.apache.hadoop.hive.metastore.api.AlreadyExistsException e)` 显式完整类名，确保仍然捕获 Hive 端的命名空间已存在异常并转译为 Iceberg 的 `AlreadyExistsException`。这是一个易错点，作者通过显式完整类名保证了语义不变。
- **新增 `registerTable` 覆写**：先 `Preconditions.checkArgument` 校验 identifier 有效与 metadataFileLocation 非空；随后 `if (tableExists(identifier)) throw new AlreadyExistsException("Table already exists: %s", identifier);` 与 `if (viewExists(identifier)) throw new AlreadyExistsException("View with same name already exists: %s", identifier);`，最后 `return super.registerTable(identifier, metadataFileLocation);`。这样在提交元数据之前就把表/视图同名冲突拦截掉，给出清晰错误信息，补齐了 HiveCatalog 相对其他 catalog 的缺口。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+38/-0 lines)

**修改目的**：为所有 view catalog 实现新增"注册表时检测同名视图已存在"的通用测试。

**工作逻辑**：新增 `registerTableThatAlreadyExistsAsView` 测试。流程为：用 `tableCatalog().createTable(identifier, SCHEMA)` 建表；通过 `((BaseTable) table).operations().current().metadataFileLocation()` 拿到元数据文件位置；`dropTable(identifier, false)`（不清理元数据）使表不存在但元数据仍可用；用 `catalog().buildView(identifier)...create()` 以同名创建视图并断言 `viewExists` 为真；然后 `assertThatThrownBy(() -> tableCatalog().registerTable(identifier, metadataLocation)).isInstanceOf(AlreadyExistsException.class).hasMessageStartingWith("View with same name already exists: %s", identifier)`，验证注册表会因同名视图而失败；最后断言表仍不存在并清理视图。该测试位于抽象基类，所有继承的 catalog（包括 HiveCatalog）都会执行，确保行为一致。

## 总结

本提交针对 `HiveCatalog` 在 `registerTable` 时不能及时检测同名视图已存在的问题，通过在 `HiveCatalog` 中覆写 `registerTable`、在提交前显式检查表/视图同名并抛出 `AlreadyExistsException` 予以修复，同时整理了 `AlreadyExistsException` 的 import 并谨慎修正了 `createNamespace` 的 catch 类型以维持原语义。配合在 `ViewCatalogTests` 基类新增的通用测试，保证各 view catalog 实现都覆盖该冲突检测场景，提升了表/视图命名空间隔离的健壮性与错误信息的清晰度。
