# 提交 3617：Site: Remove Iceberg Summit 2026 section as the event has passed (#16166)

## 提交信息

- **序号**：3617 / 4088
- **哈希**：b07435ba4a5b93312a71a8541c60cbdd7aa324fa
- **短哈希**：b07435ba4
- **日期**：2026-04-29 20:45:28 -0500
- **作者**：Talat UYARER
- **提交说明**：Site: Remove Iceberg Summit 2026 section as the event has passed (#16166)
- **PR/Issue**：#16166

## 总体目的

这个提交从 Iceberg 项目网站首页移除了 Iceberg Summit 2026 活动的宣传区域，因为该活动已于 2026 年 4 月 8-9 日在旧金山举办完毕。

Iceberg Summit 是 Apache Iceberg 社区的年度峰会。在活动举办前，网站首页添加了一个宣传区域，包含提交演讲提案（CFP）的链接和倒计时、活动详情（日期、地点）等信息。活动结束后，这些信息已过时，需要从网站移除以保持页面的时效性和整洁性。

## 如何达成设计目的

从两个文件中移除 Summit 相关的内容：
1. `home.html`：移除 Summit 宣传区域的 HTML 代码和 CFP 倒计时 JavaScript 代码。
2. `home.css`：移除 Summit 相关的 CSS 样式定义。

## 修改详情

### `site/overrides/home.html` (+0/-49 lines)

**修改目的**：移除 Summit 宣传区域的 HTML 和 JavaScript。

**工作逻辑**：
1. 移除了 Summit Box 的 HTML 结构，包含：
   - "Submit Your Talk" 链接卡片（指向 Sessionize，CFP 截止日期 January 18, 2026）
   - "Event Details" 链接卡片（活动日期 April 8-9, 2026，地点 San Francisco, CA）
2. 移除了 CFP 倒计时的 JavaScript 代码，该代码每秒更新倒计时显示。

### `site/docs/assets/stylesheets/home.css` (+0/-50 lines)

**修改目的**：移除 Summit 相关的 CSS 样式。

**工作逻辑**：
移除了以下 CSS 样式定义：
- `.summit-box`：Summit 容器盒子的样式（边距、内边距、背景、圆角、边框、阴影等）。
- `.summit-box h4`：标题样式。
- `.summit-link-item`：链接卡片的过渡效果和阴影。
- `.summit-link-item:hover`：悬停效果。
- `.summit-link-item a`：链接样式。
- 响应式媒体查询中的 Summit 相关样式（`.summit-box`、`.summit-links`、`.summit-link-item` 在小屏幕下的适配）。

## 总结

这是一个网站维护提交，移除了已过期的 Iceberg Summit 2026 活动宣传内容。这保持了网站首页的时效性和整洁性，避免向用户展示过时的活动信息。这是活动结束后常规的网站维护工作。
