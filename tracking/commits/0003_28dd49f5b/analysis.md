# 提交 0003：Python: Update pre-commit (#8651)

## 提交信息

- **序号**：0003 / 4088
- **哈希**：28dd49f5bf0c1e8d4793faeaa7d0bdbc29ebec56
- **短哈希**：28dd49f5b
- **日期**：2023-09-28 15:47:38 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Python: Update pre-commit (#8651)
- **PR/Issue**：#8651

## 总体目的

这个提交是 Iceberg Python 侧的纯工具链升级提交，不涉及任何业务代码改动。Iceberg Python 项目通过 [pre-commit](https://pre-commit.com/) 在本地与 CI 中统一管理多个代码质量工具（ruff 做 lint、black 做格式化、mypy 做类型检查、pycln 做未用 import 清理、mdformat 做 Markdown 格式化）。这些工具的版本在 `.pre-commit-config.yaml` 中以 `rev` 字段钉死，定期升级可以获取新规则、新特性与 bug 修复，避免长期固化在旧版本上导致与社区脱节。

本次升级涵盖五个工具：ruff 由 v0.0.286 升到 v0.0.291（跨 5 个 patch 版本）、black 由 23.3.0 升到 23.9.1、mypy 由 v1.3.0 升到 v1.5.1、pycln 由 v2.1.5 升到 v2.2.2、mdformat 由 0.7.16 升到 0.7.17。这些都是同主版本内的次版本升级，属于相对安全的工具链更新。这类提交在开源项目中通常作为常规维护的一部分，由维护者定期提交，以保证 lint/格式化/类型检查的结果与上游工具行为一致，也为后续启用新规则（见下一个提交 0004 "Python: Add more Ruff rules"）铺路——很多新规则只有在新版本 ruff 中才存在。

## 如何达成设计目的

整体设计思路就是"更新版本号钉死值"。`.pre-commit-config.yaml` 中每个 `- repo:` 块对应一个工具，`rev` 字段指定该工具的 Git tag。本提交逐一将五个工具的 `rev` 升级到最新稳定版，pre-commit 在运行时会自动拉取新版本镜像执行 hook，无需改动任何业务代码。改动范围极小（5 行 +5 行 -），风险低，价值在于保持工具链的现代性。

## 修改详情

### `python/.pre-commit-config.yaml`

**修改目的**：升级 pre-commit 管理的五个工具版本。

**工作逻辑**：
- ruff（lint）：`v0.0.286` → `v0.0.291`。ruff 在 0.0.x 阶段迭代很快，5 个版本间会引入新规则与修复既有规则的误报。
- black（格式化）：`23.3.0` → `23.9.1`。black 的稳定版本号采用 `年.月.修订` 格式，23.9.1 是 2023 年 9 月的稳定版，相对于 23.3.0 包含半年间的格式化行为微调与 bug 修复。
- mypy（类型检查）：`v1.3.0` → `v1.5.1`。mypy 1.x 阶段相对稳定，1.5.1 相对 1.3.0 引入了类型推断改进与新选项。
- pycln（未用 import 清理）：`v2.1.5` → `v2.2.2`。
- mdformat（Markdown 格式化）：`0.7.16` → `0.7.17`。

各 hook 的 `args`/`additional_dependencies` 配置保持不变，仅升级工具本体版本。

## 小结

本提交作为 Python 侧常规工具链维护，把 pre-commit 管理的 ruff/black/mypy/pycln/mdformat 五个工具升级到最新稳定版，为后续启用新 lint 规则与保持格式化/类型检查行为与社区同步奠定基础。
