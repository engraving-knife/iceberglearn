# 提交 3878：Build: Bump actions/checkout from 6.0.2 to 6.0.3 (#16806)

## 提交信息

- **序号**：3878 / 4088
- **哈希**：7c777f71d88d633ae838c0ef8165905291fcfaac
- **短哈希**：7c777f71d
- **日期**：2026-06-14 00:07:59 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/checkout from 6.0.2 to 6.0.3 (#16806)
- **PR/Issue**：#16806

## 总体目的

由 Dependabot 自动生成的 GitHub Actions 依赖升级提交，将 `actions/checkout` 从 6.0.2 升级到 6.0.3。`actions/checkout` 是 GitHub 官方提供的仓库检出 Action，几乎所有 CI 工作流都会使用到它。

这是 semver-patch 级别的升级，通常包含 bug 修复和小改进。由于该 Action 被项目几乎全部 CI 工作流使用，本次升级涉及 19 个工作流配置文件。

## 如何达成设计目的

通过批量修改 `.github/workflows/` 目录下的所有工作流 YAML 文件，将 `actions/checkout` 的引用版本从 `v6.0.2`（或对应的完整 SHA）更新为 `v6.0.3`。Dependabot 自动识别了所有使用该 Action 的位置并统一升级。

## 修改详情

### `.github/workflows/*.yml` (19 files, +24/-24 lines)

**修改目的**：升级所有 CI 工作流中 `actions/checkout` 的版本。

**工作逻辑**：
涉及的工作流文件包括：
- `api-binary-compatibility.yml`
- `asf-allowlist-check.yml`
- `codeql.yml`
- `cve-scan.yml`
- `delta-conversion-ci.yml`（4 处修改，因为使用了 matrix）
- `docs-ci.yml`
- `flink-ci.yml`
- `hive-ci.yml`
- `java-ci.yml`（8 处修改，因为包含多个 job）
- `jmh-benchmarks.yml`（4 处修改）
- `kafka-connect-ci.yml`
- `license-check.yml`
- `open-api.yml`
- `publish-iceberg-rest-fixture-docker.yml`
- `publish-snapshot.yml`
- `recurring-jmh-benchmarks.yml`
- `site-ci.yml`
- `spark-ci.yml`
- `zizmor.yml`

每个文件中将 `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd` 更新为 `actions/checkout@df4cb1c069e1874edd31b4311f1884172cec0e10`（即 v6.0.3 对应的提交 SHA），或从 `v6.0.2` 改为 `v6.0.3`。

## 总结

常规的 CI 工具升级，将 `actions/checkout` 从 6.0.2 升级到 6.0.3，涉及项目中几乎所有 CI 工作流文件。patch 级别升级风险较低，但确保了 CI 基础设施使用最新版本的检出工具。
