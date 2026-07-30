# 提交 1728：Site: Update site to include Iceberg Summit link (#12256)

## 提交信息

- **序号**：1728 / 4088
- **哈希**：7876ee15139b21081d13833d9efa6f338cf688cd
- **短哈希**：7876ee151
- **日期**：2025-02-13 13:38:37 -0600
- **作者**：Danica Fine
- **提交说明**：Site: Update site to include Iceberg Summit link (#12256)
- **PR/Issue**：#12256

## 总体目的

更新 Iceberg 官方网站首页上的 Iceberg Summit 2025 活动链接和文案。Iceberg Summit 2025 是一个定于 2025 年 4 月 8-9 日举办的 Iceberg 峰会活动。

此前首页显示的是 Sessionize 平台的 CFP（Call For Proposals，提案征集）链接，提示提案征集截止日期为 2 月 9 日。由于 CFP 已截止，链接需要更新为峰会官方网站，并将文案从"提案征集开放"改为"立即注册"。

## 如何达成设计目的

修改 `site/overrides/home.html` 中首页按钮的链接地址和显示文案，将 CFP 链接替换为峰会注册链接。

## 修改详情

### `site/overrides/home.html`（修改, +2/-2 lines）

**修改目的**：更新首页 Iceberg Summit 按钮的链接和文案。

**工作逻辑**：
1. 将按钮链接从 `https://sessionize.com/iceberg-summit-2025`（CFP 平台）改为 `https://www.icebergsummit2025.com/`（峰会官方网站）。
2. 将按钮文案从 "Iceberg Summit 2025: Call For Proposals - Open Until Feb 9" 改为 "Iceberg Summit 2025 (8-9 Apr): Register Now!"，即从提案征集提示改为注册提示，并包含了活动日期信息。

## 小结

- **成效**：网站首页的 Iceberg Summit 链接更新为注册链接，用户可以直接点击前往峰会官网注册。
- **影响范围**：仅影响网站首页 HTML，不影响项目代码。
- **回迁到 1.4.x 的注意事项**：不建议回迁。这是 Iceberg Summit 2025 活动的临时性链接更新，与 1.4.x 分支无关，且活动已过时。
