# 提交 0892：Address IntelliJ inspection findings (#10583)

## 提交信息

- **序号**：0892 / 4088
- **哈希**：afda8be25652d44d9339a79c6797b6bf20c55bd6
- **短哈希**：afda8be25
- **日期**：2024-07-03（Wed Jul 3 14:36:53 2024 +0200）
- **作者**：Robert Stupp
- **提交说明**：Address IntelliJ inspection findings (#10583)
- **PR/Issue**：#10583

## 总体目的

IntelliJ IDEA 自带一套非常严苛的静态代码检查（Inspections），能够发现冗余的强制转换、可改为 `final` 的字段、可用 try-with-resources 替代的 try-finally、多余的 `toString()` 调用、可用 `instanceof` 模式替代的 `Class.isInstance` 等问题。Apache Iceberg 代码库经过多年演进，积累了大量此类小瑕疵：它们不影响功能，但会让静态分析报告非常嘈杂、降低代码可读性，也容易在重构时引入难察觉的副作用。

本提交的目的是系统性地"消化" IntelliJ 检查报告，对仓库中 Spark 3.5 / Flink 1.19 / Flink 1.20 等最新主线模块（以及 api、core、aws、aliyun、common、parquet、orc、hive、nessie、snowflake、pig、dell 等通用模块）的代码做一轮集中清理，使代码更简洁、字段语义更明确、资源管理更安全，并为后续 CI 上将 IntelliJ 检查纳入质量门禁打基础。

之所以需要这次集中清理，是因为：1）一些字段在构造后实际不再被重赋值，但缺少 `final` 修饰符，使得线程可见性语义不明确；2）一些 try-finally 完全可以靠 try-with-resources 简化并避免资源泄漏；3）一些显式 cast 在 Java 自动装箱/拆箱或类型推断后已不必要；4）个别地方存在 `break` 之后给变量赋值的无用代码。

## 如何达成设计目的

通过人工逐文件审视 IntelliJ 报告并应用 quick-fix，对约 153 个文件做机械化、零语义变化的清理。整体设计原则是：**不改变运行时行为，只让代码更符合现代 Java 风格与 IntelliJ 的默认期望**。具体手法包括：

- 给在构造后不再被重赋值的字段加 `final`；
- 把只在实例内使用的常量从实例字段改为 `static final`；
- 把 `Timed` 这种 `AutoCloseable` 的 try-finally 改为 try-with-resources；
- 删除 `(Object) null`、`(long) value`、`String.valueOf(...)`、`x.booleanValue()` 等冗余调用；
- 把 `Error.class.isInstance(cause)` 改为 `cause instanceof Error`，把 `size() < 1` 改为 `isEmpty()`；
- 简化 lambda 表达式、合并 schema 数组初始化；
- 修复 javadoc 参数名；
- 删除 break 之后多余的赋值语句；
- 简化 `ZOrderByteUtils` 中 short/tinyint/float 的有序字节转换，直接转发给 int/double 版本。

## 修改详情

本次修改涉及 153 个文件、412 行新增、449 行删除，覆盖 api、core、common、aliyun、aws、parquet、orc、flink 1.19/1.20、spark 3.5、hive、nessie、snowflake、pig、dell 等模块。下面按修改类型归类说明有代表性的文件。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`（最大单文件改动，约 142 行）

**修改目的**：把 `commit()` 方法中"先 `start()` 计时器、try 主体、finally 调用 `stop()`"的模式重构为 try-with-resources，让计时器随 try 块退出自动停止，避免漏 stop。

**工作逻辑**：原代码：

```java
Timed totalDuration = commitMetrics().totalDuration().start();
try {
  Tasks.foreach(ops)...run(...);
} catch (...) { ... }
try {
  // load snapshot and cleanup
} catch (Throwable e) { ... }
totalDuration.stop();
```

问题是 `totalDuration.stop()` 放在方法最末尾，前面任意异常都会跳过它，导致计时不准；而且 try-finally 模式啰嗦。改为：

```java
try (Timed ignore = commitMetrics().totalDuration().start()) {
  try {
    Tasks.foreach(ops)...run(...);
  } catch (...) { ... }
  try {
    // load snapshot and cleanup
  } catch (Throwable e) { ... }
}
```

由于 `Timed` 实现了 `AutoCloseable`，try-with-resources 会在块退出时（无论正常或异常）自动调用 `close()`，等价于 `stop()`，从而保证计时器一定被停止。逻辑主体未变，仅是结构上把整个 commit+cleanup 包进了 try-with-resources。

### `api/src/main/java/org/apache/iceberg/metrics/DefaultTimer.java`

**修改目的**：把 `time(Supplier)`、`timeCallable(Callable)`、`time(Runnable)` 三个方法中的 try-finally 改为 try-with-resources。

**工作逻辑**：原代码：

```java
Timed timed = start();
try {
  return supplier.get();
} finally {
  timed.stop();
}
```

改为：

```java
try (Timed ignore = start()) {
  return supplier.get();
}
```

依赖 `Timed` 是 `AutoCloseable`，try-with-resources 退出时自动调用 `close()`（等价于 `stop()`），代码更简洁且更不易在异常路径上漏 stop。

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java`

**修改目的**：1）给 `dedupFields` 字段加 `final`，因为该 map 在构造后只被写入键值、从未被重新赋值；2）简化 `Preconditions.checkArgument(name != null && !name.isEmpty(), ...)` 为 `checkArgument(!name.isEmpty(), ...)`，因为前面已经做过 null 校验。

**工作逻辑**：删掉冗余的 null 检查后语义不变，更直观地表达"到这里 name 一定非 null"。

### `api/src/main/java/org/apache/iceberg/Schema.java`

**修改目的**：给 `idsToReassigned` 和 `idsToOriginal` 两个 transient 字段加 `final`，强调它们在构造后引用不可变（虽然内容可能改）。

### `api/src/main/java/org/apache/iceberg/SortOrderBuilder.java`

**修改目的**：删除接口默认方法末尾多余的 `;`（接口方法声明后无需 `;`），让 IntelliJ 不再报"unnecessary semicolon"。

### `api/src/main/java/org/apache/iceberg/io/FileIO.java`

**修改目的**：把 `this.getClass().toString()` 简化为 `this.getClass()`，因为 `String.format` 会自动调用 `toString()`。

### `api/src/main/java/org/apache/iceberg/util/BucketUtil.java`

**修改目的**：删除冗余的强制转换：`MURMUR3.hashLong((long) value)` → `MURMUR3.hashLong(value)`（Java 自动装箱 int 到 long 的重载已存在），同理 `doubleToLongBits((double) value)` → `doubleToLongBits(value)`。

### `core/src/main/java/org/apache/iceberg/util/ArrayUtil.java`

**修改目的**：把 `array[i].booleanValue()`、`array[i].byteValue()`、`array[i].shortValue()`、`array[i].intValue()`、`array[i].longValue()`、`array[i].floatValue()`、`array[i].doubleValue()` 等装箱类型的拆箱调用，改为直接赋值 `array[i]`，由 Java 自动拆箱完成。涉及 7 个基本类型数组的转换方法，每处 1 行。

### `core/src/main/java/org/apache/iceberg/util/Pair.java`

**修改目的**：1）删除 `new Schema.Field("x", xSchema, null, (Object) null)` 中冗余的 `(Object)` cast，改为 `null`；2）把 `String.valueOf(first)` 简化为 `first + ""` 形式（实际是 `"(" + first + ", " + second + ")"`，由 `+` 自动调用 toString）。

### `core/src/main/java/org/apache/iceberg/util/Tasks.java`

**修改目的**：1）给 `stopRetryExceptions` 字段加 `final`；2）把 `Error.class.isInstance(cause)` 改为 `cause instanceof Error`，更符合现代 Java 风格。

### `core/src/main/java/org/apache/iceberg/util/ZOrderByteUtils.java`

**修改目的**：简化 `shortToOrderedBytes`、`tinyintToOrderedBytes`、`floatToOrderedBytes` 三个方法的实现，直接转发给 `intToOrderedBytes` / `doubleToOrderedBytes`。

**工作逻辑**：原代码为 short 和 tinyint 分别实现"将值符号扩展到 long 再异或 `0x8000000000000000L` 再 putLong"的逻辑，这与 int 的实现等价（Java 自动类型提升），因此可以直接 `return intToOrderedBytes(val, reuse);`。同理 float 直接调用 `doubleToOrderedBytes(val, reuse)`。这样消除了重复实现，让行为更一致，也避免维护 4 份相同的"按 long 排序"代码。

### `core/src/main/java/org/apache/iceberg/ClientPoolImpl.java`

**修改目的**：把实例字段 `connectionRetryWaitPeriodMs = 1000` 改为静态常量 `private static final int CONNECTION_RETRY_WAIT_PERIOD_MS = 1000;`，并在使用处改为引用常量名。

**工作逻辑**：该值是固定的重试等待时间，所有实例共享同一值，没必要每个实例都持有副本。改为 `static final` 后既节省内存，又符合 Java 命名约定（全大写下划线分隔）。

### `core/src/main/java/org/apache/iceberg/BaseEntriesTable.java`

**修改目的**：把 `partitionType.fields().size() < 1` 改为 `partitionType.fields().isEmpty()`，更直观。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`

**修改目的**：给 `SetSnapshotRef` 内部类的 `minSnapshotsToKeep`、`maxSnapshotAgeMs`、`maxRefAgeMs` 三个字段加 `final`，因为它们在构造后不再被重赋值。

### `core/src/main/java/org/apache/iceberg/rest/responses/ErrorResponse.java` 和 `LoadTableResponse.java`

**修改目的**：给 `ErrorResponse` 的 `message`、`type`、`code`、`stack` 字段，以及 `LoadTableResponse.Builder` 的 `config` 字段加 `final`，强调不可变。

### `core/src/main/java/org/apache/iceberg/avro/AvroFileAppender.java`、`AvroSchemaUtil.java`

**修改目的**：1）给 `stream`、`datumWriter`、`icebergSchema`、`metricsConfig` 等字段加 `final`；2）删除 `new Schema.Field("key", keySchema, null, (Object) null)` 中冗余的 `(Object)` cast。

### `core/src/main/java/org/apache/iceberg/encryption/NativeFileCryptoParameters.java`

**修改目的**：给 `fileKey`、`fileEncryptionAlgorithm` 字段及其 `Builder.fileKey` 字段加 `final`。

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java`、`S3FileIOProperties.java`、`LakeFormationAwsClientFactory.java`

**修改目的**：批量给 AWS 配置类中"构造后不再被重赋值"的字段加 `final`，例如 `clientAssumeRoleArn`、`glueEndpoint`、`dynamoDbEndpoint`、`restSigningName`、`accessKeyId`、`secretAccessKey`、`sessionToken`、`isDualStackEnabled`、`isPathStyleAccess`、`isUseArnRegionEnabled`、`isAccelerationEnabled`、`endpoint`，以及 `LakeFormationCredentialsProvider` 的 `client`、`tableArn`。

### `aliyun/src/main/java/org/apache/iceberg/aliyun/oss/BaseOSSFile.java`

**修改目的**：给 `aliyunProperties` 字段加 `final`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalController.java`

**修改目的**：把方法参数 `pCount` 重命名为 `bytesRead`，并同步更新 javadoc 中的 `@param` 描述，使参数名与文档一致、更具可读性。

### `common/src/main/java/org/apache/iceberg/common/DynFields.java`、`DynMethods.java`、`DynClasses.java`

**修改目的**：给内部类 `MakeFieldAccessible.hidden`、`MakeAccessible.hidden` 等 `Field`/`Method` 字段加 `final`。

### `parquet/src/main/java/org/apache/iceberg/parquet/BaseVectorizedParquetValuesReader.java`

**修改目的**：清理 IntelliJ 报告的小问题（同样属于 final/冗余 cast 类）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java`

**修改目的**：删除 `break` 之前的 `shouldContinueReading = false;` 这条无用赋值——既然马上要 `break` 跳出循环，给循环局部变量赋值毫无意义，且 IntelliJ 会报"dead assignment"。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java`

**修改目的**：1）给 `sparkSchema`、`icebergSchema`、`data0`、`data1` 字段加 `final`；2）简化 lambda 表达式，去掉冗余的 `(MapPartitionsFunction<Row, Row>)` cast；3）把 `location.toString()` 简化为 `location`（因为 `String.format` 会自动调用 toString）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/FlinkSqlExtension.java`

**修改目的**：清理 IntelliJ 报告（小改动）。

### 测试代码批量精简（`TestTableMetadata.java`、`TestNameMappingWithAvroSchema.java` 等）

**修改目的**：1）把 `UUID.randomUUID().toString()` 简化为 `UUID.randomUUID()`（在字符串拼接中 `+` 会自动调用 toString）；2）把 `Schema.createUnion(new Schema[] { ... })` 的数组初始化简化为可变参数形式 `Schema.createUnion(...)`，让代码更紧凑；3）整理多行布局。

## 小结

- **成效**：一次性消化了 IntelliJ 在 api、core、aws、aliyun、common、parquet、orc、flink 1.19/1.20、spark 3.5 等最新版本模块上的大量静态检查报告，让代码更简洁、字段语义更明确（`final` 化）、资源管理更安全（try-with-resources）、消除了重复实现与无用赋值。这为后续把 IntelliJ 检查纳入 CI 质量门禁打下基础。
- **影响范围**：涉及 153 个文件，412 行新增、449 行删除，覆盖几乎所有主仓库模块的 main 与 test 源码。所有改动都是行为不变的清理，不改变 API、不改变算法、不改变测试断言。
- **回迁到 1.4.x 的注意事项**：可以**部分**回迁，但需要谨慎选择。1.4.x 维护分支对应的 Spark 版本通常是 3.3/3.4、Flink 是 1.17/1.18/1.19，这些版本的相同文件改动已经被另一个提交 #10625（即本批 0895 号提交）单独覆盖了。所以**不建议**把本提交（0891）整体 cherry-pick 到 1.4.x——会和 0895 重叠或冲突。建议：1.4.x 应优先回迁 0895（针对老版本 Spark/Flink 的 IntelliJ 修复）；api/core/aws/common/aliyun/parquet/orc/hive/nessie/snowflake/pig/dell 等通用模块的改动如果想回迁，可以挑选那些与 1.4.x 文件基线一致的子集逐个 cherry-pick，但需注意 `SnapshotProducer` 的 try-with-resources 改动较大，回迁前要确认 1.4.x 该文件没有其他差异冲突。整体上属于低风险、纯清理类改动，回迁价值主要在于减少未来 cherry-pick 时的合并冲突，而非修复 bug。
