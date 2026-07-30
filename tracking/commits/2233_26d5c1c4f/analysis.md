# 提交 2233：Docs: Fix broken links in Flink Configuration documentation

## 提交信息

- **序号**：2233 / 4088
- **哈希**：26d5c1c4f856d15630898abc476ac8c6c191b4a2
- **短哈希**：26d5c1c4f
- **日期**：2025-06-13 01:52:00 +0800
- **作者**：Kyle Lin
- **提交说明**：Docs: Fix broken links in Flink Configuration documentation
- **PR/Issue**：#13288

## 总体目的

本提交修复了 Flink 配置文档中的失效链接。文档中使用了 Java Javadoc 风格的 `{@link StatisticsType#Map}` 和 `{@link StatisticsType#Sketch}` 引用语法来引用 `StatisticsType` 枚举，但这类语法在 Markdown 文档中不会被解析，用户在浏览器中查看文档时这些引用无法正确显示为可点击的链接。修复方式是将 Javadoc 语法替换为正确的 Markdown 链接，指向实际的 Javadoc 页面，使文档读者能直接跳转到相关类的 API 文档。

## 如何达成设计目的

- 将 `{@link StatisticsType#Map}` 替换为 `` [`StatisticsType.Map`](../../javadoc/{{ icebergVersion }}/org/apache/iceberg/flink/sink/shuffle/StatisticsType.html#Map) ``，使用 Markdown 链接语法指向 Javadoc 页面的 Map 锚点。
- 将 `{@link StatisticsType#Sketch}` 替换为对应的 Sketch 锚点链接。
- 使用 `{{ icebergVersion }}` 模板变量确保链接指向当前 Iceberg 版本的 Javadoc。

## 修改详情

### `docs/docs/flink-configuration.md` (修改, +1/-2 lines)

**修改目的**：将 Javadoc 风格的 `{@link}` 引用替换为正确的 Markdown 链接。

**工作逻辑**：原文本中的 `{@link StatisticsType#Map}` 和 `{@link StatisticsType#Sketch}` 在 Markdown 渲染时不会被解析为链接，用户看到的是原始文本。修改后使用 Markdown 链接语法 `[`StatisticsType.Map`](URL)` 指向 `StatisticsType.html` Javadoc 页面的对应枚举值锚点（`#Map` 和 `#Sketch`），并使用 `{{ icebergVersion }}` 模板变量保证版本一致性。两行文本被合并为一行以保持段落连续性。

## 总结

这是一个纯文档修复提交，将 Flink 配置文档中无法渲染的 Javadoc `{@link}` 引用替换为正确的 Markdown 链接，使文档读者能通过链接直接访问 `StatisticsType` 枚举的 API 文档。
