# 提交 1211：Build: Update baseline-java 5.69.0 (#11252)

## 提交信息

- **序号**：1211 / 4088
- **哈希**：746e71931a35e83726852a60d7ca8cb6065e334f
- **短哈希**：746e71931
- **日期**：2024-10-04（Fri Oct 4 12:51:57 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Update baseline-java 5.69.0 (#11252)
- **PR/Issue**：#11252
- **共同作者**：dependabot[bot]

## 总体目的

本提交包含两部分工作：

1. **升级 Palantir baseline-java Gradle 插件**从 `5.61.0` 到 `5.69.0`（semver-minor，跨 8 个次版本）。`gradle-baseline-java` 是 Palantir 提供的 Gradle 静态代码质量检查插件，Iceberg 用它来做编译期的代码规范检查（error-prone 规则、best-practices 检查等）。
2. **修复升级后新增的 lint 错误**：baseline-java 5.69.0 引入/启用了更严格的"locale 敏感操作"检查规则，要求所有 `String.format` 和 `DateTimeFormatterBuilder.toFormatter()` 调用必须显式指定 `Locale`（推荐 `Locale.ROOT`），避免在非英语 locale 的 JVM 上因默认 locale 不同导致格式化结果不一致。

第二部分是本提交的实质内容：跨 49 个文件、约 256 行修改，把所有涉及 `%d`/`%x`/`%02X` 等数字格式化的 `String.format(format, args...)` 调用改为 `String.format(Locale.ROOT, format, args...)`，并把 `DateTimeFormatterBuilder.toFormatter()` 改为 `toFormatter(Locale.ROOT)`。

动机：

- **正确性**：`String.format("%d", 12345)` 在 `Locale.GERMANY` 下仍是 `12345`（数字分组分隔符仅在 `,` flag 时启用），但 `%x`（十六进制）等格式符的输出在某些 locale 下可能受影响；更重要的是 `DateTimeFormatter` 不指定 locale 时使用默认 locale，可能导致日期/时间字段名（如月份缩写）本地化，破坏跨环境一致性。baseline-java 的新规则强制要求显式 locale 以消除这类隐患。
- **跨环境一致性**：Iceberg 在元数据文件名生成（如 `%05d-%s%s`）、错误消息、日志等场景大量使用 `String.format`，这些输出应在任何 JVM locale 下保持一致（文件名尤其关键，不能因 locale 不同而变化）。
- **CI 通过**：升级 baseline 插件后，原有代码会触发新的 lint 错误导致 CI 失败，必须同步修复。

## 如何达成设计目的

整体策略是"机械式批量修复"：

1. 在 `build.gradle` 的 `buildscript.dependencies` 中把 `gradle-baseline-java` 版本从 `5.61.0` 改为 `5.69.0`。
2. 全局搜索所有 `String.format(String, Object...)` 调用，凡是格式串中包含数字格式符（`%d`、`%x`、`%X`、`%02X`、`%05d` 等）的，改为 `String.format(Locale.ROOT, String, Object...)`，并补上 `import java.util.Locale`。
3. 对所有 `new DateTimeFormatterBuilder()...toFormatter()` 调用，改为 `toFormatter(Locale.ROOT)`。
4. PR commit message 中说明：`String.format("%s", foo)` 通常不受 locale 影响（纯字符串替换），但含 `%d`/`%x` 等数字格式符的调用是 locale 敏感的，需要修复。本次只修了数字相关的，纯 `%s` 的调用未改（baseline 规则也只针对 locale 敏感格式符）。

修改涉及 `api`、`core`、`flink/v1.18`、`flink/v1.19`、`flink/v1.20`、`kafka-connect`、`nessie`、`orc`、`parquet`、`pig`、`snowflake`、`spark/v3.3`/`v3.4`/`v3.5`（benchmark）等多个模块。

## 修改详情

### `build.gradle`（修改，1 行）

**修改目的**：升级 baseline-java 插件版本。

```diff
-    classpath 'com.palantir.baseline:gradle-baseline-java:5.61.0'
+    classpath 'com.palantir.baseline:gradle-baseline-java:5.69.0'
```

### `api` 模块（6 个文件，修改）

**修改目的**：修复 api 模块中 locale 敏感的 `String.format` 和 `DateTimeFormatter`。

涉及文件与改动：

- `api/src/main/java/org/apache/iceberg/expressions/BoundReference.java`：`toString()` 中 `String.format("ref(id=%d, accessor-type=%s)", ...)` 改为加 `Locale.ROOT`。
- `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`：
  - 两处 `String.format`（含 `%d`）加 `Locale.ROOT`：一是 `"... (%d values hidden, %d in total)"`，二是 `sanitizeSimpleString` 中的 `String.format("(hash-%08x)", ...)`（十六进制格式化）。
- `api/src/main/java/org/apache/iceberg/io/BulkDeletionFailureException.java`：`String.format("Failed to delete %d files", ...)` 加 `Locale.ROOT`。
- `api/src/main/java/org/apache/iceberg/transforms/TransformUtil.java`：4 处 `humanYear`/`humanMonth`/`humanDay`/`humanHour` 的 `String.format`（含 `%04d`/`%02d` 等零填充数字格式）加 `Locale.ROOT`。这些方法生成分区值的字符串表示，跨 locale 一致性至关重要。
- `api/src/main/java/org/apache/iceberg/types/Types.java`：3 处 `toString()`（`fixed[%d]`、`decimal(%d, %d)`、`%d: %s: %s %s`）加 `Locale.ROOT`。
- `api/src/main/java/org/apache/iceberg/util/DateTimeUtil.java`：`DateTimeFormatterBuilder...toFormatter()` 改为 `toFormatter(Locale.ROOT)`。该 formatter 用于格式化 Iceberg 的时间戳，locale 无关性重要。

### `core` 模块（8 个文件，修改）

**修改目的**：修复 core 模块中 locale 敏感的 `String.format` 和 `DateTimeFormatter`。

涉及文件与改动：

- `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java`：元数据文件名生成 `String.format("%05d-%s%s", newVersion, UUID, ext)` 加 `Locale.ROOT`——文件名必须跨 locale 一致。
- `core/src/main/java/org/apache/iceberg/MetricsModes.java`：`toString()` 中 `truncate(%d)` 加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/ScanSummary.java`：异常消息 `String.format("Too many matching keys: more than %d", maxSize)` 加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`：快照 manifest 文件名 `String.format("snap-%d-%d-%s", ...)` 加 `Locale.ROOT`——文件名一致性。
- `core/src/main/java/org/apache/iceberg/data/avro/IcebergDecoder.java`：异常消息 `String.format("Unrecognized header bytes: 0x%02X 0x%02X", ...)`（十六进制）加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/io/ContentCache.java`：异常消息 `String.format("Failed to read %d bytes: %d bytes in stream", ...)` 加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/io/OutputFileFactory.java`：数据文件名生成 `String.format("%05d-%d-%s-%05d%s", ...)` 加 `Locale.ROOT`——文件名一致性。
- `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`：两处异常消息（insert/update 失败 `%d of %d succeeded`）加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java`：view 元数据文件名 `String.format("%05d-%s%s", ...)` 加 `Locale.ROOT`。
- `core/src/main/java/org/apache/iceberg/vectorized/parquet/DecimalVectorUtil.java`：`String.format` 加 `Locale.ROOT`。

### `flink` 模块（v1.18/v1.19/v1.20 各 6 个文件，修改）

**修改目的**：修复 flink 模块中 locale 敏感的 `String.format`。

三个 flink 版本的改动镜像一致，涉及文件：

- `flink/*/flink/src/main/java/org/apache/iceberg/flink/sink/ManifestOutputFileFactory.java`：manifest 文件名生成加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`：多处 `String.format`（含 `%d`，coordinator 日志/异常消息）加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`：两处 `String.format("Field %d has unsupported field type: %s", ...)` 加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/source/DataIterator.java`：`String.format` 加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/AbstractIcebergEnumerator.java`：`String.format` 加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/source/reader/RecordAndPosition.java`：`String.format` 加 `Locale.ROOT`。
- `flink/*/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplitSerializer.java`：`String.format` 加 `Locale.ROOT`。

### `kafka-connect` 模块（3 个文件，修改）

**修改目的**：修复 kafka-connect 模块中 locale 敏感的 `String.format` 和 `DateTimeFormatter`。

涉及文件：

- `kafka-connect/.../KafkaConnectUtils.java`：多处 `String.format` 加 `Locale.ROOT`。
- `kafka-connect/.../data/IcebergWriter.java`：异常消息 `String.format("An error occurred converting record, topic: %s, partition, %d, offset: %d", ...)` 加 `Locale.ROOT`。
- `kafka-connect/.../data/RecordConverter.java`：`DateTimeFormatterBuilder...toFormatter()` 改为 `toFormatter(Locale.ROOT)`。

### `nessie` 模块（1 个文件，修改）

**修改目的**：修复 nessie 模块中 locale 敏感的 `String.format`。

- `nessie/.../NessieIcebergClient.java`：Nessie 过滤表达式 `String.format("size(entry.keyElements) == %d && entry.encodedKey.startsWith('%s.')", ...)` 加 `Locale.ROOT`。注意这是传给 Nessie API 的过滤字符串，数字格式化必须 locale 无关。

### `orc` 模块（1 个文件，修改）

- `orc/.../ORCSchemaUtil.java`：异常消息 `String.format("Field %d of type %s is required and was not found.", ...)` 加 `Locale.ROOT`。

### `parquet` 模块（1 个文件，修改）

- `parquet/.../PageIterator.java`：两处 `ParquetDecodingException` 消息 `String.format("Can't read value in column %s at value %d out of %d ... repetition level: %d, definition level: %d", ...)` 加 `Locale.ROOT`。

### `pig` 模块（1 个文件，修改）

- `pig/.../PigParquetReader.java`：日期格式化 `String.format("%04d-%02d-%02d", ...)` 和异常消息加 `Locale.ROOT`。

### `snowflake` 模块（1 个文件，修改）

- `snowflake/.../NamespaceHelpers.java`：异常消息 `String.format("Snowflake max namespace level is %d, got namespace '%s'", ...)` 加 `Locale.ROOT`。

### `spark` 模块 benchmark（3 个文件，修改）

- `spark/v3.3`/`v3.4`/`v3.5` 的 `spark/.../action/DeleteOrphanFilesBenchmark.java`：`String.format` 加 `Locale.ROOT`。

## 小结

- **成效**：成功把 Palantir baseline-java 从 5.61.0 升级到 5.69.0，并同步修复了新 lint 规则要求的 locale 显式指定问题（49 个文件、约 256 行）。所有涉及数字/十六进制格式化的 `String.format` 调用和 `DateTimeFormatterBuilder.toFormatter()` 调用现在都显式使用 `Locale.ROOT`，确保文件名、错误消息、日志、日期格式化在任何 JVM locale 下行为一致。
- **影响范围**：跨 12+ 个模块的批量机械式修改，但每处改动都是"加 `Locale.ROOT` 参数"或"加 `import java.util.Locale`"，运行时行为变化极小（在英语 locale 的 JVM 上完全无变化；在非英语 locale 的 JVM 上修正了潜在的不一致）。最关键的是元数据/数据文件名生成路径（`BaseMetastoreTableOperations`、`SnapshotProducer`、`OutputFileFactory`、`BaseViewOperations`、`ManifestOutputFileFactory`）现在保证 locale 无关，避免在非英语 locale 的 JVM 上生成异常文件名。
- **回迁到 1.4.x 的注意事项**：
  1. 本提交改动量大但机械，回迁价值在于：(a) 升级 baseline 插件以获得新的 lint 规则；(b) 修复文件名生成的 locale 敏感性 bug（在非英语 locale 的 JVM 上，1.4.x 可能生成异常文件名）。若 1.4.x 有用户在非英语 locale 环境运行，回迁此 PR 有实际修复价值。
  2. 回迁时需注意 flink 模块路径差异（1.4.x 可能只支持 v1.18/v1.19/v1.20 中的部分版本），按实际存在的模块调整。
  3. baseline-java 5.69.0 可能引入了除 locale 检查外的其他新规则，回迁后若 CI 报其他 lint 错误，需额外修复。
  4. 若不想升级 baseline 插件（避免引入新规则带来额外修复工作），可以只 cherry-pick locale 修复部分（即不升级 `build.gradle`，只改 `String.format`/`toFormatter`），但这会导致 1.4.x 的 baseline 版本与 main 不一致。
  5. `Locale.ROOT` 的使用是 Java 最佳实践，回迁无害。
