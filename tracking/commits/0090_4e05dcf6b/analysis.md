# 提交 0090：Update release template (#8879)

## 提交信息

- **序号**：0090 / 4088
- **哈希**：4e05dcf6bfe709df9d6918ea7b90ce48020f1e3b
- **短哈希**：4e05dcf6b
- **日期**：2023-10-25 14:00:36 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Update release template (#8879)
- **PR/Issue**：#8879

## 总体目的

这个提交要解决的是发布投票邮件模板中投票时长表述与实际发布节奏不匹配的问题。Iceberg 的源码发布脚本 `dev/source-release.sh` 会在发布候选（RC）准备完成后，自动生成一封投票公告邮件模板，其中包含一句提示投票时长的固定文案。此前该文案为 `Please vote in the next 72 hours. (Weekends excluded)`，即"72 小时内投票，周末不计入"。

提交作者 Fokko Driesprong 在 PR 说明中指出两点考虑：第一，对于现在正在进行的 patch release（如 1.4.1）来说，不应该被"周末不计入"的约束所限制——实际发布中很少有发布在周末进行；第二，根据 Apache 基金会投票规则（https://www.apache.org/foundation/voting.html#expressing-votes-1-0-1-and-fractions），一个投票**应当**开放 72 小时。因此把文案中的"(Weekends excluded)"去掉，使投票时长表述与 Apache 标准对齐，并在 patch release 场景下避免因周末排除而把投票窗口人为拉长。

对 Iceberg 演进的意义在于：把发布流程模板与 Apache 基金会标准对齐，并使其更贴合 patch release 的实际节奏，减少发版流程中不必要的等待。

## 如何达成设计目的

设计思路很直接：修改 `dev/source-release.sh` 中那段 heredoc 投票邮件模板里的投票时长文案，把 `Please vote in the next 72 hours. (Weekends excluded)` 改为 `Please vote in the next 72 hours.`，即移除"(Weekends excluded)"这一限定。改动只涉及一行文案，不改变脚本任何逻辑，投票邮件的其余部分（RC 信息、tarball/签名/校验和链接、KEYS 文件、Nexus 仓库 URL、投票格式 `[ ] +1 / +0 / -1`、PMC binding 投票规则说明等）保持不变。

## 修改详情

### `dev/source-release.sh`

**修改目的**：移除发布投票邮件模板中"周末不计入"的限定，使投票时长表述与 Apache 标准（72 小时）对齐。

**工作逻辑**：在脚本生成投票公告邮件的 heredoc 段（紧跟 `Please download, verify, and test.` 之后）中，将 `Please vote in the next 72 hours. (Weekends excluded)` 改为 `Please vote in the next 72 hours.`。该邮件模板包含 RC 提案说明、commit ID 与 tag 链接、tarball/签名/校验和的 dist 仓库链接、KEYS 文件位置、Nexus 便利二进制仓库 URL、投票选项（`[ ] +1 Release this as Apache Iceberg ${version}` / `[ ] +0` / `[ ] -1 Do not release this because...`）、以及 PMC binding 投票通过条件（3 个 binding +1 且 +1 多于 -1）。本次改动只影响投票时长提示文案，不改变任何发版脚本逻辑或邮件其它部分。

## 小结

通过移除发布投票邮件模板中"(Weekends excluded)"的限定，本提交使 Iceberg 发版投票时长表述与 Apache 基金会 72 小时标准对齐，并更贴合 patch release 的实际节奏。
