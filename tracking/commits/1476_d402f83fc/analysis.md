# 提交 1476：Docs: Add guidelines for contributors to become committers (#11670)

## 提交信息

- **序号**：1476 / 4088
- **哈希**：d402f83fc7b224b21242c506cf503e5bcbc8c867
- **短哈希**：d402f83fc
- **日期**：2024-12-09（Mon Dec 9 11:20:45 2024 -0800）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Docs: Add guidelines for contributors to become committers (#11670)
- **PR/Issue**：#11670
- **共作者**：Fokko Driesprong <fokko@apache.org>、Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

Apache Iceberg 是 ASF（Apache 软件基金会）项目，遵循 Apache Way 的"精英制"（meritocracy）治理模型——贡献者通过持续高质量参与逐步建立信任，最终被 PMC（项目管理委员会）邀请成为 committer。但许多贡献者尤其是新人并不清楚：

- committer 具体要做什么？
- PMC 在投票时关注哪些品质？
- 多少贡献量"够"成为 committer？
- 没有明确路径时如何自我评估？

这种信息不对称会让一部分潜在贡献者因迷茫而流失，也可能让另一些人误以为"提交够多的 PR 就能成为 committer"。本提交的目的就是在已有的 `site/docs/community.md` 社区文档中新增一个完整章节《The Path from Contributor to Committer》，把 PMC 内部评价候选人的标准显式化、透明化，让贡献者能据此自我对照、有的放矢地积累信任。

这是 ASF 项目"治理透明化"的常见实践，也呼应了 Iceberg 社区在 2024 年加速扩张、需要更多 committer 分担 review 工作的现实需求。

## 如何达成设计目的

通过修改 `site/docs/community.md`，在原有"Participants with Corporate Interests"和"Recruitment"段之后，新增一个二级标题章节"## The Path from Contributor to Committer"，下设五个三级子标题：What are the responsibilities of a committer? / How are new committers added? / What does the PMC look for? / How do I demonstrate those qualities? / How can I be a committer? / How many contributions does it take to become a committer?。

同时在原文中"and abide by the Apache Foundation Code of Conduct"这句话后面新增一行链接，预告"下一节将介绍 contributor 到 committer 的路径"，让读者从社区准则顺承到晋升路径；并把"Apache Foundation"措辞修正为更正式的"Apache Software Foundation"。

## 修改详情

### `site/docs/community.md`

**修改目的**：补充 contributor → committer 晋升路径文档。

**工作逻辑**：

1. **修正措辞 + 新增过渡链接**：

```markdown
-and abide by the Apache Foundation [Code of Conduct](...).
+and abide by the Apache Software Foundation [Code of Conduct](...).
+
+More information specific to the Apache Iceberg community is in the next section, [the Path from Contributor to Committer](#the-path-from-contributor-to-committer).
```

把"Apache Foundation"补全为"Apache Software Foundation"，更符合 ASF 官方称谓；同时新增一句指引，让读者自然过渡到下面的晋升路径章节。

2. **新增核心章节《The Path from Contributor to Committer》**，约 53 行，包含以下要点：

- **committer 的职责**：在 Iceberg 项目中，committer 主要是"review 并合并代码"，reviewing 是首要责任，而非"提交自己的代码"。
- **新增 committer 的流程**：以 ASF 基金会规则为底——由 PMC 提名、讨论、共识投票通过，**这是唯一的正式要求**；不设最低时长或最低贡献数量门槛；也没有"提交够多少就自动成为 committer"的规则。committer 的产生本质是"在 PMC 成员中建立了好判断力与可靠 review 的信任"。
- **PMC 关注的四大品质**：
  - Conduct（行为）：作为项目代表遵循 ASF Code of Conduct；
  - Judgment（判断）：知道何时该独立判断、何时该求助其他领域专家；
  - Quality（质量）：自己的贡献应能反映对项目的理解，不需要大量返工；经常需要指导说明还没准备好指导别人；
  - Consistency（一致性）：review 是持续性工作，要稳定输出。
- **如何展示这些品质**：列出了 PMC 会问的 10 个具体问题，例如"是否在 mailing list/Slack/github 都表现专业""是否有独立的实质贡献""review 的对象是否多元""是否会把可能有问题的变更提到 dev list"等。这些是 contributor 自我对照的清单。
- **成为 committer 的多种路径**：没有单一标准，例如 Python 模块的贡献者通常不会去 review 其他语言的代码；某些领域需要的上下文更多。鼓励不要与他人横向比较，专注"质量与判断"。
- **贡献数量不是关键**：明确说明"贡献数量不是关键，质量（包括 review）才是"。
- **如何获取反馈**：可以随时直接联系 PMC 成员，或发邮件到 `private@iceberg.apache.org`。

## 小结

- **成效**：社区文档现有一份明确、可操作的 contributor → committer 路径说明，把 PMC 内部评价标准透明化，便于贡献者自我评估、有的放矢，并降低信息不对称造成的潜在贡献者流失。
- **影响范围**：仅 `site/docs/community.md` 一个文件，新增 56 行、删除 1 行；不影响代码与发布产物。
- **回迁到 1.4.x 的注意事项**：这是 main 分支的社区治理文档改进，对 1.4.x 运行时无任何影响；社区文档一般由 main 分支统一维护，1.4.x 作为代码维护分支不会单独维护一份社区文档。**无需回迁**。
