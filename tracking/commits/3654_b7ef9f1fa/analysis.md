# 提交 3654：CI: Use specific patch versions in workflow action comments (#16229)

## 提交信息

- **序号**：3654 / 4088
- **哈希**：b7ef9f1fa82ac1bcf57359e746128a706b2d232e
- **短哈希**：b7ef9f1fa
- **日期**：2026-05-06 10:02:43 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Use specific patch versions in workflow action comments (#16229)
- **PR/Issue**：#16229

## 总体目的

这个提交将所有 GitHub Actions 工作流中引用第三方 action 的版本注释从模糊的主版本号（如 `# v5`、`# v6`）统一更正为精确的 patch 版本号（如 `# v5.2.0`、`# v6.0.2`）。

Iceberg 项目出于安全考虑，在 GitHub Actions 中使用 pin 到具体 commit SHA 的方式引用 actions，并在注释中标注版本号以便追踪。此前注释只写了主版本号（如 `# v5`），不够精确，无法识别具体使用的是哪个 patch 版本。本提交与第 3649 号提交（labeler 注释修正）属同一改进方向，将所有 17 个工作流文件中的 action 版本注释统一更正为精确的 patch 版本号，便于后续升级、审计和维护时识别具体版本。

## 如何达成设计目的

遍历所有 `.github/workflows/*.yml` 文件，将每个 `uses: <action>@<sha> # <version>` 行中的注释版本号替换为对应的精确 patch 版本号。commit SHA 保持不变，仅修改注释文本。

涉及的主要 action 及版本更正：
- `actions/checkout`：`# v6` → `# v6.0.2`
- `actions/setup-java`：`# v5` → `# v5.2.0`
- `gradle/actions/setup-gradle`：`# v5` → `# v5.0.2`
- `actions/upload-artifact`：已是 `# v7.0.1`（精确）
- `actions/download-artifact`：`# v5` → `# v5.0.1`
- `actions/cache`：`# v5` → `# v5.0.0`
- `actions/setup-node`：`# v5` → `# v5.0.0`
- `repo-sync/pull`：`# v2` → `# v2.13`

## 修改详情

### 17 个 `.github/workflows/*.yml` 文件 (+54/-54 lines)

**修改目的**：统一更正 action 版本注释为精确 patch 版本。

**工作逻辑**：仅修改注释文本，commit SHA 不变。涉及文件：
- `api-binary-compatibility.yml`
- `asf-allowlist-check.yml`
- `codeql.yml`
- `delta-conversion-ci.yml`
- `docs-ci.yml`
- `flink-ci.yml`
- `hive-ci.yml`
- `java-ci.yml`
- `jmh-benchmarks.yml`
- `kafka-connect-ci.yml`
- `license-check.yml`
- `open-api.yml`
- `publish-iceberg-rest-fixture-docker.yml`
- `publish-snapshot.yml`
- `recurring-jmh-benchmarks.yml`
- `site-ci.yml`
- `spark-ci.yml`

示例：
```yaml
# 旧
- uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
- uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5
- uses: gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5
# 新
- uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
- uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5.2.0
- uses: gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5.0.2
```

## 总结

这是一个 CI 配置规范化提交，将所有 17 个 GitHub Actions 工作流文件中的 action 版本注释从模糊的主版本号统一更正为精确的 patch 版本号。commit SHA 不变，不影响实际 CI 行为，但提升了版本标注的准确性和可维护性，便于后续审计和升级时识别具体使用的 action 版本。
