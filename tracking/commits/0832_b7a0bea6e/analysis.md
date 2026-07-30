# 提交 0832：Build: Rename allVersions flag to allModules (#10499)

## 提交信息
- **序号**：0832 / 4088
- **哈希**：b7a0bea6e5cd31b871bd6e038ff78588d80a6655
- **短哈希**：b7a0bea6e
- **日期**：2024-06-15 14:19:51 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Rename allVersions flag to allModules (#10499)
- **PR/Issue**：#10499

## 总体目的

本提交将 Iceberg 构建系统中用于"启用全部模块"的 Gradle 系统属性 `-DallVersions` 重命名为 `-DallModules`。该属性的作用是在构建时启用全部 Flink/Spark/Hive 版本对应的模块组合，而不是仅构建默认版本子集。

提交说明明确指出：原名称 `allVersions` 容易引起误解——它暗示"启用所有版本（包括所有 Scala 版本）"，但实际语义只是"启用所有模块"。具体来说，`allVersions` 并不会为构建启用所有 Scala 版本，它仅是把 `flinkVersions`/`sparkVersions`/`hiveVersions` 设为各自已知的全集，从而让所有集成模块都参与构建。新名称 `allModules` 更直接地表达了"启用全部模块"这一意图，减少维护者与使用者的认知负担。

这是一次纯构建脚手架层面的命名优化，不改变构建产物的内容，也不改变构建逻辑本身，只统一替换属性名及其在文档、CI 工作流中的引用。

## 如何达成设计目的

改动分四个文件同步进行：①`settings.gradle` 中将读取系统属性的判断条件 `System.getProperty("allVersions")` 改为 `System.getProperty("allModules")`，这是属性的"消费端"——当该属性存在时，脚本会把 `knownFlinkVersions`/`knownSparkVersions`/`knownHiveVersions` 全集赋给对应的版本列表属性，从而激活全部模块；②③两处 GitHub Actions 工作流（`java-ci.yml` 的全模块编译任务、`publish-snapshot.yml` 的快照发布任务）把命令行参数 `-DallVersions` 改为 `-DallModules`；④`README.md` 中面向开发者的代码风格修复示例命令同步更新。四个文件保持一致，避免出现属性名不匹配导致构建静默地不启用全部模块的问题。

## 修改详情

### `.github/workflows/java-ci.yml`
**修改目的**：将 CI 中全模块编译步骤的系统属性参数改为新名称。
**工作逻辑**：Java 8 构建任务中，原命令 `./gradlew -DallVersions build -x test -x javadoc -x integrationTest` 改为 `./gradlew -DallModules build -x test -x javadoc -x integrationTest`，使 CI 仍能触发全部 Flink/Spark/Hive 模块的编译。

### `.github/workflows/publish-snapshot.yml`
**修改目的**：将快照发布任务中触发全模块发布的系统属性参数改为新名称。
**工作逻辑**：发布命令 `./gradlew -DallVersions publishApachePublicationToMavenRepository ...` 改为 `./gradlew -DallModules publishApachePublicationToMavenRepository ...`，确保快照发布仍覆盖全部模块产物。第二行使用显式版本列表的发布命令不受影响。

### `README.md`
**修改目的**：同步开发者文档中的代码风格修复示例命令。
**工作逻辑**：将"为所有 Spark/Hive/Flink 版本修复代码风格"的示例从 `./gradlew spotlessApply -DallVersions` 改为 `./gradlew spotlessApply -DallModules`，保持文档与实际属性名一致。

### `settings.gradle`
**修改目的**：将读取"启用全部模块"系统属性的判断条件改为新名称，这是属性的消费核心。
**工作逻辑**：原 `if (null != System.getProperty("allVersions"))` 改为 `if (null != System.getProperty("allModules"))`。当该属性被设置时，脚本把 `knownFlinkVersions`/`knownSparkVersions`/`knownHiveVersions`（在 `gradle.properties` 中定义的全集）分别赋给 `flinkVersions`/`sparkVersions`/`hiveVersions`，使后续模块包含逻辑覆盖所有版本的集成模块。改名后属性名与"启用全部模块"的语义对齐。

## 小结
- **成效**：将易引起误解的构建属性 `allVersions` 重命名为语义更准确的 `allModules`，统一了 `settings.gradle`、CI 工作流与 README 文档中的引用，降低了维护与使用时的认知成本。
- **影响范围**：仅影响构建脚手架（Gradle 设置脚本、GitHub Actions 工作流、README 文档），不触及任何源码或测试逻辑，构建产物内容不变。
- **回迁注意事项**：回迁到 1.4.x 风险低，但需四处同步替换以避免属性名不匹配（若 `settings.gradle` 用新名而 CI 仍传旧名，会导致全模块构建静默退化为默认子集）。回迁时需确认 1.4.x 分支的 CI 工作流与 README 内容是否与 main 一致；若 1.4.x 有独立的发布/CI 配置，也要一并更新。本提交不依赖其他提交，可独立回迁。
