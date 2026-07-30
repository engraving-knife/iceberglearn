# 提交 1796：Docs: Site updates for 1.8.1 (#12410)

## 提交信息

- **序号**：1796 / 4088
- **哈希**：4b592d08e4412468532bb923f1918b988f80c900
- **短哈希**：4b592d08e
- **日期**：2025-02-28 08:58:39 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Docs: Site updates for 1.8.1 (#12410)
- **PR/Issue**：#12410

## 总体目的

此提交用于在 Iceberg 站点文档中发布 1.8.1 版本的相关信息，配合 1.8.1 版本于 2025 年 2 月 28 日发布。主要做三件事：

1. **在发布说明中新增 1.8.1 release 章节**：列出 1.8.1 包含的 bug 修复（Core、Parquet）与依赖变更（降级 AWS SDK），并链接到 GitHub release 页面，让用户了解本次发布内容。

2. **更新站点全局版本变量**：将 `mkdocs.yml` 中的 `icebergVersion` 从 `1.8.0` 改为 `1.8.1`，使站点各处引用 `{{ icebergVersion }}` 的位置（如下载链接、版本提示）自动指向 1.8.1。

3. **在导航中注册 1.8.1 版本文档**：在 `nav.yml` 中新增 `1.8.1` 版本文档入口，并把 “Past releases” 标题位置上移到 1.8.0 之前，使 1.8.1 作为最新版本出现在导航顶部。

这是发布流程中的站点文档同步步骤，属于纯文档/配置类修改。

## 如何达成设计目的

通过编辑站点配置与发布说明三个文件达成目标：

1. `site/docs/releases.md`：新增 1.8.1 release 小节，并调整 “Past releases” 标题层级位置；
2. `site/mkdocs.yml`：更新 `icebergVersion` 全局变量；
3. `site/nav.yml`：在导航 Docs 下新增 1.8.1 文档 include 入口。

## 修改详情

### `site/docs/releases.md`（修改, +19/-2 lines）

**修改目的**：新增 1.8.1 发布说明并调整历史发布分区结构。

**工作逻辑**：
- 在 “### Gradle” 与 “### Maven” 之后、原 1.8.0 release 小节之前，插入新的 “### 1.8.1 release” 小节，内容为：
  - 发布日期：February 28, 2025；
  - 概述：1.8.1 包含 bug 修复与 LICENSE/NOTICE 文件修复，链接到 GitHub release tag `apache-iceberg-1.8.1`；
  - Core 修复清单（含 PR 链接）：不剥离绝对路径尾部斜杠（#12390）、namespace/table/view 存在性检查回退到 GET 请求（#12328）、从默认实现中移除 HEAD 端点（#12368）、调整 Jackson 设置以处理大 metadata json（#12330）、无当前快照时重新写 “-1”（#12313）；
  - Parquet 修复：reader 初始化性能回归（#12329）；
  - 依赖：降级 AWS SDK 到 2.29.52（#12339）。
- 在 1.8.1 小节之后新增 `## Past releases` 二级标题，并把原本位于 1.7.1 小节之后的 `## Past releases` 删除。这样 1.8.1 与 1.8.0 仍在新版区，1.7.1 及更早版本归入 Past releases。

### `site/mkdocs.yml`（修改, +1/-1 lines）

**修改目的**：将站点全局 Iceberg 版本变量更新为 1.8.1。

**工作逻辑**：`extra` 下的 `icebergVersion: '1.8.0'` 改为 `icebergVersion: '1.8.1'`。该变量在 releases.md 等页面通过 `{{ icebergVersion }}` 引用，用于生成最新版本的下载链接与提示文字。修改后站点所有引用处自动指向 1.8.1。

### `site/nav.yml`（修改, +1/-0 lines）

**修改目的**：在站点导航中新增 1.8.1 版本文档入口。

**工作逻辑**：在 `nav` 的 Docs 列表中，`latest` 之后插入：
```yaml
- 1.8.1: '!include docs/docs/1.8.1/mkdocs.yml'
```
位于 `1.8.0` 之前。这要求 `docs/docs/1.8.1/mkdocs.yml` 已存在（由版本化文档归档流程提供，参见提交 1793 的 how-to-release 文档）。

## 小结

- **成效**：站点文档完成 1.8.1 发布同步，包括发布说明、全局版本变量、导航入口，用户访问站点即可看到 1.8.1 为最新版本及其修复清单。
- **影响范围**：仅影响站点配置与文档（`site/docs/releases.md`、`site/mkdocs.yml`、`site/nav.yml`），不涉及代码、构建脚本或测试。
- **回迁到 1.4.x 的注意事项**：这是针对 1.8.1 发布的站点更新，**不建议回迁到 1.4.x 分支**。原因：
  1. 1.4.x 是更早的发布线，其最新版本与发布说明内容与 1.8.1 完全不同，回迁会造成站点版本信息错乱；
  2. 该提交依赖 1.8.1 版本化文档目录（`docs/docs/1.8.1/mkdocs.yml`）已存在，1.4.x 分支无此目录；
  3. 若 1.4.x 需要类似的站点更新，应针对 1.4.x 自身的发布版本单独操作，而非套用 1.8.1 的内容。
