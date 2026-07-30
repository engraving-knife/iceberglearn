# 提交 3961：Build: Bump actions/setup-java from 5.2.0 to 5.3.0 (#16991)

## 提交信息

- **序号**：3961 / 4088
- **哈希**：ce92c469bd38616f3ae1bb4cf4311d9c662c4ca4
- **短哈希**：ce92c469b
- **日期**：2026-06-27 23:48:47 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-java from 5.2.0 to 5.3.0 (#16991)
- **PR/Issue**：#16991

## 总体目的

这是 Dependabot 自动升级 GitHub Actions 的 `actions/setup-java` 从 v5.2.0 到 v5.3.0 的提交。`actions/setup-java` 用于在 CI 中配置 Java 运行环境。升级类型为 `version-update:semver-minor`（次版本升级），包含功能改进但不引入破坏性变更。

## 如何达成设计目的

通过批量更新所有 GitHub Actions workflow 文件中的 `actions/setup-java` 引用，将 commit SHA 从 `be666c2fcd27ec809703dec50e508c2fdc7f6654`（v5.2.0）更新为 `ad2b38190b15e4d6bdf0c97fb4fca8412226d287`（v5.3.0）。

## 修改详情

### 多个 GitHub workflow 文件 (`.github/workflows/*.yml`)

**修改目的**：升级 setup-java action 版本。

**工作逻辑**：在每个 workflow 文件中，将：
```yaml
- uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5.2.0
```
更新为：
```yaml
- uses: actions/setup-java@ad2b38190b15e4d6bdf0c97fb4fca8412226d287 # v5.3.0
```

涉及的 workflow 文件包括：api-binary-compatibility、cve-scan、delta-conversion-ci、flink-ci、hive-ci、java-ci、jmh-benchmarks 等。

## 总结

常规 CI 工具链升级，将 actions/setup-java 从 v5.2.0 升级到 v5.3.0，影响范围仅限 CI 环境的 Java 环境配置。
