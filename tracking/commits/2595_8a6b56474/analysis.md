# 提交 2595：Cleanup TestS3OutputStream integration tests by delegating file cleanup to JUnit (#13983)

## 提交信息

- **序号**：2595 / 4088
- **哈希**：8a6b56474037f846878a3a6e24f48c1fce4cc004
- **短哈希**：8a6b56474
- **日期**：2025-09-04 09:58:31 -0700
- **作者**：Anatoly Popov
- **提交说明**：Cleanup TestS3OutputStream integration tests by delegating file cleanup to JUnit (#13983)
- **PR/Issue**：#13983（关联 #13506）

## 总体目的

本次提交重构了 `TestS3OutputStream` 集成测试中的临时文件管理逻辑，将手动创建和清理临时目录的代码替换为 JUnit 5 的 `@TempDir` 机制，使测试更简洁且更可靠。

原有的测试代码存在几个问题：
1. 临时目录在字段初始化时通过 `Files.createTempDirectory` 创建，如果构造函数抛异常可能导致目录未清理。
2. `newTmpDirectory` 硬编码为 `/tmp/newStagingDirectory`，这在多用户共享环境或并行测试时可能冲突，且在不同操作系统上不一定可用。
3. 手动的 `@AfterEach` 清理逻辑只删除了 `newTmpDirectory`，没有清理 `tmpDir`，存在残留文件。
4. 构造函数声明抛出 `IOException`，增加了不必要的检查异常传播。

通过使用 JUnit 5 的 `@TempDir` 注解，JUnit 框架会自动管理临时目录的创建和清理，解决了上述所有问题。

## 如何达成设计目的

1. 将 `tmpDir` 和 `newTmpDirectory` 字段从手动初始化改为使用 `@TempDir` 注解，由 JUnit 自动管理。
2. 移除 `@AfterEach` 方法中手动删除临时目录的逻辑。
3. 移除构造函数中的 `throws IOException` 声明（因为不再需要在字段初始化时做 IO 操作）。
4. 在使用 `newTmpDirectory` 作为 staging directory 时，将其转为字符串（`newTmpDirectory.toString()`）。

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3OutputStream.java` (+5/-14 lines)

**修改目的**：用 JUnit `@TempDir` 替换手动临时目录管理。

**工作逻辑**：

1. **导入变更**：移除 `java.io.File` 和 `@AfterEach` 的导入，新增 `@TempDir` 和 `java.nio.file.Path`（Path 已有）的导入。

2. **字段变更**：
   - 原：`private final Path tmpDir = Files.createTempDirectory("s3fileio-test-");`
     新：`@TempDir private static Path tmpDir = null;`
   - 原：`private final String newTmpDirectory = "/tmp/newStagingDirectory";`
     新：`@TempDir private Path newTmpDirectory;`
   两个字段都改为由 JUnit 注入临时目录路径，框架保证唯一性和清理。

3. **构造函数变更**：移除 `throws IOException` 声明，因为字段初始化不再涉及 IO 操作。

4. **移除 `@AfterEach after()` 方法**：原本该方法手动删除 `/tmp/newStagingDirectory` 目录，现在由 JUnit 自动清理，不再需要。

5. **使用处变更**：在 `testStagingDirectoryCreation` 中，`newTmpDirectory` 从 String 变为 Path，需要调用 `.toString()` 传给配置。

## 总结

这是一个测试基础设施改进提交，通过采用 JUnit 5 的标准 `@TempDir` 机制简化了临时文件管理。这减少了样板代码、避免了资源泄漏、提高了测试在多环境下的兼容性（不再依赖硬编码的 `/tmp` 路径）。关联的 #13506 是一个更广泛的测试清理工作。
