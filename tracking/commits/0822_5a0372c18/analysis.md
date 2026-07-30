# 提交 0822：Build: Remove links checker (#10404)

## 提交信息

- **序号**：0822 / 4088
- **哈希**：5a0372c18c02ffb7322ed1eaeb20a89ed771f633
- **短哈希**：5a0372c18
- **日期**：2024-06-09 20:56:32 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Build: Remove links checker (#10404)
- **PR/Issue**：#10404（关联 #10397）

## 总体目的

本提交移除了文档链接检查器（links checker）的 GitHub Actions 工作流及其配置文件。提交者 Fokko Driesprong（Iceberg PMC 成员）在提交说明中明确指出原因：在 CI 中运行链接检查器非常不稳定（very flaky），关联的 PR #10397 暴露了该问题，因此决定暂时移除。

链接检查器原本用于自动验证 Iceberg 文档（`docs/` 和 `site/` 目录下）中的 Markdown 链接是否有效，防止文档中出现死链。但由于外部链接（第三方网站）的可用性不可控、网络抖动、部分网站反爬等因素，该 CI 检查频繁出现误报（false negative），导致 CI 不稳定，反而干扰了正常开发流程。移除该检查器是为了恢复 CI 的稳定性。

## 如何达成设计目的

提交通过删除两个文件来达成目的，共删除 69 行，无新增：

1. **删除工作流文件**：移除 `.github/workflows/docs-check-links.yml`，使 GitHub Actions 不再触发链接检查任务。这是实现"移除链接检查"的核心动作——删除工作流定义后，push 和 pull_request 事件不再触发该检查。

2. **删除配置文件**：移除 `site/link-checker-config.json`，该文件是链接检查器的配置（忽略模式、路径替换规则）。工作流被删除后，配置文件已无引用，一并清理以避免遗留无用文件。

3. **不保留替代方案**：提交说明中写的是"Let's remove it for now"（暂时移除），暗示未来可能以更稳定的方式重新引入链接检查，但当前直接移除以解决 CI 稳定性问题。

## 修改详情

### `.github/workflows/docs-check-links.yml`（已删除）

**修改目的**：移除文档链接检查的 CI 工作流，消除因链接检查导致的 CI 不稳定问题。

**工作逻辑**（被删除的工作流原本的逻辑）：
- **工作流名称**：`Check Markdown docs links`
- **触发条件**：
  - `push`：仅当 `docs/**` 或 `site/**` 路径下的文件变更，且目标分支为 `main` 时触发。
  - `pull_request`：当 `docs/**` 或 `site/**` 路径下文件变更时触发。
  - `workflow_dispatch`：支持手动触发。
- **执行任务**：`markdown-link-check`，运行在 `ubuntu-latest` 上。
- **执行步骤**：
  1. 使用 `actions/checkout@v4` 检出代码。
  2. 使用 `gaurav-nelson/github-action-markdown-link-check@v1` 第三方 Action 执行 Markdown 链接检查，配置文件为 `site/link-checker-config.json`，启用 verbose 模式。
- **不稳定原因**：该工具会实际请求文档中所有外部 URL 来验证可达性。由于外部网站（如 LinkedIn、Maven 仓库等）可能临时不可达、限流、或返回非 200 状态码，导致检查结果不稳定，CI 频繁误报失败。

### `site/link-checker-config.json`（已删除）

**修改目的**：清理已无用的链接检查器配置文件。

**工作逻辑**（被删除的配置原本的逻辑）：
- **ignorePatterns（忽略模式）**：以下 URL 模式不进行检查：
  - `^https://www.linkedin.com/`：LinkedIn 链接（通常反爬，不可达）。
  - `^https://mvnrepository.com/`：Maven 仓库网站链接。
  - `^../../javadoc`：相对路径的 Javadoc 链接（本地相对路径，检查器无法验证）。
  - `^https://search.maven.org/`：Maven 中央搜索网站链接。
- **replacementPatterns（替换模式）**：对链接路径进行重写以便检查：
  - `^docs/latest/` 替换为 `{{BASEURL}}/docs/docs/`：将文档中"最新版本"路径映射到实际站点路径。
  - `^../../` 替换为 `{{BASEURL}}/site/docs/`：将相对父路径映射到站点路径。
- 这些配置反映了链接检查器在处理文档相对路径和已知不可靠外部链接时的折中方案，但即便如此仍无法消除不稳定性。

## 小结

- **成效**：消除了 CI 中文档链接检查导致的不稳定（flaky）问题，避免了因外部链接不可达而误报 CI 失败，减少了开发者处理虚假 CI 失败的负担。
- **影响范围**：
  - 负面影响：文档中的死链（broken links）不再被自动检测，可能在后续文档维护中积累无效链接而不被发现。
  - 正面影响：CI 流水线更稳定，docs/site 目录的 PR 不再因链接检查误报而阻塞。
  - 不影响任何源代码、构建逻辑或运行时行为，仅影响文档质量保证流程。
- **回迁注意事项**：回迁到 1.4.x 分支时，需先确认 1.4.x 分支是否存在这两个文件。若 1.4.x 分支尚未引入链接检查器（即这两个文件不存在），则无需回迁此提交。若已存在且同样存在不稳定问题，可直接应用此提交移除。若希望保留链接检查能力，可考虑改用更稳定的替代方案（如仅在手动触发时运行、或使用更宽容的重试策略、或将外部链接检查与内部链接检查分离）。注意提交说明提到"for now"，说明社区有意未来重新引入更可靠的方案，回迁时可关注后续 main 分支是否有重新引入链接检查的提交。
