# 提交 3455：ci: pin GitHub action to commit hash (#15753)

## 提交信息

- **序号**：3455 / 4088
- **哈希**：4bcd1c16b78a73ae886fe3bf0342eb30c3f121d5
- **短哈希**：4bcd1c16b7
- **日期**：2026-03-24 16:02:02 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: pin GitHub action to commit hash (#15753)
- **PR/Issue**：#15753

## 总体目的

将所有 GitHub Actions 工作流中使用的第三方 GitHub Actions 从版本标签（如 `@v6`、`@v5`）固定到特定的 commit SHA。这是继提交 #15707 和 #15730 之后的大规模安全加固，覆盖全部 18 个工作流文件。使用 commit SHA 确保 action 内容不可被篡改，符合 Apache 软件基金会的安全要求。

## 如何达成设计目的

- 在 18 个工作流文件中，将所有第三方 GitHub Actions 的版本标签替换为 commit SHA
- 同时保留版本号作为注释（如 `@de0fac2e... # v6`），便于追踪当前使用的版本
- 涉及的 action 包括：`actions/checkout`、`actions/setup-java`、`actions/cache`、`actions/upload-artifact`、`github/codeql-action` 等

## 修改详情

### 18 个工作流文件（共 +62/-62 lines）

涉及以下文件：

- `.github/workflows/api-binary-compatibility.yml`：checkout、setup-java、cache、upload-artifact
- `.github/workflows/codeql.yml`：checkout、codeql-action/init、codeql-action/analyze
- `.github/workflows/delta-conversion-ci.yml`：checkout、setup-java、cache、upload-artifact（2个job）
- `.github/workflows/docs-ci.yml`：相关 actions
- `.github/workflows/flink-ci.yml`：相关 actions
- `.github/workflows/hive-ci.yml`：相关 actions
- `.github/workflows/java-ci.yml`：相关 actions
- `.github/workflows/jmh-benchmarks.yml`：相关 actions
- `.github/workflows/kafka-connect-ci.yml`：相关 actions
- `.github/workflows/labeler.yml`：相关 actions
- `.github/workflows/license-check.yml`：相关 actions
- `.github/workflows/open-api.yml`：相关 actions
- `.github/workflows/publish-iceberg-rest-fixture-docker.yml`：相关 actions
- `.github/workflows/publish-snapshot.yml`：相关 actions
- `.github/workflows/recurring-jmh-benchmarks.yml`：相关 actions
- `.github/workflows/site-ci.yml`：相关 actions
- `.github/workflows/spark-ci.yml`：相关 actions
- `.github/workflows/stale.yml`：相关 actions

**修改模式**：
每个修改都是将 `@vN` 替换为 `@<commit-sha> # vN`，例如：
- `actions/checkout@v6` → `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6`
- `actions/setup-java@v5` → `actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5`
- `actions/cache@v5` → `actions/cache@668228422ae6a00e4ad889ee87cd7109ec5666a7 # v5`
- `actions/upload-artifact@v7` → `actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f # v7`

## 总结

该提交是 CI/CD 安全加固的大规模操作，将 18 个 GitHub Actions 工作流文件中所有第三方 action 从版本标签固定到特定 commit SHA，同时保留版本号注释便于追踪。这是 Apache 项目安全合规的要求，防止供应链攻击。
