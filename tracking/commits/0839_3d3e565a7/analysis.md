# 提交 0839：Core: Remove deprecated APIs scheduled for removal in 1.6.0 (#10501)

## 提交信息
- **序号**：0839 / 4088
- **哈希**：3d3e565a793280f2925114ada9d853ea304d6b9b
- **短哈希**：3d3e565a7
- **日期**：2024-06-17
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core: Remove deprecated APIs scheduled for removal in 1.6.0 (#10501)
- **PR/Issue**：#10501

## 总体目的

本提交属于 Iceberg 的「弃用清理」类工作：在 1.6.0 版本正式发布前，把所有在 1.5.x（及更早）阶段被标注为 `@Deprecated`、并显式声明“将在 1.6.0 移除”的公共 API 真正删除掉。Iceberg 项目遵循“先弃用、后删除”的演进策略：先在一个版本里给 API 加上 `@Deprecated` 注解并写明计划移除的版本（例如 `since 1.5.0, will be removed in 1.6.0`），等到目标版本发布时再统一清理，以此给下游用户一个完整的迁移周期。

按时清理已弃用 API 有几个重要作用：
1. **保持 API 表面干净**：避免 `@Deprecated` 注解长期堆积，让真正的弃用信号保持有效性（如果一直不删，开发者会逐渐忽视弃用警告）。
2. **降低维护成本**：删除不再推荐使用的代码路径后，维护者不必再为兼容老 API 维护额外分支。
3. **配合二进制兼容性检查**：Iceberg 使用 Palantir Revapi 做 API 兼容性校验，删除弃用 API 会触发“破坏性变更（break）”告警，需要在 `.palantir/revapi.yml` 中显式登记并说明理由。

本次清理涉及四类弃用 API：
- `DataFiles.Builder.withEqualityFieldIds(List<Integer>)`（数据文件不应设置 equality field ids）
- `ReachableFileUtil.statisticsFilesLocations(Table, Predicate<StatisticsFile>)`（被 `statisticsFilesLocationsForSnapshots` 取代）
- `PlaintextEncryptionManager` 的 public 无参构造函数（被静态 `instance()` 取代）
- `OAuth2Util.AuthSession` 的 5 参构造函数（被 `AuthConfig` 入参版本取代）
- `NessieIcebergClient.commitTable(...IcebergTable expectedContent...)`（被 `contentId` 入参版本取代）
- `SparkTableUtil.loadCatalogMetadataTable(SparkSession, Table, MetadataTableType)`（自 0.14.0 起被 `loadMetadataTable` 取代，遗留多年）

## 如何达成设计目的

提交采用“源代码删除 + 测试侧适配 + Revapi 登记”三步走的方式：

1. **源代码删除**：直接删除被 `@Deprecated` 标注的方法或构造函数及其 Javadoc。这些方法在被删除前的内部实现大多只是转发到新的推荐方法（例如 `loadCatalogMetadataTable` 直接调用 `loadMetadataTable`，旧 `AuthSession` 构造函数内部组装 `AuthConfig` 再调用新构造函数），因此删除后调用方必须改用新 API，行为不会变化。
2. **测试侧适配**：本提交同时把内部测试中还在使用 `new PlaintextEncryptionManager()` 的地方改为 `PlaintextEncryptionManager.instance()`。由于构造函数被改为 `private`，这些调用如果不改会编译失败。Flink 1.17/1.18/1.19 三个版本的 `ReaderUtil`、`TestIcebergSourceReader`、`TestRowDataReaderFunction` 都做了同样改动，体现了 Iceberg 多版本 Flink 模块的“同一改动跨版本同步”惯例。
3. **`PlaintextEncryptionManager` 构造函数改为 `private`**：原本是 `@Deprecated public` 构造函数，本提交改成 `private` 构造函数，配合静态 `instance()` 实现单例。这既是清理弃用、也是把“无意义的可实例化”收敛为单例模式。
4. **Revapi 白名单登记**：在 `.palantir/revapi.yml` 中新增 6 条 `acceptedBreaks` 条目，把上述删除/可见性变更登记为“已接受的破坏性变更”，理由统一写为 `Deprecations for 1.6.0 release`。这样 Revapi 在 CI 中比较新旧 jar 时就不会因为这些已计划移除的变更而失败。

## 修改详情

### `.palantir/revapi.yml`
**修改目的**：登记本次因清理弃用 API 而产生的破坏性变更，让 Revapi 兼容性检查接受这些变更。

**工作逻辑**：在 `acceptedBreaks` 下的 `apache-iceberg-1.5.0` 版本段中新增针对 `org.apache.iceberg:iceberg-core` 的多条记录：
- `java.method.visibilityReduced`：`PlaintextEncryptionManager::<init>()` 由 public 变 private
- `java.element.noLongerDeprecated`：`PlaintextEncryptionManager::<init>()` 不再被标记为 deprecated（因为已变 private）
- `java.element.noLongerDeprecated` + `java.method.numberOfParametersChanged`：`OAuth2Util.AuthSession::<init>` 的 5 参版本被删除（Revapi 误报为“参数数量变化”和“不再弃用”，作者在 justification 中说明这其实是删除弃用构造函数，Revapi 报告有误）
- `java.method.removed`：`ReachableFileUtil::statisticsFilesLocations(Table, Predicate)`
- `java.method.removed`：`DataFiles.Builder::withEqualityFieldIds(List<Integer>)`

每条都带 `justification: "Deprecations for 1.6.0 release"`（或类似说明）。

### `core/src/main/java/org/apache/iceberg/DataFiles.java`
**修改目的**：删除 `DataFiles.Builder.withEqualityFieldIds(List<Integer>)` 这个被弃用的方法。

**工作逻辑**：删除了如下方法（及其 `@Deprecated` 注解和 Javadoc）：
```java
/** @deprecated since 1.5.0, will be removed in 1.6.0; must not be set for data files. */
@Deprecated
public Builder withEqualityFieldIds(List<Integer> equalityIds) {
  throw new UnsupportedOperationException("Equality field IDs must not be set for data files");
}
```
该方法本身就只是抛出 `UnsupportedOperationException`，存在意义只是为旧调用方在编译期给出弃用警告。删除后，任何仍在调用它的下游代码会编译失败，迫使其移除该调用——这是正确的，因为数据文件（data files）不应该设置 equality field ids（equality field ids 只对 position/range deletes 等删除文件有意义）。

### `core/src/main/java/org/apache/iceberg/ReachableFileUtil.java`
**修改目的**：删除被弃用的 `statisticsFilesLocations(Table, Predicate<StatisticsFile>)` 方法，并清理随之不再使用的 `Collectors` import。

**工作逻辑**：删除了如下方法：
```java
@Deprecated
public static List<String> statisticsFilesLocations(
    Table table, Predicate<StatisticsFile> predicate) {
  return table.statisticsFiles().stream()
      .filter(predicate)
      .map(StatisticsFile::path)
      .collect(Collectors.toList());
}
```
该方法的功能（按谓词过滤后收集 statistics 文件路径）已由 `statisticsFilesLocationsForSnapshots(table, snapshotIds)` 提供更精确的按快照 ID 过滤能力取代。同时移除 `import java.util.stream.Collectors;`，因为这是该方法唯一使用 `Collectors.toList()` 的地方。

### `core/src/main/java/org/apache/iceberg/encryption/PlaintextEncryptionManager.java`
**修改目的**：把 `PlaintextEncryptionManager` 的 public 无参构造函数改为 `private`，强制使用 `instance()` 静态工厂。

**工作逻辑**：
```java
// 旧
/** @deprecated will be removed in 1.6.0. use {@link #instance()} instead. */
@Deprecated
public PlaintextEncryptionManager() {}

// 新
private PlaintextEncryptionManager() {}
```
类中已存在 `private static final EncryptionManager INSTANCE = new PlaintextEncryptionManager();` 和 `public static EncryptionManager instance()`，所以原本的 public 构造函数是多余且违背单例意图的——它允许外部 new 出多个实例。改成 private 后，外部只能通过 `instance()` 拿到唯一的静态实例。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`
**修改目的**：删除 `AuthSession` 的 5 参数弃用构造函数。

**工作逻辑**：删除了如下构造函数：
```java
/** @deprecated since 1.5.0, will be removed in 1.6.0 */
@Deprecated
public AuthSession(
    Map<String, String> baseHeaders,
    String token,
    String tokenType,
    String credential,
    String scope) {
  this(
      baseHeaders,
      AuthConfig.builder()
          .token(token)
          .tokenType(tokenType)
          .credential(credential)
          .scope(scope)
          .build());
}
```
该构造函数只是把 5 个散落的字符串参数组装成 `AuthConfig` 再委托给 `AuthSession(Map, AuthConfig)`。删除后调用方必须自行构造 `AuthConfig`（推荐使用其 builder），这能让配置组合更清晰，也避免散落参数带来的调用歧义。

注意：文件中另一个 6 参数（多一个 `oauth2ServerUri`）的 `AuthSession` 构造函数仍然保留，因为它标注的是 `since 1.6.0, will be removed in 1.7.0`——本次提交只清理 1.6.0 计划移除的 API，不动 1.7.0 计划移除的。这体现了弃用清理的“按版本节奏推进”原则。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`
**修改目的**：删除 `commitTable(TableMetadata, TableMetadata, String, IcebergTable, ContentKey)` 这个被弃用的重载。

**工作逻辑**：删除了如下方法：
```java
/** @deprecated will be removed after 1.5.0 */
@Deprecated
public void commitTable(
    TableMetadata base,
    TableMetadata metadata,
    String newMetadataLocation,
    IcebergTable expectedContent,
    ContentKey key)
    throws NessieConflictException, NessieNotFoundException {
  String contentId = expectedContent == null ? null : expectedContent.getId();
  commitTable(base, metadata, newMetadataLocation, contentId, key);
}
```
旧重载接收整个 `IcebergTable expectedContent`，但内部只用到它的 `getId()`，因此新签名直接收 `String contentId`，更精简、耦合更低。删除后调用方必须直接传 `contentId`。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`
**修改目的**：删除自 0.14.0 起被弃用的 `loadCatalogMetadataTable(SparkSession, Table, MetadataTableType)`。

**工作逻辑**：删除了如下方法：
```java
/**
 * Loads a metadata table.
 *
 * @deprecated since 0.14.0, will be removed in 0.15.0; use {@link
 *     #loadMetadataTable(SparkSession, Table, MetadataTableType)}.
 */
@Deprecated
public static Dataset<Row> loadCatalogMetadataTable(
    SparkSession spark, Table table, MetadataTableType type) {
  return loadMetadataTable(spark, table, type);
}
```
这是一个“遗留多年”的弃用（自 0.14.0 起标注，本应在 0.15.0 移除，但一直未清理），本次提交借 1.6.0 清理窗口一并删除。方法体只是转发到 `loadMetadataTable`，删除后调用方直接使用 `loadMetadataTable`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/{ReaderUtil,TestIcebergSourceReader,TestRowDataReaderFunction}.java`
（v1.18、v1.19 三个版本的对应文件改动完全一致）

**修改目的**：把测试代码中 `new PlaintextEncryptionManager()` 改为 `PlaintextEncryptionManager.instance()`，以适配构造函数变 private 的改动。

**工作逻辑**：每处改动都是单行替换：
```java
// 旧
new PlaintextEncryptionManager(),
// 新
PlaintextEncryptionManager.instance(),
```
这些测试原本在构造 `IcebergSourceReader` / `RowDataReaderFunction` 时直接 new 一个 `PlaintextEncryptionManager` 作为加密管理器。改成 `instance()` 后拿到的是同一个单例实例，行为完全等价（PlaintextEncryptionManager 本身就是无状态的明文实现）。

## 小结
- **成效**：按计划清理 1.5.x 阶段标注“将在 1.6.0 移除”的弃用 API，共删除 6 处公共方法/构造函数、把 1 处构造函数改为 private、同步修改 9 个 Flink 测试文件、在 Revapi 配置中登记 6 条已接受破坏性变更，使 1.6.0 的 API 表面更干净，弃用信号更可信。
- **影响范围**：属于 **二进制不兼容** 的破坏性变更（方法被删除、构造函数可见性降低），但对源码兼容性影响有限——只要下游已经按弃用警告迁移到新 API，源码无需改动。受影响的公共 API 位于 `iceberg-core`（`DataFiles`、`ReachableFileUtil`、`PlaintextEncryptionManager`、`OAuth2Util`）、`iceberg-nessie`（`NessieIcebergClient`）、`iceberg-spark` v3.3（`SparkTableUtil`）。下游若仍使用这些 API，编译会失败。
- **回迁注意事项**：本提交是 **1.6.0 才计划的破坏性变更**，**不应回迁到 1.4.x**（1.4.x 作为已发布分支需保持二进制兼容，否则会破坏下游用户）。如果出于某种原因必须回迁相关改动，需注意：
  1. 删除 `DataFiles.Builder.withEqualityFieldIds`、`ReachableFileUtil.statisticsFilesLocations(Table, Predicate)`、`OAuth2Util.AuthSession` 5 参构造、`NessieIcebergClient.commitTable(...IcebergTable...)`、`SparkTableUtil.loadCatalogMetadataTable` 都是公共 API 删除，会在 1.4.x 上构成 **不兼容变更**，违反 1.4.x 的语义化版本承诺。
  2. `PlaintextEncryptionManager` 构造函数从 public 变 private 同样是二进制不兼容。
  3. Flink 测试侧的 `new PlaintextEncryptionManager()` → `instance()` 改动可以单独回迁（只动测试，不破坏公共 API），但意义不大。
  4. Revapi 的 `acceptedBreaks` 登记是为 1.5.0→1.6.0 比较准备的，回迁到 1.4.x 没有对应版本段可加，加了也不会被比较。
  5. 综上，建议 **整体不回迁** 本提交到 1.4.x；如需在 1.4.x 上做类似清理，应等到 1.4.x 自身的下一个 minor 版本并按其弃用节奏推进。
