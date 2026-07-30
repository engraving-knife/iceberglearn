# 提交 3169：Build: Bump gradle-baseline-java to 6.90.0 (#15160)

## 提交信息

- **序号**：3169 / 4088
- **哈希**：fec9800bcc0c4073ca727f3b3bfdc2f34abb26a3
- **短哈希**：fec9800bc
- **日期**：2026-01-28
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Bump gradle-baseline-java to 6.90.0 (#15160)
- **PR/Issue**：#15160

## 总体目的

这是一次构建工具链升级提交，由 Eduard Tudenhoefner 通过 PR #15160 合入。`gradle-baseline-java`（`com.palantir.baseline:gradle-baseline-java`）是 Palantir 提供的 Gradle 插件，为 Java 项目提供一组静态分析/代码规范检查（baseline errorprone、checkstyle 规则等）。Iceberg 在 `build.gradle` 的 `buildscript` 依赖中引入它，用于在编译期执行额外的代码质量检查。

本次提交将该插件从 `5.72.0` 升级到 `6.90.0`，跨越了一个大版本（5.x → 6.x）。Palantir baseline 6.x 引入了更严格、更新的检查规则，其中多项规则开始对 Java 弃用（deprecation）用法、反射 API（marked for removal）、过时 API 调用等发出警告或错误。由于 baseline 默认将这些检查视为编译错误，升级后 Iceberg 既有代码中大量"刻意使用已弃用 API"的处所会触发构建失败。

因此本次提交的核心工作并非仅改一行版本号，而是**伴随升级同步修复/抑制新规则报告的全部违规**，共涉及 69 个文件、201 行新增、79 行删除。改动遵循"保留既有行为，仅消除检查报错"的原则，主要通过添加 `@SuppressWarnings("deprecation")` / `@SuppressWarnings("removal")` 注解抑制弃用警告、用语义更准确的异常类型替换 `RuntimeException`、迁移到新 API、更新被弃用的方法调用等方式完成。这些修改本身不改变运行时行为（除少数 API 迁移外），目的是让代码在新版 baseline 下通过检查。

升级动机包括：获取 baseline 6.x 的改进规则、保持与现代 JDK 弃用策略（JDK 逐渐将部分遗留 API 标记为 `@Deprecated(forRemoval=true)`）的一致性、以及跟随 Iceberg 社区定期升级构建依赖的惯例。

## 如何达成设计目的

首先在 `build.gradle` 中将 baseline 版本从 `5.72.0` 改为 `6.90.0`，随后针对新版 baseline 报告的违规逐处修复。改动可归纳为五类模式：(1) 为使用已弃用 API 的元素添加 `@SuppressWarnings("deprecation")`；(2) 为使用 JDK 标记 forRemoval 的反射 API 添加 `@SuppressWarnings("removal")`；(3) 将捕获 `IOException` 后抛 `RuntimeException` 改为抛 `UncheckedIOException`；(4) 迁移 Flink/Parquet 中被弃用的方法调用到新 API；(5) 将 Benchmark 中 Guava 的 `Files.createTempDir()`（已弃用）替换为 JDK 标准的 `Files.createTempDirectory`。改动覆盖 aliyun、aws、azure、gcp、common、core、data、nessie、parquet、flink（v1.20/v2.0/v2.1）、spark（v3.4/v3.5/v4.0/v4.1）、open-api 等模块。

## 修改详情

### `build.gradle` (+1/-1 lines)

**修改目的**：升级 gradle-baseline-java 插件版本。

**工作逻辑**：将 `classpath 'com.palantir.baseline:gradle-baseline-java:5.72.0'` 改为 `6.90.0`，是本次所有后续修复的触发点。

### 抑制 `finalize()` 弃用警告（共 12 处，各 +1/-1 lines）

**修改目的**：消除新版 baseline 对 `Object.finalize()`（JDK 9+ 标记为 Deprecated）的检查报错。

**工作逻辑**：涉及 `OSSInputStream`、`OSSOutputStream`（aliyun）、`S3FileIO`、`S3InputStream`、`S3OutputStream`（aws）、`ADLSInputStream`、`ADLSOutputStream`（azure）、`GCSInputStream`、`GCSOutputStream`（gcp）、`ResolvingFileIO`（core）、`HadoopStreams`（core，2 处）等文件 IO 流类。这些类实现 `finalize()` 用于在 GC 回收时兜底关闭未关闭的流。原注解 `@SuppressWarnings({"checkstyle:NoFinalizer", "Finalize"})` 被扩展为追加 `"deprecation"`，即 `@SuppressWarnings({"checkstyle:NoFinalizer", "Finalize", "deprecation"})`，保留原有 checkstyle/Finalize 抑制的同时屏蔽新的 deprecation 检查。行为不变。

### 抑制 AWS SDK / S3 弃用警告（aws 模块，+10/-1 lines）

**修改目的**：消除 AWS SDK v2 中已弃用 API 的 baseline 报错。

**工作逻辑**：
- `RESTSigV4AuthManager.java`：为 `Aws4Signer.create()` 字段声明加 `@SuppressWarnings("deprecation")`（Aws4Signer 已弃用）。
- `RESTSigV4AuthSession.java`：为 `Aws4Signer signer` 字段、构造方法、`sign()` 私有方法分别加 `@SuppressWarnings("deprecation")`。
- `S3FileIOProperties.java`：为 `applySignerConfiguration` 和 `applyRetryConfigurations` 方法加 `@SuppressWarnings("deprecation")`。
- `S3V4RestSignerClient.java`：为 `processRequestPayload` 覆盖方法加 `@SuppressWarnings("deprecation")`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3SignRequestParser.java` (+2/-2 lines)

**修改目的**：迁移 Jackson JsonNode 遍历到非弃用 API。

**工作逻辑**：将 `headersNode.fields().forEachRemaining(...)` 改为 `headersNode.properties().forEach(...)`。Jackson 中 `JsonNode.fields()` 在新版本被弃用，推荐使用 `properties()`；行为等价，均遍历键值对。

### `common` 模块反射工具类抑制 `removal` 警告（+6/-2 lines）

**修改目的**：消除对 JDK 标记 `forRemoval` 的反射 API 的 baseline 报错。

**工作逻辑**：
- `DynConstructors.java`：`hiddenImpl` 方法加 `@SuppressWarnings("removal")`（内部使用 `setAccessible` 等 forRemoval 反射 API）。
- `DynFields.java`：`get` 方法注解从 `@SuppressWarnings("unchecked")` 扩展为 `@SuppressWarnings({"unchecked", "deprecation"})`；`set` 方法加 `@SuppressWarnings("deprecation")`；`hiddenImpl` 加 `@SuppressWarnings("removal")`。
- `DynMethods.java`：`invokeChecked` 注解扩展加 `"deprecation"`；`invoke` 加 `@SuppressWarnings("deprecation")`；`hiddenImpl` 加 `@SuppressWarnings("removal")`。

### core 模块：`UncheckedIOException` 迁移与弃用抑制（+9/-3 lines）

**修改目的**：将抛 `RuntimeException` 改为更精确的 `UncheckedIOException`，并抑制弃用警告。

**工作逻辑**：
- `CatalogHandlers.java`：`catch (IOException e) { throw new RuntimeException(e); }` 改为 `throw new UncheckedIOException(e);`，语义更准确（包装受检 IO 异常为非受检）。
- `HTTPClient.java`：`execute` 覆盖方法加 `@SuppressWarnings("deprecation")`。
- `RESTSerializers.java`：`parseScanResponseContext` 方法加 `@SuppressWarnings("deprecation")`。
- `HadoopMetricsContext.java`：`initialize` 与 `statistics()` 方法各加 `@SuppressWarnings("deprecation")`（使用 Hadoop 弃用的 FileSystem API）。
- `HadoopStreams.java`：2 处 `finalize()` 注解加 `"deprecation"`（同上模式）。
- `ResolvingFileIO.java`：`finalize()` 注解加 `"deprecation"`。

### `data/src/main/java/org/apache/iceberg/data/TableMigrationUtil.java` (+1/-1 lines)

**修改目的**：将 IOException 包装异常类型精确化。

**工作逻辑**：`throw new RuntimeException("Unable to list files in partition: " + partitionUri, e)` 改为 `throw new UncheckedIOException(...)`。

### `nessie` 模块：`UncheckedIOException` 迁移与弃用抑制（+25/-13 lines）

**修改目的**：将 Nessie 客户端中多处 `RuntimeException` 改为 `UncheckedIOException`，并抑制弃用警告。

**工作逻辑**：
- `NessieIcebergClient.java`：在 `createNamespace`、`dropNamespace`、`loadNamespaceProperties`、`updateNamespaceProperties`、`rename` 等方法的 `catch` 块中，将 `throw new RuntimeException(...)` 统一改为 `throw new UncheckedIOException(...)`；`commitView` 方法加 `@SuppressWarnings("deprecation")`。部分改动还补充了原先缺失的异常 cause 传递（如 `updateNamespaceProperties` 中一处补上 `, e`）。
- `NessieTableOperations.java` / `NessieViewOperations.java`：`refresh()` 中 `catch (NessieNotFoundException)` 改抛 `UncheckedIOException`。
- `NessieUtil.java`：`return Optional.of(new RuntimeException(e))` 改为 `Optional.of(new UncheckedIOException(e))`。

### `parquet` 模块：API 迁移与弃用抑制（+8/-4 lines）

**修改目的**：迁移 Parquet 已弃用 API 并抑制警告。

**工作逻辑**：
- `Parquet.java`：`.withRowGroupSize(rowGroupSize)` 改为 `.withRowGroupSize((long) rowGroupSize)`（新签名要求 long，原为 int 重载已弃用）。
- `ParquetWriter.java`：将 `import org.apache.parquet.hadoop.CodecFactory` 替换为 `import org.apache.parquet.compression.CompressionCodecFactory`，字段类型从 `CodecFactory.BytesCompressor` 改为 `CompressionCodecFactory.BytesInputCompressor`（迁移到新包路径）。
- `ParquetCodecFactory.java`：`getCodec` 与 `cacheKey` 方法各加 `@SuppressWarnings("deprecation")`。
- `ParquetReadSupport.java` / `ParquetWriteSupport.java`：覆盖方法各加 `@SuppressWarnings("deprecation")`（使用 Parquet 弃用的 ReadSupport/WriteSupport API）。

### Flink 模块：API 迁移与弃用抑制（flink v1.20/v2.0/v2.1，多处）

**修改目的**：迁移 Flink 已弃用 API 并抑制 baseline 警告。

**工作逻辑**（各 Flink 版本改动一致）：
- `IcebergTableSink.java` / `FlinkSink.java` / `IcebergSink.java`：`@Deprecated private TableSchema tableSchema` 字段加 `@SuppressWarnings("deprecation")` 并调整格式；使用 `TableSchema` 的方法加 `@SuppressWarnings("deprecation")`。
- `IcebergFilesCommitter.java` / `IcebergStreamWriter.java` / `RowDataRewriter.java` / `StreamingReaderOperator.java`：`getRuntimeContext().getIndexOfThisSubtask()` / `getAttemptNumber()` 迁移为 `getRuntimeContext().getTaskInfo().getIndexOfThisSubtask()` / `getTaskInfo().getAttemptNumber()`（Flink 新 API，将子任务信息收敛到 `TaskInfo` 对象）。
- `IcebergSink.java`：`createWriter` 中 `context.getSubtaskId()` / `context.getAttemptNumber()` 迁移为 `context.getTaskInfo().getIndexOfThisSubtask()` / `context.getTaskInfo().getAttemptNumber()`。
- `RewriteDataFilesAction.java`：`env.fromCollection(...)` 改为 `env.fromData(...)`（Flink 弃用 fromCollection，推荐 fromData）。
- `FlinkDynamicTableFactory.java`：`flinkConf.getString(...)` 改为 `flinkConf.get(...)`（Flink ConfigOption API 迁移）。
- `IcebergSource.java` / `IcebergTableSource.java` / `StreamingMonitorFunction.java` / `StreamingReaderOperator.java` / `DynamicIcebergSink.java`：相关方法加 `@SuppressWarnings("deprecation")`（使用 Flink 弃用的 SourceFunction 等 API）。

### Benchmark 文件：临时目录 API 迁移（spark v3.4/v3.5/v4.0/v4.1 jmh，+27/-13 lines）

**修改目的**：替换 Guava 已弃用的 `Files.createTempDir()`。

**工作逻辑**：
- `DeleteOrphanFilesBenchmark.java`（v3.4）与 `IcebergSortCompactionBenchmark.java`（v3.4/v3.5/v4.0/v4.1）：将 `Files.createTempDir().getAbsolutePath()`（来自 `org.apache.iceberg.relocated.com.google.common.io.Files`，已弃用且不抛受检异常）替换为 `Files.createTempDirectory("benchmark-").toAbsolutePath()`（来自 JDK `java.nio.file.Files`），并用 `try/catch (IOException)` 包裹、抛出 `UncheckedIOException`。import 也相应从 Guava 改为 JDK。行为等价（创建临时目录），但使用现代 JDK API。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerExtension.java` (+3/-1 lines)

**修改目的**：将端口绑定失败异常类型精确化。

**工作逻辑**：`catch (BindException e)` 中 `throw new RuntimeException("Failed to start REST server", e)` 改为 `throw new UncheckedIOException("Failed to start REST server", e)`（`BindException` 继承自 `IOException`，用 `UncheckedIOException` 包装语义更准确）。

## 总结

本次提交将 gradle-baseline-java 从 5.72.0 升级到 6.90.0，并同步修复新版 baseline 引入的全部代码检查违规，涉及 69 个文件。改动以"保持行为不变、消除检查报错"为原则，主要手段包括抑制 deprecation/removal 警告、将 `RuntimeException` 精确化为 `UncheckedIOException`、以及迁移 Flink/Parquet/Jackson/Guava 等已弃用 API 到新接口。该升级使 Iceberg 代码库符合更现代的代码规范，并降低未来 JDK 移除遗留 API 时的兼容性风险，体现了良好的依赖治理实践。
