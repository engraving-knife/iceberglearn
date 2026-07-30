# 提交 4068：Build: Bump pymarkdownlnt from 0.9.38 to 0.9.39 (#17289)

## 提交信息

- **序号**：4068 / 4088
- **哈希**：25f53e177dbbd859a6431548c100ddc6bfe2ea83
- **短哈希**：25f53e177
- **日期**：2026-07-18 22:26:33 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.38 to 0.9.39 (#17289)
- **PR/Issue**：#17289

## 总体目的

Dependabot 自动升级提交，将 `pymarkdownlnt`（PyMarkdownLint）从 0.9.38 升级到 0.9.39（semver patch 补丁版本升级）。`pymarkdownlnt` 是一个 Python Markdown 文档 lint 工具，Iceberg 在网站文档构建流程中使用它检查 Markdown 文档的格式规范性。patch 版本升级包含 bug 修复，属于低风险维护升级。

## 如何达成设计目的

在 `site/requirements.txt` 中将版本固定从 `0.9.38` 改为 `0.9.39`。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 pymarkdownlnt 版本。

**工作逻辑**：
```
pymarkdownlnt==0.9.39
```
将版本从 `0.9.38` 改为 `0.9.39`。

## 总结

常规的文档 lint 工具补丁版本升级，保持网站文档检查工具基于最新补丁版本。patch 级别升级风险很低。
