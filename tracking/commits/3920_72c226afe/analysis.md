# 提交 3920：Build: Bump gradle/actions from 5.0.2 to 6.2.0 (#16899)

## 提交信息

- **序号**：3920 / 4088
- **哈希**：72c226afe97f598d3277ece6351ef794835a6b80
- **短哈希**：72c226afe
- **日期**：2026-06-21 00:07:20 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump gradle/actions from 5.0.2 to 6.2.0 (#16899)
- **PR/Issue**：#16899

## 总体目的

这是由 Dependabot 发起的 GitHub Actions 依赖升级，将 `gradle/actions`（具体是 `setup-gradle` action）从 v5.0.2（commit `0723195856401067f7a2779048b490ace7a47d7c`）升级到 v6.2.0（commit `3f131e8634966bd73d06cc69884922b02e6faf92`）。`gradle/actions/setup-gradle` 是 Gradle 官方维护的 GitHub Action，用于在 CI 中设置 Gradle 环境，并管理 Gradle 用户主目录的缓存（cache-read-only / cache-disabled 等策略）。

此次升级是 semver-major 更新（5.x → 6.x），可能包含行为变更、新的缓存策略或对 Gradle 新版本的支持。Iceberg 项目在所有 CI 工作流中均使用该 action，因此需要批量同步更新所有工作流文件。

## 如何达成设计目的

通过批量更新所有 GitHub Actions 工作流文件中 `gradle/actions/setup-gradle` 步骤的 commit SHA 引用及其注释中的版本号。使用 commit SHA 而非 tag 引用是 GitHub Actions 的安全最佳实践，可以防止供应链劫持攻击。同时保留每个作业原有的 `cache-read-only` / `cache-disabled` 缓存策略注释。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：升级 API 二进制兼容性检查作业中的 setup-gradle action。

**工作逻辑**：将 setup-gradle 的 commit SHA 从 `0723195856401067f7a2779048b490ace7a47d7c # v5.0.2` 改为 `3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0`，保留原有的 `cache-read-only: true` 配置。

### `.github/workflows/cve-scan.yml` (+1/-1 lines)

**修改目的**：升级 CVE 扫描作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释，保留 `cache-read-only: true`。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级 Delta 转换 CI 中两个作业的 setup-gradle action。

**工作逻辑**：在该工作流中有两个作业（matrix 构建），每个作业的 setup-gradle 步骤均从 v5.0.2 升级到 v6.2.0，保留 `cache-read-only: true` 策略，即只读取 java-ci 的 build-checks（Java 17）作为全局缓存写入者生成的缓存。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/java-ci.yml` (+4/-4 lines)

**修改目的**：升级 Java CI 工作流中四个作业的 setup-gradle action。

**工作逻辑**：Java CI 是 Iceberg 的主工作流，包含 build-checks（17）作业，它是全局缓存的规范写入者（cache-read-only 仅在非 main 分支或非 Java 17 时为 true）。所有四个作业均升级到 v6.2.0，保留各自的缓存读写策略。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级 JMH 基准测试作业中的 setup-gradle action。

**工作逻辑**：更新 commit SHA 和版本号注释，保留 `cache-disabled: true` 配置（由于该作业针对任意 repo/ref 输入调度，禁用缓存以避免缓存投毒攻击）。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级发布 REST fixture Docker 镜像作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/publish-snapshot.yml` (+1/-1 lines)

**修改目的**：升级发布快照版本作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级周期性 JMH 基准测试作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 作业中的 setup-gradle action。

**工作逻辑**：同上，更新 commit SHA 和版本号注释。

## 总结

这是一次涉及 11 个 CI 工作流文件的批量升级，将 `gradle/actions/setup-gradle` 从 v5.0.2 升级到 v6.2.0。由于是 major 版本升级，可能引入新的行为或缓存策略；通过同步更新所有工作流并保留各自的缓存策略配置，确保 CI 行为一致性。使用 commit SHA 引用而非可变 tag 符合 GitHub Actions 的供应链安全最佳实践。
