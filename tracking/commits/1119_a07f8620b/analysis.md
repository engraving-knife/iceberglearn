# 提交 1119：Kafka Connect: Disable publish tasks in runtime project (#11032)

## 提交信息

- **序号**：1119 / 4088
- **哈希**：a07f8620b97d4af7fd1e843909b3c12ddd1f77aa
- **短哈希**：a07f8620b
- **日期**：2024-08-29（Thu Aug 29 20:48:25 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Disable publish tasks in runtime project (#11032)
- **PR/Issue**：#11032

## 总体目的

Iceberg 的 `kafka-connect` 模块下有一个 `iceberg-kafka-connect-runtime` 子项目，用于打包一个可独立运行的 Kafka Connect connector runtime 分发（包含所有依赖的 fat jar / tar / zip 等），方便用户在 Kafka Connect worker 中直接部署。该 runtime 项目本身**不发布任何 Maven artifact**——它只是聚合依赖的分发包，Maven 制品（jar + pom）由父项目 `iceberg-kafka-connect` 与其他子项目发布。

然而，Gradle 的 `maven-publish` 插件在配置了发布能力的项目中会自动生成一系列 `publish*` 任务（如 `publishMavenJavaPublicationToMavenLocal`、`publishToMavenLocal` 等）。runtime 项目继承了父项目的发布配置，导致这些 publish 任务在 runtime 项目中也存在，运行时会被错误地尝试发布 runtime 项目的"制品"（实际上是空 jar 或分发包），与发布流程冲突，可能产生无意义的 Maven artifact 或在 CI 上失败。

本提交在 `iceberg-kafka-connect-runtime` 项目的 `build.gradle` 中显式禁用所有 `publishing` 组的任务，避免误发布。

## 如何达成设计目的

在 `kafka-connect/build.gradle` 中 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 配置块内，使用 `project.afterEvaluate { ... }` 在项目评估完成后遍历所有任务，把 `group == 'publishing'` 的任务全部 `enabled = false`。

之所以用 `afterEvaluate`，是因为 publish 任务由 `maven-publish` 插件在配置阶段动态创建，必须在所有插件应用完毕、任务都创建完成后才能匹配到。`matching { it.group == 'publishing' }` 是 Gradle 提供的任务集合过滤方法，按 `Task.group` 属性筛选（所有 maven-publish 创建的发布任务 group 均为 `publishing`）。

这是纯构建脚本调整，无任何运行时代码改动。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：禁用 `iceberg-kafka-connect-runtime` 项目的所有 publishing 任务。

**工作逻辑**：在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 块内、既有的 `tasks.jar.enabled = false`、`tasks.distTar.enabled = false` 等禁用语句附近，新增：

```groovy
// there are no Maven artifacts so disable publishing tasks...
project.afterEvaluate {
  project.tasks.matching { it.group == 'publishing' }.each {it.enabled = false}
}
```

- `project.afterEvaluate { ... }`：延迟到项目评估完成后执行，确保 `maven-publish` 插件已创建所有 publish 任务。
- `project.tasks.matching { it.group == 'publishing' }`：匹配所有 `group` 属性为 `"publishing"` 的任务（maven-publish 插件创建的任务均归入该 group）。
- `.each { it.enabled = false }`：把匹配到的每个任务设为禁用，使其在 `./gradlew publish*` 或 CI 发布流程中被跳过。

注释 `// there are no Maven artifacts so disable publishing tasks...` 说明原因：runtime 项目无 Maven 制品。该改动与既有的 `tasks.jar.enabled = false`、`tasks.distTar.enabled = false` 等保持风格一致，都是禁用不适用于 runtime 项目的默认任务。

## 小结

- **成效**：`iceberg-kafka-connect-runtime` 项目不再生成可执行的 publish 任务，避免在发布流程中误发布无意义的 Maven artifact 或在 CI 上失败；与该项目"仅用于打包 runtime 分发、不发布 Maven 制品"的定位一致。
- **影响范围**：1 个文件、5 行新增，纯构建脚本调整，无运行时代码改动；不影响其他模块的发布行为。
- **回迁到 1.4.x 的注意事项**：这是 Kafka Connect 模块发布流程的小修复，与 1.4.x 关系取决于该模块在 1.4.x 中的状态。`kafka-connect` 是 Iceberg 较新的模块（main 上较晚引入），1.4.x 维护分支很可能**尚未包含 kafka-connect 模块**。若 1.4.x 不含该模块，则本提交无回迁对象，**无需回迁**；若 1.4.x 已含 kafka-connect 且同样存在 runtime 项目误发布问题，则可回迁这 5 行 build.gradle 改动，风险极低（仅禁用任务，不影响产物）。回迁前应先确认 1.4.x 是否存在 `kafka-connect/build.gradle` 中的 `:iceberg-kafka-connect:iceberg-kafka-connect-runtime` 项目定义。
