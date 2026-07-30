# 提交 4046：Build: Bump actions/setup-java from 5.4.0 to 5.5.0 (#17230)

## 提交信息

- **序号**：4046 / 4088
- **哈希**：4713badaeabedca90d0280b47a824ae29cca7dd7
- **短哈希**：4713badae
- **日期**：2026-07-15 18:52:50 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-java from 5.4.0 to 5.5.0 (#17230)
- **PR/Issue**：#17230

## 总体目的

Dependabot 自动升级提交，将 `actions/setup-java` 从 5.4.0 升级到 5.5.0（semver minor 版本升级）。`actions/setup-java` 是用于在 GitHub Actions runner 上配置 Java 环境的官方 action，Iceberg 的几乎所有 CI 工作流都依赖它来安装指定版本的 Zulu JDK 用于构建和测试。

由于该 action 被大量工作流引用（共 12 个工作流文件、16 处引用），Dependabot 一次性更新了所有引用位置，将 SHA 从 `1bcf9fb12cf4aa7d266a90ae39939e61372fe520`（v5.4.0）统一更新为 `0f481fcb613427c0f801b606911222b5b6f3083a`（v5.5.0）。minor 版本升级通常包含新功能和向后兼容的改进。

## 如何达成设计目的

Dependabot 遍历所有引用 `actions/setup-java` 的工作流文件，将 `uses` 行的 SHA 和版本注释统一更新。各工作流的 `with` 配置（`distribution: zulu`、`java-version` 等）保持不变。

## 修改详情

### 12 个 GitHub Actions 工作流文件 (+16/-16 lines)

涉及文件：
- `.github/workflows/api-binary-compatibility.yml`
- `.github/workflows/cve-scan.yml`
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

**修改目的**：将所有工作流中的 setup-java action 统一升级到 5.5.0。

**工作逻辑**：每处改动形式相同：
```yaml
- uses: actions/setup-java@0f481fcb613427c0f801b606911222b5b6f3083a # v5.5.0
  with:
    distribution: zulu
    java-version: ...
```
SHA 从 v5.4.0 更新为 v5.5.0，其余配置不变。

## 总结

常规的 CI 工具链维护升级，将 Java 环境配置 action 升级到 5.5.0 minor 版本。由于该 action 被 12 个工作流共 16 处引用，Dependabot 一次性完成全部更新，保证版本一致性。minor 级别升级风险较低。
