# 提交 0085：Core: Improvements around View catalog tests (#8865)

## 提交信息

- **序号**：0085 / 4088
- **哈希**：43fce1b56bc8364908941eee8a4b5a9ccca6c7fe
- **短哈希**：43fce1b56
- **日期**：2023-10-20
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Improvements around View catalog tests (#8865)
- **PR/Issue**：#8865

## 总体目的

这个提交系统性增强了 View Catalog 的测试套件，并修复了 [`InMemoryCatalog`](../../../../core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java) 中若干与视图/表并发提交和重命名相关的不一致行为。它属于 View 规范落地过程中的测试加固工作，确保抽象测试基类 [`ViewCatalogTests`](../../../../core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java) 能更严格、更真实地校验各 catalog 实现的行为。

**背景与动机**：此前的 View catalog 测试存在几个不足：

1. **使用硬编码的 `file://tmp/...` 临时路径**：测试中视图 location 用的是 `"file://tmp/ns/view"` 这类非真实临时目录的硬编码字符串，既无法验证 catalog 是否真的尊重了请求的 location，也容易在 catalog 实际覆盖 location 时产生误导。
2. **缺少对 `metadataFileLocation` 的校验**：测试没有断言视图操作（operations）的 `metadataFileLocation()` 非空，无法捕获元数据文件位置未被正确设置的问题。
3. **缺少"重命名表到已存在视图"场景**：已有测试覆盖了"重命名视图到已存在表"，但未覆盖反向场景"重命名表到已存在视图"，`InMemoryCatalog` 在该场景下也确实没有抛出 `AlreadyExistsException`。
4. **并发删除后的错误类型不精确**：当视图在被更新/重命名过程中被并发删除时，`InMemoryCatalog` 抛出的是 `CommitFailedException`（"Cannot commit"），但语义上更精确的应是 `NoSuchViewException`。测试此前断言 `CommitFailedException`，等于锁定了不精确的行为。

**引入的能力/修复**：(1) 测试改用 `@TempDir` 提供的真实临时路径作为视图 location，并新增 `overridesRequestedLocation()` 钩子，让覆盖 location 的 catalog 实现可声明该行为；(2) 多处新增 `metadataFileLocation()` 非空断言；(3) 新增 `renameTableTargetAlreadyExistsAsView` 测试，`InMemoryCatalog.renameTable` 同步增加视图存在性检查；(4) `InMemoryCatalog` 在表/视图 commit 时若 `existingLocation` 为 null（说明对象已被并发删除）改为抛 `NoSuchTableException`/`NoSuchViewException`，测试断言相应更新。这些改动让 View catalog 的契约更清晰，测试更接近真实使用场景。

## 如何达成设计目的

整体设计思路是"测试驱动 + 实现对齐"。先在抽象测试基类 [`ViewCatalogTests`](../../../../core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java) 中加强断言、引入真实临时路径与可覆盖钩子，并新增缺失场景测试；再在 [`InMemoryCatalog`](../../../../core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java)（作为参考实现）中补齐对应逻辑：重命名时检查目标是否已是视图、commit 时区分"并发修改"与"对象不存在"两种情况抛出更精确的异常。测试基类的改动会被所有 View catalog 实现（如 JDBC、REST、HiveCatalog 等）继承执行，从而统一行业标准。

## 修改详情

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java`

**修改目的**：补齐 `InMemoryCatalog` 在表/视图重命名与并发提交场景下的边界处理，使其行为与测试契约一致。

**工作逻辑**：三处修改：

1. **`renameTable` 增加视图存在性检查**：在原本检查目标 table 是否存在之后，新增 `if (views.containsKey(to)) { throw new AlreadyExistsException("Cannot rename %s to %s. View already exists", from, to); }`。这样重命名表到已存在视图标识符时会抛 `AlreadyExistsException`，与"重命名视图到已存在表"对称。

2. **表 commit 时区分对象不存在**：在 `TableLoader`/commit 逻辑中，当检测到并发修改且 `null == existingLocation` 时（说明表实际上已不存在），新增 `throw new NoSuchTableException("Table does not exist: %s", tableName());`，先于 `CommitFailedException` 抛出。语义更精确：表已删除不该算作"并发修改冲突"。

3. **视图 commit 时区分对象不存在**：对称地在视图 commit 逻辑中新增 `if (null == existingLocation) { throw new NoSuchViewException("View does not exist: %s", identifier); }`。这两处让"更新已被并发删除的视图/表"抛出 `NoSuchViewException`/`NoSuchTableException` 而非笼统的 `CommitFailedException`。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：加强 View catalog 测试的真实性、覆盖度与断言精度。

**工作逻辑**：分若干改动组：

- **引入真实临时目录与 location 钩子**：新增 `@TempDir private Path tempDir;` 字段及 `protected boolean overridesRequestedLocation() { return false; }` 钩子方法（catalog 实现可覆盖以声明它不尊重请求的 location）。新增 `import java.nio.file.Path/Paths;` 与 `import org.junit.jupiter.api.io.TempDir;`。

- **location 改用临时路径**：在 `basicCreateView`、`basicCreateAndReplaceView`（replace 场景）、`updateLocation` 等多个测试中，把硬编码的 `"file://tmp/ns/view"` 替换为 `Paths.get(tempDir.toUri().toString(), Paths.get("ns", "view").toString()).toString()`，并据此构造 `updatedLocation`。location 断言改为条件判断：`if (!overridesRequestedLocation()) { assertThat(view.location()).isEqualTo(location); } else { assertThat(view.location()).isNotNull(); }`。这让尊重 location 的 catalog 被精确校验，而覆盖 location 的 catalog 只校验非空。

- **新增 `metadataFileLocation` 非空断言**：在 `basicCreateView`、`createViewWithCustomLocation`、`replaceView`、`replaceViewVersion`、`renameView` 等测试中新增 `assertThat(((BaseView) view).operations().current().metadataFileLocation()).isNotNull();`，确保视图操作能正确暴露元数据文件位置。

- **新增 `renameTableTargetAlreadyExistsAsView` 测试**：构造一个表 `ns.table` 和一个视图 `ns.view`，断言 `tableCatalog().renameTable(tableIdentifier, viewIdentifier)` 抛 `AlreadyExistsException` 且消息含 `"Cannot rename ns.table to ns.view. View already exists"`。使用 `Assumptions.assumeThat(tableCatalog()).isNotNull()` 跳过不支持表的 catalog。

- **修正并发删除场景的异常断言**：在 `updateViewProperties`、`replaceViewVersion`（concurrent drop 场景）、`updateViewLocation` 等测试中，把原本 `assertThatThrownBy(...).isInstanceOf(CommitFailedException.class).hasMessageContaining("Cannot commit")` 改为 `.isInstanceOf(NoSuchViewException.class).hasMessageContaining("View does not exist: ns.view")`，与 `InMemoryCatalog` 的新行为对齐。

## 小结

这个提交通过引入真实临时路径、新增 metadataFileLocation 断言、补齐重命名与并发删除场景测试，并同步修正 InMemoryCatalog 的边界行为，显著加强了 View catalog 测试套件的严格性与真实场景覆盖度。
