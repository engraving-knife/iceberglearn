# 提交 1716：Spec: Typo - missing be (#12229)

## 提交信息

- **序号**：1716 / 4088
- **哈希**：8839c9bf1f1d8c9b718f9766302ff8a2018e515f
- **短哈希**：8839c9bf1
- **日期**：2025-02-11 17:13:40 -0600
- **作者**：Russell Spitzer
- **提交说明**：Spec: Typo - missing be (#12229)
- **PR/Issue**：#12229

## 总体目的

修正 Iceberg 规范文档附录 G（Geospatial Notes）中的一个英语语法错误。原文 "future versions may also used" 缺少了动词 "be"，应为 "future versions may also be used"。这是一个简单的拼写/语法修正。

附录 G 是在提交 1713 中新增的地理空间类型说明部分，描述了 OGC 标准版本的使用情况。语法错误虽然不影响技术准确性，但作为 Apache 顶级项目的规范文档，应保持专业性。

## 如何达成设计目的

直接修改 `format/spec.md` 中附录 G 最后一行的文本，在 "used" 前添加缺失的 "be"。

## 修改详情

### `format/spec.md`（修改, +1/-1 line）

**修改目的**：修正附录 G 中的英语语法错误。

**工作逻辑**：将 "The version of the OGC standard first used here is 1.2.1, but future versions may also used if the WKB representation remains wire-compatible." 修改为 "...but future versions may also be used if the WKB representation remains wire-compatible."。同时在文件末尾添加了换行符（原文末尾无换行符）。

## 小结

- **成效**：修正了规范文档中的语法错误，提升了文档质量。
- **影响范围**：仅文档变更，影响附录 G 的一句话。
- **回迁到 1.4.x 的注意事项**：取决于 1.4.x 分支是否已有附录 G（即是否已回迁提交 1713 的 geo 类型规范）。如果已有附录 G，则可回迁此修正；如果没有，则无需回迁。此提交依赖前置提交 1713。
