# 提交 0275：Hive: Refactor TestHiveCatalog tests to use CatalogTests (#8918)

## 提交信息

- **序号**：0275 / 4088
- **哈希**：5487c17474f5ed2b507af369e3540fcd46aff9e2
- **短哈希**：5487c1747
- **日期**：2023-12-14 17:22:04 +0100
- **作者**：Naveen Kumar
- **提交说明**：Hive: Refactor TestHiveCatalog tests to use CatalogTests (#8918)
- **PR/Issue**：#8918

## 总体目的

本提交把 Hive 模块的 `TestHiveCatalog` 测试重构为继承统一的 `CatalogTests<HiveCatalog>` 抽象基类，使 Hive catalog 与其他 catalog（如 JDBC、REST、Hadoop 等）共享同一套标准的 catalog 行为测试用例。

在重构之前，`TestHiveCatalog` 是一个独立的测试类，自己手写了大量与 catalog 标准行为重复的测试用例：`testCreateNamespace`、`testListNamespace`、`testSetNamespaceProperties`、`testRemoveNamespaceProperties`、`testDropNamespace`、`testRegisterTable`、`testRegisterExistingTable` 等。这些用例本质上与 `CatalogTests` 中已抽象出来的同名测试重复，但由于 `TestHiveCatalog` 没有 extends `CatalogTests`，Hive 的 catalog 没有真正跑过那套统一测试，存在两个隐患：一是 Hive 特有的偏差（例如错误消息措辞、命名空间约束、rename 行为）不会被统一测试发现；二是 catalog 标准测试的新增用例不会自动覆盖到 Hive。

这次重构正是为消除这两类隐患而做的。让 `TestHiveCatalog extends CatalogTests<HiveCatalog>` 后，Hive catalog 自动获得 `CatalogTests` 中所有 `@Test` 方法（创建/列举/删除 namespace、创建/重命名/删除表、注册表、事务、并发提交冲突等）的覆盖；同时通过覆盖若干 `supportsXxx()` 钩子声明 Hive 的能力边界（不支持带斜杠/带点的名字、需要显式创建 namespace），让不适用 Hive 的用例被 `Assumptions.assumeTrue` 跳过。这一改动让 Hive catalog 的测试覆盖与项目其他 catalog 实现对齐，是测试治理与代码去重的常规动作。

为达成这一重构，本提交还顺手修了几处 Hive catalog 的实现细节，使其行为与 `CatalogTests` 期望的契约一致：
- 调整 `createNamespace` 抛出 `AlreadyExistsException` 时的消息格式，从 `Namespace '%s' already exists!` 改为 `Namespace already exists: %s`，与 `CatalogTests` 中 `assertThat(...).hasMessage(...)` 的断言匹配。
- 在 `defaultWarehouseLocation` 中新增对 `NoSuchObjectException` 的捕获并抛出 `NoSuchNamespaceException`，使 Hive 在 namespace 不存在时的行为符合 catalog 契约。
- 在 `HiveTableOperations.commit` 的 `CommitFailedException` 消息前加 "Cannot commit: " 前缀，使其语义更清晰并与统一测试期望对齐。

另外，本提交显式标注了一个 Hive 已知缺陷（issue #9289）：当 rename 目标表已存在时，Hive metastore 抛出的是 `RuntimeException` 而非 `AlreadyExistsException`。通过覆盖 `testRenameTableDestinationTableAlreadyExists` 并加 TODO 注释，把该缺陷暂时收纳进测试矩阵，待 #9289 修复后再回归到基类默认行为。

## 如何达成设计目的

整体设计思路是"让测试类继承统一基类 + 用钩子声明能力边界 + 修正实现使其符合契约"。具体地：在 `CatalogTests` 中新增 `supportsNamesWithDot()` 钩子，并在 `testNamespaceWithDot`/`testTableNameWithDot` 中用 `Assumptions.assumeTrue` 跳过不支持点号的 catalog；让 `TestHiveCatalog extends CatalogTests<HiveCatalog>`，覆盖 `catalog()`、`requiresNamespaceCreate()`、`supportsNamesWithSlashes()`、`supportsNamesWithDot()` 等钩子；删除与基类重复的本地测试方法，避免编译冲突；为每个测试方法提供干净的 metastore 状态（`@AfterEach cleanup()` 重置 metastore）；在 `build.gradle` 中为 hive-metastore 项目添加对 `iceberg-core` testArtifacts 的依赖，使 `CatalogTests` 可被引用。同时修正 Hive catalog 三处实现细节，使其行为契约与统一测试对齐。

## 修改详情

### `build.gradle`

**修改目的**：让 `iceberg-hive-metastore` 项目能引用 `iceberg-core` 的测试类（特别是 `CatalogTests`）。

**工作逻辑**：在 `:iceberg-hive-metastore` 项目的依赖块中新增一行：
```groovy
testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')
```
`testArtifacts` 是 Gradle 的一种配置，用于把模块的测试代码（如 `CatalogTests`、`TestHelpers` 等）暴露给其他模块作为测试依赖。原先 hive-metastore 已依赖 `:iceberg-api` 的 testArtifacts，本提交追加对 `:iceberg-core` testArtifacts 的依赖，使 `TestHiveCatalog` 可以 `extends CatalogTests<HiveCatalog>`。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：新增 `supportsNamesWithDot()` 能力钩子，让不支持点号名字的 catalog（如 Hive）可以跳过相关测试。

**工作逻辑**：
- 新增受保护方法 `supportsNamesWithDot()`，默认返回 `true`：
  ```java
  protected boolean supportsNamesWithDot() {
    return true;
  }
  ```
  这与既有的 `supportsNamesWithSlashes()` 钩子风格一致，由各 catalog 子类按需覆盖。
- 在 `testNamespaceWithDot` 与 `testTableNameWithDot` 两个测试方法开头各加一行 `Assumptions.assumeTrue(supportsNamesWithDot());`。当 catalog 不支持带点的名字时，JUnit 会把这两个测试标记为 `ASSUMPTION_FAILED`（即跳过）而非失败，避免对 Hive 这类把点号视为命名空间分隔符的 catalog 误报失败。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：让 Hive catalog 的 `createNamespace` 与 `defaultWarehouseLocation` 行为符合 `CatalogTests` 期望的契约。

**工作逻辑**：
1. `createNamespace` 中捕获 `AlreadyExistsException` 时抛出的错误消息，从：
   ```java
   throw new AlreadyExistsException(e, "Namespace '%s' already exists!", namespace);
   ```
   改为：
   ```java
   throw new AlreadyExistsException(e, "Namespace already exists: %s", namespace);
   ```
   `CatalogTests` 中相应断言使用 `assertThatThrownBy(...).hasMessage(String.format("Namespace already exists: %s", namespace))` 的格式，旧消息含引号且用 `'...'` 包裹，无法匹配。修改后 Hive catalog 与其他 catalog 的消息格式统一。

2. `defaultWarehouseLocation` 方法新增对 `NoSuchObjectException` 的捕获：
   ```java
   } catch (NoSuchObjectException e) {
     throw new NoSuchNamespaceException(
         e, "Namespace does not exist: %s", tableIdentifier.namespace().levels()[0]);
   }
   ```
   当计算默认仓库路径时若 Hive metastore 中查不到对应 database（namespace），原先会落入通用的 `TException` 分支抛 `RuntimeException`，与 catalog 契约不符。修改后抛出 `NoSuchNamespaceException`，与 `CatalogTests` 中"namespace 不存在时应抛 `NoSuchNamespaceException`"的期望一致。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：让 commit 冲突时的异常消息更清晰，与统一测试期望对齐。

**工作逻辑**：在 `commit` 方法中，当检测到 base metadata location 与当前 table metadata location 不一致时抛出的 `CommitFailedException` 消息前加 "Cannot commit: " 前缀：
```java
throw new CommitFailedException(
    "Cannot commit: Base metadata location '%s' is not same as the current table metadata location '%s' for %s.%s",
    baseMetadataLocation, metadataLocation, database, tableName);
```
原消息直接以 "Base metadata location..." 开头，修改后语义更明确，且与 `CatalogTests` 中可能的断言匹配。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`

**修改目的**：把 `TestHiveCatalog` 重构为继承 `CatalogTests<HiveCatalog>`，共享统一 catalog 测试套件，同时保留 Hive 特有的测试。

**工作逻辑**：
1. **类声明**：从 `public class TestHiveCatalog` 改为：
   ```java
   /**
    * Run all the tests from abstract of {@link CatalogTests} with few specific tests related to HIVE.
    */
   public class TestHiveCatalog extends CatalogTests<HiveCatalog> {
   ```
   继承后，`TestHiveCatalog` 自动获得 `CatalogTests` 中所有 `@Test` 方法（创建/列举/删除 namespace、创建/重命名/删除表、注册表、并发提交冲突等数十个用例）的覆盖。

2. **`catalog()` 方法**：新增 `@Override protected HiveCatalog catalog()`，返回当前 `catalog` 字段。这是 `CatalogTests` 调用 catalog 的入口。

3. **能力钩子覆盖**：
   - `requiresNamespaceCreate()` 返回 `true`：Hive 在创建表前必须先创建 database/namespace。
   - `supportsNamesWithSlashes()` 返回 `false`：Hive 不支持名字中含斜杠。
   - `supportsNamesWithDot()` 返回 `false`：Hive 把点号视为命名空间分隔符，不支持名字中含点号。

4. **`HiveMetastoreExtension` 构建方式调整**：去掉 `.withDatabase(DB_NAME)`，改为在 `before()` 中显式 `createDatabase`：
   ```java
   String dbPath = HIVE_METASTORE_EXTENSION.metastore().getDatabasePath(DB_NAME);
   Database db = new Database(DB_NAME, "description", dbPath, Maps.newHashMap());
   HIVE_METASTORE_EXTENSION.metastoreClient().createDatabase(db);
   ```
   这样每个测试运行前都有一个干净的 `DB_NAME` database，避免与 `CatalogTests` 中可能预先创建 namespace 的逻辑冲突。

5. **`@AfterEach cleanup()`**：新增：
   ```java
   @AfterEach
   public void cleanup() throws Exception {
     HIVE_METASTORE_EXTENSION.metastore().reset();
   }
   ```
   每个测试后重置 metastore 状态，保证测试隔离。这是继承统一基类后必须的，因为 `CatalogTests` 中的用例会大量创建/删除 namespace 与表。

6. **删除与基类重复的测试方法**：
   - `testListNamespace`（已被 `CatalogTests.testListNamespaces` 覆盖）
   - `testSetNamespaceProperties`（已被 `CatalogTests.testSetNamespaceProperties` 覆盖）
   - `testRemoveNamespaceProperties`（已被 `CatalogTests.testRemoveNamespaceProperties` 覆盖）
   - `testRegisterTable`、`testRegisterExistingTable`（已被 `CatalogTests` 中的 register 表测试覆盖）

7. **重命名以避免冲突**：
   - `testCreateNamespace` → `testDatabaseAndNamespaceWithLocation`：原方法测试的是 Hive 特有的 namespace location 行为，重命名后避免与 `CatalogTests.testCreateNamespace` 签名冲突。
   - `testDropNamespace` → `dropNamespace()`：同理，避免与基类同名测试冲突（注意：此处重命名似乎漏了 `@Test` 注解，且方法名不再以 `test` 开头，可能是疏忽，但功能保留）。

8. **`testRenameTableDestinationTableAlreadyExists` 覆盖**：用 `@Override` 覆盖基类同名测试，并标注 TODO：
   ```java
   // TODO: This test should be removed after fix of https://github.com/apache/iceberg/issues/9289.
   ```
   该覆盖版本针对 Hive 已知缺陷（issue #9289）：当 rename 目标表已存在时，Hive metastore 抛出的是 `RuntimeException`（消息含 "new table newdb.table_renamed already exists"）而非 `AlreadyExistsException`。测试断言改为期望 `RuntimeException` 并检查消息内容，同时验证源表与目标表在失败后仍然存在且 UUID 不同。待 #9289 修复后，应删除此覆盖让 Hive 回归基类默认的 `AlreadyExistsException` 期望。

## 小结

本提交把 Hive 模块的 `TestHiveCatalog` 重构为继承统一的 `CatalogTests<HiveCatalog>` 基类，使 Hive catalog 自动获得项目内所有 catalog 共享的标准行为测试覆盖，同时通过能力钩子声明 Hive 的边界（不支持点号/斜杠名字、需要显式创建 namespace）。为达成此重构，同步修正了 Hive catalog 三处实现细节（namespace 已存在的错误消息格式、namespace 不存在时抛 `NoSuchNamespaceException`、commit 冲突消息前缀），并显式收纳了一个已知 rename 缺陷（issue #9289）。这是 Hive catalog 测试治理的一次重要推进，让 Hive 与其他 catalog 实现在测试覆盖与行为契约上保持对齐。
