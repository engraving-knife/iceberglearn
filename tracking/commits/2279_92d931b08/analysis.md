# 提交 2279：Build: Fix errorprone warnings (#13217)

## 提交信息

- **序号**：2279 / 4088
- **哈希**：92d931b08af463fd1c022beed48ba9e940363bf8
- **短哈希**：92d931b08
- **日期**：2025-06-27 11:43:38 +0200
- **作者**：Yu-Chuan Hung
- **提交说明**：Build: Fix errorprone warnings (#13217)
- **PR/Issue**：#13217

## 总体目的

本提交修复项目构建时 Error Prone 静态分析工具报告的若干警告。Error Prone 是 Google 开发的 Java 编译器插件，用于在编译期检测常见编程错误和代码异味。Iceberg 项目在构建中启用了 Error Prone，这些警告会影响构建的整洁度，部分警告还揭示了潜在的代码质量问题。

主要修复涉及四个方面：1) `Timestamps.java` 中 8 个近乎重复的 `SerializableFunction` 单例类触发了 Error Prone 的警告，通过重构为统一的 `Apply` 类解决；2) `ADLSFileIO.java` 中 lambda 参数多余的括号；3) `BigQueryMetastoreClientImpl.java` 中使用 Stream `.parallel()` 的不当用法（使用公共 ForkJoinPool 且存在流复用问题），改为使用 Iceberg 的 `Tasks` 工具类配合专用线程池；4) 三个版本的 `DynamicRecordInternalSerializer.java` 中 `Objects.hashCode(boolean)` 对基本类型的多余装箱调用，改为 `Boolean.hashCode(boolean)`。

## 如何达成设计目的

- `Timestamps` 枚举：将 8 个独立的 `MicrosToYears/Months/Days/Hours` 和 `NanosToYears/Months/Days/Hours` 单例类合并为一个 `Apply` 类，通过 `TimestampUnit` 枚举（MICROS/NANOS）和 `ChronoUnit` 粒度的嵌套 switch 分发到对应的 `DateTimeUtil` 方法。构造函数改为接收 `TimestampUnit` 而非 `SerializableFunction` 实例，内部创建 `Apply` 对象。
- `ADLSFileIO`：移除 lambda 参数多余括号 `(provider -> ...)` 改为 `provider -> ...`。
- `BigQueryMetastoreClientImpl`：将 `.parallel().filter(...)` 流式并行过滤改为先 `collect` 到列表，再用 `Tasks.foreach(allTables).executeWith(ThreadPools.getWorkerPool()).noRetry().suppressFailureWhenFinished()` 并行处理，结果收集到 `Collections.synchronizedList`，避免使用公共 ForkJoinPool。
- `DynamicRecordInternalSerializer`：`Objects.hashCode(writeSchemaAndSpec)`（boolean）改为 `Boolean.hashCode(writeSchemaAndSpec)`，避免不必要的装箱。

## 修改详情

### `api/src/main/java/org/apache/iceberg/transforms/Timestamps.java` (+78/-132 lines)

**修改目的**：消除 8 个重复 `SerializableFunction` 单例类引发的 Error Prone 警告，简化代码结构。

**工作逻辑**：原设计为每个时间粒度（YEARS/MONTHS/DAYS/HOURS）×每个时间单位（MICROS/NANOS）创建一个独立的 `@Immutable SerializableFunction` 单例类（共 8 个），每个类的 `apply` 方法仅调用对应的 `DateTimeUtil` 方法。重构后：
- 新增 `TimestampUnit` 枚举（MICROS、NANOS）。
- 新增 `Apply` 类（`@Immutable`，实现 `SerializableFunction<Long, Integer>`），持有 `granularity`（ChronoUnit）和 `timestampUnit`，`apply` 方法通过嵌套 switch（先按 timestampUnit 再按 granularity）分发到对应 `DateTimeUtil` 方法。
- 枚举常量改为引用 `TimestampUnit.MICROS`/`TimestampUnit.NANOS`，构造函数接收 `TimestampUnit` 并在内部 `new Apply(granularity, timestampUnit)`。
- 删除全部 8 个旧单例类（约 110 行）。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/ADLSFileIO.java` (+1/-1 lines)

**修改目的**：修复 lambda 参数多余括号的代码风格警告。

**工作逻辑**：`.ifPresent((provider -> ...))` 改为 `.ifPresent(provider -> ...)`，移除 lambda 表达式外层多余的括号。

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreClientImpl.java` (+22/-12 lines)

**修改目的**：修复 Stream `.parallel()` 使用不当的警告，改用更可控的并行执行方式。

**工作逻辑**：原代码对 `tablesStream` 使用 `.parallel().filter(...)` 进行并行过滤（调用 `load` 验证是否为有效 Iceberg 表），依赖公共 ForkJoinPool。新代码：
1. 先 `tablesStream.collect(Collectors.toList())` 收集到 `allTables`。
2. 若 `!listAllTables`，使用 `Tasks.foreach(allTables).executeWith(ThreadPools.getWorkerPool()).noRetry().suppressFailureWhenFinished().run(table -> { try { load(...); validTables.add(table); } catch (NoSuchTableException) { /* 忽略非 Iceberg 表 */ } })`，结果收集到 `Collections.synchronizedList`。
3. 返回 `validTables`；若 `listAllTables` 则直接返回 `allTables`。

此改动使用 Iceberg 专用的 `ThreadPools.getWorkerPool()` 替代公共 ForkJoinPool，避免影响其他并行任务；使用 `Tasks` API 提供更可控的重试和失败抑制；synchronized list 保证线程安全收集。

### `flink/v1.19`、`flink/v1.20`、`flink/v2.0` 的 `DynamicRecordInternalSerializer.java` (各 +2/-1 lines)

**修改目的**：修复 `Objects.hashCode(boolean)` 对基本类型多余装箱的警告。

**工作逻辑**：`hashCode()` 方法中 `Objects.hashCode(writeSchemaAndSpec)`（`writeSchemaAndSpec` 为 boolean）改为 `Boolean.hashCode(writeSchemaAndSpec)`，直接使用包装类的静态方法，避免 `Objects.hashCode` 内部将 boolean 装箱为 Boolean 再哈希的多余开销。同时移除不再使用的 `java.util.Objects` import。

## 总结

本提交修复了 Error Prone 报告的多类警告，包括代码重复（Timestamps 的 8 个单例类合并为 1 个 Apply 类）、不当并行流使用（改为 Tasks + 专用线程池）、多余装箱和代码风格问题。其中 `Timestamps` 重构（净减 54 行）和 `BigQueryMetastoreClientImpl` 的并行执行改写是核心改动，不仅消除了警告，还提升了代码的可维护性和并行执行的健壮性。
