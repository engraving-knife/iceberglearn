# 提交 3551：Build: set zizmor min-severity and min-confidence to medium (#16001)

## 提交信息

- **序号**：3551 / 4088
- **哈希**：ecbe8a84b1897015bdaf0e7c3fdcbb8575aa9971
- **短哈希**：ecbe8a84b
- **日期**：2026-04-17 13:19:06 +0200
- **作者**：Kevin Liu
- **提交说明**：Build: set zizmor min-severity and min-confidence to medium (#16001)
- **PR/Issue**：#16001

## 总体目的

zizmor 是 Iceberg 项目用于扫描 GitHub Actions 工作流安全问题的工具。此前各 CI 工作流中对 `gradle/actions/setup-gradle` 使用了 `# zizmor: ignore[cache-poisoning] -- cache writes are restricted to the default branch by setup-gradle` 注释来压制 cache-poisoning 告警，理由是 setup-gradle 的缓存写入仅限默认分支，风险可控。

本提交的目的是调整 zizmor 的扫描配置，将 `min-severity` 和 `min-confidence` 都设为 `medium`，这样低严重性和低置信度的告警不再上报。配合这个调整，移除了 11 个工作流中 `setup-gradle` 行的 `# zizmor: ignore[cache-poisoning]` 压制注释——因为 cache-poisoning 告警在 medium 阈值下要么不再触发，要么被认为可接受。

这是 CI 安全扫描配置的优化，减少噪音同时保持对中等及以上风险的关注。

## 如何达成设计目的

1. 在 `zizmor.yml` 工作流的 zizmor-action 配置中新增 `min-severity: medium` 和 `min-confidence: medium` 两个参数
2. 在 11 个使用 `setup-gradle` 的工作流中移除 `# zizmor: ignore[cache-poisoning] -- ...` 注释，保留 `# v5` 版本注释

## 修改详情

### `.github/workflows/zizmor.yml` (+2/-0 lines)

**修改目的**：设置 zizmor 扫描的最小严重性和置信度阈值。

**工作逻辑**：
```yaml
        with:
          advanced-security: false
          min-severity: medium
          min-confidence: medium
```
只上报严重性 >= medium 且置信度 >= medium 的发现。

### 11 个 CI 工作流（各 +1/-1 lines）

**修改目的**：移除 `setup-gradle` 的 cache-poisoning ignore 注释。

涉及文件：
- `.github/workflows/api-binary-compatibility.yml`
- `.github/workflows/delta-conversion-ci.yml`（2 处）
- `.github/workflows/flink-ci.yml`
- `.github/workflows/hive-ci.yml`
- `.github/workflows/java-ci.yml`（4 处）
- `.github/workflows/jmh-benchmarks.yml`
- `.github/workflows/kafka-connect-ci.yml`
- `.github/workflows/publish-iceberg-rest-fixture-docker.yml`
- `.github/workflows/publish-snapshot.yml`
- `.github/workflows/recurring-jmh-benchmarks.yml`
- `.github/workflows/spark-ci.yml`

**工作逻辑**：每处改动相同：
```yaml
-      - uses: gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5 # zizmor: ignore[cache-poisoning] -- cache writes are restricted to the default branch by setup-gradle
+      - uses: gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5
```
移除 `# zizmor: ignore[cache-poisoning] -- ...` 部分，保留 `# v5` 版本注释。共 15 处（部分文件有多处）。

## 总结

本提交将 zizmor 安全扫描的 `min-severity` 和 `min-confidence` 都设为 `medium`，减少低严重性/低置信度告警的噪音。配合移除 11 个 CI 工作流中 `setup-gradle` 的 `cache-poisoning` ignore 注释（共 15 处），因为该告警在 medium 阈值下不再需要逐个压制。属于 CI 安全扫描配置优化。
