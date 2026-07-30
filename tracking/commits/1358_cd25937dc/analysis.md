# 提交 1358：Docs: Adds Release notes for 1.6.1 (#11500)

## 提交信息

- **序号**：1358 / 4088
- **哈希**：cd25937dc7438637d11cd54d2065d391bdc6d405
- **短哈希**：cd25937dc
- **日期**：2024-11-08（Fri Nov 8 14:46:15 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Docs: Adds Release notes for 1.6.1 (#11500)
- **PR/Issue**：#11500

## 总体目的

Iceberg 1.6.1 早于 2024-08-27 发布，但官网 `site/docs/releases.md` 一直未补写 1.6.1 的发布说明章节，读者只能看到 1.7.0 与 1.6.0 之间跳变，缺少 1.6.1 的变更摘要。1.7.0 发布站点更新（提交 1355）后，作者补回这一遗漏的 1.6.1 章节。

本提交在 `releases.md` 的 1.7.0 章节之后、1.6.0 章节之前插入 "### 1.6.1 release" 段落，简要说明发布日期、变更性质（bug 修复与性能改进），并列出 Core 与 Dependencies 两类变更条目。

## 如何达成设计目的

直接编辑 `site/docs/releases.md`，在 1.7.0 章节末尾之后插入 1.6.1 章节文本。这是纯文档补全，无代码逻辑。文本风格与既有 release notes 章节保持一致（发布日期 + 概述 + 模块分组条目 + PR 链接）。

## 修改详情

### `site/docs/releases.md`

**修改目的**：补写 1.6.1 的发布说明章节。

**工作逻辑**：在 1.7.0 章节之后插入：

```markdown
### 1.6.1 release

Apache Iceberg 1.6.1 was released on August 27, 2024.

The 1.6.1 Release contains bug fixes and performance improvements. For full release notes visit [Github](https://github.com/apache/iceberg/releases/tag/apache-iceberg-1.6.1)

* Core
  - Limit ParallelIterable memory consumption by yielding in tasks ([#10787](.../#10787))
  - Drop ParallelIterable's queue low water mark ([#10979](.../#10979))
* Dependencies
  - ORC 1.9.4
```

章节开头给出发布日期与变更性质概述，并指向 GitHub 完整 release notes；随后按模块列出主要变更：

- **Core**：限制 `ParallelIterable` 内存占用（在任务中 yield）、移除 `ParallelIterable` 队列低水位
- **Dependencies**：ORC 升级到 1.9.4

注意：条目中的 PR 链接形式为 `https://github.com/apache/iceberg/#10787`（缺少 `pull` 路径段），与 1.7.0 章节中 `pull/10787` 的形式略有不同，是 1.6.1 章节链接的小瑕疵，但不影响读者定位 PR。

## 小结

- **成效**：官网发布说明补全 1.6.1 章节，读者可在版本时间线上看到完整的 1.7.0 → 1.6.1 → 1.6.0 顺序及其变更摘要。
- **影响范围**：仅 `site/docs/releases.md` 一个文件，新增 12 行，无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：这是 main 分支官网对历史发布说明的补全，与 1.4.x 维护分支的发布产物无关。1.4.x 不需要回迁此 1.6.1 发布说明——它属于 main 分支站点内容。**无需回迁**。
