# 提交 2157：Spec: Add details on GZIP compressed metadata files (#12598)

## 提交信息

- **序号**：2157 / 4088
- **哈希**：0379c434e0955a51eb7a70e75217621d76de15b9
- **短哈希**：0379c434e
- **日期**：2025-05-23 01:40:23 -0700
- **作者**：emkornfield
- **提交说明**：Spec: Add details on GZIP compressed metadata files (#12598)
- **PR/Issue**：#12598

## 总体目的

Iceberg 规范此前虽然允许对 metadata JSON 文件进行 GZIP 压缩，但规范文档中缺乏关于压缩支持的明确说明，也缺少关于压缩文件命名约定的描述。不同实现之间可能对压缩文件的命名存在分歧（如 `.gz.metadata.json` vs `metadata.json.gz`），导致互操作性问题。该提交向 Iceberg 规范（spec.md）中补充了关于 GZIP 压缩 metadata 文件的说明，明确表 metadata JSON 文件可以使用 GZIP 压缩，并记录了命名约定：部分实现要求使用 `.gz.metadata.json` 后缀，而 Java 参考实现还可以读取 `metadata.json.gz` 后缀的文件。

## 如何达成设计目的

- 在 `format/spec.md` 的表元数据序列化部分，添加一句说明 metadata JSON 文件可以使用 GZIP 压缩，并附上 RFC 1952 的链接。
- 在规范附录部分新增"GZIP 压缩 Metadata JSON 文件的命名"小节，描述不同实现对文件后缀的要求和兼容性。

## 修改详情

### `format/spec.md` (修改, +6 lines)

**修改目的**：补充规范中关于 GZIP 压缩 metadata 文件的说明和命名约定。

**工作逻辑**：
- 在表元数据序列化说明段落之后，添加一句："A metadata JSON file may be compressed with GZIP."并链接到 RFC 1952。
- 在附录部分（Appendix F 之后、Appendix G 之前）新增"Naming for GZIP compressed Metadata JSON files"小节，说明部分实现要求 GZIP 压缩文件使用 `.gz.metadata.json` 后缀才能正确读取，而 Java 参考实现还可以读取 `metadata.json.gz` 后缀的文件。

## 总结

该提交是对 Iceberg 规范文档的补充完善，明确了 metadata JSON 文件的 GZIP 压缩支持和命名约定，有助于不同 Iceberg 实现之间在压缩 metadata 文件处理上达成一致，减少互操作性问题。
