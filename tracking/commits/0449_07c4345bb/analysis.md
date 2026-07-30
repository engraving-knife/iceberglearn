# 提交 0449：Spark: Fix CREATE OR REPLACE VIEW when view doesn't exist (#9621)

## 提交信息

- **序号**：0449
- **完整哈希**：07c4345bbbd4deefe722bbdfe627f26998b3bccf
- **短哈希**：07c4345bb
- **日期**：2024-02-02 17:14:42 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Fix CREATE OR REPLACE VIEW when view doesn't exist (#9621)
- **关联 PR**：#9621

## 总体目的

本提交修复一个具体的功能缺陷：在 Spark 中执行 `CREATE OR REPLACE VIEW` 时，如果目标视图尚不存在（即用户想用 `OR REPLACE` 语义"建一个新视图"），旧实现会抛出"视图不存在"的错误，导致语句失败。这与 SQL 标准语义不符——`CREATE OR REPLACE` 的本意是"不存在则创建、存在则替换"，两种情况都应该成功。

根因在于 `SparkCatalog.replaceView`（v3.5）在构建视图时调用的是 `ViewBuilder.replace()`，而 `replace()` 严格要求视图已存在，否则抛 `NoSuchViewException`。正确的调用应该是 `ViewBuilder.createOrReplace()`，它会在视图不存在时走 create 路径、存在时走 replace 路径，恰好匹配 `CREATE OR REPLACE` 的语义。本提交把这一处 `.replace()` 改为 `.createOrReplace()`，并新增一个回归测试覆盖"视图不存在时执行 CREATE OR REPLACE"的场景。

值得注意的是，本提交只改了 Spark 3.5 目录。Spark 3.4 目录中的 `SparkCatalog.replaceView`（由前一个提交 0447 引入）同样调用 `.replace()`，因此 3.4 也存在同样的缺陷，但未在本提交中一并修复——这是一个值得后续跟进的不对称点（可能是 3.4 视为维护期版本，或修复待后续提交）。

## 如何达成设计目的

修复路径非常聚焦：把 `SparkCatalog.replaceView` 方法结尾的 `ViewBuilder` 终端操作从 `.replace()` 改为 `.createOrReplace()`。其余链式调用（`buildView` → `withDefaultCatalog` → `withDefaultNamespace` → `withQuery` → `withSchema` → `withLocation` → `withProperties`）完全不变。`createOrReplace()` 是 Iceberg `ViewBuilder` 接口提供的两个终端操作之一（另一个是 `create()`/`replace()`），它的语义正是"不存在则创建、存在则替换"，与 `CREATE OR REPLACE VIEW` 一一对应。测试侧新增 `createOrReplaceView()` 用例：先 `insertRows(6)` 插入 6 行数据，然后在视图不存在的情况下直接 `CREATE OR REPLACE VIEW ... AS SELECT id FROM <table> WHERE id <= 3`，断言返回 3 行 (1,2,3)；再 `CREATE OR REPLACE` 同一视图改为 `WHERE id > 3`，断言返回 3 行 (4,5,6)。第二个语句验证"已存在则替换"路径，第一个语句正是修复前会失败的场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`
**修改目的**：修复 `replaceView` 在视图不存在时抛错的缺陷。
**工作逻辑**：`replaceView` 方法中，`ViewBuilder` 链式构建完成后，终端操作由 `.replace()` 改为 `.createOrReplace()`：
```java
org.apache.iceberg.view.View view =
    asViewCatalog
        .buildView(buildIdentifier(ident))
        .withDefaultCatalog(currentCatalog)
        .withDefaultNamespace(Namespace.of(currentNamespace))
        .withQuery("spark", sql)
        .withSchema(icebergSchema)
        .withLocation(properties.get("location"))
        .withProperties(props)
        .createOrReplace();   // 原为 .replace()
return new SparkView(catalogName, view);
```
`.replace()` 要求视图已存在，否则抛 `NoSuchViewException`；`.createOrReplace()` 则根据视图是否存在自动选择 create 或 replace。`createView` 方法（用于 `CREATE VIEW`，非 replace 场景）仍调用 `.create()`，未改动。catch 块对 `NoSuchNamespaceException` 的转换保持不变。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`
**修改目的**：新增回归测试，覆盖"视图不存在时执行 CREATE OR REPLACE VIEW"的修复场景，并顺带验证"已存在时替换"路径。
**工作逻辑**：新增 `createOrReplaceView()` 测试方法（`@Test`，注意此处仍用 JUnit 4 的 `@Test`，因为 `TestViews` 此时仍继承 `SparkExtensionsTestBase` 而非新的 `ExtensionsTestBase`，JUnit 5 迁移尚未波及此类）。流程：
1. `insertRows(6)`：向基础表插入 6 行（id 1..6）。
2. 第一次 `CREATE OR REPLACE VIEW <viewName> AS SELECT id FROM <table> WHERE id <= 3`：此时视图不存在，触发修复点。断言查询视图返回 3 行，且恰好包含 (1,2,3)（用 `containsExactlyInAnyOrder`）。
3. 第二次 `CREATE OR REPLACE VIEW <viewName> AS SELECT id FROM <table> WHERE id > 3`：此时视图已存在，走 replace 路径。断言返回 3 行 (4,5,6)。
4. `viewName("simpleView")` 生成带前缀的唯一视图名，避免与其他用例冲突。

## 小结

本提交是一个精准的单点修复：把 v3.5 `SparkCatalog.replaceView` 的 `ViewBuilder` 终端操作从 `.replace()` 改为 `.createOrReplace()`，使 `CREATE OR REPLACE VIEW` 在目标视图不存在时不再抛 `NoSuchViewException`，而是按 SQL 语义创建新视图。配套新增的回归测试同时覆盖了"不存在则创建"与"存在则替换"两条路径。需要注意的对称性问题：v3.4 目录的 `replaceView`（提交 0447 引入）仍调用 `.replace()`，存在相同缺陷但未在本提交修复，后续需单独跟进。
