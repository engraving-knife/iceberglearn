# 提交 2861：docs: remove hidden spark-runtime-jar link (#14555)

## 提交信息

- **序号**：2861 / 4088
- **哈希**：59bc4635806f27aa2cb633270ce1a1bfac4cdc46
- **短哈希**：59bc46358
- **日期**：2025-11-10 12:53:24 -0800
- **作者**：Kevin Liu
- **提交说明**：docs: remove hidden spark-runtime-jar link (#14555)
- **PR/Issue**：#14555

## 总体目的

这个提交移除了 Spark 快速入门文档中一个隐藏的 Markdown 链接引用定义。该链接 `[spark-runtime-jar]` 定义了指向 Maven Central 上 Spark runtime JAR 的下载链接，但在文档正文中已经没有被引用（链接引用定义本身不显示在渲染后的页面中）。

此前在提交 2857 中，文档将硬编码的 `iceberg-spark-runtime-3.5_2.12` 替换为模板变量 `{{ sparkVersionMajor }}`，这个链接定义也相应更新了。但在文档正文中，该链接引用 `[spark-runtime-jar]` 已经不再被使用（正文已改为直接引用 Releases 页面），因此这个隐藏的链接定义成为了无用代码，需要清理。

## 如何达成设计目的

通过删除 `site/docs/spark-quickstart.md` 中三行内容（链接检查注释和链接定义）来完成清理。

## 修改详情

### `site/docs/spark-quickstart.md` (+0/-3 lines)

**修改目的**：移除未被引用的 spark-runtime-jar 链接定义。

**工作逻辑**：删除以下三行内容：
1. `<!-- markdown-link-check-disable-next-line -->` — Markdown 链接检查禁用注释
2. `[spark-runtime-jar]: https://search.maven.org/remotecontent?filepath=org/apache/iceberg/iceberg-spark-runtime-{{ sparkVersionMajor }}/{{ icebergVersion }}/iceberg-spark-runtime-{{ sparkVersionMajor }}-{{ icebergVersion }}.jar` — 链接引用定义

这些内容在渲染后的 Markdown 页面中不可见（注释和引用定义），且正文已不再引用 `[spark-runtime-jar]` 标识符，因此可以安全删除。

## 总结

这是一个文档清理提交，移除了 Spark 快速入门文档中不再被引用的隐藏链接定义及其链接检查注释。修改虽小但保持了文档的整洁性，避免了无用代码的残留。
