# 提交 0883：Fix CI script inclusion of release branches (#10514)

## 提交信息

- **序号**：0883 / 4088
- **哈希**：7071dc18ed66454542f466b5bfe8821028f2db0c
- **短哈希**：7071dc18e
- **日期**：2024-06-28（Fri Jun 28 12:30:13 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Fix CI script inclusion of release branches (#10514)
- **PR/Issue**：#10514

## 总体目的

Iceberg 仓库中的 GitHub Actions 工作流（CI 脚本）被设计为在 `main` 主分支以及各发布维护分支（如 `1.5.x`、`1.4.x` 等）上推送时自动触发。但是这些工作流的 `on.push.branches` 触发条件里，分支匹配模式只写了 `0.**`，即只匹配 `0.x`、`0.12.x` 等 0 系列分支。

这导致一个实际问题：自 Iceberg 1.0 发布以来，所有 1.x 系列的维护分支（如 `1.0.x`、`1.2.x`、`1.3.x`、`1.4.x`、`1.5.x`）的 push 都不会触发 CI。维护分支上的修复提交缺乏自动验证，存在回归风险，与维护流程的预期不符。

本提交的目的就是修正这些 CI 工作流的分支匹配模式，使它们能够正确覆盖 1.x、2.x 系列发布分支。提交说明还提到，作者前瞻性地加入了 `2.*`，为即将到来的 2.0 发布做准备；同时把原 `0.**`（双星号）改为 `0.*`（单星号），因为发布分支命名约定中不包含 `/`，单星号足以匹配，更清晰。

## 如何达成设计目的

实现方式非常直接：对仓库下 7 个 GitHub Actions 工作流 YAML 文件，将 `on.push.branches` 列表中的 `'0.**'` 一行替换为三行 `'0.*'`、`'1.*'`、`'2.*'`。其余触发条件（`main`、`apache-iceberg-**` tag、`pull_request`）保持不变。

注意 GitHub Actions 中 `**` 表示匹配包括 `/` 在内的任意字符（含路径分隔符），而 `*` 仅匹配非 `/` 字符。由于 Iceberg 发布分支命名形如 `1.5.x`，不含 `/`，使用 `*` 即可，且语义更明确。作者在提交说明里也提醒：`1.*` 的加入可能需要"传播到已有分支"，意味着已存在的 1.x 分支需要后续手动同步本工作流文件才能生效。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml`

**修改目的**：使 API 二进制兼容性检查工作流在 1.x、2.x 维护分支 push 时也触发。

**工作逻辑**：将 `on.push.branches` 中的 `'0.**'` 改为三行 `'0.*'`、`'1.*'`、`'2.*'`。

### `.github/workflows/delta-conversion-ci.yml`

**修改目的**：使 Delta 转换模块 CI 工作流覆盖新发布分支。

**工作逻辑**：同上，分支模式由 `'0.**'` 替换为 `'0.*'`、`'1.*'`、`'2.*'`。

### `.github/workflows/flink-ci.yml`

**修改目的**：使 Flink 模块 CI 工作流覆盖新发布分支。

**工作逻辑**：同上替换。

### `.github/workflows/hive-ci.yml`

**修改目的**：使 Hive 模块 CI 工作流覆盖新发布分支。

**工作逻辑**：同上替换。

### `.github/workflows/java-ci.yml`

**修改目的**：使主 Java CI 工作流覆盖新发布分支。

**工作逻辑**：同上替换。

### `.github/workflows/open-api.yml`

**修改目的**：使 OpenAPI 校验工作流覆盖新发布分支。

**工作逻辑**：同上替换。

### `.github/workflows/spark-ci.yml`

**修改目的**：使 Spark 模块 CI 工作流覆盖新发布分支。

**工作逻辑**：同上替换。

## 小结

- **成效**：修复 CI 工作流分支匹配模式遗漏 1.x、2.x 发布分支的 bug，使 7 个工作流（api-binary-compatibility、delta-conversion-ci、flink-ci、hive-ci、java-ci、open-api、spark-ci）能在维护分支 push 时正确触发，恢复对维护分支的自动构建保护。
- **影响范围**：仅 `.github/workflows/` 下 7 个 YAML 文件，纯配置改动，无代码、无运行时影响。
- **回迁到 1.4.x 的注意事项**：本提交本身就是为修复维护分支 CI 触发问题，**强烈建议回迁到 1.4.x**（以及任何其它已存在的维护分支），否则该分支上的 push 仍然不会触发 CI。回迁后该分支自身即可被 CI 覆盖。注意 GitHub Actions 工作流改动只有在该分支上重新推送后才会被 GitHub 读取生效。
