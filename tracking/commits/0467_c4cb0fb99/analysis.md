# 提交 0467：Docs: Move catalog under concepts folder and add code of conduct page to act as root for ASF. (#9642)

## 提交信息

- **序号**：0467
- **哈希**：c4cb0fb9993d6743d81a232def6801ea7dbbe176
- **短哈希**：c4cb0fb99
- **日期**：2024-02-05 10:03:56 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Docs: Move catalog under concepts folder and add code of conduct page to act as root for ASF. (#9642)
- **PR/Issue**：#9642

## 总体目的

本提交是对 Iceberg 官网（`site/` 目录）导航与文档结构的一次整理，核心目的有两个：一是把 `catalog.md` 从 `site/docs/` 根目录迁移到 `site/docs/concepts/` 子目录，使其在物理位置上与"Concepts"这一导航分类对应，便于后续在 concepts 目录下扩展更多概念性文档；二是调整 `site/nav.yml` 的导航层级，把原本独立的顶级 `ASF` 分类降级为 `Project` 分类下的子节点，同时把 `Project` 分类下的 `Join` 重命名为 `Community`，让 `Project` 成为承载 ASF 相关链接的"根节点"，符合 Apache 项目网站把基金会相关链接收拢到一个统一入口的常见做法。

提交说明中提到的"add code of conduct page to act as root for ASF"，结合 diff 实际内容来看，体现为：原本 ASF 是与 Project、Concepts 并列的顶级导航分类，其中只放了 5 个指向 apache.org 的外链（Sponsorship、Events、License、Security、Sponsors）；本提交把 ASF 整体缩进到 Project 分类之下，使其成为 Project 的子分类，这样 Project 分类（包含 Community、Spec、View spec、Puffin spec、Multi-engine support、How to release、Terms 等内部页面）就充当了 ASF 内容的"根"页面集合。同时把 `Join` 改名为 `Community`，更贴近社区/行为准则入口的命名习惯。这种结构调整让外部 Apache 链接不再孤立地占据顶级导航位，而是收纳到项目社区分类下，导航更紧凑、层级更合理。

此外，本提交顺带清理了 `site/dev/common.sh` 中 `clean()` 函数的清理逻辑，把原本分散的多条 `rm`/`git worktree remove` 命令合并精简，去掉冗余的中间步骤和多余空行，使清理脚本更易读、更不容易遗漏。这部分改动与文档结构调整属于同一 PR 的附带清理，目的是让 site 构建脚本与新的目录布局保持一致。

## 如何达成设计目的

实现路径分三步：第一步用 `git mv`（在 diff 中体现为 rename）把 `site/docs/catalog.md` 移动到 `site/docs/concepts/catalog.md`，文件内容不变（`similarity index 100%`）；第二步修改 `site/nav.yml`，调整 YAML 缩进让 ASF 成为 Project 的子节点，更新 Catalogs 条目的路径为 `concepts/catalog.md`，并把 `Join` 重命名为 `Community`；第三步精简 `site/dev/common.sh` 的 `clean()` 函数，把多条删除命令收敛为更紧凑的写法。三处改动互相配合：文件移动需要 nav.yml 同步更新路径，否则导航链接会失效；clean 函数的清理则需要覆盖到移动后的目录结构。

## 修改详情

### site/dev/common.sh

**修改目的**：精简 `clean()` 函数中清理 site 构建产物的逻辑，使其更紧凑、更易维护。

**工作逻辑**：原 `clean()` 函数在移除 `docs/docs/latest` 后，先分别调用 `git worktree remove docs/docs` 和 `git worktree remove docs/javadoc`，再单独 `rm -f docs/.asf.yaml`，最后再用一段注释 "Remove any additional temporary artifacts (e.g., 'site/' directory)" 配合 `rm -rf site/` 清理 site 目录，且函数末尾留有一个空行。

修改后：在 `rm -rf docs/docs/latest` 之后保留一个空行用于可读性；保留两条 `git worktree remove` 调用（docs/docs 与 docs/javadoc）；把原先单独的 `rm -f docs/.asf.yaml` 与 `rm -rf site/` 合并为一行 `rm -rf docs/javadoc docs/docs docs/.asf.yaml site/`，注释也简化为 "Remove any remaining artifacts"。同时去掉了函数末尾多余的空行。整体效果是把 4 条删除命令合并为 2 条（git worktree remove 两条保留，rm 合并为一条），减少重复、提升可读性。需要注意 `rm -rf docs/javadoc docs/docs` 这一行实际上与前面 `git worktree remove` 的目标重叠，属于"兜底"清理——当 worktree remove 因各种原因失败（命令已加 `&> /dev/null` 静默错误）时，rm 兜底强制删除。

### site/docs/catalog.md -> site/docs/concepts/catalog.md

**修改目的**：把 catalog 概念文档从 docs 根目录迁移到 concepts 子目录，使物理目录结构与 nav.yml 中的 "Concepts" 分类对齐。

**工作逻辑**：纯文件移动，`similarity index 100%` 表示文件内容字节级未变。迁移后路径为 `site/docs/concepts/catalog.md`，对应 nav.yml 中 `Concepts -> Catalogs: concepts/catalog.md` 的引用。这种调整让后续在 concepts 目录下新增更多概念性文档（如 table、partition、snapshot 等概念页）时有统一的归宿，避免 docs 根目录文件膨胀。

### site/nav.yml

**修改目的**：调整官网导航层级，把 ASF 收纳到 Project 之下，更新 catalog 路径，重命名 Join 为 Community。

**工作逻辑**：nav.yml 是网站导航的声明式配置，缩进决定层级。改动可拆为三部分：

1. **`Join` 重命名为 `Community`**：在 `Project:` 分类下，把 `- Join: community.md` 改为 `- Community: community.md`。仅显示名变化，指向的文件不变。这一改名更符合 Apache 项目社区入口的常见命名，也呼应提交说明中"code of conduct page"的定位（community.md 通常承载社区参与、行为准则等内容）。

2. **ASF 降级为 Project 的子分类**：原结构为：
   ```yaml
   - ASF:
     - Sponsorship: https://www.apache.org/foundation/sponsorship.html
     - Events: https://www.apache.org/events/current-event.html
     - License: https://www.apache.org/licenses/
     - Security: https://www.apache.org/security/
     - Sponsors: https://www.apache.org/foundation/thanks.html
   ```
   ASF 是顶级分类。修改后 ASF 被缩进到 Project 之下：
   ```yaml
     - ASF:
       - Sponsorship: https://www.apache.org/foundation/sponsorship.html
       - Events: https://www.apache.org/events/current-event.html
       - License: https://www.apache.org/licenses/
       - Security: https://www.apache.org/security/
       - Sponsors: https://www.apache.org/foundation/thanks.html
   ```
   注意缩进从 0 变为 2（隶属于 Project），子链接缩进相应从 2 变为 4。这样 ASF 不再独占一个顶级导航位，而是作为 Project 分类下的子节点，使 Project 成为 ASF 链接的"根"。5 个外链本身（URL 与显示名）均未改动。

3. **Concepts 分类路径更新**：原本就是顶级分类：
   ```yaml
   - Concepts:
     - Catalogs: catalog.md
   ```
   现在因文件迁移，路径更新为：
   ```yaml
   - Concepts:
     - Catalogs: concepts/catalog.md
   ```
   Concepts 仍是顶级分类，但指向的 markdown 路径反映了新的目录结构。注意 Concepts 在 YAML 中原本位于 ASF 之后，现在因 ASF 被移入 Project，Concepts 自然成为 Project 之后的下一个顶级分类，顺序保持不变。

## 小结

本提交是 Iceberg 1.4.x 落后于 main 的一个官网结构与导航整理类提交，共改动 3 个文件（12 行新增、14 行删除）。核心动作是把 `site/docs/catalog.md` 迁移到 `site/docs/concepts/catalog.md` 并同步更新 `site/nav.yml` 中的路径引用，同时把顶级 ASF 导航分类降级为 Project 分类下的子节点（让 Project 充当 ASF 链接的"根"），并把 Project 下的 `Join` 重命名为 `Community`。附带精简了 `site/dev/common.sh` 的 `clean()` 函数，把分散的删除命令合并。改动纯文档/配置层面，不涉及任何运行时代码，风险低，但对官网导航的清晰度和后续 concepts 文档的扩展性有正面影响。
