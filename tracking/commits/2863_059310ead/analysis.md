# 提交 2863：Build: Restore JVM 11 for build-checks and build-javadoc (#14534)

## 提交信息

- **序号**：2863 / 4088
- **哈希**：059310ead702814e5d95116210b9af77b29f6b94
- **短哈希**：059310ead
- **日期**：2025-11-10 15:42:23 -0800
- **作者**：Manu Zhang
- **提交说明**：Build: Restore JVM 11 for build-checks and build-javadoc (#14534)
- **PR/Issue**：#14534

## 总体目的

这个提交恢复了 CI 构建流程中 JVM 11 的支持。此前 build-checks 和 build-javadoc 这两个 CI 任务的 JVM 矩阵从 `[11, 17, 21]` 被改为了 `[17, 21]`（可能是之前某次提交移除了 JVM 11），导致项目在 JVM 11 上的构建状态不再被验证。由于 Iceberg 项目仍然支持 JVM 11 作为最低运行时版本，需要在 CI 中恢复 JVM 11 的构建检查。

但 Spark 4.0 模块要求 JDK 17 或 21，因此在 JVM 11 环境下构建时需要跳过 Spark 4.0 模块。此前代码在非 JDK 17/21 环境下会直接抛出 `GradleException` 终止构建，这导致 JVM 11 的 CI 构建失败。该提交将此行为改为仅输出警告日志并跳过 Spark 4.0 相关任务，使 JVM 11 构建能顺利完成（不包含 Spark 4.0 模块）。

## 如何达成设计目的

通过两个层面的修改来实现：

1. **CI 配置恢复**：在 GitHub Actions 工作流中将 build-checks 和 build-javadoc 任务的 JVM 矩阵恢复为包含 11。
2. **Spark 4.0 构建脚本调整**：将 Spark 4.0 构建脚本中的硬性 JDK 版本检查从抛出异常改为输出警告并跳过任务，使 JVM 11 环境下能跳过 Spark 4.0 模块而不中断整个构建。

## 修改详情

### `.github/workflows/java-ci.yml` (+2/-2 lines)

**修改目的**：恢复 build-checks 和 build-javadoc CI 任务的 JVM 11 矩阵。

**工作逻辑**：将两个 CI 任务的 JVM 矩阵从 `[17, 21]` 改回 `[11, 17, 21]`，确保项目在 JVM 11 上的构建检查和 Javadoc 生成得到验证。build-checks 任务用于运行额外的构建检查（如 linting、代码风格等），build-javadoc 任务用于生成 API 文档。

### `spark/v4.0/build.gradle` (+5/-3 lines)

**修改目的**：使 Spark 4.0 模块在 JVM 11 环境下跳过构建而非抛出异常终止。

**工作逻辑**：

1. **JDK 版本检查改为非致命**：将原来的 `throw new GradleException(...)` 改为 `logger.warn(...)`。原来在非 JDK 17/21 环境下会抛出异常终止 Gradle 构建，现在仅输出警告日志。同时将判断结果存储到 `javaVersionSupported` 布尔变量中。

2. **跳过 Spark 4.0 任务**：在 `configure(sparkProjects)` 块中添加 `tasks.configureEach { onlyIf { javaVersionSupported } }`，这意味着当 JDK 版本不受支持（即 JVM 11）时，所有 Spark 4.0 相关任务都会被跳过（`onlyIf` 返回 false 时 Gradle 会跳过该任务），而不是导致整个构建失败。

这样在 JVM 11 环境下，CI 构建可以完成核心模块和 Spark 3.x 模块的构建检查，仅跳过 Spark 4.0 模块。

## 总结

这个提交恢复了 CI 中 JVM 11 的构建检查支持，同时调整了 Spark 4.0 构建脚本使其在 JVM 11 环境下优雅跳过而非终止构建。修改确保了 Iceberg 项目在最低支持版本（JVM 11）上的构建状态得到持续验证，同时不影响需要更高 JDK 版本的 Spark 4.0 模块在 JDK 17/21 环境下的正常构建。
