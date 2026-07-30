# 提交 3585：Build: Check runtime deps baseline for all engine versions in CI (#16103)

## 提交信息

- **序号**：3585 / 4088
- **哈希**：a287f0907c9078d654d0076a457b038ef57ca6af
- **短哈希**：a287f0907
- **日期**：2026-04-24 17:57:32 -0400
- **作者**：Russell Spitzer
- **提交说明**：Build: Check runtime deps baseline for all engine versions in CI (#16103)
- **PR/Issue**：#16103

## 总体目的

该提交修复了 CI 中 `check-runtime-deps` 作业仅验证默认引擎版本（Spark 4.1、Flink 2.1）的问题。之前，该作业运行 `./gradlew checkAllRuntimeDeps` 时未启用所有模块，导致 `settings.gradle` 只激活了默认的 Spark 和 Flink 版本，其他版本（Spark 3.4/3.5/4.0、Flink 1.20/2.0、Kafka Connect）的运行时依赖基线未被检查。

这意味着如果某个非默认引擎版本的 `runtime-deps.txt` 与实际依赖不一致，CI 不会发现。该修复通过添加 `-DallModules=true` 参数，使 `settings.gradle` 激活 `gradle.properties` 中定义的所有 Spark、Flink 和 Kafka 版本，确保所有引擎版本的运行时依赖基线都得到检查。

## 如何达成设计目的

修改 GitHub Actions 工作流中 `check-runtime-deps` 作业的 Gradle 命令，添加 `-DallModules=true` 系统属性。

## 修改详情

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：启用所有模块进行运行时依赖检查。

**工作逻辑**：
```yaml
- run: ./gradlew checkAllRuntimeDeps -q -DallModules=true
```
`-DallModules=true` 系统属性被 `settings.gradle` 读取，激活所有已知引擎版本的模块，确保 `checkAllRuntimeDeps` 任务检查所有版本的 `runtime-deps.txt` 基线。

## 总结

该提交修复了 CI 中运行时依赖基线检查的覆盖范围问题，确保所有引擎版本（Spark 3.4/3.5/4.0/4.1、Flink 1.20/2.0/2.1、Kafka Connect）的 `runtime-deps.txt` 都在 CI 中被验证。这防止了类似 3570 提交（RoaringBitmap 版本不一致）的问题在非默认引擎版本中未被发现。
