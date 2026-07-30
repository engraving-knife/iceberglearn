# 提交 1924：Docs: Fix ASF sponsorship links (#12646)

## 提交信息

- **序号**：1924 / 4088
- **哈希**：695374d8bfbd22f4347cfe43b54a93c41b88f166
- **短哈希**：695374d8b
- **日期**：2025-03-26 20:02:55 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix ASF sponsorship links (#12646)
- **PR/Issue**：#12646

## 总体目的

`site/nav.yml` 是 Iceberg 官网导航配置，其中 "ASF"（Apache 软件基金会）分组下列出了多个外部链接。本提交修正其中两个链接指向错误的 URL：

1. "Sponsorship"（赞助）链接此前指向 `https://www.apache.org/foundation/thanks.html`（实际上是感谢页/赞助商列表页），应指向真正的赞助说明页 `https://www.apache.org/foundation/sponsorship.html`。
2. "Sponsors"（赞助商）链接此前也指向 `thanks.html`，应指向 `https://www.apache.org/foundation/sponsors.html`。

两个链接都错误地指向了 thanks（致谢）页面，与它们各自的标签语义不符。本次分别更正为 sponsorship.html 与 sponsors.html。

## 如何达成设计目的

直接编辑 `site/nav.yml` 中 ASF 分组下的两条链接 URL：
- `Sponsorship` → `https://www.apache.org/foundation/sponsorship.html`
- `Sponsors` → `https://www.apache.org/foundation/sponsors.html`

## 修改详情

### `site/nav.yml` (修改, +2/-2 lines)

**修改目的**：修正 ASF 赞助相关导航链接。

**工作逻辑**：

```yaml
- ASF:
  - Sponsorship: https://www.apache.org/foundation/sponsorship.html
  ...
  - Sponsors: https://www.apache.org/foundation/sponsors.html
```

把原先两条都指向 `thanks.html` 的链接分别改为 `sponsorship.html` 与 `sponsors.html`。

## 总结

本提交修正官网导航 `nav.yml` 中 ASF 分组下 "Sponsorship" 与 "Sponsors" 两条链接，从错误的 `thanks.html` 改为各自的正确页面 `sponsorship.html` 与 `sponsors.html`，使链接标签与落地页语义一致。
