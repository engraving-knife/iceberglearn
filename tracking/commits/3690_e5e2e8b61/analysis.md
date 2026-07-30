# 提交 3690：Build: Improve PR title check pattern for multi-word prefixes and reverts (#16301)

## 提交信息

- **序号**：3690 / 4088
- **哈希**：e5e2e8b610af408ea33613d2739ccf39428cbe2a
- **短哈希**：e5e2e8b61
- **日期**：2026-05-12 09:22:43 -0700
- **作者**：Manu Zhang
- **提交说明**：Build: Improve PR title check pattern for multi-word prefixes and reverts (#16301)
- **PR/Issue**：#16301

## 总体目的

这个提交改进了 PR 标题检查工作流的正则表达式模式，使其能够接受包含版本号的模块前缀（如 "Flink 2.1:"）和 Revert 操作的标题格式。

此前的 PR 标题检查正则 `^[A-Za-z][A-Za-z0-9._+/&-]*: .+` 仅支持单词前缀（如 "Core:"、"Spark:"），无法匹配以下合法的 PR 标题格式：
- `Flink 2.1: Add ...`（带版本号的模块前缀）
- `API, Core: Update ...`（多个模块前缀，用逗号分隔）
- `Revert "Spark: Add ..."`（Revert 操作的标题）

这导致合法的 PR 标题被错误地标记为格式错误，影响开发体验。

## 如何达成设计目的

通过修改 `pr-title-check.yml` 中的正则表达式模式，使其更灵活地匹配各种合法的 PR 标题格式。同时更新其他 CI 工作流的 `paths-ignore` 列表，避免 PR 标题检查工作流的变更触发无关的 CI 套件。

## 修改详情

### `.github/workflows/pr-title-check.yml` (+2/-2 lines)

**修改目的**：改进 PR 标题检查正则表达式。

**工作逻辑**：

```bash
-PATTERN='^[A-Za-z][A-Za-z0-9._+/&-]*: .+'
+PATTERN='^(Revert ")?[A-Za-z][A-Za-z0-9._+/&,-]*( [A-Za-z0-9][A-Za-z0-9._+/&,-]*)*: .+'
```

新模式解析：
- `^(Revert ")?`：可选的 Revert 前缀，匹配 `Revert "..."` 格式
- `[A-Za-z][A-Za-z0-9._+/&,-]*`：第一个模块名，以字母开头
- `( [A-Za-z0-9][A-Za-z0-9._+/&,-]*)*`：可选的后续单词，支持多词前缀如 "Flink 2.1"
- `: .+`：冒号和描述

同时更新示例提示：
```bash
-echo "Examples: 'Core: Fix ...', 'Spark: Add ...', 'API: Remove ...', 'Docs: Update ...'"
+echo "Examples: 'Core: Fix ...', 'Spark: Add ...', 'Flink 2.1: Add ...', 'API, Core: Update ...'"
```

### 其他 6 个 CI 工作流文件 (各 +1 line)

**修改目的**：在 `paths-ignore` 列表中添加对 `pr-title-check.yml` 的引用。

**工作逻辑**：在 `delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`kafka-connect-ci.yml`、`spark-ci.yml` 的 `paths-ignore` 列表中添加 `- '.github/workflows/pr-title-check.yml'`，确保 PR 标题检查工作流自身的变更不会触发这些 CI 套件。

## 总结

这是一个 CI/CD 工作流改进提交，使 PR 标题检查更加灵活，支持多词前缀（如 "Flink 2.1:"）和 Revert 标题格式。这减少了合法 PR 标题被误报为格式错误的场景，提升了开发体验。同时通过 `paths-ignore` 配置避免工作流变更触发无关 CI。
