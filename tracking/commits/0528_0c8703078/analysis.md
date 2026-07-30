# 提交 0528：Core: Only test if view exists when using SchemaVersion.V1 during table rename

## 提交信息

- **序号**：0528 / 4088
- **哈希**：0c8703078443a3c73a5aa5a6bd1cf904e0b5ce09
- **短哈希**：0c8703078
- **日期**：2024-02-21 16:57:42 +0100
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Core: Only test if view exists when using SchemaVersion.V1 during table rename
- **PR/Issue**：#9770

## 总体目的

本提交是 commit 0526（将 JDBC Catalog 默认 schema 改为 V0）的**配套修复**。0526 将默认 schema 版本从 V1 改为 V0 后，`JdbcCatalog` 中两处对 `viewExists()` 的无条件调用变成了 bug：在 V0 schema（默认）下，重命名表或用 `replaceTransaction()` 替换表时会触发 `viewExists()`，进而调用 `loadView()`，而 `loadView()` 在 V0 schema 下会直接抛出 `UnsupportedOperationException(VIEW_WARNING_LOG_MESSAGE)`。结果就是：**默认配置下，重命名表 / 替换表会失败**。

本提交的目的是把这两处 `viewExists()` 调用改为**仅在 `schemaVersion == V1` 时执行**，从而让 V0 schema 下的表重命名与表替换恢复正常工作，同时保留 V1 schema 下的“同名视图冲突检测”能力。

### 背景：0526 引入的回归

- 0526 之前：默认 `schemaVersion = V1`，所有 view 操作路径都能正常工作，`viewExists()` 不会抛错。
- 0526 之后：默认 `schemaVersion = V0`。`JdbcCatalog.loadView()`、`dropView()`、`listViews()`、`renameView()` 都已有 `if (schemaVersion != V1) throw UnsupportedOperationException(...)` 守卫，但**`renameTable()` 中对 `viewExists(to)` 的调用、以及父类 `BaseMetastoreViewCatalogTableBuilder.replaceTransaction()` 中对 `viewExists(identifier)` 的调用并没有做 V1 守卫**。这两处会无脑调用 `viewExists()` → `loadView()` → 抛 `UnsupportedOperationException`，导致 V0 默认配置下重命名/替换表失败。

## 如何达成设计目的

整体设计思路是**为 `viewExists()` 调用添加 `schemaVersion == V1` 前置条件**，并**在 `JdbcCatalog` 层覆盖 `buildTable()` 返回一个 schema 版本感知的 TableBuilder**，把 V0 时的 view 检测短路掉。

### 关键设计

1. **`renameTable()` 中的条件化 view 检测**：把 `if (viewExists(to))` 改为 `if (schemaVersion == JdbcUtil.SchemaVersion.V1 && viewExists(to))`。这样 V0 schema 下直接跳过 view 检测，不会触发 `loadView()` 的 `UnsupportedOperationException`。

2. **新增 `ViewAwareTableBuilder` 内部类**：覆盖 `buildTable(TableIdentifier, Schema)` 返回新的 `ViewAwareTableBuilder`，它的 `replaceTransaction()` 在调用 `super.replaceTransaction()` 前先判断 `schemaVersion == V1 && viewExists(identifier)`，仅当 V1 schema 下才检测同名 view 冲突。这个新类取代了父类 `BaseMetastoreViewCatalog.BaseMetastoreViewCatalogTableBuilder` 的位置——后者是无条件检测 view 的，对 V0 schema 不适用。

### 为什么需要在 JdbcCatalog 层覆盖 buildTable？

`JdbcCatalog` 继承自 `BaseMetastoreViewCatalog`，父类已经在 `buildTable()` 中返回 `BaseMetastoreViewCatalogTableBuilder`，其 `replaceTransaction()` 无条件调用 `viewExists(identifier)`。父类无法感知 JdbcCatalog 的 `schemaVersion` 字段（这是 JDBC 特有的概念），所以子类必须自己覆盖 `buildTable()` 提供一个 schema 版本感知的 TableBuilder。这就是 `ViewAwareTableBuilder` 存在的原因——它的注释明确写道：*“The purpose of this class is to add view detection only when SchemaVersion.V1 schema is used when replacing a table.”*

### viewExists 调用链路（V0 时为何会失败）

- `viewExists(identifier)` 是 `ViewCatalog` 接口的默认方法：调用 `loadView(identifier)`，捕获 `NoSuchViewException` 返回 false。
- `JdbcCatalog.loadView()` 在第 253 行有守卫：`if (schemaVersion != V1) throw UnsupportedOperationException(VIEW_WARNING_LOG_MESSAGE)`。
- 因此 V0 schema 下，`viewExists()` 会抛 `UnsupportedOperationException` 而不是返回 false。这就是 renameTable / replaceTransaction 在 V0 下失败的根因。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**修改目的**：让 V0 schema 下表重命名与表替换不再调用 `viewExists()`，修复 0526 引入的回归；同时保留 V1 schema 下的同名 view 冲突检测。

**工作逻辑**：

1. **新增 import**：`org.apache.iceberg.Schema` 与 `org.apache.iceberg.Transaction`，为新覆盖的 `buildTable` 与 `ViewAwareTableBuilder` 提供类型。

2. **`renameTable()` 加 V1 守卫**：
   - 在方法上添加 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`，因为方法内分支数增加（实际上 checkstyle 阈值告警）。
   - 将 `if (viewExists(to)) { throw new AlreadyExistsException("Cannot rename %s to %s. View already exists", from, to); }` 改为 `if (schemaVersion == JdbcUtil.SchemaVersion.V1 && viewExists(to)) { ... }`。短路求值保证 V0 时不会调用 `viewExists()`。

3. **新增 `buildTable()` 覆盖**：
   ```java
   @Override
   public TableBuilder buildTable(TableIdentifier identifier, Schema schema) {
     return new ViewAwareTableBuilder(identifier, schema);
   }
   ```
   这取代了父类 `BaseMetastoreViewCatalog.buildTable()` 返回的 `BaseMetastoreViewCatalogTableBuilder`。

4. **新增内部类 `ViewAwareTableBuilder`**：
   ```java
   protected class ViewAwareTableBuilder extends BaseMetastoreCatalogTableBuilder {
     private final TableIdentifier identifier;
     public ViewAwareTableBuilder(TableIdentifier identifier, Schema schema) {
       super(identifier, schema);
       this.identifier = identifier;
     }
     @Override
     public Transaction replaceTransaction() {
       if (schemaVersion == JdbcUtil.SchemaVersion.V1 && viewExists(identifier)) {
         throw new AlreadyExistsException("View with same name already exists: %s", identifier);
       }
       return super.replaceTransaction();
     }
   }
   ```
   - 继承自 `BaseMetastoreCatalogTableBuilder`（注意：不是 `BaseMetastoreViewCatalogTableBuilder`，避免重复 view 检测），持有 `identifier` 字段。
   - 仅在 `replaceTransaction()` 中加 view 检测，且检测前先判断 `schemaVersion == V1`。其他表构建行为（create、createTransaction 等）走父类默认实现，与 view 无关。
   - 注释明确该类的目的：在替换表时仅当 V1 schema 才做 view 检测。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：把 `TestJdbcCatalog`（默认 V0 schema 测试类）中两处显式设置 V1 schema 的代码删除，让其回归默认 V0 schema 行为，从而覆盖 V0 下的表操作（包括 rename/replace）。

**工作逻辑**：

- 在两个 `@BeforeEach`/初始化方法中删除 `properties.put(JdbcUtil.SCHEMA_VERSION_PROPERTY, JdbcUtil.SchemaVersion.V1.name());` 这一行。这使 `TestJdbcCatalog` 默认用 V0 schema 跑 `CatalogTests` 抽象测试基类的所有用例（包括 `renameTable`、`replaceTable` 等），验证本提交的修复在 V0 下工作正常。这是本提交的核心回归测试——若不修复，这些用例会因 `viewExists()` 抛 `UnsupportedOperationException` 而失败。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalogWithV1Schema.java`（新增）

**修改目的**：新增一个独立测试类，专门用 V1 schema 跑 `CatalogTests` 基类，确保 V1 schema 下的同名 view 冲突检测仍然工作（即 0528 的修复没有破坏 V1 行为）。

**工作逻辑**：

- 继承 `CatalogTests<JdbcCatalog>`，与 `TestJdbcCatalog` 平行，但 `@BeforeEach setupCatalog()` 中显式设置 `properties.put(JdbcUtil.SCHEMA_VERSION_PROPERTY, JdbcUtil.SchemaVersion.V1.name());`。
- 使用 `jdbc:sqlite:file::memory:?ic<UUID>` 作为 JDBC URI，每个测试方法用唯一 UUID 隔离内存数据库（避免不同测试间状态污染）。
- 由于 V1 schema 下 catalog 支持视图，`CatalogTests` 基类中所有“表与视图同名冲突”相关用例都能被该测试类覆盖验证。
- 这样新的测试矩阵是：`TestJdbcCatalog`（V0）+ `TestJdbcCatalogWithV1Schema`（V1），双 schema 版本都有 `CatalogTests` 全套覆盖。

## 小结

- **成效**：本提交修复了 0526 引入的回归——V0 默认 schema 下重命名表 / 替换表会因 `viewExists()` 抛 `UnsupportedOperationException` 而失败。通过把 `viewExists()` 调用条件化为 `schemaVersion == V1`，V0 schema 下表操作恢复正常；V1 schema 下保留同名 view 冲突检测。新增 `TestJdbcCatalogWithV1Schema` 与改造后的 `TestJdbcCatalog` 形成 V0/V1 双版本测试矩阵，分别覆盖两种 schema 的全套 CatalogTests。
- **影响范围**：仅影响 `JdbcCatalog` 的 `renameTable()` 与表替换路径（通过 `ViewAwareTableBuilder`）。V1 schema 用户无行为变化；V0 schema 用户从此能正常 rename/replace 表。
- **回迁到 1.4.x 的注意事项**：
  1. 本提交**强依赖 0526**：必须先回迁 0526（默认 V0 + `jdbc.schema-version` 属性重命名），否则 `schemaVersion == V1` 守卫没有意义（0526 前 `schemaVersion` 默认就是 V1，且没有 V0/V1 概念分支）。建议作为一个整体回迁 0526+0528。
  2. 1.4.x 若已存在 V0 schema 用户（例如 1.4.x 之前用 `jdbc.add-view-support=false` 显式回退 V0 的用户），回迁后他们的 rename/replace 表操作将不再失败。
  3. 注意 `ViewAwareTableBuilder` 继承的是 `BaseMetastoreCatalogTableBuilder`（不是 `BaseMetastoreViewCatalogTableBuilder`），避免与父类的 view 检测重复——回迁时要确保 `BaseMetastoreViewCatalogTableBuilder` 在父类中已存在（否则 `super` 链路变化），1.4.x 应已具备。
  4. 测试侧：1.4.x 需同步引入 `TestJdbcCatalogWithV1Schema.java` 并修改 `TestJdbcCatalog` 默认 V0 配置，否则 CI 中 `CatalogTests` 的 rename/replace 用例会在 V0 下挂掉。
