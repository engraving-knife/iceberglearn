# 提交 1044：Core: Remove deprecated APIs for 1.7.0 (#10818)

## 提交信息

- **序号**：1044 / 4088
- **哈希**：d17a7f189afa25c6be37df1415f4e2f8594effbe
- **短哈希**：d17a7f189
- **日期**：2024-08-09（Fri Aug 9 11:34:36 2024 +0200）
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Core: Remove deprecated APIs for 1.7.0 (#10818)
- **PR/Issue**：#10818

## 总体目的

Iceberg 在 1.6.0 版本中清理过一波 deprecated API，并在那时新增了一批标注 `@Deprecated since 1.6.0, will be removed in 1.7.0` 的方法/类，给下游一个版本的迁移窗口。本提交是 1.7.0 发布前的例行清理：把这些"在 1.6.0 标记、承诺在 1.7.0 移除"的 API 真正删除掉，并相应调整被它们影响的内部测试。

被删除的 API 分布在 `iceberg-common`、`iceberg-core`、`hive-metastore` 多个模块，包括：

- `DynConstructors` 的 `getConstructedClass()`、`Builder.hiddenImpl(Class<?>...)`；
- `DynFields.Builder.buildStaticChecked()`；
- `DynMethods.Builder.ctorImpl(...)` 两个重载、`DynMethods.UnboundMethod.invokeChecked` 的 public 可见性（降为包级）；
- `BaseMetastoreTableOperations.CommitStatus` 枚举（迁移到 `BaseMetastoreOperations.CommitStatus`）；
- `FileScanTaskParser.toJson(FileScanTask)` 与 `fromJson(String, boolean)`（迁移到 `ScanTaskParser`）；
- `ContentCache` 的 `get`、`getIfPresent`、`tryCache(FileIO, String, long)` 三个方法及内部 `CacheEntry` 占位类；
- `SnapshotProducer.newManifestOutput()`（迁移到 `newManifestOutputFile()`）；
- `OAuth2Util.AuthSession` 的旧多参数构造器（迁移到 `AuthConfig` 构造方式）；
- `HiveOperationsBase.storageDescriptor(TableMetadata, boolean)`（迁移到 `storageDescriptor(Schema, String, boolean)`）。

这些 API 都已有替代方案，且在 1.6.0 起就标注了弃用与替代方法，删除是为了保持 API 表面整洁并避免后续维护负担。

## 如何达成设计目的

整体策略是"按承诺删除 + 显式记录破坏性变更 + 同步调整测试"：

1. **删除已弃用 API**：直接移除被 `@Deprecated` 标注、且明确承诺在 1.7.0 移除的方法/构造器/枚举/内部类。
2. **可见性调整而非删除**：对 `DynMethods.UnboundMethod.invokeChecked` 这类仍需内部使用但不应对外暴露的 API，把可见性从 `public` 降为包级（package-private），并在 revapi 中记录 visibility reduced 的破坏性变更；同时给该方法的 NOOP 重实现加 `@Deprecated since 1.7.0, will become package-private`，为 1.8.0 进一步收敛留窗口。
3. **revapi 显式登记破坏**：在 `.palantir/revapi.yml` 新增 `1.6.0` 段落，把本次每个被删除/可见性变更的 API 列为 accepted breaks，给出 `Removing deprecated code` 的统一 justification，让二进制兼容性检查不再把这些当作未声明的破坏。
4. **测试同步**：删除测试中针对已移除 API 的用例（如 `TestFileScanTaskParser` 中针对 `FileScanTaskParser.toJson/fromJson` 的旧用例），保留对替代 API（`ScanTaskParser`）的覆盖。

## 修改详情

### `.palantir/revapi.yml`

**修改目的**：把本次 API 删除/可见性变更登记为 1.6.0 → 1.7.0 之间"已接受的破坏性变更"，避免 revapi 兼容性检查报错。

**工作逻辑**：在 `acceptedBreaks` 下新增 `"1.6.0"` 段，逐条列出本次的破坏项，每条包含 `code`（如 `java.method.removed`、`java.class.removed`、`java.method.visibilityReduced`、`java.method.returnTypeChanged`）、`old`（被移除/变更的旧签名）、`justification: "Removing deprecated code"`。涵盖 `iceberg-common`（DynConstructors、DynFields、DynMethods 共 5 项）、`iceberg-core`（BaseMetastoreTableOperations.CommitStatus 枚举、FileScanTaskParser 两个方法、ContentCache 三个方法、SnapshotProducer.newManifestOutput 在 4 个子类上的映射、OAuth2Util.AuthSession 构造器、checkCommitStatus 返回类型变更）。

### `common/src/main/java/org/apache/iceberg/common/DynConstructors.java`

**修改目的**：移除 1.6.0 标记弃用的 API，并对仍保留但将逐步收敛的 API 加新弃用标注。

**工作逻辑**：删除 `Ctor.getConstructedClass()`（`@Deprecated since 1.6.0`）和 `Builder.hiddenImpl(Class<?>...)`（与 varargs 版冲突，弃用）；同时把 `UnboundMethod.invokeChecked` 的 `@Deprecated since 1.6.0` 标注改为新的 `@Deprecated since 1.7.0, visibility will be reduced in 1.8.0`（对应下方可见性变更）。

### `common/src/main/java/org/apache/iceberg/common/DynFields.java`

**修改目的**：移除 1.6.0 标记弃用的 `Builder.buildStaticChecked()`。

**工作逻辑**：删除整个 `buildStaticChecked()` 方法及其 Javadoc（该方法只是 `buildChecked().asStatic()` 的快捷方式，调用方可直接用 `buildChecked().asStatic()` 或 `buildStatic()` 替代）。

### `common/src/main/java/org/apache/iceberg/common/DynMethods.java`

**修改目的**：移除弃用的 `ctorImpl` 重载，并把 `invokeChecked` 可见性从 public 降为包级。

**工作逻辑**：

1. `UnboundMethod.invokeChecked` 从 `public <R> R invokeChecked(...)` 改为 `<R> R invokeChecked(...)`（包级），并移除原 `@Deprecated since 1.6.0, will become private` 标注。这是 `visibilityReduced` 破坏性变更。
2. 删除 `Builder.ctorImpl(Class<?> targetClass, Class<?>... argClasses)` 与 `Builder.ctorImpl(String className, Class<?>... argClasses)` 两个 `@Deprecated since 1.6.0` 方法，调用方应改用 `DynConstructors.Builder.impl(...)`。
3. 给 NOOP 单例重写的 `invokeChecked` 加 `@Deprecated since 1.7.0, visibility will be reduced in 1.8.0`，为 1.8.0 进一步收敛留窗口。

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java`

**修改目的**：移除已弃用的内部 `CommitStatus` 枚举（已有父类 `BaseMetastoreOperations.CommitStatus` 可用）。

**工作逻辑**：删除 `protected enum CommitStatus { FAILURE, SUCCESS, UNKNOWN }` 及其 `@Deprecated since 1.6.0, will be removed in 1.7.0` Javadoc。该枚举此前是为了向后兼容从 `BaseMetastoreOperations` 抽出后的过渡期，1.7.0 起统一用父类版本。相应地 `checkCommitStatus` 的返回类型由 `BaseMetastoreTableOperations.CommitStatus` 变为 `BaseMetastoreOperations.CommitStatus`（revapi 记录为 `returnTypeChanged`）。

### `core/src/main/java/org/apache/iceberg/FileScanTaskParser.java`

**修改目的**：移除已被 `ScanTaskParser` 取代的 public 序列化/反序列化方法。

**工作逻辑**：删除 `public static String toJson(FileScanTask)` 与 `public static FileScanTask fromJson(String, boolean)` 两个 `@Deprecated` 方法及其 Javadoc。保留包级的 `toJson(FileScanTask, JsonGenerator)`、`fromJson(JsonNode, boolean)` 等内部方法供 `ScanTaskParser` 复用。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：移除被 `newManifestOutputFile()` 取代的 `newManifestOutput()`。

**工作逻辑**：删除 `protected OutputFile newManifestOutput()` 方法及其 `@Deprecated will be removed in 1.7.0` Javadoc。新代码应使用 `newManifestOutputFile()`（返回 `EncryptedOutputFile`，支持加密）。revapi 中按 4 个子类（`BaseOverwriteFiles`、`BaseReplacePartitions`、`BaseRewriteManifests`、`StreamingDelete`）分别记录了该方法的移除。

### `core/src/main/java/org/apache/iceberg/io/ContentCache.java`

**修改目的**：移除 1.6.0 标记弃用的缓存访问方法与占位内部类。

**工作逻辑**：

1. 删除 `get(String, Function<String, FileContent>)`、`getIfPresent(String)`、`tryCache(FileIO, String, long)` 三个 `@Deprecated` 方法。
2. 删除占位空类 `private static class CacheEntry {}`，把内部 `FileContent` 类改为直接继承 Object（即去掉 `extends CacheEntry`）。这样 `FileContent` 不再依赖占位父类。
3. 移除不再需要的 `import java.util.function.Function`。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java`

**修改目的**：移除 `AuthSession` 的旧多参数构造器，统一走 `AuthConfig`。

**工作逻辑**：删除 `AuthSession(Map<String,String> baseHeaders, String token, String tokenType, String credential, String scope, String oauth2ServerUri)` 构造器及其 `@Deprecated since 1.6.0` Javadoc。该构造器内部只是把这些参数组装成 `AuthConfig` 再委托给主构造器，删除后调用方应直接构造 `AuthConfig`。

### `core/src/test/java/org/apache/iceberg/TestFileScanTaskParser.java`

**修改目的**：移除对已删除 `FileScanTaskParser.toJson/fromJson` 的测试覆盖，保留对替代 API `ScanTaskParser` 的测试。

**工作逻辑**：

1. 在 `testNullArguments` 中删除对 `FileScanTaskParser.toJson(null)` 与 `FileScanTaskParser.fromJson((String) null, true)` 的断言，保留对 `ScanTaskParser.toJson/fromJson` 的断言。
2. 删除 `testFileScanTaskParser(boolean caseSensitive)` 与 `testFileScanTaskParserWithoutTaskTypeField(boolean caseSensitive)` 两个参数化用例——它们测的是被移除的 `FileScanTaskParser` public 方法，已无对应被测对象。
3. 保留 `testScanTaskParser`、`testScanTaskParserWithoutTaskTypeField` 等针对 `ScanTaskParser` 的用例。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java`

**修改目的**：移除被 `storageDescriptor(Schema, String, boolean)` 取代的旧重载。

**工作逻辑**：删除 `static StorageDescriptor storageDescriptor(TableMetadata metadata, boolean hiveEngineEnabled)` 方法及其 `@Deprecated since 1.6.0` Javadoc。该方法内部只是从 `metadata` 取 schema 与 location 后委托给新签名，删除后调用方应直接传 schema 与 location。相应地移除不再需要的 `import org.apache.iceberg.TableMetadata`。

## 小结

- **成效**：按 1.6.0 的弃用承诺，在 1.7.0 正式移除了一批 deprecated API（含方法、构造器、枚举、内部类），并把 `DynMethods.UnboundMethod.invokeChecked` 的可见性从 public 收敛为包级，保持 API 表面整洁；同时通过 revapi.yml 显式登记所有破坏性变更，使兼容性检查不再误报。
- **影响范围**：涉及 `.palantir/revapi.yml`，`iceberg-common`（DynConstructors、DynFields、DynMethods 3 个类），`iceberg-core`（BaseMetastoreTableOperations、FileScanTaskParser、SnapshotProducer、ContentCache、OAuth2Util 5 个类 + TestFileScanTaskParser 测试），`hive-metastore`（HiveOperationsBase 1 个类），共 11 个文件、81 行新增（主要是 revapi 条目）、202 行删除。
- **回迁到 1.4.x 的注意事项**：本提交是**破坏性 API 变更**，1.4.x 作为已发布的维护分支应**保持二进制兼容**，**不应回迁**此类删除 deprecated API 的提交——否则会破坏 1.4.x 系列下游已经编译好的依赖者。1.4.x 用户若需要这些 API 的替代方案，应参考对应的 `@Deprecated` 注释迁移到新方法（如 `ScanTaskParser`、`newManifestOutputFile`、`AuthConfig` 构造、`storageDescriptor(Schema, String, boolean)`），但保留旧 API 不删除。本提交属于主线版本演进，与 1.4.x 维护策略不兼容，建议跳过。
