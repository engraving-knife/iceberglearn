# 提交 0805：Docs: Refer to the README.md in `site/` for the docs (#10402)

## 提交信息

- **序号**：0805 / 4088
- **哈希**：252168419f8ac1d251b39f0944a189184056e543
- **短哈希**：252168419
- **日期**：2024-06-03 08:11:33 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Docs: Refer to the README.md in `site/` for the docs (#10402)
- **PR/Issue**：#10402

## 总体目的

本次提交是一次文档维护性整理，核心目的是**消除文档发布流程说明的重复**——把 `site/docs/how-to-release.md` 中已经过时且与 `site/README.md` 重复的"Documentation Release"详细步骤删除，改为指向 `site/README.md`。同时顺手对 `site/README.md` 做了一系列文字与格式润色（修复拼写错误、补全句号、为代码块添加 `sh` 语言标记以启用语法高亮）。

背景：
- `site/README.md` 是仓库根 `site/` 子项目的说明文件，描述了 MkDocs 文档站点的目录结构、构建流程、`make` 命令使用等，活在 `main` 分支上，可以随时更新。
- `site/docs/how-to-release.md` 是面向发布管理员的版本化文档（属于 MkDocs 站点的 `docs/` 目录），会被打包进各版本文档站点供社区参考；其中"Documentation Release"一节给出了基于 `iceberg-docs` 仓库（一个独立于主仓库的文档发布仓库）的手工命令步骤。
- 随着文档站点架构演进到使用 `site/Makefile` + `make release` / `make deploy` 的自动化方式（详见 `site/README.md` 的 "Deploying the docs" 小节），`how-to-release.md` 中描述的旧流程已经过时且重复，维护两份会产生同步漂移风险。本提交通过"单一信息源"原则，将权威说明集中到 `site/README.md`。

## 如何达成设计目的

提交人对两个文档文件做了**互补的两类操作**：
1. **`site/docs/how-to-release.md`**：删除约 70 行旧的文档发布手工步骤，替换为一句指向 `site/README.md` 的链接——避免内容重复，也避免向用户介绍已废弃的 `iceberg-docs` 仓库工作流。
2. **`site/README.md`**：对仍保留的说明做质量提升（拼写、标点、代码块语法标记），让被引用的"权威源"更准确更易读。

这样修改后，发布流程的描述只剩一份权威文档（`site/README.md`），后续维护只需更新一处，避免两份文档同步漂移。

## 修改详情

### `site/docs/how-to-release.md`

**修改目的**：删除"Documentation Release"小节下约 70 行的旧版手工发布步骤，改为单行指向 `site/README.md` 的链接。

**工作逻辑**：

被删除的内容是"### Documentation Release"标题下完整的 6 个子小节，描述了基于 `iceberg-docs` 独立仓库的手工发布流程：
- `#### Common documentation update`：将 `iceberg/format/*` 拷贝到 `iceberg-docs/landing-page/content/common/`，在 `iceberg-docs` 仓库创建分支并开 PR。
- `#### Versioned documentation update`：在 `iceberg-docs` 仓库用版本号切分支，把 `iceberg/docs` 拷贝到 `iceberg-docs/docs/content`，开 PR。
- `#### Javadoc update`：在 `iceberg` 仓库执行 `./gradlew refreshJavadoc` 生成 javadoc，再拷贝到 `iceberg-docs/javadoc`。
- `#### Update the latest branch`：在 `iceberg-docs` 仓库 `git checkout latest && git rebase main && git push apache latest`。
- `#### Set latest version in iceberg-docs repo`：手动修改 `landing-page/config.toml` 与 `docs/config.toml` 中的 `latestVersions.iceberg`、`versions.nessie`、`versions` 列表，并更新 release-notes。

这些步骤基于**已被废弃的 `iceberg-docs` 独立仓库**工作流。当前 Iceberg 文档站架构已迁移到主仓库 `site/` 下的 `Makefile` 自动化（`make release` / `make deploy`，详见 `site/README.md` 的 "Deploying the docs" 与 "Building the versioned docs" 小节，使用 `docs` 与 `javadoc` 两个 orphan 分支 + `git worktree` 挂载）。因此上述手工步骤不再适用，保留只会误导发布管理员。

替换为单行：

```markdown
Please follow the instructions on the GitHub repository in the [`README.md` in the `site/`](https://github.com/apache/iceberg/tree/main/site) directory.
```

此链接指向 GitHub 上 `apache/iceberg` 仓库 `main` 分支的 `site/` 目录（即 `site/README.md`），让发布管理员从权威源获取最新发布流程。

### `site/README.md`

**修改目的**：对作为权威源的 README 进行拼写、标点与代码块语法标记的润色，提升可读性与渲染效果。

**工作逻辑**（逐处说明）：

1. **删除空行**：在 `## Requirements` 列表（`* Python >=3.9`、`* pip`）与 `## Usage` 之间删除多余空行，使段落间距更紧凑一致。

2. **补全句号**（第 38 行附近）：
   ```diff
   -...share the same naming convention as MkDocs but does not correlate to the mkdocs
   +...share the same naming convention as MkDocs but does not correlate to the mkdocs.
   ```
   修复句子末尾缺失的句号。

3. **添加空行**（在目录树代码块后）：
   ```diff
        ├── mkdocs.yml
        └── requirements.txt
    ```
    +
    ### Building the versioned docs
   ```
   在代码块结束后与下一个标题之间加入空行，符合 Markdown 规范（代码块后需空行才能正确解析后续内容），改善 MkDocs 渲染。

4. **修复拼写**（`documenation` → `documentation`）：
   ```diff
   - 1. [`docs`](...) - contains the state of the documenation source files (`/docs`) during release. ...
   + 1. [`docs`](...) - contains the state of the documentation source files (`/docs`) during release. ...
   ```

5. **代码块添加 `sh` 语言标记**（5 处）：将 ` ``` ` 改为 ` ```sh `，让 MkDocs Material 主题对 shell 命令启用语法高亮（关键字、变量、字符串着色）：
   - `make serve`
   - `make clean`
   - `make build OFFLINE=true`
   - `make release ICEBERG_VERSION=${ICEBERG_VERSION}`
   - `make deploy`

   语法高亮能显著提升可读性，让 `${ICEBERG_VERSION}` 这样的变量更醒目。

6. **修复拼写与措辞**（第 169 行附近）：
   ```diff
   -...it expects outside of the immediate poject due to being off by one or more directories. ...
   +...it expects outside the immediate project due to being off by one or more directories. ...
   ```
   修复 `poject` → `project` 拼写错误，并把 `outside of the immediate` 简化为 `outside the immediate`（更简洁）。

## 小结

- **成效**：
  - 消除了文档发布流程说明的重复，将权威源集中到 `site/README.md`，降低后续维护成本与同步漂移风险。
  - 移除了基于已废弃 `iceberg-docs` 独立仓库的过时手工步骤，避免误导发布管理员。
  - 对 `site/README.md` 做了多处质量提升（拼写、标点、代码块语法高亮），改善文档渲染效果与可读性。
- **影响范围**：
  - 仅影响 `site/` 子项目下的文档内容，不影响任何 Java/Python 源码或构建产物。
  - `site/docs/how-to-release.md` 是版本化文档，会随各版本文档站点发布，本次修改后下次发布版本将自动反映新内容（旧版本文档不受影响，因为它们已固化在 `docs` orphan 分支）。
  - `site/README.md` 仅在 `main` 分支仓库内可见，不会被打包进发布的文档站点。
- **回迁注意事项**：
  - 该提交仅修改两个 Markdown 文档，无源码改动，cherry-pick 到 1.4.x 分支无冲突风险。
  - **关键评估**：1.4.x 分支的 `site/docs/how-to-release.md` 与 `site/README.md` 内容可能与本提交前版本差异较大（尤其是 1.4.x 时期文档发布流程可能仍在过渡中），cherry-pick 前需确认：
    - 1.4.x 的 `site/README.md` 是否已包含 `make release` / `make deploy` 的说明（即文档站点是否已迁移到 Makefile 架构）。若 1.4.x 仍依赖旧 `iceberg-docs` 工作流，则不应回迁本提交中删除 how-to-release.md 详细步骤的部分——否则发布管理员将无可用指引。
    - 拼写/标点/代码块语法标记的润色部分（README 润色）可独立回迁，风险低。
  - 若 1.4.x 已是 EOL（end-of-life）维护分支，且其文档发布流程与 main 一致，则可整体回迁；否则建议仅回迁 README 润色部分，保留 how-to-release.md 中与 1.4.x 实际流程匹配的步骤。
  - 回迁后建议本地运行 `make serve` 验证文档渲染正常，确认 ` ```sh ` 语法标记被正确识别为 shell 代码块。
