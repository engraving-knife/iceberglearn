# 提交 0558：Site: Remove embedded calendar, replace with links

## 提交信息

- **序号**：0558 / 4088
- **哈希**：45a086b3e6c34949a37aeca1d2c9439dae28fee9
- **短哈希**：45a086b3e
- **日期**：2024-03-03 12:42:55 -0800
- **作者**：Brian "bits" Olsen <bits@bitsondata.dev>
- **提交说明**：Site: Remove embedded calendar, replace with links (#9854)
- **PR/Issue**：#9854

## 总体目的

将 Iceberg 站点社区页面（`site/docs/community.md`）中嵌入的 Google Calendar `<iframe>` 替换为两个分别指向"Iceberg Community Events"和"Iceberg Dev Events"日历的 Markdown 链接。

主要动机：

1. 嵌入式 iframe 在 ASF 站点构建规范下不被推荐（站点应当尽可能避免第三方 iframe 嵌入以符合 ASF 站点指南）。
2. iframe 在某些浏览器隐私模式、广告拦截器、Content-Security-Policy 限制下加载不可靠，影响用户访问。
3. 直接提供日历订阅链接比内嵌日历更轻量、更易复用，用户可在自己的 Google Calendar 中查看，而不必依赖页面内嵌视图。

## 如何达成设计目的

通过删除原本包含日历订阅说明段与 `<iframe>` 嵌入代码的 6 行内容，改为两条 Markdown 列表项，每项把原日历的 `cid` 标识符拼成 `https://calendar.google.com/calendar/u/0?cid=<ID>` 链接形式，并使用 Jekyll/Hugo Markdown 扩展语法 `{:target="_blank"}` 让链接在新标签页打开。这样既保留了"两个独立日历"的信息结构，又去掉了 iframe 依赖。

`cid` 参数与原 iframe 中 `src=` URL 里的 `src=` 参数完全一致，确保用户点击后看到的是同一份日历数据。

## 修改详情

### `site/docs/community.md`

**修改目的**：移除嵌入式 Google Calendar iframe，改为两条 Markdown 链接形式，分别指向社区活动日历与开发者活动日历。

**工作逻辑**：

原内容（6 行）：

```markdown
* Iceberg Community Events - Events such as conferences and meetups, aimed to educate and inspire Iceberg users.
* Iceberg Dev Events - Events such as the triweekly Iceberg sync, aimed to discuss the project roadmap and how to implement features.

You can subscribe to either or both of these calendars by clicking the "+ Google Calendar" icon on the bottom right.

<iframe src="https://calendar.google.com/calendar/embed?height=600&wkst=1&bgcolor=%23ffffff&ctz=UTC&title=Iceberg%20Community%20Events%20(UTC)&showNav=1&showPrint=0&showCalendars=1&mode=AGENDA&src=NTkz...&src=Mzkw...&color=%232c7cbf&color=%23A79B8E" style="border:solid 1px #777" width="800" height="600" frameborder="0" scrolling="no"></iframe>
```

修改后内容（2 行）：

```markdown
* [Iceberg Community Events](https://calendar.google.com/calendar/u/0?cid=NTkz...){:target="_blank"} : Events such as conferences and meetups, aimed to educate and inspire Iceberg users.
* [Iceberg Dev Events](https://calendar.google.com/calendar/u/0?cid=Mzkw...){:target="_blank"} : Events such as the triweekly Iceberg sync, aimed to discuss the project roadmap and how to implement features.
```

关键改动：

1. 删除了独立的"You can subscribe ... by clicking the '+ Google Calendar' icon"提示句，因为新方案不再有内嵌日历也就不存在"+ Google Calendar"图标。
2. 删除了 `<iframe>` 整段嵌入代码（约 1 行超长 HTML）。
3. 把原本纯文本的两个日历名称改为 Markdown 链接，链接 URL 使用 Google Calendar 的 `u/0?cid=` 形式，让用户点击后直接进入自己 Google Calendar 中查看/订阅对应日历。
4. 列表项分隔符从 ` - ` 改为 ` : `，是次要风格调整。
5. `{:target="_blank"}` 是 Kramdown/Jekyll 支持的属性语法，让链接在新标签页打开，避免用户离开 Iceberg 站点。

`cid` 值与原 iframe `src` 参数中的 `src=` 值一一对应：

- Community Events 日历 ID：`NTkzYmIwMGJmZTQ1N2QzMTkxNDEzNTBkZDI0Yzk2NGYzOWJkYmQ5ZmQyNDMyODFhODYzMmEwMDk2M2EyMWQ4NkBncm91cC5jYWxlbmRhci5nb29nbGUuY29t`
- Dev Events 日历 ID：`MzkwNWQ0OTJmMWI0NTBiYTA3MTJmMmFlNmFmYTc2ZWI3NTdmMTNkODUyMjBjYzAzYWE0NTI3ODg1YWRjNTYyOUBncm91cC5jYWxlbmRhci5nb29nbGUuY29t`

## 小结

- 本提交是站点维护性改动，单文件 2 增 6 删，仅涉及 `site/docs/community.md`。
- 影响范围：仅社区页面渲染效果，不涉及任何代码逻辑、API、构建产物功能。用户访问社区页面时不再看到嵌入式日历，而是看到两个可点击的链接，点击后在新标签页打开对应 Google Calendar。
- 回迁到 1.4.x 的注意事项：1.4.x 若维护有同一份 `site/docs/community.md`，可直接套用此 patch。需确认 1.4.x 站点构建使用的 Markdown 引擎支持 `{:target="_blank"}` 属性语法（Kramdown 默认支持，Hugo 也支持）。若引擎不支持，可改为标准 Markdown 链接或显式 HTML `<a target="_blank">`。该改动无副作用，回迁风险低。
