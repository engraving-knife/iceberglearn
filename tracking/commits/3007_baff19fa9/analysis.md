# 提交 3007：Docs: Update community meetup guidelines (#14770)

## 提交信息

- **序号**：3007 / 4088
- **哈希**：baff19fa94b562402d9119683c42585c9ac66619
- **短哈希**：baff19fa9
- **日期**：2025-12-12 14:02:58 -0800
- **作者**：Danica Fine
- **提交说明**：Docs: Update community meetup guidelines (#14770)
- **PR/Issue**：#14770

## 总体目的

Iceberg 官方文档 `site/docs/community.md` 中有一节"Hosting an Apache Iceberg Meetup"，规定了以 Apache Iceberg 名义举办社区 meetup 的流程与要求：使用命名格式 `Apache Iceberg Meetup <geographical location>`、遵守 ASF 品牌与商标指南、无需 PMC 显式批准，并列出主办方必须满足的 5 条要求（强调 Iceberg 生态、vendor-neutral、至少两家公司演讲者、知会 dev list、遵守社区准则）。

然而原有指南虽然要求"小规模、符合 ASF 品牌指南"，却没有给出一套可操作的判定标准来界定"什么算 meetup"。ASF 的品牌政策对不同规模的活动（meetup vs. 更大的 branded event）有不同要求，而社区中存在组织者把规模偏大、性质模糊的活动直接套用"Apache Iceberg Meetup"名号的情况，这既可能违反 ASF 品牌政策，也让 PMC 难以一致地把关。本提交的目的就是在原有 5 条要求之后补充一段 meetup 判定标准，明确 meetup 的典型特征，并在拿不准时要求组织者事先通过私有邮件列表向 PMC 确认，从而把"是否属于 meetup"的判断从模糊的主观印象变为有据可依的清单。

## 如何达成设计目的

整体思路是纯文档增补：在"Hosting an Apache Iceberg Meetup"小节、原有 5 条编号要求之后、"Community Guidelines"小节之前，插入一个自然段加一个无序列表，描述 meetup 的典型规模与组织形态，并在末尾给出"拿不准先问 PMC"的兜底指引。改动只涉及 `community.md` 一个文件，新增 9 行，不改动既有任何条款。

## 修改详情

### `site/docs/community.md` (+9/-0 lines)

**修改目的**：在 meetup 主办要求之后补充 meetup 判定标准与 PMC 咨询渠道。

**工作逻辑**：
新增内容分两部分。第一部分是一个强调句："Meetups *must* be small events under the ASF branding guidelines and are typically small, informal gatherings. If you're unsure whether an event is a meetup, meetups usually:"——明确 meetup 必须是符合 ASF 品牌指南的小型活动，并以下方列表作为"通常特征"参考。第二部分是一个 4 项无序列表，刻画 meetup 的典型形态：

- 选题方式：依赖策划挑选的演讲，组织者与社区协作以纳入多样化的演讲者、话题与公司代表；因投稿量小，可有但不强制要求 CfP（Call for Proposals）。
- 议程形态：单轨（single-tracked），仅容纳少量 session。
- 内容时长：至多 2-3 小时内容加社交时间。
- 赞助形态：由 1-3 家公司提供餐饮、饮品或场地赞助，而非出售展位或营销机会；不需要大量资金支持。

最后一句兜底："If you don't know whether an event qualifies as a meetup, please ask the PMC through the private mailing list! (Be sure to do this *before* using the Apache Iceberg brand or trademark.)"——要求组织者在不确定时、且在使用 Iceberg 品牌商标之前，先通过私有邮件列表征询 PMC 意见。这与原指南开头"无需 PMC 显式批准"并不冲突：常规小型 meetup 仍无需批准，但当活动规模/性质模糊时，事先咨询可避免商标合规风险。

## 总结

该提交是一处纯文档增补，为 Iceberg 社区 meetup 主办指南补充了"如何判定一个活动算 meetup"的可操作标准与 PMC 事先咨询机制。其核心价值在于把原先偏主观的"小规模"要求落到具体维度（选题方式、单/多轨、时长、赞助形态）上，既帮助组织者自检活动是否合规，也便于 PMC 一致地把关，降低 ASF 品牌与商标被不当使用的风险，对维护 Apache Iceberg 社区活动的规范性与品牌一致性有实际指导意义。
