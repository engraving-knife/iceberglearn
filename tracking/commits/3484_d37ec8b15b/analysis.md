# 提交 3484：ci: fix zizmor security alerts (#15820)

## 提交信息

- **序号**：3484 / 4088
- **哈希**：d37ec8b15bcbf0f95c75dfeacd08947cbdd25df4
- **短哈希**：d37ec8b15b
- **日期**：2026-03-30 13:32:04 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: fix zizmor security alerts (#15820)
- **PR/Issue**：#15820

## 总体目的

修复 zizmor 安全扫描（在 #15793 中引入）报告的安全告警。zizmor 扫描发现两类问题：
1. **cache-poisoning 告警**：多个工作流使用 `gradle/actions/setup-gradle` 时存在缓存投毒风险。zizmor 认为 PR 触发的构建中，setup-gradle 可能写入缓存被恶意利用。但实际上 setup-gradle 的缓存写入仅限于默认分支，因此通过添加 `# zizmor: ignore[cache-poisoning]` 注释并附说明来抑制该告警。
2. **CodeQL action 版本过旧**：codeql.yml 中使用的 codeql-action commit SHA 需要更新。

同时调整了 zizmor.yml 自身的配置：移除 `security-events: write` 权限（改为 `permissions: {}`），并设置 `advanced-security: false`。

## 如何达成设计目的

1. 对所有使用 `gradle/actions/setup-gradle` 的工作流添加 `# zizmor: ignore[cache-poisoning]` 内联注释，说明缓存写入仅限默认分支。
2. 更新 codeql.yml 中 codeql-action 的 commit SHA 到新版本。
3. 调整 zizmor.yml 配置，移除不必要的写权限，禁用 advanced-security。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 line)

**修改目的**：为 setup-gradle action 添加 zizmor cache-poisoning 抑制注释。

**工作逻辑**：在 `gradle/actions/setup-gradle@...# v5` 后追加 `# zizmor: ignore[cache-poisoning] -- cache writes are restricted to the default branch by setup-gradle`。

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：更新 codeql-action init 和 analyze 的 commit SHA。

**工作逻辑**：
- `github/codeql-action/init@38697555549f1db7851b81482ff19f1fa5c4fedc` → `@c10b8064de6f491fea524254123dbe5e09572f13`
- `github/codeql-action/analyze@38697555549f1db7851b81482ff19f1fa5c4fedc` → `@c10b8064de6f491fea524254123dbe5e09572f13`

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：为两处 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/flink-ci.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/hive-ci.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/java-ci.yml` (+3/-3 lines)

**修改目的**：为三处 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/publish-snapshot.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/spark-ci.yml` (+1/-1 line)

**修改目的**：为 setup-gradle 添加 zizmor 抑制注释。

### `.github/workflows/zizmor.yml` (+5/-2 lines)

**修改目的**：调整 zizmor 工作流自身配置。

**工作逻辑**：
- 将 `permissions: security-events: write` 改为 `permissions: {}`（无权限）。
- 为 zizmor-action 添加 `with: advanced-security: false`，禁用高级安全检查（可能需要更多权限或产生更多告警）。

## 总结

修复 zizmor 安全扫描引入的告警。主要工作是对所有使用 setup-gradle 的工作流添加 cache-poisoning 告警抑制注释（因为 setup-gradle 缓存写入仅限默认分支，实际无风险），更新 CodeQL action 到新版本，并调整 zizmor 工作流自身权限配置为最小权限。
