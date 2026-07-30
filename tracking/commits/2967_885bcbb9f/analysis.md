# 提交 2967：site: Update Slack link (#14772)

## 提交信息

- **序号**：2967 / 4088
- **哈希**：885bcbb9fc98d8f4da3350261deb3bec11e1a181
- **短哈希**：885bcbb9f
- **日期**：2025-12-05
- **作者**：Fokko Driesprong
- **提交说明**：site: Update Slack link (#14772)
- **PR/Issue**：#14772

## 总体目的

Apache Iceberg 社区通过 Slack 工作区（`apache-iceberg.slack.com`）进行交流，新成员需要通过一个 Slack 的 shared_invite 邀请链接加入。Slack 的邀请链接在平台升级或链接过期时会失效（社区文档本身也注明 "this link may occasionally break when Slack does an upgrade"）。本次提交前，仓库中多处引用的邀请 token 是 `zt-287g3akar-K9Oe_En5j1UL7Y_Ikpai3A`（issue 模板里则是另一个更老的 `zt-2561tq9qr-...` token），链接已经不可用或即将失效，影响新用户加入社区以及提问引导。本提交将仓库内所有 Slack 邀请链接统一更新为新的 token `zt-3kclosz6r-3heAW3d~_PHefmN2A_~cAg`，确保社区入口畅通，并把散落各处的不一致链接收敛为同一个有效链接。

## 如何达成设计目的

改动范围仅限文档与 GitHub 配置中的链接文本。作者定位到仓库中引用 Slack 邀请链接的三处位置——GitHub issue 提问模板、社区文档页面、mkdocs 站点配置的页脚社交链接——将其中旧的 invite token 替换为新的同一个 token，使三个入口指向同一个有效邀请地址。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_question.yml` (+1/-1 lines)

**修改目的**：更新 issue 提问模板中引导用户到 Slack 提问的邀请链接。

**工作逻辑**：
提问模板的 markdown 提示语 "Feel free to ask your question on [Slack](...) as well." 中的链接由 `https://join.slack.com/t/apache-iceberg/shared_invite/zt-2561tq9qr-UtISlHgsdY3Virs3Z2_btQ` 替换为 `https://join.slack.com/t/apache-iceberg/shared_invite/zt-3kclosz6r-3heAW3d~_PHefmN2A_~cAg`。值得注意的是该处原先使用的是一个与文档站点都不同的更老 token，本次也一并收敛到新链接。

### `site/docs/community.md` (+1/-1 lines)

**修改目的**：更新社区文档中 Slack 加入方式的有效邀请链接。

**工作逻辑**：
"Slack" 小节中 "follow [this invite link](...)" 的链接由 `zt-287g3akar-K9Oe_En5j1UL7Y_Ikpai3A` 更新为 `zt-3kclosz6r-3heAW3d~_PHefmN2A_~cAg`。文档原本就提醒该链接可能因 Slack 升级而偶发失效、遇问题可邮件 `dev@iceberg.apache.org`，本次更新正是对该维护流程的执行。

### `site/mkdocs.yml` (+1/-1 lines)

**修改目的**：更新 mkdocs 站点页脚/社交图标的 Slack 链接。

**工作逻辑**：
`extra` 配置中 slack 图标对应的 `link` 由旧 token 更新为新 token，保证站点页脚点击 Slack 图标跳转到与文档一致的最新邀请地址，避免不同入口指向不同（可能已失效）的链接。

## 总结

本提交是一次纯文档/社区入口维护，把仓库三处 Slack 邀请链接统一更新并收敛为同一个有效 token，确保新用户无论通过 issue 模板、社区文档还是站点页脚都能加入 Slack 工作区。改动虽小但对社区可达性与一致性有实际价值，也体现了对 Slack 链接易失效特性的常态化维护。
