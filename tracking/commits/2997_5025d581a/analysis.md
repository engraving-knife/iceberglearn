# 提交 2997：Build: Improvements around applying spotless for Scala (#14798)

## 提交信息

- **序号**：2997 / 4088
- **哈希**：5025d581ac292b04cbaa99fbe660277ddbceb9f1
- **短哈希**：5025d581a
- **日期**：2025-12-10 15:51:25 -0800
- **作者**：Xianyang Liu
- **提交说明**：Build: Improvements around applying spotless for Scala (#14798)
- **PR/Issue**：#14798

## 总体目的

Iceberg 通过 Gradle 插件 `com.diffplug.spotless` 来统一代码风格，其中 Scala 子项目（主要是 `iceberg-spark-*`）使用 `scalafmt` 做格式化，并且为 Scala 2.12 与 2.13 配置了不同的 `.scala212fmt.conf` / `.scala213fmt.conf` 规则文件。原先的判断方式是按"项目名后缀"来分流：

```groovy
if (project.name.startsWith("iceberg-spark") && project.name.endsWith("2.13")) { ... }
else if (project.name.startsWith("iceberg-spark") && project.name.endsWith("2.12")) { ... }
```

这种基于项目名后缀的判断有一个明显缺陷：当构建脚本通过 `-PscalaVersion=2.13` 之类的方式动态切换 Scala 版本，或者项目命名约定不严格以后缀区分版本时（例如新的模块命名规范、聚合模块等），spotless 就无法正确选到对应的 scalafmt 配置，甚至完全跳过 Scala 格式化，导致 CI 上风格检查不一致或漏检。

同时，原先 `scalaVersion` 这一关键变量只在 `pluginManager.withPlugin('scala')` 块内部声明，而 spotless 块也需要它来判定目标 Scala 版本，作用域不匹配。本提交的目的就是让 spotless 对 Scala 的处理能够：1) 统一通过 `scalaVersion` 系统属性来判断当前目标 Scala 版本，而不是依赖项目名后缀；2) 把 `scalaVersion` 提升到 `subprojects` 顶层声明，使其在 spotless 和 scala 插件块中都能访问；3) 在版本不匹配 2.12/2.13 时安全地不应用 scalafmt，避免误用规则文件。

## 如何达成设计目的

整体思路是"用同一个 `scalaVersion` 系统属性驱动 Scala 相关配置"，避免项目名后缀这种间接信号。改动只在 `baseline.gradle` 中进行：

1. 在 `subprojects` 顶层、所有 `pluginManager.withPlugin` 块之前声明 `String scalaVersion`，从 `System.getProperty("scalaVersion")` 取值，缺省回退到 `defaultScalaVersion`。
2. spotless 块中，对 `iceberg-spark` 开头的项目不再判断后缀，而是先判断 `scalaVersion` 是否以 `2.12` 或 `2.13` 开头，据此选择 `scalafmtConfigFile`，再在非空时统一应用 `scalafmt("3.9.7").configFile(scalafmtConfigFile)`。
3. 从 `withPlugin('scala')` 块中删除局部 `scalaVersion` 声明，复用顶层变量。

## 修改详情

### `baseline.gradle` (+15/-12 lines)

**修改目的**：让 spotless 的 Scala 规则按 `scalaVersion` 属性选择，而非项目名后缀；并把 `scalaVersion` 提升为 `subprojects` 级共享变量。

**工作逻辑**：

新增顶层声明：

```groovy
String scalaVersion = System.getProperty("scalaVersion") != null ? System.getProperty("scalaVersion") : System.getProperty("defaultScalaVersion")
```

这一行原本位于 `pluginManager.withPlugin('scala')` 内部，现在上移到 spotless 块之前，让两个 `withPlugin` 块都能共享。语义上：构建时通过 `-DscalaVersion=2.13` 指定要格式化的 Scala 版本，未指定则回退到 `defaultScalaVersion`（项目默认 Scala 版本）。

spotless 配置块从原先的"两段硬编码 if/else"重构为"先按 `scalaVersion` 选 config 文件、再统一应用"：

```groovy
if (project.name.startsWith("iceberg-spark")) {
  String scalafmtConfigFile = null
  if (scalaVersion?.startsWith("2.12")) {
    scalafmtConfigFile = "$rootDir/.baseline/scala/.scala212fmt.conf"
  } else if (scalaVersion?.startsWith("2.13")) {
    scalafmtConfigFile = "$rootDir/.baseline/scala/.scala213fmt.conf"
  }

  if (scalafmtConfigFile != null) {
    scala {
      target 'src/**/*.scala'
      scalafmt("3.9.7").configFile(scalafmtConfigFile)
      licenseHeaderFile "$rootDir/.baseline/copyright/copyright-header-java.txt", "package"
    }
  }
}
```

这种写法的好处：
- `scalafmtConfigFile` 只在一个地方被消费，消除了原先两段几乎重复的 `scala { ... }` 块，便于后续新增 Scala 版本（例如未来 Scala 3）。
- `scalaVersion?.startsWith(...)` 使用了 Groovy 的安全导航符，当 `scalaVersion` 为 `null` 时不会 NPE，而是直接跳过格式化配置，行为更稳健。
- 当 `iceberg-spark` 项目既不是 2.12 也不是 2.13（例如临时构建其他版本，或某模块尚未声明 Scala 版本）时，`scalafmtConfigFile` 保持 `null`，外层 `if (scalafmtConfigFile != null)` 保证不会误用配置文件，也不会因为缺少规则而把 Scala 文件交给默认 scalafmt 处理。

`withPlugin('scala')` 块中删除局部 `scalaVersion` 声明后，`scalaCompile.scalaCompileOptions.additionalParameters` 等处对 `scalaVersion` 的引用仍然有效（使用顶层变量），同时消除了"两处声明同名变量"的歧义。

## 总结

该提交重构了 `baseline.gradle` 中 spotless 对 Scala 代码的格式化配置，把"按项目名后缀判断版本"改为"按 `scalaVersion` 系统属性判断版本"，并将 `scalaVersion` 变量提升到 `subprojects` 顶层供 spotless 和 scala 编译块共享。改动让 Scala 版本路由更准确、更易扩展，并消除了重复代码与潜在的空指针风险，对 Iceberg 多 Scala 版本并行构建的 CI 流程是一个稳健性提升。
