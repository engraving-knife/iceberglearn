# 提交 2941：Core: Use `@TempDir` in TestTableMetadataParser (#14732)

## 提交信息

- **序号**：2941 / 4088
- **哈希**：3747965e9d750cdf0cba65e5367a75dbc3d132e1
- **短哈希**：3747965e9
- **日期**：2025-12-02
- **作者**：Yuya Ebihara
- **提交说明**：Core: Use `@TempDir` in TestTableMetadataParser
- **PR/Issue**：#14732

## 总体目的

这是一次测试基础设施改进，目标是把 `TestTableMetadataParser` 中通过手动 `@AfterEach` 清理临时文件的做法，替换为 JUnit 5 标准的 `@TempDir` 机制。

原测试在 `testGzipCompressionProperty` 方法中，把生成的压缩元数据文件直接写到当前工作目录（文件名为 `"v3" + fileExtension`，例如 `v3.metadata.gz`），并通过 `@AfterEach` 的 `cleanup()` 方法在测试结束后用 `java.nio.file.Files.deleteIfExists(...)` 删除该文件。这种写法有几个缺点：

1. **污染工作目录**：测试运行期间会在工程根目录留下临时文件，若并发执行测试或多个 codec 参数共用同一文件名，可能互相干扰。
2. **清理不健壮**：如果测试在 `@AfterEach` 之前因异常失败（例如断言失败或抛出 `IOException`），`cleanup()` 仍会执行，但如果清理逻辑本身又出错，文件可能残留；同时把"创建"与"清理"分散在两个方法里，逻辑上不内聚。
3. **依赖参数字段 `codecName`**：`cleanup()` 需要重新从 `codecName` 推算出文件名才能删除，这种"重建文件名"的写法脆弱，一旦文件名规则改变就得同步改两处。
4. **import 冗余**：需要额外引入 `java.nio.file.Paths` 和 `org.junit.jupiter.api.AfterEach`。

通过改用 JUnit 5 的 `@TempDir`（参数注入方式），JUnit 框架会在每个测试方法执行前创建一个独立临时目录，并在测试结束后自动回收，从根本上消除上述问题。

## 如何达成设计目的

改动集中在 `core/src/test/java/org/apache/iceberg/TestTableMetadataParser.java` 一个文件。思路是：

- 把 `@TempDir Path tempDir` 作为方法参数注入到 `testGzipCompressionProperty` 中；
- 将输出文件名从 `"v3" + fileExtension` 改为 `tempDir + "/v3" + fileExtension`，使文件落到 JUnit 管理的临时目录下；
- 删除原 `@AfterEach cleanup()` 方法以及不再需要的 `import`（`java.nio.file.Paths`、`org.junit.jupiter.api.AfterEach`），新增 `org.junit.jupiter.api.io.TempDir` 的 import。

由于 `@TempDir` 由 JUnit 自动管理生命周期，无需再写任何清理代码，测试方法体本身的断言与写入逻辑保持不变。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTableMetadataParser.java` (+3/-11 lines)

**修改目的**：用 JUnit 5 的 `@TempDir` 取代手写 `@AfterEach` 临时文件清理。

**工作逻辑**：
具体改动分四块：

1. **import 调整**：删除 `import java.nio.file.Paths;` 与 `import org.junit.jupiter.api.AfterEach;`，新增 `import org.junit.jupiter.api.io.TempDir;`。`Paths` 仅在原 `cleanup()` 里用于构造路径，删除清理逻辑后该 import 不再需要。

2. **方法签名注入临时目录**：将
   ```java
   public void testGzipCompressionProperty() throws IOException {
   ```
   改为
   ```java
   public void testGzipCompressionProperty(@TempDir Path tempDir) throws IOException {
   ```
   `@TempDir` 参数注入方式让每个参数化运行（`@TestTemplate` + `@Parameter codecName`）都拿到独立的临时目录，避免多个 codec 之间的文件冲突。

3. **输出路径指向临时目录**：将
   ```java
   String fileName = "v3" + fileExtension;
   ```
   改为
   ```java
   String fileName = tempDir + "/v3" + fileExtension;
   ```
   这样 `Files.localOutput(fileName)` 写出的压缩元数据文件落在临时目录而非工作目录。注意这里用 `tempDir + "/v3" + ...` 字符串拼接（依赖 `Path.toString()`），与原代码风格保持一致。

4. **删除 `@AfterEach cleanup()` 方法**：原本的
   ```java
   @AfterEach
   public void cleanup() throws IOException {
     Codec codec = Codec.fromName(codecName);
     Path metadataFilePath = Paths.get("v3" + getFileExtension(codec));
     java.nio.file.Files.deleteIfExists(metadataFilePath);
   }
   ```
   被整体移除。这段代码通过 `codecName` 重新推算文件名再删除，现在由 JUnit 框架在测试结束后自动清理整个临时目录，无需再手动管理。其余测试逻辑（`verifyMetadata` 比对 schema/location 等）保持不变。

值得注意的是：原 `cleanup()` 依赖 `codecName` 字段，意味着清理逻辑与参数化运行耦合；改造后 `@TempDir` 与方法参数绑定，框架保证每个参数化调用的临时目录独立，从结构上也更安全。

## 总结

本次提交把 `TestTableMetadataParser` 的临时文件管理从手写 `@AfterEach` 迁移到 JUnit 5 标准的 `@TempDir`，消除了工作目录污染、清理不健壮、文件名重建脆弱等问题，使测试更内聚、更安全地并发执行。属于纯测试基础设施层面的整洁性改进，不改变被测代码与测试覆盖的语义。
