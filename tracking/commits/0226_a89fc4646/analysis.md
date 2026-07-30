# 提交 0226：Spec: Clarify how column IDs are required (#9162)

## 提交信息

- **序号**：0226 / 4088
- **哈希**：a89fc4646ae5e328a2ccadb98d41b79632afcf3b
- **短哈希**：a89fc4646
- **日期**：2023-12-06
- **作者**：emkornfield
- **提交说明**：Spec: Clarify how column IDs are required (#9162)
- **PR/Issue**：#9162

## 总体目的

这个提交对 Iceberg 规范文档（`format/spec.md`）中关于 Parquet 列 ID（column IDs）的要求进行措辞上的澄清。

在规范原文本中，仅简单声明 "Column IDs are required"（列 ID 是必需的），但没有说明这些列 ID 在 Parquet 文件中具体应如何存储。读者容易产生歧义：是必须作为 Parquet schema 中字段的 `field_id` 元数据存储，还是某种其它机制。本提交把这句话补充完整，明确指明列 ID 必须以 [Parquet field IDs](http://github.com/apache/parquet-format/blob/40699d05bd24181de6b1457babbee2c16dce3803/src/main/thrift/parquet.thrift#L459) 的形式存储在 Parquet schema 上。

这一澄清对 Iceberg 与 Parquet 的互操作性非常重要：Iceberg 依赖 Parquet 的 field id 来建立 schema 演进过程中列的稳定身份，从而允许重命名、重排序、删除和新增列而不破坏数据读取。规范的措辞如果不准确，可能导致实现者用错误的方式编码列 ID，造成跨引擎读取不一致。提交属于纯文档维护，不改实现代码，但收紧了规范的措辞。

## 如何达成设计目的

通过单行文本替换，把模糊的 "Column IDs are required." 改为可操作、可验证的 "Column IDs are required to be stored as field IDs on the parquet schema."，并附上指向 Parquet 格式定义中 `SchemaElement` 字段（field_id 所在定义）的精确链接。这样实现者可以直接核对 Parquet 格式规范来落实该要求。

## 修改详情

### `format/spec.md`

**修改目的**：澄清 Parquet 数据类型映射小节中关于列 ID 必需性的措辞，明确"列 ID 必须作为 field ID 存储在 Parquet schema 上"。

**工作逻辑**：原文位于 spec.md 第 966 行附近的 "Data Type Mappings" 小节开头，描述 Iceberg 类型在 Parquet 中如何存储。修改把句末的 "Column IDs are required." 替换为 "Column IDs are required to be stored as [field IDs](...) on the parquet schema."。链接指向 `apache/parquet-format` 仓库中 `parquet.thrift` 的第 459 行，即 Parquet `SchemaElement` 中定义 `field_id` 字段的位置。这是规范级文档的精确化修订，不涉及任何代码行为。

## 小结

这是规范文档的一次精确化补丁，消除了 Parquet 列 ID 存储方式的歧义，强化了 Iceberg 跨引擎互操作的契约基础。
