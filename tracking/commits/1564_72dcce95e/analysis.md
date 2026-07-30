# 提交 1564：Site: Put a CFP Banner on the Homepage (#11942)

## 提交信息

- **序号**：1564
- **哈希**：72dcce95e294835f978dc1d6c9a3be5d89123410
- **短哈希**：72dcce95e
- **日期**：2025-01-09（Thu Jan 9 14:05:52 2025 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Site: Put a CFP Banner on the Homepage (#11942)
- **PR/Issue**：#11942
- **共同作者**：Nhyi-streamlit <aba.micah@snowflake.com>

## 总体目的

Apache Iceberg 官网首页（基于 MkDocs Material 的 `home.html` override）原本只有标题 "Apache Iceberg™" 和副标题 "The open table format for analytic datasets."，以及一行分隔线和社交链接。社区希望把 "Iceberg Summit 2025" 的 Call For Proposals（CFP，议题征集）通知放到首页最显眼的位置，以提升演讲提交率。

本提交在首页副标题下方插入一个醒目的按钮型横幅（btn-lg 默认样式），链接到 sessionize 提案系统 `https://sessionize.com/iceberg-summit-2025`，并在新标签页打开（`target="_blank"`）。文字"Iceberg Summit 2025: Call For Proposals - Open Until Feb 9"明确告知截止日期，鼓励社区成员及时提交议题。

这是一次纯前端/内容变更，无构建或运行时影响。

## 如何达成设计目的

直接编辑 `site/overrides/home.html`，在 `<h3>` 副标题之后、`<hr class="intro-divider" />` 之前插入一个 `<a class="btn btn-default btn-lg" ...>` 元素，包含一个 `<span>` 包装的横幅文案。按钮使用 Bootstrap 风格的 `btn btn-default btn-lg` 类，与首页既有视觉风格一致；外链带 `target="_blank"`，避免用户离开官网。

### 修改详情

#### `site/overrides/home.html`
**修改目的**：在首页顶部增加 Iceberg Summit 2025 CFP 横幅。

**新增内容**：
```html
<a class="btn btn-default btn-lg" href="https://sessionize.com/iceberg-summit-2025" target="_blank">
  <span>
    Iceberg Summit 2025: Call For Proposals - Open Until Feb 9
  </span>
</a>
```

放置在 `<h3>` 与 `<hr>` 之间，使横幅位于首屏视觉焦点区域。

## 小结

- **成效**：首页增加 Iceberg Summit 2025 CFP 横幅，提升议题征集曝光度，截止日期明确（Feb 9）。
- **影响范围**：仅 `site/overrides/home.html` 一个文件，纯官网内容变更，无代码、构建或运行时影响。
- **回迁到 1.4.x 的注意事项**：与产品版本无关，且属于时效性内容（CFP 截止后即失去意义），1.4.x 维护分支无需回迁。**无需回迁**。
