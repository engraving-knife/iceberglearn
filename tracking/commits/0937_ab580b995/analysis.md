# 提交 0937：Spec: remove the JSON spec for content file and file scan task sections. (#9771)

## 提交信息

- **序号**：0937 / 4088
- **哈希**：ab580b9955ade2c4a755d5b8e150058088a48c2a
- **短哈希**：ab580b995
- **日期**：2024-07-15（Mon Jul 15 21:02:35 2024 -0700）
- **作者**：Steven Zhen Wu \<stevenz3wu@gmail.com\>
- **提交说明**：Spec: remove the JSON spec for content file and file scan task sections. (#9771)
- **PR/Issue**：#9771

## 总体目的

本提交从 Iceberg 核心表格式规范 `format/spec.md` 中移除两个 JSON 序列化小节："Content File (Data and Delete) Serialization"（内容文件序列化）与 "File Scan Task Serialization"（文件扫描任务序列化）。这两节此前以表格形式定义了内容文件（data file / delete file）与文件扫描任务（FileScanTask）在 JSON 中的字段表示，例如 `spec-id`、`content`、`file-path`、`file-format`、`partition`、`record-count`、`file-size-in-bytes`、`column-sizes`、`value-counts`、`null-value-counts`、`nan-value-counts`、`lower-bounds`、`upper-bounds`、`key-metadata`、`split-offsets`、`equality-ids`、`sort-order-id` 等字段的 JSON 表示与示例，以及 FileScanTask 的 `schema`、`spec`、`data-file`、`delete-files`、`residual-filter` 字段。

社区讨论（见提交说明中的邮件列表链接 https://lists.apache.org/thread/2ty27yx4q0zlqd5h71cyyhb5k47yf9bv）得出的结论是：这两段 JSON 序列化定义不应属于核心表格式规范（core table spec）。核心表格式规范应聚焦于表元数据、快照、manifest、数据文件等持久化在存储层的数据结构定义；而内容文件与文件扫描任务的 JSON 序列化更多是引擎/客户端侧用于 `FileScanTask` 序列化传输的实现细节，虽然其 JSON 序列化器在 `FileScanTask` 序列化场景下有价值，但把它放进核心规范会给人一种"这是规范强制的跨实现互操作格式"的误导，实际上各引擎可以有自己的任务序列化方式。

移除这两节可以使核心规范更聚焦、更准确地反映"什么是表格式规范"的边界，避免把实现侧的序列化约定与持久化格式规范混为一谈。

## 如何达成设计目的

实现方式是直接删除 `format/spec.md` 中这两个小节及其表格内容（共 36 行），不做任何替代性说明，因为这两段本就不属于核心规范。删除后该文件从 "Appendix C: JSON serialization" 中的 JSON single-value serialization 直接衔接 "Appendix D: Single-value serialization"，结构更紧凑。

不涉及任何代码改动，仅是文档层面的规范范围收敛。

## 修改详情

### `format/spec.md`

**修改目的**：从核心表格式规范中移除内容文件与文件扫描任务的 JSON 序列化定义，使规范聚焦于持久化格式而非实现侧序列化约定。

**工作逻辑**：删除以下两个小节（共 36 行）：

1. **`### Content File (Data and Delete) Serialization`**：定义了内容文件（data file 或 delete file）作为 JSON 对象序列化时的字段表，包括 `spec-id`、`content`、`file-path`、`file-format`、`partition`、`record-count`、`file-size-in-bytes`、`column-sizes`、`value-counts`、`null-value-counts`、`nan-value-counts`、`lower-bounds`、`upper-bounds`、`key-metadata`、`split-offsets`、`equality-ids`、`sort-order-id` 等字段的 JSON 表示与示例。

2. **`### File Scan Task Serialization`**：定义了文件扫描任务作为 JSON 对象序列化时的字段表，包括 `schema`、`spec`、`data-file`、`delete-files`、`residual-filter` 字段。

删除后，"Appendix C: JSON serialization" 仅保留 JSON single-value serialization 部分，随后直接进入 "Appendix D: Single-value serialization"。

## 小结

- **成效**：从核心表格式规范中移除了内容文件与文件扫描任务的 JSON 序列化小节，使规范边界更清晰——核心规范聚焦持久化格式，而非引擎侧的任务序列化实现细节。这与社区邮件列表讨论的共识一致。
- **影响范围**：仅 `format/spec.md` 一个文件，删除 36 行，无新增；不影响任何运行时代码或表格式本身，仅是文档范围收敛。
- **回迁到 1.4.x 的注意事项**：这是一次纯文档/规范范围的调整，不改变表格式版本或任何持久化行为，回迁到 1.4.x 完全无风险。是否回迁取决于 1.4.x 分支的 `spec.md` 是否仍保留这两节；若保留且希望与 main 保持规范范围一致，可回迁。由于不涉及代码，cherry-pick 不会引发任何兼容性问题。
