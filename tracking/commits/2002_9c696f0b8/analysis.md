# 提交 2002：Core: Improve CatalogTests

## 提交信息

- **序号**：2002 / 4088
- **哈希**：9c696f0b84f34edf6c026c9a734a33897520f0c9
- **短哈希**：9c696f0b8
- **日期**：2025-04-16 09:32:33 +0200
- **作者**：Talat UYARER
- **提交说明**：Core: Improve CatalogTests (#12768)
- **PR/Issue**：#12768

## 总体目的

本提交对 `CatalogTests` 测试基类和 `TestRESTCatalog` 测试类进行重构改进，主要目标是：
1. 消除测试方法中重复创建的局部 `TableIdentifier` 变量，统一使用共享常量
2. 将硬编码的表位置路径提取为可复用的辅助方法
3. 在 REST 目录测试中用 `RESOURCE_PATHS.table(TBL)` 替代硬编码的 REST 路径字符串，提高可维护性

`CatalogTests` 是 Iceberg 目录测试的核心抽象基类，所有目录实现（如 REST、Hive、JDBC、 Nessie 等）都继承它。通过将测试中反复使用的表标识符提取为常量 `TBL`，并将位置路径提取为 `baseTableLocation()` 方法，减少了代码重复，使测试更加一致和易于维护。

同时，将原 `TABLE` 常量的名称从 `"table"` 改为 `"newtable"`，避免与新的 `TBL` 常量（`TableIdentifier.of("ns", "tbl")`）可能产生的命名冲突。

## 如何达成设计目的

1. **新增共享常量**：添加 `TBL = TableIdentifier.of("ns", "tbl")` 作为测试中标准的表标识符，添加 `BASE_TABLE_LOCATION = "file:/tmp"` 作为基础路径。

2. **提取辅助方法**：添加 `baseTableLocation(TableIdentifier)` 方法，根据标识符动态生成表位置路径。

3. **统一替换**：将多个测试方法中局部创建的 `TableIdentifier ident = TableIdentifier.of("ns", "table")` 替换为共享常量 `TBL`，并将硬编码的 `"file:/tmp/ns/table"` 替换为 `baseTableLocation(TBL)`。

4. **REST 路径动态化**：在 `TestRESTCatalog` 中，将硬编码的 REST 路径 `"v1/namespaces/ns/tables/table"` 替换为 `RESOURCE_PATHS.table(TBL)`，使路径生成与实际标识符保持同步。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +120/-141 lines)

**修改目的**：重构测试基类，消除重复代码，统一表标识符和位置路径。

**工作逻辑**：
1. **新增常量**：
   ```java
   private static final String BASE_TABLE_LOCATION = "file:/tmp";
   protected static final TableIdentifier TBL = TableIdentifier.of("ns", "tbl");
   ```
   同时将原 `TABLE` 常量的表名从 `"table"` 改为 `"newtable"` 以避免冲突。

2. **新增辅助方法**：
   ```java
   protected String baseTableLocation(TableIdentifier identifier) {
       return BASE_TABLE_LOCATION + "/" + identifier.namespace() + "/" + identifier.name();
   }
   ```

3. **统一替换**：在 `testBasicCreateTable`、`testBasicCreateTableThatAlreadyExists`、`testCompleteCreateTable`、`testDefaultTableProperties`、`testDefaultTablePropertiesCreateTransaction`、`testDefaultTablePropertiesReplaceTransaction`、`testOverrideTableProperties`、`testOverrideTablePropertiesCreateTransaction`、`testOverrideTablePropertiesReplaceTransaction` 等多个测试方法中：
   - 移除局部 `TableIdentifier ident = TableIdentifier.of("ns", "table")` 声明
   - 将所有 `ident` 引用替换为 `TBL`
   - 将硬编码位置 `"file:/tmp/ns/table"` 替换为 `baseTableLocation(TBL)`
   - 更新错误消息中的表名（如 `"Table already exists: ns.table"` → `"ns.tbl"`）

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +67/-... lines)

**修改目的**：在 REST 目录测试中使用共享常量和动态路径，替代硬编码值。

**工作逻辑**：
1. 将多处 `TableIdentifier.of("ns", "table")` 替换为继承自父类的 `TBL` 常量
2. 将硬编码的 REST 路径 `"v1/namespaces/ns/tables/table"` 替换为 `RESOURCE_PATHS.table(TBL)`，确保路径生成逻辑与 REST 资源路径工具保持一致
3. 移除局部 `TableIdentifier ident` 变量声明
4. 涉及的测试方法包括多个 OAuth2/认证相关的测试和 token 交换测试

## 总结

本提交对 `CatalogTests` 和 `TestRESTCatalog` 进行了重构改进，通过提取共享常量 `TBL` 和辅助方法 `baseTableLocation()`，消除了多个测试方法中重复的表标识符创建和硬编码路径。在 REST 测试中使用 `RESOURCE_PATHS.table(TBL)` 替代硬编码路径，提高了可维护性。整体减少了约 21 行代码，使测试更加一致和简洁。
