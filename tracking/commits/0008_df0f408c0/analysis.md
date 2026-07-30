# 提交 0008：Docs: Add links to Go, Python and Rust (#8681)

## 提交信息

- **序号**：0008 / 4088
- **哈希**：df0f408c05f33d90600aac846306c539787f2250
- **短哈希**：df0f408c0
- **日期**：2023-10-02 15:37:41 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Docs: Add links to Go, Python and Rust (#8681)
- **PR/Issue**：#8681

## 总体目的

这个提交是纯文档改动，目的是在仓库根目录的 `README.md` 中补充指向 Iceberg 其他语言实现的官方仓库链接。

Apache Iceberg 最初且核心的实现是 Java 版本（即本仓库）。随着 Iceberg 生态的发展，社区已经孵化出多个其他语言的实现：Go、Python（PyIceberg）、Rust。这些实现各自独立成仓，对覆盖不同引擎和场景（如 Python 数据科学生态、Rust 高性能场景、Go 生态）具有重要意义。然而在此之前，本仓库 README 只描述了 Java 实现以及 Spark/Flink/Hive 等引擎集成模块，并没有引导访客去了解其他语言实现，导致新用户难以发现这些项目。

通过在 README 末尾新增 "Implementations" 小节并列出三个姐妹仓库的链接，本提交提升了多语言实现的可见性，帮助用户快速找到适合自己技术栈的 Iceberg 实现，也体现了 Iceberg 作为跨语言表格式规范的核心定位——规范是统一的，实现可以是多语言的。提交说明中特别提到 "In alphabetic order :)"，说明作者在排列顺序上也做了细致考虑。

## 如何达成设计目的

设计思路非常简单：在 `README.md` 文件末尾、引擎集成说明段落之后，新增一个 "### Implementations" 三级标题小节，先用一句话说明本仓库是 Java 实现、其他实现可在以下地址找到，然后以无序列表形式按字母顺序列出 Go、Python（PyIceberg）、Rust 三个实现及其 GitHub 链接。改动仅 8 行新增、0 行删除，范围限定在单一文件。

## 修改详情

### `README.md`

**修改目的**：在 README 中新增 "Implementations" 小节，列出 Go、Python、Rust 三种语言的 Iceberg 实现仓库链接。

**工作逻辑**：在原有的引擎兼容性说明段落（"See the Multi-Engine Support page..."）之后，新增空行分隔，然后添加 "### Implementations" 标题。正文先说明 "This repository contains the Java implementation of Iceberg. Other implementations can be found at:"，随后以加粗语言名 + 仓库名链接的列表形式给出三项：
- **Go**: [iceberg-go](https://github.com/apache/iceberg-go)
- **PyIceberg** (Python): [iceberg-python](https://github.com/apache/iceberg-python)
- **Rust**: [iceberg-rust](https://github.com/apache/iceberg-rust)

顺序按 Go → Python → Rust 字母序排列（提交说明中作者特意注明 "In alphabetic order :)"）。注意 Python 项的链接指向的是 `apache/iceberg-python` 仓库（即 PyIceberg），而 Rust 项指向 `apache/iceberg-rust`，这些是社区官方的多语言实现仓库。

## 小结

该提交通过在 README 新增 "Implementations" 小节并列出 Go、Python、Rust 三种语言实现的官方仓库链接，提升了 Iceberg 多语言生态的可见性，帮助用户快速定位适合自身技术栈的实现。
