# 提交 3725：Build: Bump actions/labeler from 6.0.1 to 6.1.0 (#16378)

## 提交信息

- **序号**：3725 / 4088
- **哈希**：6a3f7827f7695fe883336dcab76246db11a3a4b7
- **短哈希**：6a3f7827f
- **日期**：2026-05-17 10:03:08 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/labeler from 6.0.1 to 6.1.0 (#16378)
- **PR/Issue**：#16378

## 总体目的

Dependabot 自动发起的 GitHub Action 依赖升级，将 `actions/labeler` 从 6.0.1 升级到 6.1.0。`actions/labeler` 是 GitHub 官方提供的 PR 自动打标签 Action，根据修改的文件路径自动为 PR 添加配置好的标签。Iceberg 项目在 `.github/workflows/labeler.yml` 工作流中使用它对提交的 PR 进行自动分类打标。本次为 minor 版本升级（6.0.1 → 6.1.0），可能包含新功能与改进。

## 如何达成设计目的

Dependabot 修改 `labeler.yml` 中引用 `actions/labeler` 的 commit SHA 与版本注释，将其从 6.0.1 对应的 SHA 更新到 6.1.0 对应的 SHA。依赖类型为 `direct:production`，更新类型为 `version-update:semver-minor`。

## 修改详情

### `.github/workflows/labeler.yml` (+1/-1 lines)

**修改目的**：升级 labeler Action 版本。

**工作逻辑**：
将 `uses: actions/labeler@634933edcd8ababfe52f92936142cc22ac488b1b # v6.0.1` 修改为 `uses: actions/labeler@f27b608878404679385c85cfa523b85ccb86e213 # v6.1.0`，使 PR 自动打标签流程使用最新版本。`sync-labels: true` 配置保持不变。

## 总结

本提交是 Dependabot 自动完成的 GitHub Action 依赖升级，将 PR 自动打标签 Action `actions/labeler` 从 6.0.1 升级到 6.1.0（minor 版本）。改动仅涉及工作流配置中的 Action 版本引用，属于常规 CI 依赖维护，旨在获取最新版本的功能与改进。
