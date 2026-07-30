# 提交 0846：Remove redundant `-XX:+IgnoreUnrecognizedVMOptions` (#10475)

## 提交信息
- **序号**：0846 / 4088
- **哈希**：ae6f9066eefd1043d3f389f74f05e3279680b65b
- **短哈希**：ae6f9066e
- **日期**：2024-06-17（Mon Jun 17 21:18:31 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Remove redundant `-XX:+IgnoreUnrecognizedVMOptions` (#10475)
- **PR/Issue**：#10475

## 总体目的

本提交移除 `build.gradle` 中针对 Java 17 构建场景冗余设置的 `-XX:+IgnoreUnrecognizedVMOptions` JVM 启动参数。

在 Iceberg 的构建脚本中，针对不同 Java 版本会设置不同的 JVM 启动参数（`extraJvmArgs`），用于编译与测试时的 JVM 行为调优。其中 Java 17 分支显式添加了 `-XX:+IgnoreUnrecognizedVMOptions`，其作用是让 JVM 在遇到无法识别的启动参数时不报错直接忽略。

提交作者认为，由于 `extraJvmArgs` 列表本身就是在已知 Java 版本下显式列出的参数，所有这些参数都应当被支持；引入 `-XX:+IgnoreUnrecognizedVMOptions` 等于在掩盖可能存在的错误配置：如果某个 `--add-opens` 参数在当前 JDK 版本上不被识别，本应及早暴露，而非被静默忽略。

总体而言，这是一个构建脚本的小幅清理提交，旨在增强 JVM 启动参数配置的严格性，便于在 Java 版本切换或 JDK 供应商差异时尽早发现参数兼容问题。

## 如何达成设计目的

提交直接修改 `build.gradle` 的 Java 17 分支（`else if (JavaVersion.current() == JavaVersion.VERSION_17)`），从 `extraJvmArgs` 列表中删除一项 `-XX:+IgnoreUnrecognizedVMOptions` 字符串。其余参数（`--add-opens` 系列）保持不变。

由于 Java 8 与 Java 11 分支的 `extraJvmArgs` 为空列表 `[]`，本次修改仅影响在 Java 17 环境下的构建与测试 JVM 启动行为。

## 修改详情

### `build.gradle`
**修改目的**：从 Java 17 构建的 `extraJvmArgs` 列表中删除冗余的 `-XX:+IgnoreUnrecognizedVMOptions`。

**工作逻辑**：
- 上下文为 `JavaVersion.VERSION_17` 分支，此处为 Java 17 环境显式列出多个 `--add-opens` 参数，用于打破 JDK 模块封装以兼容旧版反射调用。
- 修改前：
  ```groovy
  project.ext.extraJvmArgs = ["-XX:+IgnoreUnrecognizedVMOptions",
                              "--add-opens", "java.base/java.io=ALL-UNNAMED",
                              ...]
  ```
- 修改后：
  ```groovy
  project.ext.extraJvmArgs = ["--add-opens", "java.base/java.io=ALL-UNNAMED",
                              ...]
  ```
- 删除 `-XX:+IgnoreUnrecognizedVMOptions` 后，JVM 启动时若遇到列表中任何无法识别的参数（如不存在的 `--add-opens` 目标模块），将直接报错，而非被静默忽略，便于开发者快速定位配置问题。

## 小结
- **成效**：构建脚本更严格，移除掩盖错误的 JVM 参数；Java 17 下任何无法识别的启动参数会立即暴露。
- **影响范围**：仅 `build.gradle` 一行变更，影响 Java 17 环境下的构建与测试 JVM 启动；不影响 Java 8 / Java 11 环境，也不影响产物本身。
- **回迁注意事项**：回迁到 1.4.x 较简单，可直接复用相同 diff。需注意当前 1.4.x 分支 `build.gradle` 中 Java 17 分支仍保留 `-XX:+IgnoreUnrecognizedVMOptions`（参见 build.gradle 第 70 行），回迁时需确认所在分支仍显式列出 Java 17 的 `--add-opens` 参数。由于仅移除单行，冲突风险低；若 1.4.x 上游构建在多种 JDK 17 发行版（如 Temurin / Zulu / Corretto）下均通过验证，回迁可放心进行。
