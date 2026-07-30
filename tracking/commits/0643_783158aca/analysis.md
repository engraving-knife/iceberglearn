# 提交 0643：CI: Run Markdown links checker only when `{docs,site}/**` changes

## 提交信息

- **序号**：0643 / 4088
- **哈希**：783158aca9c0287c4990976dc18eafdfb472ae1f
- **短哈希**：783158aca
- **日期**：2024-03-28（Thu Mar 28 11:39:25 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：CI: Run Markdown links checker only when `{docs,site}/**` changes (#10049)
- **PR/Issue**：#10049

## 总体目的

本提交优化 GitHub Actions 的 Markdown 链接检查 CI 工作流，使其在 pull_request 触发时仅当 `docs/**` 或 `site/**` 目录下的文件发生变更时才运行，避免在纯代码改动（不涉及文档）的 PR 上浪费 CI 资源。

背景动机：
- Iceberg 仓库有一个 `docs-check-links.yml` 工作流，使用 `gaurav-nelson/github-action-markdown-link-check` 检查 Markdown 文档中的链接是否有效。
- 原工作流配置中，`push` 事件（指向 main 分支）已设置了 `paths: docs/**, site/**` 过滤，但 `pull_request` 事件未设置 paths 过滤。
- 这导致任何 PR（哪怕只改 Java 代码、不碰文档）都会触发链接检查，浪费 CI 运行时间，也降低 PR 反馈速度。
- 链接检查只对文档变更有意义，因此应与 `push` 事件保持一致的路径过滤。

## 如何达成设计目的

通过 GitHub Actions 工作流语法的 `paths` 过滤器实现：

1. 在 `pull_request` 触发器下新增 `paths` 字段，列出 `docs/**` 和 `site/**` 两个路径模式。
2. GitHub Actions 在 PR 触发时会比对 PR 改动文件是否匹配任一路径模式；若都不匹配，则跳过该工作流。
3. 这与 `push` 事件已有的 `paths` 配置保持一致，使两种触发场景行为统一。
4. `workflow_dispatch`（手动触发）保留无条件运行能力，便于人工排查。

## 修改详情

### `.github/workflows/docs-check-links.yml`

**修改目的**：为 `pull_request` 触发器添加路径过滤，使链接检查仅在文档相关改动时运行。

**工作逻辑**：
- 在 `pull_request:` 键下新增 3 行：
  ```yaml
    pull_request:
      paths:
        - docs/**
        - site/**
  ```
- 修改前 `pull_request:` 下无任何过滤，所有 PR 都触发；修改后仅当 PR 改动 `docs/` 或 `site/` 目录下任意文件时触发。
- 工作流其余部分不变：仍运行 `markdown-link-check` job，使用 `site/link-checker-config.json` 配置和 verbose 模式，checkout 代码后调用 markdown-link-check action。

## 小结

本提交是一次小型 CI 优化，通过添加路径过滤减少不必要的链接检查运行。

成效：
- 纯代码 PR 不再触发文档链接检查，节省 CI 资源、加快 PR 反馈。
- `push`（main）和 `pull_request` 两种触发场景的路径过滤现已一致。
- 手动 `workflow_dispatch` 仍可随时触发，不影响排查能力。

影响范围：
- 仅改 CI 工作流配置，不影响代码和文档内容。

回迁到 1.4.x 注意事项：
- 风险极低，可直接回迁。
- 需确认 1.4.x 分支是否存在该工作流文件及相同结构；若 1.4.x 的文档目录结构不同，需调整 `paths` 模式。
- 注意：`paths` 过滤有一个已知行为——若由于路径过滤导致工作流被跳过，后续基于该工作流状态的 branch protection / required check 可能显示为 "expected" 状态。Iceberg 这里采用直接跳过策略，可接受。
