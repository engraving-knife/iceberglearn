# 提交 3999：CI: Use one Java version for PR checks (#16945)

## 提交信息

- **序号**：3999 / 4088
- **哈希**：d7c07d56f7513fa48b7e03cdb2232b0ab210bd87
- **短哈希**：d7c07d56f
- **日期**：2026-07-08 09:21:51 -0700
- **作者**：Ajantha Bhat
- **提交说明**：CI: Use one Java version for PR checks (#16945)
- **PR/Issue**：#16945

## 总体目的

本提交优化 GitHub Actions CI 工作流，使 PR（pull request）检查只在基础 JDK 版本（Java 17）上运行，而把 Java 21 的测试留给 push（合并到主分支）等事件触发。目的是减少 PR 检查的并行任务数量，降低 CI 资源消耗和等待时间，同时仍然在合并后保留对多 JDK 版本的覆盖测试。

之前所有 CI 矩阵都同时跑 Java 17 和 21 两个版本，对于 PR 检查而言，21 版本的额外覆盖往往重复了 17 的结果，浪费了大量计算资源。本次通过 matrix exclude 机制在 PR 事件下排除 jvm=21。

## 如何达成设计目的

通过在 6 个 CI 工作流的 matrix 配置中统一加入 `event_name: ['${{ github.event_name }}']` 维度，并在 `exclude` 中排除 `event_name: pull_request` + `jvm: 21` 的组合。这样：
- 当事件是 `pull_request` 时，jvm=21 的组合被排除，只跑 jvm=17。
- 当事件是 `push`（合并到主分支）等其他事件时，两个版本都跑，保持完整覆盖。

由于 `github.event_name` 是在 workflow 解析时求值，通过 matrix 的 `${{ }}` 表达式注入即可生效。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml` (+10/-0 lines)

**修改目的**：在 delta-conversion CI 的两个 job 矩阵中排除 PR 事件下的 jvm=21。

**工作逻辑**：两个 job 各添加 `event_name` 维度和 `exclude` 规则：
```yaml
event_name: ['${{ github.event_name }}']
exclude:
  - event_name: pull_request
    jvm: 21
```

### `.github/workflows/flink-ci.yml` (+5/-0 lines)

**修改目的**：在 flink CI 矩阵中排除 PR 下的 jvm=21。

**工作逻辑**：在已有的 `jvm` 和 `flink` 矩阵上添加 `event_name` 维度和 `exclude` 规则。

### `.github/workflows/hive-ci.yml` (+5/-0 lines)

**修改目的**：在 hive CI 矩阵中排除 PR 下的 jvm=21。

**工作逻辑**：同上模式。

### `.github/workflows/java-ci.yml` (+15/-0 lines)

**修改目的**：在 java CI 的三个 job 中分别排除 PR 下的 jvm=21。

**工作逻辑**：三个 job（java-ci 本身有多个 job）各添加 `event_name` 维度和 `exclude` 规则。改动量最大，因为有 3 个 job。

### `.github/workflows/kafka-connect-ci.yml` (+5/-0 lines)

**修改目的**：在 kafka-connect CI 矩阵中排除 PR 下的 jvm=21。

**工作逻辑**：同上模式。

### `.github/workflows/spark-ci.yml` (+4/-0 lines)

**修改目的**：在 spark CI 矩阵中排除 PR 下的 jvm=21。

**工作逻辑**：spark 矩阵已有 scala/spark 维度和部分 exclude 规则，本次新增 `event_name` 维度，并加入 `exclude`：
```yaml
- event_name: pull_request
  jvm: 21
```

## 总结

这是一次 CI 效率优化提交，通过统一在 6 个工作流中引入基于 `github.event_name` 的 matrix exclude，让 PR 检查只跑 Java 17 基础版本，显著减少了 PR 触发的 CI 任务数，降低资源消耗和等待时间，同时合并后仍保留 Java 21 的完整覆盖。改动模式一致、影响范围清晰。
