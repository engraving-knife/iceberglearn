# 提交 2015：Site: Remove Iceberg Summit Link from the Homepage (#12842)

## 提交信息

- **序号**：2015 / 4088
- **哈希**：38b7c090b526dd6a20ffa5ff804d3487565582af
- **短哈希**：38b7c090b
- **日期**：2025-04-18 15:35:17 -0500
- **作者**：Russell Spitzer
- **提交说明**：Site: Remove Iceberg Summit Link from the Homepage (#12842)
- **PR/Issue**：#12842

## 总体目的

这个提交从 Iceberg 官网首页移除了 Iceberg Summit 2025 活动的注册链接。Iceberg Summit 2025 是一个于 2025 年 4 月 8-9 日举办的活动，由于活动日期已过（本提交日期为 4 月 18 日），首页上继续展示该活动的注册链接已不再合适，因此需要将其移除以保持首页内容的时效性。

## 如何达成设计目的

通过删除首页 HTML 模板中包含 Iceberg Summit 2025 注册按钮的代码块来实现。

## 修改详情

### `site/overrides/home.html` (修改, +0/-5 lines)

**修改目的**：移除首页的 Iceberg Summit 2025 注册按钮。

**工作逻辑**：
删除了一个 `<a>` 标签块，该块包含一个指向 `https://www.icebergsummit2025.com/` 的按钮，文字为"Iceberg Summit 2025 (8-9 Apr): Register Now!"。该按钮位于首页标题"The open table format for analytic datasets."下方，移除后首页直接显示分割线和社交按钮列表。

## 总结

简单的网站维护提交，移除已过期活动的注册链接，保持首页内容时效性。
