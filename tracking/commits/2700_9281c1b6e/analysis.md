# 提交 2700：AWS, Spark, Flink: Remove `org.jetbrains.annotations` (#14192)

## 提交信息

- **序号**：2700 / 4088
- **哈希**：9281c1b6ee18629adec1ec775644ff240eeb7efa
- **短哈希**：9281c1b6e
- **日期**：2025-09-29 15:29:18 +0200
- **作者**：Manu Zhang
- **提交说明**：AWS, Spark, Flink: Remove `org.jetbrains.annotations` (#14192)
- **PR/Issue**：#14192

## 总体目的

本提交从 Iceberg 的 AWS、Spark、Flink 模块中移除 `org.jetbrains.annotations` 依赖，改用功能等价的 `javax.annotation`（`@Nonnull`/`@Nullable`）。`org.jetbrains.annotations`（`@NotNull`/`@Nullable`）是 JetBrains 提供的注解库，常用于标注方法参数、返回值、字段的可空性。但该库并非 Iceberg 的核心依赖，引入它会带来额外的传递依赖与发布产物中的第三方许可证负担。

Iceberg 多数模块已使用 JSR-305 风格的 `javax.annotation.Nonnull`/`javax.annotation.Nullable`（来自 `javax.annotation`/`findbugs` 体系）。本次统一将 AWS/Spark/Flink 模块中残留的 JetBrains 注解替换为 `javax.annotation`，并在根 `build.gradle` 中全局排除 `org.jetbrains:annotations` 依赖，避免其被传递引入。同时清理各 runtime 模块的 `LICENSE` 文件中关于 JetBrains annotations 的条目，因为发布产物不再包含该库。

## 如何达成设计目的

修改分为几类：

1. **源码注解替换**：在 AWS 测试、Flink（v1.20/v2.0/v2.1）源码与测试、Spark（v3.4/v3.5/v4.0）源码与测试中，将 `import org.jetbrains.annotations.NotNull` / `org.jetbrains.annotations.Nullable` 替换为 `import javax.annotation.Nonnull` / `javax.annotation.Nullable`，并将 `@NotNull` 替换为 `@Nonnull`（`@Nullable` 名称一致仅改 import）。

2. **构建全局排除**：在根 `build.gradle` 的 `subprojects` 配置中，向已有的 `exclude` 列表新增 `exclude group: 'org.jetbrains', module: 'annotations'`，确保所有子项目都不会传递引入该依赖。

3. **LICENSE 文件清理**：从 Flink（v1.20/v2.0/v2.1）、Kafka Connect（hive/main）、Spark（v3.4/v3.5/v4.0）的 runtime `LICENSE` 文件中移除"JetBrains annotations"条目（含版权、主页、许可证信息），反映发布产物不再包含该库。

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (+2/-2 lines)

**修改目的**：将测试中的 JetBrains `@NotNull` 替换为 `javax.annotation.Nonnull`。

**工作逻辑**：import 由 `org.jetbrains.annotations.NotNull` 改为 `javax.annotation.Nonnull`；方法 `signWithAwsSigner` 上的 `@NotNull` 注解改为 `@Nonnull`。语义不变，仅更换注解来源。

### `build.gradle` (+1/-0 lines)

**修改目的**：全局排除 `org.jetbrains:annotations` 传递依赖。

**工作逻辑**：在 `subprojects` 内已有的依赖排除块（排除 `com.sun.jersey`、`pentaho-aggdesigner-algorithm` 等）中新增 `exclude group: 'org.jetbrains', module: 'annotations'`，使所有子项目解析依赖时自动排除该库。

### `flink/v1.20/flink-runtime/LICENSE` (+0/-8 lines)

**修改目的**：移除 LICENSE 中 JetBrains annotations 条目。

**工作逻辑**：删除"This binary artifact contains JetBrains annotations."段落（含版权、主页、许可证）。其余条目（如 Google Guava）保留。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java` (+4/-2 lines)

**修改目的**：替换 `DataStatisticsCoordinator` 中的 JetBrains 可空性注解。

**工作逻辑**：新增 `import javax.annotation.Nonnull` 与 `import javax.annotation.Nullable`，移除对应的 JetBrains import，并将代码中 `@NotNull`/`@Nullable` 注解替换为 `javax.annotation` 版本。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ManualSource.java` (+1/-1 lines)

**修改目的**：替换 `ManualSource` 中的 JetBrains 注解 import。

**工作逻辑**：将 JetBrains `@Nullable` import 改为 `javax.annotation.Nullable`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+5/-3 lines)

**修改目的**：替换测试中的 `@NotNull` 为 `@Nonnull`。

**工作逻辑**：新增 `import javax.annotation.Nonnull`，移除 `org.jetbrains.annotations.NotNull`；3 处方法签名上的 `@NotNull` 改为 `@Nonnull`。

### `flink/v2.0/...`、`flink/v2.1/...` 同名文件

**修改目的**：在 Flink v2.0、v2.1 维护分支上同步应用与 v1.20 相同的注解替换与 LICENSE 清理。

**工作逻辑**：与 v1.20 中对应文件改动一致——`DataStatisticsCoordinator`、`ManualSource`、`TestDynamicWriter` 替换注解 import 与注解名，`flink-runtime/LICENSE` 移除 JetBrains 条目。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`、`kafka-connect/kafka-connect-runtime/main/LICENSE` (+0/-6 lines each)

**修改目的**：移除 Kafka Connect runtime LICENSE 中 JetBrains annotations 条目。

**工作逻辑**：删除"JetBrains annotations"段落。

### `spark/v3.4/spark-runtime/LICENSE`、`spark/v3.5/spark-runtime/LICENSE`、`spark/v4.0/spark-runtime/LICENSE` (+0/-8 lines each)

**修改目的**：移除 Spark runtime LICENSE 中 JetBrains annotations 条目。

**工作逻辑**：同上，删除 JetBrains annotations 段落。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+20/-16 lines)

**修改目的**：替换 `SparkTableUtil` 中自定义 `ExecutorService` 实现上的 JetBrains 注解。

**工作逻辑**：新增 `import javax.annotation.Nonnull` 与 `import javax.annotation.Nullable`，移除 JetBrains import；将 `shutdownNow`、`awaitTermination`、`submit`、`invokeAll`、`invokeAny` 等方法签名上的 `@NotNull`/`@Nullable` 替换为 `javax.annotation` 版本。涉及多处方法参数与返回值注解。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java` (+2/-2 lines)

**修改目的**：替换测试中的 JetBrains 注解。

**工作逻辑**：将 `@NotNull`/`@Nullable` 的 import 与注解名替换为 `javax.annotation` 版本。

### `spark/v3.5/...`、`spark/v4.0/...` 同名文件

**修改目的**：在 Spark v3.5、v4.0 上同步应用与 v3.4 相同的注解替换与 LICENSE 清理。

**工作逻辑**：与 v3.4 中对应文件改动一致。

## 总结

本提交从 AWS、Spark（v3.4/v3.5/v4.0）、Flink（v1.20/v2.0/v2.1）、Kafka Connect 模块中移除 `org.jetbrains.annotations` 依赖，统一改用 `javax.annotation.Nonnull`/`Nullable`，并在根 `build.gradle` 全局排除该依赖、清理各 runtime LICENSE 文件中的 JetBrains 条目。这减少了不必要的第三方依赖与许可证负担，使可空性注解体系在 Iceberg 各模块中保持一致。改动机械且语义等价，风险低。
