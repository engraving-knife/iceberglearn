# 提交 2271：Build: Require JDK 17 / 21 for Spark 4.0 support (#13381)

## 提交信息

- **序号**：2271 / 4088
- **哈希**：b2e564405ef19ca4a65bce36176e736b3538ded7
- **短哈希**：b2e564405
- **日期**：2025-06-25 15:42:06 +0200
- **作者**：Hongze Zhang
- **提交说明**：Build: Require JDK 17 / 21 for Spark 4.0 support (#13381)
- **PR/Issue**：#13381

## 总体目的

本提交为 Spark 4.0 模块的构建增加 JDK 版本校验，要求构建环境必须使用 JDK 17 或 JDK 21，否则直接抛出 GradleException 终止构建。Spark 4.0 本身要求 JDK 17 作为最低运行时版本，且官方推荐 JDK 17 或 21。如果开发者在不兼容的 JDK 版本（如 JDK 11）下尝试构建 Spark 4.0 模块，可能会遇到难以理解的编译错误或运行时问题。

通过在构建脚本早期就进行显式的 JDK 版本检查并抛出清晰的错误信息，能够帮助开发者快速定位环境问题，避免在构建过程深处才暴露出晦涩的错误。这是提升开发者体验和 CI 可靠性的防御性编程实践。

## 如何达成设计目的

- 在 `spark/v4.0/build.gradle` 文件开头（紧随版本变量定义之后）新增 JDK 版本检查逻辑。
- 使用 `JavaVersion.current()` 获取当前运行 Gradle 的 JDK 版本。
- 判断当前版本是否为 `VERSION_17` 或 `VERSION_21`，若都不是则抛出 `GradleException`，错误信息明确指出要求 JDK 17 或 21 以及实际检测到的版本。
- 使用 fail-fast 策略，在构建配置阶段即终止，而非等到编译执行阶段。

## 修改详情

### `spark/v4.0/build.gradle` (+5/-0 lines)

**修改目的**：在 Spark 4.0 构建脚本中增加 JDK 版本前置校验。

**工作逻辑**：新增代码如下逻辑：

```groovy
JavaVersion javaVersion = JavaVersion.current()
if (javaVersion != JavaVersion.VERSION_17 && javaVersion != JavaVersion.VERSION_21) {
  throw new GradleException("Spark 4.0 build requires JDK 17 or 21 but was executed with JDK " + javaVersion)
}
```

这段代码在 Gradle 配置阶段执行（脚本加载时），`JavaVersion.current()` 返回当前 JVM 的 Java 版本枚举值。通过严格相等比较（仅允许 17 或 21），排除了 JDK 8、11 等较旧版本以及 23 等更新但未经测试的版本。抛出的异常信息包含期望版本和实际版本，便于开发者诊断。这段检查放在 `sparkProjects` 定义之前，确保后续所有 Spark 4.0 子项目的配置都在正确的 JDK 环境下进行。

## 总结

本提交通过 5 行防御性代码为 Spark 4.0 构建增加了 JDK 版本前置校验，是 Spark 4.0 支持工作的一部分。这种 fail-fast 的校验方式能有效避免因 JDK 版本不匹配导致的深层构建错误，提升了开发者和 CI 环境的诊断效率。
