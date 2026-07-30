# 提交 2142：Spec: Clarify writer requirements to prevent orphan DVs

## 提交信息

- **序号**：2142 / 4088
- **哈希**：a44ec8d1c325f5f0e062e2ceefa7ee044cc956ce
- **短哈希**：a44ec8d1c
- **日期**：2025-05-18 22:14:26 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spec: Clarify writer requirements to prevent orphan DVs (#13042)
- **PR/Issue**：#13042

## 总体目的

这个提交更新了 Iceberg 规范文档（spec.md），澄清了写入器（writer）在处理删除向量（Deletion Vectors, DVs）时的要求，以防止产生孤儿删除向量（orphan DVs）。删除向量是 Iceberg 中用于高效表示单个数据文件删除的二进制格式。当数据文件被移除时（例如通过重写或过期清理），与之关联的删除向量也必须从 delete manifest 中移除，否则会产生孤儿删除向量——即引用了不存在数据文件的 DV。这个提交在规范中明确说明了这一要求，指导写入器的正确实现。

## 如何达成设计目的

1. 在 spec.md 的删除向量章节中添加一句话，明确说明移除数据文件时必须同时移除相关的删除向量，且不要求重写包含被移除 DV 的 Puffin 文件。

## 修改详情

### `format/spec.md` (修改, +1/-0 line)

**修改目的**：澄清写入器在移除数据文件时对删除向量的处理要求。

**工作逻辑**：在已有的删除向量说明段落（"Writers must ensure that there is at most one deletion vector per data file..."）之后，新增一句："When removing a data file, writers must also remove any deletion vector that applies to that data file from delete manifests. Writers are not required to rewrite Puffin files that contain the removed deletion vectors." 这句话明确了两个要求：(1) 移除数据文件时必须从 delete manifest 中移除对应的 DV 引用；(2) 但不要求重写包含被移除 DV 的 Puffin 文件（Puffin 文件中的物理 DV 数据可以保留，只是不再被 manifest 引用）。

## 总结

这个提交通过在 Iceberg 规范中添加一句话，明确了写入器在移除数据文件时对删除向量的处理要求，防止产生孤儿删除向量。这是一个规范文档层面的澄清，对引导写入器的正确实现有重要意义。
