# 提交 0408：Build: Don't run CI's on unrelated changes

## 提交信息

- **序号**：0408
- **哈希**：fd1cf49280bde07d67c6bc1a6ec60238e1e38f7f
- **短哈希**：fd1cf4928
- **日期**：2024 年 1 月 24 日（Wed Jan 24 08:23:38 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Don't run CI's on unrelated changes (#9526)
- **PR/Issue**：#9526

完整提交说明如下：

```
We want to be more explicit about running the right CI.
When an unrelated workflow changes, we don't want to run
the whole testsuite
```

## 总体目的

这个提交优化 GitHub Actions CI 的触发策略，避免"无关工作流文件变更"触发整套测试套件。Iceberg 仓库下挂了多条独立的 CI 流水线（spark-ci、flink-ci、hive-ci、java-ci、delta-conversion-ci 等），每条流水线在 `pull_request` 触发条件里都配了 `paths-ignore`，用来声明"哪些路径变了不要跑这条 CI"。但是此前的 `paths-ignore` 列表很不完整——典型情况是只列了另外一两条工作流（比如 spark-ci 只 ignore 了 flink-ci 和 hive-ci），却没把自己之外的所有工作流都 ignore 掉。这导致一个反直觉的现象：改一个完全不相关的 `.github/workflows/open-api.yml` 或 `site-ci.yml`，也会把 spark-ci、flink-ci、hive-ci、java-ci 全部拉起来跑一遍完整测试套件，浪费 CI 资源、拖慢 PR 反馈。

提交作者的意图在 commit message 里说得很清楚：要"更明确地跑对的 CI"——每条 CI 只在它真正关心的代码路径变化时运行，其他工作流文件（哪怕同在 `.github/workflows/` 下）的变更都不应触发它。除了补全 `paths-ignore`，作者还顺手把 `license_check.yml` 重命名为 `license-check.yml`（下划线改连字符），与仓库中其他工作流文件的命名风格（全部用连字符）保持一致；这也解释了为什么新 ignore 列表里出现的是 `licence-check.yml`（连字符版本）。

## 如何达成设计目的

实现路径有两块。第一块是给 5 条主要测试 CI（delta-conversion-ci、flink-ci、hive-ci、java-ci、spark-ci）的 `pull_request.paths-ignore` 列表统一补齐：把当前所有其他工作流文件（`api-binary-compatibility.yml`、`delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`spark-ci.yml`、`stale.yml`）都加进去，做到"改任何一条无关工作流都不会触发本 CI"。第二块是把 `license_check.yml` 重命名为 `license-check.yml`，与连字符命名风格统一。改动是纯 YAML 配置层面，不涉及任何业务代码或测试逻辑。

## 修改详情

### .github/workflows/delta-conversion-ci.yml

**修改目的**：补全 delta-conversion CI 的 `paths-ignore`，避免无关工作流变更触发该 CI。

**工作逻辑**：在原有 `paths-ignore`（`.github/ISSUE_TEMPLATE/**`、`flink-ci.yml`、`hive-ci.yml`、`.gitignore`、`.asf.yml`、`dev/**`）基础上，新增了除自身以外的所有工作流文件引用：`api-binary-compatibility.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`spark-ci.yml`、`stale.yml`，以及 `java-ci.yml`。这样当这些工作流文件单独变化时，delta-conversion CI 不会被触发。

### .github/workflows/flink-ci.yml

**修改目的**：补全 flink CI 的 `paths-ignore`，避免无关工作流变更触发该 CI。

**工作逻辑**：原 `paths-ignore` 只列了 `spark-ci.yml` 和 `hive-ci.yml`，本提交把列表扩展到全部其他工作流文件（`api-binary-compatibility.yml`、`delta-conversion-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`spark-ci.yml`、`stale.yml`），并删除了原来对 `spark-ci.yml` 单独存在的旧引用（被新列表覆盖，保持去重）。注意：旧列表里只 ignore 了 spark-ci 和 hive-ci 两条，意味着此前改 java-ci、delta-conversion-ci 等都会触发 flink 全量测试，这是被本次提交修复的核心问题。

### .github/workflows/hive-ci.yml

**修改目的**：补全 hive CI 的 `paths-ignore`，避免无关工作流变更触发该 CI。

**工作逻辑**：原 `paths-ignore` 只列了 `spark-ci.yml` 和 `flink-ci.yml`，本提交扩展到全部其他工作流文件（`api-binary-compatibility.yml`、`delta-conversion-ci.yml`、`flink-ci.yml`、`java-ci.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`spark-ci.yml`、`stale.yml`），逻辑与 flink-ci 对称。

### .github/workflows/java-ci.yml

**修改目的**：补全 java CI 的 `paths-ignore`，避免无关工作流变更触发该 CI。

**工作逻辑**：原 `paths-ignore` 只列了 `spark-ci.yml`、`flink-ci.yml`、`hive-ci.yml` 三条，本提交扩展到全部其他工作流文件（新增 `api-binary-compatibility.yml`、`delta-conversion-ci.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`stale.yml`），逻辑与其余几条 CI 对称。

### .github/workflows/license_check.yml → .github/workflows/license-check.yml

**修改目的**：把工作流文件名从下划线风格改为连字符风格，与仓库内其他工作流文件命名统一。

**工作逻辑**：这是一次纯重命名（`git rename`，文件内容 100% 不变）。重命名后该工作流在新加的 `paths-ignore` 列表里就以 `licence-check.yml` 出现。统一命名风格可以避免后续维护者写 ignore 路径时踩到"下划线 vs 连字符"的坑，也让 `.github/workflows/` 下的文件视觉上更整齐。

### .github/workflows/spark-ci.yml

**修改目的**：补全 spark CI 的 `paths-ignore`，避免无关工作流变更触发该 CI。

**工作逻辑**：原 `paths-ignore` 只列了 `flink-ci.yml` 和 `hive-ci.yml`，本提交扩展到全部其他工作流文件（新增 `api-binary-compatibility.yml`、`delta-conversion-ci.yml`、`java-ci.yml`、`jmh-benchmarks-ci.yml`、`labeler.yml`、`licence-check.yml`、`open-api.yml`、`publish-snapshot.yml`、`recurring-jmh-benchmarks.yml`、`site-ci.yml`、`stale.yml`），逻辑与其余几条 CI 对称。

## 小结

这是一个 CI 触发策略优化提交，模式是"补全 paths-ignore 列表 + 顺手统一文件命名"。改动覆盖 5 条主要测试 CI（delta-conversion、flink、hive、java、spark），把此前只 ignore 一两条无关工作流的稀疏列表，统一扩展为"自身之外的所有工作流文件全部 ignore"。同时把 `license_check.yml` 重命名为 `license-check.yml` 与连字符命名风格对齐。意义在于消除"改无关工作流却跑整套测试套件"的资源浪费，让每条 CI 只在它真正关心的代码路径变化时运行，提升 CI 效率、加快 PR 反馈。改动纯 YAML 配置层，不触及业务代码或测试逻辑，风险低。
