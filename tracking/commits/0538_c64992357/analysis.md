# 提交 0538：Build: Ignore major version update in dependabot

## 提交信息

- **序号**：0538 / 4088
- **哈希**：c649923570ddf417eb048dfb2916432ffb71171d
- **短哈希**：c64992357
- **日期**：2024-02-26 19:07:18 +0800
- **作者**：Manu Zhang
- **提交说明**：Build: Ignore major version update in dependabot (#9806)
- **PR/Issue**：#9806

## 总体目的

本提交调整 GitHub Dependabot 配置，让 Dependabot 不再自动为依赖发起主版本（major version）升级的 PR。

Dependabot 会定期检查 `gradle`、`pip`、`github-actions` 三类生态的依赖并自动开 PR。其中主版本升级（如 `1.x → 2.x`、`4.x → 5.x`）按语义化版本约定意味着存在不兼容变更，对 Iceberg 这种有大量集成（Spark/Flink/MR/Hive 多版本矩阵、Revapi API 兼容性检查）的项目而言，盲目跟进主版本升级极易引发构建失败或 API 兼容性破坏。这类升级需要人工评估、适配和测试，不适合由机器人自动发起。本提交通过配置让 Dependabot 只提醒次要版本（minor）和补丁版本（patch）升级，把主版本升级交由维护者主动决策。

## 如何达成设计目的

在 `.github/dependabot.yml` 中添加 `ignore` 规则，使用通配符 `dependency-name: "*"` 匹配所有依赖，`update-types: ["version-update:semver-major"]` 指定只忽略主版本升级类型。Dependabot 的 `ignore` 配置支持按 `dependency-name` 和 `update-types` 过滤，`version-update:semver-major` 是 Dependabot 预定义的语义化版本升级类型常量，精确对应主版本号变更。

## 修改详情

### `.github/dependabot.yml`

**修改目的**：让 Dependabot 跳过所有依赖的主版本升级。

**工作逻辑**：在文件末尾新增 3 行：
```yaml
  - ignore:
    dependency-name: "*"
    update-types: ["version-update:semver-major"]
```

意图是在 `gradle` 这条更新配置项下挂一个 `ignore` 子规则。

**注意：本提交存在 YAML 结构缺陷**。从 diff 看，新增的 `- ignore:` 与 `- package-ecosystem:` 处于同一缩进层级（均为 2 空格），被解析成了 `updates` 列表下的一个新列表项，而非挂在前一个 `gradle` 条目下。Dependabot 规范要求 `updates` 下每个列表项必须包含 `package-ecosystem` 和 `directory`，单独的 `ignore` 列表项是无效配置。这意味着本提交实际上并未按预期生效——`ignore` 没有被正确关联到任何更新配置。该缺陷在后续提交 20bd4ca8c（PR #9981，"Build: Fix ignoring major version update in dependabot"）中被修复，修正后的写法是把 `ignore` 正确嵌套到各 `package-ecosystem` 条目内部：
```yaml
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 50
    ignore:
      - dependency-name: "*"
        update-types: ["version-update:semver-major"]
```

## 小结

本提交的意图明确且合理：屏蔽 Dependabot 的主版本自动升级 PR，避免破坏性依赖升级冲击 CI。但实现上存在 YAML 缩进错误，导致 `ignore` 规则未被正确挂载，实际未生效，需配合后续修复提交 #9981 一起看。

**回迁到 1.4.x 的注意事项**：若 1.4.x 需要此能力，建议直接采用修正后的写法（PR #9981 的嵌套形式），而非本提交的原始写法，以免引入无效配置。回迁后可观察一次 Dependabot 周期运行结果，确认主版本升级 PR 不再出现以验证配置生效。
