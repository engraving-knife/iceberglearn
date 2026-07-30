# 提交 3342：Build, Kafka-Connect: Disable publishing for empty grouping project (#15496)

## 提交信息

- **序号**：3342 / 4088
- **哈希**：88d460425ab6a0e4c938e90665b4b772d7b57c41
- **短哈希**：88d460425
- **日期**：2026-03-03 09:37:26 -0800
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Build, Kafka-Connect: Disable publishing for empty grouping project (#15496)
- **PR/Issue**：#15496

## 总体目的

本提交禁止 kafka-connect 模块中空的"分组项目"（grouping project）参与发布，避免在发布流程中为该无源码的父项目生成空构件或导致发布失败。

背景是：`kafka-connect/build.gradle` 是一个分组构建文件，它本身不包含任何源代码或构件，只是通过 `project(':iceberg-kafka-connect:iceberg-kafka-connect-events')`、`project(':iceberg-kafka-connect:iceberg-kafka-connect')` 等块定义若干子项目。然而 Gradle 的构建配置（通常在根 `build.gradle` 或 `settings.gradle` 中通过 `allprojects`/`subprojects` 统一应用 `maven-publish` 等发布插件）会为包括该分组项目在内的所有项目都生成发布任务（如 `publish`、`publishToMavenLocal`、`generatePomFileFor...Publication` 等）。这些发布任务的 `group` 属性被 Gradle 标记为 `"publishing"`。

由于分组项目本身没有任何产物（无 jar、无源码），尝试发布会生成空的 POM 或空的 jar 构件，既污染 Maven 仓库，也可能在 Apache 发布流程（如 `publishToMavenLocal` 或上传到 Maven Central 的 staging）中因校验失败而中断。本提交通过在该分组项目级别禁用所有 `publishing` 分组的任务，确保只有真正含产物的子项目才参与发布。

## 如何达成设计目的

在 `kafka-connect/build.gradle` 顶部新增一个 `afterEvaluate` 块，在其中用 `tasks.matching { it.group == 'publishing' }.each { it.enabled = false }` 把当前项目（即分组项目 `:iceberg-kafka-connect`）下所有属于 `publishing` 分组的任务置为禁用。使用 `afterEvaluate` 是因为发布任务通常由插件在配置阶段创建，需等项目评估完成后再匹配才能确保任务已存在；匹配条件 `it.group == 'publishing'` 精确命中 Gradle `maven-publish`/`ivy-publish` 插件注册的发布类任务，不会误伤编译、测试等其他任务。由于该代码位于 `kafka-connect/build.gradle` 的顶层（不在某个 `project(':...:...') {}` 块内），其作用域仅限分组项目自身，不影响其子项目的发布能力。

## 修改详情

### `kafka-connect/build.gradle` (+5 lines)

**修改目的**：禁用空的 kafka-connect 分组项目的所有发布任务。

**工作逻辑**：
在文件 License 头之后、各子项目 `project(...) {}` 块之前，新增如下代码：
```groovy
// disable publishing from empty grouping project
afterEvaluate {
  tasks.matching { it.group == 'publishing' }.each { it.enabled = false }
}
```
`afterEvaluate` 闭包在当前项目配置评估完成后执行，此时 `maven-publish` 等插件已注册好 `publish`、`publishToMavenLocal`、`generatePomFileFor*`、`generateMetadataFileFor*` 等任务（这些任务的 `group` 均为 `"publishing"`）。`tasks.matching { it.group == 'publishing' }` 筛选出这些任务并逐个 `it.enabled = false` 禁用。禁用后的任务仍存在于任务图中但不会执行（标记为 SKIPPED 语义），从而分组项目不再尝试发布空构件，而子项目（`iceberg-kafka-connect-events`、`iceberg-kafka-connect`）的发布任务不受影响。

## 总结

本提交在 `kafka-connect/build.gradle` 中通过 `afterEvaluate` 禁用空分组项目下所有 `publishing` 分组的任务，避免该无源码的父项目在发布流程中生成空 POM/空 jar 构件或导致发布校验失败，确保仅真正含产物的子项目参与发布，保障发布流程的干净与稳定。
