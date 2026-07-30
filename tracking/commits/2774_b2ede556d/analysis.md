# 提交 2774：Build: Add unused imports check for scala code (#14344)

## 提交信息

- **序号**：2774 / 4088
- **哈希**：b2ede556d91ab593830e76cbffe7b54b51b2eca9
- **短哈希**：b2ede556d
- **日期**：2025-10-20 18:36:34 -0700
- **作者**：jackylee
- **提交说明**：Build: Add unused imports check for scala code (#14344)
- **PR/Issue**：#14344

## 总体目的

本提交为 Iceberg 项目的 Scala 代码构建流程添加未使用 import 检查。

Iceberg 项目中包含 Scala 代码（主要在 Spark 模块中），但构建脚本之前没有对 Scala 代码进行未使用 import 的检查。Java 代码已有类似的检查机制，但 Scala 代码缺失。未使用的 import 不仅影响代码整洁度，还可能造成不必要的依赖耦合。

本提交在 `baseline.gradle` 中为应用了 `scala` 插件的子项目添加编译器选项，根据 Scala 版本（2.12 或 2.13）启用不同级别的未使用 import 检查：Scala 2.12 只能启用警告（因为不支持将单个 unused import 作为 error），Scala 2.13 则可以将 unused imports 作为编译错误。

## 如何达成设计目的

通过 Gradle 的 `pluginManager.withPlugin('scala')` 钩子，在所有应用了 scala 插件的子项目中配置 `ScalaCompile` 任务的编译器参数：

1. **获取 Scala 版本**：从系统属性读取 `scalaVersion`，若未设置则回退到 `defaultScalaVersion`。

2. **Scala 2.12 配置**：添加 `-Ywarn-unused:imports` 参数。Scala 2.12 的编译器不支持将 unused imports 单独作为 error，只能产生警告。

3. **Scala 2.13 配置**：添加两个参数：`-Wconf:cat=unused:error`（将 unused 类别的警告提升为 error）和 `-Wunused:imports`（启用 unused imports 检查）。这样未使用的 import 会导致编译失败，强制开发者清理。

## 修改详情

### `baseline.gradle` (+18/-0 lines)

**修改目的**：为 Scala 代码添加未使用 import 的编译检查。

**工作逻辑**：在 `subprojects` 块末尾新增 `pluginManager.withPlugin('scala')` 块。在该块内，读取 Scala 版本，然后通过 `tasks.withType(ScalaCompile).configureEach` 配置所有 Scala 编译任务。根据版本前缀判断：若为 "2.12" 则添加 `-Ywarn-unused:imports`（仅警告）；若为 "2.13" 则添加 `-Wconf:cat=unused:error` 和 `-Wunused:imports`（编译错误）。

## 总结

本提交为 Iceberg 的 Scala 代码添加了未使用 import 检查，填补了 Java 代码已有但 Scala 代码缺失的代码质量检查空白。针对 Scala 2.12 和 2.13 不同的编译器能力，分别采用警告和错误两种级别。这有助于保持 Scala 代码的整洁，避免不必要的 import 污染。
