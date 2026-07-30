# 提交 1534 d6d3cf58a 分析

## 提交信息
- 哈希：d6d3cf58a99b99984a36802fcec078494ca4b46f
- 日期：2024-12-24（Tue Dec 24 19:05:55 2024 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：Core, Spark: Avoid deprecated methods in Guava Files (#11865)

## 总体目的

Iceberg 在 `core` 与 `spark/v3.5` 模块中通过 shade 依赖引入了 Guava 的 `com.google.common.io.Files`（relocated 包路径为 `org.apache.iceberg.relocated.com.google.common.io.Files`），用于若干测试与 JMH 基准测试场景下的临时目录创建、文本文件读写。然而 Guava 较新版本中以下方法已标记为 `@Deprecated`：

- `Files.createTempDir()`：在 Guava 22 起被弃用，原因是不安全（创建的目录权限默认可能过宽，存在安全风险）且无法自定义目录属性；官方推荐使用 JDK 标准 `java.nio.file.Files.createTempDirectory()`。
- `Files.readFirstLine(File, Charset)`：被弃用，推荐用 `Files.asCharSource(file, charset).readFirstLine()`。
- `Files.write(CharSequence, File, Charset)`：被弃用，推荐用 `Files.asCharSink(file, charset).write(charSequence)`。

随着 Iceberg 升级到更新版本的 Guava（或被依赖管理工具提示弃用告警），构建时会出现 deprecation 警告。本提交系统性地将这些已弃用调用替换为推荐的替代方案，以消除弃用告警、保持代码与未来 Guava 版本兼容。

整体策略分两类：能直接用 JDK NIO 标准库的就改用 `java.nio.file.Files`（临时目录创建场景）；文本读写场景继续使用 Guava（保持 shaded 依赖隔离），但改用未弃用的 `asCharSource`/`asCharSink` API。

## 如何达成设计目的

对 4 个文件分别处理：
- 对 `Files.createTempDir()` 调用，替换为 JDK `java.nio.file.Files.createTempDirectory("benchmark-")`，并因 NIO 方法抛出受检 `IOException` 而补充 try/catch 转换为 `UncheckedIOException` 或 `RuntimeException`。
- 对 `Files.readFirstLine` / `Files.write` 调用，改为 `Files.asCharSource(...).readFirstLine()` 与 `Files.asCharSink(...).write(...)`，仍使用 Guava（shaded 包）但用未弃用 API。

### 修改详情

#### `core/src/jmh/java/org/apache/iceberg/ManifestWriteBenchmark.java`

**修改目的**：消除 `Files.createTempDir()` 弃用调用。

**工作逻辑**：移除 `org.apache.iceberg.relocated.com.google.common.io.Files` 的 import，将
```java
this.baseDir = Files.createTempDir().getAbsolutePath();
```
改为
```java
this.baseDir =
    java.nio.file.Files.createTempDirectory("benchmark-").toAbsolutePath().toString();
```
直接使用全限定名 `java.nio.file.Files` 避免与其它 `Files` 符号混淆。`createTempDirectory` 返回 `Path`，通过 `toAbsolutePath().toString()` 得到字符串路径。该方法已在外层方法 `throws IOException` 中，无需额外捕获。

#### `core/src/test/java/org/apache/iceberg/hadoop/HadoopTableTestBase.java`

**修改目的**：消除 `Files.readFirstLine` 与 `Files.write` 弃用调用，仍保留 Guava shaded 依赖（未引入 NIO）。

**工作逻辑**：
```java
// before
return Integer.parseInt(Files.readFirstLine(versionHintFile, StandardCharsets.UTF_8));
// after
return Integer.parseInt(
    Files.asCharSource(versionHintFile, StandardCharsets.UTF_8).readFirstLine());
```
与
```java
// before
Files.write(String.valueOf(version), versionHintFile, StandardCharsets.UTF_8);
// after
Files.asCharSink(versionHintFile, StandardCharsets.UTF_8).write(String.valueOf(version));
```
`asCharSource`/`asCharSink` 是 Guava 推荐的流式 API，分别用于读与写字符内容，语义与原方法等价但未被弃用。

#### `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/action/DeleteOrphanFilesBenchmark.java`

**修改目的**：消除 `Files.createTempDir()` 弃用调用。

**工作逻辑**：将 Guava 的 `Files` import 替换为 JDK 的 `java.nio.file.Files`，并新增 `java.io.IOException`、`java.io.UncheckedIOException` import。原方法：
```java
return Files.createTempDir().getAbsolutePath() + "/" + UUID.randomUUID() + "/";
```
改为
```java
try {
  return Files.createTempDirectory("benchmark-").toAbsolutePath()
      + "/"
      + UUID.randomUUID()
      + "/";
} catch (IOException e) {
  throw new UncheckedIOException(e);
}
```
由于 `catalogWarehouse()` 方法签名不抛出受检异常，而 NIO 的 `createTempDirectory` 抛 `IOException`，因此用 try/catch 包装为 `UncheckedIOException`。

#### `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergSortCompactionBenchmark.java`

**修改目的**：消除 `Files.createTempDir()` 弃用调用。

**工作逻辑**：与上一个文件类似，将 Guava `Files` import 换为 JDK `java.nio.file.Files`，原方法：
```java
String location = Files.createTempDir().getAbsolutePath() + "/" + UUID.randomUUID() + "/";
return location;
```
改为
```java
try {
  String location =
      Files.createTempDirectory("benchmark-").toAbsolutePath() + "/" + UUID.randomUUID() + "/";
  return location;
} catch (IOException e) {
  throw new RuntimeException(e);
}
```
此处捕获后包装为 `RuntimeException`（与该 benchmark 文件风格一致，未严格使用 `UncheckedIOException`）。

## 小结

- **成效**：消除了 4 个文件中 Guava `Files` 已弃用方法的调用，避免构建告警并保持与未来 Guava 版本兼容；对临时目录创建场景顺带使用更安全的 JDK NIO API（`createTempDirectory` 默认权限更可控）。
- **影响范围**：仅影响 JMH benchmark 与一个测试基类（`HadoopTableTestBase`），不进入发布产物，对运行时无影响。
- **回迁到 1.4.x 的注意事项**：属于代码整洁/兼容性改进，不修复任何 bug，**无需回迁**。1.4.x 若使用的 Guava 版本尚未弃用这些方法，回迁反而可能引入不必要的差异。
