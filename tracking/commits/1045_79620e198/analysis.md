# 提交 1045：Core, Flink: Fix build warnings (#10899)

## 提交信息

- **序号**：1045 / 4088
- **哈希**：79620e198009fa243c278c66fd442d107b46206a
- **短哈希**：79620e198
- **日期**：2024-08-09（Fri Aug 9 20:21:00 2024 +0530）
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Core, Flink: Fix build warnings (#10899)
- **PR/Issue**：#10899

## 总体目的

Iceberg 构建中启用了 errorprone 等静态分析工具，会报告若干编译期警告。这些警告虽不影响功能，但会污染构建输出、掩盖真正的问题，并在升级编译器/依赖时可能演变为错误。本提交集中清理 core 与 flink 模块中由静态分析报告的一批警告，使构建输出更干净，并消除潜在的正确性隐患（如整数比较的精度问题、被忽略的 Future 返回值、未指定字符集的字符串构造等）。

这些修改属于代码卫生（code hygiene）范畴，逐一对应具体的 errorprone 检查规则，不改变运行时语义。

## 如何达成设计目的

针对每条警告采用最小化修复：对类型推断/精度类警告通过显式类型转换或修正字面量类型解决；对“忽略 Future 返回值”和“引用相等比较”类警告用 `@SuppressWarnings` 显式声明并保留原逻辑（因为现有逻辑是有意为之）；对未使用代码直接删除；对未指定字符集的 `new String(byte[])` 显式传入 `StandardCharsets.UTF_8`。所有改动都保持原有行为不变。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseEntriesTable.java`

**修改目的**：移除未被使用的泛型类型参数。

**工作逻辑**：`contentMatch(Integer fileContentId)` 方法原声明为 `private <T> boolean contentMatch(...)`，其中类型参数 `T` 从未在方法体或参数中使用，属于多余泛型声明。改为 `private boolean contentMatch(...)`，去除无意义的 `<T>`，消除 errorprone 的 `UnusedTypeParameter` 警告。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java`

**修改目的**：修正 `Math.min` 中 long 与 int 混合比较的类型推断。

**工作逻辑**：原表达式 `avgFileSizeWithoutRemainder < Math.min(1.1 * targetFileSize, writeMaxFileSize())` 中，`1.1 * targetFileSize` 为 double，`writeMaxFileSize()` 返回 long，`Math.min(double, long)` 会触发精度/重载选择警告。改为 `Math.min(1.1 * targetFileSize, (double) writeMaxFileSize())`，显式将 long 提升为 double，确保 `Math.min` 选取 `double` 重载，消除警告。

### `core/src/main/java/org/apache/iceberg/rest/ExponentialHttpRequestRetryStrategy.java`

**修改目的**：修正 `Math.pow` 参数的类型转换。

**工作逻辑**：原 `Math.pow(2.0, (long) execCount - 1)` 中 `(long) execCount - 1` 是 long 减 int 字面量 1，结果为 long。改为 `(long) execCount - 1.0`，使减法在 double 域进行，与 `Math.pow(double, double)` 签名匹配，消除 long 作为 `Math.pow` 第二参的精度警告。结果语义不变。

### `core/src/main/java/org/apache/iceberg/util/Pair.java`

**修改目的**：移除未被使用的 Avro 构造函数。

**工作逻辑**：删除 `private Pair(Schema schema)` 构造函数及其注释 `/** Constructor used by Avro */`。该构造函数仅赋值 `this.schema` 字段，但已无任何调用方（Avro 反序列化路径不再使用），属于死代码。移除后消除未使用代码警告。保留主构造函数 `Pair(X first, Y second)`。

### `core/src/main/java/org/apache/iceberg/util/ParallelIterable.java`

**修改目的**：显式抑制被忽略的 Future 返回值警告。

**工作逻辑**：在 `ParallelIterable` 的 `close()` 方法上添加 `@SuppressWarnings("FutureReturnValueIgnored")`。该 close 实现会取消已提交的任务，但有意不等待 Future 的返回值（关闭时应快速返回而非阻塞）。errorprone 的 `FutureReturnValueIgnored` 检查会对此报警，但此处忽略返回值是设计意图，故用注解显式声明，保留原逻辑。

### `core/src/main/java/org/apache/iceberg/util/Tasks.java`

**修改目的**：修正 `Math.min` 中 long 与 double 的混合类型。

**工作逻辑**：原 `(int) Math.min(minSleepTimeMs * Math.pow(scaleFactor, attempt - 1), maxSleepTimeMs)` 中，第一参数为 double（`long * double`），第二参数 `maxSleepTimeMs` 为 long。改为 `(double) maxSleepTimeMs`，使 `Math.min` 选取 double 重载，消除类型警告。与 `SizeBasedFileRewriter`、`ExponentialHttpRequestRetryStrategy` 的修复思路一致。

### `core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java`

**修改目的**：显式抑制引用相等比较警告。

**工作逻辑**：在 `commit(ViewMetadata base, ViewMetadata metadata)` 方法上添加 `@SuppressWarnings("ImmutablesReferenceEquality")`。该方法中 `if (base != current())` 使用 `!=` 进行引用比较以快速判断 metadata 是否过期，这是有意的引用相等检查（比较的是同一个对象实例，而非值相等）。errorprone 的 `ImmutablesReferenceEquality` 检查对可变/不可变类型的 `==`/`!=` 比较报警，此处属于合理的乐观并发判断，故用注解抑制。

### `flink/v1.20/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`

**修改目的**：为字节数组转字符串显式指定字符集。

**工作逻辑**：原 `new String(buffer)` 使用平台默认字符集，errorprone 的 `DefaultCharset` 检查会报警（因为依赖平台默认字符集会导致跨环境不一致）。改为 `new String(buffer, StandardCharsets.UTF_8)`，新增 `import java.nio.charset.StandardCharsets`，显式使用 UTF-8 解码，使基准测试行为可复现且跨平台一致。

## 小结

- **成效**：清理了 core 与 flink 模块共 8 处构建警告，涉及未使用泛型/构造函数、整数精度比较、被忽略的 Future 返回值、引用相等比较、平台默认字符集等，构建输出更干净，消除若干潜在可移植性/正确性隐患。
- **影响范围**：8 个文件，11 行增、10 行删，均为单点小改动，不改运行时语义。涉及 core 模块的工具类、REST 重试、actions 重写器、视图操作，以及 flink v1.20 的一个 JMH 基准测试。
- **回迁到 1.4.x 的注意事项**：适合回迁，风险极低。这些都是无害的代码卫生修复，不改变行为。回迁时需注意 1.4.x 对应文件是否已存在相同代码结构（如 `BaseViewOperations`、`ExponentialHttpRequestRetryStrategy` 在 1.4.x 中可能尚未引入或结构不同，需逐文件确认上下文匹配）。若 1.4.x 构建未启用同等严格的 errorprone 规则，回迁收益有限但无负面影响。`Pair(Schema)` 构造函数的删除需确认 1.4.x 上确实无 Avro 路径调用它。
