# 提交 3077：Site: Add Iceberg Summit 2026 section to homepage (#14988)

## 提交信息

- **序号**：3077 / 4088
- **哈希**：b3b6657352bc83329add02baf6154a6df13c3830
- **短哈希**：b3b665735
- **日期**：2026-01-07
- **作者**：Russell Spitzer
- **提交说明**：Site: Add Iceberg Summit 2026 section to homepage (#14988)
- **PR/Issue**：#14988

## 总体目的

Iceberg Summit 2026 即将于 2026 年 4 月 8-9 日在旧金山举办，同时会议的 CFP（Call for Proposals，演讲征集）截止日期为 2026 年 1 月 18 日。社区希望在 Apache Iceberg 官网首页（首页是最具曝光量的页面）显著位置展示这一信息，方便访客快速了解峰会详情并提交演讲提案。

本提交在官网首页的 hero 区域（标题和简介下方、社交按钮上方）新增了一个 "Iceberg Summit 2026" 信息卡片区域，包含两个可点击的链接卡片：一个引导用户前往 Sessionize 提交演讲（并带有实时倒计时显示距 CFP 截止的剩余时间），另一个展示会议日期和地点详情并链接到峰会官网。同时配套新增了 CSS 样式，确保卡片在桌面和移动端均有良好的视觉效果和响应式布局。

这是一个纯文档/网站类改动，不涉及任何代码逻辑，目的是提升社区活动的可发现性和参与度。

## 如何达成设计目的

整体思路是在 MkDocs Material 主题的首页覆盖模板 `home.html` 中插入一段 HTML 结构，配合自定义 CSS 实现视觉呈现。HTML 部分构建了包含两个卡片的 flex 布局容器；CSS 部分定义了半透明背景、圆角边框、阴影、悬停动画等样式，并通过媒体查询适配小屏幕。倒计时功能通过页面底部内联的一段 JavaScript 实现，每秒计算并更新 CFP 截止时间与当前时间的差值。

## 修改详情

### `site/docs/assets/stylesheets/home.css` (+50/-0 lines)

**修改目的**：为 Summit 信息卡片区域新增 CSS 样式及响应式适配。

**工作逻辑**：
新增 `.summit-box` 样式：居中布局、`max-width: 600px`、半透明背景 `rgba(255,255,255,0.05)`、圆角 `12px`、`2px` 半透明边框、`backdrop-filter: blur(10px)` 毛玻璃效果和阴影，营造现代卡片视觉。`.summit-box h4` 设定标题居中、加粗、20px 字号。`.summit-link-item` 定义卡片项的过渡动画（`transform 0.3s ease`）和阴影；`.summit-link-item:hover` 在悬停时上移 3px 并加深阴影，提供交互反馈。`.summit-link-item a` 设为块级元素填满整个卡片以扩大可点击区域。

在媒体查询 `@media (max-width: 767px)` 中新增移动端适配：`.summit-box` 改为 `max-width: 100%`、两侧留较小边距；`.summit-links` 强制纵向排列（`flex-direction: column !important`）；`.summit-link-item` 设为 `min-width: 100%`、`max-width: 100%`，确保在小屏幕上卡片堆叠显示。

### `site/overrides/home.html` (+49/-0 lines)

**修改目的**：在首页 hero 区域插入 Iceberg Summit 2026 信息卡片及 CFP 倒计时脚本。

**工作逻辑**：
在 `<hr class="intro-divider" />` 之后、`<ul class="list-inline intro-social-buttons">` 之前插入一个 `.summit-box` 容器。容器内有一个标题 `<h4>Iceberg Summit 2026</h4>` 和一个 flex 布局的 `.summit-links` 区域，包含两个 `.summit-link-item` 卡片：

- 第一张卡片为蓝色渐变背景（`#0969da` 到 `#0a8fd6`），链接到 `https://sessionize.com/iceberg-summit-2026`，显示 "Submit Your Talk"、截止日期 "Due: January 18, 2026"，以及一个 id 为 `cfp-countdown` 的倒计时占位元素。
- 第二张卡片为青绿色渐变背景（`#17a2b8` 到 `#20c997`），链接到 `https://www.icebergsummit.org/`，显示 "Event Details"、"April 8-9, 2026"、"San Francisco, CA"。

在页面底部脚本区新增一段 JavaScript `updateCountdown()` 函数：设定 CFP 截止时间为 `2026-01-18T08:00:00Z`（即 PST 午夜对应的 08:00 UTC），计算与当前时间的差值 `distance`。若 `distance < 0` 则在倒计时元素显示 "CFP Closed. See you at Summit!"；否则计算剩余天数、小时、分钟、秒并格式化为 `"Xd Xh Xm Xs"` 显示。函数在加载时立即调用一次，并通过 `setInterval(updateCountdown, 1000)` 每秒更新。

## 总结

本提交为 Iceberg 官网首页添加了 Iceberg Summit 2026 峰会信息展示区，包含 CFP 提交入口（带实时倒计时）和会议详情入口，配合精心设计的响应式 CSS 样式，有效提升了社区对峰会活动的可见度和参与便利性。倒计时功能为 CFP 截止前营造了紧迫感，有助于推动更多提案提交。
