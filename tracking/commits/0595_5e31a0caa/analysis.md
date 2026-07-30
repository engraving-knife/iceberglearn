# 提交 0595：移除过时的 Roadmap 文档页

## 提交信息

- **序号**：0595 / 4088
- **哈希**：5e31a0caa360a1a60198a0be6af93b689b19ac19
- **短哈希**：5e31a0caa
- **日期**：2024-03-15
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：docs: Remove roadmap (#9941)
  - 正文：For now. It is outdated and might confuse users.
- **PR/Issue**：#9941

## 总体目的

本提交移除 Iceberg 官网上的 Roadmap（路线图）文档页面，因为该页面内容已过时，可能误导用户。

Roadmap 页面最早由 PR #3163（commit 29cbe5a73，2021-09-22）添加，列出了 Iceberg 社区正在推进的各类项目，每项链接到一个 GitHub Project Board 用于跟踪状态。然而自 2021 年添加后，该页面在长达两年半的时间里基本未更新，而 Iceberg 项目本身在这期间已大幅演进：

- **Views Support**（Spec V2 Views）：已在 spec v2 中规范并实现。
- **Snapshot tagging and branching**：已实现（`ManageSnapshots`、分支/标签 API）。
- **Delete File compaction**：已实现（`RewriteDeleteFiles` action）。
- **Z-ordering / Space-filling curves**：已实现（`zorder` 排序变换）。
- **Inline file compaction**：已实现（`RewriteDataFiles` 的相关策略）。
- **Change Data Capture (CDC)**：通过 `create_changelog_view` 等过程部分支持。
- **Encryption**（Spec V3）：正在推进中（例如本批次的 0592 号提交就是 manifest 加密支持）。

由于大量路线图项目已完成或演进，而页面仍停留在 2021 年的状态，用户看到的"正在进行中"的项目实际上早已落地，这会：
1. 让用户误以为某些已实现的功能尚未支持，影响技术选型决策。
2. 让用户跟随链接访问已归档或失效的 GitHub Project Board，造成困惑。
3. 损害项目"活跃维护"的形象（文档看起来无人维护）。

作者选择"先移除"（"For now"），即不立即替换为新路线图，而是先消除误导信息，后续再考虑是否以新形式（如 GitHub Discussions、Issues 里程碑等）重新提供路线图。

## 如何达成设计目的

实现方式非常直接：删除 `site/docs/roadmap.md` 文件，并从 `site/nav.yml` 导航配置中移除对应条目。

1. **删除文档文件**：`site/docs/roadmap.md` 是 mkdocs 源文件，包含路线图正文（General / Clients / Spec V2 / Spec V3 四个分区，共 56 行）。删除后该页面不再生成。
2. **更新导航**：`site/nav.yml` 是 mkdocs 的导航栏配置，其中 `Roadmap: roadmap.md` 一行被移除，使官网导航栏不再显示 Roadmap 入口。
3. **保留后续恢复可能**：commit message 中的"For now"暗示这是临时移除，未来可能在内容更新后以新形式恢复。

不采用"更新路线图内容"而采用"直接移除"的原因推测：
- 维护一个准确的路线图需要持续投入，且 Apache 项目的优先级由社区共识驱动，难以长期承诺固定路线图。
- GitHub Project Board 的链接维护成本高，且 GitHub 多次调整 Projects 功能（从 classic projects 到 Projects v2），旧链接容易失效。
- 移除比维护一个可能再次过时的页面更稳妥。

## 修改详情

### `site/docs/roadmap.md`（删除）

**修改目的**：移除过时的路线图文档。

**工作逻辑**：该文件被整体删除（56 行）。原文件内容包含：
- Front matter：`title: "Roadmap"`。
- Apache 许可证声明。
- "Roadmap Overview"段落：说明路线图列出社区正在推进的项目，每项链接到 GitHub Project Board。
- **General** 区：8 个项目（多表事务、Views、CDC、快照标签/分支、内联压缩、Delete 文件压缩、Z-ordering、UPSERT）。
- **Clients** 区：Python、Rust、Go 三个客户端仓库链接。
- **Spec V2** 区：Views Spec、DSv2 streaming improvements、Secondary indexes。
- **Spec V3** 区：Encryption、Relative paths、Default field values。

### `site/nav.yml`

**修改目的**：从官网导航栏移除 Roadmap 入口。

**工作逻辑**：删除导航配置中的 `- Roadmap: roadmap.md` 一行。该行位于 `Releases` 与 `Blogs` 之间，删除后导航栏从 `Releases` 直接过渡到 `Blogs`。

## 小结

本提交是纯文档清理，删除 56 行文档 + 1 行导航配置，共 57 行删除、0 行新增。

**成效**：
- 移除了已过时两年半的路线图页面，避免误导用户。
- 简化了官网导航（少一个入口）。
- 体现了项目维护者对文档准确性的重视——宁可移除也不保留误导信息。

**影响范围**：
- 仅影响 `site/` 目录下的官网 mkdocs 源文件，不影响代码与 API。
- 不影响 `docs/docs/` 下的主文档（那是仓库内文档，与 `site/docs/` 是不同的文档集）。
- 官网构建后，Roadmap 页面与导航入口将消失。

**回迁到 1.4.x 的注意事项**：
- 本提交为纯文档删除，回迁风险极低。
- 回迁前应确认 1.4.x 分支的 `site/docs/roadmap.md` 与 `site/nav.yml` 是否与 main 分支一致。若 1.4.x 的路线图内容不同（例如已更新），则不应盲目回迁删除。
- 该提交属于"清理型"改动，对 1.4.x 的功能无任何影响，回迁优先级最低，可酌情跳过。
- 若 1.4.x 仍保留 roadmap 且内容同样过时，建议回迁此删除以保持与 main 的一致性。
