# 提交 3445：Spark: Remove Spark 4.x Java version skip guards (#15737)

## 提交信息

- **序号**：3445 / 4088
- **哈希**：acc2c665bf924965bb5408a1d33a88766156fa27
- **短哈希**：acc2c665bf
- **日期**：2026-03-23 09:59:08 -0700
- **作者**：Manu Zhang
- **提交说明**：Spark: Remove Spark 4.x Java version skip guards (#15737)
- **PR/Issue**：#15737

## 总体目的

移除 Spark 4.0 和 4.1 模块构建脚本中的 Java 版本跳过保护（skip guards）。这些保护之前会在 Java 版本不是 17 或 21 时跳过 Spark 4.x 的构建任务。移除这些保护意味着 Spark 4.x 模块的构建不再因 Java 版本不匹配而自动跳过。

## 如何达成设计目的

- 从 `spark/v4.0/build.gradle` 和 `spark/v4.1/build.gradle` 中删除 Java 版本检测和跳过逻辑
- 删除 `tasks.configureEach { onlyIf { javaVersionSupported } }` 配置

## 修改详情

### `spark/v4.0/build.gradle` (+0/-9 lines)

**修改目的**：移除 Spark 4.0 模块的 Java 版本跳过保护。

**工作逻辑**：
- 删除以下代码块：
  ```groovy
  JavaVersion javaVersion = JavaVersion.current()
  Boolean javaVersionSupported = javaVersion == JavaVersion.VERSION_17 || javaVersion == JavaVersion.VERSION_21
  if (!javaVersionSupported) {
    logger.warn("Skip Spark 4.0 build which requires JDK 17 or 21 but was executed with JDK " + javaVersion)
  }
  ```
- 删除任务级别的跳过配置：
  ```groovy
  tasks.configureEach {
    onlyIf { javaVersionSupported }
  }
  ```

### `spark/v4.1/build.gradle` (+0/-9 lines)

**修改目的**：移除 Spark 4.1 模块的 Java 版本跳过保护。

**工作逻辑**：
- 与 Spark 4.0 完全相同的删除操作
- 移除 Java 版本检测逻辑和任务跳过配置

## 总结

该提交移除了 Spark 4.0 和 4.1 模块构建脚本中的 Java 版本跳过保护。之前当 Java 版本不是 17 或 21 时会自动跳过这些模块的构建，现在移除了这一限制，使得 Spark 4.x 模块在任意 Java 版本下都会执行构建任务。
