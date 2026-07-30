# 提交 0323：JMH: Improvements to `jmh.gradle` (#9390)

## 提交信息

- **序号**：0323 / 4088
- **哈希**：be4e7d20874d6576ac5a4fd516c815b2548181f7
- **短哈希**：be4e7d208
- **日期**：2024-01-03 18:54:17 -0800
- **作者**：Fokko Driesprong
- **提交说明**：JMH: Improvements to `jmh.gradle` (#9390)
- **PR/Issue**：#9390

## 总体目的

这个提交对 Iceberg 项目的 JMH（Java Microbenchmark Harness）基准测试构建脚本 `jmh.gradle` 进行了清理和升级。Iceberg 使用 JMH 对核心路径（如 core、data、spark 等模块）进行性能基准测试，`jmh.gradle` 负责定义哪些项目参与基准测试、应用 JMH 插件并配置 JMH 版本与运行参数。本提交包含三处改动：修正一处拼写错误、整理项目列表的初始化方式、以及升级 JMH 版本。

第一处是拼写错误修正。原脚本在 JDK 版本校验的异常信息中写了 "The JMH benchamrks must be run with..."，其中 "benchamrks" 是 "benchmarks" 的拼写错误。虽然这只是异常提示文本，不影响正常运行逻辑，但作为面向开发者的错误信息，拼写错误会影响专业性，且在排查问题时可能造成困扰，因此本提交予以修正。

第二处是项目列表初始化的整理。原脚本在定义 `jmhProjects` 列表时只初始化为 `[project(":iceberg-core")]`，然后在所有 Spark 版本条件分支之后，再通过 `jmhProjects.add(project(":iceberg-data"))` 无条件地追加 `:iceberg-data`。这种写法在功能上没有问题（因为 `:iceberg-data` 总是被加入），但将一个非条件性项目放在条件分支之后单独追加，逻辑上不够清晰。本提交将 `:iceberg-data` 直接合并到初始列表 `[project(":iceberg-core"), project(":iceberg-data")]`，并删除了末尾的 `add` 调用，使"无条件参与的项目"和"按 Spark 版本条件参与的项目"在代码结构上分得更清楚。

第三处是 JMH 版本从 1.32 升级到 1.37。这是一个跨 5 个次版本的升级（1.33、1.34、1.35、1.36、1.37），期间 JMH 修复了若干 bug 并对基准测试基础设施做了改进。升级版本可以使基准测试工具链保持最新，获得更稳定的测量结果和更好的兼容性。

## 如何达成设计目的

整体改动是对 `jmh.gradle` 的局部清理与版本号更新，不涉及 JMH 运行逻辑或基准测试用例本身的改动。拼写修正直接改字符串；项目列表整理通过调整初始化表达式与删除末尾追加语句实现，行为等价；JMH 版本升级仅修改 `jmh.jmhVersion` 配置值。由于这些改动都是配置层面的，且 `:iceberg-data` 的加入方式变更在语义上等价，因此不会影响基准测试的执行结果，只是让脚本更整洁、工具链更新。

## 修改详情

### `jmh.gradle`

**修改目的**：修正拼写、整理项目列表初始化、升级 JMH 版本。

**工作逻辑**：

1. JDK 版本校验的异常信息由 `"The JMH benchamrks must be run with JDK 8 or JDK 11 or JDK 17"` 改为 `"The JMH benchmarks must be run with JDK 8 or JDK 11 or JDK 17"`，将 "benchamrks" 修正为 "benchmarks"。

2. `jmhProjects` 列表的初始化由 `def jmhProjects = [project(":iceberg-core")]` 改为 `def jmhProjects = [project(":iceberg-core"), project(":iceberg-data")]`，将 `:iceberg-data` 提前到初始列表中。相应地，删除了原本位于 Spark 版本条件分支之后的 `jmhProjects.add(project(":iceberg-data"))` 行。这一改动在行为上等价——`:iceberg-data` 仍然是无条件加入的——但代码组织更清晰：初始列表包含所有无条件参与的项目（core、data），后续的条件分支只处理按 Spark 版本可选的项目（spark 各版本的 core 与 extensions）。

3. JMH 插件配置块中的 `jmhVersion = '1.32'` 改为 `jmhVersion = '1.37'`，将 JMH 依赖版本升级到 1.37，使基准测试工具链保持较新版本。

## 小结

本提交对 `jmh.gradle` 进行了三项改进：修正异常信息中的拼写错误、将 `:iceberg-data` 的加入方式整理到初始项目列表中以提升代码清晰度、并将 JMH 版本从 1.32 升级到 1.37。这些改动属于构建脚本维护与工具链升级，不改变基准测试的执行行为，但使脚本更规范、工具链更现代。
