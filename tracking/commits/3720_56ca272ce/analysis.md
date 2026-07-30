# 提交 3720：Build: Bump pymarkdownlnt from 0.9.36 to 0.9.37 (#16372)

## 提交信息

- **序号**：3720 / 4088
- **哈希**：56ca272ce8979d6f8b42821e0d2b4cde3d49332f
- **短哈希**：56ca272ce
- **日期**：2026-05-16 23:16:33 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.36 to 0.9.37 (#16372)
- **PR/Issue**：#16372

## 总体目的

这是一次由 Dependabot 自动发起的依赖升级提交，将文档站点构建依赖 `pymarkdownlnt`（一个 Markdown lint 工具，用于在 CI 中对项目 Markdown 文档进行风格和质量检查）从 0.9.36 升级到 0.9.37。`pymarkdownlnt` 用于校验 Iceberg 项目 `site/` 目录及文档中的 Markdown 文件，确保文档格式遵循既定规则。本次属于 patch 级别版本升级（0.9.36 → 0.9.37），通常包含 bug 修复与小幅改进，保持 lint 工具为最新以获得最新的规则修复。

## 如何达成设计目的

Dependabot 通过修改 `site/requirements.txt` 中 `pymarkdownlnt` 的版本固定值完成升级，依赖类型为 `direct:production`，更新类型为 `version-update:semver-patch`。升级范围仅限站点构建依赖，不影响产品代码运行时行为。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 pymarkdownlnt 版本。

**工作逻辑**：
将 `pymarkdownlnt==0.9.36` 修改为 `pymarkdownlnt==0.9.37`，使 CI 文档 lint 流程使用最新 patch 版本。

## 总结

本提交是 Dependabot 自动完成的依赖升级，将 Markdown lint 工具 `pymarkdownlnt` 从 0.9.36 升级到 0.9.37（patch 版本）。改动仅涉及文档构建依赖，属于常规的依赖维护，旨在获取最新版本中的 bug 修复与改进，无产品代码影响。
